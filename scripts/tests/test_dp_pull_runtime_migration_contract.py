from __future__ import annotations

import re
import sys
import unittest
from pathlib import Path, PurePosixPath

SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from schema_migrations.catalog import load_catalog  # noqa: E402
from schema_migrations.sql_text import code_outside_literals_and_comments  # noqa: E402


class DpPullRuntimeMigrationContractTest(unittest.TestCase):
    SUCCESSOR_KEYS = {
        244: "244_dp_pull_report_bounded_apply.sql",
        245: "245_dp_pull_snapshot_bounded_apply.sql",
        246: "246_dp_pull_advertising_generation.sql",
        247: "247_dp_pull_schedule_core.sql",
        248: "248_dp_pull_dp08_member_retention.sql",
    }

    @classmethod
    def setUpClass(cls):
        cls.resources = SCRIPT_DIR.parent / "src/main/resources"
        cls.migrations = {
            migration.order: migration
            for migration in load_catalog(cls.resources)
        }
        cls.migration = cls.migrations[243]

    def test_catalog_registers_separate_exact_and_live_contracts(self):
        migration = self.migration

        self.assertEqual("243_dp_pull_runtime.sql", migration.key)
        self.assertEqual("AUTO_ADDITIVE", migration.kind)
        self.assertEqual(
            PurePosixPath("db/postcheck/243_dp_pull_runtime.sql"),
            migration.postcheck_path,
        )
        self.assertEqual(
            PurePosixPath("db/livecheck/243_dp_pull_runtime.sql"),
            migration.livecheck_path,
        )
        self.assertNotEqual(
            migration.postcheck_checksum,
            migration.livecheck_checksum,
        )

    def test_init_owns_exactly_the_twenty_one_runtime_tables(self):
        script = self.migration.script_sql
        tables = re.findall(
            r"CREATE TABLE IF NOT EXISTS (dp_pull_[a-z0-9_]+)",
            script,
        )

        self.assertEqual(21, len(tables))
        self.assertEqual(21, len(set(tables)))
        self.assertIn("dp_pull_runtime_leader", tables)
        self.assertIn("dp_pull_scope_admission", tables)
        self.assertIn("dp_pull_scope_binding_epoch", tables)
        self.assertIn("dp_pull_dp10_stage_cleanup", tables)
        self.assertNotIn("dp_pull_auth_wait", tables)
        self.assertEqual(113, len(re.findall(r"\bCONSTRAINT chk_", script)))
        self.assertEqual(16, len(re.findall(r"\bFOREIGN KEY\b", script)))
        for removed_index in (
            "idx_dp_snapshot_item_identity",
            "idx_dp10_stage_verify",
            "idx_dp_report_artifact_request",
            "idx_dp_report_locator_request",
        ):
            self.assertNotIn(removed_index, script)
        self.assertIn(
            "GREATEST(noon_pull_id_sequence.next_id, incoming.next_id)",
            script,
        )
        self.assertIn("CHARACTER SET ascii COLLATE ascii_bin", script)

    def test_exact_and_live_checks_are_read_only_and_fail_closed(self):
        exact = self.migration.postcheck_sql
        live = self.migration.livecheck_sql

        for sql in (exact, live):
            self.assertNotIn("/*!", sql)
            executable = code_outside_literals_and_comments(sql)
            self.assertTrue(executable.lstrip().startswith("WITH"))
            self.assertEqual(1, executable.count(";"))
            self.assertNotRegex(
                executable,
                re.compile(
                    r"\b(?:INSERT|UPDATE|DELETE|ALTER|CREATE|DROP|TRUNCATE|REPLACE)\b",
                    re.IGNORECASE,
                ),
            )
            self.assertIn("table_name='dp_pull_auth_wait')=0", sql)
            self.assertIn("dp10_fingerprint_actual", sql)
            self.assertIn("noon_pull_id_sequence", sql)
            self.assertIn("LOWER(c.extra) AS extra", sql)
            self.assertIn("'default_generated','')", sql)

        self.assertIn("(SELECT COUNT(*) FROM actual_column)=232", exact)
        self.assertIn("(SELECT COUNT(*) FROM actual_index)=50", exact)
        self.assertIn("(SELECT COUNT(*) FROM actual_check)=113", exact)
        self.assertIn("(SELECT COUNT(*) FROM actual_fk)=16", exact)
        self.assertNotIn("COUNT(*) FROM actual_check", live)
        self.assertNotIn("COUNT(*) FROM actual_fk", live)
        self.assertIn("a.is_nullable='YES'", live)
        self.assertIn("a.non_unique=1 AND a.safe_shape=1", live)
        self.assertIn("dp_pull_runtime_exact_postcheck", exact)
        self.assertIn("dp_pull_runtime_additive_livecheck", live)

    def test_dp10_cleanup_marker_is_exact_generation_and_fail_closed(self):
        init = self.migration.script_sql
        self.assertIn("PRIMARY KEY (task_id, generation_no)", init)
        self.assertIn("UNIQUE KEY uk_dp10_cleanup_task (task_id)", init)
        self.assertIn(
            "reason IN ('CURRENT_GENERATION', 'OLDER_GENERATION', 'FAILED_RETENTION')",
            init,
        )
        for sql in (self.migration.postcheck_sql, self.migration.livecheck_sql):
            self.assertIn("m.task_id=p.task_id AND m.generation_no=p.generation_no", sql)
            self.assertIn("m.task_id=a.task_id AND m.generation_no=a.generation_no", sql)
            self.assertIn("m.task_id=d.task_id AND m.generation_no=d.generation_no", sql)
            self.assertIn("m.task_id=i.task_id AND m.generation_no=i.generation_no", sql)
            self.assertIn("m.active_fence_epoch>m.fence_epoch", sql)
            self.assertIn("m.generation_no<>m.checkpoint_generation", sql)
            self.assertIn("m.generation_no>=m.checkpoint_generation", sql)
            self.assertIn("JSON_VALID(t.checkpoint)", sql)
            self.assertIn(
                "m.state NOT IN ('QUEUED','RUNNING','WAITING_BACKOFF','FAILED')",
                sql,
            )
            self.assertIn(
                "m.state='FAILED' AND (m.finished_at IS NULL",
                sql,
            )
            self.assertIn(
                "t.operation_code NOT IN ('DP04','DP06','DP07A','DP08A')",
                sql,
            )

    def test_snapshot_authority_and_scope_admission_are_fail_closed(self):
        init = self.migration.script_sql
        self.assertIn("CREATE TABLE IF NOT EXISTS dp_pull_scope_admission", init)
        self.assertIn("fk_dp_schedule_anchor_admission", init)
        self.assertIn("idx_dp_schedule_anchor_admission", init)
        self.assertIn("chk_dp_snapshot_stage_authority", init)
        self.assertIn("chk_dp_snapshot_apply_authority", init)
        self.assertIn("chk_dp_snapshot_apply_accounting", init)
        self.assertIn("authority_kind IS NOT NULL AND authority_kind IN", init)
        self.assertIn("authority_token_sha256 IS NOT NULL AND authority_token_sha256 REGEXP", init)
        self.assertIn("declared_collection_count IS NOT NULL AND declared_collection_count >= 0", init)
        self.assertNotIn("OR (authority_kind IN ('PAGED_GENERATION', 'COMPLETE_EXPORT')", init)
        self.assertIn("s.snapshot_as_of_utc IS NULL", self.migration.livecheck_sql)
        self.assertIn("EXISTS (SELECT 1 FROM dp_pull_snapshot_stage_page", self.migration.livecheck_sql)
        self.assertIn(
            "operation_code IN ('DP04', 'DP07A')",
            init,
        )
        for sql in (self.migration.postcheck_sql, self.migration.livecheck_sql):
            self.assertIn(
                "t.operation_code NOT IN ('DP04','DP06','DP07A','DP08A')",
                sql,
            )
            self.assertIn(
                "t.operation_code<>'DP08A' AND EXISTS "
                "(SELECT 1 FROM dp_pull_snapshot_stage_page",
                sql,
            )
            self.assertIn("s.authority_kind IS NULL", sql)
            self.assertIn("s.authority_kind IS NOT NULL AND s.authority_kind IN", sql)
            self.assertIn("s.authority_token_sha256 IS NOT NULL AND s.authority_token_sha256 REGEXP", sql)
            self.assertIn("s.declared_collection_count IS NOT NULL AND s.declared_collection_count>=0", sql)
            self.assertNotIn("OR (s.authority_kind IN ('PAGED_GENERATION','COMPLETE_EXPORT')", sql)
            self.assertIn("information_schema.check_constraints", sql)
            self.assertIn("p.source_item_count<>p.item_count+p.business_skipped_item_count", sql)
            self.assertIn("a.reconcile_after_utc<>s.first_eligible_at_utc", sql)

    def test_successor_migrations_are_contiguous_additive_and_fail_closed(self):
        required_markers = {
            244: "dp_pull_report_artifact_chunk",
            245: "dp_pull_snapshot_fingerprint_count",
            246: "dp_pull_advertising_generation",
            247: "dp_pull_schedule_manifest_seal",
            248: "dp_pull_dp08_member_set",
        }
        for order, key in self.SUCCESSOR_KEYS.items():
            migration = self.migrations[order]
            with self.subTest(migration=key):
                self.assertEqual(key, migration.key)
                self.assertEqual("AUTO_ADDITIVE", migration.kind)
                self.assertEqual(
                    PurePosixPath(f"db/postcheck/{key}"),
                    migration.postcheck_path,
                )
                self.assertEqual(
                    PurePosixPath(f"db/livecheck/{key}"),
                    migration.livecheck_path,
                )
                self.assertNotEqual(
                    migration.postcheck_checksum,
                    migration.livecheck_checksum,
                )
                self.assertIn(required_markers[order], migration.script_sql)
                for sql in (migration.postcheck_sql, migration.livecheck_sql):
                    self.assertNotIn("/*!", sql)
                    executable = code_outside_literals_and_comments(sql)
                    self.assertTrue(executable.lstrip().startswith("WITH"))
                    self.assertEqual(1, executable.count(";"))
                    self.assertNotRegex(
                        executable,
                        re.compile(
                            r"\b(?:INSERT|UPDATE|DELETE|ALTER|CREATE|DROP|"
                            r"TRUNCATE)\b|\bREPLACE\s+INTO\b",
                            re.IGNORECASE,
                        ),
                    )
                for path in (
                    migration.script_file,
                    migration.postcheck_file,
                    migration.livecheck_file,
                ):
                    self.assertLessEqual(
                        len(path.read_bytes().splitlines()),
                        300,
                    )

    def test_report_artifact_header_is_successor_compatible(self):
        init = self.migration.script_sql
        successor = self.migrations[244].script_sql
        self.assertIn("nuono_dp244_shape_guard", successor)
        self.assertNotIn("SIGNAL SQLSTATE", successor)
        self.assertIn("CONCAT(CHAR(92),CHAR(39))", successor)

        self.assertIn("content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL", init)
        self.assertIn("download_state VARCHAR(20) NOT NULL DEFAULT 'LEGACY_COMPLETE'", init)
        self.assertIn("persisted_chunk_count INT NOT NULL DEFAULT 0", init)
        self.assertIn("idx_dp_report_artifact_download_state", init)
        self.assertIn("chk_dp_report_artifact_storage_shape", init)
        self.assertIn(
            "content_length BETWEEN 0 AND 2251799812636672",
            init,
        )
        self.assertIn(
            "persisted_chunk_count BETWEEN 0 AND 2147483647",
            init,
        )
        self.assertNotIn("content_length BETWEEN 0 AND 536870912", init)
        self.assertIn(
            "download_state = 'LEGACY_COMPLETE' AND content_sha256 IS NOT NULL "
            "AND content_length > 0 AND content_bytes IS NOT NULL",
            init,
        )
        for sql in (self.migration.livecheck_sql, self.migration.postcheck_sql):
            self.assertIn("a.download_state='LEGACY_COMPLETE'", sql)
            self.assertIn("a.content_bytes IS NOT NULL", sql)
            self.assertIn("a.content_length=OCTET_LENGTH(a.content_bytes)", sql)

    def test_dp08_scope_binding_is_temporal_immutable_and_task_copied(self):
        init = self.migration.script_sql
        self.assertIn("CREATE TABLE IF NOT EXISTS dp_pull_scope_binding_epoch", init)
        self.assertIn("UNIQUE KEY uk_dp_scope_binding_open (open_scope_slot)", init)
        self.assertIn("chk_dp_scope_binding_open", init)
        self.assertIn("chk_dp_pull_task_scope_binding", init)
        self.assertIn("fk_dp_pull_task_scope_binding", init)
        for sql in (self.migration.postcheck_sql, self.migration.livecheck_sql):
            self.assertIn("LOWER(SHA2(b.payload,256))<>b.payload_sha256", sql)
            self.assertIn("a.binding_id<b.binding_id", sql)
            self.assertIn("b.payload_type<>t.scope_payload_type", sql)
            self.assertIn("t.schedule_slot>=b.effective_until_utc", sql)

    def test_authorization_wait_can_be_untimed_only_after_recovery_escalation(self):
        init = self.migration.script_sql

        self.assertIn(
            "state IN ('WAITING_REMOTE', 'WAITING_BACKOFF') AND retry_not_before IS NOT NULL",
            init,
        )
        self.assertIn("OR state = 'WAITING_AUTH' OR", init)
        self.assertIn(
            "state NOT IN ('WAITING_REMOTE', 'WAITING_BACKOFF', 'WAITING_AUTH') "
            "AND retry_not_before IS NULL",
            init,
        )

    def test_all_three_sql_files_fit_the_source_size_gate(self):
        for relative in (
            "db/init/243_dp_pull_runtime.sql",
            "db/postcheck/243_dp_pull_runtime.sql",
            "db/livecheck/243_dp_pull_runtime.sql",
        ):
            with self.subTest(path=relative):
                path = self.resources / relative
                self.assertLessEqual(len(path.read_bytes().splitlines()), 300)


if __name__ == "__main__":
    unittest.main()
