package com.nuono.next.postsaleprofit;

final class PostSaleProfitSourceIds {
    static final String UNASSIGNED_PREFIX = "UNASSIGNED:";
    static final String UNKNOWN_SKU = "UNKNOWN_SKU";

    private PostSaleProfitSourceIds() {
    }

    static String unassigned(String partnerSku) {
        return UNASSIGNED_PREFIX + normalizePartnerSku(partnerSku);
    }

    static String normalizePartnerSku(String partnerSku) {
        return partnerSku == null || partnerSku.isBlank() ? UNKNOWN_SKU : partnerSku.trim();
    }

    static boolean isUnassigned(String sourceId) {
        return sourceId != null && sourceId.startsWith(UNASSIGNED_PREFIX);
    }
}
