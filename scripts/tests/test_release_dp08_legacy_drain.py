import unittest

from scripts.tests.test_release_dp_runtime_cutover import build_script, cutover_fragment, run_fragment


class ReleaseDp08LegacyDrainTest(unittest.TestCase):
    def test_only_exact_scheduled_task_run_pairs_are_captured(self):
        accepted = run_fragment(r'''
dp08_legacy_pair_ids() {
  case "$1:$2" in
    TASK:QUEUED) printf '10,11' ;;
    RUN:QUEUED) printf '90,91' ;;
    *) printf '' ;;
  esac
}
dp08_legacy_active_counts() { printf '2\t2'; }
capture_dp08_legacy_cohort
[ "$DP08_LEGACY_QUEUED_TASK_IDS" = 10,11 ]
[ "$DP08_LEGACY_QUEUED_RUN_IDS" = 90,91 ]
''')
        mismatch = run_fragment(r'''
dp08_legacy_pair_ids() {
  case "$1:$2" in TASK:QUEUED) printf '10,11' ;; RUN:QUEUED) printf '90,91' ;; *) printf '' ;; esac
}
dp08_legacy_active_counts() { printf '3\t2'; }
capture_dp08_legacy_cohort
''')

        self.assertEqual(0, accepted.returncode, accepted.stderr)
        self.assertNotEqual(0, mismatch.returncode)

    def test_cutover_drains_after_old_jvm_stops_and_rollback_restores(self):
        script = build_script()
        execution = script.split("\nvalidate_cutover\n", 1)[1]
        stop = execution.index('stop_pid "$ACTIVE_PID"')
        no_jvm = execution.index("assert_no_backend_jvms", stop)
        finalize = execution.index("finalize_dp08_legacy_cutover", no_jvm)
        bootstrap = execution.index("bootstrap_dp_runtime_cutover", finalize)
        rollback = script[
            script.index("rollback_managed_release_data()"):
            script.index("dp_runtime_health_status()")
        ]

        self.assertLess(no_jvm, finalize)
        self.assertLess(finalize, bootstrap)
        self.assertIn("rollback_dp08_legacy_cohort", rollback)
        self.assertLess(
            rollback.index("rollback_dp08_legacy_cohort"),
            rollback.index("rollback_dp_runtime_legacy_cohort"),
        )

    def test_terminal_transition_is_identity_bounded_and_reversible(self):
        fragment = cutover_fragment()
        drain = fragment[fragment.index("DP08_LEGACY_QUEUED_TASK_IDS") :]

        for marker in (
            "SCHEDULED_RANK_MONITOR",
            "SCHEDULED_DETAIL_MONITOR",
            "r.task_id=t.id",
            "r.status=t.status",
            "DP_RUNTIME_SUPERSEDED",
            "operational_task|$DP08_LEGACY_QUEUED_TASK_IDS|QUEUED|",
            "operations_competitor_search_run|$DP08_LEGACY_RUNNING_RUN_IDS|RUNNING|error_message",
            "finished_at=NULL,error_code=NULL",
        ):
            self.assertIn(marker, drain)


if __name__ == "__main__":
    unittest.main()
