package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noonlog.NoonHttpCallLogService;
import com.nuono.next.noonpull.NoonPullFailurePolicy;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnInboundReceiptRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnLineRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AsnInboundDetailView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AsnView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.sales.NoonSalesReportBindingResolver;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalDbOfficialWarehouseAsnInboundDetailTest {

    private OfficialWarehouseMapper mapper;
    private LocalDbOfficialWarehouseService service;

    @BeforeEach
    void setUp() {
        mapper = mock(OfficialWarehouseMapper.class);
        service = new LocalDbOfficialWarehouseService(
                mapper,
                mock(NoonSessionGateway.class),
                mock(NoonSalesReportBindingResolver.class),
                mock(NoonHttpCallLogService.class),
                mock(OfficialWarehouseNoonInboundClient.class),
                new ObjectMapper(),
                null,
                new NoonPullFailurePolicy()
        );
    }

    @Test
    void showsExactReceiptProgressOnListAndMergesDetailByLineIdOrBusinessKey() {
        AsnRecord asn = asn();
        AsnLineRecord firstLine = line(510001L, "FIRST-PSKU", "Z-FIRST", 300);
        AsnLineRecord secondLine = line(510002L, "SECOND-PSKU", "Z-SECOND", 300);
        AsnInboundReceiptRecord shortReceipt = receipt(510001L, "FIRST-PSKU", "Z-FIRST", 300, 280, 0);
        AsnInboundReceiptRecord qcReceipt = receipt(null, "SECOND-PSKU", "Z-SECOND", 300, 300, 5);

        when(mapper.listAsns(307L, List.of("STR245027-NAE"), "STR245027-NAE", "AE", null, 200))
                .thenReturn(List.of(asn));
        when(mapper.selectAsn(307L, 500001L)).thenReturn(asn);
        when(mapper.listAsnLines(500001L)).thenReturn(List.of(firstLine, secondLine));
        when(mapper.listAsnInboundReceipts(307L, List.of(500001L)))
                .thenReturn(List.of(shortReceipt, qcReceipt));

        List<AsnView> list = service.listAsns(access(), "STR245027-NAE", "AE", null);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).inboundSummary.reportConnected).isTrue();
        assertThat(list.get(0).inboundSummary.asnQuantity).isEqualTo(600);
        assertThat(list.get(0).inboundSummary.expectedQuantity).isEqualTo(600);
        assertThat(list.get(0).inboundSummary.receivedQuantity).isEqualTo(580);
        assertThat(list.get(0).inboundSummary.shortQuantity).isEqualTo(20);
        assertThat(list.get(0).inboundSummary.qcFailedQuantity).isEqualTo(5);

        AsnInboundDetailView detail = service.getAsnInboundDetail(access(), "500001");
        assertThat(detail.lines).hasSize(2);
        assertThat(detail.lines.get(0).inboundStatus).isEqualTo("SHORT_RECEIVED");
        assertThat(detail.lines.get(0).shortQuantity).isEqualTo(20);
        assertThat(detail.lines.get(0).matchStatus).isEqualTo("MATCHED");
        assertThat(detail.lines.get(1).inboundStatus).isEqualTo("QC_FAILED");
        assertThat(detail.lines.get(1).matchStatus).isEqualTo("MATCHED_BY_BUSINESS_KEY");
        assertThat(detail.summary.unmatchedLineCount).isZero();
        assertThat(detail.summary.exceptionLineCount).isEqualTo(2);
    }

    @Test
    void keepsNoonBackofficeReceiptVisibleWhenLocalAsnLinesAreMissing() {
        AsnRecord asn = asn();
        asn.sourceType = "NOON_SYNC";
        AsnInboundReceiptRecord receipt = receipt(null, "REPORT-ONLY", "Z-REPORT", 10, 10, 0);
        receipt.matchStatus = "LINE_UNMATCHED";
        when(mapper.selectAsn(307L, 500001L)).thenReturn(asn);
        when(mapper.listAsnLines(500001L)).thenReturn(List.of());
        when(mapper.listAsnInboundReceipts(307L, List.of(500001L))).thenReturn(List.of(receipt));

        AsnInboundDetailView detail = service.getAsnInboundDetail(access(), "500001");

        assertThat(detail.summary.reportConnected).isTrue();
        assertThat(detail.summary.unmatchedLineCount).isEqualTo(1);
        assertThat(detail.lines).singleElement().satisfies(line -> {
            assertThat(line.reportOnly).isTrue();
            assertThat(line.partnerSku).isEqualTo("REPORT-ONLY");
            assertThat(line.receivedQuantity).isEqualTo(10);
            assertThat(line.inboundStatus).isEqualTo("UNMATCHED");
        });
    }

    private static AsnRecord asn() {
        AsnRecord asn = new AsnRecord();
        asn.id = 500001L;
        asn.ownerUserId = 307L;
        asn.storeCode = "STR245027-NAE";
        asn.siteCode = "AE";
        asn.localAsnNo = "ASN-LOCAL-500001";
        asn.noonAsnNr = "A05584393PN";
        asn.sourceType = "MANUAL";
        asn.status = "LINES_CREATED";
        asn.productCount = 2;
        asn.totalQuantity = 600;
        return asn;
    }

    private static AsnLineRecord line(Long id, String partnerSku, String noonSku, int quantity) {
        AsnLineRecord line = new AsnLineRecord();
        line.id = id;
        line.asnId = 500001L;
        line.productVariantId = id + 1000;
        line.partnerSku = partnerSku;
        line.pskuCode = noonSku;
        line.noonSku = noonSku;
        line.qty = quantity;
        line.titleCache = partnerSku + " title";
        return line;
    }

    private static AsnInboundReceiptRecord receipt(
            Long asnLineId,
            String partnerSku,
            String noonSku,
            int expected,
            int received,
            int qcFailed
    ) {
        AsnInboundReceiptRecord receipt = new AsnInboundReceiptRecord();
        receipt.asnId = 500001L;
        receipt.asnLineId = asnLineId;
        receipt.importId = 623001L;
        receipt.reportRowId = asnLineId == null ? 624002L : 624001L;
        receipt.noonAsnNr = "A05584393PN";
        receipt.partnerSku = partnerSku;
        receipt.pskuCode = noonSku;
        receipt.noonSku = noonSku;
        receipt.qtyExpected = expected;
        receipt.receivedQty = received;
        receipt.qcFailedQty = qcFailed;
        receipt.unidentifiedQty = 0;
        receipt.receiptStatus = qcFailed > 0 ? "QC_FAILED" : received < expected ? "SHORT_RECEIVED" : "NORMAL";
        receipt.matchStatus = asnLineId == null ? "LINE_UNMATCHED" : "MATCHED";
        receipt.importedAt = "2026-07-15 23:42:00";
        return receipt;
    }

    private static BusinessAccessContext access() {
        return BusinessAccessContext.builder()
                .sessionUserId(901L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.BOSS)
                .storeCodes(Set.of("STR245027-NAE"))
                .build();
    }
}
