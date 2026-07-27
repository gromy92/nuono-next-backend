package com.nuono.next.productlisting;

public class ProductListingCreateOutcomeVerificationView {

    private Long taskId;
    private String partnerSku;
    private String status;
    private String skuParent;
    private String pskuCode;
    private String failureCode;
    private String message;
    private Integer lookupAttemptCount;
    private Boolean canConfirmNotCreated;

    public static ProductListingCreateOutcomeVerificationView found(
            Long taskId,
            String partnerSku,
            String skuParent,
            String pskuCode
    ) {
        ProductListingCreateOutcomeVerificationView view = base(taskId, partnerSku, "found");
        view.setSkuParent(skuParent);
        view.setPskuCode(pskuCode);
        view.setMessage("已找到并保存 Noon 商品引用；本次核对未执行后续写入。");
        return view;
    }

    public static ProductListingCreateOutcomeVerificationView notFound(
            Long taskId,
            String partnerSku,
            int lookupAttemptCount,
            boolean canConfirmNotCreated
    ) {
        ProductListingCreateOutcomeVerificationView view = base(taskId, partnerSku, "not_found");
        view.setFailureCode("noon_create_reference_not_found");
        view.setLookupAttemptCount(lookupAttemptCount);
        view.setCanConfirmNotCreated(canConfirmNotCreated);
        view.setMessage(canConfirmNotCreated
                ? "已完成多次只读核对，当前可由你确认未创建并返回编辑。"
                : "Noon 中暂未找到该 PSKU；请稍后继续核对，系统不会重复创建商品。");
        return view;
    }

    public static ProductListingCreateOutcomeVerificationView lookupFailed(
            Long taskId,
            String partnerSku
    ) {
        ProductListingCreateOutcomeVerificationView view = base(taskId, partnerSku, "lookup_failed");
        view.setFailureCode("noon_create_reference_lookup_failed");
        view.setMessage("核对 Noon 创建结果失败，请稍后重试核对；系统不会重复创建商品。");
        return view;
    }

    public static ProductListingCreateOutcomeVerificationView
            reauthenticationRequired(Long taskId, String partnerSku) {
        ProductListingCreateOutcomeVerificationView view =
                base(taskId, partnerSku, "reauthentication_required");
        view.setFailureCode("noon_auth_required");
        view.setMessage(
                "核对 Noon 创建结果时授权已失效；请重新授权后继续只读核对，系统不会重复创建商品。"
        );
        return view;
    }

    private static ProductListingCreateOutcomeVerificationView base(
            Long taskId,
            String partnerSku,
            String status
    ) {
        ProductListingCreateOutcomeVerificationView view =
                new ProductListingCreateOutcomeVerificationView();
        view.setTaskId(taskId);
        view.setPartnerSku(partnerSku);
        view.setStatus(status);
        view.setLookupAttemptCount(0);
        view.setCanConfirmNotCreated(false);
        return view;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getPartnerSku() {
        return partnerSku;
    }

    public void setPartnerSku(String partnerSku) {
        this.partnerSku = partnerSku;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSkuParent() {
        return skuParent;
    }

    public void setSkuParent(String skuParent) {
        this.skuParent = skuParent;
    }

    public String getPskuCode() {
        return pskuCode;
    }

    public void setPskuCode(String pskuCode) {
        this.pskuCode = pskuCode;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getLookupAttemptCount() {
        return lookupAttemptCount;
    }

    public void setLookupAttemptCount(Integer lookupAttemptCount) {
        this.lookupAttemptCount = lookupAttemptCount;
    }

    public Boolean getCanConfirmNotCreated() {
        return canConfirmNotCreated;
    }

    public void setCanConfirmNotCreated(Boolean canConfirmNotCreated) {
        this.canConfirmNotCreated = canConfirmNotCreated;
    }
}
