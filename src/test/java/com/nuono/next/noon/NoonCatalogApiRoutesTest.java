package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

class NoonCatalogApiRoutesTest {

    @Test
    void catalogRocketDefaultsUseTheCurrentPartnersWebProxy() {
        assertEquals(
                "https://noon-catalog.noon.partners/_vs/mp/mp-noon-catalog-api-rocket/offer/list/noon",
                NoonCatalogApiRoutes.OFFER_LIST_NOON
        );
        assertEquals(
                "https://noon-catalog.noon.partners/_vs/mp/mp-noon-catalog-api-rocket/offer/list/supermall",
                NoonCatalogApiRoutes.OFFER_LIST_SUPERMALL
        );
        assertEquals(
                "https://noon-catalog.noon.partners/_vs/mp/mp-noon-catalog-api-rocket/offer/stock/noon",
                NoonCatalogApiRoutes.OFFER_STOCK_NOON
        );
        assertEquals(
                "https://noon-catalog.noon.partners/_vs/mp/mp-partner-impex-api/export/create",
                NoonCatalogApiRoutes.EXPORT_CREATE
        );
        assertEquals(
                "https://noon-catalog.noon.partners/_vs/mp/mp-partner-impex-api/export/status",
                NoonCatalogApiRoutes.EXPORT_STATUS
        );
        assertEquals(
                "https://noon-catalog.noon.partners/_vs/mp/mp-noon-catalog-api-content/catplat/zsku/retrieve",
                NoonCatalogApiRoutes.ZSKU_RETRIEVE
        );
        assertEquals(
                "https://noon-catalog.noon.partners/_vs/mp/mp-noon-catalog-api-content/catplat/variants/information",
                NoonCatalogApiRoutes.VARIANT_INFORMATION
        );
        assertEquals(
                "https://noon-catalog.noon.partners/_vs/mp/mp-pricing-api/pricing/info",
                NoonCatalogApiRoutes.PRICING_INFORMATION
        );

        assertFalse(List.of(
                NoonCatalogApiRoutes.OFFER_LIST_NOON,
                NoonCatalogApiRoutes.OFFER_LIST_SUPERMALL,
                NoonCatalogApiRoutes.OFFER_STOCK_NOON,
                NoonCatalogApiRoutes.EXPORT_CREATE,
                NoonCatalogApiRoutes.EXPORT_STATUS,
                NoonCatalogApiRoutes.ZSKU_RETRIEVE,
                NoonCatalogApiRoutes.GROUP_CURRENT_PREFIX,
                NoonCatalogApiRoutes.GROUP_DETAIL,
                NoonCatalogApiRoutes.GROUP_LIST,
                NoonCatalogApiRoutes.VARIANT_INFORMATION,
                NoonCatalogApiRoutes.PRICING_INFORMATION
        ).stream().anyMatch(url -> url.contains("/_svc/")));
    }
}
