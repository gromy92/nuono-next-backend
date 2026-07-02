package com.nuono.next.noonads;

final class NoonAdvertisingProductIdentity {
    private NoonAdvertisingProductIdentity() {
    }

    static String key(String storeCode, String siteCode, String partnerSku) {
        if (!hasText(partnerSku)) {
            return "";
        }
        return normalize(storeCode) + "|" + normalize(siteCode) + "|" + normalize(partnerSku);
    }

    static String advertisingKey(String storeCode, String siteCode, String partnerSku, String adSkuCode) {
        String productKey = key(storeCode, siteCode, partnerSku);
        if (!productKey.isEmpty()) {
            return productKey;
        }
        return normalize(storeCode) + "|" + normalize(siteCode) + "|ADSKU|" + normalize(adSkuCode);
    }

    static boolean resolved(String partnerSku) {
        return hasText(partnerSku);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
