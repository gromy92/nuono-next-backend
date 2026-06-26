package com.nuono.next.intransit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nuono.next.infrastructure.mapper.InTransitFreightCostMapper;
import com.nuono.next.infrastructure.mapper.InTransitGoodsMapper;
import com.nuono.next.intransit.InTransitBatchRecords.BatchRow;
import com.nuono.next.intransit.InTransitBatchRecords.BatchView;
import com.nuono.next.intransit.InTransitBatchRecords.PackageRow;
import com.nuono.next.intransit.InTransitFreightCostCommands.ActualFreightBillCommand;
import com.nuono.next.intransit.InTransitFreightCostCommands.ActualFreightComponentCommand;
import com.nuono.next.intransit.InTransitFreightCostCommands.ActualFreightSyncCommand;
import com.nuono.next.intransit.InTransitFreightCostCommands.SaveEstimateComponentCommand;
import com.nuono.next.intransit.InTransitFreightCostCommands.SaveEstimateSnapshotCommand;
import com.nuono.next.intransit.InTransitFreightCostCommands.SaveRateCardRuleCommand;
import com.nuono.next.intransit.InTransitFreightCostCommands.SaveRateCardVersionCommand;
import com.nuono.next.intransit.InTransitFreightCostRecords.ActualFreightBillRow;
import com.nuono.next.intransit.InTransitFreightCostRecords.ActualFreightComponentRow;
import com.nuono.next.intransit.InTransitFreightCostRecords.ActualFreightSyncView;
import com.nuono.next.intransit.InTransitFreightCostRecords.BatchFreightCostView;
import com.nuono.next.intransit.InTransitFreightCostRecords.EstimateComponentRow;
import com.nuono.next.intransit.InTransitFreightCostRecords.EstimateMatchRow;
import com.nuono.next.intransit.InTransitFreightCostRecords.EstimateSnapshotRow;
import com.nuono.next.intransit.InTransitFreightCostRecords.EstimateSnapshotView;
import com.nuono.next.intransit.InTransitFreightCostRecords.ForwarderFreightComparisonView;
import com.nuono.next.intransit.InTransitFreightCostRecords.FreightStatisticsView;
import com.nuono.next.intransit.InTransitFreightCostRecords.RateCardRuleRow;
import com.nuono.next.intransit.InTransitFreightCostRecords.RateCardVersionRow;
import com.nuono.next.intransit.InTransitFreightCostRecords.RateCardVersionView;
import com.nuono.next.intransit.InTransitFreightCostRecords.SkuFreightCostHistoryView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InTransitFreightCostService {

    private static final Set<String> SUPPORTED_SOURCE_SYSTEMS = Set.of("CHIC", "ET", "YITONG", "YITE");

    private final InTransitGoodsMapper goodsMapper;
    private final InTransitFreightCostMapper freightCostMapper;
    private final InTransitBatchService batchService;
    private final InTransitGoodsAccessScopeService accessScopeService;
    private final ObjectMapper objectMapper;

    public InTransitFreightCostService(
            InTransitGoodsMapper goodsMapper,
            InTransitFreightCostMapper freightCostMapper,
            InTransitBatchService batchService,
            InTransitGoodsAccessScopeService accessScopeService,
            ObjectMapper objectMapper
    ) {
        this.goodsMapper = goodsMapper;
        this.freightCostMapper = freightCostMapper;
        this.batchService = batchService;
        this.accessScopeService = accessScopeService;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper.copy();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Transactional
    public ActualFreightSyncView syncActualCosts(ActualFreightSyncCommand command) {
        ActualFreightSyncCommand resolved = command == null ? new ActualFreightSyncCommand() : command;
        Long ownerUserId = requireOwnerUserId(resolved.getOwnerUserId());
        Long operatorUserId = resolved.getOperatorUserId();
        String sourceSystem = normalizeCode(resolved.getSourceSystem());
        if (!SUPPORTED_SOURCE_SYSTEMS.contains(sourceSystem)) {
            throw new IllegalArgumentException("实际运费插件来源系统只支持 CHIC、ET、YITONG、YITE。");
        }
        if (resolved.getBills().isEmpty()) {
            throw new IllegalArgumentException("实际运费同步账单不能为空。");
        }

        ActualFreightSyncView view = new ActualFreightSyncView();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int componentCount = 0;
        Set<Long> touchedBatchIds = new HashSet<>();
        for (ActualFreightBillCommand bill : resolved.getBills()) {
            String batchReferenceNo = clean(bill.getBatchReferenceNo());
            String billNo = clean(bill.getBillNo());
            if (!StringUtils.hasText(batchReferenceNo)) {
                throw new IllegalArgumentException("实际运费账单批次号不能为空。");
            }
            if (!StringUtils.hasText(billNo)) {
                throw new IllegalArgumentException("实际运费账单号不能为空。");
            }
            BatchRow batch = goodsMapper.selectBatchByReferenceNo(ownerUserId, batchReferenceNo);
            if (batch == null) {
                throw new IllegalArgumentException("实际运费账单找不到本地在途批次：" + batchReferenceNo);
            }
            BatchView batchView = batchService.getBatch(ownerUserId, batch.getId());
            accessScopeService.requireBatchAccess(resolved.getAccessContext(), batchView);

            ActualFreightBillRow existing = freightCostMapper.selectActualBillBySource(
                    ownerUserId,
                    sourceSystem,
                    billNo,
                    batch.getId()
            );
            ActualFreightBillRow billRow = toBillRow(resolved, bill, batch, sourceSystem, existing);
            if (existing == null) {
                billRow.setId(freightCostMapper.nextActualBillId());
                freightCostMapper.insertActualBill(billRow);
            } else {
                freightCostMapper.updateActualBill(billRow);
                freightCostMapper.softDeleteActualComponents(ownerUserId, billRow.getId(), operatorUserId);
            }
            totalAmount = totalAmount.add(zeroIfNull(billRow.getCnyTotalAmount()));

            for (ActualFreightComponentCommand component : bill.getComponents()) {
                ActualFreightComponentRow componentRow = toComponentRow(
                        resolved,
                        component,
                        batch,
                        billRow.getId()
                );
                freightCostMapper.insertActualComponent(componentRow);
                componentCount += 1;
            }
            touchedBatchIds.add(batch.getId());
        }
        for (Long batchId : touchedBatchIds) {
            recalculateEstimateMatch(ownerUserId, batchId, operatorUserId);
        }
        view.setBillCount(resolved.getBills().size());
        view.setComponentCount(componentCount);
        view.setTotalAmountCny(totalAmount);
        return view;
    }

    public BatchFreightCostView batchActualCosts(Long ownerUserId, Long batchId) {
        requireOwnerUserId(ownerUserId);
        if (batchId == null) {
            throw new IllegalArgumentException("在途批次 ID 不能为空。");
        }
        BatchFreightCostView view = new BatchFreightCostView();
        view.setBills(freightCostMapper.listActualBillsByBatch(ownerUserId, batchId));
        view.setComponents(freightCostMapper.listActualComponentsByBatch(ownerUserId, batchId));
        return view;
    }

    public FreightStatisticsView statistics(Long ownerUserId, java.time.LocalDateTime fromInclusive, java.time.LocalDateTime toExclusive, Long standardForwarderId) {
        requireOwnerUserId(ownerUserId);
        FreightStatisticsView view = new FreightStatisticsView();
        view.setItems(freightCostMapper.statistics(ownerUserId, fromInclusive, toExclusive, standardForwarderId));
        return view;
    }

    public SkuFreightCostHistoryView skuHistory(
            Long ownerUserId,
            String psku,
            String targetSiteCode,
            java.time.LocalDateTime fromInclusive,
            java.time.LocalDateTime toExclusive
    ) {
        requireOwnerUserId(ownerUserId);
        String resolvedPsku = clean(psku);
        String resolvedSite = clean(targetSiteCode);
        if (!StringUtils.hasText(resolvedPsku)) {
            throw new IllegalArgumentException("PSKU 不能为空。");
        }
        if (!StringUtils.hasText(resolvedSite)) {
            throw new IllegalArgumentException("站点不能为空。");
        }
        SkuFreightCostHistoryView view = new SkuFreightCostHistoryView();
        view.setItems(freightCostMapper.listSkuSiteActualCosts(
                ownerUserId,
                resolvedPsku,
                resolvedSite,
                fromInclusive,
                toExclusive
        ));
        return view;
    }

    public ForwarderFreightComparisonView forwarderComparison(
            Long ownerUserId,
            String psku,
            String targetSiteCode,
            String transportMode,
            String destinationCode
    ) {
        requireOwnerUserId(ownerUserId);
        String resolvedPsku = clean(psku);
        String resolvedSite = clean(targetSiteCode);
        if (!StringUtils.hasText(resolvedPsku)) {
            throw new IllegalArgumentException("PSKU 不能为空。");
        }
        if (!StringUtils.hasText(resolvedSite)) {
            throw new IllegalArgumentException("站点不能为空。");
        }
        ForwarderFreightComparisonView view = new ForwarderFreightComparisonView();
        view.setItems(freightCostMapper.compareForwardersForSkuSite(
                ownerUserId,
                resolvedPsku,
                resolvedSite,
                clean(transportMode),
                clean(destinationCode)
        ));
        return view;
    }

    @Transactional
    public RateCardVersionView saveRateCardVersion(SaveRateCardVersionCommand command) {
        SaveRateCardVersionCommand resolved = command == null ? new SaveRateCardVersionCommand() : command;
        Long ownerUserId = requireOwnerUserId(resolved.getOwnerUserId());
        Long operatorUserId = resolved.getOperatorUserId();
        if (resolved.getStandardForwarderId() == null || resolved.getStandardForwarderId() <= 0) {
            throw new IllegalArgumentException("货代不能为空。");
        }
        if (resolved.getEffectiveFrom() == null) {
            throw new IllegalArgumentException("价格版本生效时间不能为空。");
        }
        if (resolved.getRules().isEmpty()) {
            throw new IllegalArgumentException("价格版本规则不能为空。");
        }

        RateCardVersionRow version = new RateCardVersionRow();
        version.setId(freightCostMapper.nextRateCardVersionId());
        version.setOwnerUserId(ownerUserId);
        version.setStandardForwarderId(resolved.getStandardForwarderId());
        version.setForwarderCode(clean(resolved.getForwarderCode()));
        version.setForwarderName(clean(resolved.getForwarderName()));
        version.setVersionNo(clean(resolved.getVersionNo()));
        version.setVersionName(clean(resolved.getVersionName()));
        version.setTransportMode(normalizeCode(resolved.getTransportMode()));
        version.setDestinationCode(normalizeCode(resolved.getDestinationCode()));
        version.setTargetSiteCode(normalizeCode(resolved.getTargetSiteCode()));
        version.setCurrencyCode(defaultCurrency(resolved.getCurrencyCode()));
        version.setExchangeRateToCny(defaultRate(resolved.getExchangeRateToCny()));
        version.setEffectiveFrom(resolved.getEffectiveFrom());
        version.setEffectiveTo(resolved.getEffectiveTo());
        version.setVersionStatus(defaultText(clean(resolved.getVersionStatus()), "active"));
        version.setSourceType(defaultText(normalizeCode(resolved.getSourceType()), "INTERNAL"));
        version.setRawPayloadJson(toJson(resolved));
        version.setCreatedBy(operatorUserId);
        version.setUpdatedBy(operatorUserId);
        freightCostMapper.insertRateCardVersion(version);

        int ruleCount = 0;
        for (SaveRateCardRuleCommand rule : resolved.getRules()) {
            RateCardRuleRow row = new RateCardRuleRow();
            row.setId(freightCostMapper.nextRateCardRuleId());
            row.setOwnerUserId(ownerUserId);
            row.setRateCardVersionId(version.getId());
            row.setStandardFeeType(requireStandardFeeType(rule.getStandardFeeType()));
            row.setRawFeeName(clean(rule.getRawFeeName()));
            row.setProductCategory(clean(rule.getProductCategory()));
            row.setBoxCategory(clean(rule.getBoxCategory()));
            row.setChargeUnit(clean(rule.getChargeUnit()));
            row.setUnitPrice(rule.getUnitPrice());
            row.setMinChargeQuantity(rule.getMinChargeQuantity());
            row.setMinAmountCny(rule.getMinAmountCny());
            row.setCurrencyCode(defaultCurrency(rule.getCurrencyCode()));
            row.setExchangeRateToCny(defaultRate(rule.getExchangeRateToCny()));
            row.setRuleStatus(defaultText(clean(rule.getRuleStatus()), "active"));
            row.setFormulaJson(clean(rule.getFormulaJson()));
            row.setRawPayloadJson(toJson(rule));
            row.setCreatedBy(operatorUserId);
            row.setUpdatedBy(operatorUserId);
            freightCostMapper.insertRateCardRule(row);
            ruleCount += 1;
        }

        RateCardVersionView view = new RateCardVersionView();
        view.setRateCardVersionId(version.getId());
        view.setRuleCount(ruleCount);
        return view;
    }

    @Transactional
    public EstimateSnapshotView saveEstimateSnapshot(SaveEstimateSnapshotCommand command) {
        SaveEstimateSnapshotCommand resolved = command == null ? new SaveEstimateSnapshotCommand() : command;
        Long ownerUserId = requireOwnerUserId(resolved.getOwnerUserId());
        Long operatorUserId = resolved.getOperatorUserId();
        if (resolved.getBatchId() == null || resolved.getBatchId() <= 0) {
            throw new IllegalArgumentException("估算运费批次不能为空。");
        }
        String sourceEstimateType = requireCode(resolved.getSourceEstimateType(), "估算运费来源类型不能为空。");
        if (resolved.getStandardForwarderId() == null || resolved.getStandardForwarderId() <= 0) {
            throw new IllegalArgumentException("估算运费货代不能为空。");
        }
        if (resolved.getRateCardVersionId() == null || resolved.getRateCardVersionId() <= 0) {
            throw new IllegalArgumentException("估算运费价格版本不能为空。");
        }
        if (resolved.getComponents().isEmpty()) {
            throw new IllegalArgumentException("估算运费明细不能为空。");
        }

        freightCostMapper.softDeleteEstimateSnapshotBySource(
                ownerUserId,
                sourceEstimateType,
                resolved.getSourceEstimateId(),
                resolved.getSourceRecommendationId(),
                operatorUserId
        );

        EstimateSnapshotRow snapshot = new EstimateSnapshotRow();
        snapshot.setId(freightCostMapper.nextEstimateSnapshotId());
        snapshot.setOwnerUserId(ownerUserId);
        snapshot.setBatchId(resolved.getBatchId());
        snapshot.setSourceEstimateType(sourceEstimateType);
        snapshot.setSourceEstimateId(resolved.getSourceEstimateId());
        snapshot.setSourceEstimateNo(clean(resolved.getSourceEstimateNo()));
        snapshot.setSourceRecommendationId(resolved.getSourceRecommendationId());
        snapshot.setRateCardVersionId(resolved.getRateCardVersionId());
        snapshot.setStandardForwarderId(resolved.getStandardForwarderId());
        snapshot.setForwarderCode(clean(resolved.getForwarderCode()));
        snapshot.setForwarderName(clean(resolved.getForwarderName()));
        snapshot.setTransportMode(normalizeCode(resolved.getTransportMode()));
        snapshot.setDestinationCode(normalizeCode(resolved.getDestinationCode()));
        snapshot.setTargetSiteCode(normalizeCode(resolved.getTargetSiteCode()));
        snapshot.setRecommended(Boolean.TRUE.equals(resolved.getRecommended()));
        snapshot.setEstimateStatus(defaultText(clean(resolved.getEstimateStatus()), "snapshot"));
        snapshot.setCurrencyCode(defaultCurrency(resolved.getCurrencyCode()));
        snapshot.setExchangeRateToCny(defaultRate(resolved.getExchangeRateToCny()));
        snapshot.setEstimatedTotalAmount(resolved.getEstimatedTotalAmount());
        snapshot.setEstimatedTotalCny(defaultCnyAmount(
                resolved.getEstimatedTotalCny(),
                resolved.getEstimatedTotalAmount(),
                snapshot.getExchangeRateToCny()
        ));
        snapshot.setGeneratedAt(resolved.getGeneratedAt());
        snapshot.setRawPayloadJson(toJson(resolved));
        snapshot.setCreatedBy(operatorUserId);
        snapshot.setUpdatedBy(operatorUserId);
        freightCostMapper.insertEstimateSnapshot(snapshot);

        int componentCount = 0;
        for (SaveEstimateComponentCommand component : resolved.getComponents()) {
            EstimateComponentRow row = new EstimateComponentRow();
            row.setId(freightCostMapper.nextEstimateComponentId());
            row.setOwnerUserId(ownerUserId);
            row.setEstimateSnapshotId(snapshot.getId());
            row.setTargetSiteCode(defaultText(normalizeCode(component.getTargetSiteCode()), snapshot.getTargetSiteCode()));
            row.setPsku(clean(component.getPsku()));
            row.setComponentType(requireStandardFeeType(component.getComponentType()));
            row.setRawFeeName(clean(component.getRawFeeName()));
            row.setQuantity(component.getQuantity());
            row.setChargeableWeightKg(component.getChargeableWeightKg());
            row.setChargeQuantity(component.getChargeQuantity());
            row.setChargeUnit(clean(component.getChargeUnit()));
            row.setUnitPrice(component.getUnitPrice());
            row.setCurrencyCode(defaultCurrency(component.getCurrencyCode()));
            row.setExchangeRateToCny(defaultRate(component.getExchangeRateToCny()));
            row.setEstimatedAmount(component.getEstimatedAmount());
            row.setEstimatedAmountCny(defaultCnyAmount(
                    component.getEstimatedAmountCny(),
                    component.getEstimatedAmount(),
                    row.getExchangeRateToCny()
            ));
            row.setRawPayloadJson(toJson(component));
            row.setCreatedBy(operatorUserId);
            row.setUpdatedBy(operatorUserId);
            freightCostMapper.insertEstimateComponent(row);
            componentCount += 1;
        }

        EstimateSnapshotView view = new EstimateSnapshotView();
        view.setEstimateSnapshotId(snapshot.getId());
        view.setComponentCount(componentCount);
        recalculateEstimateMatch(ownerUserId, snapshot.getBatchId(), operatorUserId);
        return view;
    }

    @Transactional
    public void recalculateEstimateMatch(Long ownerUserId, Long batchId, Long operatorUserId) {
        Long resolvedOwnerUserId = requireOwnerUserId(ownerUserId);
        if (batchId == null || batchId <= 0) {
            return;
        }
        BigDecimal actualTotalCny = freightCostMapper.sumActualBillByBatch(resolvedOwnerUserId, batchId);
        if (actualTotalCny == null) {
            return;
        }

        List<EstimateSnapshotRow> estimates = freightCostMapper.listRecommendedEstimatesByBatch(resolvedOwnerUserId, batchId);
        EstimateMatchRow row = new EstimateMatchRow();
        row.setId(freightCostMapper.nextEstimateMatchId());
        row.setOwnerUserId(resolvedOwnerUserId);
        row.setBatchId(batchId);
        row.setActualTotalCny(actualTotalCny);
        row.setMatchedAt(LocalDateTime.now());
        row.setCreatedBy(operatorUserId);
        row.setUpdatedBy(operatorUserId);

        if (estimates == null || estimates.isEmpty()) {
            row.setMatchStatus("unmatched");
            row.setReason("no_recommended_estimate");
        } else if (estimates.size() > 1) {
            row.setMatchStatus("ambiguous");
            row.setReason("multiple_recommended_estimates");
        } else {
            EstimateSnapshotRow estimate = estimates.get(0);
            BigDecimal estimatedTotalCny = estimate.getEstimatedTotalCny();
            row.setEstimateSnapshotId(estimate.getId());
            row.setMatchStatus("matched");
            row.setEstimatedTotalCny(estimatedTotalCny);
            if (estimatedTotalCny != null) {
                BigDecimal diffAmountCny = actualTotalCny.subtract(estimatedTotalCny);
                row.setDiffAmountCny(diffAmountCny);
                if (BigDecimal.ZERO.compareTo(estimatedTotalCny) != 0) {
                    row.setDiffRate(diffAmountCny.divide(estimatedTotalCny, 8, RoundingMode.HALF_UP));
                }
            }
        }

        freightCostMapper.softDeleteEstimateMatchesByBatch(resolvedOwnerUserId, batchId, operatorUserId);
        freightCostMapper.insertEstimateMatch(row);
    }

    private ActualFreightBillRow toBillRow(
            ActualFreightSyncCommand command,
            ActualFreightBillCommand bill,
            BatchRow batch,
            String sourceSystem,
            ActualFreightBillRow existing
    ) {
        ActualFreightBillRow row = existing == null ? new ActualFreightBillRow() : existing;
        row.setOwnerUserId(command.getOwnerUserId());
        row.setBatchId(batch.getId());
        row.setStandardForwarderId(batch.getStandardForwarderId());
        row.setForwarderCode(clean(batch.getStandardForwarderCode()));
        row.setForwarderName(clean(batch.getStandardForwarderName()));
        row.setTransportMode(clean(batch.getTransportMode()));
        row.setDestinationCode(clean(batch.getTargetStoreCode()));
        row.setTargetSiteCode(resolveTargetSiteCode(batch));
        row.setSourceType("PLUGIN_SYNC");
        row.setSourceSystem(sourceSystem);
        row.setBillNo(clean(bill.getBillNo()));
        row.setBillStatus(clean(bill.getBillStatus()));
        row.setBusinessOccurredAt(bill.getBusinessOccurredAt());
        row.setBillDate(bill.getBillDate());
        row.setPaidAt(bill.getPaidAt());
        row.setCurrencyCode(defaultCurrency(bill.getCurrencyCode()));
        row.setExchangeRateToCny(defaultRate(bill.getExchangeRateToCny()));
        row.setOriginalTotalAmount(bill.getOriginalTotalAmount());
        row.setCnyTotalAmount(defaultCnyAmount(bill.getCnyTotalAmount(), bill.getOriginalTotalAmount(), row.getExchangeRateToCny()));
        row.setFreightAmountCny(bill.getFreightAmountCny());
        row.setCustomsAmountCny(bill.getCustomsAmountCny());
        row.setStorageAmountCny(bill.getStorageAmountCny());
        row.setHandlingAmountCny(bill.getHandlingAmountCny());
        row.setDeliveryAmountCny(bill.getDeliveryAmountCny());
        row.setInterestAmountCny(bill.getInterestAmountCny());
        row.setPostedAmountCny(bill.getPostedAmountCny());
        row.setBalanceAmountCny(bill.getBalanceAmountCny());
        row.setRawPayloadJson(toJson(bill));
        row.setCreatedBy(existing == null ? command.getOperatorUserId() : row.getCreatedBy());
        row.setUpdatedBy(command.getOperatorUserId());
        return row;
    }

    private ActualFreightComponentRow toComponentRow(
            ActualFreightSyncCommand command,
            ActualFreightComponentCommand component,
            BatchRow batch,
            Long billId
    ) {
        PackageRow itemPackage = freightCostMapper.selectPackageByAnyBoxNo(
                command.getOwnerUserId(),
                batch.getId(),
                clean(component.getBoxNo()),
                clean(component.getExternalBoxNo())
        );
        ActualFreightComponentRow row = new ActualFreightComponentRow();
        row.setId(freightCostMapper.nextActualComponentId());
        row.setOwnerUserId(command.getOwnerUserId());
        row.setActualBillId(billId);
        row.setBatchId(batch.getId());
        row.setPackageId(itemPackage == null ? null : itemPackage.getId());
        row.setBoxNo(itemPackage == null ? clean(component.getBoxNo()) : itemPackage.getBoxNo());
        row.setExternalBoxNo(itemPackage == null ? clean(component.getExternalBoxNo()) : itemPackage.getExternalBoxNo());
        row.setPsku(clean(component.getPsku()));
        row.setTransportMode(clean(batch.getTransportMode()));
        row.setDestinationCode(clean(batch.getTargetStoreCode()));
        row.setTargetSiteCode(resolveTargetSiteCode(batch));
        row.setRawFeeName(clean(component.getRawFeeName()));
        row.setStandardFeeType(requireStandardFeeType(component.getStandardFeeType()));
        row.setChargeQuantity(component.getChargeQuantity());
        row.setChargeUnit(clean(component.getChargeUnit()));
        row.setUnitPrice(component.getUnitPrice());
        row.setCurrencyCode(defaultCurrency(component.getCurrencyCode()));
        row.setExchangeRateToCny(defaultRate(component.getExchangeRateToCny()));
        row.setOriginalAmount(component.getOriginalAmount());
        row.setCnyAmount(defaultCnyAmount(component.getCnyAmount(), component.getOriginalAmount(), row.getExchangeRateToCny()));
        row.setQuantity(component.getQuantity());
        row.setMeasuredWeightKg(component.getMeasuredWeightKg());
        row.setMeasuredVolumeCbm(component.getMeasuredVolumeCbm());
        row.setVolumeWeightKg(component.getVolumeWeightKg());
        row.setChargeableWeightKg(component.getChargeableWeightKg());
        row.setAllocationBasis(clean(component.getAllocationBasis()));
        row.setRawPayloadJson(toJson(component));
        row.setCreatedBy(command.getOperatorUserId());
        row.setUpdatedBy(command.getOperatorUserId());
        return row;
    }

    private Long requireOwnerUserId(Long ownerUserId) {
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("业务 ownerUserId 不能为空。");
        }
        return ownerUserId;
    }

    private String requireStandardFeeType(String value) {
        String cleaned = normalizeCode(value);
        if (!StringUtils.hasText(cleaned)) {
            throw new IllegalArgumentException("实际运费明细标准费用类型不能为空。");
        }
        return cleaned;
    }

    private String requireCode(String value, String message) {
        String cleaned = normalizeCode(value);
        if (!StringUtils.hasText(cleaned)) {
            throw new IllegalArgumentException(message);
        }
        return cleaned;
    }

    private String clean(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeCode(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.toUpperCase(Locale.ROOT);
    }

    private String resolveTargetSiteCode(BatchRow batch) {
        String siteCode = normalizeCode(batch.getTargetSiteCode());
        if (StringUtils.hasText(siteCode)) {
            return siteCode;
        }
        String targetStoreCode = normalizeCode(batch.getTargetStoreCode());
        if ("RUH".equals(targetStoreCode)) {
            return "SA";
        }
        if ("DB".equals(targetStoreCode) || "DXB".equals(targetStoreCode)) {
            return "AE";
        }
        return null;
    }

    private String defaultCurrency(String value) {
        String cleaned = normalizeCode(value);
        return cleaned == null ? "CNY" : cleaned;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private BigDecimal defaultRate(BigDecimal value) {
        return value == null || BigDecimal.ZERO.compareTo(value) == 0 ? BigDecimal.ONE : value;
    }

    private BigDecimal defaultCnyAmount(BigDecimal cnyAmount, BigDecimal originalAmount, BigDecimal exchangeRate) {
        if (cnyAmount != null) {
            return cnyAmount;
        }
        if (originalAmount == null) {
            return null;
        }
        return originalAmount.multiply(defaultRate(exchangeRate));
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("实际运费原始 payload 序列化失败。", exception);
        }
    }
}
