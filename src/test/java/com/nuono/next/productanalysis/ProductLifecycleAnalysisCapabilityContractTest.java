package com.nuono.next.productanalysis;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.permission.access.BusinessCapability;
import org.junit.jupiter.api.Test;

class ProductLifecycleAnalysisCapabilityContractTest {

    @Test
    void salesDataCapabilityCoversProductLifecycleAnalysisRoutes() {
        assertTrue(
                BusinessCapability.SALES_DATA.getMenuPathPrefixes().contains("/data/product-analysis"),
                "Sales data capability must cover the product analysis workspace route."
        );
        assertTrue(
                BusinessCapability.SALES_DATA.getMenuPathPrefixes().contains("/api/product-analysis/lifecycle"),
                "Sales data capability must cover the product lifecycle analysis API."
        );
    }
}
