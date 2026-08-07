import unittest

from noon_auth_manual_hold_retry import build_manifest
from noon_auth_manual_hold_retry_sql import build_apply_sql, build_finalize_sql


class NoonAuthManualHoldRetryTest(unittest.TestCase):
    def setUp(self):
        self.snapshot = {
            "recovery": {
                "id": 741, "identityKey": "c" * 64, "status": "MANUAL_HOLD",
                "failureCode": "SEND_RESULT_UNKNOWN", "versionNo": 10,
                "generationNo": 2, "sendBudgetEpoch": 0, "sendAttemptCount": 2,
                "firstSendAt": "2026-08-06 12:03:24.000",
                "secondSendAt": "2026-08-06 12:08:32.000",
                "configFingerprint": "d" * 64, "leaseFree": True,
            },
            "item": {
                "id": 9000, "recoveryId": 741, "ownerUserId": 307,
                "projectCode": "PRJ245027", "storeCode": "STR245027-NAE",
                "siteCode": "AE", "sourceTaskId": 918054,
                "sourceDomain": "SALES", "sourceCheckpoint": "PERSISTED_TASK_STATE",
                "resumePolicy": "AUTO_RESUME", "status": "PENDING",
                "expectedAuthVersion": 4,
            },
            "projectState": {
                "ownerUserId": 307, "projectCode": "PRJ245027",
                "status": "MANUAL_HOLD", "activeRecoveryId": 741,
                "authVersion": 4, "identityKey": "c" * 64,
                "bindingFingerprint": None, "configFingerprint": "d" * 64,
            },
            "ownerScope": {
                "id": 1, "status": "ACTIVE", "ownerUserId": 307,
                "predecessorRecoveryId": 741, "sourceRecoveryId": 743,
                "scopedRecoveryId": 767,
                "scopedRecoveryStatus": "WAITING_PREDECESSOR",
                "scopedSendAttemptCount": 0,
            },
            "sourceTaskItemCount": 1,
            "taskItems": [self._task_item(
                9000, 741, 918054, "PRJ245027", "STR245027-NAE", "AE", "SALES", 741
            )],
            "ledgerCount": 2,
            "ledgerFirstAt": "2026-08-06 12:03:24.000",
            "ledgerLastAt": "2026-08-06 12:08:32.000",
            "incidentGenerateCount": 0,
            "providerGenerateCount": 5,
            "providerGenerateMaxId": 631292,
            "providerGenerateLastAt": "2026-08-06 08:51:56.000",
        }
        self.provenance = {
            "manifestSha256": "a" * 64, "commit": "b" * 40,
            "runId": 123, "artifactName": "backend-b", "operationBundleSha256": "e" * 64,
        }

    def test_manifest_and_sql_rebase_only_exact_predecessor_budget_epoch(self):
        manifest = build_manifest(
            self.snapshot, 741, 307, "PRJ245027", 60, "ci", self.provenance
        )
        sql = build_apply_sql(manifest, "ci")

        self.assertEqual(self.provenance, manifest["releaseProvenance"])
        self.assertIn("SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE", sql)
        self.assertIn("status='WAITING_COOLDOWN'", sql)
        self.assertIn("send_budget_epoch=send_budget_epoch+1", sql)
        self.assertIn("generation_no=0,send_attempt_count=0", sql)
        self.assertIn("status='REAUTH_REQUIRED'", sql)
        self.assertIn("PREDECESSOR_RETRY_REBASE", sql)
        self.assertIn("status='CANCELLED'", sql)
        self.assertIn("paused=b'1'", sql)
        self.assertIn("status='BLOCKED_AUTH'", sql)
        self.assertIn("sr.status='WAITING_PREDECESSOR'", sql)
        self.assertIn("sr.send_attempt_count=0", sql)
        self.assertIn("COMMIT", sql)
        self.assertIn("UPDATE noon_pull_task", sql)
        self.assertNotIn("official_warehouse_asn", sql)

    def test_manifest_rejects_real_or_unproven_sends_and_active_successor(self):
        for key, value in (
            ("incidentGenerateCount", 1),
            ("ledgerCount", 1),
        ):
            with self.subTest(key=key):
                snapshot = {**self.snapshot, key: value}
                with self.assertRaises(ValueError):
                    build_manifest(snapshot, 741, 307, "PRJ245027", 60, "ci", self.provenance)
        snapshot = dict(self.snapshot)
        snapshot["ownerScope"] = {**self.snapshot["ownerScope"],
                                  "scopedRecoveryStatus": "COALESCING"}
        with self.assertRaisesRegex(ValueError, "successor fence"):
            build_manifest(snapshot, 741, 307, "PRJ245027", 60, "ci", self.provenance)

    def test_manifest_rejects_third_send_shape_and_wrong_owner(self):
        snapshot = dict(self.snapshot)
        snapshot["recovery"] = {**self.snapshot["recovery"], "sendAttemptCount": 3}
        with self.assertRaisesRegex(ValueError, "exact exhausted"):
            build_manifest(snapshot, 741, 307, "PRJ245027", 60, "ci", self.provenance)
        with self.assertRaisesRegex(ValueError, "task-owned item"):
            build_manifest(self.snapshot, 741, 308, "PRJ245027", 60, "ci", self.provenance)

    def test_manifest_freezes_every_safe_predecessor_and_scoped_task(self):
        tasks = list(self.snapshot["taskItems"])
        tasks.extend([
            self._task_item(9001, 767, 918086, "PRJ108065", "STR108065-NAE", "AE",
                            "FINANCE_TRANSACTION", 743),
            self._task_item(9002, 767, 918088, "PRJ244978", "STR244978-NAE", "AE",
                            "FINANCE_TRANSACTION", 743),
            self._task_item(9003, 767, 918090, "PRJ69486", "STR69486-NSA", "SA",
                            "FINANCE_TRANSACTION", 743),
        ])
        snapshot = {**self.snapshot, "taskItems": tasks, "sourceTaskItemCount": 4}

        manifest = build_manifest(snapshot, 741, 307, "PRJ245027", 60, "ci", self.provenance)
        sql = build_apply_sql(manifest, "ci")

        self.assertEqual([918054, 918086, 918088, 918090], manifest["cancelledTaskIds"])
        self.assertEqual([919054, 919086, 919088, 919090], manifest["pausedPlanIds"])
        self.assertIn("ROW_COUNT()=4", sql)
        self.assertIn("918090", sql)

    def test_finalize_only_unpauses_after_both_recoveries_and_projects_complete(self):
        manifest = build_manifest(
            self.snapshot, 741, 307, "PRJ245027", 60, "ci", self.provenance
        )
        sql = build_finalize_sql(manifest, "ci")

        self.assertIn("status='COMPLETED'", sql)
        self.assertIn("status='HEALTHY'", sql)
        self.assertIn("active_recovery_id IS NULL", sql)
        self.assertIn("status='CANCELLED'", sql)
        self.assertIn("paused=b'0'", sql)
        self.assertIn("SOURCE_PLANS_UNPAUSED", sql)
        self.assertIn("status='WAITING_PREDECESSOR'", sql)

    def test_manifest_rejects_task_with_progress_or_disabled_plan(self):
        for key, value in (("requestCount", 1), ("planEnabled", False),
                           ("status", "RUNNING"), ("authRecoveryId", 999)):
            with self.subTest(key=key):
                task = {**self.snapshot["taskItems"][0], key: value}
                snapshot = {**self.snapshot, "taskItems": [task]}
                with self.assertRaisesRegex(ValueError, "safe scheduled task"):
                    build_manifest(snapshot, 741, 307, "PRJ245027", 60, "ci", self.provenance)

    @staticmethod
    def _task_item(item_id, recovery_id, task_id, project, store, site, domain,
                   task_recovery_id):
        return {
            "itemId": item_id, "recoveryId": recovery_id, "ownerUserId": 307,
            "projectCode": project, "storeCode": store, "siteCode": site,
            "sourceTaskId": task_id, "taskId": task_id, "sourceDomain": domain,
            "sourceCheckpoint": "PERSISTED_TASK_STATE", "resumePolicy": "AUTO_RESUME",
            "itemStatus": "PENDING", "taskOwnerUserId": 307,
            "taskStoreCode": store, "taskSiteCode": site, "dataDomain": domain,
            "status": "BLOCKED_AUTH", "authRecoveryId": task_recovery_id,
            "triggerMode": "SCHEDULED_DAILY", "pullType": "REPORT",
            "targetIdentity": f"{domain.lower()}-target", "retryAction": "WAIT_FOR_AUTH",
            "checkpointCursor": None, "nextResumePosition": None,
            "lastSafeResponseSummary": None, "processedItemCount": 0,
            "requestCount": 0, "finishedAt": None, "isDeleted": False,
            "planId": task_id + 1000, "planEnabled": True, "planPaused": False,
        }


if __name__ == "__main__":
    unittest.main()
