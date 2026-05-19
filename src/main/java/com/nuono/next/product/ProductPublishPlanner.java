package com.nuono.next.product;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

public class ProductPublishPlanner {

    private static final List<String> PUBLISH_OFFER_FIELDS = List.of(
            "site",
            "pskuCode",
            "offerCode",
            "currency",
            "price",
            "salePrice",
            "saleStart",
            "saleEnd",
            "priceMin",
            "priceMax",
            "isActive",
            "idWarranty"
    );

    private final ProductDraftMergePolicy draftMergePolicy;

    public ProductPublishPlanner(ProductDraftMergePolicy draftMergePolicy) {
        this.draftMergePolicy = draftMergePolicy;
    }

    public ProductPublishPlan plan(
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) {
        ProductMasterSnapshotView publishable = draft;
        hydrateOffers(publishable, baseline, currentSiteCode);
        List<String> blockers = new ArrayList<>();
        if (hasLocalImageUrl(publishable)) {
            blockers.add("本地上传图片仍是系统相对地址，不能发布到 Noon。");
        }
        return new ProductPublishPlan(publishable, blockers);
    }

    private void hydrateOffers(
            ProductMasterSnapshotView publishable,
            ProductMasterSnapshotView baseline,
            String currentSiteCode
    ) {
        if (publishable == null || baseline == null || publishable.getSiteOffers() == null || baseline.getSiteOffers() == null) {
            return;
        }
        Map<String, Map<String, Object>> baselineOffers = offersByStoreCode(baseline.getSiteOffers());
        List<Map<String, Object>> hydratedOffers = new ArrayList<>();
        for (Map<String, Object> offer : publishable.getSiteOffers()) {
            Map<String, Object> mutableOffer = offer == null ? new LinkedHashMap<>() : new LinkedHashMap<>(offer);
            String storeCode = text(mutableOffer.get("storeCode"));
            if (StringUtils.hasText(currentSiteCode) && !currentSiteCode.equalsIgnoreCase(storeCode)) {
                hydratedOffers.add(mutableOffer);
                continue;
            }
            Map<String, Object> baselineOffer = baselineOffers.get(storeCode);
            if (baselineOffer == null) {
                hydratedOffers.add(mutableOffer);
                continue;
            }
            Map<String, Object> hydrated = draftMergePolicy.hydrateMissingOfferFieldsForPublish(
                    mutableOffer,
                    baselineOffer,
                    PUBLISH_OFFER_FIELDS
            );
            hydratedOffers.add(hydrated);
        }
        publishable.setSiteOffers(hydratedOffers);
    }

    private boolean hasLocalImageUrl(ProductMasterSnapshotView snapshot) {
        if (snapshot == null || snapshot.getContent() == null) {
            return false;
        }
        Object images = snapshot.getContent().get("images");
        if (!(images instanceof List<?>)) {
            return false;
        }
        for (Object image : (List<?>) images) {
            String url = text(image);
            if (url.startsWith("/api/product-master/image-assets/")) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Map<String, Object>> offersByStoreCode(List<Map<String, Object>> offers) {
        Map<String, Map<String, Object>> byStore = new LinkedHashMap<>();
        for (Map<String, Object> offer : offers) {
            String storeCode = text(offer.get("storeCode"));
            if (StringUtils.hasText(storeCode)) {
                byStore.put(storeCode, offer);
            }
        }
        return byStore;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
