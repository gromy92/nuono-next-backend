import re
import unittest
from pathlib import Path


MIGRATION_PATH = (
    Path(__file__).parents[2]
    / "src/main/resources/db/init/240_operations_competitor_snapshot_active_uniqueness.sql"
)
SNAPSHOT_POSTCHECK_PATH = (
    Path(__file__).parents[2]
    / "src/main/resources/db/postcheck/240_operations_competitor_snapshot_active_uniqueness.sql"
)
FENCE_POSTCHECK_PATH = (
    Path(__file__).parents[2]
    / "src/main/resources/db/postcheck/241_operations_competitor_correction_writer_fence.sql"
)


def compact_sql(sql: str) -> str:
    without_comments = re.sub(r"--[^\n]*", " ", sql)
    return re.sub(r"\s+", " ", without_comments).strip()


class CompetitorSnapshotActiveUniquenessMigrationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = MIGRATION_PATH.read_text(encoding="utf-8")
        cls.compact = compact_sql(cls.sql)

    def test_bounds_row_and_metadata_lock_waits(self):
        self.assertIn("SET SESSION innodb_lock_wait_timeout = 5;", self.sql)
        self.assertIn("SET SESSION lock_wait_timeout = 5;", self.sql)

    def test_accepts_only_exact_legacy_or_exact_target_state(self):
        self.assertIn("@cps_state", self.sql)
        self.assertIn("'LEGACY'", self.sql)
        self.assertIn("'TARGET'", self.sql)
        self.assertIn("'UNSUPPORTED'", self.sql)
        self.assertIn("@cps_base_columns_exact = 1", self.sql)
        self.assertIn("@cps_preserved_indexes_exact = 1", self.sql)
        self.assertIn("@cps_index_count = 7", self.sql)
        self.assertIn("@cps_active_column_count = 0", self.sql)
        self.assertIn("@cps_active_column_exact = 1", self.sql)
        self.assertIn("@cps_old_exact = 1", self.sql)
        self.assertIn("@cps_target_exact = 1", self.sql)
        self.assertIn("@cps_old_equivalent_count = 1", self.sql)
        self.assertIn("@cps_target_equivalent_count = 1", self.sql)
        self.assertIn("migration_240_unsupported_schema_state", self.sql)

    def test_generated_expression_accepts_mysql_bit_literal_renderings(self):
        self.assertIn("(_binary|b)?''\\\\\\\\?0''", self.sql)
        self.assertIn("(0|0b0|0x00)", self.sql)
        self.assertEqual(2, self.sql.count("REGEXP_REPLACE(") // 2)

    def test_atomic_online_ddl_replaces_only_the_unique_guard(self):
        ddl_match = re.search(
            r"'ALTER TABLE `operations_competitor_product_snapshot` "
            r"ADD COLUMN `active_fact_date`.*?ALGORITHM=INPLACE, LOCK=NONE'",
            self.compact,
        )
        self.assertIsNotNone(ddl_match)
        ddl = ddl_match.group(0)

        self.assertIn(
            "DATE GENERATED ALWAYS AS "
            "(CASE WHEN `is_deleted` = b''0'' THEN `fact_date` ELSE NULL END) VIRTUAL",
            ddl,
        )
        self.assertIn("DROP INDEX `uk_ops_comp_snapshot_daily`", ddl)
        self.assertIn(
            "ADD UNIQUE INDEX `uk_ops_comp_snapshot_active_daily` "
            "(`watch_product_id`, `subject_type`, `noon_product_code`, `active_fact_date`)",
            ddl,
        )
        self.assertEqual(1, ddl.count("DROP INDEX"))

    def test_existing_lookup_indexes_are_prechecked_and_never_dropped(self):
        preserved = (
            "PRIMARY",
            "idx_ops_comp_snapshot_watch_date",
            "idx_ops_comp_snapshot_product_date",
            "idx_ops_comp_snapshot_code_date",
            "idx_ops_comp_snapshot_task",
            "idx_ops_comp_snapshot_run",
        )
        for index_name in preserved:
            with self.subTest(index=index_name):
                self.assertIn(index_name, self.sql)
                self.assertNotIn(f"DROP INDEX `{index_name}`", self.sql)
        self.assertEqual(2, self.sql.count("SET @cps_preserved_indexes_exact"))
        self.assertEqual(2, self.sql.count("SET @cps_index_count"))

    def test_postcheck_requires_exact_target_and_rejects_old_equivalent_guard(self):
        self.assertIn("@cps_postcheck_ok", self.sql)
        self.assertIn("@cps_active_column_exact = 1", self.sql)
        self.assertIn("@cps_index_count = 7", self.sql)
        self.assertIn("index_name = 'uk_ops_comp_snapshot_daily'", self.sql)
        self.assertIn("index_name = 'uk_ops_comp_snapshot_active_daily'", self.sql)
        self.assertIn("migration_240_postcheck_failed", self.sql)
        self.assertIn("migration_240_target_verified", self.sql)

    def test_release_postcheck_is_readonly_and_revalidates_exact_target(self):
        postcheck = SNAPSHOT_POSTCHECK_PATH.read_text(encoding="utf-8")
        compact = compact_sql(postcheck).lower()

        self.assertTrue(compact.startswith("select if("))
        self.assertNotRegex(
            compact,
            r"(?:^|;)\s*(?:set|insert|update|delete|alter|create|drop|truncate)\b",
        )
        for marker in (
            "uk_ops_comp_snapshot_active_daily",
            "active_fact_date",
            "virtual generated",
            "having count(*) > 1",
            "count(*) = 7",
        ):
            self.assertIn(marker, compact)

    def test_writer_fence_release_postcheck_is_readonly_and_exact(self):
        postcheck = FENCE_POSTCHECK_PATH.read_text(encoding="utf-8")
        compact = compact_sql(postcheck).lower()

        self.assertTrue(compact.startswith("select if("))
        self.assertNotRegex(
            compact,
            r"(?:^|;)\s*(?:set|insert|update|delete|alter|create|drop|truncate)\b",
        )
        for marker in (
            "competitor correction writer fence v1",
            "chk_ops_comp_cwf_name",
            "chk_ops_comp_cwf_status",
            "chk_ops_comp_cwf_active_audit",
            "historical_business_date_correction",
            "count(*) = 10",
        ):
            self.assertIn(marker, compact)


if __name__ == "__main__":
    unittest.main()
