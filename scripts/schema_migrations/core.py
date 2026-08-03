"""Compatibility surface for the Database Migration Module."""

from schema_migrations.model import (
    APPLIED_STATE,
    BASELINED_STATE,
    BLOCKED_STATES,
    VALID_STATES,
    Migration,
    MigrationDatabase,
    MigrationError,
    MigrationState,
)
from schema_migrations.runner import MigrationRunner
from schema_migrations.state import (
    completed_prefix,
    plan_migrations,
    validate_repair_target,
)

__all__ = [
    "APPLIED_STATE",
    "BASELINED_STATE",
    "BLOCKED_STATES",
    "VALID_STATES",
    "Migration",
    "MigrationDatabase",
    "MigrationError",
    "MigrationRunner",
    "MigrationState",
    "completed_prefix",
    "plan_migrations",
    "validate_repair_target",
]
