package com.nuono.next.product;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.product.ProductProjectionPersistenceService.ProductMasterSeed;
import com.nuono.next.product.ProductProjectionPersistenceService.SiteSeed;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Fail-closed DP-04 adapter that projects facts only into one existing active store/site scope. */
@Component
public class Dp04ExistingScopeProjectionAdapter {
    private final ProductManagementMapper productManagementMapper;
    private final Dp04ProjectionSchemaPreflight schemaPreflight;
    private final ProductProjectionPersistenceService persistenceService;

    public Dp04ExistingScopeProjectionAdapter(
            ProductManagementMapper productManagementMapper,
            Dp04ProjectionSchemaPreflight schemaPreflight,
            ProductProjectionPersistenceService persistenceService
    ) {
        this.productManagementMapper = Objects.requireNonNull(
                productManagementMapper,
                "productManagementMapper"
        );
        this.schemaPreflight = Objects.requireNonNull(
                schemaPreflight,
                "schemaPreflight"
        );
        this.persistenceService = Objects.requireNonNull(
                persistenceService,
                "persistenceService"
        );
    }

    @Transactional
    public void persist(
            Long ownerUserId,
            Long requiredLogicalStoreId,
            String projectCode,
            String referenceStoreCode,
            List<SiteSeed> siteSeeds,
            List<ProductMasterSeed> productSeeds,
            List<String> warnings,
            boolean preserveDrafts
    ) {
        if (ownerUserId == null || requiredLogicalStoreId == null || requiredLogicalStoreId < 1L
                || !StringUtils.hasText(projectCode)
                || !StringUtils.hasText(referenceStoreCode)
                || productSeeds == null) {
            throw new IllegalArgumentException("DP-04 existing product scope is invalid");
        }
        schemaPreflight.requireReady();
        Map<String, Long> siteIds = requireExistingSite(
                ownerUserId,
                requiredLogicalStoreId,
                projectCode,
                referenceStoreCode,
                siteSeeds
        );
        if (productSeeds.isEmpty()) {
            return;
        }
        persistenceService.persistProductSeeds(
                ownerUserId,
                requiredLogicalStoreId,
                projectCode,
                siteIds,
                ProductProjectionSiteSeedIndex.siteCodes(siteSeeds),
                productSeeds,
                warnings,
                preserveDrafts,
                false
        );
    }

    private Map<String, Long> requireExistingSite(
            Long ownerUserId,
            Long logicalStoreId,
            String projectCode,
            String referenceStoreCode,
            List<SiteSeed> siteSeeds
    ) {
        if (siteSeeds == null || siteSeeds.size() != 1 || siteSeeds.get(0) == null) {
            throw new IllegalArgumentException("DP-04 requires exactly one existing store/site scope");
        }
        SiteSeed site = siteSeeds.get(0);
        String storeCode = normalize(site.getStoreCode());
        String siteCode = normalize(site.getSite());
        if (!StringUtils.hasText(storeCode)
                || !StringUtils.hasText(siteCode)
                || !storeCode.equalsIgnoreCase(normalize(referenceStoreCode))) {
            throw new IllegalArgumentException("DP-04 existing store/site scope is invalid");
        }
        Long siteId = productManagementMapper.selectActiveBoundLogicalStoreSiteId(
                ownerUserId,
                logicalStoreId,
                normalize(projectCode),
                storeCode,
                siteCode
        );
        if (siteId == null || siteId < 1L) {
            throw new IllegalStateException("DP-04 active bound store/site scope changed before apply");
        }
        Map<String, Long> result = new LinkedHashMap<>();
        result.put(storeCode, siteId);
        return result;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
