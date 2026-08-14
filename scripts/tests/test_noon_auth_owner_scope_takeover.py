import copy
import unittest

from noon_auth_owner_scope_takeover import build_manifest, load_snapshot
from noon_auth_owner_scope_takeover_sql import build_apply_sql


class NoonAuthOwnerScopeTakeoverTest(unittest.TestCase):
    def setUp(self):
        self.provenance = {"manifestSha256": "a" * 64, "commit": "b" * 40, "runId": 1,
                           "artifactName": "backend", "operationBundleSha256": "c" * 64}
        self.snapshot = {"identityKey": "d" * 64, "activeManifestCount": 0, "activeRecoveryCount": 0,
            "predecessor": {"id": 877, "versionNo": 16, "status": "COMPLETED", "failureCode": None,
                            "generationNo": 1, "sendBudgetEpoch": 3, "sendAttemptCount": 1,
                            "firstSendAt": "2026-08-14 01:43:08", "secondSendAt": None, "leaseFree": True},
            "source": {"id": 882, "predecessorRecoveryId": 877, "versionNo": 11,
                       "status": "WAITING_PREDECESSOR", "generationNo": 0, "sendBudgetEpoch": 0,
                       "sendAttemptCount": 0, "firstSendAt": None, "secondSendAt": None, "leaseFree": True},
            "items": [self.item(881, "PRJ108065", 918051, "SALES", 7),
                      self.item(897, "PRJ244978", None, "STORE_BINDING", 70, "NONE"),
                      self.item(898, "PRJ244978", 918155, "ORDER", 70),
                      self.item(896, "PRJ245027", 918156, "SALES", 156),
                      self.item(909, "PRJ69486", 918163, "FINANCE_TRANSACTION", 10),
                      self.item(910, "PRJ100085", None, "STORE_BINDING", 6, "NONE", 308)],
            "projectStates": [self.state("PRJ108065", "HEALTHY", None, 72),
                self.state("PRJ244978", "REAUTH_REQUIRED", 882, 70),
                self.state("PRJ245027", "REAUTH_REQUIRED", 882, 156),
                self.state("PRJ69486", "HEALTHY", None, 61), self.state("PRJ100085", "REAUTH_REQUIRED", 882, 6, 308)],
            "tasks": [self.task(918051, 120041, "SALES"), self.task(918155, 910066, "ORDER"),
                self.task(918156, 120048, "SALES"), self.task(918163, 910037, "FINANCE_TRANSACTION")],
            "providerGenerateCount": 500, "providerGenerateMaxId": 600}

    @staticmethod
    def item(item_id, project, task_id, domain, version, policy="AUTO_RESUME", owner=307):
        return {"id": item_id, "ownerUserId": owner, "projectCode": project, "storeCode": "STR" + project[3:] + "-NSA", "siteCode": "SA", "sourceTaskId": task_id, "sourceDomain": domain, "sourceCheckpoint": "PROJECT_BINDING" if task_id is None else "PERSISTED_TASK_STATE", "resumePolicy": policy, "expectedAuthVersion": version, "status": "PENDING"}

    @staticmethod
    def state(project, status, active, version, owner=307):
        return {"ownerUserId": owner, "projectCode": project, "status": status, "activeRecoveryId": active, "authVersion": version}

    @staticmethod
    def task(task_id, plan_id, domain):
        return {"id": task_id, "planId": plan_id, "ownerUserId": 307, "status": "BLOCKED_AUTH", "authRecoveryId": 882, "domain": domain, "pullType": "REPORT", "triggerMode": "SCHEDULED_DAILY", "retryAction": "WAIT_FOR_AUTH", "checkpointCursor": None, "nextResumePosition": None, "lastSafeResponse": None, "processedItemCount": None, "requestCount": None, "finishedAt": None, "isDeleted": "base64:type16:AA==", "planEnabled": "base64:type16:AQ==", "planPaused": "base64:type16:AA=="}

    def manifest(self):
        return build_manifest(self.snapshot, 307, ("PRJ244978", "PRJ245027"), "ci-owner307", self.provenance)

    def test_terminal_drain_keeps_non_owner_and_uses_login_only_items(self):
        manifest = self.manifest()
        sql = build_apply_sql(manifest, "ci-owner307")
        self.assertEqual([896, 897, 898], [item["id"] for item in manifest["items"]])
        self.assertEqual([881, 909], [item["id"] for item in manifest["settledItems"]])
        self.assertEqual(["PRJ244978", "PRJ245027"], [item["projectCode"] for item in manifest["bindingItems"]])
        self.assertIn("status='SKIPPED'", sql)
        self.assertIn("status='RECOVERED'", sql)
        self.assertIn("'STORE_BINDING','PROJECT_BINDING','NONE'", sql)
        self.assertIn("auth_version=auth_version+1", sql)
        self.assertNotIn("paused=1", sql)
        self.assertNotIn("UPDATE noon_auth_identity_recovery_item SET recovery_id=", sql)
        self.assertNotIn("SIGNAL SQLSTATE", sql)

    def test_rejects_active_recovery_or_unhealthy_settled_project(self):
        snapshot = copy.deepcopy(self.snapshot)
        snapshot["activeRecoveryCount"] = 1
        with self.assertRaisesRegex(ValueError, "dormant"):
            build_manifest(snapshot, 307, ("PRJ244978", "PRJ245027"), "ci", self.provenance)
        snapshot = copy.deepcopy(self.snapshot)
        snapshot["projectStates"][0]["status"] = "REAUTH_REQUIRED"
        with self.assertRaisesRegex(ValueError, "settled"):
            build_manifest(snapshot, 307, ("PRJ244978", "PRJ245027"), "ci", self.provenance)

    def test_snapshot_reader_accepts_mysql_newline_but_not_second_document(self):
        class Client:
            def execute_readonly(self, sql): return '\n {"id": 877}\n'
        self.assertEqual({"id": 877}, load_snapshot(Client(), 877, 882, 307))
        class InvalidClient:
            def execute_readonly(self, sql): return '{"id": 877}\n{"id": 882}'
        with self.assertRaisesRegex(ValueError, "exactly one"):
            load_snapshot(InvalidClient(), 877, 882, 307)


if __name__ == "__main__":
    unittest.main()
