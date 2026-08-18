package com.nuono.next.procurement.aliorder;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10FactLookupMapper;
import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Per-order transaction for the predecessor DP-10 scheduler. */
@Service
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.LEGACY)
class LegacyAli1688HistoricalOrderFactWriter {
    private final Ali1688HistoricalOrderMapper mapper;
    private final Ali1688Dp10FactLookupMapper facts;
    private final Ali1688HistoricalOrderFactRows rows = new Ali1688HistoricalOrderFactRows();
    private final Ali1688HistoricalOrderFactPreflight preflight =
            new Ali1688HistoricalOrderFactPreflight();

    LegacyAli1688HistoricalOrderFactWriter(
            Ali1688HistoricalOrderMapper mapper,
            Ali1688Dp10FactLookupMapper facts
    ) {
        this.mapper = mapper;
        this.facts = facts;
    }

    @Transactional
    WriteResult write(
            Long ownerUserId,
            Ali1688HistoricalOrderAuthorizationRow authorization,
            Ali1688HistoricalOrderProvider.OrderSnapshot snapshot
    ) {
        Ali1688HistoricalOrderFactPreflight.Decision decision =
                preflight.inspectFact(snapshot);
        if (!decision.isAccepted()) {
            return WriteResult.skipped(decision.getSanitizedCode());
        }
        String orderKey = rows.orderKey(ownerUserId, authorization, snapshot);
        List<Ali1688Dp10OrderHeaderIdentityRow> identities =
                facts.selectCanonicalOrderHeadersForUpdate(
                        ownerUserId,
                        authorization.getProviderCode(),
                        authorization.getProviderAccountId(),
                        snapshot.getProviderOrderNo(),
                        orderKey);
        if (identities == null || identities.size() > 1) {
            return WriteResult.skipped("DP10_ORDER_HEADER_IDENTITY_AMBIGUOUS");
        }
        Ali1688Dp10OrderHeaderIdentityRow identity = identities.isEmpty()
                ? null : identities.get(0);
        if (identity != null && (!sameSourceIdentity(identity, authorization, snapshot)
                || identity.getDeleted() == null || positive(identity.getId()) == null)) {
            return WriteResult.skipped("DP10_ORDER_HEADER_IDENTITY_AMBIGUOUS");
        }
        if (identity != null && Boolean.TRUE.equals(identity.getDeleted())) {
            return WriteResult.skipped("DP10_ORDER_HEADER_MANUALLY_DELETED");
        }
        Long orderId = identity == null ? null : positive(identity.getId());
        boolean existingOrder = orderId != null;
        Ali1688HistoricalOrderRow order = rows.order(
                ownerUserId,
                authorization,
                existingOrder ? orderId : mapper.nextOrderId(),
                snapshot
        );
        mapper.upsertOrder(order);
        orderId = requireCanonical(order.getId(), "order");

        List<Ali1688HistoricalOrderProvider.OrderItemSnapshot> items = snapshot.getItems();
        int written = 0;
        for (int index = 0; index < items.size(); index++) {
            if (!rows.isFirstProviderIdentity(items, index)) continue;
            Ali1688HistoricalOrderProvider.OrderItemSnapshot itemSnapshot = items.get(index);
            Ali1688HistoricalOrderItemRow item = rows.item(
                    ownerUserId,
                    null,
                    orderId,
                    authorization,
                    snapshot,
                    itemSnapshot,
                    rows.identityOccurrence(items, index)
            );
            Long itemId = mapper.selectOrderItemIdByNaturalKey(
                    ownerUserId, item.getItemNaturalKey());
            if (positive(itemId) == null) {
                itemId = facts.selectAnyCanonicalItemIdByNaturalKey(
                        orderId, item.getItemNaturalKey());
            }
            if (positive(itemId) == null && existingOrder) {
                itemId = facts.selectCanonicalItemIdByStableTuple(
                        orderId,
                        rows.normalizedFallbackPart(itemSnapshot.getOfferId()),
                        rows.normalizedFallbackPart(itemSnapshot.getSkuId()),
                        rows.normalizedFallbackPart(itemSnapshot.getProductCode()),
                        rows.normalizedFallbackPart(itemSnapshot.getSingleProductCode()),
                        rows.stableTupleOccurrence(items, index) - 1);
            }
            item.setId(positive(itemId) == null ? mapper.nextOrderItemId() : itemId);
            mapper.upsertOrderItem(item);
            itemId = requireCanonical(item.getId(), "item");
            facts.activateCanonicalItemIdentity(itemId, orderId, item.getItemNaturalKey());
            if (hasText(itemSnapshot.getLogisticsCompany())
                    || hasText(itemSnapshot.getTrackingNo())) {
                Ali1688HistoricalOrderLogisticsRow logistics = rows.logistics(
                        null, orderId, itemId, itemSnapshot, item.getItemNaturalKey());
                Long logisticsId = mapper.selectOrderLogisticsIdByNaturalKey(
                        ownerUserId, logistics.getLogisticsNaturalKey());
                if (positive(logisticsId) == null) {
                    logisticsId = facts.selectAnyCanonicalLogisticsIdByNaturalKey(
                            orderId, logistics.getLogisticsNaturalKey());
                }
                if (positive(logisticsId) == null) {
                    logisticsId = facts.selectCanonicalLogisticsId(orderId, itemId);
                }
                logistics.setId(positive(logisticsId) == null
                        ? mapper.nextOrderLogisticsId()
                        : logisticsId);
                mapper.upsertOrderLogistics(logistics);
                logisticsId = requireCanonical(logistics.getId(), "logistics");
                facts.activateCanonicalLogisticsIdentity(
                        logisticsId, orderId, itemId, logistics.getLogisticsNaturalKey());
            }
            written++;
        }
        return WriteResult.written(written);
    }

    private boolean sameSourceIdentity(
            Ali1688Dp10OrderHeaderIdentityRow identity,
            Ali1688HistoricalOrderAuthorizationRow authorization,
            Ali1688HistoricalOrderProvider.OrderSnapshot snapshot
    ) {
        return Ali1688HistoricalOrderSourceIdentity.compatible(
                identity.getProviderCode(), authorization.getProviderCode())
                && snapshot.getProviderOrderNo().equals(identity.getProviderOrderNo());
    }

    private Long requireCanonical(Long value, String type) {
        Long id = positive(value);
        if (id == null) {
            throw new IllegalStateException("Legacy 1688 " + type + " canonical id missing");
        }
        return id;
    }

    private Long positive(Long value) {
        return value != null && value > 0L ? value : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    static final class WriteResult {
        private final int itemCount;
        private final String failureCode;

        private WriteResult(int itemCount, String failureCode) {
            this.itemCount = itemCount;
            this.failureCode = failureCode;
        }

        static WriteResult written(int itemCount) {
            return new WriteResult(Math.max(0, itemCount), null);
        }

        static WriteResult skipped(String failureCode) {
            return new WriteResult(0, failureCode);
        }

        int getItemCount() { return itemCount; }
        String getFailureCode() { return failureCode; }
        boolean isSkipped() { return failureCode != null; }
    }
}
