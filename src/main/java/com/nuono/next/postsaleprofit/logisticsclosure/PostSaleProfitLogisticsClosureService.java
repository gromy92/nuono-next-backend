package com.nuono.next.postsaleprofit.logisticsclosure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.PostSaleProfitLogisticsClosureMapper;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.LogisticsClosureAllocationResultView;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.LogisticsClosureAllocationRow;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.LogisticsClosureCandidateListView;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.LogisticsClosureCandidateRow;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.LogisticsClosureCandidateView;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.LogisticsClosureConfirmCommand;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.LogisticsClosurePurchaseBatchListView;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.LogisticsClosurePurchaseBatchRow;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.LogisticsClosurePurchaseBatchView;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.LogisticsClosureRejectCommand;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.LogisticsClosureSummaryView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PostSaleProfitLogisticsClosureService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PostSaleProfitLogisticsClosureMapper mapper;

    public PostSaleProfitLogisticsClosureService(PostSaleProfitLogisticsClosureMapper mapper) {
        this.mapper = mapper;
    }

    public LogisticsClosurePurchaseBatchListView listPurchaseBatches(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate dateFrom,
            LocalDate dateTo,
            String keyword,
            String status,
            int page,
            int pageSize
    ) {
        int safePage = page < 1 ? 1 : page;
        int safePageSize = pageSize < 1 ? 50 : Math.min(pageSize, 200);
        LogisticsClosureSummaryView summary = mapper.selectSummary(ownerUserId, storeCode, siteCode, dateFrom, dateTo);
        if (summary == null) {
            summary = new LogisticsClosureSummaryView();
        }
        List<LogisticsClosurePurchaseBatchView> rows = mapper.listPurchaseBatches(
                        ownerUserId,
                        storeCode,
                        siteCode,
                        dateFrom,
                        dateTo,
                        trim(keyword),
                        trim(status),
                        safePageSize,
                        (safePage - 1) * safePageSize
                )
                .stream()
                .map(LogisticsClosurePurchaseBatchView::from)
                .collect(Collectors.toList());
        return new LogisticsClosurePurchaseBatchListView(summary, rows);
    }

    public LogisticsClosureCandidateListView listCandidates(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate dateFrom,
            LocalDate dateTo,
            String sourceType,
            String sourceId
    ) {
        LogisticsClosurePurchaseBatchRow purchase = requirePurchase(
                ownerUserId,
                storeCode,
                siteCode,
                dateFrom,
                dateTo,
                sourceType,
                sourceId
        );
        List<LogisticsClosureCandidateView> rows = mapper.listCandidates(
                        ownerUserId,
                        storeCode,
                        siteCode,
                        purchase.sourceType,
                        purchase.sourceId,
                        purchase.partnerSku
                )
                .stream()
                .map(LogisticsClosureCandidateView::from)
                .collect(Collectors.toList());
        return new LogisticsClosureCandidateListView(LogisticsClosurePurchaseBatchView.from(purchase), rows);
    }

    public LogisticsClosureAllocationResultView confirmAllocation(LogisticsClosureConfirmCommand command) {
        validateConfirmCommand(command);
        LogisticsClosurePurchaseBatchRow purchase = requirePurchase(command);
        LogisticsClosureCandidateRow candidate = requireCandidate(command, purchase);
        BigDecimal allocatedQuantity = command.getAllocatedQuantity();

        BigDecimal confirmedPurchaseQuantity = valueOrZero(mapper.sumConfirmedQuantityBySource(
                command.getOwnerUserId(),
                purchase.sourceType,
                purchase.sourceId
        ));
        BigDecimal remainingPurchaseQuantity = valueOrZero(purchase.purchaseQuantity).subtract(confirmedPurchaseQuantity);

        BigDecimal confirmedInTransitQuantity = valueOrZero(mapper.sumConfirmedQuantityByInTransitLine(
                command.getOwnerUserId(),
                candidate.inTransitGoodsLineId
        ));
        BigDecimal remainingInTransitQuantity = valueOrZero(candidate.shippedQuantity).subtract(confirmedInTransitQuantity);
        if (allocatedQuantity.compareTo(remainingPurchaseQuantity) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "allocatedQuantity exceeds remaining purchase quantity.");
        }
        if (allocatedQuantity.compareTo(remainingInTransitQuantity) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "allocatedQuantity exceeds remaining in-transit quantity.");
        }

        LogisticsClosureAllocationRow row = allocationRow(command, purchase, candidate, "CONFIRMED");
        row.id = mapper.nextAllocationId();
        row.allocatedQuantity = allocatedQuantity;
        row.confirmedBy = command.getOperatorUserId();
        row.confirmedAt = LocalDateTime.now();
        row.evidenceJson = evidenceJson(command, purchase, candidate, row.confirmationStatus);
        mapper.insertAllocation(row);
        return new LogisticsClosureAllocationResultView(row.id, row.confirmationStatus);
    }

    public LogisticsClosureAllocationResultView rejectCandidate(LogisticsClosureRejectCommand command) {
        validateRejectCommand(command);
        LogisticsClosurePurchaseBatchRow purchase = requirePurchase(command);
        LogisticsClosureCandidateRow candidate = requireCandidate(command, purchase);
        LogisticsClosureAllocationRow row = allocationRow(command, purchase, candidate, "REJECTED");
        row.id = mapper.nextAllocationId();
        row.allocatedQuantity = ZERO;
        row.rejectReason = trim(command.getReason());
        row.confirmedBy = command.getOperatorUserId();
        row.confirmedAt = LocalDateTime.now();
        row.evidenceJson = evidenceJson(command, purchase, candidate, row.confirmationStatus);
        mapper.insertAllocation(row);
        return new LogisticsClosureAllocationResultView(row.id, row.confirmationStatus);
    }

    public LogisticsClosureAllocationResultView deleteAllocation(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            Long operatorUserId,
            Long allocationId
    ) {
        if (allocationId == null || allocationId < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "allocationId is required.");
        }
        if (!StringUtils.hasText(storeCode) || !StringUtils.hasText(siteCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storeCode and siteCode are required.");
        }
        mapper.softDeleteAllocation(ownerUserId, storeCode, siteCode, allocationId, operatorUserId);
        return new LogisticsClosureAllocationResultView(allocationId, "DELETED");
    }

    private LogisticsClosurePurchaseBatchRow requirePurchase(LogisticsClosureConfirmCommand command) {
        return requirePurchase(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode(),
                null,
                null,
                command.getSourceType(),
                command.getSourceId()
        );
    }

    private LogisticsClosurePurchaseBatchRow requirePurchase(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            LocalDate dateFrom,
            LocalDate dateTo,
            String sourceType,
            String sourceId
    ) {
        if (!StringUtils.hasText(sourceType) || !StringUtils.hasText(sourceId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceType and sourceId are required.");
        }
        LogisticsClosurePurchaseBatchRow purchase = mapper.selectPurchaseBatchBySource(
                ownerUserId,
                storeCode,
                siteCode,
                dateFrom,
                dateTo,
                sourceType.trim(),
                sourceId.trim()
        );
        if (purchase == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "purchase batch not found.");
        }
        return purchase;
    }

    private LogisticsClosureCandidateRow requireCandidate(
            LogisticsClosureConfirmCommand command,
            LogisticsClosurePurchaseBatchRow purchase
    ) {
        LogisticsClosureCandidateRow candidate = mapper.selectCandidateByLine(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode(),
                purchase.partnerSku,
                command.getInTransitBatchId(),
                command.getInTransitGoodsLineId()
        );
        if (candidate == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "in-transit goods line not found.");
        }
        return candidate;
    }

    private void validateConfirmCommand(LogisticsClosureConfirmCommand command) {
        validateCommonCommand(command);
        if (command.getAllocatedQuantity() == null || command.getAllocatedQuantity().compareTo(ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "allocatedQuantity must be greater than 0.");
        }
    }

    private void validateRejectCommand(LogisticsClosureRejectCommand command) {
        validateCommonCommand(command);
        if (!StringUtils.hasText(command.getReason())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reason is required.");
        }
    }

    private void validateCommonCommand(LogisticsClosureConfirmCommand command) {
        if (command == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
        }
        if (command.getOwnerUserId() == null
                || !StringUtils.hasText(command.getStoreCode())
                || !StringUtils.hasText(command.getSiteCode())
                || !StringUtils.hasText(command.getSourceType())
                || !StringUtils.hasText(command.getSourceId())
                || command.getInTransitBatchId() == null
                || command.getInTransitGoodsLineId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "storeCode, siteCode, sourceType, sourceId, inTransitBatchId and inTransitGoodsLineId are required."
            );
        }
    }

    private LogisticsClosureAllocationRow allocationRow(
            LogisticsClosureConfirmCommand command,
            LogisticsClosurePurchaseBatchRow purchase,
            LogisticsClosureCandidateRow candidate,
            String confirmationStatus
    ) {
        LogisticsClosureAllocationRow row = new LogisticsClosureAllocationRow();
        row.ownerUserId = command.getOwnerUserId();
        row.sourceType = purchase.sourceType;
        row.sourceId = purchase.sourceId;
        row.sourceLineId = purchase.sourceLineId;
        row.targetStoreCode = command.getStoreCode();
        row.targetSiteCode = command.getSiteCode();
        row.partnerSku = purchase.partnerSku;
        row.skuParent = purchase.skuParent;
        row.productVariantId = purchase.productVariantId;
        row.purchaseBatchId = purchase.purchaseBatchId;
        row.inTransitBatchId = candidate.inTransitBatchId;
        row.inTransitGoodsLineId = candidate.inTransitGoodsLineId;
        row.allocationUnit = "PCS";
        row.matchMethod = StringUtils.hasText(command.getMatchMethod()) ? command.getMatchMethod().trim() : "MANUAL";
        row.confirmationStatus = confirmationStatus;
        row.confidenceScore = candidate.confidenceScore;
        row.createdBy = command.getOperatorUserId();
        row.updatedBy = command.getOperatorUserId();
        return row;
    }

    private String evidenceJson(
            LogisticsClosureConfirmCommand command,
            LogisticsClosurePurchaseBatchRow purchase,
            LogisticsClosureCandidateRow candidate,
            String confirmationStatus
    ) {
        Map<String, Object> root = new LinkedHashMap<>();
        put(root, "confirmationStatus", confirmationStatus);
        put(root, "matchMethod", StringUtils.hasText(command.getMatchMethod()) ? command.getMatchMethod().trim() : "MANUAL");
        put(root, "reason", trim(command.getReason()));

        Map<String, Object> purchaseEvidence = new LinkedHashMap<>();
        put(purchaseEvidence, "sourceType", purchase.sourceType);
        put(purchaseEvidence, "sourceId", purchase.sourceId);
        put(purchaseEvidence, "purchaseBatchId", purchase.purchaseBatchId);
        put(purchaseEvidence, "sourceLineId", purchase.sourceLineId);
        put(purchaseEvidence, "partnerSku", purchase.partnerSku);
        put(purchaseEvidence, "skuParent", purchase.skuParent);
        put(purchaseEvidence, "targetStoreCode", purchase.targetStoreCode);
        put(purchaseEvidence, "targetSiteCode", purchase.targetSiteCode);
        put(purchaseEvidence, "purchaseBatchTime", purchase.purchaseBatchTime == null ? null : purchase.purchaseBatchTime.toString());
        put(purchaseEvidence, "purchaseQuantity", purchase.purchaseQuantity);
        put(purchaseEvidence, "purchaseCostCny", purchase.purchaseCostCny);
        root.put("purchase", purchaseEvidence);

        Map<String, Object> logisticsEvidence = new LinkedHashMap<>();
        put(logisticsEvidence, "inTransitBatchId", candidate.inTransitBatchId);
        put(logisticsEvidence, "inTransitGoodsLineId", candidate.inTransitGoodsLineId);
        put(logisticsEvidence, "batchReferenceNo", candidate.batchReferenceNo);
        put(logisticsEvidence, "forwarderName", candidate.forwarderName);
        put(logisticsEvidence, "transportMode", candidate.transportMode);
        put(logisticsEvidence, "nodeStatus", candidate.nodeStatus);
        put(logisticsEvidence, "nodeHappenedAt", candidate.nodeHappenedAt == null ? null : candidate.nodeHappenedAt.toString());
        put(logisticsEvidence, "siteCode", candidate.siteCode);
        put(logisticsEvidence, "partnerSku", candidate.partnerSku);
        put(logisticsEvidence, "shippedQuantity", candidate.shippedQuantity);
        put(logisticsEvidence, "allocatedQuantity", command.getAllocatedQuantity());
        put(logisticsEvidence, "headhaulStatus", candidate.headhaulStatus);
        put(logisticsEvidence, "headhaulUnitCostCny", candidate.headhaulUnitCostCny);
        put(logisticsEvidence, "headhaulCostCny", candidate.headhaulCostCny);
        put(logisticsEvidence, "candidateStrength", candidate.candidateStrength);
        put(logisticsEvidence, "confidenceScore", candidate.confidenceScore);
        root.put("logistics", logisticsEvidence);

        try {
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private void put(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
