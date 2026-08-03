"""Digest the exact correction implementation and migration bytes."""
from __future__ import annotations

import hashlib
from pathlib import Path
from typing import Any

from .manifest_digest import canonical_json
from .secure_files import sha256_file


class BundleIdentityError(RuntimeError):
    pass


def operation_bundle_identity() -> dict[str, Any]:
    backend_root = Path(__file__).resolve().parents[2]
    package = backend_root / "scripts/competitor_business_date"
    files = sorted(package.glob("*.py"))
    files.extend(
        [
            backend_root / "scripts/competitor_business_date_correction.py",
            backend_root
            / "src/main/resources/db/init"
            / "240_operations_competitor_snapshot_active_uniqueness.sql",
            backend_root
            / "src/main/resources/db/init"
            / "241_operations_competitor_correction_writer_fence.sql",
        ]
    )
    if any(not path.is_file() or path.is_symlink() for path in files):
        raise BundleIdentityError("correction operation bundle is incomplete")
    descriptors = [
        {
            "path": path.relative_to(backend_root).as_posix(),
            "sha256": sha256_file(path),
            "size": path.stat().st_size,
        }
        for path in files
    ]
    digest = hashlib.sha256(
        canonical_json(descriptors).encode("utf-8")
    ).hexdigest()
    return {
        "schema_version": 1,
        "sha256": digest,
        "files": descriptors,
    }
