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
    private static final Path SNAPSHOT_CHANGE_SCHEMA_PATH = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "103_operations_competitor_product_snapshot_change.sql"
    );
    private static final Path RANK_CHANNEL_SCAN_DEPTH_SCHEMA_PATH = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "104_operations_competitor_rank_fact_channel_scan_depth.sql"
    );
    private static final Path RANK_SEARCH_METADATA_SCHEMA_PATH = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "171_operations_competitor_rank_search_metadata.sql"
    );
    private static final Path KEYWORD_LINK_SCHEMA_PATH = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "init",
            "175_product_keyword_competitor_link.sql"
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
        assertTrue(sql.contains("captured_at"));
        assertTrue(sql.contains("rank_status"));
        assertTrue(sql.contains("not_in_top_20"));
        assertFalse(sql.contains("provider_failed"), "provider failures must not be encoded as rank facts");
    }

    @Test
    void competitorProductSnapshotChangeSchemaDefinesDailySnapshotsAndFieldEvents() throws IOException {
        assertTrue(Files.exists(SNAPSHOT_CHANGE_SCHEMA_PATH), "competitor product snapshot schema migration must exist");
        String sql = normalizedSql(SNAPSHOT_CHANGE_SCHEMA_PATH);

        assertTrue(sql.contains("create table if not exists operations_competitor_product_snapshot"));
        assertTrue(sql.contains("create table if not exists operations_competitor_product_change_event"));
        assertTrue(sql.contains("subject_type"));
        assertTrue(sql.contains("fact_date"));
        assertTrue(sql.contains("captured_at"));
        assertTrue(sql.contains("source_task_id"));
        assertTrue(sql.contains("source_run_id"));
        assertTrue(sql.contains("title_en"));
        assertTrue(sql.contains("title_ar"));
        assertTrue(sql.contains("seller_name"));
        assertTrue(sql.contains("main_image_url_raw"));
        assertTrue(sql.contains("main_image_url_normalized"));
        assertTrue(sql.contains("main_image_asset_key"));
        assertTrue(sql.contains("supermall_enabled"));
        assertTrue(sql.contains("sold_recently_text"));
        assertTrue(sql.contains("logistics_tags_json"));
        assertTrue(sql.contains("badges_json"));
        assertTrue(sql.contains("raw_detail_json"));
        assertTrue(sql.contains("snapshot_hash"));
        assertTrue(sql.contains("previous_snapshot_id"));
        assertTrue(sql.contains("field_key"));
        assertTrue(sql.contains("field_label"));
        assertTrue(sql.contains("old_value_json"));
        assertTrue(sql.contains("new_value_json"));
        assertTrue(sql.contains("operations_competitor_product_snapshot"));
        assertTrue(sql.contains("operations_competitor_product_change_event"));
    }

    @Test
    void rankFactSchemaSupportsNaturalSponsoredFactsAndScanDepth() throws IOException {
        assertTrue(Files.exists(RANK_CHANNEL_SCAN_DEPTH_SCHEMA_PATH), "rank fact channel migration must exist");
        String sql = normalizedSql(RANK_CHANNEL_SCAN_DEPTH_SCHEMA_PATH);

        assertTrue(sql.contains("requested_result_limit"));
        assertTrue(sql.contains("rank_channel"));
        assertTrue(sql.contains("scan_depth"));
        assertTrue(sql.contains("sponsored"));
        assertTrue(sql.contains("organic"));
        assertTrue(sql.contains("drop index uk_ops_comp_rank_fact_run_product"));
        assertTrue(sql.contains("uk_ops_comp_rank_fact_run_product_channel"));
        assertTrue(sql.contains("keyword_run_id, tracked_product_type, noon_product_code, rank_channel"));
    }

    @Test
    void rankSearchMetadataMigrationPersistsLocalizedTitlesAndTags() throws IOException {
        assertTrue(Files.exists(RANK_SEARCH_METADATA_SCHEMA_PATH), "rank search metadata migration must exist");
        String sql = normalizedSql(RANK_SEARCH_METADATA_SCHEMA_PATH);

        assertTrue(sql.contains("operations_competitor_search_result"));
        assertTrue(sql.contains("operations_competitor_product"));
        assertTrue(sql.contains("title_en_snapshot"));
        assertTrue(sql.contains("title_ar_snapshot"));
        assertTrue(sql.contains("tags_json"));
        assertTrue(sql.contains("tags_snapshot_json"));
    }

    @Test
    void competitorKeywordSchemaLinksToProductKeywordAssetsAndBackfillsHistory() throws IOException {
        assertTrue(Files.exists(KEYWORD_LINK_SCHEMA_PATH), "competitor keyword link migration must exist");
        String sql = normalizedSql(KEYWORD_LINK_SCHEMA_PATH);

        assertTrue(sql.contains("alter table operations_competitor_keyword"));
        assertTrue(sql.contains("product_keyword_id"));
        assertTrue(sql.contains("idx_ops_comp_keyword_product_keyword"));
        assertTrue(sql.contains("insert into product_keyword"));
        assertTrue(sql.contains("insert into product_keyword_usage_event"));
        assertTrue(sql.contains("competitor_keyword"));
        assertTrue(sql.contains("event_status"));
        assertTrue(sql.contains("observed"));
        assertTrue(sql.contains("update operations_competitor_keyword"));
    }

    private static String normalizedSql(Path path) throws IOException {
        return Files.readString(path)
                .replace("`", "")
                .toLowerCase(Locale.ROOT);
    }
}
