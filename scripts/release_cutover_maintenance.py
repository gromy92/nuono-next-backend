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
    build_runtime_single_scheduler_upgrade_script,
)
from release_single_scheduler_cutover import (  # noqa: E402
    build_single_scheduler_cutover_script as build_dp_runtime_single_scheduler_cutover_script,
)


def build_single_scheduler_cutover_script(
    *,
    preserve_dp_legacy: bool = False,
    preserve_dp_runtime: bool = False,
    expected_dp_execution_mode: str = "",
    **arguments,
):
    if preserve_dp_legacy and preserve_dp_runtime:
        raise ValueError("only one DP preservation mode may be selected")
    if preserve_dp_legacy:
        return build_legacy_single_scheduler_cutover_script(
            expected_dp_execution_mode=expected_dp_execution_mode,
            **arguments,
        )
    if preserve_dp_runtime:
        return build_runtime_single_scheduler_upgrade_script(
            expected_dp_execution_mode=expected_dp_execution_mode,
            **arguments,
        )
    if expected_dp_execution_mode:
        raise ValueError("DP execution mode is only valid for mode preservation")
    return build_dp_runtime_single_scheduler_cutover_script(**arguments)

__all__ = [
    "build_additive_schema_migration_script",
    "build_irreversible_schema_cutover_script",
    "build_single_scheduler_cutover_script",
]
