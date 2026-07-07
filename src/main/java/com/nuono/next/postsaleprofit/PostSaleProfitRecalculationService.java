package com.nuono.next.postsaleprofit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.PostSaleProfitLogisticsClosureMapper;
import com.nuono.next.infrastructure.mapper.PostSaleProfitMapper;
import com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.AttributionWriteRow;
import com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.BatchWriteRow;
import com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.LockedAttributionRow;
import com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.RecalculationRunRow;
import com.nuono.next.postsaleprofit.PostSaleProfitSourceRecords.FinanceSaleCandidateRow;
import com.nuono.next.postsaleprofit.PostSaleProfitSourceRecords.HeadhaulCostBatchRow;
import com.nuono.next.postsaleprofit.PostSaleProfitSourceRecords.OrderSaleCandidateRow;
import com.nuono.next.postsaleprofit.PostSaleProfitSourceRecords.PurchaseCostBatchRow;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.ConfirmedHeadhaulAllocationRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PostSaleProfitRecalculationService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final PostSaleProfitMapper mapper;
    private final PostSaleProfitLogisticsClosureMapper logisticsClosureMapper;

    public PostSaleProfitRecalculationService(PostSaleProfitMapper mapper) {
        this(mapper, null);
    }

    @Autowired
    public PostSaleProfitRecalculationService(
            PostSaleProfitMapper mapper,
            PostSaleProfitLogisticsClosureMapper logisticsClosureMapper
    ) {
        this.mapper = mapper;
        this.logisticsClosureMapper = logisticsClosureMapper;
    }

    public PostSaleProfitRecalculationView recalculatePreview(PostSaleProfitRecalculationCommand command) {
        List<FinanceSaleCandidateRow> financeRows = mapper.listFinanceSaleCandidates(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode(),
                command.getDateFrom(),
                command.getDateTo()
        );
        List<OrderSaleCandidateRow> orderRows = mapper.listOrderSaleCandidates(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode(),
                command.getDateFrom(),
                command.getDateTo()
        );
        List<PurchaseCostBatchRow> purchaseRows = mapper.listPurchaseCostBatches(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode()
        );
        List<HeadhaulCostBatchRow> headhaulRows = mapper.listHeadhaulCostBatches(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode()
        );
        List<ConfirmedHeadhaulAllocationRow> confirmedHeadhaulRows = logisticsClosureMapper == null
                ? List.of()
                : logisticsClosureMapper.listConfirmedHeadhaulAllocations(
                        command.getOwnerUserId(),
                        command.getStoreCode(),
                        command.getSiteCode()
                );
        List<LockedAttributionRow> lockedRows = mapper.listLockedAttributionsForScope(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode(),
                command.getDateFrom(),
                command.getDateTo()
        );

        int batchCandidateCount = purchaseRows.size();
        PreviewCalculation preview = buildPreview(command, financeRows, orderRows, purchaseRows, headhaulRows, confirmedHeadhaulRows, lockedRows);
        int saleCandidateCount = preview.saleLineCount;
        int missingIssueCount = missingIssueCount(financeRows, orderRows, purchaseRows, headhaulRows, preview.rows);
        RunSummary runSummary = runSummary(saleCandidateCount, preview.attributionResult);
        RecalculationRunRow run = previewRun(command, runSummary, missingIssueCount);
        mapper.softDeletePreviewRuns(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode(),
                command.getDateFrom(),
                command.getDateTo()
        );
        mapper.insertRecalculationRun(run);
        Map<String, Long> batchIdBySourceId = new LinkedHashMap<>();
        for (int index = 0; index < preview.batchResults.size(); index++) {
            PostSaleProfitBatchResult result = preview.batchResults.get(index);
            BatchWriteRow batchRow = batchWriteRow(command, run.id, result);
            mapper.insertBatch(batchRow);
            batchIdBySourceId.put(result.sourceId(), batchRow.id);
            preview.rows.get(index).setBatchId(batchRow.id);
        }
        for (PostSaleProfitOrderAttributionSlice slice : preview.attributionResult.attributions()) {
            Long batchId = batchIdBySourceId.get(slice.sourceId());
            if (batchId != null) {
                mapper.insertOrderAttribution(attributionWriteRow(command, run.id, batchId, slice));
            }
        }
        return new PostSaleProfitRecalculationView(
                run.id,
                "PREVIEW",
                saleCandidateCount,
                batchCandidateCount,
                missingIssueCount,
                preview.rows
        );
    }

    private PreviewCalculation buildPreview(
            PostSaleProfitRecalculationCommand command,
            List<FinanceSaleCandidateRow> financeRows,
            List<OrderSaleCandidateRow> orderRows,
            List<PurchaseCostBatchRow> purchaseRows,
            List<HeadhaulCostBatchRow> headhaulRows,
            List<ConfirmedHeadhaulAllocationRow> confirmedHeadhaulRows,
            List<LockedAttributionRow> lockedRows
    ) {
        List<PostSaleProfitSaleCandidate> sales = !financeRows.isEmpty()
                ? financeRows.stream().map(this::financeSaleCandidate).collect(Collectors.toList())
                : orderRows.stream().map(this::orderSaleCandidate).collect(Collectors.toList());
        Map<String, String> currencyBySku = new LinkedHashMap<>();
        Map<String, BigDecimal> soldQuantityBySku = new LinkedHashMap<>();
        for (PostSaleProfitSaleCandidate sale : sales) {
            if (sale.partnerSku() != null && sale.currency() != null) {
                currencyBySku.putIfAbsent(sale.partnerSku(), sale.currency());
            }
            soldQuantityBySku.merge(sale.partnerSku(), valueOrZero(sale.quantity()), BigDecimal::add);
        }
        Map<String, BigDecimal> fxRateByCurrency = new LinkedHashMap<>();
        for (String currency : currencyBySku.values()) {
            fxRateByCurrency.computeIfAbsent(currency, value -> mapper.selectApplicableFxRate(
                    command.getOwnerUserId(),
                    command.getSiteCode(),
                    value,
                    command.getDateFrom(),
                    command.getDateTo()
            ));
        }
        Map<String, HeadhaulCost> headhaulCostBySku = new LinkedHashMap<>();
        for (HeadhaulCostBatchRow row : headhaulRows) {
            if (row.partnerSku != null && row.headhaulUnitCostCny != null) {
                headhaulCostBySku.putIfAbsent(row.partnerSku, HeadhaulCost.from(row, isEstimatedHeadhaul(row.sourceId)));
            }
        }
        Map<String, HeadhaulCost> confirmedHeadhaulCostBySourceId = confirmedHeadhaulCostBySourceId(confirmedHeadhaulRows);
        List<PostSaleProfitBatchCandidate> batches = new ArrayList<>();
        for (PurchaseCostBatchRow row : purchaseRows) {
            String partnerSku = firstText(row.partnerSku, row.pskuCode, row.skuParent);
            String currency = currencyBySku.get(partnerSku);
            HeadhaulCost headhaulCost = confirmedHeadhaulCostBySourceId.get(row.sourceId);
            if (headhaulCost == null) {
                headhaulCost = headhaulCostBySku.get(partnerSku);
            }
            List<String> reviewReasons = purchaseReviewReasons(row);
            String evidenceJson = evidenceJson(row, headhaulCost, reviewReasons);
            batches.add(new PostSaleProfitBatchCandidate(
                    row.sourceId,
                    partnerSku,
                    row.skuParent,
                    row.productTitle,
                    row.productImageUrl,
                    row.purchaseBatchTime,
                    row.purchaseQuantity,
                    row.purchaseUnitCostCny,
                    headhaulCost == null ? null : headhaulCost.unitCostCny,
                    headhaulCost != null && headhaulCost.estimated,
                    currency == null ? null : fxRateByCurrency.get(currency),
                    evidenceJson,
                    !reviewReasons.isEmpty()
            ));
        }
        for (Map.Entry<String, BigDecimal> entry : soldQuantityBySku.entrySet()) {
            String partnerSku = entry.getKey();
            String currency = currencyBySku.get(partnerSku);
            HeadhaulCost headhaulCost = headhaulCostBySku.get(partnerSku);
            String evidenceJson = unassignedEvidenceJson(partnerSku, headhaulCost);
            batches.add(new PostSaleProfitBatchCandidate(
                    PostSaleProfitSourceIds.unassigned(partnerSku),
                    partnerSku,
                    null,
                    null,
                    null,
                    null,
                    entry.getValue(),
                    null,
                    headhaulCost == null ? null : headhaulCost.unitCostCny,
                    headhaulCost != null && headhaulCost.estimated,
                    currency == null ? null : fxRateByCurrency.get(currency),
                    evidenceJson,
                    false
            ));
        }
        PostSaleProfitAttributionResult attributionResult = new PostSaleProfitAttributionService()
                .attribute(sales, batches, lockedAttributions(lockedRows));
        List<PostSaleProfitBatchResult> batchResults = attributionResult.batchResults();
        List<PostSaleProfitBatchRowView> rows = batchResults
                .stream()
                .map(this::batchRowView)
                .collect(Collectors.toList());
        return new PreviewCalculation(attributionResult, batchResults, rows, sales.size());
    }

    private PostSaleProfitSaleCandidate financeSaleCandidate(FinanceSaleCandidateRow row) {
        return new PostSaleProfitSaleCandidate(
                row.itemNr,
                row.orderNr,
                PostSaleProfitSourceIds.normalizePartnerSku(row.partnerSku),
                valueOrOne(row.soldQuantity),
                row.netProceedsLcy,
                row.referralFeeLcy,
                row.fulfillmentFeeLcy,
                row.otherFeeNetLcy,
                row.currency,
                row.orderTime
        );
    }

    private PostSaleProfitSaleCandidate orderSaleCandidate(OrderSaleCandidateRow row) {
        return new PostSaleProfitSaleCandidate(
                row.itemNr,
                row.orderNr,
                PostSaleProfitSourceIds.normalizePartnerSku(row.partnerSku),
                valueOrOne(row.soldQuantity),
                null,
                null,
                null,
                null,
                row.currency,
                row.orderTime
        );
    }

    private List<PostSaleProfitLockedAttribution> lockedAttributions(List<LockedAttributionRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new PostSaleProfitLockedAttribution(
                        row.itemNr,
                        row.sourceId,
                        valueOrZero(row.quantity),
                        row.manualReason
                ))
                .collect(Collectors.toList());
    }

    private PostSaleProfitBatchRowView batchRowView(PostSaleProfitBatchResult result) {
        PostSaleProfitBatchRowView view = new PostSaleProfitBatchRowView();
        view.setSourceId(result.sourceId());
        view.setSkuParent(result.skuParent());
        view.setPartnerSku(result.partnerSku());
        view.setProductTitle(result.productTitle());
        view.setProductImageUrl(result.productImageUrl());
        view.setPurchaseBatchTime(result.purchaseBatchTime());
        view.setPurchaseQuantity(result.availableQuantity());
        view.setPurchaseUnitCostCny(result.purchaseUnitCostCny());
        view.setPurchaseCostCny(result.purchaseCostCny());
        ShippingFields shippingFields = shippingFields(
                result.sourceId(),
                result.headhaulUnitCostCny(),
                result.qualityStatuses(),
                result.evidenceJson()
        );
        view.setShippingSourceType(shippingFields.shippingSourceType);
        view.setShippingSourceId(shippingFields.shippingSourceId);
        view.setShippingBatchNo(shippingFields.shippingBatchNo);
        view.setInTransitBatchId(shippingFields.inTransitBatchId);
        view.setInTransitReferenceNo(shippingFields.inTransitReferenceNo);
        view.setAvailableAt(shippingFields.availableAt);
        view.setAvailableAtSource(shippingFields.availableAtSource);
        view.setHeadhaulCostSourceType(shippingFields.headhaulCostSourceType);
        view.setHeadhaulUnitCostCny(result.headhaulUnitCostCny());
        view.setHeadhaulCostCny(result.headhaulCostCny());
        view.setSoldQuantity(result.soldQuantity());
        view.setAutoQuantity(result.autoQuantity());
        view.setLockedQuantity(result.lockedQuantity());
        view.setNetProceedsLcy(result.netProceedsLcy());
        view.setReferralFeeLcy(result.referralFeeLcy());
        view.setFulfillmentFeeLcy(result.fulfillmentFeeLcy());
        view.setOtherFeeNetLcy(result.otherFeeNetLcy());
        view.setCurrency(result.currency());
        view.setFxRateToCny(result.fxRateToCny());
        view.setProfitCny(result.profitCny());
        view.setProfitRate(result.profitRate());
        view.setQualityStatuses(result.qualityStatuses().stream()
                .map(Enum::name)
                .collect(Collectors.toList()));
        view.setEvidenceJson(result.evidenceJson());
        return view;
    }

    private RecalculationRunRow previewRun(
            PostSaleProfitRecalculationCommand command,
            RunSummary summary,
            int missingIssueCount
    ) {
        RecalculationRunRow row = new RecalculationRunRow();
        row.ownerUserId = command.getOwnerUserId();
        row.storeCode = command.getStoreCode();
        row.siteCode = command.getSiteCode();
        row.dateFrom = command.getDateFrom();
        row.dateTo = command.getDateTo();
        row.status = "PREVIEW";
        row.scopeHash = scopeHash(command.getOwnerUserId(), command.getStoreCode(), command.getSiteCode(), command.getDateFrom(), command.getDateTo());
        row.orderLineCount = summary.orderLineCount;
        row.attributedQuantity = summary.attributedQuantity;
        row.lockedQuantity = summary.lockedQuantity;
        row.unassignedQuantity = summary.unassignedQuantity;
        row.missingIssueCount = missingIssueCount;
        row.diagnosticJson = "{}";
        row.startedAt = LocalDateTime.now();
        row.finishedAt = row.startedAt;
        row.createdBy = null;
        return row;
    }

    private RunSummary runSummary(int saleLineCount, PostSaleProfitAttributionResult attributionResult) {
        BigDecimal attributedQuantity = BigDecimal.ZERO;
        BigDecimal lockedQuantity = BigDecimal.ZERO;
        BigDecimal unassignedQuantity = valueOrZero(attributionResult.unassignedQuantity());
        for (PostSaleProfitOrderAttributionSlice slice : attributionResult.attributions()) {
            BigDecimal quantity = valueOrZero(slice.quantity());
            attributedQuantity = attributedQuantity.add(quantity);
            if (slice.attributionMethod() == PostSaleProfitAttributionMethod.MANUAL) {
                lockedQuantity = lockedQuantity.add(quantity);
            }
            if (slice.attributionMethod() == PostSaleProfitAttributionMethod.UNASSIGNED) {
                unassignedQuantity = unassignedQuantity.add(quantity);
            }
        }
        return new RunSummary(saleLineCount, attributedQuantity, lockedQuantity, unassignedQuantity);
    }

    private BatchWriteRow batchWriteRow(
            PostSaleProfitRecalculationCommand command,
            Long runId,
            PostSaleProfitBatchResult result
    ) {
        BatchWriteRow row = new BatchWriteRow();
        row.runId = runId;
        row.ownerUserId = command.getOwnerUserId();
        row.storeCode = command.getStoreCode();
        row.siteCode = command.getSiteCode();
        row.sourceId = result.sourceId();
        row.skuParent = result.skuParent();
        row.partnerSku = result.partnerSku();
        row.productTitle = result.productTitle();
        row.productImageUrl = result.productImageUrl();
        row.purchaseBatchTime = result.purchaseBatchTime();
        row.purchaseQuantity = result.availableQuantity();
        row.purchaseUnitCostCny = result.purchaseUnitCostCny();
        row.purchaseCostCny = result.purchaseCostCny();
        ShippingFields shippingFields = shippingFields(
                result.sourceId(),
                result.headhaulUnitCostCny(),
                result.qualityStatuses(),
                result.evidenceJson()
        );
        row.shippingSourceType = shippingFields.shippingSourceType;
        row.shippingSourceId = shippingFields.shippingSourceId;
        row.shippingBatchNo = shippingFields.shippingBatchNo;
        row.inTransitBatchId = shippingFields.inTransitBatchId;
        row.inTransitReferenceNo = shippingFields.inTransitReferenceNo;
        row.availableAt = shippingFields.availableAt;
        row.availableAtSource = shippingFields.availableAtSource;
        row.headhaulCostSourceType = shippingFields.headhaulCostSourceType;
        row.headhaulUnitCostCny = result.headhaulUnitCostCny();
        row.headhaulCostCny = result.headhaulCostCny();
        row.soldQuantity = result.soldQuantity();
        row.autoQuantity = result.autoQuantity();
        row.lockedQuantity = result.lockedQuantity();
        row.netProceedsLcy = result.netProceedsLcy();
        row.referralFeeLcy = result.referralFeeLcy();
        row.fulfillmentFeeLcy = result.fulfillmentFeeLcy();
        row.otherFeeNetLcy = result.otherFeeNetLcy();
        row.currency = result.currency();
        row.fxRateToCny = result.fxRateToCny();
        row.profitCny = result.profitCny();
        row.profitRate = result.profitRate();
        row.qualityStatusJson = qualityStatusJson(result.qualityStatuses());
        row.evidenceJson = result.evidenceJson() == null || result.evidenceJson().isBlank()
                ? "{}"
                : result.evidenceJson();
        return row;
    }

    private ShippingFields shippingFields(
            String purchaseSourceId,
            BigDecimal headhaulUnitCostCny,
            List<PostSaleProfitQualityStatus> qualityStatuses,
            String evidenceJson
    ) {
        String defaultShippingSourceType = PostSaleProfitSourceIds.isUnassigned(purchaseSourceId)
                ? "UNASSIGNED_ORDER_QUANTITY"
                : "NO_SHIPPING_BATCH";
        ShippingFields fields = new ShippingFields();
        fields.shippingSourceType = defaultShippingSourceType;
        fields.headhaulCostSourceType = defaultHeadhaulCostSourceType(headhaulUnitCostCny, qualityStatuses);

        JsonNode freight = freightNode(evidenceJson);
        if (freight == null) {
            return fields;
        }
        String freightSourceId = text(freight, "sourceId");
        String freightSourceType = text(freight, "sourceType");
        boolean confirmed = (freightSourceId != null && freightSourceId.startsWith("CONFIRMED_HEADHAUL:"))
                || "CONFIRMED_IN_TRANSIT_GOODS_LINE_HEADHAUL".equals(freightSourceType);
        if (!confirmed) {
            return fields;
        }

        String batchReferenceNo = text(freight, "batchReferenceNo");
        fields.shippingSourceType = firstText(freightSourceType, "CONFIRMED_IN_TRANSIT_GOODS_LINE_HEADHAUL", defaultShippingSourceType);
        fields.shippingSourceId = freightSourceId;
        fields.shippingBatchNo = batchReferenceNo;
        fields.inTransitBatchId = text(freight, "inTransitBatchId");
        fields.inTransitReferenceNo = batchReferenceNo;
        fields.availableAt = dateTime(text(freight, "freightOccurredAt"));
        fields.availableAtSource = "CONFIRMED_IN_TRANSIT";
        if (headhaulUnitCostCny != null) {
            fields.headhaulCostSourceType = fields.shippingSourceType;
        }
        return fields;
    }

    private String defaultHeadhaulCostSourceType(
            BigDecimal headhaulUnitCostCny,
            List<PostSaleProfitQualityStatus> qualityStatuses
    ) {
        if (headhaulUnitCostCny == null) {
            return "MISSING_HEADHAUL";
        }
        if (qualityStatuses != null && qualityStatuses.contains(PostSaleProfitQualityStatus.ESTIMATED_HEADHAUL)) {
            return "ESTIMATED_SKU_AVERAGE";
        }
        return "ACTUAL_SKU_COMPONENT";
    }

    private JsonNode freightNode(String evidenceJson) {
        if (evidenceJson == null || evidenceJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(evidenceJson);
            JsonNode freight = root == null ? null : root.get("freight");
            return freight == null || freight.isNull() || !freight.isObject() ? null : freight;
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || fieldName == null) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private LocalDateTime dateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String evidenceJson(PurchaseCostBatchRow row, HeadhaulCost headhaulCost, List<String> reviewReasons) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> purchase = new LinkedHashMap<>();
        put(purchase, "sourceType", firstText(row.sourceType, sourceTypeFromSourceId(row.sourceId), "UNKNOWN"));
        put(purchase, "sourceId", row.sourceId);
        put(purchase, "providerOrderNo", row.providerOrderNo);
        put(purchase, "orderTime", row.purchaseBatchTime == null ? null : row.purchaseBatchTime.toString());
        put(purchase, "itemId", row.itemId);
        put(purchase, "assignmentId", row.assignmentId);
        put(purchase, "sourceTitle", row.sourceTitle);
        put(purchase, "sourceSkuText", row.sourceSkuText);
        put(purchase, "sourceModelText", row.sourceModelText);
        put(purchase, "sourceImageUrl", row.sourceImageUrl);
        put(purchase, "sourceQuantity", row.sourceQuantity);
        put(purchase, "sourceUnit", row.sourceUnit);
        put(purchase, "sourceAmount", row.sourceAmount);
        put(purchase, "paidAmount", row.paidAmount);
        put(purchase, "assignmentQuantity", row.assignmentQuantity);
        put(purchase, "purchaseQuantity", row.purchaseQuantity);
        put(purchase, "purchaseUnitCostCny", row.purchaseUnitCostCny);
        put(purchase, "allocationBasis", row.allocationBasis);
        put(purchase, "packSize", row.packSize);
        put(purchase, "skuQuantity", row.skuQuantity);
        put(purchase, "evidenceText", row.evidenceText);
        put(purchase, "confidence", row.purchaseUnitCostCny == null ? "missing" : reviewReasons.isEmpty() ? "confirmed" : "review");
        purchase.put("reviewReasons", reviewReasons);
        root.put("purchase", purchase);
        root.put("freight", freightEvidence(headhaulCost));
        return writeJson(root);
    }

    private String unassignedEvidenceJson(String partnerSku, HeadhaulCost headhaulCost) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> purchase = new LinkedHashMap<>();
        put(purchase, "sourceType", "UNASSIGNED_ORDER_QUANTITY");
        put(purchase, "sourceId", PostSaleProfitSourceIds.unassigned(partnerSku));
        put(purchase, "confidence", "missing");
        purchase.put("reviewReasons", List.of("missing_purchase_cost"));
        root.put("purchase", purchase);
        root.put("freight", freightEvidence(headhaulCost));
        return writeJson(root);
    }

    private Map<String, Object> freightEvidence(HeadhaulCost headhaulCost) {
        Map<String, Object> freight = new LinkedHashMap<>();
        if (headhaulCost == null) {
            put(freight, "confidence", "missing");
            freight.put("reviewReasons", List.of("missing_sku_level_headhaul"));
            return freight;
        }
        boolean missingUnitCost = headhaulCost.unitCostCny == null;
        put(freight, "confidence", missingUnitCost ? "missing" : headhaulCost.estimated ? "estimated" : "confirmed");
        put(freight, "sourceType", headhaulSourceType(headhaulCost));
        put(freight, "sourceId", headhaulCost.sourceId);
        put(freight, "purchaseSourceId", headhaulCost.purchaseSourceId);
        put(freight, "inTransitBatchId", headhaulCost.inTransitBatchId);
        put(freight, "inTransitGoodsLineId", headhaulCost.inTransitGoodsLineId);
        put(freight, "billNo", headhaulCost.billNo);
        put(freight, "batchReferenceNo", headhaulCost.batchReferenceNo);
        put(freight, "transportMode", headhaulCost.transportMode);
        put(freight, "destinationCode", headhaulCost.destinationCode);
        put(freight, "freightOccurredAt", headhaulCost.freightOccurredAt == null ? null : headhaulCost.freightOccurredAt.toString());
        put(freight, "freightQuantity", headhaulCost.freightQuantity);
        put(freight, "headhaulCostCny", headhaulCost.headhaulCostCny);
        put(freight, "headhaulUnitCostCny", headhaulCost.unitCostCny);
        put(freight, "allocationBasis", headhaulCost.allocationBasis);
        put(freight, "evidenceText", headhaulCost.evidenceText);
        if (missingUnitCost) {
            freight.put("reviewReasons", headhaulCost.sourceId != null && headhaulCost.sourceId.startsWith("CONFIRMED_HEADHAUL:")
                    ? List.of("missing_confirmed_headhaul_component")
                    : List.of("missing_sku_level_headhaul"));
        } else {
            freight.put("reviewReasons", headhaulCost.estimated ? List.of("estimated_sku_average_headhaul") : List.of());
        }
        return freight;
    }

    private Map<String, HeadhaulCost> confirmedHeadhaulCostBySourceId(List<ConfirmedHeadhaulAllocationRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<String, ConfirmedHeadhaulAccumulator> accumulators = new LinkedHashMap<>();
        for (ConfirmedHeadhaulAllocationRow row : rows) {
            if (row == null || row.sourceId == null || row.sourceId.isBlank()) {
                continue;
            }
            accumulators.computeIfAbsent(row.sourceId, ConfirmedHeadhaulAccumulator::new).add(row);
        }
        Map<String, HeadhaulCost> result = new LinkedHashMap<>();
        for (Map.Entry<String, ConfirmedHeadhaulAccumulator> entry : accumulators.entrySet()) {
            result.put(entry.getKey(), entry.getValue().toHeadhaulCost());
        }
        return result;
    }

    private List<String> purchaseReviewReasons(PurchaseCostBatchRow row) {
        List<String> reasons = new ArrayList<>();
        String sourceType = firstText(row.sourceType, sourceTypeFromSourceId(row.sourceId), "");
        if ("ALI1688_ALLOCATION".equals(sourceType)) {
            if (row.allocationBasis == null || row.allocationBasis.isBlank()) {
                reasons.add("missing_allocation_basis");
            }
            if ((row.sourceTitle == null || row.sourceTitle.isBlank()) && (row.sourceImageUrl == null || row.sourceImageUrl.isBlank())) {
                reasons.add("missing_source_identity");
            }
        } else if ("ALI1688_PRODUCT_LINK".equals(sourceType)) {
            if ((row.sourceTitle == null || row.sourceTitle.isBlank()) && (row.sourceImageUrl == null || row.sourceImageUrl.isBlank())) {
                reasons.add("missing_source_identity");
            }
        }
        return reasons;
    }

    private String sourceTypeFromSourceId(String sourceId) {
        if (sourceId == null) {
            return null;
        }
        if (sourceId.startsWith("UNASSIGNED:")) {
            return "UNASSIGNED_ORDER_QUANTITY";
        }
        int separator = sourceId.indexOf(':');
        return separator > 0 ? sourceId.substring(0, separator) : "MANUAL_SKU_PURCHASE_BATCH";
    }

    private void put(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private AttributionWriteRow attributionWriteRow(
            PostSaleProfitRecalculationCommand command,
            Long runId,
            Long batchId,
            PostSaleProfitOrderAttributionSlice slice
    ) {
        AttributionWriteRow row = new AttributionWriteRow();
        row.runId = runId;
        row.batchId = batchId;
        row.ownerUserId = command.getOwnerUserId();
        row.storeCode = command.getStoreCode();
        row.siteCode = command.getSiteCode();
        row.financeItemNr = slice.itemNr();
        row.orderNr = slice.orderNr();
        row.itemNr = slice.itemNr();
        row.orderTime = slice.orderTime();
        row.partnerSku = slice.partnerSku();
        row.sku = null;
        row.attributedQuantity = slice.quantity();
        row.attributionMethod = slice.attributionMethod().name();
        row.locked = slice.attributionMethod() == PostSaleProfitAttributionMethod.MANUAL;
        row.manualReason = slice.manualReason();
        row.netProceedsLcy = slice.netProceedsLcy();
        row.referralFeeLcy = slice.referralFeeLcy();
        row.fulfillmentFeeLcy = slice.fulfillmentFeeLcy();
        row.otherFeeNetLcy = slice.otherFeeNetLcy();
        row.currency = slice.currency();
        row.qualityStatusJson = "[]";
        return row;
    }

    private int missingIssueCount(
            List<FinanceSaleCandidateRow> financeRows,
            List<OrderSaleCandidateRow> orderRows,
            List<PurchaseCostBatchRow> purchaseRows,
            List<HeadhaulCostBatchRow> headhaulRows,
            List<PostSaleProfitBatchRowView> rows
    ) {
        int issues = 0;
        for (PostSaleProfitBatchRowView row : rows) {
            for (String status : row.getQualityStatuses()) {
                try {
                    if (PostSaleProfitQualityPolicy.isHardMissing(PostSaleProfitQualityStatus.valueOf(status))) {
                        issues++;
                    }
                } catch (IllegalArgumentException ignored) {
                    // Ignore forward-compatible quality statuses that this backend does not classify yet.
                }
            }
        }
        return issues;
    }

    private BigDecimal valueOrOne(BigDecimal value) {
        return value == null ? BigDecimal.ONE : value;
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String scopeHash(Long ownerUserId, String storeCode, String siteCode, LocalDate dateFrom, LocalDate dateTo) {
        return ownerUserId + "|" + storeCode + "|" + siteCode + "|" + dateFrom + "|" + dateTo + "|PREVIEW";
    }

    private String qualityStatusJson(List<PostSaleProfitQualityStatus> statuses) {
        return statuses.stream()
                .map(status -> "\"" + status.name() + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private String firstText(String first, String second, String third) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return third;
    }

    private boolean isEstimatedHeadhaul(String sourceId) {
        return sourceId != null && sourceId.startsWith("IN_TRANSIT_HEADHAUL_SKU_AVERAGE:");
    }

    private String headhaulSourceType(HeadhaulCost headhaulCost) {
        if (headhaulCost.sourceId != null && headhaulCost.sourceId.startsWith("CONFIRMED_HEADHAUL:")) {
            return "CONFIRMED_IN_TRANSIT_GOODS_LINE_HEADHAUL";
        }
        if (headhaulCost.sourceId != null && headhaulCost.sourceId.startsWith("IN_TRANSIT_HEADHAUL_BATCH:")) {
            return "IN_TRANSIT_BATCH_HEADHAUL";
        }
        if (headhaulCost.estimated) {
            return "IN_TRANSIT_SKU_AVERAGE_HEADHAUL";
        }
        return "IN_TRANSIT_HEADHAUL";
    }

    private static final class HeadhaulCost {
        private final String sourceId;
        private final String purchaseSourceId;
        private final Long inTransitBatchId;
        private final Long inTransitGoodsLineId;
        private final String billNo;
        private final String batchReferenceNo;
        private final String transportMode;
        private final String destinationCode;
        private final LocalDateTime freightOccurredAt;
        private final BigDecimal freightQuantity;
        private final BigDecimal headhaulCostCny;
        private final BigDecimal unitCostCny;
        private final boolean estimated;
        private final String allocationBasis;
        private final String evidenceText;

        private HeadhaulCost(
                String sourceId,
                String purchaseSourceId,
                Long inTransitBatchId,
                Long inTransitGoodsLineId,
                String billNo,
                String batchReferenceNo,
                String transportMode,
                String destinationCode,
                LocalDateTime freightOccurredAt,
                BigDecimal freightQuantity,
                BigDecimal headhaulCostCny,
                BigDecimal unitCostCny,
                boolean estimated,
                String allocationBasis,
                String evidenceText
        ) {
            this.sourceId = sourceId;
            this.purchaseSourceId = purchaseSourceId;
            this.inTransitBatchId = inTransitBatchId;
            this.inTransitGoodsLineId = inTransitGoodsLineId;
            this.billNo = billNo;
            this.batchReferenceNo = batchReferenceNo;
            this.transportMode = transportMode;
            this.destinationCode = destinationCode;
            this.freightOccurredAt = freightOccurredAt;
            this.freightQuantity = freightQuantity;
            this.headhaulCostCny = headhaulCostCny;
            this.unitCostCny = unitCostCny;
            this.estimated = estimated;
            this.allocationBasis = allocationBasis;
            this.evidenceText = evidenceText;
        }

        private static HeadhaulCost from(HeadhaulCostBatchRow row, boolean estimated) {
            return new HeadhaulCost(
                    row.sourceId,
                    null,
                    row.inTransitBatchId,
                    null,
                    row.billNo,
                    row.batchReferenceNo,
                    row.transportMode,
                    row.destinationCode,
                    row.freightOccurredAt,
                    row.freightQuantity,
                    row.headhaulCostCny,
                    row.headhaulUnitCostCny,
                    estimated,
                    row.allocationBasis,
                    row.evidenceText
            );
        }
    }

    private static final class ConfirmedHeadhaulAccumulator {
        private final String purchaseSourceId;
        private String partnerSku;
        private String siteCode;
        private Long inTransitBatchId;
        private Long inTransitGoodsLineId;
        private String billNo;
        private String batchReferenceNo;
        private LocalDateTime freightOccurredAt;
        private BigDecimal allocatedQuantity = BigDecimal.ZERO;
        private BigDecimal freightQuantity = BigDecimal.ZERO;
        private BigDecimal allocatedHeadhaulCostCny = BigDecimal.ZERO;
        private boolean missingUnitCost;
        private String batchProratedAllocationBasis;
        private String batchProratedEvidenceText;

        private ConfirmedHeadhaulAccumulator(String purchaseSourceId) {
            this.purchaseSourceId = purchaseSourceId;
        }

        private void add(ConfirmedHeadhaulAllocationRow row) {
            if (partnerSku == null) {
                partnerSku = row.partnerSku;
                siteCode = row.siteCode;
                inTransitBatchId = row.inTransitBatchId;
                inTransitGoodsLineId = row.inTransitGoodsLineId;
                billNo = row.billNo;
                batchReferenceNo = row.batchReferenceNo;
                freightOccurredAt = row.freightOccurredAt;
            }
            BigDecimal quantity = row.allocatedQuantity == null ? BigDecimal.ZERO : row.allocatedQuantity;
            if (quantity.compareTo(BigDecimal.ZERO) <= 0 || row.headhaulUnitCostCny == null) {
                missingUnitCost = true;
            } else {
                allocatedQuantity = allocatedQuantity.add(quantity);
                allocatedHeadhaulCostCny = allocatedHeadhaulCostCny.add(row.headhaulUnitCostCny.multiply(quantity));
            }
            if (row.freightQuantity != null) {
                freightQuantity = freightQuantity.add(row.freightQuantity);
            }
            if (freightOccurredAt == null) {
                freightOccurredAt = row.freightOccurredAt;
            }
            if ("confirmed_batch_headhaul_prorated_by_shipped_quantity".equals(row.allocationBasis)) {
                batchProratedAllocationBasis = row.allocationBasis;
                batchProratedEvidenceText = row.evidenceText;
            }
        }

        private HeadhaulCost toHeadhaulCost() {
            BigDecimal unitCost = null;
            BigDecimal allocatedHeadhaulCost = null;
            if (!missingUnitCost && allocatedQuantity.compareTo(BigDecimal.ZERO) > 0) {
                unitCost = allocatedHeadhaulCostCny.divide(allocatedQuantity, 6, RoundingMode.HALF_UP);
                allocatedHeadhaulCost = allocatedHeadhaulCostCny;
            }
            String sourceId = "CONFIRMED_HEADHAUL:" + purchaseSourceId;
            return new HeadhaulCost(
                    sourceId,
                    purchaseSourceId,
                    inTransitBatchId,
                    inTransitGoodsLineId,
                    billNo,
                    batchReferenceNo,
                    null,
                    null,
                    freightOccurredAt,
                    freightQuantity.compareTo(BigDecimal.ZERO) > 0 ? freightQuantity : null,
                    allocatedHeadhaulCost,
                    unitCost,
                    false,
                    confirmedAllocationBasis(unitCost),
                    confirmedEvidenceText(unitCost)
            );
        }

        private String confirmedAllocationBasis(BigDecimal unitCost) {
            if (unitCost == null) {
                return "confirmed_allocation_missing_headhaul_component";
            }
            if (batchProratedAllocationBasis != null) {
                return batchProratedAllocationBasis;
            }
            return "confirmed_allocation_weighted_unit_cost";
        }

        private String confirmedEvidenceText(BigDecimal unitCost) {
            if (unitCost == null) {
                return "confirmed allocation exists but SKU/site HEADHAUL component is missing";
            }
            if (batchProratedEvidenceText != null && !batchProratedEvidenceText.isBlank()) {
                return batchProratedEvidenceText;
            }
            return "confirmed allocation matched to SKU/site HEADHAUL component";
        }
    }

    private static final class ShippingFields {
        private String shippingSourceType;
        private String shippingSourceId;
        private String shippingBatchNo;
        private String inTransitBatchId;
        private String inTransitReferenceNo;
        private LocalDateTime availableAt;
        private String availableAtSource;
        private String headhaulCostSourceType;
    }

    private static final class PreviewCalculation {
        private final PostSaleProfitAttributionResult attributionResult;
        private final List<PostSaleProfitBatchResult> batchResults;
        private final List<PostSaleProfitBatchRowView> rows;
        private final int saleLineCount;

        private PreviewCalculation(
                PostSaleProfitAttributionResult attributionResult,
                List<PostSaleProfitBatchResult> batchResults,
                List<PostSaleProfitBatchRowView> rows,
                int saleLineCount
        ) {
            this.attributionResult = attributionResult;
            this.batchResults = batchResults;
            this.rows = rows;
            this.saleLineCount = saleLineCount;
        }
    }

    private static final class RunSummary {
        private final int orderLineCount;
        private final BigDecimal attributedQuantity;
        private final BigDecimal lockedQuantity;
        private final BigDecimal unassignedQuantity;

        private RunSummary(
                int orderLineCount,
                BigDecimal attributedQuantity,
                BigDecimal lockedQuantity,
                BigDecimal unassignedQuantity
        ) {
            this.orderLineCount = orderLineCount;
            this.attributedQuantity = attributedQuantity;
            this.lockedQuantity = lockedQuantity;
            this.unassignedQuantity = unassignedQuantity;
        }
    }
}
