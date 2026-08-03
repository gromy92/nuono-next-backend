from __future__ import annotations

import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from schema_migrations.artifact import (  # noqa: E402
    RUNNER_RELATIVE_PATHS,
    RUNNER_RESOURCE_ROOT,
    runner_descriptors,
    verify_release_inputs,
)
from schema_migrations.catalog import (  # noqa: E402
    CATALOG_PATH,
    load_catalog,
    sha256_file,
)
from schema_migrations.core import MigrationError  # noqa: E402


class SchemaMigrationArtifactTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.resources = self.root / "resources"
        (self.resources / "db/init").mkdir(parents=True)
        (self.resources / "db/postcheck").mkdir(parents=True)
        (self.resources / "db/livecheck").mkdir(parents=True)
        self.script = self.resources / "db/init/227_history.sql"
        self.postcheck = self.resources / "db/postcheck/227_history.sql"
        self.livecheck = self.resources / "db/livecheck/227_history.sql"
        self.script.write_text("SELECT 227;\n", encoding="utf-8")
        self.postcheck.write_text("SELECT 1;\n", encoding="utf-8")
        self.livecheck.write_text("SELECT 2;\n", encoding="utf-8")
        self.catalog = self.resources.joinpath(*CATALOG_PATH.parts)
        self.catalog.write_text(
            "order\tmigration_key\tkind\tscript_path\tpostcheck_path\t"
            "livecheck_path\n"
            "227\t227_history.sql\tBOOTSTRAP\tdb/init/227_history.sql\t"
            "db/postcheck/227_history.sql\tdb/livecheck/227_history.sql\n",
            encoding="utf-8",
        )
        self.migrations = load_catalog(self.resources)
        self.jar = self.root / "backend.jar"
        with zipfile.ZipFile(self.jar, "w") as archive:
            for path in (self.catalog, self.script, self.postcheck, self.livecheck):
                relative = path.relative_to(self.resources).as_posix()
                archive.write(path, "BOOT-INF/classes/" + relative)
            for relative in RUNNER_RELATIVE_PATHS:
                archive.write(
                    SCRIPT_DIR.joinpath(*relative.parts),
                    "BOOT-INF/classes/"
                    + (RUNNER_RESOURCE_ROOT / relative).as_posix(),
                )
        self.manifest = self.root / "release-manifest.json"
        self.write_manifest("a" * 40)

    def tearDown(self):
        self.temporary.cleanup()

    def test_accepts_all_migration_checks_bound_to_same_jar(self):
        verify_release_inputs(
            self.manifest,
            self.jar,
            "a" * 40,
            SCRIPT_DIR,
        )

    def test_executes_frozen_jar_resources_not_mutable_workspace_sql(self):
        self.postcheck.write_text("SELECT 0;\n", encoding="utf-8")

        migrations = verify_release_inputs(
            self.manifest,
            self.jar,
            "a" * 40,
            SCRIPT_DIR,
        )

        self.assertEqual(b"SELECT 1;\n", migrations[0].postcheck_bytes)
        self.assertEqual(b"SELECT 2;\n", migrations[0].livecheck_bytes)

    def test_never_reopens_a_jar_path_after_freezing_its_bytes(self):
        original = self.jar.read_bytes()

        def freeze_then_replace(path):
            path.write_bytes(b"replacement after governed hash")
            return original

        with patch(
            "schema_migrations.artifact._read_frozen_jar",
            side_effect=freeze_then_replace,
        ):
            migrations = verify_release_inputs(
                self.manifest,
                self.jar,
                "a" * 40,
                SCRIPT_DIR,
            )

        self.assertEqual(b"SELECT 1;\n", migrations[0].postcheck_bytes)

    def test_rejects_release_commit_mismatch(self):
        with self.assertRaisesRegex(MigrationError, "commit mismatch"):
            verify_release_inputs(
                self.manifest,
                self.jar,
                "b" * 40,
                SCRIPT_DIR,
            )

    def write_manifest(self, commit):
        migration = self.migrations[0]
        payload = {
            "schema_version": 1,
            "component": "backend",
            "repository": "gromy92/nuono-next-backend",
            "commit": commit,
            "event": "push",
            "ref": "refs/heads/master",
            "deployable": True,
            "files": [
                {
                    "path": self.jar.name,
                    "sha256": sha256_file(self.jar),
                    "size": self.jar.stat().st_size,
                }
            ],
            "migration_catalog": {
                "path": CATALOG_PATH.as_posix(),
                "sha256": sha256_file(self.catalog),
                "size": self.catalog.stat().st_size,
            },
            "forward_migrations": [
                {
                    "order": migration.order,
                    "migration_key": migration.key,
                    "kind": migration.kind,
                    "script_path": migration.script_path.as_posix(),
                    "script_sha256": migration.checksum,
                    "script_size": len(migration.script_bytes),
                    "postcheck_path": migration.postcheck_path.as_posix(),
                    "postcheck_sha256": migration.postcheck_checksum,
                    "postcheck_size": len(migration.postcheck_bytes),
                    "livecheck_path": migration.livecheck_path.as_posix(),
                    "livecheck_sha256": migration.livecheck_checksum,
                    "livecheck_size": len(migration.livecheck_bytes),
                }
            ],
            "migration_runner": runner_descriptors(SCRIPT_DIR),
        }
        self.manifest.write_text(json.dumps(payload), encoding="utf-8")


if __name__ == "__main__":
    unittest.main()
