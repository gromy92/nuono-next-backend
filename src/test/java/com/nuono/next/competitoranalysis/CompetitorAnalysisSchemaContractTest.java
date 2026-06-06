package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class CompetitorAnalysisSchemaContractTest {
    private static final Path SCHEMA_PATH = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "099_operations_competitor_analysis.sql"
    );

    @Test
    void competitorAnalysisSchemaDefinesFirstPhaseTablesAndStatusSeparation() throws IOException {
        assertTrue(Files.exists(SCHEMA_PATH), "competitor analysis schema migration must exist");
        String sql = normalizedSql(SCHEMA_PATH);

        assertTrue(sql.contains("create table if not exists operations_competitor_watch_product"));
        assertTrue(sql.contains("create table if not exists operations_competitor_keyword"));
        assertTrue(sql.contains("create table if not exists operations_competitor_product"));
        assertTrue(sql.contains("create table if not exists operations_competitor_keyword_product"));
        assertTrue(sql.contains("create table if not exists operations_competitor_search_run"));
        assertTrue(sql.contains("create table if not exists operations_competitor_keyword_run"));
        assertTrue(sql.contains("create table if not exists operations_competitor_search_result"));
        assertTrue(sql.contains("create table if not exists operations_competitor_rank_fact"));
        assertTrue(sql.contains("create table if not exists operations_competitor_analysis_id_sequence"));

        assertTrue(sql.contains("product_site_offer_id"));
        assertTrue(sql.contains("self_noon_product_code"));
        assertTrue(sql.contains("active_natural_slot"));
        assertTrue(sql.contains("keyword_norm"));
        assertTrue(sql.contains("provider_status"));
        assertTrue(sql.contains("source_url"));
        assertTrue(sql.contains("parser_version"));
        assertTrue(sql.contains("provider_http_status"));
        assertTrue(sql.contains("response_hash"));
        assertTrue(sql.contains("rank_status"));
        assertTrue(sql.contains("not_in_top_30"));
        assertFalse(sql.contains("provider_failed"), "provider failures must not be encoded as rank facts");
    }

    private static String normalizedSql(Path path) throws IOException {
        return Files.readString(path)
                .replace("`", "")
                .toLowerCase(Locale.ROOT);
    }
}
