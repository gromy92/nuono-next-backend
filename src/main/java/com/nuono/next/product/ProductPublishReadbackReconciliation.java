package com.nuono.next.product;

public class ProductPublishReadbackReconciliation {

    private final String status;
    private final String errorCode;
    private final String errorMessage;
    private final boolean confirmed;
    private final boolean scheduleAnotherReadback;

    ProductPublishReadbackReconciliation(
            String status,
            String errorCode,
            String errorMessage,
            boolean confirmed,
            boolean scheduleAnotherReadback
    ) {
        this.status = status;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.confirmed = confirmed;
        this.scheduleAnotherReadback = scheduleAnotherReadback;
    }

    public static ProductPublishReadbackReconciliation synced() {
        return new ProductPublishReadbackReconciliation("synced", null, null, true, false);
    }

    public static ProductPublishReadbackReconciliation pendingEffective() {
        return new ProductPublishReadbackReconciliation(
                "pending_effective",
                "noon_effect_pending",
                "Noon 可能延迟生效，本轮未读到目标值，系统将稍后继续回读校验。",
                false,
                true
        );
    }

    public static ProductPublishReadbackReconciliation pendingManualCheck() {
        return new ProductPublishReadbackReconciliation(
                "pending_manual_check",
                "noon_effect_not_confirmed",
                "多轮回读仍未确认 Noon 已生效，请人工核对官方后台后再处理。",
                false,
                false
        );
    }

    public static ProductPublishReadbackReconciliation verifyTimeout() {
        return new ProductPublishReadbackReconciliation(
                "verify_timeout",
                "noon_verify_timeout",
                "Noon 回读校验超时，系统将稍后继续核对官方结果。",
                false,
                true
        );
    }

    public String getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public boolean shouldScheduleAnotherReadback() {
        return scheduleAnotherReadback;
    }
}
