package com.nuono.next.productlisting;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nuono.product-listing.real-write")
public class ProductListingRealWriteProperties {

    private boolean enabled;
    private boolean offerUpsertEnabled;
    private Endpoints endpoints = new Endpoints();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isOfferUpsertEnabled() {
        return offerUpsertEnabled;
    }

    public void setOfferUpsertEnabled(boolean offerUpsertEnabled) {
        this.offerUpsertEnabled = offerUpsertEnabled;
    }

    public Endpoints getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(Endpoints endpoints) {
        this.endpoints = endpoints == null ? new Endpoints() : endpoints;
    }

    public static class Endpoints {
        public static final String DEFAULT_CREATE_PRODUCT_URL =
                "https://noon-catalog.noon.partners/_svc/mp-partner-catalog/catalog/product/create";
        public static final String DEFAULT_SKU_CACHE_URL =
                "https://noon-catalog.noon.partners/_svc/mp-noon-catalog-api-content/catplat/sku/cache";
        public static final String DEFAULT_UPSERT_ZSKU_URL =
                "https://noon-catalog.noon.partners/_svc/mp-noon-catalog-api-content/catplat/zsku/upsert";
        public static final String DEFAULT_UPSERT_OFFER_URL =
                "https://noon-catalog.noon.partners/_svc/mp-partner-catalog/offer/upsert";
        public static final String DEFAULT_WAREHOUSE_LIST_URL =
                "https://fbp.sc.noon.partners/_svc/sc-ds-api/v2/onboarding/warehouse-list";
        public static final String DEFAULT_UPSERT_PRICE_URL =
                "https://noon-catalog.noon.partners/_vs/mp/mp-noon-catalog-api-offermgmt/offer/upsert/price";
        public static final String DEFAULT_UPSERT_WARRANTY_URL =
                "https://noon-catalog.noon.partners/_vs/mp/mp-noon-catalog-api-offermgmt/offer/upsert/id_warranty";
        public static final String DEFAULT_UPSERT_BARCODE_URL =
                "https://noon-catalog.noon.partners/_svc/mp-partner-catalog/pbarcode/upsert";

        private String createProductUrl = DEFAULT_CREATE_PRODUCT_URL;
        private String skuCacheUrl = DEFAULT_SKU_CACHE_URL;
        private String upsertZskuUrl = DEFAULT_UPSERT_ZSKU_URL;
        private String upsertOfferUrl = DEFAULT_UPSERT_OFFER_URL;
        private String warehouseListUrl = DEFAULT_WAREHOUSE_LIST_URL;
        private String upsertPriceUrl = DEFAULT_UPSERT_PRICE_URL;
        private String upsertWarrantyUrl = DEFAULT_UPSERT_WARRANTY_URL;
        private String upsertBarcodeUrl = DEFAULT_UPSERT_BARCODE_URL;

        public String getCreateProductUrl() {
            return createProductUrl;
        }

        public void setCreateProductUrl(String createProductUrl) {
            this.createProductUrl = createProductUrl;
        }

        public String getSkuCacheUrl() {
            return skuCacheUrl;
        }

        public void setSkuCacheUrl(String skuCacheUrl) {
            this.skuCacheUrl = skuCacheUrl;
        }

        public String getUpsertZskuUrl() {
            return upsertZskuUrl;
        }

        public void setUpsertZskuUrl(String upsertZskuUrl) {
            this.upsertZskuUrl = upsertZskuUrl;
        }

        public String getUpsertOfferUrl() {
            return upsertOfferUrl;
        }

        public void setUpsertOfferUrl(String upsertOfferUrl) {
            this.upsertOfferUrl = upsertOfferUrl;
        }

        public String getWarehouseListUrl() {
            return warehouseListUrl;
        }

        public void setWarehouseListUrl(String warehouseListUrl) {
            this.warehouseListUrl = warehouseListUrl;
        }

        public String getUpsertPriceUrl() {
            return upsertPriceUrl;
        }

        public void setUpsertPriceUrl(String upsertPriceUrl) {
            this.upsertPriceUrl = upsertPriceUrl;
        }

        public String getUpsertWarrantyUrl() {
            return upsertWarrantyUrl;
        }

        public void setUpsertWarrantyUrl(String upsertWarrantyUrl) {
            this.upsertWarrantyUrl = upsertWarrantyUrl;
        }

        public String getUpsertBarcodeUrl() {
            return upsertBarcodeUrl;
        }

        public void setUpsertBarcodeUrl(String upsertBarcodeUrl) {
            this.upsertBarcodeUrl = upsertBarcodeUrl;
        }
    }
}
