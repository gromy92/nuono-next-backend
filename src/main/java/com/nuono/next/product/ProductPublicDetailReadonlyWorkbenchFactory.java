package com.nuono.next.product;

import com.nuono.next.productpublicdetail.ProductPublicDetailSnapshot;
import com.nuono.next.productpublicdetail.ProductPublicDetailSyncStatus;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

class ProductPublicDetailReadonlyWorkbenchFactory {

    static final String MODE = "public-detail-readonly";
    static final String READONLY_WARNING =
            "当前商品仅拿到 Noon 前台公开详情，尚未拿到可写的 Noon catalog 详情基线；此视图只允许查看，保存和发布已禁用。";

    private static final DateTimeFormatter FETCH_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    ProductMasterSnapshotView buildBaseline(
            ProductMasterFetchCommand command,
            StoreSyncStoreRecord store,
            ProductListProjectionRecord projection,
            ProductPublicDetailSnapshot detail
    ) {
        if (projection == null || !isUsablePublicDetail(detail)) {
            return null;
        }

        ProductMasterSnapshotView baseline = new ProductMasterSnapshotView();
        baseline.setMode(MODE);
        baseline.setReady(true);
        baseline.setDegraded(true);
        baseline.setMessage("已使用 Noon 前台公开详情打开只读视图。");
        baseline.setWarnings(new ArrayList<>(List.of(READONLY_WARNING)));
        baseline.setStoreContext(buildStoreContext(command, store, detail));
        baseline.setIdentity(buildIdentity(projection, detail));
        baseline.setTaxonomy(buildTaxonomy(projection, detail));
        baseline.setContent(buildContent(projection, detail));
        baseline.setPricing(buildPricing(projection, detail));
        baseline.setSiteOffers(buildSiteOffers(store, projection, detail));
        return baseline;
    }

    boolean isReadonlySnapshot(ProductMasterSnapshotView snapshot) {
        return snapshot != null && MODE.equalsIgnoreCase(text(snapshot.getMode()));
    }

    private Map<String, Object> buildStoreContext(
            ProductMasterFetchCommand command,
            StoreSyncStoreRecord store,
            ProductPublicDetailSnapshot detail
    ) {
        Map<String, Object> context = new LinkedHashMap<>();
        putIfNotNull(context, "ownerUserId", command == null ? null : command.getOwnerUserId());
        putIfNotBlank(context, "projectName", store == null ? null : store.getProjectName());
        putIfNotBlank(context, "projectCode", store == null ? null : store.getProjectCode());
        putIfNotBlank(context, "storeCode", firstNonBlank(
                detail == null ? null : detail.getStoreCode(),
                store == null ? null : store.getStoreCode(),
                command == null ? null : command.getStoreCode()
        ));
        putIfNotBlank(context, "site", firstNonBlank(
                detail == null ? null : detail.getSiteCode(),
                store == null ? null : store.getSite()
        ));
        if (detail != null && detail.getFetchedAt() != null) {
            putIfNotBlank(context, "fetchedAt", FETCH_TIME_FORMATTER.format(detail.getFetchedAt()));
        }
        putIfNotBlank(context, "source", "NOON_PUBLIC_DETAIL");
        return context;
    }

    private Map<String, Object> buildIdentity(
            ProductListProjectionRecord projection,
            ProductPublicDetailSnapshot detail
    ) {
        Map<String, Object> identity = new LinkedHashMap<>();
        String skuParent = firstNonBlank(detail.getSkuParent(), projection.getSkuParent(), detail.getNoonProductCode());
        putIfNotBlank(identity, "skuParent", skuParent);
        putIfNotBlank(identity, "partnerSku", firstNonBlank(detail.getPartnerSku(), projection.getPartnerSku()));
        putIfNotBlank(identity, "pskuCode", projection.getPskuCode());
        putIfNotBlank(identity, "brand", firstNonBlank(detail.getBrand(), projection.getBrand()));
        putIfNotBlank(identity, "childSku", detail.getNoonProductCode());
        putIfNotBlank(identity, "noonProductCode", detail.getNoonProductCode());
        putIfNotBlank(identity, "codeType", detail.getCodeType());
        putIfNotBlank(identity, "productSourceType", ProductSourceTypeSupport.resolve(
                projection.getProductSourceType(),
                detail.getNoonProductCode(),
                skuParent
        ));
        putIfNotNull(identity, "readonly", true);
        return identity;
    }

    private Map<String, Object> buildTaxonomy(
            ProductListProjectionRecord projection,
            ProductPublicDetailSnapshot detail
    ) {
        Map<String, Object> taxonomy = new LinkedHashMap<>();
        putIfNotBlank(taxonomy, "productFulltype", projection.getProductFulltype());
        putIfNotBlank(taxonomy, "categoryPath", detail.getCategoryPath());
        return taxonomy;
    }

    private Map<String, Object> buildContent(
            ProductListProjectionRecord projection,
            ProductPublicDetailSnapshot detail
    ) {
        Map<String, Object> content = new LinkedHashMap<>();
        putIfNotBlank(content, "titleEn", firstNonBlank(detail.getTitleEn(), projection.getTitle()));
        putIfNotBlank(content, "titleAr", detail.getTitleAr());
        putIfNotBlank(content, "titleCn", projection.getTitleCn());
        List<String> images = new ArrayList<>();
        String image = firstNonBlank(detail.getMainImageUrl(), projection.getImageUrl());
        if (StringUtils.hasText(image)) {
            images.add(image);
        }
        content.put("images", images);
        content.put("imageCount", images.size());
        putIfNotBlank(content, "detailUrl", detail.getDetailUrl());
        return content;
    }

    private Map<String, Object> buildPricing(
            ProductListProjectionRecord projection,
            ProductPublicDetailSnapshot detail
    ) {
        Map<String, Object> pricing = new LinkedHashMap<>();
        putIfNotBlank(pricing, "price", firstNonBlank(decimalText(detail.getPriceAmount()), projection.getOriginalPrice(), projection.getReferencePrice()));
        putIfNotBlank(pricing, "salePrice", projection.getSalePrice());
        putIfNotBlank(pricing, "currency", detail.getCurrencyCode());
        return pricing;
    }

    private List<Map<String, Object>> buildSiteOffers(
            StoreSyncStoreRecord store,
            ProductListProjectionRecord projection,
            ProductPublicDetailSnapshot detail
    ) {
        Map<String, Object> siteOffer = new LinkedHashMap<>();
        putIfNotBlank(siteOffer, "storeCode", firstNonBlank(
                detail.getStoreCode(),
                store == null ? null : store.getStoreCode()
        ));
        putIfNotBlank(siteOffer, "site", firstNonBlank(
                detail.getSiteCode(),
                store == null ? null : store.getSite()
        ));
        putIfNotBlank(siteOffer, "partnerSku", firstNonBlank(detail.getPartnerSku(), projection.getPartnerSku()));
        putIfNotBlank(siteOffer, "pskuCode", projection.getPskuCode());
        putIfNotBlank(siteOffer, "price", firstNonBlank(decimalText(detail.getPriceAmount()), projection.getOriginalPrice(), projection.getReferencePrice()));
        putIfNotBlank(siteOffer, "salePrice", projection.getSalePrice());
        putIfNotBlank(siteOffer, "currency", detail.getCurrencyCode());
        putIfNotBlank(siteOffer, "statusCode", projection.getCurrentSiteLiveStatus());
        putIfNotNull(siteOffer, "readonly", true);
        return List.of(siteOffer);
    }

    private boolean isUsablePublicDetail(ProductPublicDetailSnapshot detail) {
        if (detail == null) {
            return false;
        }
        ProductPublicDetailSyncStatus status = detail.getSyncStatus();
        return status == ProductPublicDetailSyncStatus.SUCCEEDED || status == ProductPublicDetailSyncStatus.PARTIAL;
    }

    private String decimalText(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value.trim());
        }
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = text(value);
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private String text(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
