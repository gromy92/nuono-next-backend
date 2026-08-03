import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))

from ci.competitor_business_date_mysql_manifest import (  # noqa: E402
    seed_and_build_manifest,
)
from competitor_business_date.evidence_contract import (  # noqa: E402
    validate_source_evidence,
)
from competitor_business_date.execution_plan import (  # noqa: E402
    ordered_groups,
    prevalidate_batches,
)
from competitor_business_date.manifest import ManifestReader  # noqa: E402
from competitor_business_date.manifest_contract import (  # noqa: E402
    ManifestContractError,
    validate_manifest_changes,
    validate_manifest_metadata,
)


class FakeMysql:
    schema = "nuonuoai"

    def __init__(self):
        self.scripts = []

    def run_script(self, sql, **_):
        self.scripts.append(sql)
        return ""

    def query_one_json(self, _):
        return {
            "sequence_name": "operations_competitor_product_change_event",
            "next_id": 270000,
            "max_event_id": 0,
            "gmt_create": "2026-07-01 00:00:00",
            "gmt_updated": "2026-07-01 00:00:00",
        }


FINGERPRINT = [{
    "record_type": "server",
    "table_name": "",
    "object_name": "",
    "ordinal_position": 0,
    "payload_json": json.dumps({
        "database": "nuonuoai",
        "server_uuid": "12345678-1234-1234-1234-123456789abc",
        "hostname": "mysql-ci",
        "port": 3306,
        "version": "8.4.0",
        "max_allowed_packet": 64 * 1024 * 1024,
    }),
}]


class CompetitorBusinessDateMysqlManifestFixtureTest(unittest.TestCase):
    def test_fixture_is_a_complete_executable_closed_contract(self):
        mysql = FakeMysql()
        with tempfile.TemporaryDirectory() as temporary, patch(
            "ci.competitor_business_date_mysql_manifest.read_schema_fingerprint",
            return_value=(FINGERPRINT, "a" * 64),
        ):
            manifest, backup, digest = seed_and_build_manifest(
                mysql,
                Path(temporary),
            )
            with ManifestReader(manifest, digest) as reader:
                validate_manifest_metadata(reader.metadata)
                isolated = dict(reader.metadata)
                isolated["target_schema"] = "nuono_schema_migration_ci"
                isolated["database_identity"] = dict(isolated["database_identity"])
                isolated["database_identity"]["database"] = isolated["target_schema"]
                validate_manifest_metadata(isolated)
                isolated["target_schema"] = "unsafe-schema;drop"
                with self.assertRaises(ManifestContractError):
                    validate_manifest_metadata(isolated)
                self.assertEqual(
                    {
                        "event": 0,
                        "event_sequence": 1,
                        "keyword_run": 1,
                        "rank": 1,
                        "schema_fingerprint": 1,
                        "snapshot": 2,
                    },
                    validate_source_evidence(reader, reader.metadata),
                )
                counts = validate_manifest_changes(
                    reader.iter_changes(),
                    reader.metadata,
                )
                self.assertEqual(6, sum(counts.values()))
                batches, total, max_group, _ = prevalidate_batches(
                    reader,
                    ordered_groups(reader, "apply"),
                    batch_size=10,
                    max_sql_bytes=16 * 1024 * 1024,
                    direction="apply",
                )
                self.assertEqual(3, len(batches))
                self.assertEqual(6, total)
                self.assertEqual(3, max_group)
            self.assertTrue(backup.is_file())
            self.assertEqual(4, len(mysql.scripts))


if __name__ == "__main__":
    unittest.main()
