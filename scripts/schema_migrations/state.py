from __future__ import annotations

from typing import Mapping, Sequence

from schema_migrations.model import (
    APPLIED_STATE,
    BASELINED_STATE,
    BLOCKED_STATES,
    VALID_STATES,
    Migration,
    MigrationError,
    MigrationState,
)


def plan_migrations(
    migrations: Sequence[Migration],
    states: Mapping[str, MigrationState],
) -> list[Migration]:
    _validate_known_entries(migrations, states)
    pending: list[Migration] = []
    incomplete_seen = False
    blocked: MigrationState | None = None
    for index, migration in enumerate(migrations):
        state = states.get(migration.key)
        if state is None:
            incomplete_seen = True
            pending.append(migration)
            continue
        _validate_state_record(migration, state, index)
        if incomplete_seen:
            raise MigrationError(
                f"{migration.key}: migration history is not a continuous catalog prefix"
            )
        if state.state in {APPLIED_STATE, BASELINED_STATE}:
            continue
        incomplete_seen = True
        blocked = state
    if blocked is not None:
        raise MigrationError(
            f"{blocked.key}: state {blocked.state} requires repair-forward"
        )
    return pending


def validate_repair_target(
    migrations: Sequence[Migration],
    states: Mapping[str, MigrationState],
    migration_key: str,
) -> MigrationState:
    _validate_known_entries(migrations, states)
    target_index = migration_index(migrations, migration_key)
    target_state = None
    for index, migration in enumerate(migrations):
        state = states.get(migration.key)
        if index < target_index:
            if state is None:
                raise MigrationError(
                    f"{migration.key}: predecessor is missing; repair chain is invalid"
                )
            _validate_state_record(migration, state, index)
            if state.state not in {APPLIED_STATE, BASELINED_STATE}:
                raise MigrationError(
                    f"{migration.key}: predecessor state {state.state} blocks repair"
                )
        elif index == target_index:
            if state is None or state.state not in BLOCKED_STATES:
                raise MigrationError(
                    f"{migration.key}: repair-forward requires APPLYING or FAILED state"
                )
            _validate_state_record(migration, state, index)
            target_state = state
        elif state is not None:
            raise MigrationError(
                f"{migration.key}: history exists after the repair target"
            )
    assert target_state is not None
    return target_state


def completed_prefix(
    migrations: Sequence[Migration],
    states: Mapping[str, MigrationState],
) -> tuple[Migration, ...]:
    completed = []
    for migration in migrations:
        state = states.get(migration.key)
        if state is None or state.state not in {APPLIED_STATE, BASELINED_STATE}:
            break
        completed.append(migration)
    return tuple(completed)


def migration_index(
    migrations: Sequence[Migration],
    migration_key: str,
) -> int:
    for index, migration in enumerate(migrations):
        if migration.key == migration_key:
            return index
    raise MigrationError(f"unknown migration key: {migration_key}")


def _validate_known_entries(
    migrations: Sequence[Migration],
    states: Mapping[str, MigrationState],
) -> None:
    catalog_keys = {migration.key for migration in migrations}
    unknown = sorted(set(states) - catalog_keys)
    if unknown:
        raise MigrationError(
            "migration history contains entries missing from catalog: "
            + ", ".join(unknown)
        )


def _validate_state_record(
    migration: Migration,
    state: MigrationState,
    index: int,
) -> None:
    if state.checksum != migration.checksum:
        raise MigrationError(f"{migration.key}: applied checksum drift")
    if state.postcheck_checksum != migration.postcheck_checksum:
        raise MigrationError(f"{migration.key}: postcheck checksum drift")
    if state.state not in VALID_STATES:
        raise MigrationError(f"{migration.key}: unknown history state {state.state}")
    if state.attempt_no < 1:
        raise MigrationError(f"{migration.key}: invalid current attempt number")
    if state.state == BASELINED_STATE and (
        index != 0 or migration.kind != "BOOTSTRAP"
    ):
        raise MigrationError(
            f"{migration.key}: BASELINED is only valid for the first BOOTSTRAP"
        )
