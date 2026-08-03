"""Build the legacy fixture from frozen historical migrations."""
from __future__ import annotations

from pathlib import Path

from competitor_business_date.mysql_cli import MysqlCli, MysqlCliError
from competitor_business_date.preflight import (
    read_schema_fingerprint,
    validate_target_schema,
)


FENCE_TABLE = "operations_competitor_correction_writer_fence"
SEQUENCE_NAME = "operations_competitor_product_change_event"
CONTRACT_TABLES = (
    "operations_competitor_product_snapshot",
    "operations_competitor_product_change_event",
    "operations_competitor_rank_fact",
    "operations_competitor_keyword_run",
    "operations_competitor_analysis_id_sequence",
)
MIGRATIONS = (
    "240_operations_competitor_snapshot_active_uniqueness.sql",
    "241_operations_competitor_correction_writer_fence.sql",
)


def create_contract_tables(mysql: MysqlCli, backend_root: Path) -> None:
    migration_root = backend_root / "src/main/resources/db/init"
    historical = {
        "099": (migration_root / "099_operations_competitor_analysis.sql").read_text(
            encoding="utf-8"
        ),
        "103": (
            migration_root / "103_operations_competitor_product_snapshot_change.sql"
        ).read_text(encoding="utf-8"),
    }
    origins = {
        CONTRACT_TABLES[0]: "103",
        CONTRACT_TABLES[1]: "103",
        CONTRACT_TABLES[2]: "099",
        CONTRACT_TABLES[3]: "099",
        CONTRACT_TABLES[4]: "099",
    }
    statements = [
        _extract_create_table(historical[origins[name]], name)
        for name in CONTRACT_TABLES
    ]
    mysql.run_script("\n".join(statements))
    mysql.run_script(
        (
            migration_root
            / "104_operations_competitor_rank_fact_channel_scan_depth.sql"
        ).read_text(encoding="utf-8")
    )
    mysql.run_script(
        "INSERT INTO `operations_competitor_analysis_id_sequence` "
        "(`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`) VALUES "
        f"('{SEQUENCE_NAME}', 270000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);\n",
    )


def drop_contract_tables(mysql: MysqlCli) -> None:
    names = [FENCE_TABLE, *reversed(CONTRACT_TABLES)]
    mysql.run_script(
        "SET FOREIGN_KEY_CHECKS = 0;\n"
        + "\n".join(f"DROP TABLE IF EXISTS `{name}`;" for name in names)
        + "\nSET FOREIGN_KEY_CHECKS = 1;\n"
    )


def run_migrations(mysql: MysqlCli, backend_root: Path) -> None:
    for _ in range(2):
        for name in MIGRATIONS:
            run_migration(mysql, backend_root, name)
    rows, _ = read_schema_fingerprint(mysql)
    if validate_target_schema(rows, expected_schema=mysql.schema) != "TARGET":
        raise RuntimeError("migrations did not produce the exact TARGET schema")


def run_migration(mysql: MysqlCli, backend_root: Path, name: str) -> None:
    if name not in MIGRATIONS:
        raise RuntimeError(f"unmanaged fixture migration: {name}")
    path = backend_root / "src/main/resources/db/init" / name
    try:
        mysql.run_script(path.read_text(encoding="utf-8"), timeout_seconds=120)
    except MysqlCliError as error:
        if name != MIGRATIONS[1]:
            raise
        checks = mysql.run_script(
            "SELECT constraint_name, HEX(check_clause), "
            "HEX(REGEXP_REPLACE(REPLACE(REPLACE(LOWER(check_clause), "
            "'`', ''), '_utf8mb4', ''), '[[:space:]()]', '')) "
            "FROM information_schema.check_constraints "
            "WHERE constraint_schema=DATABASE() "
            "AND constraint_name LIKE 'chk_ops_comp_cwf_%' "
            "ORDER BY constraint_name;"
        ).strip()
        raise MysqlCliError(f"{error}; writer fence checks: {checks}") from error


def _extract_create_table(source: str, table_name: str) -> str:
    marker = f"CREATE TABLE IF NOT EXISTS `{table_name}`"
    start = source.find(marker)
    if start < 0:
        raise RuntimeError(f"historical migration lacks table: {table_name}")
    end = source.find(";\n", start)
    if end < 0:
        raise RuntimeError(f"historical table DDL is unterminated: {table_name}")
    return source[start : end + 1]
