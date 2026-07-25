package com.nuono.next.product;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

final class ProductImageProfileSummaryAssembler {
    private static final DateTimeFormatter API_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ProductImageProfileSummaryAssembler() {
    }

    static ProductImageProfileSummaryView fromRecord(ProductImageProfileSummaryRecord record) {
        ProductImageProfileSummaryView view = new ProductImageProfileSummaryView();
        view.setId(record.getId());
        view.setOwnerUserId(record.getOwnerUserId());
        view.setStoreCode(record.getStoreCode());
        view.setPskuCode(record.getPskuCode());
        view.setProductIdentityKey(record.getProductIdentityKey());
        view.setProductMasterId(record.getProductMasterId());
        view.setProductTitle(record.getProductTitle());
        view.setBrand(record.getBrand());
        view.setTitleAr(record.getTitleAr());
        view.setTitleEn(record.getTitleEn());
        view.setSpecSummary(record.getSpecSummary());
        view.setCoverImageUrl(record.getCoverImageUrl());
        view.setAssetCount(orZero(record.getAssetCount()));
        view.setSuiteCount(orZero(record.getSuiteCount()));
        view.setActiveSuiteCount(orZero(record.getActiveSuiteCount()));
        view.setHasAdoptedSuite(Boolean.TRUE.equals(record.getHasAdoptedSuite()));
        applyStatuses(
                view,
                record.getBrand(),
                record.getTitleAr(),
                record.getTitleEn(),
                record.getSpecSummary(),
                record.getProductFactText(),
                record.getPrimarySuiteStatus()
        );
        view.setUpdatedAt(record.getUpdatedAt() == null ? null : API_TIME_FORMATTER.format(record.getUpdatedAt()));
        return view;
    }

    static ProductImageProfileSummaryView fromCandidate(
            Long ownerUserId,
            String storeCode,
            ProductImageProductCandidateRecord candidate,
            String productFactText
    ) {
        ProductImageProfileSummaryView view = new ProductImageProfileSummaryView();
        view.setOwnerUserId(ownerUserId);
        view.setStoreCode(storeCode);
        view.setPskuCode(candidate.getPskuCode());
        view.setProductIdentityKey(candidate.getProductIdentityKey());
        view.setProductMasterId(candidate.getProductMasterId());
        view.setProductTitle(candidate.getProductTitle());
        view.setBrand(candidate.getBrand());
        view.setCoverImageUrl(candidate.getCoverImageUrl());
        view.setAssetCount(StringUtils.hasText(candidate.getCoverImageUrl()) ? 1 : 0);
        view.setSuiteCount(0);
        view.setActiveSuiteCount(0);
        view.setHasAdoptedSuite(false);
        applyStatuses(view, candidate.getBrand(), null, candidate.getProductTitle(), null, productFactText, null);
        return view;
    }

    private static void applyStatuses(
            ProductImageProfileSummaryView view,
            String brand,
            String titleAr,
            String titleEn,
            String specSummary,
            String productFactText,
            ProductImageSuiteStatus primarySuiteStatus
    ) {
        List<ProductImageProfileMissingField> missingFields = new ArrayList<>();
        if (!StringUtils.hasText(brand)) missingFields.add(ProductImageProfileMissingField.BRAND);
        if (!StringUtils.hasText(titleAr) && !StringUtils.hasText(titleEn)) {
            missingFields.add(ProductImageProfileMissingField.BILINGUAL_TITLE);
        }
        if (!StringUtils.hasText(specSummary)) missingFields.add(ProductImageProfileMissingField.SPEC_SUMMARY);
        if (!StringUtils.hasText(productFactText)) missingFields.add(ProductImageProfileMissingField.PRODUCT_FACTS);
        if (view.getAssetCount() == null || view.getAssetCount() <= 0) {
            missingFields.add(ProductImageProfileMissingField.BASE_IMAGE);
        }
        view.setMissingProfileFields(missingFields);
        view.setProfileReadinessStatus(missingFields.isEmpty()
                ? ProductImageProfileReadinessStatus.COMPLETE
                : ProductImageProfileReadinessStatus.INCOMPLETE);
        view.setImageStatus(toImageStatus(primarySuiteStatus));
    }

    private static ProductImageSummaryStatus toImageStatus(ProductImageSuiteStatus suiteStatus) {
        if (suiteStatus == null) return ProductImageSummaryStatus.NOT_REQUESTED;
        switch (suiteStatus) {
            case DRAFT:
                return ProductImageSummaryStatus.CANDIDATE;
            case PENDING_GENERATION:
            case GENERATING:
            case REGENERATING:
                return ProductImageSummaryStatus.GENERATING;
            case PENDING_REVIEW:
            case ADOPTED:
                return ProductImageSummaryStatus.PENDING_CONFIRMATION;
            case PUBLISHING:
                return ProductImageSummaryStatus.PUBLISHING;
            case ONLINE:
                return ProductImageSummaryStatus.ONLINE;
            case FAILED:
                return ProductImageSummaryStatus.ACTION_REQUIRED;
            default:
                return ProductImageSummaryStatus.NOT_REQUESTED;
        }
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
