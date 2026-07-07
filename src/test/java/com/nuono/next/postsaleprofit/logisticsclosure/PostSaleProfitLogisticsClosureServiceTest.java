package com.nuono.next.postsaleprofit.logisticsclosure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.PostSaleProfitLogisticsClosureMapper;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.LogisticsClosureAllocationRow;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.LogisticsClosureCandidateRow;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.LogisticsClosureConfirmCommand;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.LogisticsClosurePurchaseBatchRow;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PostSaleProfitLogisticsClosureServiceTest {

    @Mock
    private PostSaleProfitLogisticsClosureMapper mapper;

    @Test
    void confirmRejectsNonPositiveAllocatedQuantity() {
        PostSaleProfitLogisticsClosureService service = new PostSaleProfitLogisticsClosureService(mapper);

        assertThatThrownBy(() -> service.confirmAllocation(command("0")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("allocatedQuantity must be greater than 0");
    }

    @Test
    void confirmRejectsWhenQuantityExceedsPurchaseRemainingQuantity() {
        when(mapper.selectPurchaseBatchBySource(307L, "STR108065-NSA", "SA", null, null, "ALI1688_SKU_PURCHASE_BATCH", "BATCH-A"))
                .thenReturn(purchase("10"));
        when(mapper.selectCandidateByLine(307L, "STR108065-NSA", "SA", "PSKU-1", 53001L, 54001L))
                .thenReturn(candidate("100"));
        when(mapper.sumConfirmedQuantityBySource(307L, "ALI1688_SKU_PURCHASE_BATCH", "BATCH-A"))
                .thenReturn(new BigDecimal("8"));
        when(mapper.sumConfirmedQuantityByInTransitLine(307L, 54001L))
                .thenReturn(BigDecimal.ZERO);
        PostSaleProfitLogisticsClosureService service = new PostSaleProfitLogisticsClosureService(mapper);

        assertThatThrownBy(() -> service.confirmAllocation(command("3")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("exceeds remaining purchase quantity");
    }

    @Test
    void confirmWritesConfirmedAllocationWithEvidenceSnapshot() {
        when(mapper.nextAllocationId()).thenReturn(120001L);
        when(mapper.selectPurchaseBatchBySource(307L, "STR108065-NSA", "SA", null, null, "ALI1688_SKU_PURCHASE_BATCH", "BATCH-A"))
                .thenReturn(purchase("10"));
        when(mapper.selectCandidateByLine(307L, "STR108065-NSA", "SA", "PSKU-1", 53001L, 54001L))
                .thenReturn(candidate("100"));
        when(mapper.sumConfirmedQuantityBySource(307L, "ALI1688_SKU_PURCHASE_BATCH", "BATCH-A"))
                .thenReturn(BigDecimal.ZERO);
        when(mapper.sumConfirmedQuantityByInTransitLine(307L, 54001L))
                .thenReturn(BigDecimal.ZERO);
        PostSaleProfitLogisticsClosureService service = new PostSaleProfitLogisticsClosureService(mapper);

        var result = service.confirmAllocation(command("5"));

        assertThat(result.getAllocationId()).isEqualTo(120001L);
        assertThat(result.getConfirmationStatus()).isEqualTo("CONFIRMED");
        ArgumentCaptor<LogisticsClosureAllocationRow> captor = ArgumentCaptor.forClass(LogisticsClosureAllocationRow.class);
        verify(mapper).insertAllocation(captor.capture());
        LogisticsClosureAllocationRow row = captor.getValue();
        assertThat(row.id).isEqualTo(120001L);
        assertThat(row.confirmationStatus).isEqualTo("CONFIRMED");
        assertThat(row.matchMethod).isEqualTo("MANUAL");
        assertThat(row.allocatedQuantity).isEqualByComparingTo("5");
        assertThat(row.evidenceJson)
                .contains("\"sourceId\":\"BATCH-A\"")
                .contains("\"inTransitBatchId\":53001")
                .contains("\"inTransitGoodsLineId\":54001")
                .contains("\"reason\":\"用户确认同批次\"");
    }

    private static LogisticsClosureConfirmCommand command(String quantity) {
        LogisticsClosureConfirmCommand command = new LogisticsClosureConfirmCommand();
        command.setOwnerUserId(307L);
        command.setOperatorUserId(307L);
        command.setStoreCode("STR108065-NSA");
        command.setSiteCode("SA");
        command.setSourceType("ALI1688_SKU_PURCHASE_BATCH");
        command.setSourceId("BATCH-A");
        command.setInTransitBatchId(53001L);
        command.setInTransitGoodsLineId(54001L);
        command.setAllocatedQuantity(new BigDecimal(quantity));
        command.setMatchMethod("MANUAL");
        command.setReason("用户确认同批次");
        return command;
    }

    private static LogisticsClosurePurchaseBatchRow purchase(String quantity) {
        LogisticsClosurePurchaseBatchRow row = new LogisticsClosurePurchaseBatchRow();
        row.sourceType = "ALI1688_SKU_PURCHASE_BATCH";
        row.sourceId = "BATCH-A";
        row.purchaseBatchId = 102001L;
        row.targetStoreCode = "STR108065-NSA";
        row.targetSiteCode = "SA";
        row.partnerSku = "PSKU-1";
        row.skuParent = "SKU-PARENT-1";
        row.purchaseQuantity = new BigDecimal(quantity);
        row.purchaseCostCny = new BigDecimal("20.00");
        return row;
    }

    private static LogisticsClosureCandidateRow candidate(String quantity) {
        LogisticsClosureCandidateRow row = new LogisticsClosureCandidateRow();
        row.inTransitBatchId = 53001L;
        row.inTransitGoodsLineId = 54001L;
        row.batchReferenceNo = "YT2603941446";
        row.forwarderName = "义特";
        row.transportMode = "SEA";
        row.siteCode = "SA";
        row.partnerSku = "PSKU-1";
        row.shippedQuantity = new BigDecimal(quantity);
        row.headhaulUnitCostCny = new BigDecimal("1.25");
        row.headhaulStatus = "confirmed";
        row.candidateStrength = "strong";
        row.confidenceScore = 90;
        return row;
    }
}
