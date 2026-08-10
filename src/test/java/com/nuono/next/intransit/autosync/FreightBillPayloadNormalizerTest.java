package com.nuono.next.intransit.autosync;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.intransit.InTransitFreightCostCommands.ActualFreightBillCommand;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FreightBillPayloadNormalizerTest {

    @Test
    void normalizesVerifiedYiteInvoiceRowsByBillAndShipment() {
        FreightBillFetchResult result = FreightBillPayloadNormalizer.normalize("YITE", json(
                "{",
                "  \"success\": 1,",
                "  \"data\": {",
                "    \"invoice_number\": \"202605180063\",",
                "    \"invoice_date\": \"2026-05-18\",",
                "    \"invoice_status\": \"已核销\",",
                "    \"invoice_total\": 9715.60,",
                "    \"updated_at\": \"2026-05-18 18:30:00\",",
                "    \"rows\": [",
                "      {\"shipment_no\":\"YT2603941446\",\"expense_name\":\"运费（A）\",\"quantity\":3.07,\"unit\":\"CBM\",\"unit_price\":1190,\"amount\":3653.30,\"cbm\":3.23,\"kg\":870},",
                "      {\"shipment_no\":\"YT2603941446\",\"expense_name\":\"运费（C）\",\"quantity\":0.12,\"unit\":\"CBM\",\"unit_price\":1740,\"amount\":208.80},",
                "      {\"shipment_no\":\"YT2603941446\",\"expense_name\":\"运费（D）\",\"quantity\":0.04,\"unit\":\"CBM\",\"unit_price\":2140,\"amount\":85.60},",
                "      {\"shipment_no\":\"YT2603941446\",\"expense_name\":\"海外派送运费\",\"quantity\":3.23,\"unit\":\"CBM\",\"unit_price\":200,\"amount\":646.00},",
                "      {\"shipment_no\":\"YT2604377333\",\"expense_name\":\"运费（A）\",\"quantity\":2.75,\"unit\":\"CBM\",\"unit_price\":1190,\"amount\":3272.50},",
                "      {\"shipment_no\":\"YT2604377333\",\"expense_name\":\"运费（B）\",\"quantity\":0.33,\"unit\":\"CBM\",\"unit_price\":1640,\"amount\":541.20},",
                "      {\"shipment_no\":\"YT2604377333\",\"expense_name\":\"运费（C）\",\"quantity\":0.20,\"unit\":\"CBM\",\"unit_price\":1740,\"amount\":348.00},",
                "      {\"shipment_no\":\"YT2604377333\",\"expense_name\":\"运费（D）\",\"quantity\":0.13,\"unit\":\"CBM\",\"unit_price\":2140,\"amount\":278.20},",
                "      {\"shipment_no\":\"YT2604377333\",\"expense_name\":\"末端派送费\",\"quantity\":3.41,\"unit\":\"CBM\",\"unit_price\":200,\"amount\":682.00}",
                "    ]",
                "  }",
                "}"
        ));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isSnapshotComplete()).isTrue();
        assertThat(result.getSourceRowCount()).isEqualTo(9);
        assertThat(result.getRevisionDigest()).hasSize(64);
        assertThat(result.getSourceUpdatedAt()).isEqualTo("2026-05-18 18:30:00");
        assertThat(result.getCommand().getBills()).hasSize(2);

        ActualFreightBillCommand first = result.getCommand().getBills().get(0);
        assertThat(first.getBillNo()).isEqualTo("202605180063");
        assertThat(first.getBatchReferenceNo()).isEqualTo("YT2603941446");
        assertThat(first.getCnyTotalAmount()).isEqualByComparingTo(new BigDecimal("4593.700000"));
        assertThat(first.getFreightAmountCny()).isEqualByComparingTo(new BigDecimal("3947.700000"));
        assertThat(first.getDeliveryAmountCny()).isEqualByComparingTo(new BigDecimal("646.000000"));
        assertThat(first.getComponents()).hasSize(4);
    }

    @Test
    void blocksChicOrderReportWhenItContainsWeightsButNoActualBillAmounts() {
        FreightBillFetchResult result = FreightBillPayloadNormalizer.normalize("CHIC", json(
                "{\"data\":{\"records\":[{",
                "  \"purchaseBatchSn\":\"XGGEKSA04070\",",
                "  \"warehousingSn\":\"XGGEKSA04070-1\",",
                "  \"chargeableWeight\":16,",
                "  \"status\":\"已入仓\"",
                "}]}}"
        ));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isSnapshotComplete()).isFalse();
        assertThat(result.getSourceRowCount()).isEqualTo(1);
        assertThat(result.getCommand().getBills()).isEmpty();
        assertThat(result.getIssues()).contains("MISSING_ACTUAL_COST_FIELDS");
    }

    @Test
    void normalizesChicActualCostRowsOnlyWhenBillNumberAndAmountExist() {
        FreightBillFetchResult result = FreightBillPayloadNormalizer.normalize("CHIC", json(
                "{\"data\":{\"records\":[{",
                "  \"statementNo\":\"CHIC-202606\",",
                "  \"statementTotal\":976,",
                "  \"purchaseBatchSn\":\"XGGEKSA04070\",",
                "  \"feeName\":\"空运费\",",
                "  \"chargeQuantity\":16,",
                "  \"chargeUnit\":\"KG\",",
                "  \"unitPrice\":61,",
                "  \"amount\":976",
                "}]}}"
        ));

        assertThat(result.isSnapshotComplete()).isTrue();
        assertThat(result.getCommand().getBills()).singleElement().satisfies(bill -> {
            assertThat(bill.getBillNo()).isEqualTo("CHIC-202606");
            assertThat(bill.getBatchReferenceNo()).isEqualTo("XGGEKSA04070");
            assertThat(bill.getCnyTotalAmount()).isEqualByComparingTo("976.000000");
        });
    }

    @Test
    void blocksPaginatedOrPartialRowsWhenDeclaredBillTotalDoesNotClose() {
        FreightBillFetchResult result = FreightBillPayloadNormalizer.normalize("YITE", json(
                "{\"data\":{",
                "  \"invoice_number\":\"202605180063\",",
                "  \"invoice_total\":200,",
                "  \"rows\":[{\"shipment_no\":\"YT2603941446\",\"expense_name\":\"运费（A）\",\"amount\":100}]",
                "}}"
        ));

        assertThat(result.isSnapshotComplete()).isFalse();
        assertThat(result.getIssues()).contains("DECLARED_BILL_TOTAL_MISMATCH:202605180063");
    }

    @Test
    void normalizedRevisionDigestIgnoresProviderRowOrder() {
        String first = "{\"data\":{\"invoice_number\":\"B1\",\"invoice_total\":30,\"rows\":["
                + "{\"shipment_no\":\"S1\",\"expense_name\":\"运费（B）\",\"amount\":20},"
                + "{\"shipment_no\":\"S1\",\"expense_name\":\"运费（A）\",\"amount\":10}]}}";
        String second = "{\"data\":{\"invoice_total\":30,\"invoice_number\":\"B1\",\"rows\":["
                + "{\"expense_name\":\"运费（A）\",\"amount\":10,\"shipment_no\":\"S1\"},"
                + "{\"amount\":20,\"shipment_no\":\"S1\",\"expense_name\":\"运费（B）\"}]}}";

        FreightBillFetchResult firstResult = FreightBillPayloadNormalizer.normalize("YITE", first);
        FreightBillFetchResult secondResult = FreightBillPayloadNormalizer.normalize("YITE", second);

        assertThat(firstResult.isSnapshotComplete()).isTrue();
        assertThat(secondResult.isSnapshotComplete()).isTrue();
        assertThat(firstResult.getRevisionDigest()).isEqualTo(secondResult.getRevisionDigest());
    }

    private static String json(String... lines) {
        return String.join("\n", lines);
    }
}
