package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InTransitFreightCostMapperContractTest {

    private static final Path MAPPER = Path.of(
            "src",
            "main",
            "java",
            "com",
            "nuono",
            "next",
            "infrastructure",
            "mapper",
            "InTransitFreightCostMapper.java"
    );

    @Test
    void statisticsAggregatesComponentsBeforeSummingBillAmounts() throws IOException {
        String source = Files.readString(MAPPER);

        assertTrue(source.contains(") component_summary"));
        assertTrue(source.contains("SUM(COALESCE(component_summary.component_count, 0)) AS component_count"));
        assertTrue(source.contains("SUM(COALESCE(bill.cny_total_amount, 0)) AS total_amount_cny"));
        assertFalse(source.contains("COUNT(component.id) AS component_count"));
    }

    @Test
    void skuHistoryIsFilteredByPskuAndSite() throws IOException {
        String source = Files.readString(MAPPER);

        assertTrue(source.contains("listSkuSiteActualCosts"));
        assertTrue(source.contains("component.psku = #{psku}"));
        assertTrue(source.contains("component.target_site_code = #{targetSiteCode}"));
    }
}
