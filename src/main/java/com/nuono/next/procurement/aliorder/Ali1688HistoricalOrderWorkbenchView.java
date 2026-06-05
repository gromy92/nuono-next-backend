package com.nuono.next.procurement.aliorder;

import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.ArrayList;
import java.util.List;

public class Ali1688HistoricalOrderWorkbenchView {

    private boolean ready;
    private String mode;
    private String message;
    private AuthorizationView authorization;
    private StoreScopeView storeScope;
    private RoleCapabilities roleCapabilities;
    private SyncSummaryView syncSummary;
    private List<OrderRowView> orders = new ArrayList<>();
    private PaginationView pagination;

    public static Ali1688HistoricalOrderWorkbenchView noAuthorization(BusinessAccessContext context) {
        return base(context, AuthorizationView.notAuthorized(), false);
    }

    public static Ali1688HistoricalOrderWorkbenchView noAuthorization(
            BusinessAccessContext context,
            Ali1688HistoricalOrderQuery query
    ) {
        Ali1688HistoricalOrderWorkbenchView view = noAuthorization(context);
        view.setStoreScope(StoreScopeView.noAuthorization(query));
        return view;
    }

    public static Ali1688HistoricalOrderWorkbenchView authorizedDev(BusinessAccessContext context, Long authorizationId) {
        Ali1688HistoricalOrderAuthorizationRow row = new Ali1688HistoricalOrderAuthorizationRow();
        row.setId(authorizationId);
        row.setStatus("authorized");
        row.setProviderCode(LocalDbAli1688HistoricalOrderService.DEV_PROVIDER_CODE);
        row.setProviderAccountId("dev-1688-" + (context == null ? "" : context.getBusinessOwnerUserId()));
        row.setAccountLabel(LocalDbAli1688HistoricalOrderService.DEV_ACCOUNT_LABEL);
        row.setScopeSummary(LocalDbAli1688HistoricalOrderService.ORDER_READ_SCOPE);
        return authorized(context, row);
    }

    public static Ali1688HistoricalOrderWorkbenchView authorized(
            BusinessAccessContext context,
            Ali1688HistoricalOrderAuthorizationRow authorization
    ) {
        return base(context, AuthorizationView.fromRow(authorization), true);
    }

    public static Ali1688HistoricalOrderWorkbenchView authorizedWithOrders(
            BusinessAccessContext context,
            Ali1688HistoricalOrderAuthorizationRow authorization,
            List<OrderRowView> orders,
            int totalOrderCount,
            int totalItemCount,
            Ali1688HistoricalOrderSyncTaskRow latestTask,
            Ali1688HistoricalOrderQuery query,
            StoreScopeView storeScope
    ) {
        Ali1688HistoricalOrderWorkbenchView view = authorized(context, authorization);
        view.setStoreScope(storeScope);
        view.setOrders(orders);
        view.setSyncSummary(SyncSummaryView.fromCounts(totalOrderCount, totalItemCount, latestTask));
        PaginationView pagination = new PaginationView();
        Ali1688HistoricalOrderQuery resolvedQuery = query == null
                ? Ali1688HistoricalOrderQuery.defaultQuery()
                : query;
        pagination.setPage(resolvedQuery.getPage());
        pagination.setPageSize(resolvedQuery.getPageSize());
        pagination.setTotal(totalOrderCount);
        view.setPagination(pagination);
        return view;
    }

    private static Ali1688HistoricalOrderWorkbenchView base(
            BusinessAccessContext context,
            AuthorizationView authorization,
            boolean canTriggerSync
    ) {
        Ali1688HistoricalOrderWorkbenchView view = new Ali1688HistoricalOrderWorkbenchView();
        view.setReady(true);
        view.setMode("local-db");
        view.setMessage(authorization.getMessage());
        view.setAuthorization(authorization);
        view.setStoreScope(StoreScopeView.ownerScope());
        view.setRoleCapabilities(RoleCapabilities.fromContext(context, canTriggerSync));
        view.setSyncSummary(SyncSummaryView.notStarted());
        view.setOrders(List.of());
        PaginationView pagination = new PaginationView();
        pagination.setPage(1);
        pagination.setPageSize(20);
        pagination.setTotal(0);
        view.setPagination(pagination);
        return view;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public AuthorizationView getAuthorization() {
        return authorization;
    }

    public void setAuthorization(AuthorizationView authorization) {
        this.authorization = authorization;
    }

    public StoreScopeView getStoreScope() {
        return storeScope;
    }

    public void setStoreScope(StoreScopeView storeScope) {
        this.storeScope = storeScope == null ? StoreScopeView.ownerScope() : storeScope;
    }

    public RoleCapabilities getRoleCapabilities() {
        return roleCapabilities;
    }

    public void setRoleCapabilities(RoleCapabilities roleCapabilities) {
        this.roleCapabilities = roleCapabilities;
    }

    public SyncSummaryView getSyncSummary() {
        return syncSummary;
    }

    public void setSyncSummary(SyncSummaryView syncSummary) {
        this.syncSummary = syncSummary;
    }

    public List<OrderRowView> getOrders() {
        return orders;
    }

    public void setOrders(List<OrderRowView> orders) {
        this.orders = orders == null ? List.of() : orders;
    }

    public PaginationView getPagination() {
        return pagination;
    }

    public void setPagination(PaginationView pagination) {
        this.pagination = pagination;
    }

    public static class AuthorizationView {
        private Long authorizationId;
        private String status;
        private String message;
        private String providerCode;
        private String providerAccountId;
        private String accountLabel;
        private String scopeSummary;
        private String expiresAt;

        static AuthorizationView notAuthorized() {
            AuthorizationView view = new AuthorizationView();
            view.setStatus("not_authorized");
            view.setMessage("老板授权后可同步 1688 历史订单");
            return view;
        }

        static AuthorizationView fromRow(Ali1688HistoricalOrderAuthorizationRow row) {
            AuthorizationView view = new AuthorizationView();
            view.setAuthorizationId(row.getId());
            view.setStatus(row.getStatus());
            view.setMessage("1688 历史订单授权已连接。");
            view.setProviderCode(row.getProviderCode());
            view.setProviderAccountId(row.getProviderAccountId());
            view.setAccountLabel(row.getAccountLabel());
            view.setScopeSummary(row.getScopeSummary());
            view.setExpiresAt(row.getExpiresAt() == null ? null : row.getExpiresAt().toString());
            return view;
        }

        public Long getAuthorizationId() {
            return authorizationId;
        }

        public void setAuthorizationId(Long authorizationId) {
            this.authorizationId = authorizationId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getProviderCode() {
            return providerCode;
        }

        public void setProviderCode(String providerCode) {
            this.providerCode = providerCode;
        }

        public String getProviderAccountId() {
            return providerAccountId;
        }

        public void setProviderAccountId(String providerAccountId) {
            this.providerAccountId = providerAccountId;
        }

        public String getAccountLabel() {
            return accountLabel;
        }

        public void setAccountLabel(String accountLabel) {
            this.accountLabel = accountLabel;
        }

        public String getScopeSummary() {
            return scopeSummary;
        }

        public void setScopeSummary(String scopeSummary) {
            this.scopeSummary = scopeSummary;
        }

        public String getExpiresAt() {
            return expiresAt;
        }

        public void setExpiresAt(String expiresAt) {
            this.expiresAt = expiresAt;
        }
    }

    public static class StoreScopeView {
        private String status;
        private String storeCode;
        private String siteCode;
        private String message;
        private List<String> matchedAuthorizationIds = new ArrayList<>();

        static StoreScopeView ownerScope() {
            StoreScopeView view = new StoreScopeView();
            view.setStatus("owner_scope");
            view.setMessage("当前按老板全部 1688 授权账号查看。");
            return view;
        }

        static StoreScopeView noAuthorization(Ali1688HistoricalOrderQuery query) {
            StoreScopeView view = scoped(query);
            view.setStatus("no_authorization");
            view.setMessage("当前老板还没有 1688 授权账号。");
            return view;
        }

        static StoreScopeView unbound(Ali1688HistoricalOrderQuery query) {
            StoreScopeView view = scoped(query);
            view.setStatus("unbound");
            view.setMessage("当前店铺还没有绑定 1688 授权账号，订单先进入未归属范围。");
            return view;
        }

        static StoreScopeView bound(Ali1688HistoricalOrderQuery query, List<Long> authorizationIds) {
            StoreScopeView view = scoped(query);
            view.setStatus(hasText(view.getStoreCode()) ? "bound" : "owner_scope");
            view.setMatchedAuthorizationIds(toStringIds(authorizationIds));
            view.setMessage(hasText(view.getStoreCode())
                    ? "当前按店铺绑定的 1688 授权账号查看。"
                    : "当前按老板全部 1688 授权账号查看。");
            return view;
        }

        private static StoreScopeView scoped(Ali1688HistoricalOrderQuery query) {
            StoreScopeView view = new StoreScopeView();
            view.setStoreCode(query == null ? null : query.getStoreCode());
            view.setSiteCode(query == null ? null : query.getSiteCode());
            return view;
        }

        private static List<String> toStringIds(List<Long> values) {
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            List<String> ids = new ArrayList<>();
            for (Long value : values) {
                ids.add(String.valueOf(value));
            }
            return ids;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getStoreCode() {
            return storeCode;
        }

        public void setStoreCode(String storeCode) {
            this.storeCode = storeCode;
        }

        public String getSiteCode() {
            return siteCode;
        }

        public void setSiteCode(String siteCode) {
            this.siteCode = siteCode;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public List<String> getMatchedAuthorizationIds() {
            return matchedAuthorizationIds;
        }

        public void setMatchedAuthorizationIds(List<String> matchedAuthorizationIds) {
            this.matchedAuthorizationIds = matchedAuthorizationIds == null ? List.of() : matchedAuthorizationIds;
        }
    }

    public static class RoleCapabilities {
        private boolean canAuthorize;
        private boolean canTriggerSync;
        private boolean canViewOrders;

        static RoleCapabilities fromContext(BusinessAccessContext context, boolean canTriggerSync) {
            RoleCapabilities capabilities = new RoleCapabilities();
            capabilities.setCanAuthorize(context != null && context.isBossAccount());
            capabilities.setCanTriggerSync(canTriggerSync && Ali1688HistoricalOrderPermission.canTriggerSync(context));
            capabilities.setCanViewOrders(true);
            return capabilities;
        }

        public boolean isCanAuthorize() {
            return canAuthorize;
        }

        public void setCanAuthorize(boolean canAuthorize) {
            this.canAuthorize = canAuthorize;
        }

        public boolean isCanTriggerSync() {
            return canTriggerSync;
        }

        public void setCanTriggerSync(boolean canTriggerSync) {
            this.canTriggerSync = canTriggerSync;
        }

        public boolean isCanViewOrders() {
            return canViewOrders;
        }

        public void setCanViewOrders(boolean canViewOrders) {
            this.canViewOrders = canViewOrders;
        }
    }

    public static class SyncSummaryView {
        private String latestTaskStatus;
        private int totalOrderCount;
        private int totalItemCount;
        private int processedCount;
        private int importedCount;
        private int failedCount;
        private int progressPercent;
        private String failureCode;
        private String failureMessage;
        private boolean retryable;
        private boolean requiresManualAction;
        private String checkpointJson;

        static SyncSummaryView notStarted() {
            SyncSummaryView view = new SyncSummaryView();
            view.setLatestTaskStatus("not_started");
            view.setTotalOrderCount(0);
            view.setTotalItemCount(0);
            return view;
        }

        static SyncSummaryView fromCounts(
                int totalOrderCount,
                int totalItemCount,
                Ali1688HistoricalOrderSyncTaskRow latestTask
        ) {
            SyncSummaryView view = new SyncSummaryView();
            view.setLatestTaskStatus(latestTask == null
                    ? (totalOrderCount > 0 || totalItemCount > 0 ? "success" : "not_started")
                    : latestTask.getStatus());
            view.setTotalOrderCount(totalOrderCount);
            view.setTotalItemCount(totalItemCount);
            view.setProcessedCount(latestTask == null ? 0 : nullToZero(latestTask.getProcessedCount()));
            view.setImportedCount(latestTask == null ? totalItemCount : nullToZero(latestTask.getImportedCount()));
            view.setFailedCount(latestTask == null ? 0 : nullToZero(latestTask.getFailedCount()));
            view.setProgressPercent(latestTask == null ? 0 : nullToZero(latestTask.getProgressPercent()));
            view.setFailureCode(latestTask == null ? null : latestTask.getFailureCode());
            view.setFailureMessage(latestTask == null ? null : latestTask.getFailureMessage());
            view.setRetryable(latestTask != null && Boolean.TRUE.equals(latestTask.getRetryable()));
            view.setRequiresManualAction(latestTask != null && Boolean.TRUE.equals(latestTask.getRequiresManualAction()));
            view.setCheckpointJson(latestTask == null ? null : latestTask.getCheckpointJson());
            return view;
        }

        private static int nullToZero(Integer value) {
            return value == null ? 0 : value;
        }

        public String getLatestTaskStatus() {
            return latestTaskStatus;
        }

        public void setLatestTaskStatus(String latestTaskStatus) {
            this.latestTaskStatus = latestTaskStatus;
        }

        public int getTotalOrderCount() {
            return totalOrderCount;
        }

        public void setTotalOrderCount(int totalOrderCount) {
            this.totalOrderCount = totalOrderCount;
        }

        public int getTotalItemCount() {
            return totalItemCount;
        }

        public void setTotalItemCount(int totalItemCount) {
            this.totalItemCount = totalItemCount;
        }

        public int getProcessedCount() {
            return processedCount;
        }

        public void setProcessedCount(int processedCount) {
            this.processedCount = processedCount;
        }

        public int getImportedCount() {
            return importedCount;
        }

        public void setImportedCount(int importedCount) {
            this.importedCount = importedCount;
        }

        public int getFailedCount() {
            return failedCount;
        }

        public void setFailedCount(int failedCount) {
            this.failedCount = failedCount;
        }

        public int getProgressPercent() {
            return progressPercent;
        }

        public void setProgressPercent(int progressPercent) {
            this.progressPercent = progressPercent;
        }

        public String getFailureCode() {
            return failureCode;
        }

        public void setFailureCode(String failureCode) {
            this.failureCode = failureCode;
        }

        public String getFailureMessage() {
            return failureMessage;
        }

        public void setFailureMessage(String failureMessage) {
            this.failureMessage = failureMessage;
        }

        public boolean isRetryable() {
            return retryable;
        }

        public void setRetryable(boolean retryable) {
            this.retryable = retryable;
        }

        public boolean isRequiresManualAction() {
            return requiresManualAction;
        }

        public void setRequiresManualAction(boolean requiresManualAction) {
            this.requiresManualAction = requiresManualAction;
        }

        public String getCheckpointJson() {
            return checkpointJson;
        }

        public void setCheckpointJson(String checkpointJson) {
            this.checkpointJson = checkpointJson;
        }
    }

    public static class OrderRowView {
        private String id;
        private String orderNo;
        private String orderTime;
        private String paidAt;
        private String supplierName;
        private String buyerCompanyName;
        private String buyerMemberName;
        private String sellerMemberName;
        private String goodsTotalText;
        private String freightText;
        private String adjustmentText;
        private String paidAmountText;
        private String amountText;
        private String orderStatus;
        private String logisticsStatus;
        private String shipperName;
        private String originalUrl;
        private String receiverName;
        private String receiverPostalCode;
        private String receiverTelephone;
        private String receiverMobile;
        private String initiatorLoginName;
        private String sourceBatchNo;
        private String downstreamOrderNo;
        private List<String> missingFields = new ArrayList<>();
        private List<OrderItemView> items = new ArrayList<>();

        public static OrderRowView fromRow(Ali1688HistoricalOrderRow row, List<OrderItemView> items) {
            OrderRowView view = new OrderRowView();
            view.setId(row.getId() == null ? null : String.valueOf(row.getId()));
            view.setOrderNo(row.getProviderOrderNo());
            view.setOrderTime(row.getOrderTime());
            view.setPaidAt(row.getPaidAt());
            view.setSupplierName(row.getSupplierName());
            view.setBuyerCompanyName(row.getBuyerCompanyName());
            view.setBuyerMemberName(row.getBuyerMemberName());
            view.setSellerMemberName(row.getSellerMemberName());
            view.setGoodsTotalText(row.getGoodsTotalText());
            view.setFreightText(row.getFreightText());
            view.setAdjustmentText(row.getAdjustmentText());
            view.setPaidAmountText(row.getPaidAmountText());
            view.setAmountText(row.getAmountText());
            view.setOrderStatus(row.getOrderStatus());
            view.setLogisticsStatus(row.getLogisticsStatus());
            view.setShipperName(row.getShipperName());
            view.setOriginalUrl(row.getOriginalUrl());
            view.setReceiverName(row.getReceiverName());
            view.setReceiverPostalCode(row.getReceiverPostalCode());
            view.setInitiatorLoginName(row.getInitiatorLoginName());
            view.setSourceBatchNo(row.getSourceBatchNo());
            view.setDownstreamOrderNo(row.getDownstreamOrderNo());
            view.setMissingFields(orderMissingFields(row));
            view.setItems(items);
            return view;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        public String getOrderTime() {
            return orderTime;
        }

        public void setOrderTime(String orderTime) {
            this.orderTime = orderTime;
        }

        public String getPaidAt() {
            return paidAt;
        }

        public void setPaidAt(String paidAt) {
            this.paidAt = paidAt;
        }

        public String getSupplierName() {
            return supplierName;
        }

        public void setSupplierName(String supplierName) {
            this.supplierName = supplierName;
        }

        public String getBuyerCompanyName() {
            return buyerCompanyName;
        }

        public void setBuyerCompanyName(String buyerCompanyName) {
            this.buyerCompanyName = buyerCompanyName;
        }

        public String getBuyerMemberName() {
            return buyerMemberName;
        }

        public void setBuyerMemberName(String buyerMemberName) {
            this.buyerMemberName = buyerMemberName;
        }

        public String getSellerMemberName() {
            return sellerMemberName;
        }

        public void setSellerMemberName(String sellerMemberName) {
            this.sellerMemberName = sellerMemberName;
        }

        public String getGoodsTotalText() {
            return goodsTotalText;
        }

        public void setGoodsTotalText(String goodsTotalText) {
            this.goodsTotalText = goodsTotalText;
        }

        public String getFreightText() {
            return freightText;
        }

        public void setFreightText(String freightText) {
            this.freightText = freightText;
        }

        public String getAdjustmentText() {
            return adjustmentText;
        }

        public void setAdjustmentText(String adjustmentText) {
            this.adjustmentText = adjustmentText;
        }

        public String getPaidAmountText() {
            return paidAmountText;
        }

        public void setPaidAmountText(String paidAmountText) {
            this.paidAmountText = paidAmountText;
        }

        public String getAmountText() {
            return amountText;
        }

        public void setAmountText(String amountText) {
            this.amountText = amountText;
        }

        public String getOrderStatus() {
            return orderStatus;
        }

        public void setOrderStatus(String orderStatus) {
            this.orderStatus = orderStatus;
        }

        public String getLogisticsStatus() {
            return logisticsStatus;
        }

        public void setLogisticsStatus(String logisticsStatus) {
            this.logisticsStatus = logisticsStatus;
        }

        public String getShipperName() {
            return shipperName;
        }

        public void setShipperName(String shipperName) {
            this.shipperName = shipperName;
        }

        public String getOriginalUrl() {
            return originalUrl;
        }

        public void setOriginalUrl(String originalUrl) {
            this.originalUrl = originalUrl;
        }

        public String getReceiverName() {
            return receiverName;
        }

        public void setReceiverName(String receiverName) {
            this.receiverName = receiverName;
        }

        public String getReceiverPostalCode() {
            return receiverPostalCode;
        }

        public void setReceiverPostalCode(String receiverPostalCode) {
            this.receiverPostalCode = receiverPostalCode;
        }

        public String getReceiverTelephone() {
            return receiverTelephone;
        }

        public void setReceiverTelephone(String receiverTelephone) {
            this.receiverTelephone = receiverTelephone;
        }

        public String getReceiverMobile() {
            return receiverMobile;
        }

        public void setReceiverMobile(String receiverMobile) {
            this.receiverMobile = receiverMobile;
        }

        public String getInitiatorLoginName() {
            return initiatorLoginName;
        }

        public void setInitiatorLoginName(String initiatorLoginName) {
            this.initiatorLoginName = initiatorLoginName;
        }

        public String getSourceBatchNo() {
            return sourceBatchNo;
        }

        public void setSourceBatchNo(String sourceBatchNo) {
            this.sourceBatchNo = sourceBatchNo;
        }

        public String getDownstreamOrderNo() {
            return downstreamOrderNo;
        }

        public void setDownstreamOrderNo(String downstreamOrderNo) {
            this.downstreamOrderNo = downstreamOrderNo;
        }

        public List<String> getMissingFields() {
            return missingFields;
        }

        public void setMissingFields(List<String> missingFields) {
            this.missingFields = missingFields == null ? List.of() : missingFields;
        }

        public List<OrderItemView> getItems() {
            return items;
        }

        public void setItems(List<OrderItemView> items) {
            this.items = items == null ? List.of() : items;
        }
    }

    public static class OrderItemView {
        private String id;
        private String offerId;
        private String skuId;
        private String title;
        private String skuText;
        private String modelText;
        private String productCode;
        private String singleProductCode;
        private Integer quantity;
        private Integer originalQuantity;
        private Integer assignedQuantity;
        private Integer remainingQuantity;
        private String assignmentStatus;
        private String assignmentStatusLabel;
        private String assignmentBreakdownText;
        private Long assignmentId;
        private String assignmentTargetType;
        private String assignmentTargetStoreCode;
        private String assignmentTargetSiteCode;
        private Ali1688HistoricalOrderProductLinkView.LinkedProductView productLink;
        private String unit;
        private String unitPriceText;
        private String amountText;
        private String imageUrl;
        private String logisticsCompany;
        private String trackingNo;
        private List<String> missingFields = new ArrayList<>();

        public static OrderItemView fromRow(Ali1688HistoricalOrderItemRow row) {
            return fromRow(row, null);
        }

        public static OrderItemView fromRow(
                Ali1688HistoricalOrderItemRow row,
                Ali1688HistoricalOrderLogisticsRow logistics
        ) {
            return fromRow(row, logistics, null);
        }

        public static OrderItemView fromRow(
                Ali1688HistoricalOrderItemRow row,
                Ali1688HistoricalOrderLogisticsRow logistics,
                Ali1688HistoricalOrderItemAssignmentSummaryRow assignmentSummary
        ) {
            OrderItemView view = new OrderItemView();
            view.setId(row.getId() == null ? null : String.valueOf(row.getId()));
            view.setOfferId(row.getOfferId());
            view.setSkuId(row.getSkuId());
            view.setTitle(row.getTitle());
            view.setSkuText(row.getSkuText());
            view.setModelText(row.getModelText());
            view.setProductCode(row.getProductCode());
            view.setSingleProductCode(row.getSingleProductCode());
            view.setQuantity(row.getQuantity());
            view.applyAssignmentSummary(row, assignmentSummary);
            view.setUnit(row.getUnit());
            view.setUnitPriceText(row.getUnitPriceText());
            view.setAmountText(row.getAmountText());
            view.setImageUrl(row.getImageUrl());
            if (logistics != null) {
                view.setLogisticsCompany(logistics.getLogisticsCompany());
                view.setTrackingNo(logistics.getTrackingNo());
            }
            view.setMissingFields(itemMissingFields(row));
            return view;
        }

        private void applyAssignmentSummary(
                Ali1688HistoricalOrderItemRow row,
                Ali1688HistoricalOrderItemAssignmentSummaryRow assignmentSummary
        ) {
            Integer original = row.getQuantity();
            int assigned = Math.max(0, assignmentSummary == null || assignmentSummary.getAssignedQuantity() == null
                    ? 0
                    : assignmentSummary.getAssignedQuantity());
            setAssignmentBreakdownText(assignmentSummary == null ? null : assignmentSummary.getAssignmentBreakdownText());
            setOriginalQuantity(original);
            setAssignedQuantity(assigned);
            if (original == null) {
                setRemainingQuantity(null);
                setAssignmentStatus("quantity_missing");
                setAssignmentStatusLabel("数量未返回");
                return;
            }
            int remaining = Math.max(0, original - assigned);
            setRemainingQuantity(remaining);
            if (assigned <= 0) {
                setAssignmentStatus("unassigned");
                setAssignmentStatusLabel("未分配");
            } else if (remaining > 0) {
                setAssignmentStatus("partially_assigned");
                setAssignmentStatusLabel("部分分配");
            } else {
                setAssignmentStatus("assigned");
                setAssignmentStatusLabel("已分配");
            }
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getOfferId() {
            return offerId;
        }

        public void setOfferId(String offerId) {
            this.offerId = offerId;
        }

        public String getSkuId() {
            return skuId;
        }

        public void setSkuId(String skuId) {
            this.skuId = skuId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getSkuText() {
            return skuText;
        }

        public void setSkuText(String skuText) {
            this.skuText = skuText;
        }

        public String getModelText() {
            return modelText;
        }

        public void setModelText(String modelText) {
            this.modelText = modelText;
        }

        public String getProductCode() {
            return productCode;
        }

        public void setProductCode(String productCode) {
            this.productCode = productCode;
        }

        public String getSingleProductCode() {
            return singleProductCode;
        }

        public void setSingleProductCode(String singleProductCode) {
            this.singleProductCode = singleProductCode;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public Integer getOriginalQuantity() {
            return originalQuantity;
        }

        public void setOriginalQuantity(Integer originalQuantity) {
            this.originalQuantity = originalQuantity;
        }

        public Integer getAssignedQuantity() {
            return assignedQuantity;
        }

        public void setAssignedQuantity(Integer assignedQuantity) {
            this.assignedQuantity = assignedQuantity;
        }

        public Integer getRemainingQuantity() {
            return remainingQuantity;
        }

        public void setRemainingQuantity(Integer remainingQuantity) {
            this.remainingQuantity = remainingQuantity;
        }

        public String getAssignmentStatus() {
            return assignmentStatus;
        }

        public void setAssignmentStatus(String assignmentStatus) {
            this.assignmentStatus = assignmentStatus;
        }

        public String getAssignmentStatusLabel() {
            return assignmentStatusLabel;
        }

        public void setAssignmentStatusLabel(String assignmentStatusLabel) {
            this.assignmentStatusLabel = assignmentStatusLabel;
        }

        public String getAssignmentBreakdownText() {
            return assignmentBreakdownText;
        }

        public void setAssignmentBreakdownText(String assignmentBreakdownText) {
            this.assignmentBreakdownText = assignmentBreakdownText;
        }

        public Long getAssignmentId() {
            return assignmentId;
        }

        public void setAssignmentId(Long assignmentId) {
            this.assignmentId = assignmentId;
        }

        public String getAssignmentTargetType() {
            return assignmentTargetType;
        }

        public void setAssignmentTargetType(String assignmentTargetType) {
            this.assignmentTargetType = assignmentTargetType;
        }

        public String getAssignmentTargetStoreCode() {
            return assignmentTargetStoreCode;
        }

        public void setAssignmentTargetStoreCode(String assignmentTargetStoreCode) {
            this.assignmentTargetStoreCode = assignmentTargetStoreCode;
        }

        public String getAssignmentTargetSiteCode() {
            return assignmentTargetSiteCode;
        }

        public void setAssignmentTargetSiteCode(String assignmentTargetSiteCode) {
            this.assignmentTargetSiteCode = assignmentTargetSiteCode;
        }

        public Ali1688HistoricalOrderProductLinkView.LinkedProductView getProductLink() {
            return productLink;
        }

        public void setProductLink(Ali1688HistoricalOrderProductLinkView.LinkedProductView productLink) {
            this.productLink = productLink;
        }

        public String getUnit() {
            return unit;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }

        public String getUnitPriceText() {
            return unitPriceText;
        }

        public void setUnitPriceText(String unitPriceText) {
            this.unitPriceText = unitPriceText;
        }

        public String getAmountText() {
            return amountText;
        }

        public void setAmountText(String amountText) {
            this.amountText = amountText;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }

        public String getLogisticsCompany() {
            return logisticsCompany;
        }

        public void setLogisticsCompany(String logisticsCompany) {
            this.logisticsCompany = logisticsCompany;
        }

        public String getTrackingNo() {
            return trackingNo;
        }

        public void setTrackingNo(String trackingNo) {
            this.trackingNo = trackingNo;
        }

        public List<String> getMissingFields() {
            return missingFields;
        }

        public void setMissingFields(List<String> missingFields) {
            this.missingFields = missingFields == null ? List.of() : missingFields;
        }
    }

    public static class OrderDetailView extends OrderRowView {
        private SensitiveFieldsView sensitiveFields;

        public static OrderDetailView fromRow(
                Ali1688HistoricalOrderRow row,
                List<OrderItemView> items,
                SensitiveFieldsView sensitiveFields
        ) {
            OrderRowView base = OrderRowView.fromRow(row, items);
            OrderDetailView view = new OrderDetailView();
            view.setId(base.getId());
            view.setOrderNo(base.getOrderNo());
            view.setOrderTime(base.getOrderTime());
            view.setPaidAt(base.getPaidAt());
            view.setSupplierName(base.getSupplierName());
            view.setBuyerCompanyName(base.getBuyerCompanyName());
            view.setBuyerMemberName(base.getBuyerMemberName());
            view.setSellerMemberName(base.getSellerMemberName());
            view.setGoodsTotalText(base.getGoodsTotalText());
            view.setFreightText(base.getFreightText());
            view.setAdjustmentText(base.getAdjustmentText());
            view.setPaidAmountText(base.getPaidAmountText());
            view.setAmountText(base.getAmountText());
            view.setOrderStatus(base.getOrderStatus());
            view.setLogisticsStatus(base.getLogisticsStatus());
            view.setShipperName(base.getShipperName());
            view.setOriginalUrl(base.getOriginalUrl());
            view.setReceiverName(base.getReceiverName());
            view.setReceiverPostalCode(base.getReceiverPostalCode());
            view.setReceiverTelephone(base.getReceiverTelephone());
            view.setReceiverMobile(base.getReceiverMobile());
            view.setInitiatorLoginName(base.getInitiatorLoginName());
            view.setSourceBatchNo(base.getSourceBatchNo());
            view.setDownstreamOrderNo(base.getDownstreamOrderNo());
            view.setMissingFields(base.getMissingFields());
            view.setItems(base.getItems());
            view.setSensitiveFields(sensitiveFields);
            return view;
        }

        public SensitiveFieldsView getSensitiveFields() {
            return sensitiveFields;
        }

        public void setSensitiveFields(SensitiveFieldsView sensitiveFields) {
            this.sensitiveFields = sensitiveFields;
        }
    }

    public static class SensitiveFieldsView {
        private String redactionLevel;
        private String receiverPhone;
        private String receiverAddress;
        private String buyerRemark;
        private String supplierContact;

        public String getRedactionLevel() {
            return redactionLevel;
        }

        public void setRedactionLevel(String redactionLevel) {
            this.redactionLevel = redactionLevel;
        }

        public String getReceiverPhone() {
            return receiverPhone;
        }

        public void setReceiverPhone(String receiverPhone) {
            this.receiverPhone = receiverPhone;
        }

        public String getReceiverAddress() {
            return receiverAddress;
        }

        public void setReceiverAddress(String receiverAddress) {
            this.receiverAddress = receiverAddress;
        }

        public String getBuyerRemark() {
            return buyerRemark;
        }

        public void setBuyerRemark(String buyerRemark) {
            this.buyerRemark = buyerRemark;
        }

        public String getSupplierContact() {
            return supplierContact;
        }

        public void setSupplierContact(String supplierContact) {
            this.supplierContact = supplierContact;
        }
    }

    public static class PaginationView {
        private int page;
        private int pageSize;
        private long total;

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public int getPageSize() {
            return pageSize;
        }

        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }

        public long getTotal() {
            return total;
        }

        public void setTotal(long total) {
            this.total = total;
        }
    }

    private static List<String> orderMissingFields(Ali1688HistoricalOrderRow row) {
        if (row == null) {
            return List.of("supplier", "amount", "logistics", "sourceLink");
        }
        List<String> fields = new ArrayList<>();
        if (!hasText(row.getSupplierName())) {
            fields.add("supplier");
        }
        if (!hasText(row.getAmountText())) {
            fields.add("amount");
        }
        if (!hasText(row.getLogisticsStatus())) {
            fields.add("logistics");
        }
        if (!hasText(row.getOriginalUrl())) {
            fields.add("sourceLink");
        }
        return fields;
    }

    private static List<String> itemMissingFields(Ali1688HistoricalOrderItemRow row) {
        if (row == null) {
            return List.of("sku", "image");
        }
        List<String> fields = new ArrayList<>();
        if (!hasText(row.getSkuText())) {
            fields.add("sku");
        }
        if (!hasText(row.getImageUrl())) {
            fields.add("image");
        }
        return fields;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
