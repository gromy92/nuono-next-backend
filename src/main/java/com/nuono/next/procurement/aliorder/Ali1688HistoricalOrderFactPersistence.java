package com.nuono.next.procurement.aliorder;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10FactLookupMapper;
import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10ApplySlice;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10FactSegmentResult;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10FactSegmentWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Persists one bounded segment of a verified, sealed DP-10 order. */
@Service
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
final class Ali1688HistoricalOrderFactPersistence implements Ali1688Dp10FactSegmentWriter {
    private final Ali1688HistoricalOrderMapper mapper;
    private final Ali1688Dp10FactLookupMapper dp10Facts;
    private final Ali1688HistoricalOrderFactRows rows = new Ali1688HistoricalOrderFactRows();

    Ali1688HistoricalOrderFactPersistence(
            Ali1688HistoricalOrderMapper mapper,
            Ali1688Dp10FactLookupMapper dp10Facts
    ) {
        this.mapper = mapper;
        this.dp10Facts = dp10Facts;
    }

    @Override
    public Ali1688Dp10FactSegmentResult applySegment(
            DataPullTask task,
            Ali1688HistoricalOrderAuthorizationRow authorization,
            Ali1688Dp10ApplySlice slice,
            int maxFactRows
    ) {
        SegmentResult result = persistSegment(task, authorization, slice, maxFactRows);
        return result.isBusinessSkipped()
                ? Ali1688Dp10FactSegmentResult.businessSkipped(result.getBusinessSkipCode())
                : Ali1688Dp10FactSegmentResult.advanced(result.getNextItemCursor());
    }

    /** Applies a deterministic whole-item segment whose item+logistics row weight is bounded. */
    SegmentResult persistSegment(
            DataPullTask task,
            Ali1688HistoricalOrderAuthorizationRow authorization,
            Ali1688Dp10ApplySlice slice,
            int maxFactRows
    ) {
        if (dp10Facts == null) {
            throw new IllegalStateException("DP10_FACT_LOOKUP_MAPPER_MISSING");
        }
        Long ownerUserId = task == null ? null : task.getOwnerUserId();
        Ali1688HistoricalOrderProvider.OrderSnapshot snapshot =
                slice == null ? null : slice.getOrder();
        int itemCursor = slice == null ? -1 : slice.getItemCursor();
        if (task == null || task.getId() == null
                || task.getOperationCode() != OperationCode.DP10
                || task.getState() != TaskState.RUNNING
                || task.getFenceEpoch() == null || task.getLeaseUntil() == null
                || task.getLeaseOwner() == null || task.getLeaseOwner().isBlank()
                || task.getAccountKey() == null || task.getAccountKey().isBlank()
                || task.getScopeKey() == null || task.getScopeKey().isBlank()
                || ownerUserId == null || authorization == null
                || !ownerUserId.equals(authorization.getOwnerUserId())
                || !Ali1688HistoricalOrderOAuthService.PROVIDER_CODE.equals(
                        authorization.getProviderCode())
                || slice == null
                || slice.getGenerationNo() <= 0L
                || slice.getPartition() == null || slice.getPartition().isBlank()
                || slice.getPageNo() < 1 || slice.getItemOrdinal() < 0
                || snapshot == null || itemCursor < 0 || maxFactRows < 2
                || snapshot.getItems() == null || itemCursor >= snapshot.getItems().size()) {
            throw new IllegalArgumentException("1688 fact segment locator is invalid");
        }
        String orderKey = rows.orderKey(ownerUserId, authorization, snapshot);
        List<Ali1688Dp10OrderHeaderIdentityRow> identities =
                dp10Facts.selectCanonicalOrderHeadersForUpdate(
                        ownerUserId,
                        authorization.getProviderCode(),
                        authorization.getProviderAccountId(),
                        snapshot.getProviderOrderNo(),
                        orderKey);
        if (identities == null || identities.size() > 1) {
            return SegmentResult.businessSkipped(
                    "DP10_ORDER_HEADER_IDENTITY_AMBIGUOUS");
        }
        Ali1688Dp10OrderHeaderIdentityRow identity = identities.isEmpty()
                ? null : identities.get(0);
        if (identity != null && !sameSourceIdentity(identity, authorization, snapshot)) {
            return SegmentResult.businessSkipped(
                    "DP10_ORDER_HEADER_IDENTITY_AMBIGUOUS");
        }
        if (identity != null && identity.getDeleted() == null) {
            return SegmentResult.businessSkipped(
                    "DP10_ORDER_HEADER_IDENTITY_AMBIGUOUS");
        }
        if (identity != null && Boolean.TRUE.equals(identity.getDeleted())) {
            return SegmentResult.businessSkipped(
                    "DP10_ORDER_HEADER_MANUALLY_DELETED");
        }
        Long orderId = identity == null ? null : positive(identity.getId());
        if (identity != null && orderId == null) {
            return SegmentResult.businessSkipped(
                    "DP10_ORDER_HEADER_IDENTITY_AMBIGUOUS");
        }
        boolean existingOrder = orderId != null;
        Ali1688HistoricalOrderRow order = rows.order(
                ownerUserId, authorization,
                existingOrder ? orderId : mapper.nextOrderId(), snapshot);
        mapper.upsertOrder(order);
        orderId = canonical(order.getId(), "order");
        int cursor = itemCursor;
        int factRows = 0;
        while (cursor < snapshot.getItems().size()) {
            Ali1688HistoricalOrderProvider.OrderItemSnapshot itemSnapshot =
                    snapshot.getItems().get(cursor);
            if (!rows.isFirstProviderIdentity(snapshot.getItems(), cursor)) {
                cursor++;
                continue;
            }
            int weight = 1 + (hasText(itemSnapshot.getLogisticsCompany())
                    || hasText(itemSnapshot.getTrackingNo()) ? 1 : 0);
            if (factRows > 0 && factRows + weight > maxFactRows) break;
            int identityOccurrence = rows.identityOccurrence(snapshot.getItems(), cursor);
            Ali1688HistoricalOrderItemRow item = rows.item(
                    ownerUserId, null, orderId, authorization, snapshot,
                    itemSnapshot, identityOccurrence);
            Long itemId = positive(mapper.selectOrderItemIdByNaturalKey(
                    ownerUserId, item.getItemNaturalKey()));
            if (itemId == null) {
                itemId = positive(dp10Facts.selectAnyCanonicalItemIdByNaturalKey(
                        orderId, item.getItemNaturalKey()));
            }
            if (itemId == null && existingOrder) {
                itemId = positive(dp10Facts.selectCanonicalItemIdByStableTuple(
                        orderId,
                        rows.normalizedFallbackPart(itemSnapshot.getOfferId()),
                        rows.normalizedFallbackPart(itemSnapshot.getSkuId()),
                        rows.normalizedFallbackPart(itemSnapshot.getProductCode()),
                        rows.normalizedFallbackPart(itemSnapshot.getSingleProductCode()),
                        rows.stableTupleOccurrence(snapshot.getItems(), cursor) - 1
                ));
            }
            item.setId(itemId == null ? mapper.nextOrderItemId() : itemId);
            mapper.upsertOrderItem(item);
            itemId = canonical(item.getId(), "order item");
            dp10Facts.activateCanonicalItemIdentity(
                    itemId, orderId, item.getItemNaturalKey());
            if (weight == 2) {
                Ali1688HistoricalOrderLogisticsRow logistics = rows.logistics(
                        null, orderId, itemId, itemSnapshot, item.getItemNaturalKey());
                Long logisticsId = positive(mapper.selectOrderLogisticsIdByNaturalKey(
                        ownerUserId, logistics.getLogisticsNaturalKey()));
                if (logisticsId == null) {
                    logisticsId = positive(
                            dp10Facts.selectAnyCanonicalLogisticsIdByNaturalKey(
                                    orderId, logistics.getLogisticsNaturalKey()));
                }
                if (logisticsId == null) {
                    logisticsId = positive(
                            dp10Facts.selectCanonicalLogisticsId(orderId, itemId));
                }
                logistics.setId(logisticsId == null ? mapper.nextOrderLogisticsId() : logisticsId);
                mapper.upsertOrderLogistics(logistics);
                logisticsId = canonical(logistics.getId(), "order logistics");
                dp10Facts.activateCanonicalLogisticsIdentity(
                        logisticsId, orderId, itemId, logistics.getLogisticsNaturalKey());
            }
            factRows += weight;
            cursor++;
        }
        if (cursor == snapshot.getItems().size()) {
            finalizeAuthoritativeChildren(task, slice, orderId, authorization, snapshot);
        }
        return new SegmentResult(cursor, factRows);
    }

    private boolean sameSourceIdentity(
            Ali1688Dp10OrderHeaderIdentityRow identity,
            Ali1688HistoricalOrderAuthorizationRow authorization,
            Ali1688HistoricalOrderProvider.OrderSnapshot snapshot
    ) {
        return identity != null
                && Ali1688HistoricalOrderSourceIdentity.compatible(
                        identity.getProviderCode(), authorization.getProviderCode())
                && snapshot.getProviderOrderNo().equals(identity.getProviderOrderNo());
    }

    private void finalizeAuthoritativeChildren(
            DataPullTask task,
            Ali1688Dp10ApplySlice slice,
            Long orderId,
            Ali1688HistoricalOrderAuthorizationRow authorization,
            Ali1688HistoricalOrderProvider.OrderSnapshot snapshot
    ) {
        List<String> itemKeys = new ArrayList<>();
        List<String> logisticsKeys = new ArrayList<>();
        for (int index = 0; index < snapshot.getItems().size(); index++) {
            if (!rows.isFirstProviderIdentity(snapshot.getItems(), index)) continue;
            Ali1688HistoricalOrderProvider.OrderItemSnapshot item =
                    snapshot.getItems().get(index);
            String itemKey = rows.itemKey(
                    task.getOwnerUserId(), authorization, snapshot, item,
                    rows.identityOccurrence(snapshot.getItems(), index));
            itemKeys.add(itemKey);
            if (hasText(item.getLogisticsCompany()) || hasText(item.getTrackingNo())) {
                logisticsKeys.add(rows.logisticsKey(itemKey));
            }
        }
        List<String> authoritativeItemKeys = List.copyOf(new LinkedHashSet<>(itemKeys));
        List<String> authoritativeLogisticsKeys =
                List.copyOf(new LinkedHashSet<>(logisticsKeys));
        if (dp10Facts.countDp10ChildFinalizeFence(task, slice) != 1) {
            throw new IllegalStateException("DP10_CHILD_FINALIZE_FENCE_STALE");
        }
        dp10Facts.softRetireDp10LogisticsMissingFromAuthoritativeSet(
                task, slice, orderId, authoritativeLogisticsKeys);
        dp10Facts.softRetireDp10ItemsMissingFromAuthoritativeSet(
                task, slice, orderId, authoritativeItemKeys);
    }

    private Long canonical(Long id, String type) {
        if (id == null || id <= 0L) {
            throw new IllegalStateException("1688 provider " + type + " canonical id missing");
        }
        return id;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private Long positive(Long id) {
        return id != null && id > 0L ? id : null;
    }

    static final class SegmentResult {
        private final int nextItemCursor;
        private final int factRows;
        private final String businessSkipCode;

        SegmentResult(int nextItemCursor, int factRows) {
            this(nextItemCursor, factRows, null);
        }

        private SegmentResult(int nextItemCursor, int factRows, String businessSkipCode) {
            this.nextItemCursor = nextItemCursor;
            this.factRows = factRows;
            this.businessSkipCode = businessSkipCode;
        }

        static SegmentResult businessSkipped(String code) {
            return new SegmentResult(-1, 0, code);
        }

        int getNextItemCursor() { return nextItemCursor; }
        int getFactRows() { return factRows; }
        boolean isBusinessSkipped() { return businessSkipCode != null; }
        String getBusinessSkipCode() { return businessSkipCode; }
    }
}
