"""Complete high-risk correction manifest used by the MySQL CI fixture."""
from __future__ import annotations

import hashlib
from pathlib import Path
from typing import Any

from competitor_business_date.bundle_identity import operation_bundle_identity
from competitor_business_date.manifest import ManifestWriter, copy_manifest_backup
from competitor_business_date.manifest_digest import canonical_json
from competitor_business_date.mysql_cli import MysqlCli
from competitor_business_date.preflight import (
    read_schema_fingerprint,
    server_identity,
)
from competitor_business_date.table_contracts import (
    CHANGE_EVENT,
    ID_SEQUENCE,
    KEYWORD_RUN,
    RANK_FACT,
    SNAPSHOT,
)

from ci.competitor_business_date_mysql_rows import (
    ACTOR_USER_ID,
    CORRECTION_TIME,
    SEQUENCE_NAME,
    correction_state,
    event_row,
    event_sequence_state,
    insert_row,
    rank_changes,
    snapshot_changes,
)

FENCE_GENERATION = 1
RUN_ID = "ci-mysql-fixture"
RELEASE_PROVENANCE = {
    "manifest_sha256": "c" * 64,
    "repository": "gromy92/nuono-next-backend",
    "commit": "d" * 40,
    "workflow": "Backend CI",
    "run_id": 1,
    "run_attempt": 1,
    "artifact_name": "competitor-mysql-fixture",
    "operation_bundle_sha256": operation_bundle_identity()["sha256"],
}


def seed_and_build_manifest(
    mysql: MysqlCli,
    output_root: Path,
) -> tuple[Path, Path, str]:
    snapshots, snapshot_posts = snapshot_changes()
    keyword_run, keyword_post, rank, rank_post = rank_changes()
    for contract, row in (
        (SNAPSHOT, snapshots[0]),
        (SNAPSHOT, snapshots[1]),
        (KEYWORD_RUN, keyword_run),
        (RANK_FACT, rank),
    ):
        insert_row(mysql, contract, row)
    sequence_source = event_sequence_state(mysql)
    sequence = {
        column.name: sequence_source[column.name]
        for column in ID_SEQUENCE.row_columns
    }
    sequence_post = {
        **sequence,
        "next_id": sequence["next_id"] + 1,
        "gmt_updated": CORRECTION_TIME,
    }
    event = event_row(sequence_post["next_id"], snapshot_posts[1])
    fingerprint, fingerprint_sha = read_schema_fingerprint(mysql)
    snapshot_group = _snapshot_group(snapshots[0])
    rank_group = str(keyword_run["id"])
    source_rows = [
        *(
            ("schema_fingerprint", "schema", f"{ordinal:06d}", row)
            for ordinal, row in enumerate(fingerprint, 1)
        ),
        ("event_sequence", SEQUENCE_NAME, SEQUENCE_NAME, sequence_source),
        *(
            ("snapshot", snapshot_group, str(row["id"]), row)
            for row in snapshots
        ),
        ("rank", rank_group, str(rank["id"]), rank),
        ("keyword_run", rank_group, str(keyword_run["id"]), keyword_run),
    ]
    summary = {
        "source_snapshot_rows": 2,
        "source_event_rows": 0,
        "source_rank_rows": 1,
        "source_keyword_run_rows": 1,
        "legacy_snapshot_rows": 1,
        "current_snapshot_rows": 1,
        "snapshot_chain_rows": 2,
        "legacy_rank_rows": 1,
        "current_rank_rows": 0,
        "keyword_run_rows": 1,
        f"{ID_SEQUENCE.name}:UPDATE": 1,
        f"{SNAPSHOT.name}:UPDATE": 2,
        f"{CHANGE_EVENT.name}:INSERT": 1,
        f"{KEYWORD_RUN.name}:UPDATE": 1,
        f"{RANK_FACT.name}:UPDATE": 1,
    }
    manifest = output_root / "manifest.sqlite"
    writer = ManifestWriter(
        manifest,
        {
            "run_id": RUN_ID,
            "algorithm_version": 1,
            "target_schema": mysql.schema,
            "actor_user_id": ACTOR_USER_ID,
            "correction_time": CORRECTION_TIME,
            "fence_generation": FENCE_GENERATION,
            "writer_cutover": "2026-07-28 20:00:50",
            "snapshot_legacy_max_id": 358244,
            "rank_legacy_max_id": 1001946,
            "list_v1_runtime_start": "2026-07-29 16:28:40",
            "source_fingerprint": {
                "sha256": fingerprint_sha,
                "schema_state": "TARGET",
            },
            "source_snapshot": {
                "sha256": _source_digest(source_rows),
                "counts": {
                    "schema_fingerprint": len(fingerprint),
                    "event_sequence": 1,
                    "snapshot": 2,
                    "event": 0,
                    "rank": 1,
                    "keyword_run": 1,
                },
            },
            "database_identity": server_identity(fingerprint),
            "non_scope_tables": ["operations_competitor_search_result"],
            "operation_bundle": operation_bundle_identity(),
            "release_artifact": RELEASE_PROVENANCE,
            "summary": summary,
        },
    )
    for kind, group, row_key, row in source_rows:
        writer.add_source_row(
            kind=kind, group_key=group, row_key=row_key, row=row
        )
    _change(
        writer, "event_sequence", SEQUENCE_NAME, ID_SEQUENCE,
        SEQUENCE_NAME, sequence, sequence_post,
    )
    for pre, post in zip(snapshots, snapshot_posts):
        _change(
            writer, "snapshot_chain", snapshot_group, SNAPSHOT,
            str(pre["id"]), pre, post,
        )
    writer.add_change(
        group_kind="snapshot_chain",
        group_key=snapshot_group,
        table_name=CHANGE_EVENT.name,
        primary_key=str(event["id"]),
        action="INSERT",
        pre=None,
        post=event,
    )
    _change(
        writer, "rank_run", rank_group, KEYWORD_RUN,
        str(keyword_run["id"]), keyword_run, keyword_post,
    )
    _change(
        writer, "rank_run", rank_group, RANK_FACT,
        str(rank["id"]), rank, rank_post,
    )
    writer.add_resolution(
        kind="snapshot_chain",
        group_key=snapshot_group,
        resolution={
            "candidate_snapshot_ids": [row["id"] for row in snapshots],
            "corrected_snapshots": [
                {
                    "id": row["id"],
                    "captured_at": row["captured_at"],
                    "fact_date": row["fact_date"],
                    "is_deleted": row["is_deleted"],
                    "role": "REDUNDANT" if row["is_deleted"] else "CANONICAL",
                }
                for row in snapshot_posts
            ],
            "source_event_ids": [],
            "inserted_event_ids": [event["id"]],
            "desired_active_events": [{
                "id": event["id"],
                "snapshot_id": event["snapshot_id"],
                "previous_snapshot_id": event["previous_snapshot_id"],
                "field_key": event["field_key"],
                "fact_date": event["fact_date"],
            }],
        },
    )
    writer.add_resolution(
        kind="rank_run",
        group_key=rank_group,
        resolution={
            "keyword_run_id": keyword_run["id"],
            "search_run_id": keyword_run["search_run_id"],
            "keyword_id": keyword_run["keyword_id"],
            "candidate_rank_ids": [rank["id"]],
            "parent_change_count": 1,
            "rank_change_ids": [rank["id"]],
        },
    )
    writer.add_resolution(
        kind="event_sequence",
        group_key=SEQUENCE_NAME,
        resolution={
            "sequence_name": SEQUENCE_NAME,
            "source_next_id": sequence["next_id"],
            "source_max_event_id": sequence_source["max_event_id"],
            "planned_next_id": sequence_post["next_id"],
        },
    )
    seal = writer.seal()
    backup = output_root / "manifest.backup.sqlite"
    copy_manifest_backup(manifest, backup, seal.file_sha256)
    return manifest, backup, seal.file_sha256


def _snapshot_group(row: dict[str, Any]) -> str:
    return f"{row['watch_product_id']}|{row['subject_type']}|{row['noon_product_code']}"


def _change(writer, kind, group, contract, key, pre, post) -> None:
    writer.add_change(
        group_kind=kind, group_key=group, table_name=contract.name,
        primary_key=key, action="UPDATE", pre=pre, post=post,
    )


def _source_digest(rows) -> str:
    digest = hashlib.sha256()
    for kind, group, row_key, row in sorted(rows):
        digest.update(
            canonical_json([kind, group, row_key, canonical_json(row)]).encode()
        )
        digest.update(b"\n")
    return digest.hexdigest()
