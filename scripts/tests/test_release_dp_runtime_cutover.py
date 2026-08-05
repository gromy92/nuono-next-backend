import importlib.util
import subprocess
import unittest
from pathlib import Path


SCRIPT_DIR = Path(__file__).parents[1]
MODULE_PATH = SCRIPT_DIR / "release_cutover_maintenance.py"


def load_module():
    spec = importlib.util.spec_from_file_location("release_cutover_maintenance", MODULE_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def build_script():
    return load_module().build_single_scheduler_cutover_script(
        staged_jar="/staged/backend.jar",
        expected_jar_sha256="a" * 64,
        expected_commit="c" * 40,
        expected_active_jar_sha256="b" * 64,
        expected_active_pid=4242,
        expected_nginx_upstream_sha256="d" * 64,
        expected_topology_cas_sha256="e" * 64,
        active_slot="blue",
        target_slot="green",
        active_port=18087,
        target_port=18088,
        maintenance_port=18089,
        nginx_upstream_file="/managed/upstream.inc",
        release_name="dp-runtime-cutover-test",
        external_health_url="https://www.nuoon.com/ai/actuator/health",
        app_dir="/app",
    )


def cutover_fragment():
    script = build_script()
    return script[
        script.index('DP_RUNTIME_MYSQL_CNF="$APP_DIR/.migration.cnf"'):
        script.index("dp_runtime_health_status()")
    ]


def run_fragment(body):
    script = "\n".join((
        "set -Eeuo pipefail",
        "APP_DIR=/app",
        "BACKUP_DIR=/backup",
        "STAGED_JAR=/staged/backend.jar",
        "EXPECTED_COMMIT=" + "c" * 40,
        "EXPECTED_JAR_SHA256=" + "a" * 64,
        cutover_fragment(),
        body,
    ))
    return subprocess.run(
        ["bash", "-c", script], text=True, capture_output=True, check=False
    )


class ReleaseDpRuntimeCutoverTest(unittest.TestCase):
    def test_preflight_precedes_probe_and_every_service_mutation(self):
        execution = build_script().split("\nvalidate_cutover\n", 1)[1]
        preflight = execution.index("prepare_dp_runtime_cutover")
        probe = execution.index("run_dp10_openapi_probe")
        maintenance = execution.index("start_maintenance_responder")
        stop = execution.index('stop_pid "$ACTIVE_PID"')

        self.assertLess(preflight, probe)
        self.assertLess(preflight, maintenance)
        self.assertLess(preflight, stop)

    def test_old_service_is_stopped_before_the_only_legacy_write(self):
        execution = build_script().split("\nvalidate_cutover\n", 1)[1]
        stop = execution.index('stop_pid "$ACTIVE_PID"')
        no_jvm = execution.index("assert_no_backend_jvms", stop)
        finalize = execution.index("finalize_dp_runtime_legacy_cutover", no_jvm)
        start = execution.index('start_runtime "$TARGET_SLOT_DIR" "$TARGET_PORT"')
        function = cutover_fragment()[
            cutover_fragment().index("finalize_dp_runtime_legacy_cutover()"):
        ]

        self.assertLess(stop, no_jvm)
        self.assertLess(no_jvm, finalize)
        self.assertLess(finalize, start)
        self.assertLess(function.index("assert_no_backend_jvms"), function.index("UPDATE noon_pull_task"))
        self.assertLess(function.index("require_legacy_cutover_ready"), function.index("UPDATE noon_pull_task"))
        self.assertLess(function.index("UPDATE noon_pull_task"), function.index("require_legacy_cutover_empty"))

    def test_only_strict_never_started_complete_snapshots_can_be_cancelled(self):
        function = cutover_fragment()[
            cutover_fragment().index("finalize_dp_runtime_legacy_cutover()"):
        ]
        required = (
            "UPDATE noon_pull_task",
            "status='QUEUED'",
            "trigger_mode='SCHEDULED_DAILY'",
            "pull_type='INTERFACE'",
            "target_identity LIKE 'product-list:%'",
            "target_identity LIKE 'official-warehouse-fbn-inventory:%'",
            "started_at IS NULL",
            "locked_by IS NULL",
            "source_batch_id IS NULL",
            "auth_recovery_id IS NULL",
            "checkpoint_cursor IS NULL",
            "next_resume_position IS NULL",
            "report_export_id IS NULL",
            "report_download_url IS NULL",
            "report_poll_attempts,0)=0",
            "failure_type IS NULL",
            "retry_action IS NULL",
            "diagnostic_summary IS NULL",
            "finished_at IS NULL",
            "failure_type='runtime_cutover_superseded'",
        )
        for marker in required:
            self.assertIn(marker, function)
        self.assertNotIn("UPDATE operational_task", function)
        self.assertNotIn("UPDATE procurement_ali1688_order_sync_task", function)
        self.assertNotIn("UPDATE sales_sync_task", function)
        self.assertNotIn("UPDATE noon_auth_identity_recovery_item", function)

    def test_every_legacy_writer_cohort_and_auth_wait_is_fail_closed(self):
        fragment = cutover_fragment()
        markers = (
            "status IN ('QUEUED','RUNNING','BLOCKED_AUTH')",
            "task_type='PRODUCT_PUBLIC_DETAIL_SYNC'",
            "FROM procurement_ali1688_order_sync_task",
            "FROM sales_sync_task",
            "status IN ('queued','running','waiting_authorization')",
            "FROM noon_auth_identity_recovery_item",
            "source_task_id IS NOT NULL",
            "'SALES_SYNC'",
            "'OFFICIAL_WAREHOUSE_ASN'",
        )
        for marker in markers:
            self.assertIn(marker, fragment)

    def test_ready_gate_allows_only_zero_or_supersedable_noon_rows(self):
        accepted = run_fragment("""
dp_runtime_legacy_counts() { printf '2\\t2\\t0\\t0\\t0\\t0\\n'; }
require_legacy_cutover_ready
[ "$DP_RUNTIME_LEGACY_SAFE_SNAPSHOT_COUNT" = 2 ]
""")
        started = run_fragment("""
dp_runtime_legacy_counts() { printf '2\\t1\\t0\\t0\\t0\\t0\\n'; }
require_legacy_cutover_ready
""")
        sales = run_fragment("""
dp_runtime_legacy_counts() { printf '0\\t0\\t0\\t0\\t1\\t0\\n'; }
require_legacy_cutover_ready
""")

        self.assertEqual(0, accepted.returncode, accepted.stderr)
        self.assertNotEqual(0, started.returncode)
        self.assertNotEqual(0, sales.returncode)

    def test_stopped_old_service_recheck_blocks_before_update(self):
        result = run_fragment("""
assert_no_backend_jvms() { :; }
require_legacy_cutover_ready() { return 19; }
dp_runtime_db_scalar() { echo unexpected-database-write >&2; return 88; }
finalize_dp_runtime_legacy_cutover
""")

        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("unexpected-database-write", result.stderr)

    def test_database_binding_is_rechecked_before_start_and_cutover(self):
        script = build_script()
        execution = script.split("\nvalidate_cutover\n", 1)[1]
        start = execution.index('start_runtime "$TARGET_SLOT_DIR" "$TARGET_PORT"')
        switch = execution.index('switch_nginx_to_port "$TARGET_PORT"')
        checks = [
            index for index in range(len(execution))
            if execution.startswith("verify_dp_runtime_database_binding", index)
        ]

        self.assertTrue(any(index < start for index in checks))
        self.assertTrue(any(start < index < switch for index in checks))
        for migration in (
            "243_dp_pull_runtime.sql",
            "244_dp_pull_report_bounded_apply.sql",
            "245_dp_pull_snapshot_bounded_apply.sql",
            "246_dp_pull_advertising_generation.sql",
            "247_dp_pull_schedule_core.sql",
            "248_dp_pull_dp08_member_retention.sql",
        ):
            self.assertIn(migration, script)
        self.assertNotIn("247_dp_pull_schedule_bounded.sql", script)
        self.assertIn("schema_binding.migration_count=6", script)
        self.assertIn("ORDER BY BINARY h.migration_key", script)
        self.assertIn("[ \"$DP_RUNTIME_CUTOVER_OPERATION_COUNT\" = 11 ]", script)

    def test_candidate_manifest_is_rechecked_after_drain_before_bootstrap(self):
        execution = build_script().split("\nvalidate_cutover\n", 1)[1]
        baseline = execution.index("run_dp_runtime_cutover_manifest")
        maintenance = execution.index("start_maintenance_responder")
        no_jvm = execution.index("assert_no_backend_jvms", maintenance)
        finalize = execution.index("finalize_dp_runtime_legacy_cutover", no_jvm)
        recheck = execution.index("recheck_dp_runtime_cutover_manifest", finalize)
        bootstrap = execution.index("bootstrap_dp_runtime_cutover", recheck)
        environment = execution.index("prepare_dp10_probe_runtime_environment", bootstrap)
        start = execution.index('start_runtime "$TARGET_SLOT_DIR" "$TARGET_PORT"')

        self.assertLess(baseline, maintenance)
        self.assertLess(no_jvm, finalize)
        self.assertLess(finalize, recheck)
        self.assertLess(recheck, bootstrap)
        self.assertLess(bootstrap, environment)
        self.assertLess(environment, start)

    def test_manifest_is_a_candidate_jar_one_shot_with_exact_baseline(self):
        script = build_script()
        self.assertIn("dp-runtime-cutover-manifest --env-file", script)
        self.assertIn('--candidate-jar "$STAGED_JAR"', script)
        self.assertIn('--baseline-manifest "$DP_RUNTIME_BASELINE_MANIFEST"', script)
        self.assertIn("SAFE_FALLBACK_PREVIOUS_BUSINESS_DAY", script)
        self.assertIn("candidate Jar DP runtime cutover marker mismatch", script)
        self.assertIn("DP cutover stopped-JVM cohort drift", script)

    def test_bootstrap_is_one_transaction_in_fk_order_and_fails_on_existing_tasks(self):
        fragment = cutover_fragment()
        writer = fragment[
            fragment.index("write_dp_runtime_bootstrap_sql()"):
            fragment.index("dp_runtime_bootstrap_counts()")
        ]
        self.assertIn('lines = ["SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;", "START TRANSACTION;"]', writer)
        self.assertLess(writer.index("dp_pull_scope_admission"), writer.index("dp_pull_scope_binding_epoch"))
        self.assertLess(writer.index("dp_pull_scope_binding_epoch"), writer.index("dp_pull_schedule_cutover"))
        self.assertLess(writer.index("dp_pull_schedule_cutover"), writer.index("dp_pull_schedule_anchor"))
        self.assertIn("UPDATE dp_pull_task SET id = NULL", writer)
        self.assertIn('lines.append("COMMIT;")', writer)

    def test_automatic_predecessor_restart_requires_exact_zero_work_data_rollback(self):
        script = build_script()
        rollback = script[script.index("rollback_cutover()") : script.index("validate_cutover()")]
        hook = script[script.index("rollback_managed_release_data()") : script.index("dp_runtime_health_status()")]
        self.assertIn("rollback_managed_release_data", rollback)
        self.assertIn("BLOCKED_MANAGED_DATA_REPAIR_FORWARD_REQUIRED", rollback)
        self.assertLess(rollback.index("rollback_managed_release_data"), rollback.index("restart_old_runtime"))
        self.assertIn('"$(dp_runtime_new_work_count)" = 0', hook)
        self.assertIn("verify_dp_runtime_database_binding", hook)
        self.assertIn("Managed DP runtime cutover $EXPECTED_COMMIT", hook)

    def test_rollback_fence_counts_every_244_through_248_runtime_table(self):
        script = build_script()
        hook = script[script.index("dp_runtime_new_work_count()") :]
        tables = (
            "dp_pull_report_artifact_chunk", "dp_pull_report_stage", "dp_pull_report_stage_row",
            "dp_pull_snapshot_fingerprint_count", "dp_pull_snapshot_verify_page", "dp_pull_snapshot_apply_progress",
            "dp_pull_snapshot_effective_item", "dp_pull_snapshot_current_head", "dp_pull_advertising_generation",
            "dp_pull_advertising_campaign_fact", "dp_pull_advertising_query_fact", "dp_pull_advertising_current_head",
            "dp_pull_schedule_rotation", "dp_pull_schedule_epoch_sequence", "dp_pull_schedule_manifest_seal",
            "dp_pull_schedule_source_epoch", "dp_pull_schedule_source_scope", "dp_pull_schedule_dp08_member_stage_head",
            "dp_pull_schedule_dp08_member_stage_item", "dp_pull_dp08_member_set", "dp_pull_dp08_member_set_item",
            "dp_pull_dp08_task_member_progress",
        )
        for table in tables:
            self.assertEqual(1, hook.count(f"(SELECT COUNT(*) FROM {table})"), table)

    def test_provenance_markers_are_inside_the_attested_target_environment(self):
        script = build_script()
        prepare = script[
            script.index("prepare_dp10_probe_runtime_environment()"):
            script.index("prepare_target_runtime_payloads()")
        ]
        for name in (
            "NUONO_DP_RUNTIME_RELEASE_EXPECTED_COMMIT",
            "NUONO_DP_RUNTIME_RELEASE_SCHEMA_BINDING_SHA256",
            "NUONO_DP_RUNTIME_RELEASE_CUTOVER_BINDING_SHA256",
        ):
            self.assertIn(name, prepare)
        self.assertLess(
            prepare.index("NUONO_DP_RUNTIME_RELEASE_CUTOVER_BINDING_SHA256"),
            prepare.index('TARGET_ENV_SHA256="$(secure_file_operation install'),
        )

    def test_release_summary_contains_stopped_service_drain_evidence(self):
        script = build_script()
        self.assertIn('emit DP_LEGACY_SUPERSEDED_COUNT', script)
        self.assertIn('emit DP_LEGACY_REMAINING_AFTER_STOP', script)
        self.assertIn('DP_RUNTIME_LEGACY_REMAINING_AFTER_STOP=0', script)

    def test_generated_release_script_is_valid_bash(self):
        result = subprocess.run(
            ["bash", "-n"], input=build_script(), text=True,
            capture_output=True, check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)


if __name__ == "__main__":
    unittest.main()
