import unittest

from scripts.competitor_business_date.schema_contract import _normalize_expression
from scripts.competitor_business_date.table_contracts import (
    SNAPSHOT,
    TABLE_CONTRACTS,
)


EXPECTED_COLUMNS = {
    "operations_competitor_product_snapshot": """
        id owner_user_id watch_product_id competitor_product_id subject_type
        site_code noon_product_code code_type fact_date active_fact_date captured_at
        source_task_id source_run_id detail_url title_en title_ar brand seller_name
        price_amount currency_code rating review_count main_image_url_raw
        main_image_url_normalized main_image_asset_key supermall_enabled
        sold_recently_text logistics_tags_json badges_json availability_status
        snapshot_hash raw_detail_json is_deleted created_by updated_by
        gmt_create gmt_updated
    """.split(),
    "operations_competitor_product_change_event": """
        id snapshot_id previous_snapshot_id owner_user_id watch_product_id
        competitor_product_id subject_type site_code noon_product_code fact_date
        field_key field_label change_type old_value_json new_value_json severity
        is_deleted created_by updated_by gmt_create gmt_updated
    """.split(),
    "operations_competitor_rank_fact": """
        id watch_product_id keyword_id keyword_run_id search_run_id fact_time
        fact_date tracked_product_type rank_channel noon_product_code rank_status
        rank_no scan_depth is_sponsored price_amount currency_code rating
        review_count source_result_id is_deleted created_by updated_by
        gmt_create gmt_updated
    """.split(),
    "operations_competitor_keyword_run": """
        id search_run_id keyword_id keyword_snapshot locale_snapshot
        provider_status result_count requested_result_limit source_url
        parser_version provider_http_status response_hash captured_at
        error_code error_message started_at finished_at is_deleted created_by
        updated_by gmt_create gmt_updated
    """.split(),
    "operations_competitor_analysis_id_sequence": """
        sequence_name next_id gmt_create gmt_updated
    """.split(),
}


class CompetitorBusinessDateTableContractTest(unittest.TestCase):
    def test_contracts_are_closed_complete_and_explicitly_typed(self):
        self.assertEqual(set(EXPECTED_COLUMNS), set(TABLE_CONTRACTS))
        for name, expected in EXPECTED_COLUMNS.items():
            contract = TABLE_CONTRACTS[name]
            self.assertEqual(expected, [column.name for column in contract.columns])
            self.assertTrue(all(column.sql_type for column in contract.columns))
        kinds = {
            column.kind
            for contract in TABLE_CONTRACTS.values()
            for column in contract.columns
        }
        self.assertEqual(
            {"text", "date", "datetime", "json", "decimal", "int", "bit"},
            kinds,
        )
        active = SNAPSHOT.column("active_fact_date")
        self.assertTrue(active.generated)
        self.assertNotIn(active.name, SNAPSHOT.row_column_names)

    def test_mysql_bit_zero_generation_expressions_share_one_canonical_form(self):
        expected = "casewhenis_deleted=0thenfact_dateelsenullend"
        for zero in ("b'0'", "0b0", "0x00", "_binary'\\0'"):
            with self.subTest(zero=zero):
                expression = (
                    "CASE WHEN (`is_deleted` = "
                    f"{zero}) THEN `fact_date` ELSE NULL END"
                )
                self.assertEqual(expected, _normalize_expression(expression))

    def test_mysql_escaped_check_literals_share_one_canonical_form(self):
        self.assertEqual(
            "fence_status=open",
            _normalize_expression("(`fence_status` = _utf8mb4\\'OPEN\\')"),
        )


if __name__ == "__main__":
    unittest.main()
