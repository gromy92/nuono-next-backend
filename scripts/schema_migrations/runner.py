from __future__ import annotations

import re
from typing import Sequence

from schema_migrations.model import Migration, MigrationDatabase, MigrationError
from schema_migrations.state import (
    completed_prefix,
    migration_index,
    plan_migrations,
    validate_repair_target,
)

RUNTIME_DRAIN_MIGRATIONS = frozenset({
    "242_file_management_parse_retirement.sql",
})


class MigrationRunner:
    def __init__(
        self,
        database: MigrationDatabase,
        migrations: Sequence[Migration],
        *,
        release_commit: str,
        installed_by: str,
        lock_timeout_seconds: int = 30,
    ):
        if not migrations or migrations[0].kind != "BOOTSTRAP":
            raise MigrationError("migration catalog must begin with BOOTSTRAP")
        if not re.fullmatch(r"[0-9a-f]{40}", release_commit):
            raise MigrationError("release_commit must be a full lowercase Git SHA")
        if not installed_by.strip() or len(installed_by.strip()) > 128:
            raise MigrationError("installed_by must contain 1-128 characters")
        if lock_timeout_seconds < 0 or lock_timeout_seconds > 300:
            raise MigrationError("lock timeout must be between 0 and 300 seconds")
        self.database = database
        self.migrations = tuple(migrations)
        self.release_commit = release_commit
        self.installed_by = installed_by.strip()
        self.lock_timeout_seconds = lock_timeout_seconds

    def apply(
        self,
        *,
        approved_managed: Sequence[str] = (),
        approved_runtime_drains: Sequence[str] = (),
    ) -> list[str]:
        self.database.acquire_lock(self.lock_timeout_seconds)
        try:
            self._bootstrap()
            states = self.database.load_states()
            pending = plan_migrations(self.migrations, states)
            self._validate_live(completed_prefix(self.migrations, states))
            self._validate_managed_approvals(
                pending,
                approved_managed,
                allow_completed=True,
            )
            self._acknowledge_runtime_drains(pending, approved_runtime_drains)
            for migration in pending:
                if migration.kind == "BOOTSTRAP":
                    raise MigrationError("history bootstrap was not recorded")
                self._apply_one(migration, operation="APPLY")
            return [migration.key for migration in pending]
        finally:
            self.database.release_lock()

    def repair_forward(
        self,
        migration_key: str,
        *,
        rerun: bool = False,
        approved_managed: Sequence[str] = (),
        approved_runtime_drains: Sequence[str] = (),
    ) -> str:
        if approved_runtime_drains and not rerun:
            raise MigrationError(
                "runtime-drain approval is only valid for repair-forward --rerun"
            )
        migration = self._find(migration_key)
        self.database.acquire_lock(self.lock_timeout_seconds)
        try:
            self._bootstrap()
            states = self.database.load_states()
            state = validate_repair_target(
                self.migrations, states, migration.key
            )
            target_index = migration_index(self.migrations, migration.key)
            self._validate_live(self.migrations[:target_index])
            self._validate_managed_approvals(
                (migration,),
                approved_managed,
                allow_completed=False,
            )
            if self.database.postcheck(migration):
                self.database.reconcile(
                    migration,
                    state.attempt_no,
                    self.release_commit,
                    self.installed_by,
                )
                return "POSTCHECK_RECONCILED"
            if not rerun:
                raise MigrationError(
                    f"{migration.key}: postcheck failed; review evidence before --rerun"
                )
            if migration.kind == "BOOTSTRAP":
                raise MigrationError(f"{migration.key}: bootstrap cannot be rerun here")
            self._acknowledge_runtime_drains(
                (migration,), approved_runtime_drains
            )
            if state.state == "APPLYING":
                self.database.mark_failed(
                    migration,
                    state.attempt_no,
                    MigrationError("superseded by explicit repair-forward rerun"),
                )
            self._apply_one(migration, operation="RERUN")
            return "RERUN_APPLIED"
        finally:
            self.database.release_lock()

    def _bootstrap(self) -> None:
        self.database.bootstrap(
            self.migrations[0], self.release_commit, self.installed_by
        )

    def _apply_one(self, migration: Migration, *, operation: str) -> None:
        attempt_no = self.database.begin(
            migration,
            self.release_commit,
            self.installed_by,
            operation,
        )
        try:
            self.database.run_script(migration)
            if not self.database.postcheck(migration):
                raise MigrationError("postcheck returned false")
            self.database.mark_applied(migration, attempt_no)
        except Exception as error:
            try:
                self.database.mark_failed(migration, attempt_no, error)
            except Exception as audit_error:
                raise MigrationError(
                    f"{migration.key}: migration failed ({error}); "
                    f"recording FAILED also failed ({audit_error})"
                ) from audit_error
            raise MigrationError(
                f"{migration.key}: migration failed and requires repair-forward: {error}"
            ) from error

    def _validate_live(self, migrations: Sequence[Migration]) -> None:
        for migration in migrations:
            if not self.database.livecheck(migration):
                raise MigrationError(
                    f"{migration.key}: live schema drift; add a new forward migration"
                )

    def _validate_managed_approvals(
        self,
        pending: Sequence[Migration],
        approved_managed: Sequence[str],
        *,
        allow_completed: bool,
    ) -> None:
        approved = tuple(approved_managed)
        required = {
            migration.key for migration in pending if migration.kind == "MANAGED"
        }
        supplied = set(approved)
        allowed = (
            {
                migration.key
                for migration in self.migrations
                if migration.kind == "MANAGED"
            }
            if allow_completed
            else required
        )
        if len(supplied) != len(approved):
            raise MigrationError("managed migration approvals contain duplicates")
        if not required.issubset(supplied) or not supplied.issubset(allowed):
            missing = sorted(required - supplied)
            stale = sorted(supplied - allowed)
            details = []
            if missing:
                details.append("missing " + ", ".join(missing))
            if stale:
                details.append("not allowed " + ", ".join(stale))
            raise MigrationError(
                "managed migration approvals must cover pending MANAGED "
                "migrations and contain only allowed catalog keys: "
                + "; ".join(details)
            )

    def _acknowledge_runtime_drains(
        self,
        pending: Sequence[Migration],
        approved_runtime_drains: Sequence[str],
    ) -> None:
        approved = tuple(approved_runtime_drains)
        required = {
            migration.key
            for migration in pending
            if migration.key in RUNTIME_DRAIN_MIGRATIONS
        }
        supplied = set(approved)
        if len(supplied) != len(approved):
            raise MigrationError("runtime-drain approvals contain duplicates")
        if supplied != required:
            missing = sorted(required - supplied)
            stale = sorted(supplied - required)
            details = []
            if missing:
                details.append("missing " + ", ".join(missing))
            if stale:
                details.append("not allowed " + ", ".join(stale))
            raise MigrationError(
                "runtime-drain approvals must exactly cover pending drained-runtime "
                "migrations: " + "; ".join(details)
            )
        for migration_key in approved:
            self.database.acknowledge_runtime_drain(migration_key)

    def _find(self, migration_key: str) -> Migration:
        return self.migrations[migration_index(self.migrations, migration_key)]
