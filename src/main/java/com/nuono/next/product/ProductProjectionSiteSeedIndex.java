package com.nuono.next.product;

import com.nuono.next.product.ProductProjectionPersistenceService.SiteSeed;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/** Canonical store/site indexes derived from product projection site seeds. */
final class ProductProjectionSiteSeedIndex {

    private ProductProjectionSiteSeedIndex() {
    }

    static Map<String, String> siteCodes(List<SiteSeed> siteSeeds) {
        Map<String, String> siteCodes = new LinkedHashMap<>();
        if (siteSeeds == null) {
            return siteCodes;
        }
        for (SiteSeed siteSeed : siteSeeds) {
            if (siteSeed == null) {
                continue;
            }
            String storeCode = normalize(siteSeed.getStoreCode());
            String siteCode = normalize(siteSeed.getSite());
            if (StringUtils.hasText(storeCode) && StringUtils.hasText(siteCode)) {
                siteCodes.put(storeCode, siteCode);
            }
        }
        return siteCodes;
    }

    static List<SiteSeed> deduplicate(List<SiteSeed> seeds) {
        Map<String, SiteSeed> byStoreCode = new LinkedHashMap<>();
        for (SiteSeed seed : seeds) {
            if (!StringUtils.hasText(seed.getStoreCode())) {
                continue;
            }
            byStoreCode.put(normalize(seed.getStoreCode()), seed);
        }
        return new ArrayList<>(byStoreCode.values());
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
