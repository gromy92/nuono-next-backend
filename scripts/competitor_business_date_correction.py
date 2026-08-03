#!/usr/bin/env python3
"""Public entry point for competitor historical business-date correction."""
from __future__ import annotations

import sys
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from competitor_business_date.cli import parse_args  # noqa: E402
from competitor_business_date.service import run_command  # noqa: E402


def main() -> int:
    return run_command(parse_args())


if __name__ == "__main__":
    raise SystemExit(main())
