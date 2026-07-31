package com.nuono.next.product;

import java.util.Map;
import java.util.Optional;
import org.springframework.util.StringUtils;

final class ProductActiveStateEvidenceResolver {
    static final String TRUSTED_SOURCE = "NOON_PRICING_INFO";

    private ProductActiveStateEvidenceResolver() {
    }

    static Optional<Boolean> resolve(
            ProductMasterSnapshotView snapshot,
            ProductActiveStateReconciliationCandidate target
    ) {
        if (snapshot == null || !snapshot.isReady() || snapshot.getSiteOffers() == null || target == null) {
            return Optional.empty();
        }
        for (Map<String, Object> offer : snapshot.getSiteOffers()) {
            if (!matchesTarget(offer, target) || !trusted(offer)) {
                continue;
            }
            Boolean value = explicitBoolean(offer.get("isActive"));
            if (value != null) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private static boolean matchesTarget(
            Map<String, Object> offer,
            ProductActiveStateReconciliationCandidate target
    ) {
        if (offer == null
                || !same(offer.get("storeCode"), target.getStoreCode())
                || !same(offer.get("partnerSku"), target.getPartnerSku())) {
            return false;
        }
        return !StringUtils.hasText(target.getSiteCode())
                || !StringUtils.hasText(text(offer.get("site")))
                || same(offer.get("site"), target.getSiteCode());
    }

    private static boolean trusted(Map<String, Object> offer) {
        return same(offer.get("activeStateSource"), TRUSTED_SOURCE);
    }

    private static Boolean explicitBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value != null && "true".equalsIgnoreCase(String.valueOf(value).trim())) {
            return Boolean.TRUE;
        }
        if (value != null && "false".equalsIgnoreCase(String.valueOf(value).trim())) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static boolean same(Object actual, String expected) {
        return StringUtils.hasText(text(actual))
                && StringUtils.hasText(expected)
                && text(actual).trim().equalsIgnoreCase(expected.trim());
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
