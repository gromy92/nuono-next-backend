package com.nuono.next.postsaleprofit;

final class PostSaleProfitQualityPolicy {
    private PostSaleProfitQualityPolicy() {
    }

    static boolean isHardMissing(PostSaleProfitQualityStatus status) {
        return status == PostSaleProfitQualityStatus.MISSING_ORDER_LINE
                || status == PostSaleProfitQualityStatus.MISSING_FINANCE
                || status == PostSaleProfitQualityStatus.MISSING_PURCHASE_COST
                || status == PostSaleProfitQualityStatus.MISSING_HEADHAUL
                || status == PostSaleProfitQualityStatus.MISSING_FX_RATE
                || status == PostSaleProfitQualityStatus.UNASSIGNED_ORDER_QUANTITY;
    }
}
