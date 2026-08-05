package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderFactPreflight;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

/** Localizes deterministic single-order decisions; transport and container errors never enter here. */
final class Ali1688Dp10OrderValidator {
    private final Ali1688HistoricalOrderFactPreflight factPreflight =
            new Ali1688HistoricalOrderFactPreflight();

    Ali1688Dp10ListEntry classifyListOrder(
            int ordinal,
            Ali1688HistoricalOrderProvider.OrderSnapshot order,
            String rawFingerprint
    ) {
        Ali1688HistoricalOrderFactPreflight.Decision stage =
                factPreflight.inspectStage(order);
        if (!stage.isAccepted()) {
            return skipped(ordinal, order, stage.getSanitizedCode(), rawFingerprint);
        }
        List<Ali1688HistoricalOrderProvider.OrderItemSnapshot> items = validItems(order.getItems());
        order.setItems(items);
        if (!items.isEmpty()) {
            Ali1688HistoricalOrderFactPreflight.Decision fact =
                    factPreflight.inspectFact(order);
            if (!fact.isAccepted()) {
                return skipped(ordinal, order, fact.getSanitizedCode(), rawFingerprint);
            }
        }
        return new Ali1688Dp10ListEntry(
                ordinal,
                order,
                items.isEmpty()
                        ? Ali1688Dp10ItemState.PENDING_DETAIL
                        : Ali1688Dp10ItemState.COMPLETE,
                null,
                rawFingerprint
        );
    }

    Ali1688Dp10DetailDecision validateDetail(
            Ali1688HistoricalOrderProvider.OrderSnapshot base,
            Ali1688HistoricalOrderProvider.OrderSnapshot detail
    ) {
        if (base == null || detail == null) {
            throw new Ali1688Dp10PageContractException("DP10_DETAIL_CONTAINER_INVALID");
        }
        String expected = base.getProviderOrderNo();
        String actual = detail.getProviderOrderNo();
        if (StringUtils.hasText(actual) && !expected.equals(actual.trim())) {
            throw new Ali1688Dp10PageContractException("DP10_DETAIL_IDENTITY_MISMATCH");
        }
        detail.setProviderOrderNo(expected);
        if (detail.getProviderModifiedAt() == null) {
            detail.setProviderModifiedAt(base.getProviderModifiedAt());
        }
        List<Ali1688HistoricalOrderProvider.OrderItemSnapshot> items = validItems(detail.getItems());
        detail.setItems(items);
        if (items.isEmpty()) {
            return new Ali1688Dp10DetailDecision(
                    Ali1688Dp10ItemState.SKIP_BUSINESS_ITEM,
                    null,
                    "DP10_DETAIL_HAS_NO_VALID_ITEMS"
            );
        }
        Ali1688HistoricalOrderFactPreflight.Decision fact =
                factPreflight.inspectFact(detail);
        if (!fact.isAccepted()) {
            return new Ali1688Dp10DetailDecision(
                    Ali1688Dp10ItemState.SKIP_BUSINESS_ITEM,
                    null,
                    fact.getSanitizedCode()
            );
        }
        return new Ali1688Dp10DetailDecision(Ali1688Dp10ItemState.COMPLETE, detail, null);
    }

    private Ali1688Dp10ListEntry skipped(
            int ordinal,
            Ali1688HistoricalOrderProvider.OrderSnapshot order,
            String code,
            String rawFingerprint
    ) {
        return new Ali1688Dp10ListEntry(
                ordinal,
                order,
                Ali1688Dp10ItemState.SKIP_BUSINESS_ITEM,
                code,
                rawFingerprint
        );
    }

    private List<Ali1688HistoricalOrderProvider.OrderItemSnapshot> validItems(
            List<Ali1688HistoricalOrderProvider.OrderItemSnapshot> items
    ) {
        if (items == null) {
            return List.of();
        }
        List<Ali1688HistoricalOrderProvider.OrderItemSnapshot> accepted = new ArrayList<>();
        for (Ali1688HistoricalOrderProvider.OrderItemSnapshot item : items) {
            if (item != null && hasIdentity(item)) {
                accepted.add(item);
            }
        }
        return List.copyOf(accepted);
    }

    private boolean hasIdentity(Ali1688HistoricalOrderProvider.OrderItemSnapshot item) {
        return Ali1688HistoricalOrderFactPreflight.hasStableItemIdentity(item);
    }
}
