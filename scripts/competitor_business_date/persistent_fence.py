"""Transactional lifecycle for the database-backed correction maintenance fence."""
from __future__ import annotations

import re
from typing import Any

from .mysql_cli import MysqlCli
from .table_contracts import mysql_text_literal


FENCE_NAME = "HISTORICAL_BUSINESS_DATE_CORRECTION"
FENCE_TABLE = "operations_competitor_correction_writer_fence"


class PersistentFenceError(RuntimeError):
    pass


def read_fence(mysql: MysqlCli) -> dict[str, Any]:
    return mysql.query_one_json(_status_sql())


def assert_fence_active(
    mysql: MysqlCli,
    *,
    generation: int,
    run_id: str,
) -> None:
    _validate_identity(generation, run_id)
    row = read_fence(mysql)
    expected = {
        "fence_name": FENCE_NAME,
        "generation": generation,
        "fence_status": "ACTIVE",
        "operation_run_id": run_id,
    }
    if (
        any(row.get(key) != value for key, value in expected.items())
        or not row.get("activated_by")
        or not row.get("activated_at")
        or row.get("reopened_by") is not None
        or row.get("reopened_at") is not None
    ):
        raise PersistentFenceError(
            "persistent maintenance fence is not ACTIVE for the exact run/generation"
        )


def activate_fence(
    mysql: MysqlCli,
    *,
    generation: int,
    run_id: str,
    actor: str,
    timeout_seconds: int = 300,
) -> str:
    _validate_identity(generation, run_id)
    _validate_actor(actor)
    expected = (
        "COMPETITOR_CORRECTION_FENCE_ACTIVATED"
        f"|generation={generation}|run_id={run_id}"
    )
    output = mysql.run_script(
        _activation_sql(generation, run_id, actor, expected),
        timeout_seconds=timeout_seconds,
    )
    _require_exact_output(output, expected)
    assert_fence_active(mysql, generation=generation, run_id=run_id)
    return expected


def reopen_fence(
    mysql: MysqlCli,
    *,
    generation: int,
    run_id: str,
    actor: str,
    timeout_seconds: int = 300,
) -> str:
    _validate_identity(generation, run_id)
    _validate_actor(actor)
    expected = (
        "COMPETITOR_CORRECTION_FENCE_REOPENED"
        f"|generation={generation}|run_id={run_id}"
    )
    output = mysql.run_script(
        _reopen_sql(generation, run_id, actor, expected),
        timeout_seconds=timeout_seconds,
    )
    _require_exact_output(output, expected)
    row = read_fence(mysql)
    if (
        row.get("generation") != generation
        or row.get("fence_status") != "OPEN"
        or row.get("operation_run_id") != run_id
        or not row.get("activated_by")
        or not row.get("activated_at")
        or row.get("reopened_by") != actor
        or not row.get("reopened_at")
    ):
        raise PersistentFenceError("persistent fence did not reopen exactly")
    return expected


def _status_sql() -> str:
    return f"""
SELECT REPLACE(TO_BASE64(CAST(JSON_OBJECT(
  'fence_name', `fence_name`,
  'generation', `generation`,
  'fence_status', `fence_status`,
  'operation_run_id', `operation_run_id`,
  'activated_by', `activated_by`,
  'activated_at', CAST(`activated_at` AS CHAR),
  'reopened_by', `reopened_by`,
  'reopened_at', CAST(`reopened_at` AS CHAR)
) AS CHAR CHARACTER SET utf8mb4)), '\\n', '')
FROM `{FENCE_TABLE}`
WHERE `fence_name` = '{FENCE_NAME}';
"""


def _activation_sql(
    generation: int,
    run_id: str,
    actor: str,
    sentinel: str,
) -> str:
    return _transaction_prefix() + f"""
SET @cbd_expected_generation := {generation - 1};
SET @cbd_current_generation := NULL;
SET @cbd_current_status := NULL;
SELECT `generation`, `fence_status`
INTO @cbd_current_generation, @cbd_current_status
FROM `{FENCE_TABLE}`
WHERE `fence_name` = '{FENCE_NAME}'
FOR UPDATE;
INSERT INTO `_cbd_fence_guard` VALUES (
  IF(@cbd_current_status = 'OPEN'
     AND @cbd_current_generation = @cbd_expected_generation, 1, 0)
);
UPDATE `{FENCE_TABLE}`
SET `generation` = {generation},
    `fence_status` = 'ACTIVE',
    `operation_run_id` = {mysql_text_literal(run_id)},
    `activated_by` = {mysql_text_literal(actor)},
    `activated_at` = NOW(),
    `reopened_by` = NULL,
    `reopened_at` = NULL
WHERE `fence_name` = '{FENCE_NAME}'
  AND `fence_status` = 'OPEN'
  AND `generation` = @cbd_expected_generation;
INSERT INTO `_cbd_fence_guard` VALUES (IF(ROW_COUNT() = 1, 1, 0));
COMMIT;
SELECT {mysql_text_literal(sentinel)};
"""


def _reopen_sql(
    generation: int,
    run_id: str,
    actor: str,
    sentinel: str,
) -> str:
    run_literal = mysql_text_literal(run_id)
    return _transaction_prefix() + f"""
SET @cbd_current_generation := NULL;
SET @cbd_current_status := NULL;
SET @cbd_current_run_id := NULL;
SELECT `generation`, `fence_status`, `operation_run_id`
INTO @cbd_current_generation, @cbd_current_status, @cbd_current_run_id
FROM `{FENCE_TABLE}`
WHERE `fence_name` = '{FENCE_NAME}'
FOR UPDATE;
INSERT INTO `_cbd_fence_guard` VALUES (
  IF(@cbd_current_status = 'ACTIVE'
     AND @cbd_current_generation = {generation}
     AND BINARY @cbd_current_run_id = BINARY {run_literal}, 1, 0)
);
UPDATE `{FENCE_TABLE}`
SET `fence_status` = 'OPEN',
    `reopened_by` = {mysql_text_literal(actor)},
    `reopened_at` = NOW()
WHERE `fence_name` = '{FENCE_NAME}'
  AND `fence_status` = 'ACTIVE'
  AND `generation` = {generation}
  AND BINARY `operation_run_id` = BINARY {run_literal};
INSERT INTO `_cbd_fence_guard` VALUES (IF(ROW_COUNT() = 1, 1, 0));
COMMIT;
SELECT {mysql_text_literal(sentinel)};
"""


def _transaction_prefix() -> str:
    return """
SET SESSION innodb_lock_wait_timeout = 300;
SET SESSION lock_wait_timeout = 300;
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
START TRANSACTION;
CREATE TEMPORARY TABLE `_cbd_fence_guard` (
  `must_equal_one` TINYINT NOT NULL CHECK (`must_equal_one` = 1)
) ENGINE=InnoDB;
"""


def _validate_identity(generation: int, run_id: str) -> None:
    if isinstance(generation, bool) or not isinstance(generation, int) or generation <= 0:
        raise PersistentFenceError("fence generation must be a positive integer")
    if not isinstance(run_id, str) or not re.fullmatch(
        r"[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}", run_id
    ):
        raise PersistentFenceError("fence run ID is invalid")


def _validate_actor(actor: str) -> None:
    if not isinstance(actor, str) or not actor.strip() or len(actor) > 128:
        raise PersistentFenceError("fence actor is invalid")


def _require_exact_output(output: str, expected: str) -> None:
    lines = [line.strip() for line in output.splitlines() if line.strip()]
    if lines != [expected]:
        raise PersistentFenceError("fence mutation did not return its exact sentinel")
