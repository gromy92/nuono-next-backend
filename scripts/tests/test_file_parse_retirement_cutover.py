from __future__ import annotations

import sys
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from release_file_parse_retirement_cutover import (  # noqa: E402
    MIGRATION_KEY,
    build_file_parse_retirement_cutover_script,
)


class FileParseRetirementCutoverTest(unittest.TestCase):
    def script(self):
        return build_file_parse_retirement_cutover_script(
            staged_jar="/stage/new.jar",
            expected_jar_sha256="a" * 64,
            expected_active_jar_sha256="b" * 64,
            expected_commit="c" * 40,
            active_slot="blue",
            target_slot="green",
            active_port=18087,
            target_port=18088,
            maintenance_port=18089,
            nginx_upstream_file="/etc/nginx/conf.d/upstream.conf",
            release_name="file-parse-retirement",
            external_health_url="https://example.test/actuator/health",
            app_dir="/srv/nuono",
            mysql_defaults_file="/srv/nuono/.migration.cnf",
            expected_schema="nuono",
            expected_db_host="127.0.0.1",
            expected_db_port=3306,
        )

    def test_migration_runs_only_after_old_runtime_is_fully_drained(self):
        script = self.script()

        stop = script.index('OLD_STOPPED=1')
        zero_jvms = script.index('[ "$(backend_jvm_count)" = 0 ]', stop)
        migration = script.index('run_runtime_drain_migration', zero_jvms)
        new_start = script.index('NEW_START_ATTEMPTED=1', migration)
        self.assertLess(stop, zero_jvms)
        self.assertLess(zero_jvms, migration)
        self.assertLess(migration, new_start)

    def test_runner_uses_exact_same_session_ack_and_governed_jar(self):
        script = self.script()

        self.assertIn('--governed-jar-sha256 "$EXPECTED_JAR_SHA256"', script)
        self.assertIn('--approve-managed "$DRAIN_MIGRATION_KEY"', script)
        self.assertIn('--approve-runtime-drain "$DRAIN_MIGRATION_KEY"', script)
        self.assertIn(f"DRAIN_MIGRATION_KEY={MIGRATION_KEY}", script)
        self.assertIn('stat -c \'%a\' "$DRAIN_MYSQL_CNF"', script)

    def test_failure_after_migration_start_never_restarts_old_jar(self):
        script = self.script()
        guard = script.index('if [ "$DRAINED_RUNTIME_MIGRATION_STARTED" = 1 ]')
        restart = script.index('restart_old_runtime || true', guard)
        forbidden = script.index('emit SAFE_OLD_JAR_ROLLBACK FORBIDDEN', guard)

        self.assertLess(forbidden, restart)
        guarded_block = script[guard:restart]
        self.assertNotIn('restart_old_runtime', guarded_block)
        self.assertIn('REPAIR_FORWARD_REQUIRED', guarded_block)

    def test_rejects_unbound_database_or_release_identity(self):
        kwargs = dict(
            staged_jar="/stage/new.jar", expected_jar_sha256="a" * 64,
            expected_active_jar_sha256="b" * 64, expected_commit="bad",
            active_slot="blue", target_slot="green", active_port=1,
            target_port=2, maintenance_port=3, nginx_upstream_file="/n",
            release_name="r", external_health_url="https://e/h",
            app_dir="/a", mysql_defaults_file="relative.cnf",
            expected_schema="nuono", expected_db_host="db", expected_db_port=3306,
        )
        with self.assertRaisesRegex(ValueError, "expected_commit"):
            build_file_parse_retirement_cutover_script(**kwargs)
        kwargs["expected_commit"] = "c" * 40
        with self.assertRaisesRegex(ValueError, "absolute"):
            build_file_parse_retirement_cutover_script(**kwargs)


if __name__ == "__main__":
    unittest.main()
