package com.nuono.next.procurement.aliorder;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Ali1688Dp10ChildIdentityTest {

    @Test
    void openApiMapperCapturesProviderChildIdsAndRawIdentityEvidence() {
        Ali1688OpenApiJson json = new Ali1688OpenApiJson(new ObjectMapper());
        Ali1688OpenApiOrderMapper mapper = new Ali1688OpenApiOrderMapper(
                new Ali1688HistoricalOrderOpenApiProperties(), json);

        Ali1688HistoricalOrderProvider.OrderItemSnapshot item = mapper.map(json.read(
                "{\"idOfStr\":\"ORDER-1\",\"productItems\":[{"
                        + "\"subOrderId\":\"SUB-1\",\"itemId\":\"ITEM-1\","
                        + "\"offerId\":\"OFFER-1\"}]}"
        )).getItems().get(0);

        assertThat(item.getProviderSubOrderId()).isEqualTo("SUB-1");
        assertThat(item.getProviderItemId()).isEqualTo("ITEM-1");
        assertThat(item.getRawSnapshotJson())
                .contains("\"subOrderId\":\"SUB-1\"")
                .contains("\"itemId\":\"ITEM-1\"");
    }

    @Test
    void mutableDescriptionAloneIsNotAChildIdentityButProviderIdIs() {
        Ali1688HistoricalOrderProvider.OrderItemSnapshot item = item(null, null);
        item.setSkuText("red / large");
        Ali1688HistoricalOrderProvider.OrderSnapshot order = order(item);
        Ali1688HistoricalOrderFactPreflight preflight =
                new Ali1688HistoricalOrderFactPreflight();

        assertThat(preflight.inspectFact(order).getSanitizedCode())
                .isEqualTo("DP10_FACT_ITEM_IDENTITY_INVALID");

        item.setProviderItemId("ITEM-1");
        assertThat(preflight.inspectFact(order).isAccepted()).isTrue();
    }

    @Test
    void reauthorizationKeepsNaturalKeysBoundToExternalAccountIdentity() {
        Ali1688HistoricalOrderAuthorizationRow oldAuthorization = authorization(91_001L);
        Ali1688HistoricalOrderAuthorizationRow newAuthorization = authorization(99_999L);
        Ali1688HistoricalOrderProvider.OrderSnapshot snapshot = order(item("OFFER-1", "SKU-1"));
        Ali1688HistoricalOrderFactRows rows = new Ali1688HistoricalOrderFactRows();

        String oldOrderKey = rows.orderKey(307L, oldAuthorization, snapshot);
        String newOrderKey = rows.orderKey(307L, newAuthorization, snapshot);
        String oldItemKey = key(rows, oldAuthorization, snapshot, 0);
        String newItemKey = key(rows, newAuthorization, snapshot, 0);

        assertThat(newOrderKey).isEqualTo(oldOrderKey);
        assertThat(newItemKey).isEqualTo(oldItemKey);
    }

    @Test
    void providerItemIdentitySurvivesReorderAndInsertionButChangesWithProviderId() {
        Ali1688HistoricalOrderFactRows rows = new Ali1688HistoricalOrderFactRows();
        Ali1688HistoricalOrderAuthorizationRow authorization = authorization(91_001L);
        Ali1688HistoricalOrderProvider.OrderItemSnapshot first = item("OFFER-A", "SKU-A");
        first.setProviderSubOrderId("SUBORDER-A");
        first.setTitle("before");
        Ali1688HistoricalOrderProvider.OrderItemSnapshot second = item("OFFER-B", "SKU-B");
        second.setProviderSubOrderId("SUBORDER-B");
        Ali1688HistoricalOrderProvider.OrderItemSnapshot inserted = item("OFFER-C", "SKU-C");
        inserted.setProviderSubOrderId("SUBORDER-C");

        Ali1688HistoricalOrderProvider.OrderSnapshot original = order(first, second);
        Ali1688HistoricalOrderProvider.OrderSnapshot reordered = order(second, first);
        Ali1688HistoricalOrderProvider.OrderSnapshot withInsert = order(inserted, first, second);
        String originalKey = key(rows, authorization, original, 0);

        first.setTitle("after");
        first.setSkuText("mutable description changed");
        assertThat(key(rows, authorization, reordered, 1)).isEqualTo(originalKey);
        assertThat(key(rows, authorization, withInsert, 1)).isEqualTo(originalKey);

        first.setProviderSubOrderId("SUBORDER-A-CHANGED");
        assertThat(key(rows, authorization, withInsert, 1)).isNotEqualTo(originalKey);
    }

    @Test
    void duplicateFallbackTupleUsesTupleLocalOccurrenceAndNotGlobalPosition() {
        Ali1688HistoricalOrderFactRows rows = new Ali1688HistoricalOrderFactRows();
        Ali1688HistoricalOrderAuthorizationRow authorization = authorization(91_001L);
        Ali1688HistoricalOrderProvider.OrderItemSnapshot duplicateOne = item("OFFER-A", "SKU-A");
        Ali1688HistoricalOrderProvider.OrderItemSnapshot duplicateTwo = item("OFFER-A", "SKU-A");
        duplicateTwo.setTitle("a mutable distinction");
        Ali1688HistoricalOrderProvider.OrderItemSnapshot other = item("OFFER-B", "SKU-B");
        Ali1688HistoricalOrderProvider.OrderSnapshot separated =
                order(duplicateOne, other, duplicateTwo);
        Ali1688HistoricalOrderProvider.OrderSnapshot adjacent =
                order(duplicateOne, duplicateTwo, other);

        Set<String> separatedKeys = Set.of(
                key(rows, authorization, separated, 0),
                key(rows, authorization, separated, 2));
        Set<String> adjacentKeys = Set.of(
                key(rows, authorization, adjacent, 0),
                key(rows, authorization, adjacent, 1));

        assertThat(separatedKeys).hasSize(2).isEqualTo(adjacentKeys);
    }

    @Test
    void logisticsIdentityFollowsStableItemAndIgnoresTrackingMutationOrReorder() {
        Ali1688HistoricalOrderFactRows rows = new Ali1688HistoricalOrderFactRows();
        Ali1688HistoricalOrderAuthorizationRow authorization = authorization(91_001L);
        Ali1688HistoricalOrderProvider.OrderItemSnapshot item = item("OFFER-A", "SKU-A");
        item.setProviderItemId("ITEM-A");
        item.setTrackingNo("TRACK-OLD");
        Ali1688HistoricalOrderProvider.OrderItemSnapshot other = item("OFFER-B", "SKU-B");
        Ali1688HistoricalOrderProvider.OrderSnapshot original = order(item, other);
        String itemKey = key(rows, authorization, original, 0);
        String logisticsKey = rows.logisticsKey(itemKey);

        item.setTrackingNo("TRACK-NEW");
        item.setLogisticsCompany("NEW-COMPANY");
        Ali1688HistoricalOrderProvider.OrderSnapshot reordered = order(other, item);

        assertThat(key(rows, authorization, reordered, 1)).isEqualTo(itemKey);
        assertThat(rows.logisticsKey(key(rows, authorization, reordered, 1)))
                .isEqualTo(logisticsKey);
    }

    private Ali1688HistoricalOrderAuthorizationRow authorization(long id) {
        Ali1688HistoricalOrderAuthorizationRow row = new Ali1688HistoricalOrderAuthorizationRow();
        row.setId(id);
        row.setOwnerUserId(307L);
        row.setProviderCode("ALI1688_OPEN_API");
        row.setProviderAccountId("member-307");
        row.setStatus("authorized");
        return row;
    }

    private Ali1688HistoricalOrderProvider.OrderSnapshot order(
            Ali1688HistoricalOrderProvider.OrderItemSnapshot... items
    ) {
        Ali1688HistoricalOrderProvider.OrderSnapshot order =
                new Ali1688HistoricalOrderProvider.OrderSnapshot();
        order.setProviderOrderNo("ORDER-1");
        order.setProviderModifiedAt(Instant.parse("2026-08-02T03:00:00Z"));
        order.setItems(List.of(items));
        return order;
    }

    private Ali1688HistoricalOrderProvider.OrderItemSnapshot item(
            String offerId,
            String skuId
    ) {
        Ali1688HistoricalOrderProvider.OrderItemSnapshot item =
                new Ali1688HistoricalOrderProvider.OrderItemSnapshot();
        item.setOfferId(offerId);
        item.setSkuId(skuId);
        return item;
    }

    private String key(
            Ali1688HistoricalOrderFactRows rows,
            Ali1688HistoricalOrderAuthorizationRow authorization,
            Ali1688HistoricalOrderProvider.OrderSnapshot order,
            int index
    ) {
        return rows.itemKey(
                307L, authorization, order, order.getItems().get(index),
                rows.identityOccurrence(order.getItems(), index));
    }
}
