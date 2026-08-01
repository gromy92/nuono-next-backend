from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Mapping, Protocol

APPLIED_STATE = "APPLIED"
BASELINED_STATE = "BASELINED"
BLOCKED_STATES = frozenset({"APPLYING", "FAILED"})
VALID_STATES = frozenset({APPLIED_STATE, BASELINED_STATE, *BLOCKED_STATES})


class MigrationError(RuntimeError):
    pass


@dataclass(frozen=True)
class Migration:
    order: int
    key: str
    kind: str
    script_path: PurePosixPath
    postcheck_path: PurePosixPath
    checksum: str
    postcheck_checksum: str
    script_bytes: bytes
    postcheck_bytes: bytes
    script_file: Path | None = None
    postcheck_file: Path | None = None

    @property
    def script_sql(self) -> str:
        return self.script_bytes.decode("utf-8")

    @property
    def postcheck_sql(self) -> str:
        return self.postcheck_bytes.decode("utf-8")


@dataclass(frozen=True)
class MigrationState:
    key: str
    checksum: str
    postcheck_checksum: str
    state: str
    attempt_no: int


class MigrationDatabase(Protocol):
    def acquire_lock(self, timeout_seconds: int) -> None: ...

    def release_lock(self) -> None: ...

    def bootstrap(
        self, migration: Migration, release_commit: str, installed_by: str
    ) -> None: ...

    def load_states(self) -> Mapping[str, MigrationState]: ...

    def begin(
        self,
        migration: Migration,
        release_commit: str,
        installed_by: str,
        operation: str,
    ) -> int: ...

    def run_script(self, migration: Migration) -> None: ...

    def postcheck(self, migration: Migration) -> bool: ...

    def mark_applied(self, migration: Migration, attempt_no: int) -> None: ...

    def mark_failed(
        self, migration: Migration, attempt_no: int, error: Exception
    ) -> None: ...

    def reconcile(
        self,
        migration: Migration,
        blocked_attempt_no: int,
        release_commit: str,
        installed_by: str,
    ) -> int: ...
