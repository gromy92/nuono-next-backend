package com.nuono.next.productlogisticscost;

import com.nuono.next.infrastructure.mapper.ProductLogisticsCostMapper;
import com.nuono.next.productlogisticscost.ProductLogisticsCostCommands.BatchCategoryAssignmentCommand;
import com.nuono.next.productlogisticscost.ProductLogisticsCostCommands.CategoryAssignmentItem;
import com.nuono.next.productlogisticscost.ProductLogisticsCostCommands.ManualCurrentQuoteCommand;
import com.nuono.next.productlogisticscost.ProductLogisticsCostCommands.ManualRateCardCommand;
import com.nuono.next.productlogisticscost.ProductLogisticsCostCommands.ProductMatchRow;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.BatchCategoryAssignmentItemResult;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.BatchCategoryAssignmentResult;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CostExceptionRow;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CostExceptionView;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CostHistoryRow;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CostHistoryView;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CurrentCostRow;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.CurrentCostView;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.RateCardRow;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.RateCardView;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProductLogisticsCostLedgerService {

    private static final int DEFAULT_READ_LIMIT = 50;
    private static final int MAX_READ_LIMIT = 5000;

    private final ProductLogisticsCostMapper mapper;

    public ProductLogisticsCostLedgerService(ProductLogisticsCostMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public CurrentCostView currentCosts(
            Long ownerUserId,
            Long logicalStoreId,
            String storeCode,
            Long productVariantId,
            String partnerSku,
            String siteCode,
            String forwarderCode,
            String transportMode,
            Integer limit
    ) {
        CurrentCostView view = new CurrentCostView();
        Long requiredOwnerUserId = requireOwnerUserId(ownerUserId);
        Long effectiveLogicalStoreId = resolveLogicalStoreId(requiredOwnerUserId, logicalStoreId, storeCode);
        if (StringUtils.hasText(storeCode) && effectiveLogicalStoreId == null) {
            return view;
        }
        List<CurrentCostRow> rows = mapper.listCurrentCosts(
                requiredOwnerUserId,
                effectiveLogicalStoreId,
                productVariantId,
                clean(partnerSku),
                normalizeCode(siteCode),
                normalizeForwarderCode(forwarderCode),
                normalizeCode(transportMode),
                normalizeReadLimit(limit)
        );
        if (rows != null) {
            view.items.addAll(rows);
        }
        return view;
    }

    @Transactional(readOnly = true)
    public CostHistoryView history(
            Long ownerUserId,
            Long logicalStoreId,
            String storeCode,
            Long productVariantId,
            String partnerSku,
            String siteCode,
            String forwarderCode,
            String transportMode,
            Integer limit
    ) {
        CostHistoryView view = new CostHistoryView();
        Long requiredOwnerUserId = requireOwnerUserId(ownerUserId);
        Long effectiveLogicalStoreId = resolveLogicalStoreId(requiredOwnerUserId, logicalStoreId, storeCode);
        if (StringUtils.hasText(storeCode) && effectiveLogicalStoreId == null) {
            return view;
        }
        List<CostHistoryRow> rows = mapper.listHistory(
                requiredOwnerUserId,
                effectiveLogicalStoreId,
                productVariantId,
                clean(partnerSku),
                normalizeCode(siteCode),
                normalizeForwarderCode(forwarderCode),
                normalizeCode(transportMode),
                normalizeReadLimit(limit)
        );
        if (rows != null) {
            view.items.addAll(rows);
        }
        return view;
    }

    @Transactional(readOnly = true)
    public RateCardView rateCards(
            Long ownerUserId,
            String siteCode,
            String forwarderCode,
            String transportMode
    ) {
        RateCardView view = new RateCardView();
        List<RateCardRow> rows = mapper.listRateCards(
                requireOwnerUserId(ownerUserId),
                normalizeCode(siteCode),
                normalizeForwarderCode(forwarderCode),
                normalizeCode(transportMode)
        );
        if (rows != null) {
            view.items.addAll(rows);
        }
        return view;
    }

    @Transactional
    public RateCardRow manualRateCard(Long ownerUserId, Long operatorUserId, ManualRateCardCommand command) {
        Long requiredOwnerUserId = requireOwnerUserId(ownerUserId);
        Long requiredOperatorUserId = requireOperatorUserId(operatorUserId);
        if (command == null) {
            throw new IllegalArgumentException("线路类别报价不能为空。");
        }
        String siteCode = normalizeCode(requireText(command.siteCode, "站点不能为空。"));
        String forwarderCode = normalizeForwarderCode(requireText(command.forwarderCode, "货代不能为空。"));
        String transportMode = normalizeCode(requireText(command.transportMode, "货运方式不能为空。"));
        String feeType = normalizeCode(defaultText(command.feeType, "HEADHAUL"));
        String chargeUnit = normalizeCode(requireText(command.chargeUnit, "计费单位不能为空。"));
        BigDecimal unitCostCny = requirePositive(command.unitCostCny, "当前报价必须大于 0。");
        String cargoCategoryName = clean(command.cargoCategoryName);
        String cargoCategoryCode = normalizeCategoryCode(command.cargoCategoryCode, cargoCategoryName);
        if (cargoCategoryCode == null) {
            throw new IllegalArgumentException("类别不能为空。");
        }
        if (cargoCategoryName == null) {
            cargoCategoryName = defaultCategoryName(cargoCategoryCode);
        }
        String sourceType = normalizeCode(defaultText(command.sourceType, "MANUAL_RATE_CARD"));
        LocalDateTime effectiveAt = LocalDateTime.now();

        RateCardRow row = new RateCardRow();
        row.id = mapper.nextProductLogisticsRateCardId();
        row.ownerUserId = requiredOwnerUserId;
        row.siteCode = siteCode;
        row.forwarderCode = forwarderCode;
        row.forwarderName = defaultText(command.forwarderName, defaultForwarderName(forwarderCode));
        row.transportMode = transportMode;
        row.feeType = feeType;
        row.cargoCategoryCode = cargoCategoryCode;
        row.cargoCategoryName = cargoCategoryName;
        row.chargeUnit = chargeUnit;
        row.unitCostCny = unitCostCny;
        row.currencyCode = "CNY";
        row.sourceType = sourceType;
        row.sourceReference = clean(command.sourceReference);
        row.effectiveAt = effectiveAt;
        row.remark = clean(command.remark);
        row.evidenceJson = rateCardEvidenceJson(row);
        mapper.upsertRateCard(row, requiredOperatorUserId);
        return row;
    }

    @Transactional
    public CurrentCostRow manualCurrentQuote(Long ownerUserId, Long operatorUserId, ManualCurrentQuoteCommand command) {
        Long requiredOwnerUserId = requireOwnerUserId(ownerUserId);
        Long requiredOperatorUserId = requireOperatorUserId(operatorUserId);
        if (command == null) {
            throw new IllegalArgumentException("人工维护报价不能为空。");
        }
        String storeCode = requireText(command.storeCode, "店铺不能为空。");
        String partnerSku = requireText(command.partnerSku, "系统 PSKU 不能为空。");
        String siteCode = normalizeCode(requireText(command.siteCode, "站点不能为空。"));
        String forwarderCode = normalizeForwarderCode(requireText(command.forwarderCode, "货代不能为空。"));
        String transportMode = normalizeCode(requireText(command.transportMode, "货运方式不能为空。"));
        String feeType = normalizeCode(defaultText(command.feeType, "HEADHAUL"));
        String chargeUnit = normalizeCode(requireText(command.chargeUnit, "计费单位不能为空。"));
        BigDecimal unitCostCny = requirePositive(command.unitCostCny, "当前报价必须大于 0。");
        String cargoCategoryName = clean(command.cargoCategoryName);
        String cargoCategoryCode = normalizeCategoryCode(command.cargoCategoryCode, cargoCategoryName);
        if ("ET".equals(forwarderCode) && cargoCategoryName == null) {
            throw new IllegalArgumentException("易通人工报价必须维护 Charge Desct 类别。");
        }
        if (cargoCategoryName == null && cargoCategoryCode != null) {
            cargoCategoryName = cargoCategoryCode;
        }

        List<ProductMatchRow> matches = mapper.selectProductMatches(
                requiredOwnerUserId,
                storeCode,
                partnerSku,
                siteCode
        );
        if (matches == null || matches.isEmpty()) {
            throw new IllegalArgumentException("未找到当前店铺下的系统 PSKU：" + partnerSku);
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("系统 PSKU 匹配到多个当前商品，请先清理商品主档：" + partnerSku);
        }
        ProductMatchRow product = matches.get(0);
        String resolvedPartnerSku = requireText(product.partnerSku, "商品主档系统 PSKU 不能为空。");
        LocalDateTime occurredAt = LocalDateTime.now();
        String sourceType = "MANUAL_CURRENT_QUOTE";
        String costType = "CURRENT_QUOTE";
        String evidenceJson = manualEvidenceJson(storeCode, resolvedPartnerSku, siteCode, forwarderCode, transportMode,
                cargoCategoryCode, cargoCategoryName, chargeUnit, unitCostCny, command.remark);

        CostHistoryRow history = new CostHistoryRow();
        history.id = mapper.nextProductLogisticsCostHistoryId();
        history.ownerUserId = requiredOwnerUserId;
        history.logicalStoreId = product.logicalStoreId;
        history.productMasterId = product.productMasterId;
        history.productVariantId = product.productVariantId;
        history.partnerSku = product.partnerSku;
        history.barcode = product.barcode;
        history.siteCode = siteCode;
        history.forwarderCode = forwarderCode;
        history.forwarderName = defaultText(command.forwarderName, defaultForwarderName(forwarderCode));
        history.transportMode = transportMode;
        history.sourceType = sourceType;
        history.costType = costType;
        history.feeType = feeType;
        history.rawFeeName = "人工维护当前报价";
        history.cargoCategoryCode = cargoCategoryCode;
        history.cargoCategoryName = cargoCategoryName;
        history.chargeUnit = chargeUnit;
        history.unitCost = unitCostCny;
        history.currencyCode = "CNY";
        history.exchangeRateToCny = BigDecimal.ONE;
        history.unitCostCny = unitCostCny;
        history.allocationBasis = "manual_current_quote";
        history.confidenceLevel = "MANUAL";
        history.costOccurredAt = occurredAt;
        history.idempotencyKey = String.join(":",
                sourceType,
                String.valueOf(requiredOwnerUserId),
                String.valueOf(product.logicalStoreId),
                resolvedPartnerSku,
                siteCode,
                forwarderCode,
                transportMode,
                feeType,
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(requiredOperatorUserId)
        );
        history.evidenceJson = evidenceJson;
        history.rawSnapshotJson = evidenceJson;
        history.reviewStatus = "ACCEPTED";
        mapper.insertCostHistory(history, requiredOperatorUserId);

        CurrentCostRow current = new CurrentCostRow();
        current.id = mapper.nextProductLogisticsCurrentCostId();
        current.ownerUserId = history.ownerUserId;
        current.logicalStoreId = history.logicalStoreId;
        current.productMasterId = history.productMasterId;
        current.productVariantId = history.productVariantId;
        current.partnerSku = history.partnerSku;
        current.barcode = history.barcode;
        current.siteCode = history.siteCode;
        current.forwarderCode = history.forwarderCode;
        current.forwarderName = history.forwarderName;
        current.transportMode = history.transportMode;
        current.currentHistoryId = history.id;
        current.sourceType = history.sourceType;
        current.costType = history.costType;
        current.feeType = history.feeType;
        current.cargoCategoryCode = history.cargoCategoryCode;
        current.cargoCategoryName = history.cargoCategoryName;
        current.chargeUnit = history.chargeUnit;
        current.unitCostCny = history.unitCostCny;
        current.totalCostCny = history.totalCostCny;
        current.currencyCode = history.currencyCode;
        current.confidenceLevel = history.confidenceLevel;
        current.costOccurredAt = history.costOccurredAt;
        current.evidenceJson = history.evidenceJson;
        mapper.upsertCurrentCost(current, requiredOperatorUserId);
        return current;
    }

    @Transactional
    public BatchCategoryAssignmentResult batchAssignCategories(
            Long ownerUserId,
            Long operatorUserId,
            BatchCategoryAssignmentCommand command
    ) {
        Long requiredOwnerUserId = requireOwnerUserId(ownerUserId);
        Long requiredOperatorUserId = requireOperatorUserId(operatorUserId);
        if (command == null) {
            throw new IllegalArgumentException("批量维护类别不能为空。");
        }
        String storeCode = requireText(command.storeCode, "店铺不能为空。");
        String siteCode = normalizeCode(requireText(command.siteCode, "站点不能为空。"));
        String forwarderCode = normalizeForwarderCode(requireText(command.forwarderCode, "货代不能为空。"));
        String transportMode = normalizeCode(requireText(command.transportMode, "货运方式不能为空。"));
        String feeType = normalizeCode(defaultText(command.feeType, "HEADHAUL"));
        String cargoCategoryName = clean(command.cargoCategoryName);
        String cargoCategoryCode = normalizeCategoryCode(command.cargoCategoryCode, cargoCategoryName);
        if (cargoCategoryCode == null && cargoCategoryName == null) {
            throw new IllegalArgumentException("请选择要批量维护的类别。");
        }
        if (cargoCategoryName == null) {
            cargoCategoryName = defaultCategoryName(cargoCategoryCode);
        }

        List<CategoryAssignmentItem> items = command.items == null ? new ArrayList<>() : command.items;
        BatchCategoryAssignmentResult result = new BatchCategoryAssignmentResult();
        result.requestedCount = items.size();
        for (CategoryAssignmentItem item : items) {
            BatchCategoryAssignmentItemResult itemResult = new BatchCategoryAssignmentItemResult();
            String requestedPartnerSku = clean(item == null ? null : item.partnerSku);
            itemResult.partnerSku = requestedPartnerSku;
            result.items.add(itemResult);
            if (requestedPartnerSku == null) {
                skip(result, itemResult, "SKIPPED", "系统 PSKU 不能为空。");
                continue;
            }

            List<ProductMatchRow> matches = mapper.selectProductMatches(
                    requiredOwnerUserId,
                    storeCode,
                    requestedPartnerSku,
                    siteCode
            );
            if (matches == null || matches.isEmpty()) {
                skip(result, itemResult, "NOT_FOUND", "未找到当前店铺下的系统 PSKU。");
                continue;
            }
            if (matches.size() > 1) {
                skip(result, itemResult, "AMBIGUOUS", "系统 PSKU 匹配到多个当前商品，请先清理商品主档。");
                continue;
            }

            ProductMatchRow product = matches.get(0);
            String resolvedPartnerSku = requireText(product.partnerSku, "商品主档系统 PSKU 不能为空。");
            itemResult.resolvedPartnerSku = resolvedPartnerSku;
            CurrentCostRow currentSource = mapper.selectCurrentCostForCategoryAssignment(
                    requiredOwnerUserId,
                    product.logicalStoreId,
                    resolvedPartnerSku,
                    siteCode,
                    forwarderCode,
                    transportMode
            );
            CostHistoryRow historySource = null;
            if (currentSource == null) {
                historySource = mapper.selectLatestHistoryForCategoryAssignment(
                        requiredOwnerUserId,
                        product.logicalStoreId,
                        resolvedPartnerSku,
                        siteCode,
                        forwarderCode,
                        transportMode
                );
            }
            RateCardRow rateCardSource = null;
            if (currentSource == null && historySource == null) {
                rateCardSource = mapper.selectRateCardForCategoryAssignment(
                        requiredOwnerUserId,
                        siteCode,
                        forwarderCode,
                        transportMode,
                        feeType,
                        cargoCategoryCode
                );
            }
            if (currentSource == null && historySource == null && rateCardSource == null) {
                skip(result, itemResult, "NO_RATE_CARD", "当前线路类别没有维护报价。");
                continue;
            }

            LocalDateTime occurredAt = LocalDateTime.now();
            CostHistoryRow history;
            if (currentSource != null) {
                history = categoryAssignmentHistoryFromCurrent(currentSource, product, feeType, cargoCategoryCode,
                        cargoCategoryName, storeCode, command.remark, occurredAt, requiredOperatorUserId);
            } else if (historySource != null) {
                history = categoryAssignmentHistoryFromHistory(historySource, product, feeType, cargoCategoryCode,
                        cargoCategoryName, storeCode, command.remark, occurredAt, requiredOperatorUserId);
            } else {
                history = categoryAssignmentHistoryFromRateCard(rateCardSource, product, feeType, cargoCategoryCode,
                        cargoCategoryName, storeCode, command.remark, occurredAt, requiredOperatorUserId);
            }
            history.id = mapper.nextProductLogisticsCostHistoryId();
            mapper.insertCostHistory(history, requiredOperatorUserId);

            CurrentCostRow current = categoryAssignmentCurrent(history);
            current.id = mapper.nextProductLogisticsCurrentCostId();
            mapper.upsertCurrentCost(current, requiredOperatorUserId);

            result.updatedCount++;
            itemResult.status = "UPDATED";
            itemResult.message = "已维护类别。";
        }
        return result;
    }

    @Transactional(readOnly = true)
    public CostExceptionView openExceptions(Long ownerUserId, Integer limit) {
        CostExceptionView view = new CostExceptionView();
        List<CostExceptionRow> rows = mapper.listOpenExceptions(
                requireOwnerUserId(ownerUserId),
                normalizeReadLimit(limit)
        );
        if (rows != null) {
            view.items.addAll(rows);
        }
        return view;
    }

    private void skip(BatchCategoryAssignmentResult result,
                      BatchCategoryAssignmentItemResult itemResult,
                      String status,
                      String message) {
        result.skippedCount++;
        itemResult.status = status;
        itemResult.message = message;
    }

    private CostHistoryRow categoryAssignmentHistoryFromCurrent(
            CurrentCostRow source,
            ProductMatchRow product,
            String feeType,
            String cargoCategoryCode,
            String cargoCategoryName,
            String storeCode,
            String remark,
            LocalDateTime occurredAt,
            Long operatorUserId
    ) {
        CostHistoryRow history = new CostHistoryRow();
        history.ownerUserId = source.ownerUserId;
        history.logicalStoreId = product.logicalStoreId;
        history.productMasterId = product.productMasterId;
        history.productVariantId = product.productVariantId;
        history.partnerSku = product.partnerSku;
        history.barcode = defaultText(product.barcode, source.barcode);
        history.siteCode = source.siteCode;
        history.forwarderCode = source.forwarderCode;
        history.forwarderName = source.forwarderName;
        history.transportMode = source.transportMode;
        history.routeCode = source.routeCode;
        history.routeName = source.routeName;
        history.serviceCode = source.serviceCode;
        history.serviceName = source.serviceName;
        history.sourceType = "MANUAL_CATEGORY_ASSIGNMENT";
        history.costType = defaultText(source.costType, "CURRENT_QUOTE");
        history.feeType = defaultText(feeType, defaultText(source.feeType, "HEADHAUL"));
        history.rawFeeName = "批量维护类别";
        history.cargoCategoryCode = cargoCategoryCode;
        history.cargoCategoryName = cargoCategoryName;
        history.chargeUnit = source.chargeUnit;
        history.unitCost = source.unitCostCny;
        history.totalCost = source.totalCostCny;
        history.currencyCode = defaultText(source.currencyCode, "CNY");
        history.exchangeRateToCny = BigDecimal.ONE;
        history.unitCostCny = source.unitCostCny;
        history.totalCostCny = source.totalCostCny;
        history.allocationBasis = "manual_category_assignment";
        history.confidenceLevel = "MANUAL";
        history.costOccurredAt = occurredAt;
        history.idempotencyKey = categoryAssignmentIdempotencyKey(
                history,
                source.currentHistoryId,
                operatorUserId
        );
        history.evidenceJson = categoryAssignmentEvidenceJson(storeCode, history.partnerSku, history.siteCode,
                history.forwarderCode, history.transportMode, cargoCategoryCode, cargoCategoryName,
                source.id, source.currentHistoryId, remark);
        history.rawSnapshotJson = history.evidenceJson;
        history.reviewStatus = "ACCEPTED";
        return history;
    }

    private CostHistoryRow categoryAssignmentHistoryFromHistory(
            CostHistoryRow source,
            ProductMatchRow product,
            String feeType,
            String cargoCategoryCode,
            String cargoCategoryName,
            String storeCode,
            String remark,
            LocalDateTime occurredAt,
            Long operatorUserId
    ) {
        CostHistoryRow history = new CostHistoryRow();
        history.ownerUserId = source.ownerUserId;
        history.logicalStoreId = product.logicalStoreId;
        history.productMasterId = product.productMasterId;
        history.productVariantId = product.productVariantId;
        history.partnerSku = product.partnerSku;
        history.barcode = defaultText(product.barcode, source.barcode);
        history.siteCode = source.siteCode;
        history.forwarderCode = source.forwarderCode;
        history.forwarderName = source.forwarderName;
        history.transportMode = source.transportMode;
        history.routeCode = source.routeCode;
        history.routeName = source.routeName;
        history.serviceCode = source.serviceCode;
        history.serviceName = source.serviceName;
        history.inTransitBatchId = source.inTransitBatchId;
        history.batchReferenceNo = source.batchReferenceNo;
        history.sourceType = "MANUAL_CATEGORY_ASSIGNMENT";
        history.costType = defaultText(source.costType, "CURRENT_QUOTE");
        history.sourceActualBillId = source.sourceActualBillId;
        history.sourceActualComponentId = source.sourceActualComponentId;
        history.sourceShippingOrderId = source.sourceShippingOrderId;
        history.sourceQuoteLineId = source.sourceQuoteLineId;
        history.feeType = defaultText(feeType, defaultText(source.feeType, "HEADHAUL"));
        history.rawFeeName = "批量维护类别";
        history.cargoCategoryCode = cargoCategoryCode;
        history.cargoCategoryName = cargoCategoryName;
        history.quantity = source.quantity;
        history.chargeQuantity = source.chargeQuantity;
        history.chargeUnit = source.chargeUnit;
        history.unitCost = source.unitCost;
        history.totalCost = source.totalCost;
        history.currencyCode = defaultText(source.currencyCode, "CNY");
        history.exchangeRateToCny = source.exchangeRateToCny == null ? BigDecimal.ONE : source.exchangeRateToCny;
        history.unitCostCny = source.unitCostCny;
        history.totalCostCny = source.totalCostCny;
        history.allocationBasis = "manual_category_assignment";
        history.confidenceLevel = "MANUAL";
        history.costOccurredAt = occurredAt;
        history.idempotencyKey = categoryAssignmentIdempotencyKey(history, source.id, operatorUserId);
        history.evidenceJson = categoryAssignmentEvidenceJson(storeCode, history.partnerSku, history.siteCode,
                history.forwarderCode, history.transportMode, cargoCategoryCode, cargoCategoryName,
                null, source.id, remark);
        history.rawSnapshotJson = history.evidenceJson;
        history.reviewStatus = "ACCEPTED";
        return history;
    }

    private CostHistoryRow categoryAssignmentHistoryFromRateCard(
            RateCardRow source,
            ProductMatchRow product,
            String feeType,
            String cargoCategoryCode,
            String cargoCategoryName,
            String storeCode,
            String remark,
            LocalDateTime occurredAt,
            Long operatorUserId
    ) {
        CostHistoryRow history = new CostHistoryRow();
        history.ownerUserId = source.ownerUserId;
        history.logicalStoreId = product.logicalStoreId;
        history.productMasterId = product.productMasterId;
        history.productVariantId = product.productVariantId;
        history.partnerSku = product.partnerSku;
        history.barcode = product.barcode;
        history.siteCode = source.siteCode;
        history.forwarderCode = source.forwarderCode;
        history.forwarderName = source.forwarderName;
        history.transportMode = source.transportMode;
        history.sourceType = "ROUTE_RATE_CARD_CATEGORY_ASSIGNMENT";
        history.costType = "CURRENT_QUOTE";
        history.feeType = defaultText(feeType, defaultText(source.feeType, "HEADHAUL"));
        history.rawFeeName = "批量维护类别报价";
        history.cargoCategoryCode = defaultText(cargoCategoryCode, source.cargoCategoryCode);
        history.cargoCategoryName = defaultText(cargoCategoryName, source.cargoCategoryName);
        history.chargeUnit = source.chargeUnit;
        history.unitCost = source.unitCostCny;
        history.currencyCode = defaultText(source.currencyCode, "CNY");
        history.exchangeRateToCny = BigDecimal.ONE;
        history.unitCostCny = source.unitCostCny;
        history.allocationBasis = "route_rate_card_category_assignment";
        history.confidenceLevel = "MANUAL";
        history.costOccurredAt = occurredAt;
        history.idempotencyKey = rateCardCategoryAssignmentIdempotencyKey(history, source.id, operatorUserId);
        history.evidenceJson = rateCardCategoryAssignmentEvidenceJson(storeCode, history.partnerSku, history.siteCode,
                history.forwarderCode, history.transportMode, history.feeType, history.cargoCategoryCode,
                history.cargoCategoryName, history.chargeUnit, history.unitCostCny, source.id, source.sourceType,
                source.sourceReference, remark);
        history.rawSnapshotJson = history.evidenceJson;
        history.reviewStatus = "ACCEPTED";
        return history;
    }

    private CurrentCostRow categoryAssignmentCurrent(CostHistoryRow history) {
        CurrentCostRow current = new CurrentCostRow();
        current.ownerUserId = history.ownerUserId;
        current.logicalStoreId = history.logicalStoreId;
        current.productMasterId = history.productMasterId;
        current.productVariantId = history.productVariantId;
        current.partnerSku = history.partnerSku;
        current.barcode = history.barcode;
        current.siteCode = history.siteCode;
        current.forwarderCode = history.forwarderCode;
        current.forwarderName = history.forwarderName;
        current.transportMode = history.transportMode;
        current.routeCode = history.routeCode;
        current.routeName = history.routeName;
        current.serviceCode = history.serviceCode;
        current.serviceName = history.serviceName;
        current.currentHistoryId = history.id;
        current.sourceType = history.sourceType;
        current.costType = history.costType;
        current.feeType = history.feeType;
        current.cargoCategoryCode = history.cargoCategoryCode;
        current.cargoCategoryName = history.cargoCategoryName;
        current.chargeUnit = history.chargeUnit;
        current.unitCostCny = history.unitCostCny;
        current.totalCostCny = history.totalCostCny;
        current.currencyCode = history.currencyCode;
        current.confidenceLevel = history.confidenceLevel;
        current.costOccurredAt = history.costOccurredAt;
        current.evidenceJson = history.evidenceJson;
        return current;
    }

    private Long resolveLogicalStoreId(Long ownerUserId, Long logicalStoreId, String storeCode) {
        if (logicalStoreId != null) {
            return logicalStoreId;
        }
        String cleanedStoreCode = clean(storeCode);
        if (cleanedStoreCode == null) {
            return null;
        }
        return mapper.selectLogicalStoreIdByStoreCode(ownerUserId, cleanedStoreCode);
    }

    private Long requireOperatorUserId(Long operatorUserId) {
        if (operatorUserId == null || operatorUserId <= 0) {
            throw new IllegalArgumentException("操作人不能为空。");
        }
        return operatorUserId;
    }

    private Long requireOwnerUserId(Long ownerUserId) {
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("业务 ownerUserId 不能为空。");
        }
        return ownerUserId;
    }

    private String clean(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String requireText(String value, String message) {
        String cleaned = clean(value);
        if (cleaned == null) {
            throw new IllegalArgumentException(message);
        }
        return cleaned;
    }

    private String defaultText(String value, String defaultValue) {
        String cleaned = clean(value);
        return cleaned == null ? defaultValue : cleaned;
    }

    private BigDecimal requirePositive(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String normalizeCode(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.toUpperCase(Locale.ROOT);
    }

    private String normalizeForwarderCode(String value) {
        String normalized = normalizeCode(value);
        if (normalized == null) {
            return null;
        }
        if ("易通".equals(normalized) || "YITONG".equals(normalized)) {
            return "ET";
        }
        if ("义特".equals(normalized) || "YT".equals(normalized)) {
            return "YITE";
        }
        if ("CHIC".equals(normalized) || "启客".equals(normalized) || "奇克".equals(normalized)) {
            return "QIKE";
        }
        return normalized;
    }

    private String normalizeCategoryCode(String code, String name) {
        String cleanedCode = normalizeCode(code);
        if (cleanedCode != null) {
            return cleanedCode;
        }
        String cleanedName = clean(name);
        if (cleanedName == null) {
            return null;
        }
        int categoryIndex = cleanedName.indexOf("类别");
        if (categoryIndex > 0) {
            return cleanedName.substring(0, categoryIndex).trim().toUpperCase(Locale.ROOT);
        }
        int shortCategoryIndex = cleanedName.indexOf("类");
        if (shortCategoryIndex > 0) {
            return cleanedName.substring(0, shortCategoryIndex).trim().toUpperCase(Locale.ROOT);
        }
        return cleanedName.toUpperCase(Locale.ROOT);
    }

    private String defaultForwarderName(String forwarderCode) {
        if ("ET".equals(forwarderCode)) {
            return "易通";
        }
        if ("YITE".equals(forwarderCode)) {
            return "义特";
        }
        if ("QIKE".equals(forwarderCode)) {
            return "CHIC";
        }
        return forwarderCode;
    }

    private String defaultCategoryName(String cargoCategoryCode) {
        if (cargoCategoryCode == null) {
            return null;
        }
        if (cargoCategoryCode.length() == 1 && Character.isLetterOrDigit(cargoCategoryCode.charAt(0))) {
            return cargoCategoryCode + "类别运费";
        }
        return cargoCategoryCode;
    }

    private String categoryAssignmentIdempotencyKey(
            CostHistoryRow history,
            Long sourceHistoryId,
            Long operatorUserId
    ) {
        return String.join(":",
                "MANUAL_CATEGORY_ASSIGNMENT",
                String.valueOf(history.ownerUserId),
                String.valueOf(history.logicalStoreId),
                history.partnerSku,
                history.siteCode,
                history.forwarderCode,
                history.transportMode,
                history.feeType,
                defaultText(history.cargoCategoryCode, history.cargoCategoryName),
                String.valueOf(sourceHistoryId),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(operatorUserId)
        );
    }

    private String rateCardCategoryAssignmentIdempotencyKey(
            CostHistoryRow history,
            Long sourceRateCardId,
            Long operatorUserId
    ) {
        return String.join(":",
                "ROUTE_RATE_CARD_CATEGORY_ASSIGNMENT",
                String.valueOf(history.ownerUserId),
                String.valueOf(history.logicalStoreId),
                history.partnerSku,
                history.siteCode,
                history.forwarderCode,
                history.transportMode,
                history.feeType,
                defaultText(history.cargoCategoryCode, history.cargoCategoryName),
                String.valueOf(sourceRateCardId),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(operatorUserId)
        );
    }

    private String categoryAssignmentEvidenceJson(
            String storeCode,
            String partnerSku,
            String siteCode,
            String forwarderCode,
            String transportMode,
            String cargoCategoryCode,
            String cargoCategoryName,
            Long sourceCurrentCostId,
            Long sourceHistoryId,
            String remark
    ) {
        return "{"
                + "\"source\":\"MANUAL_CATEGORY_ASSIGNMENT\","
                + "\"storeCode\":\"" + jsonEscape(storeCode) + "\","
                + "\"partnerSku\":\"" + jsonEscape(partnerSku) + "\","
                + "\"siteCode\":\"" + jsonEscape(siteCode) + "\","
                + "\"forwarderCode\":\"" + jsonEscape(forwarderCode) + "\","
                + "\"transportMode\":\"" + jsonEscape(transportMode) + "\","
                + "\"cargoCategoryCode\":\"" + jsonEscape(cargoCategoryCode) + "\","
                + "\"cargoCategoryName\":\"" + jsonEscape(cargoCategoryName) + "\","
                + "\"sourceCurrentCostId\":\"" + (sourceCurrentCostId == null ? "" : sourceCurrentCostId) + "\","
                + "\"sourceHistoryId\":\"" + (sourceHistoryId == null ? "" : sourceHistoryId) + "\","
                + "\"remark\":\"" + jsonEscape(remark) + "\""
                + "}";
    }

    private String rateCardCategoryAssignmentEvidenceJson(
            String storeCode,
            String partnerSku,
            String siteCode,
            String forwarderCode,
            String transportMode,
            String feeType,
            String cargoCategoryCode,
            String cargoCategoryName,
            String chargeUnit,
            BigDecimal unitCostCny,
            Long sourceRateCardId,
            String sourceRateCardType,
            String sourceReference,
            String remark
    ) {
        return "{"
                + "\"source\":\"ROUTE_RATE_CARD_CATEGORY_ASSIGNMENT\","
                + "\"storeCode\":\"" + jsonEscape(storeCode) + "\","
                + "\"partnerSku\":\"" + jsonEscape(partnerSku) + "\","
                + "\"siteCode\":\"" + jsonEscape(siteCode) + "\","
                + "\"forwarderCode\":\"" + jsonEscape(forwarderCode) + "\","
                + "\"transportMode\":\"" + jsonEscape(transportMode) + "\","
                + "\"feeType\":\"" + jsonEscape(feeType) + "\","
                + "\"cargoCategoryCode\":\"" + jsonEscape(cargoCategoryCode) + "\","
                + "\"cargoCategoryName\":\"" + jsonEscape(cargoCategoryName) + "\","
                + "\"chargeUnit\":\"" + jsonEscape(chargeUnit) + "\","
                + "\"unitCostCny\":\"" + (unitCostCny == null ? "" : unitCostCny.toPlainString()) + "\","
                + "\"sourceRateCardId\":\"" + (sourceRateCardId == null ? "" : sourceRateCardId) + "\","
                + "\"sourceRateCardType\":\"" + jsonEscape(sourceRateCardType) + "\","
                + "\"sourceReference\":\"" + jsonEscape(sourceReference) + "\","
                + "\"remark\":\"" + jsonEscape(remark) + "\""
                + "}";
    }

    private String manualEvidenceJson(
            String storeCode,
            String partnerSku,
            String siteCode,
            String forwarderCode,
            String transportMode,
            String cargoCategoryCode,
            String cargoCategoryName,
            String chargeUnit,
            BigDecimal unitCostCny,
            String remark
    ) {
        return "{"
                + "\"source\":\"MANUAL_CURRENT_QUOTE\","
                + "\"storeCode\":\"" + jsonEscape(storeCode) + "\","
                + "\"partnerSku\":\"" + jsonEscape(partnerSku) + "\","
                + "\"siteCode\":\"" + jsonEscape(siteCode) + "\","
                + "\"forwarderCode\":\"" + jsonEscape(forwarderCode) + "\","
                + "\"transportMode\":\"" + jsonEscape(transportMode) + "\","
                + "\"cargoCategoryCode\":\"" + jsonEscape(cargoCategoryCode) + "\","
                + "\"cargoCategoryName\":\"" + jsonEscape(cargoCategoryName) + "\","
                + "\"chargeUnit\":\"" + jsonEscape(chargeUnit) + "\","
                + "\"unitCostCny\":\"" + unitCostCny.toPlainString() + "\","
                + "\"remark\":\"" + jsonEscape(remark) + "\""
                + "}";
    }

    private String rateCardEvidenceJson(RateCardRow row) {
        return "{"
                + "\"source\":\"" + jsonEscape(row.sourceType) + "\","
                + "\"siteCode\":\"" + jsonEscape(row.siteCode) + "\","
                + "\"forwarderCode\":\"" + jsonEscape(row.forwarderCode) + "\","
                + "\"transportMode\":\"" + jsonEscape(row.transportMode) + "\","
                + "\"feeType\":\"" + jsonEscape(row.feeType) + "\","
                + "\"cargoCategoryCode\":\"" + jsonEscape(row.cargoCategoryCode) + "\","
                + "\"cargoCategoryName\":\"" + jsonEscape(row.cargoCategoryName) + "\","
                + "\"chargeUnit\":\"" + jsonEscape(row.chargeUnit) + "\","
                + "\"unitCostCny\":\"" + row.unitCostCny.toPlainString() + "\","
                + "\"sourceReference\":\"" + jsonEscape(row.sourceReference) + "\","
                + "\"remark\":\"" + jsonEscape(row.remark) + "\""
                + "}";
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private int normalizeReadLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_READ_LIMIT;
        }
        return Math.min(limit, MAX_READ_LIMIT);
    }
}
