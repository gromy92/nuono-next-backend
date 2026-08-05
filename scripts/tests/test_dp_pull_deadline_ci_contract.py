import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/ci.yml"
HANDSHAKE_TEST = ROOT / (
    "src/test/java/com/nuono/next/datapull/orchestration/"
    "DataPullConnectionHandshakeMySqlTest.java"
)
EXACT_PATH_TEST = ROOT / (
    "src/test/java/com/nuono/next/procurement/aliorder/datapull/"
    "Ali1688Dp10ExactPathMySqlTest.java"
)
EXACT_PATH_CONTEXT = ROOT / (
    "src/test/java/com/nuono/next/procurement/aliorder/datapull/"
    "Ali1688Dp10ExactPathMySqlContext.java"
)
EXACT_PATH_FIXTURE = ROOT / (
    "src/test/java/com/nuono/next/procurement/aliorder/datapull/"
    "Ali1688Dp10ExactPathFixture.java"
)
EXACT_PATH_DATABASE = ROOT / (
    "src/test/java/com/nuono/next/procurement/aliorder/datapull/"
    "Ali1688Dp10ExactPathMySqlDatabase.java"
)
TOMBSTONE_TEST = ROOT / (
    "src/test/java/com/nuono/next/procurement/aliorder/datapull/"
    "Ali1688Dp10TombstoneMySqlTest.java"
)
FACT_PERSISTENCE_SUPPORT = ROOT / (
    "src/test/java/com/nuono/next/procurement/aliorder/"
    "Ali1688Dp10FactPersistenceTestSupport.java"
)
DEADLINE_SUPPORT = ROOT / (
    "src/test/java/com/nuono/next/datapull/orchestration/"
    "DataPullDeadlineMySqlTestSupport.java"
)
GENERIC_DEADLINE_TEST = ROOT / (
    "src/test/java/com/nuono/next/datapull/orchestration/"
    "DataPullDatabaseDeadlineMySqlTest.java"
)
EXACT_PATH_SCHEMA_PREPARE = ROOT / "scripts/ci/prepare_dp10_exact_path_mysql.py"


class DataPullDeadlineCiContractTest(unittest.TestCase):
    def setUp(self):
        self.workflow = WORKFLOW.read_text(encoding="utf-8")

    def test_dedicated_rds_compatible_mysql_gate_is_fail_closed(self):
        required = (
            "Verify migrations and DP deadlines against RDS-compatible MySQL 8 policy",
            "mysql:8.0.36",
            "--disabled-storage-engines=MyISAM,MEMORY,ARCHIVE",
            "NUONO_DP_DEADLINE_MYSQL_URL: jdbc:mysql://127.0.0.1:3307/"
            "nuono_schema_migration_rds_ci",
            "NUONO_DP_DEADLINE_MYSQL_USERNAME: migration_ci",
            "NUONO_DP_DEADLINE_MYSQL_PASSWORD: migration_ci",
            'mvn -q -Dtest="$dp_deadline_tests" test',
        )
        for marker in required:
            self.assertIn(marker, self.workflow)

    def test_gate_names_every_real_fault_and_phase_contract(self):
        expected_tests = (
            "DataPullDatabaseDeadlineMySqlTest",
            "DataPullDatabaseDeadlineConcurrencyMySqlTest",
            "DataPullConnectionHandshakeMySqlTest",
            "DataPullTransactionBeginNetworkMySqlTest",
            "DataPullRuntimeSchedulerPhaseMySqlTest",
            "DataPullRuntimeIndependentPhaseBudgetTest",
            "RuntimeExecutorDeadlineTest",
            "DataPullRuntimeSchedulerTest",
            "DataPullAdvanceDeadlineRaceTest",
            "Ali1688Dp10ExactPathMySqlTest",
            "Ali1688Dp10TombstoneMySqlTest",
        )
        assignment = next(
            line for line in self.workflow.splitlines()
            if line.strip().startswith('dp_deadline_tests="')
        )
        for test_name in expected_tests:
            self.assertIn(test_name, assignment)

    def test_dp10_capacity_gate_uses_migrated_production_mapper_path(self):
        self.assertIn(
            "python3 scripts/ci/prepare_dp10_exact_path_mysql.py",
            self.workflow,
        )
        sources = {
            path.name: path.read_text(encoding="utf-8")
            for path in (
                EXACT_PATH_TEST,
                EXACT_PATH_CONTEXT,
                EXACT_PATH_FIXTURE,
                EXACT_PATH_DATABASE,
                TOMBSTONE_TEST,
                FACT_PERSISTENCE_SUPPORT,
                DEADLINE_SUPPORT,
            )
        }
        source = "\n".join(sources.values())
        for marker in (
            "Ali1688Dp10MyBatisPageStageStore",
            "Ali1688Dp10FactTransaction",
            "Ali1688HistoricalOrderFactPersistence",
            "Ali1688HistoricalOrderMapper",
            "Ali1688Dp10FactLookupMapper",
            "DataPullDeadlineAwareDataSource",
            "DataPullMyBatisDeadlineInterceptor",
            "@EnableTransactionManagement(proxyTargetClass = true)",
            "stageList",
            "advance",
            "100",
            "20",
            "AopUtils.isAopProxy(context.factTransaction())",
        ):
            self.assertIn(marker, source)
        self.assertNotIn("FixtureMapper", source)
        self.assertIn(
            "return new Ali1688HistoricalOrderFactPersistence(mapper, factLookupMapper)",
            sources[FACT_PERSISTENCE_SUPPORT.name],
        )
        self.assertIn(
            "return new DataPullDeadlineAwareDataSource(source)",
            sources[DEADLINE_SUPPORT.name],
        )
        self.assertIn("stageStore.stageList", sources[EXACT_PATH_FIXTURE.name])
        self.assertIn("facts.advance(command)", sources[EXACT_PATH_FIXTURE.name])
        self.assertIn(
            "for (int index = 0; index < 11; index++)",
            sources[EXACT_PATH_FIXTURE.name],
        )
        self.assertIn(
            "applyCursor(task.getId(), 1L)).isEqualTo(10)",
            sources[EXACT_PATH_TEST.name],
        )
        self.assertIn(
            "applyCursor(task.getId(), 1L)).isEqualTo(11)",
            sources[EXACT_PATH_TEST.name],
        )
        for environment_name in (
            "NUONO_DP10_EXACT_MYSQL_URL",
            "NUONO_DP10_EXACT_MYSQL_USERNAME",
            "NUONO_DP10_EXACT_MYSQL_PASSWORD",
        ):
            self.assertIn(environment_name, sources[EXACT_PATH_TEST.name])
        self.assertNotIn(
            "NUONO_DP_DEADLINE_MYSQL", sources[EXACT_PATH_TEST.name]
        )
        self.assertIn(
            'setActiveProfiles("local-db")', sources[EXACT_PATH_CONTEXT.name]
        )
        self.assertIn(
            "DataPullExecutionMode.RUNTIME.name()", sources[EXACT_PATH_CONTEXT.name]
        )
        self.assertIn(
            "setMapUnderscoreToCamelCase(true)", sources[EXACT_PATH_CONTEXT.name]
        )
        for marker in (
            "DP10_ORDER_HEADER_MANUALLY_DELETED",
            "headerAudit(deletedOrderNo)",
            "stageOutcome(task.getId(), 5L, nextOrderNo)",
            "Ali1688Dp10FactAdvance.COMPLETE",
            "database.highWater()",
        ):
            self.assertIn(marker, sources[TOMBSTONE_TEST.name])

        generic = GENERIC_DEADLINE_TEST.read_text(encoding="utf-8")
        self.assertNotIn(
            "hundredRowPageAndTwentyFactSliceFitTheTenSecondTransactionBudget",
            generic,
        )

    def test_dp10_schema_prepare_is_pinned_to_real_migrations(self):
        source = EXACT_PATH_SCHEMA_PREPARE.read_text(encoding="utf-8")
        for marker in (
            "003_product_management_v1.sql",
            "058_noon_pull_foundation.sql",
            "071_procurement_ali1688_historical_order_sync.sql",
            "092_procurement_ali1688_order_cleanup_audit.sql",
            "127_procurement_ali1688_history_read_model.sql",
            "243_dp_pull_runtime.sql",
            "EXPECTED_SOURCE_DIGESTS",
            "procurement_ali1688_order_authorization",
            "procurement_ali1688_order_header",
            "procurement_ali1688_order_item",
            "procurement_ali1688_order_logistics",
            "NUONO_DP10_EXACT_MYSQL_DEFAULTS_FILE",
            "assert_read_model_compatibility",
            "PRODUCT_SEQUENCE_FLOORS",
            "information_schema.statistics",
            "runtime_tables | set(LEGACY_BASE_TABLE_SOURCES)",
        ):
            self.assertIn(marker, source)
        self.assertNotIn("CREATE TABLE", source)
        evolution = source.split("EVOLUTION_MIGRATIONS = (", 1)[1].split(")", 1)[0]
        self.assertIn("092_procurement_ali1688_order_cleanup_audit.sql", evolution)
        self.assertIn("243_dp_pull_runtime.sql", evolution)
        self.assertNotIn("127_procurement_ali1688_history_read_model.sql", evolution)

    def test_dp10_exact_path_uses_an_isolated_rds_policy_schema(self):
        for marker in (
            "CREATE DATABASE nuono_dp10_exact_rds_ci",
            "GRANT ALL PRIVILEGES ON nuono_dp10_exact_rds_ci.*",
            "database=nuono_dp10_exact_rds_ci",
            "NUONO_DP10_EXACT_MYSQL_DEFAULTS_FILE",
            "NUONO_DP10_EXACT_EXPECTED_SCHEMA: nuono_dp10_exact_rds_ci",
            "NUONO_DP10_EXACT_MYSQL_URL: jdbc:mysql://127.0.0.1:3307/"
            "nuono_dp10_exact_rds_ci",
        ):
            self.assertIn(marker, self.workflow)

    def test_full_package_does_not_duplicate_the_mysql_fault_suite(self):
        package = self.workflow.split("      - name: Test and package", 1)[1]
        package = package.split("      - name:", 1)[0]
        self.assertNotIn("NUONO_DP_DEADLINE_MYSQL", package)
        self.assertEqual("run: mvn -q package", package.strip())

    def test_handshake_fixture_keeps_one_endpoint_during_recovery(self):
        source = HANDSHAKE_TEST.read_text(encoding="utf-8")
        self.assertIn("setJdbcUrl(target.through(proxy.port()))", source)
        self.assertIn("RecoveringHandshakeProxy", source)
        self.assertNotIn("AbstractDataSource", source)
        self.assertNotIn("DriverManager.getConnection", source)


if __name__ == "__main__":
    unittest.main()
