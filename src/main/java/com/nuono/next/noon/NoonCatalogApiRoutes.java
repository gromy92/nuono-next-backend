package com.nuono.next.noon;

/** Current browser-facing routes for Noon Catalog services used by Nuono. */
public final class NoonCatalogApiRoutes {

    private static final String ROCKET_BASE_URL =
            "https://noon-catalog.noon.partners/_vs/mp/mp-noon-catalog-api-rocket";
    private static final String IMPEX_BASE_URL =
            "https://noon-catalog.noon.partners/_vs/mp/mp-partner-impex-api";
    private static final String CONTENT_BASE_URL =
            "https://noon-catalog.noon.partners/_vs/mp/mp-noon-catalog-api-content";

    public static final String OFFER_LIST_NOON = ROCKET_BASE_URL + "/offer/list/noon";
    public static final String OFFER_LIST_SUPERMALL = ROCKET_BASE_URL + "/offer/list/supermall";
    public static final String OFFER_STOCK_NOON = ROCKET_BASE_URL + "/offer/stock/noon";
    public static final String EXPORT_CREATE = IMPEX_BASE_URL + "/export/create";
    public static final String EXPORT_STATUS = IMPEX_BASE_URL + "/export/status";
    public static final String ZSKU_RETRIEVE = CONTENT_BASE_URL + "/catplat/zsku/retrieve";
    public static final String GROUP_CURRENT_PREFIX = CONTENT_BASE_URL + "/catalog/v2/group/";
    public static final String GROUP_DETAIL = CONTENT_BASE_URL + "/catplat/group/get";
    public static final String GROUP_LIST = CONTENT_BASE_URL + "/catalog/groups/list";
    public static final String VARIANT_INFORMATION = CONTENT_BASE_URL + "/catplat/variants/information";
    public static final String PRICING_INFORMATION =
            "https://noon-catalog.noon.partners/_vs/mp/mp-pricing-api/pricing/info";

    private NoonCatalogApiRoutes() {
    }
}
