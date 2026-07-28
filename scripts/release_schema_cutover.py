#!/usr/bin/env python3
"""Render governed additive and irreversible schema cutover scripts."""
from __future__ import annotations

import shlex
from pathlib import Path

TEMPLATE_DIR = Path(__file__).with_name("release_templates")


def _assignments(values: dict[str, str | int]) -> str:
    return "\n".join(
        f"{key}={shlex.quote(str(value))}" for key, value in values.items()
    )


def _render(values: dict[str, str | int], *templates: str) -> str:
    body = "\n".join(
        (TEMPLATE_DIR / template).read_text(encoding="utf-8").rstrip()
        for template in templates
    )
    return f"#!/usr/bin/env bash\nset -Eeuo pipefail\n{_assignments(values)}\n{body}\n"


def build_additive_schema_migration_script(
    *,
    staged_jar: str,
    expected_jar_sha256: str,
    expected_commit: str,
    expected_182_sha256: str,
    expected_189_sha256: str,
    expected_190_sha256: str,
    expected_204_sha256: str,
    expected_205_sha256: str,
    app_dir: str,
    release_name: str,
) -> str:
    """Return the locked body for conditional 182 plus additive 204/205."""
    return _render(
        {
            "APP_DIR": app_dir,
            "STAGED_JAR": staged_jar,
            "EXPECTED_JAR_SHA256": expected_jar_sha256,
            "EXPECTED_COMMIT": expected_commit,
            "EXPECTED_182_SHA256": expected_182_sha256,
            "EXPECTED_189_SHA256": expected_189_sha256,
            "EXPECTED_190_SHA256": expected_190_sha256,
            "EXPECTED_204_SHA256": expected_204_sha256,
            "EXPECTED_205_SHA256": expected_205_sha256,
            "RELEASE_NAME": release_name,
        },
        "additive_schema_migrations.sh",
        "additive_frozen_jar.sh",
        "migration_182_contract.sh",
        "migration_189_contract.sh",
        "additive_execute.sh",
    )


def build_irreversible_schema_cutover_script(
    *,
    expected_jar_sha256: str,
    expected_commit: str,
    expected_182_sha256: str,
    expected_189_sha256: str,
    expected_206_sha256: str,
    active_slot: str,
    active_port: int,
    standby_port: int,
    maintenance_port: int,
    nginx_upstream_file: str,
    release_name: str,
    external_health_url: str,
    app_dir: str,
) -> str:
    """Return the locked body for the irreversible migration-206 window."""
    return _render(
        {
            "APP_DIR": app_dir,
            "EXPECTED_JAR_SHA256": expected_jar_sha256,
            "EXPECTED_COMMIT": expected_commit,
            "EXPECTED_182_SHA256": expected_182_sha256,
            "EXPECTED_189_SHA256": expected_189_sha256,
            "EXPECTED_206_SHA256": expected_206_sha256,
            "ACTIVE_SLOT": active_slot,
            "ACTIVE_PORT": active_port,
            "STANDBY_PORT": standby_port,
            "MAINTENANCE_PORT": maintenance_port,
            "NGINX_UPSTREAM_FILE": nginx_upstream_file,
            "RELEASE_NAME": release_name,
            "EXTERNAL_HEALTH_URL": external_health_url,
        },
        "irreversible_runtime.sh",
        "irreversible_database.sh",
        "irreversible_database_idle.sh",
        "migration_182_contract.sh",
        "migration_189_contract.sh",
        "irreversible_execute.sh",
    )
