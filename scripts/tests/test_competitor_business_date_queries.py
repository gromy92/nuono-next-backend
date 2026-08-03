import re
import unittest

from scripts.competitor_business_date import queries


class FixedReadOnlyQueryContractTest(unittest.TestCase):
    def test_every_query_is_one_encoded_select_only_statement(self):
        self.assertEqual(9, len(queries.READ_ONLY_QUERIES))
        forbidden = re.compile(
            r"^\s*(INSERT|UPDATE|DELETE|ALTER|CREATE|DROP|TRUNCATE|CALL|DO|SET|"
            r"PREPARE|EXECUTE|LOAD|REPLACE\s+INTO)\b",
            re.IGNORECASE | re.MULTILINE,
        )
        for name, sql in queries.READ_ONLY_QUERIES.items():
            with self.subTest(query=name):
                self.assertRegex(sql.lstrip(), r"^(SELECT|WITH)\b")
                self.assertEqual(1, sql.count(";"))
                self.assertTrue(sql.rstrip().endswith(";"))
                self.assertIsNone(forbidden.search(sql))
                self.assertIn("TO_BASE64", sql)
                self.assertIn("JSON_OBJECT", sql)
                self.assertIn("ORDER BY", sql)

    def test_large_search_result_payload_is_never_read(self):
        combined = "\n".join(queries.READ_ONLY_QUERIES.values())
        self.assertNotIn("operations_competitor_search_result", combined)
        self.assertNotIn("raw_result_json", combined)
        self.assertNotIn("INTERVAL 8 HOUR", combined.upper())

    def test_fixed_boundaries_cannot_be_overridden(self):
        self.assertEqual(358244, queries.SNAPSHOT_CUTOFF)
        self.assertEqual(1001946, queries.RANK_CUTOFF)
        self.assertEqual("2026-07-28 20:00:50", queries.WRITER_CUTOVER)
        self.assertEqual("2026-07-29 16:28:40", queries.LIST_ONLY_RUNTIME)
        with self.assertRaises(TypeError):
            queries.READ_ONLY_QUERIES["other"] = "SELECT 1"


class FingerprintAndAmbiguityQueryTest(unittest.TestCase):
    def test_fingerprint_covers_server_columns_and_indexes(self):
        sql = queries.SERVER_SCHEMA_FINGERPRINT_SQL
        for anchor in (
            "VERSION()", "@@GLOBAL.time_zone", "@@SESSION.time_zone",
            "@@system_time_zone", "@@SESSION.sql_mode",
            "INFORMATION_SCHEMA.TABLES", "INFORMATION_SCHEMA.COLUMNS",
            "INFORMATION_SCHEMA.STATISTICS", "GENERATION_EXPRESSION",
            "operations_competitor_product_snapshot",
            "operations_competitor_product_change_event",
            "operations_competitor_rank_fact",
            "operations_competitor_keyword_run",
            "operations_competitor_analysis_id_sequence",
            "operations_competitor_correction_writer_fence",
            "TABLE_COMMENT", "COLLATION", "CHECK_CONSTRAINTS",
        ):
            self.assertIn(anchor.lower(), sql.lower())

    def test_ambiguity_audit_checks_both_sides_of_both_cutoffs(self):
        sql = queries.AMBIGUITY_AUDIT_SQL
        self.assertIn("id <= 358244", sql)
        self.assertIn("id > 358244", sql)
        self.assertIn("id <= 1001946", sql)
        self.assertIn("id > 1001946", sql)
        self.assertGreaterEqual(sql.count("gmt_updated > '2026-07-28 20:00:50'"), 2)
        self.assertIn("captured_at < '2026-07-28 20:00:50'", sql)
        self.assertIn("fact_time < '2026-07-28 20:00:50'", sql)
        self.assertIn("SNAPSHOT_EVENT_CONTRACT_SPANS_LIST_V1_CUTOVER", sql)
        self.assertIn("ORDER BY table_name, id, audit_kind", sql)


class FullRowExtractQueryTest(unittest.TestCase):
    def assert_columns_present(self, sql, columns):
        for column in columns:
            with self.subTest(column=column):
                self.assertIn(f"'{column}'", sql)
                self.assertRegex(sql, rf"\.`{re.escape(column)}`")

    def test_snapshot_chain_has_all_columns_types_scope_and_order(self):
        sql = queries.SNAPSHOT_CHAIN_SQL
        self.assert_columns_present(sql, (
            "id", "owner_user_id", "watch_product_id", "competitor_product_id",
            "subject_type", "site_code", "noon_product_code", "code_type",
            "fact_date", "captured_at", "source_task_id", "source_run_id",
            "detail_url", "title_en", "title_ar", "brand", "seller_name",
            "price_amount", "currency_code", "rating", "review_count",
            "main_image_url_raw", "main_image_url_normalized",
            "main_image_asset_key", "supermall_enabled", "sold_recently_text",
            "logistics_tags_json", "badges_json", "availability_status",
            "snapshot_hash", "raw_detail_json", "is_deleted", "created_by",
            "updated_by", "gmt_create", "gmt_updated",
        ))
        self.assertIn("id <= 358244", sql)
        self.assertIn("WHERE s.is_deleted = b'0'", sql)
        self.assertIn("CAST(`s`.`supermall_enabled` AS UNSIGNED)", sql)
        self.assertIn("CAST(`s`.`price_amount` AS CHAR)", sql)
        self.assertIn("CAST(`s`.`fact_date` AS CHAR)", sql)
        self.assertIn("CAST(`s`.`raw_detail_json` AS CHAR", sql)
        self.assertIn(
            "ORDER BY s.watch_product_id, s.subject_type, s.noon_product_code",
            sql,
        )

    def test_change_event_chain_has_complete_rows_including_deleted(self):
        sql = queries.CHANGE_EVENT_CHAIN_SQL
        self.assert_columns_present(sql, (
            "id", "snapshot_id", "previous_snapshot_id", "owner_user_id",
            "watch_product_id", "competitor_product_id", "subject_type",
            "site_code", "noon_product_code", "fact_date", "field_key",
            "field_label", "change_type", "old_value_json", "new_value_json",
            "severity", "is_deleted", "created_by", "updated_by",
            "gmt_create", "gmt_updated",
        ))
        self.assertNotRegex(sql, r"WHERE\s+e\.is_deleted")
        self.assertIn("CAST(`e`.`old_value_json` AS CHAR", sql)

    def test_rank_and_distinct_keyword_run_extracts_are_complete(self):
        rank_sql = queries.RANK_FACT_ROWS_SQL
        self.assert_columns_present(rank_sql, (
            "id", "watch_product_id", "keyword_id", "keyword_run_id",
            "search_run_id", "fact_time", "fact_date", "tracked_product_type",
            "rank_channel", "noon_product_code", "rank_status", "rank_no",
            "scan_depth", "is_sponsored",
            "price_amount", "currency_code", "rating", "review_count",
            "source_result_id", "is_deleted", "created_by", "updated_by",
            "gmt_create", "gmt_updated",
        ))
        self.assertIn("id <= 1001946", rank_sql)
        self.assertIn("JOIN affected_keyword_runs", rank_sql)
        self.assertNotIn("WHERE r.id <= 1001946", rank_sql)
        self.assertIn("ORDER BY r.id", rank_sql)
        self.assertIn("CAST(`r`.`is_sponsored` AS UNSIGNED)", rank_sql)
        self.assertIn("CAST(`r`.`price_amount` AS CHAR)", rank_sql)

        run_sql = queries.KEYWORD_RUN_ROWS_SQL
        self.assert_columns_present(run_sql, (
            "id", "search_run_id", "keyword_id", "keyword_snapshot",
            "locale_snapshot", "provider_status", "result_count", "source_url",
            "requested_result_limit",
            "parser_version", "provider_http_status", "response_hash",
            "captured_at", "error_code", "error_message", "started_at",
            "finished_at", "is_deleted", "created_by", "updated_by",
            "gmt_create", "gmt_updated",
        ))
        self.assertIn("SELECT DISTINCT keyword_run_id", run_sql)
        self.assertIn("id <= 1001946", run_sql)
        self.assertIn("ORDER BY kr.id", run_sql)

        sequence_sql = queries.EVENT_SEQUENCE_SQL
        self.assertIn("operations_competitor_product_change_event", sequence_sql)
        self.assertIn("COALESCE(MAX(id), 0)", sequence_sql)
        self.assertIn("operations_competitor_analysis_id_sequence", sequence_sql)


class BoundaryAndSummaryQueryTest(unittest.TestCase):
    def test_event_contract_boundary_is_an_audit_not_an_inference(self):
        sql = queries.EVENT_CONTRACT_BOUNDARY_AUDIT_SQL
        self.assertIn("2026-07-28 20:00:50", sql)
        self.assertIn("2026-07-29 16:28:40", sql)
        self.assertIn("BEFORE_WRITER_CUTOVER_REVIEW", sql)
        self.assertIn("AMBIGUOUS_TRANSITION_WINDOW", sql)
        self.assertIn("AT_OR_AFTER_CONFIRMED_LIST_ONLY_RUNTIME", sql)
        self.assertIn("'requires_manual_contract_assignment', 1", sql)
        self.assertNotIn("'legacy'", sql.lower())
        self.assertNotIn("'list_v1'", sql.lower())
        self.assertIn("ORDER BY e.gmt_create, e.id", sql)

    def test_preflight_summary_is_bounded_and_counts_key_scopes(self):
        sql = queries.PREFLIGHT_COUNT_SUMMARY_SQL
        for metric in (
            "legacy_snapshot_candidates", "affected_business_keys",
            "affected_active_snapshot_chain_rows", "affected_change_event_rows",
            "legacy_rank_candidates", "legacy_distinct_keyword_runs",
            "affected_rank_scope_rows", "rank_cross_cutoff_same_run",
            "snapshot_within_cutoff_updated_after_cutover",
            "snapshot_outside_cutoff_requires_clock_review",
            "rank_within_cutoff_updated_after_cutover",
            "rank_outside_cutoff_requires_clock_review",
            "snapshot_event_contract_spans_list_v1_cutover",
        ):
            self.assertIn(metric, sql)
        self.assertIn("id <= 358244", sql)
        self.assertIn("id <= 1001946", sql)
        self.assertIn("FROM metrics ORDER BY metric", sql)


if __name__ == "__main__":
    unittest.main()
