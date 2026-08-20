from __future__ import annotations

import re
import sys
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from schema_migrations.catalog import load_catalog  # noqa: E402
from schema_migrations.sql_text import code_outside_literals_and_comments  # noqa: E402


class Dp02SnapshotOperationMigrationTest(unittest.TestCase):
    ROOT = SCRIPT_DIR.parent / "src/main/resources"

    def migration(self):
        return next(item for item in load_catalog(self.ROOT) if item.order == 256)

    def test_expands_only_the_two_snapshot_operation_constraints(self):
        migration = self.migration()
        script = migration.script_sql

        self.assertEqual("256_dp02_snapshot_operation_contract.sql", migration.key)
        self.assertEqual("AUTO_ADDITIVE", migration.kind)
        self.assertEqual(2, script.count("DROP CHECK"))
        self.assertEqual(2, script.count("CHECK (`operation_code` IN (''DP02'',''DP04'',''DP07A''))"))
        self.assertIn("DP256_APPLY_OPERATION_PREDECESSOR_DRIFT", script)
        self.assertIn("DP256_HEAD_OPERATION_PREDECESSOR_DRIFT", script)
        self.assertNotRegex(
            code_outside_literals_and_comments(script),
            re.compile(
                r"\b(?:INSERT|UPDATE|DELETE|TRUNCATE)\b|\bREPLACE\s+INTO\b",
                re.IGNORECASE,
            ),
        )

    def test_exact_and_live_checks_are_read_only_and_require_dp02(self):
        migration = self.migration()
        for sql in (migration.postcheck_sql, migration.livecheck_sql):
            executable = code_outside_literals_and_comments(sql)
            self.assertTrue(executable.lstrip().startswith("WITH actual_check AS"))
            self.assertEqual(1, executable.count(";"))
            self.assertIn("LOCATE('dp02',clause_signature)=0", sql)
            self.assertIn("operation_code NOT IN ('DP02','DP04','DP07A')", sql)
            self.assertNotRegex(
                executable,
                re.compile(
                    r"\b(?:INSERT|UPDATE|DELETE|ALTER|CREATE|DROP|TRUNCATE)\b"
                    r"|\bREPLACE\s+INTO\b",
                    re.IGNORECASE,
                ),
            )


if __name__ == "__main__":
    unittest.main()
