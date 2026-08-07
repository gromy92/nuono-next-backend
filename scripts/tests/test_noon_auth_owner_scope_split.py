import unittest

from noon_auth_owner_scope_split import build_manifest
from noon_auth_owner_scope_split_sql import build_apply_sql, build_rollback_sql


class NoonAuthOwnerScopeSplitSqlTest(unittest.TestCase):
    def setUp(self):
        self.manifest = {
            "manifestKey": "owner307-predecessor741-source743-a1b2c3d4",
            "manifestSha256": "a" * 64,
            "preStateSha256": "b" * 64,
            "ownerUserId": 307,
            "identityKey": "c" * 64,
            "predecessor": {"id": 741, "versionNo": 10, "status": "MANUAL_HOLD"},
            "source": {
                "id": 743,
                "versionNo": 4,
                "status": "WAITING_PREDECESSOR",
                "generationNo": 0,
                "sendBudgetEpoch": 0,
                "sendAttemptCount": 0,
                "configFingerprint": "d" * 64,
                "remainingItemCount": 1,
                "remainingProjectCount": 1,
                "remainingSha256": "e" * 64,
            },
            "items": [
                {
                    "id": 9001,
                    "projectCode": "PRJ108065",
                    "storeCode": "STR108065-NSA",
                    "siteCode": "SA",
                    "sourceTaskId": None,
                    "sourceDomain": "STORE_BINDING",
                    "sourceCheckpoint": None,
                    "resumePolicy": "AUTO_RESUME",
                    "expectedAuthVersion": 3,
                    "status": "PENDING",
                    "itemSha256": "f" * 64,
                },
                {
                    "id": 9002,
                    "projectCode": "PRJ244978",
                    "storeCode": "STR244978-NSA",
                    "siteCode": "SA",
                    "sourceTaskId": 501,
                    "sourceDomain": "NOON_PULL",
                    "sourceCheckpoint": "AUTH",
                    "resumePolicy": "AUTO_RESUME",
                    "expectedAuthVersion": 8,
                    "status": "PENDING",
                    "itemSha256": "1" * 64,
                },
            ],
            "remainingItems": [
                {
                    "id": 9101, "ownerUserId": 308, "projectCode": "PRJ100085",
                    "storeCode": "STR100085-NSA", "siteCode": "SA", "sourceTaskId": None,
                    "sourceDomain": "STORE_BINDING", "sourceCheckpoint": None,
                    "resumePolicy": "AUTO_RESUME", "expectedAuthVersion": 2,
                    "status": "PENDING", "itemSha256": "2" * 64,
                },
            ],
        }

    def test_apply_is_one_transaction_with_exact_cas_and_no_source_budget_update(self):
        sql = build_apply_sql(self.manifest, "codex-owner307-recovery")

        self.assertIn("START TRANSACTION", sql)
        self.assertIn("FOR UPDATE", sql)
        self.assertIn("scope_owner_user_id", sql)
        self.assertIn("noon_auth_owner_scope_manifest", sql)
        self.assertIn("noon_auth_owner_scope_manifest_item", sql)
        self.assertIn("selected_for_scope", sql)
        self.assertIn("noon_auth_owner_scope_audit", sql)
        self.assertIn("recovery_id=@dp251_scoped_recovery_id", sql)
        self.assertIn("active_recovery_id=@dp251_scoped_recovery_id", sql)
        self.assertIn("owner_user_id=307", sql)
        self.assertIn("version_no=4", sql)
        self.assertIn("send_attempt_count=0", sql)
        self.assertIn("COMMIT", sql)
        self.assertNotIn("UPDATE noon_auth_identity_recovery SET send_attempt_count", sql)
        self.assertNotIn("UPDATE noon_auth_identity_recovery SET send_budget_epoch", sql)

    def test_rollback_only_accepts_unstarted_scoped_successor(self):
        sql = build_rollback_sql(self.manifest, "codex-owner307-recovery")

        self.assertIn("status='WAITING_PREDECESSOR'", sql)
        self.assertIn("send_attempt_count=0", sql)
        self.assertIn("lease_token IS NULL", sql)
        self.assertIn("status='ROLLED_BACK'", sql)
        self.assertIn("status='CANCELLED'", sql)
        self.assertIn("recovery_id=743", sql)
        self.assertIn("active_recovery_id=743", sql)

    def test_manifest_selects_every_requested_owner_item_and_freezes_non_scope(self):
        snapshot = {
            "predecessor": {"id": 741, "versionNo": 10, "status": "MANUAL_HOLD"},
            "source": {
                "id": 743, "predecessorRecoveryId": 741, "versionNo": 4,
                "status": "WAITING_PREDECESSOR", "generationNo": 0,
                "sendBudgetEpoch": 0, "sendAttemptCount": 0,
                "configFingerprint": "d" * 64,
            },
            "identityKey": "c" * 64,
            "items": [
                self._snapshot_item(9001, 307, "PRJ108065", 3),
                self._snapshot_item(9002, 307, "PRJ244978", 8),
                self._snapshot_item(9101, 308, "PRJ100085", 2),
                self._snapshot_item(9102, 308, "PRJ101128", 5),
            ],
            "projectStates": [
                {"ownerUserId": 307, "projectCode": "PRJ108065", "activeRecoveryId": 743, "authVersion": 3},
                {"ownerUserId": 307, "projectCode": "PRJ244978", "activeRecoveryId": 743, "authVersion": 8},
            ],
        }

        manifest = build_manifest(
            snapshot, 307, ("PRJ108065", "PRJ244978"), "codex-owner307-recovery"
        )

        self.assertEqual([9001, 9002], [item["id"] for item in manifest["items"]])
        self.assertEqual([9101, 9102], [item["id"] for item in manifest["remainingItems"]])
        self.assertEqual(2, manifest["source"]["remainingItemCount"])
        self.assertEqual(2, manifest["source"]["remainingProjectCount"])
        self.assertEqual(64, len(manifest["source"]["remainingSha256"]))
        self.assertEqual(64, len(manifest["manifestSha256"]))
        self.assertEqual(64, len(manifest["preStateSha256"]))

    def test_manifest_rejects_partial_owner_selection(self):
        snapshot = {
            "predecessor": {"id": 741, "versionNo": 10, "status": "MANUAL_HOLD"},
            "source": {"id": 743, "predecessorRecoveryId": 741, "versionNo": 4,
                       "status": "WAITING_PREDECESSOR", "generationNo": 0,
                       "sendBudgetEpoch": 0, "sendAttemptCount": 0,
                       "configFingerprint": "d" * 64},
            "identityKey": "c" * 64,
            "items": [self._snapshot_item(9001, 307, "PRJ108065", 3),
                      self._snapshot_item(9002, 307, "PRJ244978", 8)],
            "projectStates": [
                {"ownerUserId": 307, "projectCode": "PRJ108065", "activeRecoveryId": 743, "authVersion": 3},
                {"ownerUserId": 307, "projectCode": "PRJ244978", "activeRecoveryId": 743, "authVersion": 8},
            ],
        }

        with self.assertRaisesRegex(ValueError, "complete owner scope"):
            build_manifest(snapshot, 307, ("PRJ108065",), "codex-owner307-recovery")

    @staticmethod
    def _snapshot_item(item_id, owner, project, version):
        return {
            "id": item_id, "ownerUserId": owner, "projectCode": project,
            "storeCode": f"STR{project[3:]}-NSA", "siteCode": "SA",
            "sourceTaskId": None, "sourceDomain": "STORE_BINDING",
            "sourceCheckpoint": None, "resumePolicy": "AUTO_RESUME",
            "expectedAuthVersion": version, "status": "PENDING",
        }


if __name__ == "__main__":
    unittest.main()
