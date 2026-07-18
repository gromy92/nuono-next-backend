package com.nuono.next.noon;

/** Current browser-facing routes for the Noon Catalog Rocket service. */
public final class NoonCatalogApiRoutes {

    private static final String ROCKET_BASE_URL =
            "https://noon-catalog.noon.partners/_vs/mp/mp-noon-catalog-api-rocket";

    public static final String OFFER_LIST_NOON = ROCKET_BASE_URL + "/offer/list/noon";
    public static final String OFFER_LIST_SUPERMALL = ROCKET_BASE_URL + "/offer/list/supermall";
    public static final String OFFER_STOCK_NOON = ROCKET_BASE_URL + "/offer/stock/noon";

    private NoonCatalogApiRoutes() {
    }
}
