from __future__ import annotations

import os
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from schema_migrations.core import MigrationError  # noqa: E402
from schema_migrations.mysql_client import MySqlClient  # noqa: E402
from schema_migrations.mysql_support import sql_literal  # noqa: E402


class MySqlMigrationClientTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.defaults_file = self.root / "migration.cnf"
        self.defaults_file.write_text("[client]\nuser=test\n", encoding="utf-8")
        self.defaults_file.chmod(0o600)
        self.input_log = self.root / "mysql-input.log"

    def tearDown(self):
        self.temporary.cleanup()

    def test_sql_values_use_hex_literals_even_with_backslash_quote(self):
        value = "operator\\'; DROP TABLE users; --"

        rendered = sql_literal(value)

        self.assertEqual(
            "CONVERT(X'"
            + value.encode("utf-8").hex()
            + "' USING utf8mb4) COLLATE utf8mb4_unicode_ci",
            rendered,
        )
        self.assertNotIn(value, rendered)

    def test_defaults_file_is_frozen_and_ambient_login_paths_are_disabled(self):
        client = self.client(lock_result="1")
        try:
            self.defaults_file.write_text(
                "[client]\nuser=changed\n",
                encoding="utf-8",
            )

            self.assertEqual(
                "[client]\nuser=test\n",
                client.defaults_file.read_text(encoding="utf-8"),
            )
            command = client.command()
            self.assertTrue(command[1].startswith("--defaults-file="))
            self.assertIn("--no-login-paths", command)
            self.assertIn("--skip-reconnect", command)
            self.assertIn("--host=db.internal", command)
            self.assertIn("--port=3307", command)
            self.assertIn("--database=nuono_test", command)
            self.assertNotIn("--defaults-extra-file", " ".join(command))
        finally:
            client.close()

    @unittest.skipIf(os.name == "nt", "symlink policy is POSIX-only")
    def test_defaults_file_must_not_be_a_symlink(self):
        link = self.root / "migration-link.cnf"
        link.symlink_to(self.defaults_file)

        with self.assertRaisesRegex(MigrationError, "regular file"):
            MySqlClient(
                link,
                expected_schema="nuono_test",
                expected_host="db.internal",
                expected_port=3307,
                execution_timeout_seconds=5,
            )

    def test_failed_lock_attempt_terminates_the_owning_connection(self):
        client = self.client(lock_result="0")
        try:
            with self.assertRaisesRegex(MigrationError, "not acquired"):
                client.acquire_lock("nuono:schema-migrations", 0)

            self.assertIsNone(client.lock_process)
        finally:
            client.close()

    def test_release_uses_the_same_connection_that_owns_the_lock(self):
        client = self.client(lock_result="1")
        try:
            client.acquire_lock("nuono:schema-migrations", 1)
            process = client.lock_process

            client.release_lock("nuono:schema-migrations")

            self.assertIsNone(client.lock_process)
            self.assertIsNotNone(process)
            self.assertIsNotNone(process.returncode)
            logged = self.input_log.read_text(encoding="utf-8")
            self.assertIn(
                "SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci",
                logged,
            )
            self.assertIn("GET_LOCK", logged)
            self.assertIn("RELEASE_LOCK", logged)
            self.assertLess(logged.index("GET_LOCK"), logged.index("RELEASE_LOCK"))
        finally:
            client.close()

    def client(self, *, lock_result):
        mysql_bin = self.root / f"fake-mysql-{lock_result}.py"
        mysql_bin.write_text(
            "#!/usr/bin/env python3\n"
            "import re\n"
            "import sys\n"
            "from pathlib import Path\n"
            f"log = Path({str(self.input_log)!r})\n"
            f"lock_result = {lock_result!r}\n"
            "for raw in sys.stdin:\n"
            "    with log.open('a', encoding='utf-8') as handle:\n"
            "        handle.write(raw)\n"
            "    line = raw.strip()\n"
            "    if 'HEX(DATABASE())' in line:\n"
            "        print('6E756F6E6F5F74657374\\t"
            "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee', flush=True)\n"
            "    elif 'GET_LOCK' in line:\n"
            "        print(lock_result, flush=True)\n"
            "    elif line.startswith('SELECT CONVERT(X'):\n"
            "        match = re.search(\"X'([0-9a-f]+)'\", line)\n"
            "        if match:\n"
            "            print(bytes.fromhex(match.group(1)).decode(), flush=True)\n"
            "    elif line == 'quit':\n"
            "        break\n",
            encoding="utf-8",
        )
        mysql_bin.chmod(0o700)
        return MySqlClient(
            self.defaults_file,
            expected_schema="nuono_test",
            expected_host="db.internal",
            expected_port=3307,
            mysql_bin=str(mysql_bin),
            execution_timeout_seconds=5,
        )


if __name__ == "__main__":
    unittest.main()
