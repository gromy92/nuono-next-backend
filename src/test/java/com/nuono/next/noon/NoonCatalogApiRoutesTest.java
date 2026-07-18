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

        assertFalse(List.of(
                NoonCatalogApiRoutes.OFFER_LIST_NOON,
                NoonCatalogApiRoutes.OFFER_LIST_SUPERMALL,
                NoonCatalogApiRoutes.OFFER_STOCK_NOON,
                NoonCatalogApiRoutes.EXPORT_CREATE,
                NoonCatalogApiRoutes.EXPORT_STATUS
        ).stream().anyMatch(url -> url.contains("/_svc/")));
    }
}
