import copy
import unittest

from noon_auth_owner_scope_takeover import build_manifest, load_snapshot
from noon_auth_owner_scope_takeover_sql import build_apply_sql


class NoonAuthOwnerScopeTakeoverTest(unittest.TestCase):
    def setUp(self):
        self.provenance = {"manifestSha256": "a" * 64, "commit": "b" * 40,
                           "runId": 1, "artifactName": "backend", "operationBundleSha256": "c" * 64}
        self.snapshot = {
            "identityKey": "d" * 64, "activeManifestCount": 0,
            "predecessor": {"id": 877, "versionNo": 4, "status": "MANUAL_HOLD",
                            "failureCode": "IDENTITY_AUTH_FAILED", "generationNo": 0,
                            "sendBudgetEpoch": 0, "sendAttemptCount": 0, "firstSendAt": None,
                            "secondSendAt": None, "leaseFree": True},
            "source": {"id": 882, "predecessorRecoveryId": 877, "versionNo": 3,
                       "status": "WAITING_PREDECESSOR", "generationNo": 0,
                       "sendBudgetEpoch": 0, "sendAttemptCount": 0, "firstSendAt": None,
                       "secondSendAt": None, "leaseFree": True},
            "items": [
                self.item(881, 307, "PRJ108065", 918051, "SALES", 7),
                self.item(897, 307, "PRJ244978", None, "STORE_BINDING", 8, "NONE"),
                self.item(898, 307, "PRJ244978", 918155, "ORDER", 8),
                self.item(896, 307, "PRJ245027", 918156, "SALES", 9),
                self.item(909, 307, "PRJ69486", 918163, "FINANCE_TRANSACTION", 10),
                self.item(910, 308, "PRJ100085", None, "STORE_BINDING", 6, "NONE"),
            ],
            "projectStates": [
                self.state(307, "PRJ108065", 7), self.state(307, "PRJ244978", 8),
                self.state(307, "PRJ245027", 9), self.state(307, "PRJ69486", 10),
                self.state(308, "PRJ100085", 6),
            ],
            "tasks": [
                self.task(918051, 120041, "SALES"), self.task(918155, 910066, "ORDER"),
                self.task(918156, 120048, "SALES"), self.task(918163, 910037, "FINANCE_TRANSACTION"),
            ],
            "providerGenerateCount": 500, "providerGenerateMaxId": 600,
        }

    @staticmethod
    def item(item_id, owner, project, task_id, domain, version, policy="AUTO_RESUME"):
        return {"id": item_id, "ownerUserId": owner, "projectCode": project,
                "storeCode": "STR" + project[3:] + "-NSA", "siteCode": "SA",
                "sourceTaskId": task_id, "sourceDomain": domain,
                "sourceCheckpoint": "PROJECT_BINDING" if task_id is None else "PERSISTED_TASK_STATE",
                "resumePolicy": policy, "expectedAuthVersion": version, "status": "PENDING"}

    @staticmethod
    def state(owner, project, version):
        return {"ownerUserId": owner, "projectCode": project, "status": "REAUTH_REQUIRED",
                "activeRecoveryId": 882, "authVersion": version}

    @staticmethod
    def task(task_id, plan_id, domain):
        return {"id": task_id, "planId": plan_id, "ownerUserId": 307,
                "status": "BLOCKED_AUTH", "authRecoveryId": 882, "domain": domain,
                "pullType": "REPORT", "triggerMode": "SCHEDULED_DAILY", "retryAction": "WAIT_FOR_AUTH",
                "checkpointCursor": None, "nextResumePosition": None, "lastSafeResponse": None,
                "processedItemCount": None, "requestCount": None, "finishedAt": None,
                "isDeleted": "base64:type16:AA==", "planEnabled": "base64:type16:AQ==",
                "planPaused": "base64:type16:AA=="}

    def manifest(self):
        return build_manifest(self.snapshot, 307,
                              ("PRJ108065", "PRJ244978", "PRJ245027", "PRJ69486"),
                              "ci-owner307", self.provenance)

    def test_takeover_freezes_only_owner_scope_and_cancels_zero_progress_tasks(self):
        manifest = self.manifest()
        sql = build_apply_sql(manifest, "ci-owner307")

        self.assertEqual([881, 896, 897, 898, 909], [item["id"] for item in manifest["items"]])
        self.assertEqual([910], [item["id"] for item in manifest["remainingItems"]])
        self.assertIn("SUPERSEDED_BY_OWNER_SCOPE_TAKEOVER", sql)
        self.assertIn("'COALESCING',NULL,0,0,0", sql)
        self.assertIn("status='CANCELLED'", sql)
        self.assertIn("paused=1", sql)
        self.assertIn("recovery_id=@owner_takeover_recovery_id", sql)
        self.assertNotIn("UPDATE noon_auth_identity_recovery SET send_attempt_count", sql)
        self.assertNotIn("UPDATE noon_auth_identity_recovery SET send_budget_epoch", sql)
        self.assertNotIn("noon_auth_owner_scope_manifest", sql)

    def test_rejects_any_task_with_request_or_progress(self):
        snapshot = copy.deepcopy(self.snapshot)
        snapshot["tasks"][0]["requestCount"] = 1
        with self.assertRaisesRegex(ValueError, "zero-progress"):
            build_manifest(snapshot, 307,
                           ("PRJ108065", "PRJ244978", "PRJ245027", "PRJ69486"),
                           "ci-owner307", self.provenance)

    def test_rejects_partial_owner_scope_or_active_owner_manifest(self):
        with self.assertRaisesRegex(ValueError, "complete pending owner scope"):
            build_manifest(self.snapshot, 307, ("PRJ108065",), "ci-owner307", self.provenance)
        snapshot = copy.deepcopy(self.snapshot)
        snapshot["activeManifestCount"] = 1
        with self.assertRaisesRegex(ValueError, "mixed successor"):
            build_manifest(snapshot, 307,
                           ("PRJ108065", "PRJ244978", "PRJ245027", "PRJ69486"),
                           "ci-owner307", self.provenance)

    def test_snapshot_reader_accepts_mysql_newline_but_not_second_document(self):
        class Client:
            def execute_readonly(self, sql):
                return '\n {"id": 877}\n'
        self.assertEqual({"id": 877}, load_snapshot(Client(), 877, 882, 307))
        class InvalidClient:
            def execute_readonly(self, sql):
                return '{"id": 877}\n{"id": 882}'
        with self.assertRaisesRegex(ValueError, "exactly one"):
            load_snapshot(InvalidClient(), 877, 882, 307)


if __name__ == "__main__":
    unittest.main()
