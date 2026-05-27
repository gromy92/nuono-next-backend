package com.nuono.next.procurement;

import com.nuono.next.infrastructure.mapper.ProcurementLogisticsRequirementMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbProcurementLogisticsRequirementService {

    private final ProcurementLogisticsRequirementMapper mapper;

    public LocalDbProcurementLogisticsRequirementService(ProcurementLogisticsRequirementMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public ProcurementLogisticsRequirementView saveRequirement(ProcurementLogisticsRequirementCommand command) {
        validateOwnerAndDemand(command);
        if (mapper.countOwnedDemandItem(command.getOwnerUserId(), command.getDemandItemId()) <= 0) {
            throw new IllegalArgumentException("当前采购需求不在老板名下，不能保存物流需求。");
        }
        ProcurementLogisticsRequirementRow existing = mapper.selectRequirement(
                command.getOwnerUserId(),
                command.getDemandItemId()
        );
        ProcurementLogisticsRequirementRow row = toRow(command, existing);
        int changed = mapper.upsertRequirement(row, operatorUserId(command));
        if (changed <= 0) {
            throw new IllegalStateException("物流需求保存失败，请刷新后重试。");
        }
        ProcurementLogisticsRequirementView view = toView(row);
        view.setMessage("物流需求已保存。");
        return view;
    }

    @Transactional(readOnly = true)
    public ProcurementLogisticsRequirementView getRequirement(Long ownerUserId, Long demandItemId) {
        if (ownerUserId == null) {
            throw new IllegalArgumentException("缺少老板上下文，暂时不能读取物流需求。");
        }
        if (demandItemId == null) {
            throw new IllegalArgumentException("缺少采购需求 ID。");
        }
        ProcurementLogisticsRequirementRow row = mapper.selectRequirement(ownerUserId, demandItemId);
        if (row == null) {
            ProcurementLogisticsRequirementView view = baseView();
            view.setMessage("当前采购需求还没有填写物流需求。");
            view.setRecommendationReady(false);
            view.setRecommendationBlockers(List.of("请先填写物流需求后再生成货代推荐。"));
            return view;
        }
        ProcurementLogisticsRequirementView view = toView(row);
        view.setMessage("物流需求已读取。");
        return view;
    }

    @Transactional(readOnly = true)
    public ProcurementLogisticsRequirementRow requireReadyForRecommendation(Long ownerUserId, Long demandItemId) {
        ProcurementLogisticsRequirementView view = getRequirement(ownerUserId, demandItemId);
        if (view.getRequirement() == null) {
            throw new IllegalStateException("请先填写物流需求后再生成货代推荐。");
        }
        if (!view.getRecommendationBlockers().isEmpty()) {
            throw new IllegalStateException("请先补全物流需求：" + blockerMessage(view.getRecommendationBlockers()));
        }
        ProcurementLogisticsRequirementView.RequirementView requirement = view.getRequirement();
        ProcurementLogisticsRequirementRow row = new ProcurementLogisticsRequirementRow();
        row.setId(requirement.getId());
        row.setOwnerUserId(requirement.getOwnerUserId());
        row.setDemandItemId(requirement.getDemandItemId());
        row.setTransportMode(requirement.getTransportMode());
        row.setDestinationCountry(requirement.getDestinationCountry());
        row.setDestinationNode(requirement.getDestinationNode());
        row.setOriginNode(requirement.getOriginNode());
        row.setPackageLengthCm(requirement.getPackageLengthCm());
        row.setPackageWidthCm(requirement.getPackageWidthCm());
        row.setPackageHeightCm(requirement.getPackageHeightCm());
        row.setUnitWeightGrams(requirement.getUnitWeightGrams());
        row.setQuantity(requirement.getQuantity());
        row.setCargoAttributes(requirement.getCargoAttributes());
        row.setStatus(requirement.getStatus());
        return row;
    }

    private String blockerMessage(List<String> blockers) {
        List<String> fragments = new ArrayList<>();
        for (String blocker : blockers) {
            fragments.add(trimTerminalPunctuation(blocker));
        }
        return String.join("；", fragments) + "。";
    }

    private String trimTerminalPunctuation(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replaceAll("[。；;,.，]+$", "");
    }

    private ProcurementLogisticsRequirementRow toRow(
            ProcurementLogisticsRequirementCommand command,
            ProcurementLogisticsRequirementRow existing
    ) {
        ProcurementLogisticsRequirementRow row = new ProcurementLogisticsRequirementRow();
        row.setId(existing == null || existing.getId() == null ? mapper.nextRequirementId() : existing.getId());
        row.setOwnerUserId(command.getOwnerUserId());
        row.setDemandItemId(command.getDemandItemId());
        row.setTransportMode(normalizeTransportMode(command.getTransportMode()));
        row.setDestinationCountry(normalizeUpper(command.getDestinationCountry()));
        row.setDestinationNode(trim(command.getDestinationNode()));
        row.setOriginNode(trim(command.getOriginNode()));
        row.setPackageLengthCm(command.getPackageLengthCm());
        row.setPackageWidthCm(command.getPackageWidthCm());
        row.setPackageHeightCm(command.getPackageHeightCm());
        row.setUnitWeightGrams(command.getUnitWeightGrams());
        row.setQuantity(command.getQuantity());
        row.setCargoAttributes(trim(command.getCargoAttributes()));
        row.setStatus("ACTIVE");
        return row;
    }

    private ProcurementLogisticsRequirementView toView(ProcurementLogisticsRequirementRow row) {
        ProcurementLogisticsRequirementView view = baseView();
        ProcurementLogisticsRequirementView.RequirementView requirement =
                new ProcurementLogisticsRequirementView.RequirementView();
        requirement.setId(row.getId());
        requirement.setOwnerUserId(row.getOwnerUserId());
        requirement.setDemandItemId(row.getDemandItemId());
        requirement.setTransportMode(row.getTransportMode());
        requirement.setDestinationCountry(row.getDestinationCountry());
        requirement.setDestinationNode(row.getDestinationNode());
        requirement.setOriginNode(row.getOriginNode());
        requirement.setPackageLengthCm(row.getPackageLengthCm());
        requirement.setPackageWidthCm(row.getPackageWidthCm());
        requirement.setPackageHeightCm(row.getPackageHeightCm());
        requirement.setUnitWeightGrams(row.getUnitWeightGrams());
        requirement.setQuantity(row.getQuantity());
        requirement.setCargoAttributes(row.getCargoAttributes());
        requirement.setStatus(row.getStatus());
        view.setRequirement(requirement);

        List<String> blockers = recommendationBlockers(row);
        view.setRecommendationBlockers(blockers);
        view.setRecommendationReady(blockers.isEmpty());
        return view;
    }

    private ProcurementLogisticsRequirementView baseView() {
        ProcurementLogisticsRequirementView view = new ProcurementLogisticsRequirementView();
        view.setMode("local-db");
        view.setReady(true);
        return view;
    }

    private List<String> recommendationBlockers(ProcurementLogisticsRequirementRow row) {
        List<String> blockers = new ArrayList<>();
        addMissing(blockers, row.getTransportMode(), "请选择空运或海运。");
        addMissing(blockers, row.getDestinationCountry(), "请填写目的国家。");
        addMissing(blockers, row.getDestinationNode(), "请填写目的仓或目的节点。");
        addMissing(blockers, row.getOriginNode(), "请填写发货地。");
        addMissing(blockers, row.getPackageLengthCm(), "请填写包装长度。");
        addMissing(blockers, row.getPackageWidthCm(), "请填写包装宽度。");
        addMissing(blockers, row.getPackageHeightCm(), "请填写包装高度。");
        addMissing(blockers, row.getUnitWeightGrams(), "请填写单件重量。");
        addMissing(blockers, row.getQuantity(), "请填写采购数量。");
        return blockers;
    }

    private void addMissing(List<String> blockers, String value, String message) {
        if (!StringUtils.hasText(value)) {
            blockers.add(message);
        }
    }

    private void addMissing(List<String> blockers, BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            blockers.add(message);
        }
    }

    private void addMissing(List<String> blockers, Integer value, String message) {
        if (value == null || value <= 0) {
            blockers.add(message);
        }
    }

    private void validateOwnerAndDemand(ProcurementLogisticsRequirementCommand command) {
        if (command == null || command.getOwnerUserId() == null) {
            throw new IllegalArgumentException("缺少老板上下文，暂时不能保存物流需求。");
        }
        if (command.getDemandItemId() == null) {
            throw new IllegalArgumentException("缺少采购需求 ID。");
        }
    }

    private Long operatorUserId(ProcurementLogisticsRequirementCommand command) {
        return command.getOperatorUserId() == null ? command.getOwnerUserId() : command.getOperatorUserId();
    }

    private String normalizeTransportMode(String value) {
        String normalized = trim(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if ("air".equals(lower) || lower.contains("空运")) {
            return "air";
        }
        if ("sea".equals(lower) || lower.contains("海运")) {
            return "sea";
        }
        throw new IllegalArgumentException("运输方式只能选择空运或海运。");
    }

    private String normalizeUpper(String value) {
        String normalized = trim(value);
        return StringUtils.hasText(normalized) ? normalized.toUpperCase(Locale.ROOT) : null;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
