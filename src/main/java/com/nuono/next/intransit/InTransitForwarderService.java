package com.nuono.next.intransit;

import com.nuono.next.infrastructure.mapper.InTransitGoodsMapper;
import com.nuono.next.intransit.InTransitForwarderCommands.ResolveForwarderCommand;
import com.nuono.next.intransit.InTransitForwarderCommands.SaveForwarderAliasCommand;
import com.nuono.next.intransit.InTransitForwarderCommands.SaveForwarderCommand;
import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderAliasRow;
import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderAliasView;
import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderResolveView;
import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderRow;
import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class InTransitForwarderService {

    private final InTransitGoodsMapper mapper;
    private final InTransitOperationAuditService auditService;

    public InTransitForwarderService(InTransitGoodsMapper mapper, InTransitOperationAuditService auditService) {
        this.mapper = mapper;
        this.auditService = auditService;
    }

    public InTransitContractView contract() {
        return InTransitContractView.build();
    }

    public InTransitTransportMode requireTransportMode(String transportMode) {
        return InTransitTransportMode.require(transportMode);
    }

    public List<ForwarderView> listForwarders(Long ownerUserId) {
        requireOwnerUserId(ownerUserId);
        return mapper.listForwarders(ownerUserId).stream()
                .map(ForwarderView::from)
                .collect(Collectors.toList());
    }

    public ForwarderView saveForwarder(SaveForwarderCommand command) {
        SaveForwarderCommand resolved = command == null ? new SaveForwarderCommand() : command;
        Long ownerUserId = requireOwnerUserId(resolved.getOwnerUserId());
        String forwarderName = requireText(resolved.getForwarderName(), "标准货代名称不能为空。");
        String forwarderCode = normalizeForwarderCode(resolved.getForwarderCode(), forwarderName);
        Long operatorUserId = resolved.getOperatorUserId();

        ForwarderRow row = mapper.selectForwarderByOwnerAndCode(ownerUserId, forwarderCode);
        if (row == null) {
            row = new ForwarderRow();
            row.setId(mapper.nextForwarderId());
            row.setOwnerUserId(ownerUserId);
            row.setForwarderCode(forwarderCode);
            row.setForwarderName(forwarderName);
            row.setStatus("ACTIVE");
            row.setCreatedBy(operatorUserId);
            row.setUpdatedBy(operatorUserId);
            mapper.insertForwarder(row);
            audit(
                    ownerUserId,
                    operatorUserId,
                    "forwarder_created",
                    "forwarder",
                    row.getId(),
                    "标准货代已创建。",
                    detail("forwarderCode", row.getForwarderCode(), "forwarderName", row.getForwarderName())
            );
            return ForwarderView.from(requireForwarder(ownerUserId, row.getId()));
        }

        row.setForwarderName(forwarderName);
        row.setStatus("ACTIVE");
        row.setUpdatedBy(operatorUserId);
        mapper.updateForwarder(row);
        audit(
                ownerUserId,
                operatorUserId,
                "forwarder_updated",
                "forwarder",
                row.getId(),
                "标准货代已更新。",
                detail("forwarderCode", row.getForwarderCode(), "forwarderName", row.getForwarderName())
        );
        return ForwarderView.from(requireForwarder(ownerUserId, row.getId()));
    }

    public ForwarderAliasView saveForwarderAlias(SaveForwarderAliasCommand command) {
        SaveForwarderAliasCommand resolved = command == null ? new SaveForwarderAliasCommand() : command;
        Long ownerUserId = requireOwnerUserId(resolved.getOwnerUserId());
        Long standardForwarderId = requireId(resolved.getStandardForwarderId(), "标准货代不能为空。");
        String rawForwarderName = requireText(resolved.getRawForwarderName(), "原始货代名称不能为空。");
        String normalizedRawName = normalizeRawForwarderName(rawForwarderName);
        ForwarderRow forwarder = mapper.selectForwarderById(ownerUserId, standardForwarderId);
        if (forwarder == null) {
            throw new IllegalArgumentException("标准货代不存在。");
        }

        Long operatorUserId = resolved.getOperatorUserId();
        ForwarderAliasRow row = mapper.selectAliasByOwnerAndNormalized(ownerUserId, normalizedRawName);
        if (row == null) {
            row = new ForwarderAliasRow();
            row.setId(mapper.nextForwarderAliasId());
            row.setOwnerUserId(ownerUserId);
            row.setStandardForwarderId(standardForwarderId);
            row.setRawForwarderName(rawForwarderName);
            row.setNormalizedRawForwarderName(normalizedRawName);
            row.setStatus("ACTIVE");
            row.setCreatedBy(operatorUserId);
            row.setUpdatedBy(operatorUserId);
            mapper.insertForwarderAlias(row);
            audit(
                    ownerUserId,
                    operatorUserId,
                    "forwarder_alias_saved",
                    "forwarder_alias",
                    row.getId(),
                    "货代别名已保存。",
                    detail("standardForwarderId", standardForwarderId, "rawForwarderName", rawForwarderName)
            );
            return ForwarderAliasView.from(requireAlias(ownerUserId, row.getId()));
        }

        row.setStandardForwarderId(standardForwarderId);
        row.setRawForwarderName(rawForwarderName);
        row.setStatus("ACTIVE");
        row.setUpdatedBy(operatorUserId);
        mapper.updateForwarderAlias(row);
        audit(
                ownerUserId,
                operatorUserId,
                "forwarder_alias_saved",
                "forwarder_alias",
                row.getId(),
                "货代别名已保存。",
                detail("standardForwarderId", standardForwarderId, "rawForwarderName", rawForwarderName)
        );
        return ForwarderAliasView.from(requireAlias(ownerUserId, row.getId()));
    }

    public ForwarderResolveView resolveForwarder(ResolveForwarderCommand command) {
        ResolveForwarderCommand resolved = command == null ? new ResolveForwarderCommand() : command;
        Long ownerUserId = requireOwnerUserId(resolved.getOwnerUserId());
        String rawForwarderName = requireText(resolved.getRawForwarderName(), "原始货代名称不能为空。");
        String normalizedRawName = normalizeRawForwarderName(rawForwarderName);
        ForwarderAliasRow alias = mapper.selectActiveAliasByOwnerAndNormalized(ownerUserId, normalizedRawName);
        if (alias == null) {
            return ForwarderResolveView.unmatched(rawForwarderName, normalizedRawName);
        }
        return ForwarderResolveView.matched(alias);
    }

    static String normalizeRawForwarderName(String value) {
        return requireText(value, "原始货代名称不能为空。")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
    }

    private ForwarderRow requireForwarder(Long ownerUserId, Long forwarderId) {
        ForwarderRow row = mapper.selectForwarderById(ownerUserId, forwarderId);
        if (row == null) {
            throw new IllegalStateException("标准货代保存后读取失败。");
        }
        return row;
    }

    private ForwarderAliasRow requireAlias(Long ownerUserId, Long aliasId) {
        ForwarderAliasRow row = mapper.selectAliasById(ownerUserId, aliasId);
        if (row == null) {
            throw new IllegalStateException("货代别名保存后读取失败。");
        }
        return row;
    }

    private static Long requireOwnerUserId(Long ownerUserId) {
        return requireId(ownerUserId, "缺少老板账号范围。");
    }

    private static Long requireId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalizeForwarderCode(String code, String name) {
        if (StringUtils.hasText(code)) {
            return code.trim().toUpperCase(Locale.ROOT);
        }
        return "FWD_" + Integer.toHexString(normalizeRawForwarderName(name).hashCode()).toUpperCase(Locale.ROOT);
    }

    private void audit(
            Long ownerUserId,
            Long operatorUserId,
            String operationType,
            String targetType,
            Long targetId,
            String summary,
            Map<String, Object> detail
    ) {
        InTransitOperationAuditService.AuditCommand command = new InTransitOperationAuditService.AuditCommand();
        command.setOwnerUserId(ownerUserId);
        command.setOperatorUserId(operatorUserId);
        command.setOperationType(operationType);
        command.setTargetType(targetType);
        command.setTargetId(targetId);
        command.setSummary(summary);
        command.setDetail(detail);
        auditService.record(command);
    }

    private static Map<String, Object> detail(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (pairs == null) {
            return result;
        }
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            result.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return result;
    }
}
