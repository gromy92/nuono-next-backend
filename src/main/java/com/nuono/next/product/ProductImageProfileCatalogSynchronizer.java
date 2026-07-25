package com.nuono.next.product;

import com.nuono.next.infrastructure.mapper.ProductImageProfileMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.util.StringUtils;

final class ProductImageProfileCatalogSynchronizer {
    private final ProductImageProfileMapper mapper;

    ProductImageProfileCatalogSynchronizer(ProductImageProfileMapper mapper) {
        this.mapper = mapper;
    }

    void ensureStoreProfiles(Long ownerUserId, String storeCode, Long operatorUserId) {
        LocalDateTime now = LocalDateTime.now();
        for (ProductImageProductCandidateRecord candidate
                : safeList(mapper.selectAllProductCandidatesForStore(ownerUserId, storeCode))) {
            String pskuCode = trimToNull(candidate.getPskuCode());
            String productIdentityKey = trimToNull(candidate.getProductIdentityKey());
            if (pskuCode == null || productIdentityKey == null) continue;

            ProductImageProfileRecord existing =
                    mapper.selectProfileByIdentity(ownerUserId, storeCode, pskuCode, productIdentityKey);
            if (existing == null) {
                mapper.insertProfile(newInitialProfile(ownerUserId, storeCode, candidate, operatorUserId, now));
                continue;
            }
            ProductImageProfileRecord refreshed = refreshedProfile(existing, candidate, operatorUserId, now);
            if (shouldRefreshProfile(existing, refreshed)) mapper.updateProfile(refreshed);
        }
    }

    String initialProductFactText(ProductImageProductCandidateRecord candidate) {
        List<String> lines = new ArrayList<>();
        appendFactLine(lines, "商品", candidate.getProductTitle());
        appendFactLine(lines, "PSKU", candidate.getPskuCode());
        appendFactLine(lines, "品牌", candidate.getBrand());
        appendFactLine(lines, "英文完整标题", candidate.getProductTitle());
        return String.join("\n", lines);
    }

    private ProductImageProfileRecord newInitialProfile(
            Long ownerUserId,
            String storeCode,
            ProductImageProductCandidateRecord candidate,
            Long operatorUserId,
            LocalDateTime now
    ) {
        ProductImageProfileRecord record = new ProductImageProfileRecord();
        record.setOwnerUserId(ownerUserId);
        record.setStoreCode(storeCode);
        record.setPskuCode(trimToNull(candidate.getPskuCode()));
        record.setProductIdentityKey(trimToNull(candidate.getProductIdentityKey()));
        record.setProductMasterId(candidate.getProductMasterId());
        record.setProductTitle(trimToNull(candidate.getProductTitle()));
        record.setBrand(trimToNull(candidate.getBrand()));
        record.setTitleEn(trimToNull(candidate.getProductTitle()));
        record.setTitleAr(null);
        record.setSpecSummary(null);
        record.setProductFactText(initialProductFactText(candidate));
        record.setHeroSellingPointsJson("[]");
        record.setProfileStatus("ACTIVE");
        record.setCreatedBy(operatorUserId);
        record.setUpdatedBy(operatorUserId);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setDeleted(false);
        return record;
    }

    private ProductImageProfileRecord refreshedProfile(
            ProductImageProfileRecord existing,
            ProductImageProductCandidateRecord candidate,
            Long operatorUserId,
            LocalDateTime now
    ) {
        String candidateTitle = trimToNull(candidate.getProductTitle());
        String candidateBrand = trimToNull(candidate.getBrand());
        String existingTitleEn = trimToNull(existing.getTitleEn());
        ProductImageProfileRecord record = new ProductImageProfileRecord();
        record.setId(existing.getId());
        record.setOwnerUserId(existing.getOwnerUserId());
        record.setStoreCode(existing.getStoreCode());
        record.setLogicalStoreId(existing.getLogicalStoreId());
        record.setPskuCode(existing.getPskuCode());
        record.setProductIdentityKey(existing.getProductIdentityKey());
        record.setProductMasterId(candidate.getProductMasterId());
        record.setProductTitle(candidateTitle == null ? trimToNull(existing.getProductTitle()) : candidateTitle);
        record.setBrand(candidateBrand == null ? trimToNull(existing.getBrand()) : candidateBrand);
        record.setTitleAr(trimToNull(existing.getTitleAr()));
        record.setTitleEn(existingTitleEn == null ? candidateTitle : existingTitleEn);
        record.setSpecSummary(trimToNull(existing.getSpecSummary()));
        record.setProductFactText(trimToNull(existing.getProductFactText()) == null
                ? initialProductFactText(candidate)
                : existing.getProductFactText());
        record.setHeroSellingPointsJson(trimToNull(existing.getHeroSellingPointsJson()) == null
                ? "[]"
                : existing.getHeroSellingPointsJson());
        record.setProfileStatus(trimToNull(existing.getProfileStatus()) == null
                ? "ACTIVE"
                : existing.getProfileStatus());
        record.setCreatedBy(existing.getCreatedBy());
        record.setUpdatedBy(operatorUserId == null ? existing.getUpdatedBy() : operatorUserId);
        record.setCreatedAt(existing.getCreatedAt());
        record.setUpdatedAt(now);
        record.setDeleted(false);
        return record;
    }

    private boolean shouldRefreshProfile(
            ProductImageProfileRecord existing,
            ProductImageProfileRecord refreshed
    ) {
        return !Objects.equals(existing.getProductMasterId(), refreshed.getProductMasterId())
                || !Objects.equals(trimToNull(existing.getProductTitle()), refreshed.getProductTitle())
                || !Objects.equals(trimToNull(existing.getBrand()), refreshed.getBrand())
                || !Objects.equals(trimToNull(existing.getTitleEn()), refreshed.getTitleEn())
                || trimToNull(existing.getProductFactText()) == null
                || trimToNull(existing.getHeroSellingPointsJson()) == null
                || trimToNull(existing.getProfileStatus()) == null;
    }

    private void appendFactLine(List<String> lines, String label, String value) {
        String text = trimToNull(value);
        if (text != null) lines.add(label + "：" + text);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }
}
