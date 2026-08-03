"""Stream production source rows into a sealed correction manifest."""
from __future__ import annotations

import os
import tempfile
import uuid
from datetime import datetime
from pathlib import Path
from typing import Callable

from .consistent_read import ConsistentDatasetReader
from .bundle_identity import operation_bundle_identity
from .event_rebuild import EventIdAllocator
from .manifest import ManifestSeal, ManifestWriter
from .mysql_cli import MysqlCli
from .overrides import ClockOverrides
from .plan_build import (
    copy_source_evidence,
    validate_override_coverage,
    write_plan,
)
from .plan_source import (
    source_queries,
    stage_business_source,
    stage_event_sequence,
    stage_schema_fingerprint,
    validate_work_dir,
)
from .plan_validation import validate_frozen_plan
from .planner import CorrectionPlanner
from .preflight import (
    require_server_uuid,
    schema_fingerprint_digest,
    validate_target_schema,
)
from .row_utils import as_int
from .secure_files import fsync_directory
from .source_stage import SourceStage


class PlanServiceError(RuntimeError):
    pass


def freeze_manifest(
    mysql: MysqlCli,
    output: Path,
    *,
    actor_user_id: int,
    fence_generation: int,
    run_id: str,
    correction_time: datetime,
    fence_check: Callable[[], None],
    expected_server_uuid: str,
    work_dir: Path,
    release_provenance: dict[str, object],
    clock_overrides: ClockOverrides | None = None,
) -> tuple[ManifestSeal, dict[str, int]]:
    bundle_before = operation_bundle_identity()
    planner = CorrectionPlanner(
        actor_user_id=actor_user_id,
        correction_time=correction_time,
        snapshot_clock_overrides=(
            clock_overrides.snapshot if clock_overrides else None
        ),
        rank_clock_overrides=clock_overrides.rank if clock_overrides else None,
        event_contract_overrides=(
            clock_overrides.event_contract if clock_overrides else None
        ),
    )
    final_path = Path(output)
    if final_path.is_symlink() or final_path.exists():
        raise PlanServiceError("manifest output already exists or is a symlink")
    final_path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    temporary_path = final_path.with_name(
        f".{final_path.name}.{uuid.uuid4().hex}.partial"
    )
    try:
        with tempfile.TemporaryDirectory(
            prefix="nuono-competitor-date-plan-",
            dir=validate_work_dir(work_dir),
        ) as directory:
            os.chmod(directory, 0o700)
            with SourceStage(Path(directory) / "source.sqlite") as stage:
                fence_check()
                reader = ConsistentDatasetReader(
                    mysql,
                    source_queries(),
                    timeout_seconds=1800,
                )
                fingerprint = list(reader.read("schema_fingerprint"))
                state = validate_target_schema(
                    fingerprint,
                    expected_schema=mysql.schema,
                )
                if state != "TARGET":
                    raise PlanServiceError(
                        "migration 240 exact TARGET state is required, "
                        f"found {state}"
                    )
                database_identity = require_server_uuid(
                    fingerprint,
                    expected_server_uuid,
                )
                sequence_rows = list(reader.read("event_sequence"))
                if len(sequence_rows) != 1:
                    raise PlanServiceError("event sequence row is missing or duplicated")
                sequence = dict(sequence_rows[0])
                max_event_id = as_int(
                    sequence["max_event_id"],
                    "max_event_id",
                )
                event_ids = EventIdAllocator(
                    {
                        key: sequence[key]
                        for key in (
                            "sequence_name",
                            "next_id",
                            "gmt_create",
                            "gmt_updated",
                        )
                    }
                )
                event_ids.require_not_behind(max_event_id)
                source_counts = {
                    "schema_fingerprint": stage_schema_fingerprint(
                        stage, fingerprint
                    ),
                    "event_sequence": stage_event_sequence(stage, sequence_rows),
                    **stage_business_source(reader, stage),
                }
                reader.finish()
                fence_check()
                validate_override_coverage(stage, clock_overrides)
                fingerprint_sha = schema_fingerprint_digest(fingerprint)
                source_snapshot = {
                    "sha256": stage.content_digest(),
                    "counts": source_counts,
                }
                metadata = planner.metadata(
                    run_id=run_id,
                    fence_generation=fence_generation,
                    source_fingerprint={
                        "sha256": fingerprint_sha,
                        "schema_state": state,
                    },
                )
                metadata["source_snapshot"] = source_snapshot
                metadata["database_identity"] = database_identity
                if operation_bundle_identity() != bundle_before:
                    raise PlanServiceError("operation bundle changed while planning")
                metadata["operation_bundle"] = bundle_before
                metadata["release_artifact"] = dict(release_provenance)
                if clock_overrides:
                    metadata["clock_overrides"] = clock_overrides.manifest_value()
                writer = ManifestWriter(temporary_path, metadata)
                try:
                    copy_source_evidence(stage, writer)
                    summary = write_plan(
                        stage,
                        writer,
                        planner,
                        event_ids,
                        source_counts,
                    )
                    writer.set_metadata("summary", summary)
                    seal = writer.seal()
                    validate_frozen_plan(temporary_path, seal.file_sha256)
                except BaseException:
                    writer.abort()
                    raise
        os.link(temporary_path, final_path, follow_symlinks=False)
        fsync_directory(final_path.parent)
        temporary_path.unlink()
        fsync_directory(final_path.parent)
        return (
            ManifestSeal(final_path.resolve(), seal.file_sha256, seal.content_digest),
            summary,
        )
    except BaseException:
        if temporary_path.exists() and not temporary_path.is_symlink():
            temporary_path.unlink()
        raise
