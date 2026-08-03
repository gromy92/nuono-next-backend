from __future__ import annotations

import unittest
import sys
from pathlib import Path
from pathlib import PurePosixPath

SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from schema_migrations.core import (  # noqa: E402
    Migration,
    MigrationError,
    MigrationRunner,
    MigrationState,
)
from tests.schema_migration_fakes import FakeDatabase  # noqa: E402


BOOTSTRAP_KEY = "227_database_migration_history.sql"
RETIREMENT_KEY = "242_file_management_parse_retirement.sql"


def migration(order: int, key: str, kind: str) -> Migration:
    sql = f"SELECT {order};\n".encode()
    check = b"SELECT 1;\n"
    return Migration(
        order=order,
        key=key,
        kind=kind,
        script_path=PurePosixPath(f"db/init/{key}"),
        postcheck_path=PurePosixPath(f"db/postcheck/{key}"),
        livecheck_path=PurePosixPath(f"db/postcheck/{key}"),
        checksum=str(order).zfill(64),
        postcheck_checksum="1" * 64,
        livecheck_checksum="1" * 64,
        script_bytes=sql,
        postcheck_bytes=check,
        livecheck_bytes=check,
    )


class RuntimeDrainMigrationTest(unittest.TestCase):
    def setUp(self):
        self.bootstrap = migration(227, BOOTSTRAP_KEY, "BOOTSTRAP")
        self.retirement = migration(242, RETIREMENT_KEY, "MANAGED")

    def runner(self, database: FakeDatabase) -> MigrationRunner:
        return MigrationRunner(
            database,
            (self.bootstrap, self.retirement),
            release_commit="a" * 40,
            installed_by="unit-test",
        )

    def test_apply_requires_exact_runtime_drain_approval(self):
        database = FakeDatabase()
        runner = self.runner(database)

        with self.assertRaisesRegex(MigrationError, "missing .*242"):
            runner.apply(approved_managed=[RETIREMENT_KEY])
        self.assertNotIn(("script", RETIREMENT_KEY), database.events)

        with self.assertRaisesRegex(MigrationError, "not allowed .*241"):
            runner.apply(
                approved_managed=[RETIREMENT_KEY],
                approved_runtime_drains=[RETIREMENT_KEY, "241_wrong.sql"],
            )

    def test_acknowledgement_is_in_the_locked_session_before_script(self):
        database = FakeDatabase()
        runner = self.runner(database)

        runner.apply(
            approved_managed=[RETIREMENT_KEY],
            approved_runtime_drains=[RETIREMENT_KEY],
        )

        ack = database.events.index(("runtime-drain", RETIREMENT_KEY))
        begin = database.events.index(("begin", RETIREMENT_KEY, 1, "APPLY"))
        self.assertLess(ack, begin)
        with self.assertRaisesRegex(MigrationError, "not allowed .*242"):
            runner.apply(
                approved_managed=[RETIREMENT_KEY],
                approved_runtime_drains=[RETIREMENT_KEY],
            )

    def test_repair_rerun_requires_fresh_drain_acknowledgement(self):
        class RepairDatabase(FakeDatabase):
            def run_script(inner_self, migration):
                super().run_script(migration)
                inner_self.postcheck_results[migration.key] = True

        failed = MigrationState(
            RETIREMENT_KEY,
            self.retirement.checksum,
            self.retirement.postcheck_checksum,
            "FAILED",
            1,
        )
        database = RepairDatabase({RETIREMENT_KEY: failed})
        database.postcheck_results[RETIREMENT_KEY] = False
        runner = self.runner(database)

        with self.assertRaisesRegex(MigrationError, "missing .*242"):
            runner.repair_forward(
                RETIREMENT_KEY,
                rerun=True,
                approved_managed=[RETIREMENT_KEY],
            )
        runner.repair_forward(
            RETIREMENT_KEY,
            rerun=True,
            approved_managed=[RETIREMENT_KEY],
            approved_runtime_drains=[RETIREMENT_KEY],
        )
        self.assertIn(("runtime-drain", RETIREMENT_KEY), database.events)


if __name__ == "__main__":
    unittest.main()
