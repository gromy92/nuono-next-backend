"""Real-MySQL assertion for completed owner-scope release and promotion."""
from __future__ import annotations

from noon_auth_owner_scope_release import (
    build_manifest as build_scope_release_manifest,
    load_snapshot as load_scope_release_snapshot,
)
from noon_auth_owner_scope_release_sql import build_release_sql


def verify_completed_scope_release(test_case, database, scoped_id: int) -> None:
    database.client.execute(
        "UPDATE noon_auth_identity_recovery_item SET status='RECOVERED',"
        "recovered_at=UTC_TIMESTAMP(3),gmt_updated=UTC_TIMESTAMP(3) "
        f"WHERE recovery_id={int(scoped_id)} AND status='PENDING';"
    )
    manifest_id = int(database.client.execute_readonly(
        "SELECT id FROM noon_auth_owner_scope_manifest "
        f"WHERE scoped_recovery_id={int(scoped_id)};"
    ))
    scope_release = build_scope_release_manifest(
        load_scope_release_snapshot(database.client, manifest_id),
        "ci-scope-release",
        {"manifestSha256": "a" * 64, "commit": "b" * 40, "runId": 1,
         "artifactName": "ci", "operationBundleSha256": "e" * 64},
    )
    database.client.execute(build_release_sql(scope_release, "ci-scope-release"))
    test_case.assertEqual(
        "RELEASED:WAITING_PREDECESSOR:1",
        database.client.execute_readonly(
            "SELECT CONCAT(manifest.status,':',source.status,':',"
            "(SELECT COUNT(*) FROM noon_auth_owner_scope_audit audit "
            "WHERE audit.manifest_id=manifest.id AND audit.action='SCOPE_RELEASED')) "
            "FROM noon_auth_owner_scope_manifest manifest "
            "JOIN noon_auth_identity_recovery source ON source.id=manifest.source_recovery_id "
            f"WHERE manifest.scoped_recovery_id={int(scoped_id)};"
        ),
    )
    database.client.execute(
        "UPDATE noon_auth_identity_recovery successor "
        "JOIN noon_auth_identity_recovery predecessor "
        "ON predecessor.id=successor.predecessor_recovery_id "
        "LEFT JOIN noon_auth_identity_recovery active ON active.identity_key=successor.identity_key "
        "AND active.active_identity_slot IS NOT NULL "
        "LEFT JOIN noon_auth_owner_scope_manifest owner_scope "
        "ON owner_scope.predecessor_recovery_id=predecessor.id AND owner_scope.status='ACTIVE' "
        "SET successor.status='COALESCING',successor.version_no=successor.version_no+1 "
        "WHERE successor.status='WAITING_PREDECESSOR' "
        "AND predecessor.status IN ('COMPLETED','FAILED_FINAL','CANCELLED') "
        "AND (owner_scope.id IS NULL OR owner_scope.scoped_recovery_id=successor.id) "
        "AND active.id IS NULL;"
    )
    test_case.assertEqual(
        "RELEASED:COALESCING:0:0:0",
        database.client.execute_readonly(
            "SELECT CONCAT(manifest.status,':',source.status,':',source.generation_no,':',"
            "source.send_budget_epoch,':',source.send_attempt_count) "
            "FROM noon_auth_owner_scope_manifest manifest "
            "JOIN noon_auth_identity_recovery source ON source.id=manifest.source_recovery_id "
            f"WHERE manifest.scoped_recovery_id={int(scoped_id)};"
        ),
    )


__all__ = ["verify_completed_scope_release"]
