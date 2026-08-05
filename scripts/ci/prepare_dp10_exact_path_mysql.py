from __future__ import annotations

import hashlib
import os
import re
import sys
from pathlib import Path


SCRIPT_ROOT = Path(__file__).resolve().parents[1]
REPOSITORY_ROOT = SCRIPT_ROOT.parent
if str(SCRIPT_ROOT) not in sys.path:
    sys.path.insert(0, str(SCRIPT_ROOT))

from schema_migrations.mysql_database import MySqlMigrationDatabase  # noqa: E402


INIT = REPOSITORY_ROOT / "src/main/resources/db/init"
POSTCHECK = REPOSITORY_ROOT / "src/main/resources/db/postcheck"

# A source change must be reviewed together with this exact-path fixture.
# Authoritative legacy tables are extracted byte-for-byte because running all of
# 071 would require unrelated menu/user seed domains. Migration 092 and runtime
# 243 execute whole; 127 stays a pinned, non-executed compatibility definition.
EXPECTED_SOURCE_DIGESTS = {
    "003_product_management_v1.sql":
        "9ec9573ab1b63ee370ede60639d7ea021ea403ef16dada8669be417f8f90c9a7",
    "058_noon_pull_foundation.sql":
        "a833ffeed0d7577d92de1b7c39c49689dc2d4f313ccc2caedf66f7141fa1efa3",
    "071_procurement_ali1688_historical_order_sync.sql":
        "aca14a649c943dc2b71f9b9a47799823defe2ceafd345fbcf02bb41d0f95dd05",
    "092_procurement_ali1688_order_cleanup_audit.sql":
        "78f619e671b4f63d1802c28bca1827cadf518cac1d7fbbcdc7cb241ee5d2f5fb",
    "127_procurement_ali1688_history_read_model.sql":
        "d4165cdd19d9b9db804c68bff18d1919bf9c5ff96acc7ba1c13f6ea35c2dc8ae",
    "243_dp_pull_runtime.sql":
        "b8676bae80cc5f5d261317bf3346b57d5dd3c12b79192ca0652b19f29cdb07e9",
}

LEGACY_BASE_TABLE_SOURCES = {
    "product_management_id_sequence": "003_product_management_v1.sql",
    "noon_pull_id_sequence": "058_noon_pull_foundation.sql",
    "procurement_ali1688_order_authorization":
        "071_procurement_ali1688_historical_order_sync.sql",
    "procurement_ali1688_order_header":
        "071_procurement_ali1688_historical_order_sync.sql",
    "procurement_ali1688_order_item":
        "071_procurement_ali1688_historical_order_sync.sql",
    "procurement_ali1688_order_logistics":
        "071_procurement_ali1688_historical_order_sync.sql",
}

FACT_TARGET_TABLES = (
    "procurement_ali1688_order_authorization",
    "procurement_ali1688_order_header",
    "procurement_ali1688_order_item",
    "procurement_ali1688_order_logistics",
)

PRODUCT_SEQUENCE_FLOORS = {
    "procurement_ali1688_order_header": 93_000,
    "procurement_ali1688_order_item": 94_000,
    "procurement_ali1688_order_logistics": 95_000,
}

EVOLUTION_MIGRATIONS = (
    "092_procurement_ali1688_order_cleanup_audit.sql",
    "243_dp_pull_runtime.sql",
)


def main() -> None:
    defaults_value = os.environ.get("NUONO_DP10_EXACT_MYSQL_DEFAULTS_FILE")
    if not defaults_value:
        raise SystemExit("NUONO_DP10_EXACT_MYSQL_DEFAULTS_FILE is required")
    expected_schema = os.environ.get(
        "NUONO_DP10_EXACT_EXPECTED_SCHEMA", "nuono_dp10_exact_rds_ci"
    )
    database = MySqlMigrationDatabase(
        Path(defaults_value),
        expected_schema=expected_schema,
        expected_host="127.0.0.1",
        expected_port=int(os.environ.get("NUONO_DP10_EXACT_EXPECTED_PORT", "3307")),
    )
    try:
        sources = verified_sources()
        assert_read_model_compatibility(sources)
        assert_targets_absent(
            database, sources["243_dp_pull_runtime.sql"]
        )
        for table, source_name in LEGACY_BASE_TABLE_SOURCES.items():
            database.client.execute(extract_create_table(sources[source_name], table))
        legacy = sources["071_procurement_ali1688_historical_order_sync.sql"]
        for sequence_name in (
            "procurement_ali1688_order_header",
            "procurement_ali1688_order_item",
            "procurement_ali1688_order_logistics",
        ):
            database.client.execute(extract_sequence_seed(legacy, sequence_name))
        for migration in EVOLUTION_MIGRATIONS:
            database.client.execute(sources[migration])
        assert_legacy_shapes(database, sources)
        assert_sequence_tables(database, sources)
        exact_243 = (POSTCHECK / "243_dp_pull_runtime.sql").read_text(
            encoding="utf-8"
        )
        if database.client.execute_readonly(exact_243) != "1":
            raise RuntimeError("migration 243 exact postcheck failed after DP10 setup")
    finally:
        database.close()


def verified_sources() -> dict[str, str]:
    sources: dict[str, str] = {}
    for name, expected in EXPECTED_SOURCE_DIGESTS.items():
        content = (INIT / name).read_text(encoding="utf-8")
        actual = hashlib.sha256(content.encode("utf-8")).hexdigest()
        if actual != expected:
            raise RuntimeError(
                f"DP10 exact-path migration source drift: {name} {actual}"
            )
        sources[name] = content
    return sources


def extract_create_table(source: str, table: str) -> str:
    prefix = r"CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS"
    pattern = re.compile(
        prefix + rf"\s+`{re.escape(table)}`\s*\(.*?\)\s*"
        r"ENGINE=InnoDB[^;]*;",
        re.DOTALL,
    )
    matches = pattern.findall(source)
    if len(matches) != 1:
        raise RuntimeError(f"expected one authoritative table definition for {table}")
    return matches[0]


def extract_sequence_seed(source: str, sequence_name: str) -> str:
    pattern = re.compile(
        r"INSERT\s+INTO\s+`product_management_id_sequence`\s*"
        r"\(`sequence_name`,\s*`next_id`,\s*`gmt_create`,\s*`gmt_updated`\)\s*"
        rf"SELECT\s+'{re.escape(sequence_name)}'.*?;",
        re.DOTALL,
    )
    matches = pattern.findall(source)
    if len(matches) != 1:
        raise RuntimeError(f"expected one authoritative sequence seed for {sequence_name}")
    return matches[0]


def assert_read_model_compatibility(sources: dict[str, str]) -> None:
    legacy = sources["071_procurement_ali1688_historical_order_sync.sql"]
    read_model = sources["127_procurement_ali1688_history_read_model.sql"]
    for table in (
        "procurement_ali1688_order_header",
        "procurement_ali1688_order_item",
    ):
        legacy_body = normalized_table_body(extract_create_table(legacy, table))
        read_model_body = normalized_table_body(
            extract_create_table(read_model, table)
        )
        if legacy_body != read_model_body:
            raise RuntimeError(
                f"DP10 writer table {table} drifted from migration 127 read model"
            )


def normalized_table_body(create_table: str) -> str:
    body = re.search(r"\((.*)\)\s*ENGINE=InnoDB", create_table, re.DOTALL)
    if body is None:
        raise RuntimeError("unable to parse authoritative InnoDB table body")
    return " ".join(body.group(1).split())


def assert_targets_absent(
    database: MySqlMigrationDatabase, runtime_migration: str
) -> None:
    runtime_tables = set(re.findall(
        r"CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+`?([A-Za-z0-9_]+)`?",
        runtime_migration,
        re.IGNORECASE,
    ))
    if not runtime_tables:
        raise RuntimeError("migration 243 contains no runtime table definitions")
    targets = sorted(runtime_tables | set(LEGACY_BASE_TABLE_SOURCES))
    quoted = ",".join(f"'{table}'" for table in targets)
    found = database.client.execute_readonly(
        "SELECT GROUP_CONCAT(table_name ORDER BY table_name SEPARATOR ',') "
        "FROM information_schema.tables WHERE table_schema=DATABASE() "
        f"AND table_name IN ({quoted});"
    )
    if found:
        raise RuntimeError(f"DP10 exact-path target schema is not isolated: {found}")


def assert_legacy_shapes(
    database: MySqlMigrationDatabase, sources: dict[str, str]
) -> None:
    base = sources["071_procurement_ali1688_historical_order_sync.sql"]
    audit = sources["092_procurement_ali1688_order_cleanup_audit.sql"]
    for table in FACT_TARGET_TABLES:
        expected = set(column_names(extract_create_table(base, table)))
        if table == "procurement_ali1688_order_header":
            expected.update(re.findall(r"ADD\s+COLUMN\s+`([^`]+)`", audit))
        actual_text = database.client.execute_readonly(
            "SELECT GROUP_CONCAT(column_name ORDER BY column_name SEPARATOR ',') "
            "FROM information_schema.columns WHERE table_schema=DATABASE() "
            f"AND table_name='{table}';"
        )
        actual = set(actual_text.split(",")) if actual_text else set()
        if actual != expected:
            missing = sorted(expected - actual)
            extra = sorted(actual - expected)
            raise RuntimeError(
                f"DP10 current schema drift for {table}: missing={missing}, extra={extra}"
            )


def assert_sequence_tables(
    database: MySqlMigrationDatabase, sources: dict[str, str]
) -> None:
    for table, source_name in (
        ("product_management_id_sequence", "003_product_management_v1.sql"),
        ("noon_pull_id_sequence", "058_noon_pull_foundation.sql"),
    ):
        expected = set(column_names(extract_create_table(sources[source_name], table)))
        actual_text = database.client.execute_readonly(
            "SELECT GROUP_CONCAT(column_name ORDER BY column_name SEPARATOR ',') "
            "FROM information_schema.columns WHERE table_schema=DATABASE() "
            f"AND table_name='{table}';"
        )
        actual = set(actual_text.split(",")) if actual_text else set()
        if actual != expected:
            raise RuntimeError(
                f"DP10 sequence table drift for {table}: "
                f"missing={sorted(expected - actual)}, extra={sorted(actual - expected)}"
            )
        primary_key = database.client.execute_readonly(
            "SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') "
            "FROM information_schema.statistics WHERE table_schema=DATABASE() "
            f"AND table_name='{table}' AND index_name='PRIMARY';"
        )
        if primary_key != "sequence_name":
            raise RuntimeError(
                f"DP10 sequence table {table} must key exactly by sequence_name"
            )

    for sequence_name, floor in PRODUCT_SEQUENCE_FLOORS.items():
        next_id = database.client.execute_readonly(
            "SELECT next_id FROM product_management_id_sequence "
            f"WHERE sequence_name='{sequence_name}';"
        )
        if not next_id or int(next_id) < floor:
            raise RuntimeError(
                f"DP10 required sequence seed missing or below floor: {sequence_name}"
            )
    task_next_id = database.client.execute_readonly(
        "SELECT next_id FROM noon_pull_id_sequence "
        "WHERE sequence_name='dp_pull_task';"
    )
    if not task_next_id or int(task_next_id) < 0:
        raise RuntimeError("migration 243 did not seed noon sequence dp_pull_task")


def column_names(create_table: str) -> tuple[str, ...]:
    return tuple(
        re.findall(r"^\s*`([^`]+)`\s+[A-Z]", create_table, re.MULTILINE)
    )


if __name__ == "__main__":
    main()
