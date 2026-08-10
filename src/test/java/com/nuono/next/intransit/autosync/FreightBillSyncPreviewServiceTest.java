package com.nuono.next.intransit.autosync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nuono.next.infrastructure.mapper.InTransitFreightCostMapper;
import com.nuono.next.infrastructure.mapper.InTransitGoodsMapper;
import com.nuono.next.intransit.InTransitBatchRecords.BatchRow;
import com.nuono.next.intransit.InTransitFreightCostCommands.ActualFreightBillCommand;
import com.nuono.next.intransit.InTransitFreightCostCommands.ActualFreightComponentCommand;
import com.nuono.next.intransit.InTransitFreightCostCommands.ActualFreightSyncCommand;
import com.nuono.next.intransit.InTransitFreightCostRecords.ActualFreightBillRow;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FreightBillSyncPreviewServiceTest {
    @Mock private InTransitGoodsMapper batchMapper;
    @Mock private InTransitFreightCostMapper freightCostMapper;

    @Test
    void incompleteSnapshotIsNeverCommittable() {
        FreightBillSyncPreview preview = service().preview(command(), FreightBillFetchResult.success(
                command(), false, 1, "digest", List.of("INCOMPLETE_COST_ROWS:1")
        ));

        assertThat(preview.isCommittable()).isFalse();
        assertThat(preview.getIssues()).extracting(FreightBillSyncPreview.Issue::getCode)
                .contains("INCOMPLETE_SNAPSHOT", "PROVIDER_PAYLOAD_INCOMPLETE");
    }

    @Test
    void completeClosedSnapshotCreatesNewBill() {
        ActualFreightSyncCommand command = command();
        BatchRow batch = new BatchRow();
        batch.setId(53001L);
        when(batchMapper.selectBatchByReferenceNo(307L, "YT2603941446")).thenReturn(batch);
        when(freightCostMapper.selectActualBillBySource(307L, "YITE", "202605180063", 53001L)).thenReturn(null);

        FreightBillSyncPreview preview = service().preview(command, FreightBillFetchResult.success(
                command, true, 1, "digest", List.of()
        ));

        assertThat(preview.isCommittable()).isTrue();
        assertThat(preview.getCreateCount()).isEqualTo(1);
        assertThat(preview.getChangedCommand().getBills()).hasSize(1);
    }

    @Test
    void identicalStoredPayloadIsIdempotentlySkipped() throws Exception {
        ActualFreightSyncCommand command = command();
        ActualFreightBillCommand bill = command.getBills().get(0);
        BatchRow batch = new BatchRow();
        batch.setId(53001L);
        ActualFreightBillRow existing = new ActualFreightBillRow();
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        existing.setRawPayloadJson(mapper.writeValueAsString(bill));
        when(batchMapper.selectBatchByReferenceNo(307L, "YT2603941446")).thenReturn(batch);
        when(freightCostMapper.selectActualBillBySource(307L, "YITE", "202605180063", 53001L)).thenReturn(existing);

        FreightBillSyncPreview preview = service().preview(command, FreightBillFetchResult.success(
                command, true, 1, "digest", List.of()
        ));

        assertThat(preview.isCommittable()).isTrue();
        assertThat(preview.getUnchangedCount()).isEqualTo(1);
        assertThat(preview.getChangedCommand().getBills()).isEmpty();
    }

    private FreightBillSyncPreviewService service() {
        return new FreightBillSyncPreviewService(batchMapper, freightCostMapper, new ObjectMapper());
    }

    private static ActualFreightSyncCommand command() {
        ActualFreightComponentCommand component = new ActualFreightComponentCommand();
        component.setRawFeeName("运费（A）");
        component.setStandardFeeType("HEADHAUL");
        component.setCurrencyCode("CNY");
        component.setExchangeRateToCny(BigDecimal.ONE);
        component.setOriginalAmount(new BigDecimal("100.00"));
        component.setCnyAmount(new BigDecimal("100.00"));

        ActualFreightBillCommand bill = new ActualFreightBillCommand();
        bill.setBillNo("202605180063");
        bill.setBatchReferenceNo("YT2603941446");
        bill.setCurrencyCode("CNY");
        bill.setExchangeRateToCny(BigDecimal.ONE);
        bill.setOriginalTotalAmount(new BigDecimal("100.00"));
        bill.setCnyTotalAmount(new BigDecimal("100.00"));
        bill.setFreightAmountCny(new BigDecimal("100.00"));
        bill.setComponents(List.of(component));

        ActualFreightSyncCommand command = new ActualFreightSyncCommand();
        command.setOwnerUserId(307L);
        command.setOperatorUserId(408L);
        command.setSourceSystem("YITE");
        command.setBills(List.of(bill));
        return command;
    }
}
