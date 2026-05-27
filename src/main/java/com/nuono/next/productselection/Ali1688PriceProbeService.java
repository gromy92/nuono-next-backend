package com.nuono.next.productselection;

import com.nuono.next.infrastructure.mapper.Ali1688CollectionMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class Ali1688PriceProbeService {

    private static final String SAFETY_MODE = "preview_only";
    private static final String SIDE_EFFECT_POLICY = "no_payment_no_order_no_message";
    private static final Set<String> FAILURE_CODES = Set.of(
            "login_required",
            "captcha_required",
            "rate_limited",
            "sku_selection_required",
            "stock_unavailable",
            "shipping_unavailable",
            "preview_failed"
    );

    private final Ali1688CollectionMapper ali1688CollectionMapper;
    private final Ali1688CandidateGateService candidateGateService;
    private final ObjectProvider<Ali1688PriceProbeExecutor> executorProvider;

    public Ali1688PriceProbeService(
            Ali1688CollectionMapper ali1688CollectionMapper,
            Ali1688CandidateGateService candidateGateService,
            ObjectProvider<Ali1688PriceProbeExecutor> executorProvider
    ) {
        this.ali1688CollectionMapper = ali1688CollectionMapper;
        this.candidateGateService = candidateGateService;
        this.executorProvider = executorProvider;
    }

    @Transactional
    public Ali1688RealPriceSnapshot requestProbe(String candidateId, Ali1688PriceProbeCommand command, Long operatorUserId) {
        Long id = parseId(candidateId);
        Ali1688CollectionRecords.CandidateRecord candidate = ali1688CollectionMapper.selectCandidateById(id);
        if (candidate == null) {
            throw new IllegalArgumentException("1688 候选不存在。");
        }
        Ali1688RealPriceSnapshot latestSnapshot = ali1688CollectionMapper.selectLatestRealPriceSnapshotByCandidate(candidate.id);
        Ali1688CandidateGateView gate = candidateGateService.resolve(
                candidate,
                resolvePriceState(latestSnapshot),
                false,
                latestSnapshot == null ? null : latestSnapshot.failureCode
        );
        if (!Boolean.TRUE.equals(gate.allowsPriceProbe)) {
            throw new IllegalArgumentException("候选未通过 AI 门禁，不能执行真实价格探针。");
        }
        Ali1688CollectionRecords.TaskRecord task = ali1688CollectionMapper.selectTaskById(candidate.taskId);
        if (task == null) {
            throw new IllegalArgumentException("1688 采集任务不存在。");
        }

        Ali1688PriceProbeRequest request = new Ali1688PriceProbeRequest(
                task,
                candidate,
                normalizeQuantity(command == null ? null : command.getQuantity()),
                trim(command == null ? null : command.getSkuText()),
                trim(command == null ? null : command.getRegionText()),
                SAFETY_MODE,
                operatorUserId
        );
        Ali1688PriceProbeExecutor executor = executorProvider.getIfAvailable();
        Ali1688PriceProbeResult result = executor == null
                ? Ali1688PriceProbeResult.failed("preview_failed", "真实价格探针执行器未启用。")
                : executor.probe(request);
        Ali1688RealPriceSnapshot snapshot = toSnapshot(task, candidate, request, normalizeResult(result), operatorUserId);
        ali1688CollectionMapper.insertRealPriceSnapshot(snapshot);
        return snapshot;
    }

    private Ali1688RealPriceSnapshot toSnapshot(
            Ali1688CollectionRecords.TaskRecord task,
            Ali1688CollectionRecords.CandidateRecord candidate,
            Ali1688PriceProbeRequest request,
            Ali1688PriceProbeResult result,
            Long operatorUserId
    ) {
        Ali1688RealPriceSnapshot snapshot = new Ali1688RealPriceSnapshot();
        snapshot.id = ali1688CollectionMapper.nextRealPriceSnapshotId();
        snapshot.taskId = task.id;
        snapshot.candidateId = candidate.id;
        snapshot.sourceCollectionId = candidate.sourceCollectionId;
        snapshot.ownerUserId = candidate.ownerUserId;
        snapshot.logicalStoreId = candidate.logicalStoreId;
        snapshot.status = defaultText(result.status, "failed");
        snapshot.safetyMode = SAFETY_MODE;
        snapshot.sideEffectPolicy = SIDE_EFFECT_POLICY;
        snapshot.source = defaultText(result.source, "order_preview");
        snapshot.skuText = defaultText(result.skuText, request.skuText);
        snapshot.quantity = result.quantity == null ? request.quantity : result.quantity;
        snapshot.unitPrice = result.unitPrice;
        snapshot.freightPrice = result.freightPrice;
        snapshot.discountPrice = result.discountPrice;
        snapshot.totalPrice = result.totalPrice;
        snapshot.currency = defaultText(result.currency, "CNY");
        snapshot.rmbTotalPrice = result.rmbTotalPrice;
        snapshot.exchangeRateToRmb = result.exchangeRateToRmb;
        snapshot.regionText = defaultText(result.regionText, request.regionText);
        snapshot.addressContextJson = result.addressContextJson;
        snapshot.failureCode = result.failureCode;
        snapshot.failureMessage = result.failureMessage;
        snapshot.rawSnapshotJson = result.rawSnapshotJson;
        snapshot.capturedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        snapshot.createdBy = operatorUserId;
        snapshot.updatedBy = operatorUserId;
        return snapshot;
    }

    private Ali1688PriceProbeResult normalizeResult(Ali1688PriceProbeResult result) {
        if (result == null) {
            return Ali1688PriceProbeResult.failed("preview_failed", "真实价格探针未返回结果。");
        }
        if (!"confirmed".equals(result.status)) {
            result.status = "failed";
            if (!FAILURE_CODES.contains(defaultText(result.failureCode, ""))) {
                result.failureCode = "preview_failed";
            }
            result.failureMessage = defaultText(result.failureMessage, "真实价格探针失败。");
        }
        return result;
    }

    private Long parseId(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("缺少1688候选ID。");
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("1688候选ID格式不正确。", exception);
        }
    }

    private Integer normalizeQuantity(Integer quantity) {
        return quantity == null || quantity <= 0 ? 1 : quantity;
    }

    private String resolvePriceState(Ali1688RealPriceSnapshot snapshot) {
        if (snapshot == null) {
            return "list_hint_only";
        }
        if ("confirmed".equals(snapshot.status)) {
            return "price_confirmed";
        }
        if ("failed".equals(snapshot.status)) {
            return "price_probe_failed";
        }
        return "price_probe_pending";
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
