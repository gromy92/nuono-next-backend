package com.nuono.next.store;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.noon.NoonCatalogApiRoutes;
import org.junit.jupiter.api.Test;

class NoonCatalogConnectionProbeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void defaultProbeUsesCurrentCatalogOfferListRoute() {
        assertEquals(
                NoonCatalogApiRoutes.OFFER_LIST_NOON,
                NoonCatalogConnectionProbe.DEFAULT_OFFER_LIST_URL
        );
    }

    @Test
    void offerListShapeIsAccepted() throws Exception {
        assertDoesNotThrow(() -> NoonCatalogConnectionProbe.requireValidCatalogResponse(
                objectMapper.readTree("{\"data\":{\"hits\":[]}}")
        ));
    }

    @Test
    void malformedOrBusinessErrorResponseFailsClosed() throws Exception {
        assertThrows(
                IllegalStateException.class,
                () -> NoonCatalogConnectionProbe.requireValidCatalogResponse(
                        objectMapper.readTree("{}")
                )
        );
        assertThrows(
                IllegalStateException.class,
                () -> NoonCatalogConnectionProbe.requireValidCatalogResponse(
                        objectMapper.readTree(
                                "{\"error\":\"unauthorized\",\"data\":{\"hits\":[]}}")
                )
        );
    }
}
