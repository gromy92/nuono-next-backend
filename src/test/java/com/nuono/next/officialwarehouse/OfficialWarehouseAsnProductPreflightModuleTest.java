package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noon.NoonResponseClassifier;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.officialwarehouse.OfficialWarehouseAsnProductPreflightModule.Proof;
import com.nuono.next.officialwarehouse.OfficialWarehouseNoonInboundClient.NoonCallContext;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnLineInsertRecord;
import com.nuono.next.sales.NoonSalesReportBinding;
import com.nuono.next.web.ApiProblemException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OfficialWarehouseAsnProductPreflightModuleTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private OfficialWarehouseNoonInboundClient inboundClient;
    private OfficialWarehouseAsnProductPreflightModule module;
    private NoonSession session;
    private NoonSalesReportBinding binding;
    private NoonCallContext context;

    @BeforeEach
    void setUp() {
        inboundClient = mock(OfficialWarehouseNoonInboundClient.class);
        module = new OfficialWarehouseAsnProductPreflightModule(inboundClient);
        session = null;
        binding = binding();
        context = NoonCallContext.asn(500001L, "OWA-500001");
    }

    @Test
    void freezesEverySelectedIdentityAndAllMappedPartnerBarcodes() {
        when(inboundClient.searchProductOffersPage(isNull(), eq(binding), eq(context), any()))
                .thenAnswer(invocation -> {
                    String partnerSku = invocation.<JsonNode>getArgument(3).path("search").asText();
                    if ("SGGRB329".equals(partnerSku)) {
                        return offerPage("SGGRB329", "PSKU-329", "PB-329-B", "PB-329-A");
                    }
                    return offerPage("PAPERSAYSB014", "PSKU-014", "PB-014");
                });

        Proof proof = module.freeze(
                session,
                binding,
                context,
                List.of(line("SGGRB329", "PSKU-329", "N329", 2),
                        line("PAPERSAYSB014", "PSKU-014", "N014", 3))
        );

        assertThat(proof.totalQuantity()).isEqualTo(5);
        assertThat(proof.lines()).hasSize(2);
        assertThat(proof.lines().get(0).partnerSku()).isEqualTo("SGGRB329");
        assertThat(proof.lines().get(0).pbarcodes()).containsExactly("PB-329-A", "PB-329-B");
        assertThat(proof.requestLineRows()).extracting(row -> row.partnerSku)
                .containsExactly("SGGRB329", "PAPERSAYSB014");
        verify(inboundClient, times(2)).searchProductOffersPage(isNull(), eq(binding), eq(context), any());
    }

    @Test
    void reportsSggrb329PbarcodeFailureBeforeAProofCanExist() {
        when(inboundClient.searchProductOffersPage(isNull(), eq(binding), eq(context), any()))
                .thenReturn(offerPage("SGGRB329", "PSKU-329"));

        assertThatThrownBy(() -> module.freeze(
                session, binding, context, List.of(line("SGGRB329", "PSKU-329", "N329", 20))))
                .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                    assertThat(problem.getCode())
                            .isEqualTo("OFFICIAL_WAREHOUSE_ASN_PRODUCT_PREFLIGHT_FAILED");
                    assertThat(problem.isPartialSuccess()).isFalse();
                    List<?> invalidLines = (List<?>) problem.getDetails().get("invalidLines");
                    assertThat(invalidLines).singleElement().satisfies(raw -> {
                        Map<?, ?> issue = (Map<?, ?>) raw;
                        assertThat(issue.get("partnerSku")).isEqualTo("SGGRB329");
                        assertThat(issue.get("reasonCode")).isEqualTo("PBARCODE_UNMAPPED");
                    });
                });
    }

    @Test
    void rejectsAStaleLocalPskuEvenWhenThePartnerSkuStillExists() {
        when(inboundClient.searchProductOffersPage(isNull(), eq(binding), eq(context), any()))
                .thenReturn(offerPage("SGGRB329", "PSKU-NEW", "PB-329"));

        assertThatThrownBy(() -> module.freeze(
                session, binding, context, List.of(line("SGGRB329", "PSKU-OLD", "N329", 1))))
                .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                    List<?> invalidLines = (List<?>) problem.getDetails().get("invalidLines");
                    assertThat(((Map<?, ?>) invalidLines.get(0)).get("reasonCode"))
                            .isEqualTo("PSKU_MISMATCH");
                });
    }

    @Test
    void rejectsWhenTheActualLogisticsBarcodeIsNotAnExactNoonPbarcode() {
        when(inboundClient.searchProductOffersPage(isNull(), eq(binding), eq(context), any()))
                .thenReturn(offerPage("SGGRB290", "PSKU-290", "SGGRB290", "sggrb329"));

        AsnLineInsertRecord selected = line("SGGRB290", "PSKU-290", "N290", 1);
        selected.sourceBarcodes.add("SGGRB329");

        assertThatThrownBy(() -> module.freeze(session, binding, context, List.of(selected)))
                .isInstanceOfSatisfying(ApiProblemException.class, problem -> {
                    List<?> invalidLines = (List<?>) problem.getDetails().get("invalidLines");
                    Map<?, ?> issue = (Map<?, ?>) invalidLines.get(0);
                    assertThat(issue.get("sourceBarcode")).isEqualTo("SGGRB329");
                    assertThat(issue.get("reasonCode")).isEqualTo("BARCODE_PBARCODE_MISMATCH");
                });
    }

    @Test
    void freezesTheExactLogisticsBarcodeProvenByNoonPbarcode() {
        when(inboundClient.searchProductOffersPage(isNull(), eq(binding), eq(context), any()))
                .thenReturn(offerPage("SGGRB290", "PSKU-290", "SGGRB329"));

        AsnLineInsertRecord selected = line("SGGRB290", "PSKU-290", "N290", 1);
        selected.sourceBarcodes.add("SGGRB329");

        Proof proof = module.freeze(session, binding, context, List.of(selected));

        assertThat(proof.lines()).singleElement().satisfies(line -> {
            assertThat(line.sourceBarcodes()).containsExactly("SGGRB329");
            assertThat(line.pbarcodes()).containsExactly("SGGRB329");
        });
    }

    @Test
    void unrelatedUnselectedOfferWithoutPbarcodeDoesNotBlockTheSelectedLine() {
        ObjectNode page = offerPage("SGGRB329", "PSKU-329", "PB-329");
        ObjectNode data = (ObjectNode) page.path("data");
        data.put("total", 2);
        data.withArray("hits").addObject()
                .put("partner_sku", "UNSELECTED-SKU")
                .put("psku_code", "PSKU-UNSELECTED");
        when(inboundClient.searchProductOffersPage(isNull(), eq(binding), eq(context), any()))
                .thenReturn(page);

        Proof proof = module.freeze(
                session, binding, context, List.of(line("SGGRB329", "PSKU-329", "N329", 1)));

        assertThat(proof.lines()).singleElement()
                .satisfies(line -> assertThat(line.partnerSku()).isEqualTo("SGGRB329"));
    }

    @Test
    void proofCannotAuthorizeAWriteForAnotherAsnScope() {
        when(inboundClient.searchProductOffersPage(isNull(), eq(binding), eq(context), any()))
                .thenReturn(offerPage("SGGRB329", "PSKU-329", "PB-329"));
        Proof proof = module.freeze(
                session, binding, context, List.of(line("SGGRB329", "PSKU-329", "N329", 1)));
        OfficialWarehouseNoonInboundClient writeClient =
                new OfficialWarehouseNoonInboundClient(objectMapper, new NoonResponseClassifier());

        assertThatThrownBy(() -> writeClient.createAsn(
                null, binding, NoonCallContext.asn(500002L, "OWA-500002"), proof))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("业务范围不一致");
    }

    private ObjectNode offerPage(String partnerSku, String pskuCode, String... pbarcodes) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode data = root.putObject("data");
        data.put("total", 1);
        ObjectNode hit = data.putArray("hits").addObject();
        hit.put("partner_sku", partnerSku);
        hit.put("psku_code", pskuCode);
        for (String pbarcode : pbarcodes) {
            hit.withArray("partner_barcodes").add(pbarcode);
        }
        return root;
    }

    private AsnLineInsertRecord line(
            String partnerSku,
            String pskuCode,
            String noonSku,
            int quantity
    ) {
        AsnLineInsertRecord line = new AsnLineInsertRecord();
        line.partnerSku = partnerSku;
        line.pskuCode = pskuCode;
        line.noonSku = noonSku;
        line.quantity = quantity;
        line.cubicFeet = new BigDecimal("0.12345");
        line.storageTypeCode = "standard";
        return line;
    }

    private NoonSalesReportBinding binding() {
        return new NoonSalesReportBinding(
                307L, 108065L, "PRJ108065", "STR108065-NSA", "SA", "108065",
                "merchant@example.com", null, null, "persisted-cookie"
        );
    }
}
