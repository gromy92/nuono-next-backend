package com.nuono.next.procurement.aliorder;

import com.nuono.next.datapull.orchestration.DataPullScopeKey;
import java.util.List;
import java.util.Objects;

/** Stable external-account natural keys and bounded fact-row mapping. */
final class Ali1688HistoricalOrderFactRows {
    Ali1688HistoricalOrderRow order(
            Long ownerUserId,
            Ali1688HistoricalOrderAuthorizationRow authorization,
            Long orderId,
            Ali1688HistoricalOrderProvider.OrderSnapshot snapshot
    ) {
        Ali1688HistoricalOrderRow row = new Ali1688HistoricalOrderRow();
        row.setId(orderId);
        row.setOwnerUserId(ownerUserId);
        row.setAuthorizationId(authorization.getId());
        row.setOrderNaturalKey(orderKey(ownerUserId, authorization, snapshot));
        row.setProviderOrderNo(snapshot.getProviderOrderNo());
        row.setOrderTime(snapshot.getOrderTime());
        row.setPaidAt(snapshot.getPaidAt());
        row.setBuyerCompanyName(snapshot.getBuyerCompanyName());
        row.setBuyerMemberName(snapshot.getBuyerMemberName());
        row.setSupplierName(snapshot.getSupplierName());
        row.setSellerMemberName(snapshot.getSellerMemberName());
        row.setGoodsTotalText(snapshot.getGoodsTotalText());
        row.setFreightText(snapshot.getFreightText());
        row.setAdjustmentText(snapshot.getAdjustmentText());
        row.setPaidAmountText(snapshot.getPaidAmountText());
        row.setAmountText(snapshot.getAmountText());
        row.setAmountValue(Ali1688HistoricalOrderFactPreflight.mappedAmount(
                snapshot.getAmountText()));
        row.setCurrency(snapshot.getCurrency());
        row.setOrderStatus(snapshot.getOrderStatus());
        row.setLogisticsStatus(snapshot.getLogisticsStatus());
        row.setShipperName(snapshot.getShipperName());
        row.setOriginalUrl(snapshot.getOriginalUrl());
        row.setReceiverName(snapshot.getReceiverName());
        row.setReceiverPostalCode(snapshot.getReceiverPostalCode());
        row.setReceiverTelephone(snapshot.getReceiverTelephone());
        row.setReceiverMobile(snapshot.getReceiverMobile());
        row.setReceiverPhone(snapshot.getReceiverPhone());
        row.setReceiverAddress(snapshot.getReceiverAddress());
        row.setBuyerRemark(snapshot.getBuyerRemark());
        row.setSupplierContact(snapshot.getSupplierContact());
        row.setInitiatorLoginName(snapshot.getInitiatorLoginName());
        row.setSourceBatchNo(snapshot.getSourceBatchNo());
        row.setDownstreamOrderNo(snapshot.getDownstreamOrderNo());
        row.setRawSnapshotJson(snapshot.getRawSnapshotJson());
        return row;
    }

    Ali1688HistoricalOrderItemRow item(
            Long ownerUserId,
            Long itemId,
            Long orderId,
            Ali1688HistoricalOrderAuthorizationRow authorization,
            Ali1688HistoricalOrderProvider.OrderSnapshot order,
            Ali1688HistoricalOrderProvider.OrderItemSnapshot snapshot,
            int identityOccurrence
    ) {
        Ali1688HistoricalOrderItemRow row = new Ali1688HistoricalOrderItemRow();
        row.setId(itemId);
        row.setOrderId(orderId);
        row.setItemNaturalKey(itemKey(
                ownerUserId, authorization, order, snapshot, identityOccurrence));
        row.setOfferId(snapshot.getOfferId());
        row.setSkuId(snapshot.getSkuId());
        row.setTitle(snapshot.getTitle());
        row.setSkuText(snapshot.getSkuText());
        row.setModelText(snapshot.getModelText());
        row.setProductCode(snapshot.getProductCode());
        row.setSingleProductCode(snapshot.getSingleProductCode());
        row.setQuantity(snapshot.getQuantity());
        row.setUnit(snapshot.getUnit());
        row.setUnitPriceText(snapshot.getUnitPriceText());
        row.setAmountText(snapshot.getAmountText());
        row.setImageUrl(snapshot.getImageUrl());
        row.setRawSnapshotJson(snapshot.getRawSnapshotJson());
        return row;
    }

    Ali1688HistoricalOrderLogisticsRow logistics(
            Long logisticsId,
            Long orderId,
            Long itemId,
            Ali1688HistoricalOrderProvider.OrderItemSnapshot snapshot,
            String itemNaturalKey
    ) {
        Ali1688HistoricalOrderLogisticsRow row = new Ali1688HistoricalOrderLogisticsRow();
        row.setId(logisticsId);
        row.setOrderId(orderId);
        row.setItemId(itemId);
        row.setLogisticsNaturalKey(logisticsKey(itemNaturalKey));
        row.setLogisticsCompany(snapshot.getLogisticsCompany());
        row.setTrackingNo(snapshot.getTrackingNo());
        row.setRawSnapshotJson(snapshot.getRawSnapshotJson());
        return row;
    }

    String itemKey(
            Long ownerUserId,
            Ali1688HistoricalOrderAuthorizationRow authorization,
            Ali1688HistoricalOrderProvider.OrderSnapshot order,
            Ali1688HistoricalOrderProvider.OrderItemSnapshot item,
            int identityOccurrence
    ) {
        if (identityOccurrence < 1) {
            throw new IllegalArgumentException("1688 item identity occurrence must be positive");
        }
        String identity = itemIdentity(item);
        if (hasProviderIdentity(item)) {
            return DataPullScopeKey.from(
                    "ALI1688_ORDER_ITEM",
                    ownerUserId.toString(),
                    providerAccount(authorization),
                    order.getProviderOrderNo(),
                    identity
            );
        }
        return DataPullScopeKey.from(
                "ALI1688_ORDER_ITEM",
                ownerUserId.toString(),
                providerAccount(authorization),
                order.getProviderOrderNo(),
                identity,
                String.valueOf(identityOccurrence)
        );
    }

    String itemIdentity(Ali1688HistoricalOrderProvider.OrderItemSnapshot item) {
        if (item == null) throw new IllegalArgumentException("1688 item identity is required");
        if (hasText(item.getProviderSubOrderId())) {
            return "PROVIDER_SUBORDER:" + item.getProviderSubOrderId().trim();
        }
        if (hasText(item.getProviderItemId())) {
            return "PROVIDER_ITEM:" + item.getProviderItemId().trim();
        }
        if (!Ali1688HistoricalOrderFactPreflight.hasStableItemIdentity(item)) {
            throw new IllegalArgumentException("1688 stable item identity is required");
        }
        return DataPullScopeKey.from(
                "ALI1688_ORDER_ITEM_FALLBACK",
                value(item.getOfferId()),
                value(item.getSkuId()),
                value(item.getProductCode()),
                value(item.getSingleProductCode())
        );
    }

    int identityOccurrence(
            List<Ali1688HistoricalOrderProvider.OrderItemSnapshot> items,
            int itemIndex
    ) {
        if (items == null || itemIndex < 0 || itemIndex >= items.size()) {
            throw new IllegalArgumentException("1688 item occurrence locator is invalid");
        }
        if (hasProviderIdentity(items.get(itemIndex))) return 1;
        String identity = itemIdentity(items.get(itemIndex));
        int occurrence = 1;
        for (int index = 0; index < itemIndex; index++) {
            if (Objects.equals(identity, itemIdentity(items.get(index)))) occurrence++;
        }
        return occurrence;
    }

    boolean hasProviderIdentity(Ali1688HistoricalOrderProvider.OrderItemSnapshot item) {
        return item != null && (hasText(item.getProviderSubOrderId())
                || hasText(item.getProviderItemId()));
    }

    boolean isFirstProviderIdentity(
            List<Ali1688HistoricalOrderProvider.OrderItemSnapshot> items,
            int itemIndex
    ) {
        if (items == null || itemIndex < 0 || itemIndex >= items.size()) {
            throw new IllegalArgumentException("1688 item identity locator is invalid");
        }
        Ali1688HistoricalOrderProvider.OrderItemSnapshot item = items.get(itemIndex);
        if (!hasProviderIdentity(item)) return true;
        String identity = itemIdentity(item);
        for (int index = 0; index < itemIndex; index++) {
            if (Objects.equals(identity, itemIdentity(items.get(index)))) return false;
        }
        return true;
    }

    String normalizedFallbackPart(String value) {
        return hasText(value) ? value.trim() : "";
    }

    String logisticsKey(String itemNaturalKey) {
        return DataPullScopeKey.from("ALI1688_ORDER_LOGISTICS", itemNaturalKey);
    }

    String orderKey(
            Long ownerUserId,
            Ali1688HistoricalOrderAuthorizationRow authorization,
            Ali1688HistoricalOrderProvider.OrderSnapshot snapshot
    ) {
        return DataPullScopeKey.from(
                "ALI1688_ORDER",
                ownerUserId.toString(),
                providerAccount(authorization),
                snapshot.getProviderOrderNo()
        );
    }

    private String providerAccount(Ali1688HistoricalOrderAuthorizationRow authorization) {
        String value = authorization.getProviderAccountId();
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException("1688 provider account identity is required");
        }
        return value;
    }

    private String value(String value) {
        return value == null || value.trim().isEmpty()
                ? "MISSING"
                : "VALUE:" + value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
