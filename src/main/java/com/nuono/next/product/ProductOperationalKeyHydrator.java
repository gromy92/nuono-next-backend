package com.nuono.next.product;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

class ProductOperationalKeyHydrator {

    private final ProductProjectionPersistenceService productProjectionPersistenceService;

    ProductOperationalKeyHydrator(ProductProjectionPersistenceService productProjectionPersistenceService) {
        this.productProjectionPersistenceService = productProjectionPersistenceService;
    }

    OperationalKeys resolveOperationalKeysFromProjection(
            Long ownerUserId,
            String storeCode,
            String skuParent,
            String partnerSku,
            String pskuCode
    ) {
        if (StringUtils.hasText(partnerSku) && StringUtils.hasText(pskuCode)) {
            return new OperationalKeys(partnerSku, pskuCode);
        }
        List<String> lookupWarnings = new ArrayList<>();
        ProductListSummaryView summary = productProjectionPersistenceService.loadProductListSummary(
                ownerUserId,
                storeCode,
                skuParent,
                lookupWarnings
        );
        if (summary == null || !summary.isReady()) {
            return new OperationalKeys(partnerSku, pskuCode);
        }
        return new OperationalKeys(
                firstNonBlank(partnerSku, summary.getPartnerSku()),
                firstNonBlank(pskuCode, summary.getPskuCode())
        );
    }

    void hydrateSnapshotOperationalKeys(
            Long ownerUserId,
            String storeCode,
            String skuParent,
            ProductMasterSnapshotView snapshot
    ) {
        if (snapshot == null) {
            return;
        }
        OperationalKeys operationalKeys = resolveOperationalKeysFromProjection(
                ownerUserId,
                storeCode,
                skuParent,
                textValue(snapshot.getIdentity().get("partnerSku")),
                textValue(snapshot.getIdentity().get("pskuCode"))
        );
        putIfNotBlank(snapshot.getIdentity(), "partnerSku", operationalKeys.getPartnerSku());
        putIfNotBlank(snapshot.getIdentity(), "pskuCode", operationalKeys.getPskuCode());
    }

    List<String> collectMissingOperationalKeys(String partnerSku, String pskuCode) {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(partnerSku)) {
            missing.add("partnerSku");
        }
        if (!StringUtils.hasText(pskuCode)) {
            missing.add("pskuCode");
        }
        return missing;
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    static class OperationalKeys {

        private final String partnerSku;

        private final String pskuCode;

        OperationalKeys(String partnerSku, String pskuCode) {
            this.partnerSku = partnerSku;
            this.pskuCode = pskuCode;
        }

        String getPartnerSku() {
            return partnerSku;
        }

        String getPskuCode() {
            return pskuCode;
        }
    }
}
