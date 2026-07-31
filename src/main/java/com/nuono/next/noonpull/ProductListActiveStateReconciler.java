package com.nuono.next.noonpull;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.product.ProductProjectionPersistenceService.ProductMasterSeed;
import com.nuono.next.product.ProductProjectionPersistenceService.SiteSeed;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProductListActiveStateReconciler {
    static final String ABSENCE_STATE_SOURCE = "NOON_PRODUCT_LIST_ABSENCE";

    private final ProductManagementMapper productManagementMapper;

    public ProductListActiveStateReconciler(ProductManagementMapper productManagementMapper) {
        this.productManagementMapper = productManagementMapper;
    }

    public void reconcile(NoonProductProjectionWriteCommand command) {
        if (command == null || !command.isCompleteProductScope() || command.getOwnerUserId() == null) {
            return;
        }
        String storeCode = normalize(command.getReferenceStoreCode());
        String siteCode = referenceSiteCode(command.getSiteSeeds(), storeCode);
        List<String> presentPartnerSkus = presentPartnerSkus(command.getProductSeeds());
        if (!StringUtils.hasText(storeCode)
                || !StringUtils.hasText(siteCode)
                || presentPartnerSkus.isEmpty()) {
            return;
        }
        productManagementMapper.markProductOffersMissingFromCompleteListInactive(
                command.getOwnerUserId(),
                storeCode,
                siteCode,
                presentPartnerSkus,
                ABSENCE_STATE_SOURCE,
                LocalDateTime.now(),
                command.getOwnerUserId()
        );
    }

    private List<String> presentPartnerSkus(List<ProductMasterSeed> productSeeds) {
        if (productSeeds == null || productSeeds.isEmpty()) {
            return List.of();
        }
        Set<String> presentPartnerSkus = new LinkedHashSet<>();
        for (ProductMasterSeed seed : productSeeds) {
            String partnerSku = seed == null ? null : normalize(seed.getPartnerSku());
            if (!StringUtils.hasText(partnerSku)) {
                return List.of();
            }
            presentPartnerSkus.add(partnerSku.toUpperCase(Locale.ROOT));
        }
        return new ArrayList<>(presentPartnerSkus);
    }

    private String referenceSiteCode(List<SiteSeed> siteSeeds, String referenceStoreCode) {
        if (!StringUtils.hasText(referenceStoreCode) || siteSeeds == null) {
            return null;
        }
        for (SiteSeed siteSeed : siteSeeds) {
            if (siteSeed != null && referenceStoreCode.equalsIgnoreCase(normalize(siteSeed.getStoreCode()))) {
                return normalize(siteSeed.getSite());
            }
        }
        return null;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
