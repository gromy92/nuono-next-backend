package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.nuono.next.intransit.InTransitFreightCostRecords.EstimateComponentRow;
import com.nuono.next.intransit.InTransitFreightCostRecords.EstimateMatchRow;
import com.nuono.next.intransit.InTransitFreightCostRecords.EstimateSnapshotRow;
import com.nuono.next.intransit.InTransitFreightCostRecords.EstimateSnapshotView;
import com.nuono.next.intransit.InTransitFreightCostRecords.RateCardRuleRow;
import com.nuono.next.intransit.InTransitFreightCostRecords.RateCardVersionRow;
import com.nuono.next.intransit.InTransitFreightCostRecords.RateCardVersionView;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InTransitFreightCostServiceTest {

    @Mock
    private InTransitGoodsMapper goodsMapper;

    @Mock
    private InTransitFreightCostMapper freightCostMapper;

    @Mock
    private InTransitBatchService batchService;

    @Mock
    private InTransitGoodsAccessScopeService accessScopeService;

    private InTransitFreightCostService service;

    @BeforeEach
    void setUp() {
        service = new InTransitFreightCostService(
                goodsMapper,
                freightCostMapper,
                batchService,
                accessScopeService,
                new ObjectMapper()
        );
    }

    @Test
    void shouldCommitPluginActualFreightBillWithPackageMatchedComponent() {
        ActualFreightSyncCommand command = new ActualFreightSyncCommand();
        BusinessAccessContext context = context();
        command.setOwnerUserId(307L);
        command.setOperatorUserId(90001L);
        command.setAccessContext(context);
        command.setSourceSystem("YITONG");
        ActualFreightBillCommand bill = new ActualFreightBillCommand();
        bill.setBatchReferenceNo("F2501135117714");
        bill.setBillNo("AR25012055917");
        bill.setBillStatus("已支付");
        bill.setBusinessOccurredAt(LocalDateTime.of(2025, 1, 20, 10, 32));
        bill.setCurrencyCode("CNY");
        bill.setExchangeRateToCny(BigDecimal.ONE);
        bill.setOriginalTotalAmount(new BigDecimal("691.47"));
        bill.setCnyTotalAmount(new BigDecimal("691.47"));
        bill.setFreightAmountCny(new BigDecimal("691.47"));

        ActualFreightComponentCommand component = new ActualFreightComponentCommand();
        component.setExternalBoxNo("X25011446217");
        component.setPsku("SGGRB148");
        component.setRawFeeName("B类别运费");
        component.setStandardFeeType("HEADHAUL");
        component.setChargeQuantity(new BigDecimal("0.061317"));
        component.setChargeUnit("CBM");
        component.setUnitPrice(new BigDecimal("1400"));
        component.setCurrencyCode("CNY");
        component.setExchangeRateToCny(BigDecimal.ONE);
        component.setOriginalAmount(new BigDecimal("85.84"));
        component.setCnyAmount(new BigDecimal("85.84"));
        component.setMeasuredWeightKg(new BigDecimal("15.65"));
        component.setMeasuredVolumeCbm(new BigDecimal("0.061317"));
        component.setChargeableWeightKg(new BigDecimal("0.061317"));
        bill.setComponents(List.of(component));
        command.setBills(List.of(bill));

        BatchRow batch = new BatchRow();
        batch.setId(53069L);
        batch.setOwnerUserId(307L);
        batch.setBatchReferenceNo("F2501135117714");
        batch.setStandardForwarderId(30700002L);
        batch.setStandardForwarderCode("YITONG");
        batch.setStandardForwarderName("易通");
        batch.setTransportMode("SEA");
        batch.setTargetStoreCode("RUH");
        batch.setTargetSiteCode("SA");
        when(goodsMapper.selectBatchByReferenceNo(307L, "F2501135117714")).thenReturn(batch);
        BatchView batchView = BatchView.from(batch);
        when(batchService.getBatch(307L, 53069L)).thenReturn(batchView);
        PackageRow itemPackage = new PackageRow();
        itemPackage.setId(58001L);
        itemPackage.setBatchId(53069L);
        itemPackage.setBoxNo("5-1");
        itemPackage.setExternalBoxNo("X25011446217");
        when(freightCostMapper.selectPackageByAnyBoxNo(307L, 53069L, null, "X25011446217"))
                .thenReturn(itemPackage);
        when(freightCostMapper.selectActualBillBySource(307L, "YITONG", "AR25012055917", 53069L))
                .thenReturn(null);
        when(freightCostMapper.nextActualBillId()).thenReturn(59001L);
        when(freightCostMapper.nextActualComponentId()).thenReturn(60001L);

        ActualFreightSyncView result = service.syncActualCosts(command);

        assertEquals(1, result.getBillCount());
        assertEquals(1, result.getComponentCount());
        assertEquals(new BigDecimal("691.47"), result.getTotalAmountCny());
        verify(accessScopeService).requireBatchAccess(same(context), same(batchView));

        ArgumentCaptor<ActualFreightBillRow> billCaptor = ArgumentCaptor.forClass(ActualFreightBillRow.class);
        verify(freightCostMapper).insertActualBill(billCaptor.capture());
        assertEquals(59001L, billCaptor.getValue().getId());
        assertEquals(53069L, billCaptor.getValue().getBatchId());
        assertEquals(30700002L, billCaptor.getValue().getStandardForwarderId());
        assertEquals("SEA", billCaptor.getValue().getTransportMode());
        assertEquals("RUH", billCaptor.getValue().getDestinationCode());
        assertEquals("SA", billCaptor.getValue().getTargetSiteCode());
        assertEquals("PLUGIN_SYNC", billCaptor.getValue().getSourceType());

        ArgumentCaptor<ActualFreightComponentRow> componentCaptor = ArgumentCaptor.forClass(ActualFreightComponentRow.class);
        verify(freightCostMapper).insertActualComponent(componentCaptor.capture());
        assertEquals(60001L, componentCaptor.getValue().getId());
        assertEquals(59001L, componentCaptor.getValue().getActualBillId());
        assertEquals(58001L, componentCaptor.getValue().getPackageId());
        assertEquals("SGGRB148", componentCaptor.getValue().getPsku());
        assertEquals("5-1", componentCaptor.getValue().getBoxNo());
        assertEquals("X25011446217", componentCaptor.getValue().getExternalBoxNo());
        assertEquals("SEA", componentCaptor.getValue().getTransportMode());
        assertEquals("RUH", componentCaptor.getValue().getDestinationCode());
        assertEquals("SA", componentCaptor.getValue().getTargetSiteCode());
        assertEquals(new BigDecimal("0.061317"), componentCaptor.getValue().getChargeableWeightKg());
    }

    @Test
    void shouldOverwriteExistingPluginActualFreightBillOnRepeatedSync() {
        ActualFreightSyncCommand command = new ActualFreightSyncCommand();
        BusinessAccessContext context = context();
        command.setOwnerUserId(307L);
        command.setOperatorUserId(90002L);
        command.setAccessContext(context);
        command.setSourceSystem("yitong");

        ActualFreightBillCommand bill = new ActualFreightBillCommand();
        bill.setBatchReferenceNo("F2501135117714");
        bill.setBillNo("AR25012055917");
        bill.setCnyTotalAmount(new BigDecimal("700.00"));
        bill.setFreightAmountCny(new BigDecimal("700.00"));

        ActualFreightComponentCommand component = new ActualFreightComponentCommand();
        component.setExternalBoxNo("X25011446217");
        component.setStandardFeeType("headhaul");
        component.setOriginalAmount(new BigDecimal("700.00"));
        component.setCnyAmount(new BigDecimal("700.00"));
        component.setChargeableWeightKg(new BigDecimal("0.500000"));
        bill.setComponents(List.of(component));
        command.setBills(List.of(bill));

        BatchRow batch = new BatchRow();
        batch.setId(53069L);
        batch.setOwnerUserId(307L);
        batch.setBatchReferenceNo("F2501135117714");
        batch.setStandardForwarderId(30700002L);
        batch.setStandardForwarderCode("YITONG");
        batch.setStandardForwarderName("易通");
        when(goodsMapper.selectBatchByReferenceNo(307L, "F2501135117714")).thenReturn(batch);
        BatchView batchView = BatchView.from(batch);
        when(batchService.getBatch(307L, 53069L)).thenReturn(batchView);
        ActualFreightBillRow existing = new ActualFreightBillRow();
        existing.setId(59008L);
        existing.setCreatedBy(80001L);
        when(freightCostMapper.selectActualBillBySource(307L, "YITONG", "AR25012055917", 53069L))
                .thenReturn(existing);
        when(freightCostMapper.nextActualComponentId()).thenReturn(60008L);

        ActualFreightSyncView result = service.syncActualCosts(command);

        assertEquals(1, result.getBillCount());
        assertEquals(1, result.getComponentCount());
        assertEquals(new BigDecimal("700.00"), result.getTotalAmountCny());
        verify(accessScopeService).requireBatchAccess(same(context), same(batchView));

        ArgumentCaptor<ActualFreightBillRow> billCaptor = ArgumentCaptor.forClass(ActualFreightBillRow.class);
        verify(freightCostMapper).updateActualBill(billCaptor.capture());
        assertEquals(59008L, billCaptor.getValue().getId());
        assertEquals(80001L, billCaptor.getValue().getCreatedBy());
        assertEquals(90002L, billCaptor.getValue().getUpdatedBy());
        assertEquals("YITONG", billCaptor.getValue().getSourceSystem());
        assertEquals(new BigDecimal("700.00"), billCaptor.getValue().getCnyTotalAmount());
        verify(freightCostMapper).softDeleteActualComponents(307L, 59008L, 90002L);

        ArgumentCaptor<ActualFreightComponentRow> componentCaptor = ArgumentCaptor.forClass(ActualFreightComponentRow.class);
        verify(freightCostMapper).insertActualComponent(componentCaptor.capture());
        assertEquals(60008L, componentCaptor.getValue().getId());
        assertEquals(59008L, componentCaptor.getValue().getActualBillId());
        assertEquals("HEADHAUL", componentCaptor.getValue().getStandardFeeType());
    }

    @Test
    void shouldSaveRateCardVersionWithRules() {
        SaveRateCardVersionCommand command = new SaveRateCardVersionCommand();
        command.setOwnerUserId(307L);
        command.setOperatorUserId(90001L);
        command.setStandardForwarderId(30700002L);
        command.setForwarderCode("YITONG");
        command.setForwarderName("易通");
        command.setTransportMode("SEA");
        command.setDestinationCode("RUH");
        command.setTargetSiteCode("SA");
        command.setVersionNo("YITONG-SEA-RUH-2026-06");
        command.setVersionName("易通 2026-06 海运利雅得");
        command.setEffectiveFrom(LocalDateTime.of(2026, 6, 1, 0, 0));

        SaveRateCardRuleCommand rule = new SaveRateCardRuleCommand();
        rule.setStandardFeeType("headhaul");
        rule.setRawFeeName("B类别运费");
        rule.setProductCategory("B类别");
        rule.setChargeUnit("CBM");
        rule.setUnitPrice(new BigDecimal("1400.00"));
        rule.setMinChargeQuantity(new BigDecimal("0.2"));
        command.setRules(List.of(rule));

        when(freightCostMapper.nextRateCardVersionId()).thenReturn(64001L);
        when(freightCostMapper.nextRateCardRuleId()).thenReturn(65001L);

        RateCardVersionView result = service.saveRateCardVersion(command);

        assertEquals(64001L, result.getRateCardVersionId());
        assertEquals(1, result.getRuleCount());

        ArgumentCaptor<RateCardVersionRow> versionCaptor = ArgumentCaptor.forClass(RateCardVersionRow.class);
        verify(freightCostMapper).insertRateCardVersion(versionCaptor.capture());
        assertEquals(64001L, versionCaptor.getValue().getId());
        assertEquals(307L, versionCaptor.getValue().getOwnerUserId());
        assertEquals(30700002L, versionCaptor.getValue().getStandardForwarderId());
        assertEquals("YITONG", versionCaptor.getValue().getForwarderCode());
        assertEquals("SEA", versionCaptor.getValue().getTransportMode());
        assertEquals("RUH", versionCaptor.getValue().getDestinationCode());
        assertEquals("SA", versionCaptor.getValue().getTargetSiteCode());
        assertEquals("active", versionCaptor.getValue().getVersionStatus());

        ArgumentCaptor<RateCardRuleRow> ruleCaptor = ArgumentCaptor.forClass(RateCardRuleRow.class);
        verify(freightCostMapper).insertRateCardRule(ruleCaptor.capture());
        assertEquals(65001L, ruleCaptor.getValue().getId());
        assertEquals(64001L, ruleCaptor.getValue().getRateCardVersionId());
        assertEquals("HEADHAUL", ruleCaptor.getValue().getStandardFeeType());
        assertEquals("B类别运费", ruleCaptor.getValue().getRawFeeName());
        assertEquals(new BigDecimal("1400.00"), ruleCaptor.getValue().getUnitPrice());
        assertEquals(new BigDecimal("0.2"), ruleCaptor.getValue().getMinChargeQuantity());
        assertEquals("active", ruleCaptor.getValue().getRuleStatus());
    }

    @Test
    void shouldSaveEstimateSnapshotWithRateCardVersionAndComponents() {
        SaveEstimateSnapshotCommand command = new SaveEstimateSnapshotCommand();
        command.setOwnerUserId(307L);
        command.setOperatorUserId(90001L);
        command.setBatchId(53069L);
        command.setSourceEstimateType("LOGISTICS_PLAN");
        command.setSourceEstimateId(88001L);
        command.setSourceEstimateNo("PLAN-88001");
        command.setSourceRecommendationId(88002L);
        command.setRecommended(true);
        command.setStandardForwarderId(30700002L);
        command.setForwarderCode("YITONG");
        command.setForwarderName("易通");
        command.setTransportMode("SEA");
        command.setDestinationCode("RUH");
        command.setTargetSiteCode("SA");
        command.setRateCardVersionId(64001L);
        command.setEstimatedTotalCny(new BigDecimal("691.47"));
        command.setGeneratedAt(LocalDateTime.of(2026, 6, 10, 16, 30));

        SaveEstimateComponentCommand component = new SaveEstimateComponentCommand();
        component.setPsku("SGGRB148");
        component.setTargetSiteCode("SA");
        component.setComponentType("headhaul");
        component.setChargeQuantity(new BigDecimal("0.493907"));
        component.setChargeUnit("CBM");
        component.setUnitPrice(new BigDecimal("1400.00"));
        component.setEstimatedAmountCny(new BigDecimal("691.47"));
        command.setComponents(List.of(component));

        when(freightCostMapper.nextEstimateSnapshotId()).thenReturn(61001L);
        when(freightCostMapper.nextEstimateComponentId()).thenReturn(62001L);

        EstimateSnapshotView result = service.saveEstimateSnapshot(command);

        assertEquals(61001L, result.getEstimateSnapshotId());
        assertEquals(1, result.getComponentCount());
        verify(freightCostMapper).softDeleteEstimateSnapshotBySource(307L, "LOGISTICS_PLAN", 88001L, 88002L, 90001L);

        ArgumentCaptor<EstimateSnapshotRow> snapshotCaptor = ArgumentCaptor.forClass(EstimateSnapshotRow.class);
        verify(freightCostMapper).insertEstimateSnapshot(snapshotCaptor.capture());
        assertEquals(61001L, snapshotCaptor.getValue().getId());
        assertEquals(64001L, snapshotCaptor.getValue().getRateCardVersionId());
        assertEquals("SEA", snapshotCaptor.getValue().getTransportMode());
        assertEquals("RUH", snapshotCaptor.getValue().getDestinationCode());
        assertEquals("SA", snapshotCaptor.getValue().getTargetSiteCode());
        assertEquals(Boolean.TRUE, snapshotCaptor.getValue().getRecommended());

        ArgumentCaptor<EstimateComponentRow> componentCaptor = ArgumentCaptor.forClass(EstimateComponentRow.class);
        verify(freightCostMapper).insertEstimateComponent(componentCaptor.capture());
        assertEquals(62001L, componentCaptor.getValue().getId());
        assertEquals(61001L, componentCaptor.getValue().getEstimateSnapshotId());
        assertEquals("SA", componentCaptor.getValue().getTargetSiteCode());
        assertEquals("SGGRB148", componentCaptor.getValue().getPsku());
        assertEquals("HEADHAUL", componentCaptor.getValue().getComponentType());
        assertEquals(new BigDecimal("0.493907"), componentCaptor.getValue().getChargeQuantity());
    }

    @Test
    void shouldCreateMatchedEstimateComparison() {
        EstimateSnapshotRow estimate = new EstimateSnapshotRow();
        estimate.setId(61001L);
        estimate.setEstimatedTotalCny(new BigDecimal("691.47"));
        when(freightCostMapper.sumActualBillByBatch(307L, 53069L)).thenReturn(new BigDecimal("700.00"));
        when(freightCostMapper.listRecommendedEstimatesByBatch(307L, 53069L)).thenReturn(List.of(estimate));
        when(freightCostMapper.nextEstimateMatchId()).thenReturn(63001L);

        service.recalculateEstimateMatch(307L, 53069L, 90001L);

        verify(freightCostMapper).softDeleteEstimateMatchesByBatch(307L, 53069L, 90001L);
        ArgumentCaptor<EstimateMatchRow> matchCaptor = ArgumentCaptor.forClass(EstimateMatchRow.class);
        verify(freightCostMapper).insertEstimateMatch(matchCaptor.capture());
        assertEquals(63001L, matchCaptor.getValue().getId());
        assertEquals(307L, matchCaptor.getValue().getOwnerUserId());
        assertEquals(53069L, matchCaptor.getValue().getBatchId());
        assertEquals(61001L, matchCaptor.getValue().getEstimateSnapshotId());
        assertEquals("matched", matchCaptor.getValue().getMatchStatus());
        assertEquals(new BigDecimal("700.00"), matchCaptor.getValue().getActualTotalCny());
        assertEquals(new BigDecimal("691.47"), matchCaptor.getValue().getEstimatedTotalCny());
        assertEquals(new BigDecimal("8.53"), matchCaptor.getValue().getDiffAmountCny());
        assertEquals(new BigDecimal("0.01233604"), matchCaptor.getValue().getDiffRate());
    }

    @Test
    void shouldCreateUnmatchedEstimateComparisonWhenRecommendedEstimateMissing() {
        when(freightCostMapper.sumActualBillByBatch(307L, 53069L)).thenReturn(new BigDecimal("700.00"));
        when(freightCostMapper.listRecommendedEstimatesByBatch(307L, 53069L)).thenReturn(List.of());
        when(freightCostMapper.nextEstimateMatchId()).thenReturn(63002L);

        service.recalculateEstimateMatch(307L, 53069L, 90001L);

        ArgumentCaptor<EstimateMatchRow> matchCaptor = ArgumentCaptor.forClass(EstimateMatchRow.class);
        verify(freightCostMapper).insertEstimateMatch(matchCaptor.capture());
        assertEquals("unmatched", matchCaptor.getValue().getMatchStatus());
        assertEquals(new BigDecimal("700.00"), matchCaptor.getValue().getActualTotalCny());
        assertEquals(null, matchCaptor.getValue().getEstimatedTotalCny());
        assertEquals("no_recommended_estimate", matchCaptor.getValue().getReason());
    }

    @Test
    void shouldCreateAmbiguousEstimateComparisonWhenMultipleRecommendedEstimatesExist() {
        EstimateSnapshotRow first = new EstimateSnapshotRow();
        first.setId(61001L);
        first.setEstimatedTotalCny(new BigDecimal("691.47"));
        EstimateSnapshotRow second = new EstimateSnapshotRow();
        second.setId(61002L);
        second.setEstimatedTotalCny(new BigDecimal("710.00"));
        when(freightCostMapper.sumActualBillByBatch(307L, 53069L)).thenReturn(new BigDecimal("700.00"));
        when(freightCostMapper.listRecommendedEstimatesByBatch(307L, 53069L)).thenReturn(List.of(first, second));
        when(freightCostMapper.nextEstimateMatchId()).thenReturn(63003L);

        service.recalculateEstimateMatch(307L, 53069L, 90001L);

        ArgumentCaptor<EstimateMatchRow> matchCaptor = ArgumentCaptor.forClass(EstimateMatchRow.class);
        verify(freightCostMapper).insertEstimateMatch(matchCaptor.capture());
        assertEquals("ambiguous", matchCaptor.getValue().getMatchStatus());
        assertEquals(new BigDecimal("700.00"), matchCaptor.getValue().getActualTotalCny());
        assertEquals(null, matchCaptor.getValue().getEstimatedTotalCny());
        assertEquals("multiple_recommended_estimates", matchCaptor.getValue().getReason());
    }

    private BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .businessOwnerUserId(307L)
                .sessionUserId(90001L)
                .build();
    }
}
