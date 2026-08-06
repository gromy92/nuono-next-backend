import subprocess
import unittest

from scripts.tests.test_release_schema_cutover import load_module


class Migration205SuccessorContractTest(unittest.TestCase):
    def additive_script(self):
        return load_module().build_additive_schema_migration_script(
            staged_jar="/staged/backend.jar",
            expected_jar_sha256="a" * 64,
            expected_commit="b" * 40,
            expected_182_sha256="1" * 64,
            expected_189_sha256="2" * 64,
            expected_190_sha256="3" * 64,
            expected_204_sha256="4" * 64,
            expected_205_sha256="5" * 64,
            approved_managed_migrations=(
                "250_dp_pull_advertising_campaign_pagination.sql",
            ),
            app_dir="/app",
            release_name="successor-order-test",
        )

    def run_contract(self, body):
        script = self.additive_script()
        definitions = script[: script.index("validate_additive_migrations\n")]
        return subprocess.run(
            ["bash"],
            input=f"{definitions}\n{body}\n",
            text=True,
            capture_output=True,
            check=False,
        )

    def test_first_run_retires_through_238_and_continues_migration_250(self):
        result = self.run_contract(
            r'''
SUCCESSOR_STATE=ABSENT
migration_238_state() { printf '%s\n' "$SUCCESSOR_STATE"; }
migration_205_relation_count() { printf '1\n'; }
migration_205_exact_shape() { printf '1\n'; }
migration_205_row_count() { printf '0\n'; }
drop_migration_205_table() { emit UNEXPECTED_DROP yes; return 91; }
apply_migration() { emit UNEXPECTED_205_APPLY yes; return 92; }
postcheck_migration_205() { emit UNEXPECTED_205_POSTCHECK yes; return 93; }
repair_retired_migration_205
SUCCESSOR_STATE=APPLIED
emit FORWARD_SCHEMA_MIGRATIONS MIGRATION_250_CONTINUED
migration_205_relation_count() { printf '0\n'; }
apply_or_skip_migration_205
'''
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn(
            "MIGRATION_205_SUCCESSOR_REPAIR_STATE="
            "READY_EMPTY_EXACT_205_SUCCESSOR_238_PENDING",
            result.stdout,
        )
        self.assertIn("FORWARD_SCHEMA_MIGRATIONS=MIGRATION_250_CONTINUED", result.stdout)
        self.assertIn("MIGRATION_205_RESULT=SKIPPED_RETIRED_SUCCESSOR_238", result.stdout)
        self.assertNotIn("UNEXPECTED", result.stdout)

    def test_second_apply_is_empty_after_238(self):
        result = self.run_contract(
            r'''
migration_238_state() { printf 'APPLIED\n'; }
migration_205_relation_count() { printf '0\n'; }
apply_migration() { emit UNEXPECTED_205_APPLY yes; return 92; }
postcheck_migration_205() { emit UNEXPECTED_205_POSTCHECK yes; return 93; }
repair_retired_migration_205
emit FORWARD_SCHEMA_MIGRATIONS EMPTY_APPLY
apply_or_skip_migration_205
'''
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn(
            "MIGRATION_205_SUCCESSOR_REPAIR_STATE=SKIPPED_SUCCESSOR_238_READY",
            result.stdout,
        )
        self.assertIn("FORWARD_SCHEMA_MIGRATIONS=EMPTY_APPLY", result.stdout)
        self.assertIn("MIGRATION_205_RESULT=SKIPPED_RETIRED_SUCCESSOR_238", result.stdout)
        self.assertNotIn("UNEXPECTED", result.stdout)

    def test_applied_successor_repairs_only_exact_empty_obsolete_table(self):
        result = self.run_contract(
            r'''
DROPPED=0
migration_238_state() { printf 'APPLIED\n'; }
migration_205_relation_count() {
  if [ "$DROPPED" = 1 ]; then printf '0\n'; else printf '1\n'; fi
}
migration_205_exact_shape() { printf '1\n'; }
migration_205_row_count() { printf '0\n'; }
drop_migration_205_table() { DROPPED=1; emit OBSOLETE_205_DROP CALLED; }
repair_retired_migration_205
'''
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("OBSOLETE_205_DROP=CALLED", result.stdout)
        self.assertIn("MIGRATION_205_OBSOLETE_ROW_COUNT=0", result.stdout)
        self.assertIn(
            "MIGRATION_205_SUCCESSOR_REPAIR_STATE="
            "DROPPED_EMPTY_EXACT_205_SUCCESSOR_238",
            result.stdout,
        )

    def test_rejects_nonempty_table(self):
        result = self.run_contract(
            r'''
migration_238_state() { printf 'APPLIED\n'; }
migration_205_relation_count() { printf '1\n'; }
migration_205_exact_shape() { printf '1\n'; }
migration_205_row_count() { printf '2\n'; }
drop_migration_205_table() { emit UNEXPECTED_DROP yes; return 91; }
repair_retired_migration_205
'''
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("MIGRATION_205_SUCCESSOR_REPAIR_STATE=BLOCKED_NON_EMPTY", result.stdout)
        self.assertIn("MIGRATION_205_OBSOLETE_ROW_COUNT=2", result.stdout)
        self.assertNotIn("UNEXPECTED_DROP", result.stdout)

    def test_rejects_shape_drift(self):
        result = self.run_contract(
            r'''
migration_238_state() { printf 'APPLIED\n'; }
migration_205_relation_count() { printf '1\n'; }
migration_205_exact_shape() { printf '0\n'; }
migration_205_row_count() { emit UNEXPECTED_ROW_COUNT yes; return 90; }
drop_migration_205_table() { emit UNEXPECTED_DROP yes; return 91; }
repair_retired_migration_205
'''
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("MIGRATION_205_SUCCESSOR_REPAIR_STATE=BLOCKED_SHAPE_DRIFT", result.stdout)
        self.assertNotIn("UNEXPECTED", result.stdout)

    def test_rejects_inconsistent_238_history(self):
        result = self.run_contract(
            r'''
migration_238_state() { printf 'BLOCKED\n'; }
migration_205_relation_count() { printf '0\n'; }
repair_retired_migration_205
'''
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn(
            "MIGRATION_205_SUCCESSOR_REPAIR_STATE=BLOCKED_SUCCESSOR_238_HISTORY",
            result.stdout,
        )

    def test_contract_proves_history_and_exact_shape(self):
        script = self.additive_script()

        self.assertIn("JOIN nuono_schema_migration_attempt a", script)
        self.assertIn("a.attempt_no = h.attempt_no", script)
        self.assertIn("h.state = 'APPLIED'", script)
        self.assertIn("a.state = 'APPLIED'", script)
        self.assertIn("h.checksum_sha256 = a.checksum_sha256", script)
        self.assertIn("17:gmt_updated:datetime:-:NO:CURRENT_TIMESTAMP:", script)
        self.assertIn("idx_listing_reauth_recovery:1:3:project_code:-:A", script)
