import unittest

from scripts.tests.test_release_dp_runtime_cutover import cutover_fragment, run_fragment


class ReleaseDpRuntimeLegacyDrainTest(unittest.TestCase):
    def test_only_zero_fact_noon_and_linked_auth_waits_can_be_superseded(self):
        function = cutover_fragment()[
            cutover_fragment().index("finalize_dp_runtime_legacy_cutover()"):
        ]
        required = (
            "UPDATE noon_pull_task",
            "status IN ('QUEUED','RUNNING','BLOCKED_AUTH')",
            "trigger_mode='SCHEDULED_DAILY'",
            "data_domain IN ('PRODUCT','SALES','ORDER','FINANCE_TRANSACTION'",
            "status='BLOCKED_AUTH' AND retry_action='WAIT_FOR_AUTH'",
            "status='QUEUED' AND started_at IS NULL",
            "status='RUNNING' AND started_at IS NOT NULL",
            "checkpoint_cursor IS NULL",
            "next_resume_position IS NULL",
            "last_safe_response_summary IS NULL",
            "COALESCE(processed_item_count,0)=0",
            "COALESCE(request_count,0)=0",
            "finished_at IS NULL",
            "status='CANCELLED'",
            "UPDATE procurement_ali1688_order_sync_task",
            "status='running'",
            "COALESCE(processed_count,0)=0",
            "COALESCE(imported_count,0)=0",
            "COALESCE(failed_count,0)=0",
            "COALESCE(progress_percent,0)=0",
            "failure_code IS NULL",
            "failure_message IS NULL",
            "status='cancelled'",
            "UPDATE noon_auth_identity_recovery_item item",
            "task.auth_recovery_id=item.recovery_id",
            "item.status='STALE'",
            "item.failure_code='DP_RUNTIME_SUPERSEDED'",
        )
        for marker in required:
            self.assertIn(marker, function)
        self.assertNotIn("UPDATE operational_task", function)
        self.assertNotIn("UPDATE sales_sync_task", function)
        self.assertNotIn("COALESCE(report_total_rows,0)=0", function)

    def test_ready_gate_allows_only_fully_supersedable_cohorts(self):
        accepted = run_fragment("""
dp_runtime_legacy_counts() { printf '14\\t14\\t0\\t2\\t2\\t0\\t6\\n'; }
require_legacy_cutover_ready
[ "$DP_RUNTIME_LEGACY_SAFE_SNAPSHOT_COUNT" = 14 ]
[ "$DP_RUNTIME_LEGACY_SAFE_DP10_COUNT" = 2 ]
[ "$DP_RUNTIME_LEGACY_SAFE_AUTH_COUNT" = 6 ]
""")
        started = run_fragment("""
dp_runtime_legacy_counts() { printf '14\\t13\\t0\\t2\\t2\\t0\\t0\\n'; }
require_legacy_cutover_ready
""")
        dp10 = run_fragment("""
dp_runtime_legacy_counts() { printf '14\\t14\\t0\\t2\\t1\\t0\\t0\\n'; }
require_legacy_cutover_ready
""")
        sales = run_fragment("""
dp_runtime_legacy_counts() { printf '0\\t0\\t0\\t0\\t0\\t1\\t0\\n'; }
require_legacy_cutover_ready
""")

        self.assertEqual(0, accepted.returncode, accepted.stderr)
        self.assertNotEqual(0, started.returncode)
        self.assertNotEqual(0, dp10.returncode)
        self.assertNotEqual(0, sales.returncode)

    def test_cohort_capture_requires_every_auth_wait_to_bind_to_a_frozen_noon_task(self):
        accepted = run_fragment("""
DP_RUNTIME_LEGACY_SAFE_SNAPSHOT_COUNT=2
DP_RUNTIME_LEGACY_SAFE_DP10_COUNT=0
DP_RUNTIME_LEGACY_SAFE_AUTH_COUNT=2
dp_runtime_safe_noon_ids() { printf '10,11'; }
dp_runtime_safe_dp10_ids() { printf ''; }
dp_runtime_safe_auth_ids() {
  if [ "$1" = PENDING ]; then printf '90'; else printf '91'; fi
}
capture_dp_runtime_legacy_cohort
""")
        orphan = run_fragment("""
DP_RUNTIME_LEGACY_SAFE_SNAPSHOT_COUNT=2
DP_RUNTIME_LEGACY_SAFE_DP10_COUNT=0
DP_RUNTIME_LEGACY_SAFE_AUTH_COUNT=3
dp_runtime_safe_noon_ids() { printf '10,11'; }
dp_runtime_safe_dp10_ids() { printf ''; }
dp_runtime_safe_auth_ids() {
  if [ "$1" = PENDING ]; then printf '90'; else printf '91'; fi
}
capture_dp_runtime_legacy_cohort
""")

        self.assertEqual(0, accepted.returncode, accepted.stderr)
        self.assertNotEqual(0, orphan.returncode)

    def test_rollback_restores_original_pending_and_validating_auth_states(self):
        function = cutover_fragment()[
            cutover_fragment().index("rollback_dp_runtime_legacy_cohort()"):
        ]

        self.assertIn("status='PENDING'", function)
        self.assertIn("status='VALIDATING'", function)
        self.assertIn("failure_code='DP_RUNTIME_SUPERSEDED'", function)
        self.assertIn("failure_code=NULL", function)

    def test_terminal_retryable_dp10_history_is_not_an_active_writer(self):
        fragment = cutover_fragment()
        dp10_count = fragment[
            fragment.index("FROM procurement_ali1688_order_sync_task") - 200:
            fragment.index("FROM sales_sync_task")
        ]

        self.assertIn("status='running'", dp10_count)
        self.assertNotIn("status IN ('failed','partial_success')", dp10_count)

    def test_stopped_old_service_recheck_blocks_before_update(self):
        result = run_fragment("""
assert_no_backend_jvms() { :; }
require_legacy_cutover_ready() { return 19; }
dp_runtime_db_scalar() { echo unexpected-database-write >&2; return 88; }
finalize_dp_runtime_legacy_cutover
""")

        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("unexpected-database-write", result.stderr)


if __name__ == "__main__":
    unittest.main()
