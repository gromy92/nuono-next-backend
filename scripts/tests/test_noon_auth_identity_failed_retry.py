import json
import tempfile
import unittest
from unittest.mock import Mock
from pathlib import Path

from noon_auth_identity_failed_retry import _load, _load_snapshot, build_manifest
from noon_auth_identity_failed_retry_sql import build_apply_sql


class NoonAuthIdentityFailedRetryTest(unittest.TestCase):
    def setUp(self):
        self.provenance = {"manifestSha256": "a" * 64, "commit": "b" * 40,
                           "runId": 1, "artifactName": "backend", "operationBundleSha256": "c" * 64}
        self.snapshot = {
            "recovery": {"id": 772, "identityKey": "d" * 64, "status": "MANUAL_HOLD",
                         "failureCode": "IDENTITY_AUTH_FAILED", "versionNo": 3, "generationNo": 1,
                         "sendBudgetEpoch": 2, "sendAttemptCount": 1,
                         "firstSendAt": "2026-08-08 11:54:42", "secondSendAt": None,
                         "configFingerprint": "e" * 64, "leaseFree": True},
            "successor": {"id": 777, "identityKey": "d" * 64, "status": "WAITING_PREDECESSOR",
                          "predecessorRecoveryId": 772, "versionNo": 0, "generationNo": 0,
                          "sendAttemptCount": 0, "firstSendAt": None, "secondSendAt": None,
                          "leaseFree": True},
            "projects": [
                {"ownerUserId": 307, "projectCode": "PRJ108065", "authVersion": 64, "identityKey": "d" * 64},
                {"ownerUserId": 307, "projectCode": "PRJ69486", "authVersion": 53, "identityKey": "d" * 64},
            ],
            "tasks": [
                self.task(777, 772, 307, "PRJ108065", 918150, 910062, "ORDER"),
                self.task(779, 772, 307, "PRJ69486", 918158, 910068, "ORDER"),
                self.task(801, 777, 308, "PRJ100085", 918060, 910071, "SALES"),
            ],
            "ledgerCount": 1, "incidentGenerateCount": 0,
            "providerGenerateCount": 3498, "providerGenerateMaxId": 636077,
        }

    @staticmethod
    def task(item, recovery, owner, project, task, plan, domain):
        return {"itemId": item, "recoveryId": recovery, "ownerUserId": owner,
                "projectCode": project, "taskId": task, "authRecoveryId": recovery,
                "storeCode": "STR" + project[3:] + "-NAE", "siteCode": "AE",
                "domain": domain, "planId": plan}

    def test_preserves_the_one_remaining_send_and_cancels_only_frozen_zero_fact_tasks(self):
        manifest = build_manifest(self.snapshot, 772, 777, 60, "ci", self.provenance)
        sql = build_apply_sql(manifest, "ci")

        self.assertEqual(772, manifest["recovery"]["id"])
        self.assertEqual(777, manifest["successor"]["id"])
        self.assertIn("failure_code='IDENTITY_AUTH_FAILED'", sql)
        self.assertIn("generation_no=1", sql)
        self.assertIn("send_attempt_count=1", sql)
        self.assertIn("status='WAITING_COOLDOWN'", sql)
        self.assertNotIn("SET send_attempt_count=", sql)
        self.assertNotIn("SET generation_no=", sql)
        self.assertIn("status='CANCELLED'", sql)
        self.assertIn("paused=b'1'", sql)
        self.assertIn("incidentGenerateCount", str(manifest))

    def test_rejects_any_shape_that_could_create_an_extra_or_unfenced_send(self):
        for key, value in (("sendAttemptCount", 2), ("generationNo", 2), ("secondSendAt", "x")):
            with self.subTest(key=key):
                snapshot = dict(self.snapshot)
                snapshot["recovery"] = {**self.snapshot["recovery"], key: value}
                with self.assertRaises(ValueError):
                    build_manifest(snapshot, 772, 777, 60, "ci", self.provenance)
        snapshot = dict(self.snapshot)
        snapshot["successor"] = {**self.snapshot["successor"], "sendAttemptCount": 1}
        with self.assertRaises(ValueError):
            build_manifest(snapshot, 772, 777, 60, "ci", self.provenance)
        snapshot = dict(self.snapshot)
        snapshot["incidentGenerateCount"] = 1
        with self.assertRaises(ValueError):
            build_manifest(snapshot, 772, 777, 60, "ci", self.provenance)

    def test_snapshot_reader_accepts_mysql_wrapping_newlines_but_not_a_second_document(self):
        client = Mock()
        client.execute_readonly.return_value = '\n {"recovery": 772}\n'
        self.assertEqual({"recovery": 772}, _load_snapshot(client, 772, 777))
        client.execute_readonly.return_value = '{"recovery": 772}\n{"recovery": 777}'
        with self.assertRaises(ValueError):
            _load_snapshot(client, 772, 777)

    def test_manifest_loader_rejects_a_key_not_derived_from_its_frozen_payload(self):
        manifest = build_manifest(self.snapshot, 772, 777, 60, "ci", self.provenance)
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "manifest.json"
            path.write_text(json.dumps(manifest), encoding="utf-8")
            path.chmod(0o600)
            self.assertEqual(manifest["manifestSha256"], _load(path)["manifestSha256"])
            path.write_text(json.dumps({**manifest, "manifestKey": "other"}), encoding="utf-8")
            path.chmod(0o600)
            with self.assertRaises(ValueError):
                _load(path)


if __name__ == "__main__":
    unittest.main()
