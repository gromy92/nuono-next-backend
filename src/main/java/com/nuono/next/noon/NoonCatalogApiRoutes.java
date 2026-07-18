package com.nuono.next.noon;

/** Current browser-facing routes for Noon Catalog services used by Nuono. */
public final class NoonCatalogApiRoutes {

    private static final String ROCKET_BASE_URL =
            "https://noon-catalog.noon.partners/_vs/mp/mp-noon-catalog-api-rocket";
    private static final String IMPEX_BASE_URL =
            "https://noon-catalog.noon.partners/_vs/mp/mp-partner-impex-api";

    public static final String OFFER_LIST_NOON = ROCKET_BASE_URL + "/offer/list/noon";
    public static final String OFFER_LIST_SUPERMALL = ROCKET_BASE_URL + "/offer/list/supermall";
    public static final String OFFER_STOCK_NOON = ROCKET_BASE_URL + "/offer/stock/noon";
    public static final String EXPORT_CREATE = IMPEX_BASE_URL + "/export/create";
    public static final String EXPORT_STATUS = IMPEX_BASE_URL + "/export/status";

    private NoonCatalogApiRoutes() {
    }
}
