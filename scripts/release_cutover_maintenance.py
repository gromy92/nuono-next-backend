#!/usr/bin/env python3
"""Public governed release cutover surface."""
from __future__ import annotations

import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from release_schema_cutover import (  # noqa: E402
    build_additive_schema_migration_script,
    build_irreversible_schema_cutover_script,
)
from release_legacy_single_scheduler_cutover import (  # noqa: E402
    build_legacy_single_scheduler_cutover_script,
)
from release_single_scheduler_cutover import (  # noqa: E402
    build_single_scheduler_cutover_script as build_dp_runtime_single_scheduler_cutover_script,
)


def build_single_scheduler_cutover_script(
    *,
    preserve_dp_legacy: bool = False,
    expected_dp_execution_mode: str = "",
    **arguments,
):
    if preserve_dp_legacy:
        return build_legacy_single_scheduler_cutover_script(
            expected_dp_execution_mode=expected_dp_execution_mode,
            **arguments,
        )
    if expected_dp_execution_mode:
        raise ValueError("DP execution mode is only valid for LEGACY preservation")
    return build_dp_runtime_single_scheduler_cutover_script(**arguments)

__all__ = [
    "build_additive_schema_migration_script",
    "build_irreversible_schema_cutover_script",
    "build_single_scheduler_cutover_script",
]
