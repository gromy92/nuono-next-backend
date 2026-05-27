package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ProductLifecycleFirstRunInitializerTest {

    private final ProductLifecycleListingDateResolver resolver = new ProductLifecycleListingDateResolver();
    private final ProductLifecycleFirstRunInitializer initializer = new ProductLifecycleFirstRunInitializer();

    @Test
    void initializesPulledOnlyRecentProductAsLowConfidenceNewCandidate() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        ProductLifecycleListingDateResolution resolution = resolver.resolve(new ProductLifecycleListingSignals(
                query(),
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 5, 15),
                0,
                0,
                0,
                0
        ), analysisDate);

        ProductLifecycleCurrentState state = initializer.initialize(query(), resolution, analysisDate, 72001L);

        assertEquals("new", state.getLifecycleCode());
        assertEquals("新品", state.getLifecycleLabel());
        assertEquals(LocalDate.of(2026, 5, 15), state.getListingDate());
        assertEquals("pulled", state.getListingDateSource());
        assertEquals("low_confidence", state.getQualityState());
        assertTrue(state.getEvidenceJson().contains("\"confidence\":\"low\""));
    }

    @Test
    void doesNotInitializeRecentPulledProductAsNewWhenSixtyDaySignalsExist() {
        LocalDate analysisDate = LocalDate.of(2026, 5, 20);
        ProductLifecycleListingDateResolution resolution = resolver.resolve(new ProductLifecycleListingSignals(
                query(),
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 5, 15),
                60,
                30,
                60,
                0
        ), analysisDate);

        ProductLifecycleCurrentState state = initializer.initialize(query(), resolution, analysisDate, 72001L);

        assertNotEquals("new", state.getLifecycleCode());
        assertEquals("data_insufficient", state.getLifecycleCode());
        assertEquals("historical_old_product", state.getQualityState());
        assertTrue(state.getEvidenceJson().contains("\"historicalOldProduct\":true"));
    }

    private ProductLifecycleStateQuery query() {
        return new ProductLifecycleStateQuery(
                10002L,
                "STR245027-SAU",
                "SA",
                "SAMPLE-SKU-001",
                "Z57C90A4184D0CFD75218Z-1"
        );
    }
}
