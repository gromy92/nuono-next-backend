from __future__ import annotations

import copy
import unittest

from noon_auth_owner_scope_release import build_manifest
from noon_auth_owner_scope_release_sql import build_release_sql


def snapshot() -> dict:
    identity = "c" * 64
    return {
        "activeManifestCount": 1,
        "activeRecoveryCount": 0,
        "releaseAuditCount": 0,
        "manifestSelectedItemCount": 2,
        "manifestRemainingItemCount": 1,
        "manifestItemDriftCount": 0,
        "sourceSendLedgerCount": 0,
        "manifest": {
            "id": 1, "ownerUserId": 307, "identityKey": identity,
            "predecessorRecoveryId": 741, "sourceRecoveryId": 743,
            "scopedRecoveryId": 767, "manifestSha256": "a" * 64,
            "itemCount": 2, "projectCount": 2,
            "sourceRemainingItemCount": 1, "sourceRemainingProjectCount": 1,
            "status": "ACTIVE", "versionNo": 0,
        },
        "predecessor": {
            "id": 741, "identityKey": identity, "status": "COMPLETED",
            "versionNo": 11, "leaseFree": True,
        },
        "source": {
            "id": 743, "predecessorRecoveryId": 741, "identityKey": identity,
            "status": "WAITING_PREDECESSOR", "versionNo": 4,
            "scopeOwnerUserId": None, "generationNo": 0, "sendBudgetEpoch": 0,
            "sendAttemptCount": 0, "leaseFree": True,
        },
        "scoped": {
            "id": 767, "predecessorRecoveryId": 741, "identityKey": identity,
            "status": "COMPLETED", "versionNo": 7, "scopeOwnerUserId": 307,
            "sendAttemptCount": 1, "leaseFree": True,
        },
        "sourceItems": [{
            "id": 3, "ownerUserId": 308, "projectCode": "PRJ3",
            "storeCode": "STR3", "siteCode": "SA", "sourceTaskId": 13,
            "sourceDomain": "OFFICIAL_WAREHOUSE_ASN", "sourceCheckpoint": None,
            "resumePolicy": "AUTO_RESUME", "expectedAuthVersion": 5,
            "status": "PENDING",
        }],
        "scopedItems": [{
            "id": 1, "ownerUserId": 307, "projectCode": "PRJ1",
            "storeCode": "STR1", "siteCode": "SA", "sourceTaskId": 11,
            "sourceDomain": "SALES", "sourceCheckpoint": "PERSISTED_TASK_STATE",
            "resumePolicy": "AUTO_RESUME", "expectedAuthVersion": 3,
            "status": "RECOVERED",
        }, {
            "id": 2, "ownerUserId": 307, "projectCode": "PRJ2",
            "storeCode": "STR2", "siteCode": "SA", "sourceTaskId": 12,
            "sourceDomain": "FINANCE_TRANSACTION", "sourceCheckpoint": "PERSISTED_TASK_STATE",
            "resumePolicy": "AUTO_RESUME", "expectedAuthVersion": 8,
            "status": "STALE",
        }],
        "sourceProjectStates": [{
            "ownerUserId": 308, "projectCode": "PRJ3", "status": "REAUTH_REQUIRED",
            "activeRecoveryId": 743, "authVersion": 5,
        }],
        "scopedProjectStates": [{
            "ownerUserId": 307, "projectCode": "PRJ1", "status": "HEALTHY",
            "activeRecoveryId": None, "authVersion": 4,
        }, {
            "ownerUserId": 307, "projectCode": "PRJ2", "status": "HEALTHY",
            "activeRecoveryId": None, "authVersion": 9,
        }],
    }


PROVENANCE = {
    "manifestSha256": "b" * 64, "commit": "d" * 40, "runId": 1,
    "artifactName": "ci", "operationBundleSha256": "e" * 64,
}


class NoonAuthOwnerScopeReleaseTest(unittest.TestCase):
    def test_builds_exact_audited_release_without_advancing_source(self):
        manifest = build_manifest(snapshot(), "ci-release", PROVENANCE)
        sql = build_release_sql(manifest, "ci-apply")
        self.assertEqual(743, manifest["snapshot"]["source"]["id"])
        self.assertIn("status='RELEASED'", sql)
        self.assertIn("'SCOPE_RELEASED'", sql)
        self.assertIn("NOON_AUTH_SCOPE_RELEASE_CAS_FAILED", sql)
        self.assertIn(
            "SELECT state.owner_user_id,state.project_code FROM "
            "noon_project_auth_state state WHERE",
            sql,
        )
        self.assertNotIn("UPDATE noon_auth_identity_recovery SET status", sql)

    def test_rejects_identity_slot_or_send_drift(self):
        active = snapshot()
        active["activeRecoveryCount"] = 1
        with self.assertRaisesRegex(ValueError, "identity slots"):
            build_manifest(active, "ci", PROVENANCE)
        sent = snapshot()
        sent["source"]["sendAttemptCount"] = 1
        with self.assertRaisesRegex(ValueError, "send-free"):
            build_manifest(sent, "ci", PROVENANCE)

    def test_rejects_incomplete_scoped_drain_or_changed_source_item(self):
        incomplete = snapshot()
        incomplete["scopedItems"][0]["status"] = "PENDING"
        with self.assertRaisesRegex(ValueError, "did not drain"):
            build_manifest(incomplete, "ci", PROVENANCE)
        changed = copy.deepcopy(snapshot())
        changed["sourceItems"][0]["ownerUserId"] = 307
        with self.assertRaisesRegex(ValueError, "non-scope remainder"):
            build_manifest(changed, "ci", PROVENANCE)


if __name__ == "__main__":
    unittest.main()
