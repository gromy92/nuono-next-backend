from __future__ import annotations

import base64
import json
import sys
import unittest
from pathlib import Path


SCRIPT_DIR = Path(__file__).parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from competitor_business_date.consistent_read import (  # noqa: E402
    ConsistentDatasetReader,
    ConsistentReadError,
    build_consistent_read_script,
)


def encoded(value):
    return base64.b64encode(
        json.dumps(value, separators=(",", ":"), sort_keys=True).encode()
    ).decode()


class FakeMysql:
    def __init__(self, rows):
        self.rows = rows
        self.script = None
        self.timeout = None

    def query_json_objects(self, script, *, timeout_seconds):
        self.script = script
        self.timeout = timeout_seconds
        yield from self.rows


class ConsistentReadTest(unittest.TestCase):
    def test_one_transaction_streams_named_datasets_in_order(self):
        mysql = FakeMysql(
            [
                {"__dataset__": "first", "__phase__": "start"},
                {"id": 1},
                {"__dataset__": "first", "__phase__": "end"},
                {"__dataset__": "second", "__phase__": "start"},
                {"id": 2},
                {"__dataset__": "second", "__phase__": "end"},
            ]
        )
        reader = ConsistentDatasetReader(
            mysql,
            (("first", "SELECT 'x';"), ("second", "SELECT 'y';")),
            timeout_seconds=90,
        )

        self.assertEqual([{"id": 1}], list(reader.read("first")))
        self.assertEqual([{"id": 2}], list(reader.read("second")))
        reader.finish()
        self.assertIn("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY", mysql.script)
        self.assertTrue(mysql.script.rstrip().endswith("COMMIT;"))
        self.assertEqual(90, mysql.timeout)

    def test_rejects_out_of_order_or_missing_markers(self):
        reader = ConsistentDatasetReader(
            FakeMysql([]),
            (("first", "SELECT 'x';"),),
            timeout_seconds=1,
        )
        with self.assertRaisesRegex(ConsistentReadError, "unexpected dataset"):
            list(reader.read("other"))
        with self.assertRaisesRegex(ConsistentReadError, "missing start"):
            list(reader.read("first"))

    def test_script_uses_base64_json_control_rows(self):
        script = build_consistent_read_script((("rows", "SELECT 'payload';"),))
        control = encoded({"__dataset__": "rows", "__phase__": "start"})
        self.assertIn(f"SELECT '{control}';", script)

    def test_rejects_multistatement_dataset(self):
        with self.assertRaisesRegex(ConsistentReadError, "one SQL statement"):
            build_consistent_read_script((("rows", "SELECT 1; SELECT 2;"),))


if __name__ == "__main__":
    unittest.main()
