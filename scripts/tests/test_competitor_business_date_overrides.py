from __future__ import annotations

import json
import os
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_DIR = Path(__file__).parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from competitor_business_date.overrides import (  # noqa: E402
    OverrideError,
    load_clock_overrides,
)
from competitor_business_date.manifest_metadata_values import (  # noqa: E402
    validate_clock_overrides,
)
from competitor_business_date.secure_files import sha256_file  # noqa: E402


class ClockOverridesTest(unittest.TestCase):
    def write(self, root: Path, payload: object) -> Path:
        path = root / "overrides.json"
        path.write_text(json.dumps(payload), encoding="utf-8")
        os.chmod(path, 0o600)
        return path

    def test_loads_checksum_bound_audited_classifications(self):
        with tempfile.TemporaryDirectory() as directory:
            path = self.write(
                Path(directory),
                {
                    "schema_version": 1,
                    "reason": "release evidence confirms legacy clock",
                    "approved_by": "operator-307",
                    "snapshot": {"12": "legacy"},
                    "rank": {"34": "current"},
                    "event_contract": {"56": "list_v1"},
                },
            )

            loaded = load_clock_overrides(path, sha256_file(path))

            self.assertEqual({12: "legacy"}, loaded.snapshot)
            self.assertEqual({34: "current"}, loaded.rank)
            self.assertEqual({56: "list_v1"}, loaded.event_contract)
            manifest_value = loaded.manifest_value()
            self.assertEqual(1, manifest_value["schema_version"])
            self.assertEqual("operator-307", manifest_value["approved_by"])
            validate_clock_overrides(manifest_value)

    def test_rejects_digest_mismatch(self):
        with tempfile.TemporaryDirectory() as directory:
            path = self.write(Path(directory), {})
            with self.assertRaisesRegex(OverrideError, "mismatch"):
                load_clock_overrides(path, "a" * 64)

    def test_rejects_unaudited_or_invalid_classification(self):
        with tempfile.TemporaryDirectory() as directory:
            path = self.write(
                Path(directory),
                {
                    "schema_version": 1,
                    "reason": "",
                    "approved_by": "operator",
                    "snapshot": {"12": "guess"},
                    "rank": {},
                    "event_contract": {},
                },
            )
            with self.assertRaisesRegex(OverrideError, "reason"):
                load_clock_overrides(path, sha256_file(path))


if __name__ == "__main__":
    unittest.main()
