package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseNoonInboundClient.NoonCallContext;
import com.nuono.next.sales.NoonSalesReportBinding;
import com.nuono.next.web.ApiProblemException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class OfficialWarehouseAsnPreflightAuditRecorderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void recordsScopedInvalidLinesWithoutPersistingAnAsn() {
        OfficialWarehouseMapper mapper = mock(OfficialWarehouseMapper.class);
        org.mockito.Mockito.when(mapper.nextAsnPreflightAuditId()).thenReturn(630001L);

        OfficialWarehouseAsnPreflightAuditRecorder.record(
                mapper, objectMapper, binding(), NoonCallContext.asn(500001L, "OWA-500001"), 901L, 1, failure()
        );

        ArgumentCaptor<OfficialWarehouseAsnPreflightAuditRecord> captor =
                ArgumentCaptor.forClass(OfficialWarehouseAsnPreflightAuditRecord.class);
        verify(mapper).insertAsnPreflightAudit(captor.capture());
        OfficialWarehouseAsnPreflightAuditRecord row = captor.getValue();
        assertThat(row.id).isEqualTo(630001L);
        assertThat(row.ownerUserId).isEqualTo(307L);
        assertThat(row.operatorUserId).isEqualTo(901L);
        assertThat(row.storeCode).isEqualTo("STR108065-NSA");
        assertThat(row.siteCode).isEqualTo("SA");
        assertThat(row.attemptAsnId).isEqualTo(500001L);
        assertThat(row.attemptRef).isEqualTo("OWA-500001");
        assertThat(row.failureCode).isEqualTo("OFFICIAL_WAREHOUSE_ASN_PRODUCT_PREFLIGHT_FAILED");
        assertThat(row.reasonSummary).isEqualTo("PBARCODE_UNMAPPED x1");
        assertThat(row.invalidLinesJson).contains("SGGRB329").contains("PBARCODE_UNMAPPED");
    }

    @Test
    void auditFailureIsContained() {
        OfficialWarehouseMapper mapper = mock(OfficialWarehouseMapper.class);
        doThrow(new IllegalStateException("audit table unavailable")).when(mapper).nextAsnPreflightAuditId();

        OfficialWarehouseAsnPreflightAuditRecorder.record(
                mapper, objectMapper, binding(), NoonCallContext.asn(500001L, "OWA-500001"), 901L, 1, failure()
        );

        verify(mapper).nextAsnPreflightAuditId();
    }

    private static NoonSalesReportBinding binding() {
        return new NoonSalesReportBinding(
                307L, 108065L, "PRJ108065", "STR108065-NSA", "SA", "N108065", "merchant@example.com", "cookie"
        );
    }

    private static ApiProblemException failure() {
        return new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "OFFICIAL_WAREHOUSE_ASN_PRODUCT_PREFLIGHT_FAILED",
                "VALIDATION",
                "CREATE_ASN",
                "商品未通过预检",
                false,
                false,
                "OWA-500001",
                Map.of("invalidLines", List.of(Map.of(
                        "partnerSku", "SGGRB329", "pskuCode", "PSKU-329", "reasonCode", "PBARCODE_UNMAPPED"
                ))),
                null
        );
    }
}
