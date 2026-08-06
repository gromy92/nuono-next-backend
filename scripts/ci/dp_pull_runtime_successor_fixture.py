from __future__ import annotations

import re


SOURCE_TABLES = {
    "003_product_management_v1.sql": (
        "product_management_id_sequence",
        "logical_store",
        "logical_store_site",
        "product_master",
        "product_variant",
        "product_site_offer",
    ),
    "143_official_warehouse_statistics.sql": (
        "official_warehouse_inventory_sync_batch",
        "official_warehouse_inventory_snapshot_line",
    ),
    "160_noon_advertising_read_model.sql": (
        "noon_ad_id_sequence",
        "noon_ad_report_batch",
        "noon_ad_campaign_fact",
        "noon_ad_query_fact",
    ),
}
SUPPORT_COLUMNS = {
    "logical_store": {
        "owner_user_id": "BIGINT NOT NULL",
        "is_deleted": "BIT(1) DEFAULT b'0'",
    },
    "logical_store_site": {
        "logical_store_id": "BIGINT NOT NULL",
        "store_code": "VARCHAR(100) NOT NULL",
        "site": "VARCHAR(20) NOT NULL",
        "is_deleted": "BIT(1) DEFAULT b'0'",
    },
    "product_master": {
        "logical_store_id": "BIGINT NOT NULL",
        "sku_parent": "VARCHAR(100) NOT NULL",
        "brand_cache": "VARCHAR(200) DEFAULT NULL",
        "title_cache": "VARCHAR(500) DEFAULT NULL",
        "title_cn_cache": "VARCHAR(500) DEFAULT NULL",
        "is_deleted": "BIT(1) DEFAULT b'0'",
    },
    "product_variant": {
        "product_master_id": "BIGINT NOT NULL",
        "child_sku": "VARCHAR(100) DEFAULT NULL",
        "partner_sku": "VARCHAR(100) NOT NULL",
        "is_deleted": "BIT(1) DEFAULT b'0'",
    },
    "product_site_offer": {
        "variant_id": "BIGINT NOT NULL",
        "site_id": "BIGINT NOT NULL",
        "psku_code": "VARCHAR(100) DEFAULT NULL",
        "is_deleted": "BIT(1) DEFAULT b'0'",
    },
}


class SuccessorFixture:
    def __init__(self) -> None:
        self.created_tables: list[str] = []
        self.added_columns: list[tuple[str, str]] = []
        self.sequence_values: list[tuple[str, str, str | None]] = []

    def cleanup(self, database) -> None:
        for table, key, next_id in self.sequence_values:
            if next_id is None:
                database.client.execute(
                    f"DELETE FROM `{table}` WHERE sequence_name='{key}';"
                )
            else:
                database.client.execute(
                    f"UPDATE `{table}` SET next_id={next_id} "
                    f"WHERE sequence_name='{key}';"
                )
        for table, column in reversed(self.added_columns):
            if _column_exists(database, table, column):
                database.client.execute(
                    f"ALTER TABLE `{table}` DROP COLUMN `{column}`;"
                )
        if self.created_tables:
            database.client.execute(
                "SET FOREIGN_KEY_CHECKS=0;"
                + "".join(
                    f"DROP TABLE IF EXISTS `{table}`;"
                    for table in reversed(self.created_tables)
                )
                + "SET FOREIGN_KEY_CHECKS=1;"
            )


def prepare_successor_fixture(database, resources) -> SuccessorFixture:
    fixture = SuccessorFixture()
    init_root = resources / "init"
    for source_name, tables in SOURCE_TABLES.items():
        source = (init_root / source_name).read_text(encoding="utf-8")
        for table in tables:
            if not _table_exists(database, table):
                database.client.execute(_create_table_statement(source, table))
                fixture.created_tables.append(table)
    for table, columns in SUPPORT_COLUMNS.items():
        for column, definition in columns.items():
            if not _column_exists(database, table, column):
                database.client.execute(
                    f"ALTER TABLE `{table}` ADD COLUMN `{column}` {definition};"
                )
                fixture.added_columns.append((table, column))
    _remember_sequences(database, fixture)
    _assert_legacy_shapes(database)
    return fixture


def _remember_sequences(database, fixture) -> None:
    keys = {
        "product_management_id_sequence": (
            "official_warehouse_inventory_snapshot_line",
        ),
        "noon_ad_id_sequence": (
            "noon_ad_report_batch",
            "noon_ad_campaign_fact",
            "noon_ad_query_fact",
        ),
    }
    for table, sequence_keys in keys.items():
        if table in fixture.created_tables:
            continue
        for key in sequence_keys:
            value = database.client.execute_readonly(
                f"SELECT next_id FROM `{table}` WHERE sequence_name='{key}';"
            )
            fixture.sequence_values.append((table, key, value or None))


def _create_table_statement(source: str, table: str) -> str:
    match = re.search(
        rf"CREATE TABLE IF NOT EXISTS `{re.escape(table)}` \(.*?\) "
        r"ENGINE=InnoDB DEFAULT CHARSET=utf8mb4(?: COLLATE=[^;]+)?;",
        source,
        re.DOTALL,
    )
    if match is None:
        raise AssertionError(f"historical fixture table missing: {table}")
    return match.group(0)


def _table_exists(database, table: str) -> bool:
    return database.client.execute_readonly(
        "SELECT COUNT(*) FROM information_schema.tables "
        "WHERE table_schema=DATABASE() "
        f"AND table_name='{table}' AND table_type='BASE TABLE';"
    ) == "1"


def _column_exists(database, table: str, column: str) -> bool:
    return database.client.execute_readonly(
        "SELECT COUNT(*) FROM information_schema.columns "
        "WHERE table_schema=DATABASE() "
        f"AND table_name='{table}' AND column_name='{column}';"
    ) == "1"


def _assert_legacy_shapes(database) -> None:
    expected = {
        "official_warehouse_inventory_sync_batch": 21,
        "official_warehouse_inventory_snapshot_line": 35,
        "noon_ad_report_batch": 18,
        "noon_ad_campaign_fact": 33,
        "noon_ad_query_fact": 31,
    }
    for table, minimum in expected.items():
        actual = int(
            database.client.execute_readonly(
                "SELECT COUNT(*) FROM information_schema.columns "
                "WHERE table_schema=DATABASE() "
                f"AND table_name='{table}';"
            )
        )
        if actual < minimum:
            raise AssertionError(
                f"legacy fixture {table} has {actual} columns, expected >= {minimum}"
            )
