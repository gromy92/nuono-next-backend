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

        assertFalse(List.of(
                NoonCatalogApiRoutes.OFFER_LIST_NOON,
                NoonCatalogApiRoutes.OFFER_LIST_SUPERMALL,
                NoonCatalogApiRoutes.OFFER_STOCK_NOON
        ).stream().anyMatch(url -> url.contains("/_svc/mp-noon-catalog-api-rocket/")));
    }
}
