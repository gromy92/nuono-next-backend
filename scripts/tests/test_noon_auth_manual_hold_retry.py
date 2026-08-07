import unittest

from noon_auth_manual_hold_retry import build_manifest
from noon_auth_manual_hold_retry_sql import build_apply_sql


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
                "projectCode": "PRJ245027", "sourceTaskId": None,
                "status": "PENDING", "expectedAuthVersion": 4,
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
        self.assertIn("sr.status='WAITING_PREDECESSOR'", sql)
        self.assertIn("sr.send_attempt_count=0", sql)
        self.assertIn("COMMIT", sql)
        self.assertNotIn("UPDATE noon_pull_task", sql)
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
        with self.assertRaisesRegex(ValueError, "source-less item"):
            build_manifest(self.snapshot, 741, 308, "PRJ245027", 60, "ci", self.provenance)


if __name__ == "__main__":
    unittest.main()
