package com.nuono.next.procurement.aliorder;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class Ali1688HistoricalOrderFactPreflightTest {
    private final Ali1688HistoricalOrderFactPreflight preflight =
            new Ali1688HistoricalOrderFactPreflight();

    @Test
    void acceptsExactSchemaBoundariesIncludingUtf8mb4Characters() {
        Ali1688HistoricalOrderProvider.OrderSnapshot order = validOrder();
        order.setProviderOrderNo(repeat("订", 120));
        order.setOrderTime("9999-12-31 23:59:59");
        order.setSupplierName(repeat("供", 300));
        order.setAmountText("99999999999999.9999");
        Ali1688HistoricalOrderProvider.OrderItemSnapshot item = order.getItems().get(0);
        item.setOfferId(repeat("😀", 80));
        item.setTitle(repeat("品", 500));
        item.setLogisticsCompany(repeat("物", 200));
        item.setTrackingNo(repeat("号", 160));

        assertThat(preflight.inspectFact(order).isAccepted()).isTrue();
    }

    @Test
    void rejectsHeaderItemAndLogisticsTextBeyondPersistedVarcharWidths() {
        Ali1688HistoricalOrderProvider.OrderSnapshot header = validOrder();
        header.setSupplierName(repeat("x", 301));
        assertRejected(header, "DP10_FACT_ORDER_TEXT_TOO_LONG");

        Ali1688HistoricalOrderProvider.OrderSnapshot item = validOrder();
        item.getItems().get(0).setTitle(repeat("x", 501));
        assertRejected(item, "DP10_FACT_ITEM_TEXT_TOO_LONG");

        Ali1688HistoricalOrderProvider.OrderSnapshot logistics = validOrder();
        logistics.getItems().get(0).setTrackingNo(repeat("x", 161));
        assertRejected(logistics, "DP10_FACT_LOGISTICS_TEXT_TOO_LONG");
    }

    @Test
    void rejectsInvalidDatetimeDecimalAndMalformedUnicodeBeforeSql() {
        Ali1688HistoricalOrderProvider.OrderSnapshot stageTime = validOrder();
        stageTime.setProviderModifiedAt(
                Instant.parse("1000-01-01T00:00:00Z").minusSeconds(1));
        assertRejected(stageTime, "DP10_ORDER_MODIFIED_AT_INVALID");

        Ali1688HistoricalOrderProvider.OrderSnapshot date = validOrder();
        date.setOrderTime("2026-02-30 03:00:00");
        assertRejected(date, "DP10_FACT_ORDER_TIME_INVALID");

        Ali1688HistoricalOrderProvider.OrderSnapshot amount = validOrder();
        amount.setAmountText("99999999999999.99995");
        assertRejected(amount, "DP10_FACT_AMOUNT_OUT_OF_RANGE");

        Ali1688HistoricalOrderProvider.OrderSnapshot unicode = validOrder();
        unicode.setBuyerRemark("bad-" + String.valueOf((char) 0xD800) + "-text");
        assertRejected(unicode, "DP10_FACT_TEXT_ENCODING_INVALID");
    }

    private void assertRejected(
            Ali1688HistoricalOrderProvider.OrderSnapshot order,
            String code
    ) {
        Ali1688HistoricalOrderFactPreflight.Decision decision =
                preflight.inspectFact(order);
        assertThat(decision.isAccepted()).isFalse();
        assertThat(decision.getSanitizedCode()).isEqualTo(code);
    }

    private Ali1688HistoricalOrderProvider.OrderSnapshot validOrder() {
        Ali1688HistoricalOrderProvider.OrderSnapshot order =
                new Ali1688HistoricalOrderProvider.OrderSnapshot();
        order.setProviderOrderNo("ORDER-1");
        order.setProviderModifiedAt(Instant.parse("2026-08-02T03:00:00Z"));
        order.setOrderTime("2026-08-02 11:00:00");
        Ali1688HistoricalOrderProvider.OrderItemSnapshot item =
                new Ali1688HistoricalOrderProvider.OrderItemSnapshot();
        item.setOfferId("OFFER-1");
        order.setItems(List.of(item));
        return order;
    }

    private String repeat(String value, int count) {
        return value.repeat(count);
    }
}
