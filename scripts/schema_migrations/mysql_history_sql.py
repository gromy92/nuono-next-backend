from __future__ import annotations

from schema_migrations.model import Migration, MigrationState
from schema_migrations.mysql_support import sql_literal

HISTORY_TABLE = "nuono_schema_migration"
ATTEMPT_TABLE = "nuono_schema_migration_attempt"


def bootstrap_statements(
    migration: Migration,
    state: str,
    release_commit: str,
    installed_by: str,
) -> tuple[tuple[str, int], ...]:
    key = sql_literal(migration.key)
    checksum = sql_literal(migration.checksum)
    postcheck = sql_literal(migration.postcheck_checksum)
    commit = sql_literal(release_commit)
    installer = sql_literal(installed_by)
    attempt_sql = (
        f"INSERT INTO {ATTEMPT_TABLE} "
        "(migration_key, attempt_no, checksum_sha256, postcheck_sha256, "
        "state, operation, release_commit, started_at, finished_at, installed_by) "
        f"VALUES ({key}, 1, {checksum}, {postcheck}, "
        f"{sql_literal(state)}, 'BOOTSTRAP', {commit}, UTC_TIMESTAMP(6), "
        f"UTC_TIMESTAMP(6), {installer});"
    )
    history_sql = (
        f"INSERT INTO {HISTORY_TABLE} "
        "(migration_key, script_path, checksum_sha256, postcheck_sha256, "
        "state, release_commit, attempt_no, started_at, finished_at, installed_by) "
        f"VALUES ({key}, {sql_literal(migration.script_path.as_posix())}, "
        f"{checksum}, {postcheck}, {sql_literal(state)}, {commit}, 1, "
        f"UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), {installer});"
    )
    return ((attempt_sql, 1), (history_sql, 1))


def begin_statements(
    migration: Migration,
    release_commit: str,
    installed_by: str,
    operation: str,
    attempt_no: int,
    existing: MigrationState | None,
) -> tuple[tuple[str, int], ...]:
    key = sql_literal(migration.key)
    checksum = sql_literal(migration.checksum)
    postcheck = sql_literal(migration.postcheck_checksum)
    commit = sql_literal(release_commit)
    installer = sql_literal(installed_by)
    attempt_sql = (
        f"INSERT INTO {ATTEMPT_TABLE} "
        "(migration_key, attempt_no, checksum_sha256, postcheck_sha256, "
        "state, operation, release_commit, started_at, installed_by) VALUES "
        f"({key}, {attempt_no}, {checksum}, {postcheck}, 'APPLYING', "
        f"{sql_literal(operation)}, {commit}, UTC_TIMESTAMP(6), {installer});"
    )
    if existing is None:
        history_sql = (
            f"INSERT INTO {HISTORY_TABLE} "
            "(migration_key, script_path, checksum_sha256, postcheck_sha256, "
            "state, release_commit, attempt_no, started_at, finished_at, "
            "installed_by, error_code, error_digest, error_summary) VALUES "
            f"({key}, {sql_literal(migration.script_path.as_posix())}, "
            f"{checksum}, {postcheck}, 'APPLYING', {commit}, {attempt_no}, "
            f"UTC_TIMESTAMP(6), NULL, {installer}, NULL, NULL, NULL);"
        )
    else:
        history_sql = (
            f"UPDATE {HISTORY_TABLE} SET "
            f"script_path={sql_literal(migration.script_path.as_posix())}, "
            f"state='APPLYING', release_commit={commit}, attempt_no={attempt_no}, "
            f"started_at=UTC_TIMESTAMP(6), finished_at=NULL, "
            f"installed_by={installer}, error_code=NULL, error_digest=NULL, "
            f"error_summary=NULL WHERE migration_key={key} AND state='FAILED' "
            f"AND attempt_no={existing.attempt_no} "
            f"AND checksum_sha256={checksum} AND postcheck_sha256={postcheck};"
        )
    return ((attempt_sql, 1), (history_sql, 1))


def reconcile_statements(
    migration: Migration,
    blocked_attempt_no: int,
    release_commit: str,
    installed_by: str,
) -> tuple[tuple[str, int], ...]:
    attempt_no = blocked_attempt_no + 1
    key = sql_literal(migration.key)
    checksum = sql_literal(migration.checksum)
    postcheck = sql_literal(migration.postcheck_checksum)
    commit = sql_literal(release_commit)
    installer = sql_literal(installed_by)
    attempt_sql = (
        f"INSERT INTO {ATTEMPT_TABLE} "
        "(migration_key, attempt_no, checksum_sha256, postcheck_sha256, "
        "state, operation, reconciles_attempt_no, release_commit, "
        "started_at, finished_at, installed_by) VALUES "
        f"({key}, {attempt_no}, {checksum}, {postcheck}, 'APPLIED', "
        f"'RECONCILE', {blocked_attempt_no}, {commit}, UTC_TIMESTAMP(6), "
        f"UTC_TIMESTAMP(6), {installer});"
    )
    history_sql = (
        f"UPDATE {HISTORY_TABLE} SET state='APPLIED', release_commit={commit}, "
        f"attempt_no={attempt_no}, finished_at=UTC_TIMESTAMP(6), "
        f"installed_by={installer}, error_code=NULL, error_digest=NULL, "
        f"error_summary=NULL WHERE migration_key={key} "
        f"AND attempt_no={blocked_attempt_no} "
        f"AND state IN ('APPLYING', 'FAILED') "
        f"AND checksum_sha256={checksum} AND postcheck_sha256={postcheck};"
    )
    return ((attempt_sql, 1), (history_sql, 1))


def finish_sql(
    migration: Migration,
    attempt_no: int,
    state: str,
    error_code: str | None,
    error_digest: str | None,
    error_summary: str | None,
) -> str:
    key = sql_literal(migration.key)
    checksum = sql_literal(migration.checksum)
    postcheck = sql_literal(migration.postcheck_checksum)
    code = "NULL" if error_code is None else sql_literal(error_code)
    digest = "NULL" if error_digest is None else sql_literal(error_digest)
    summary = "NULL" if error_summary is None else sql_literal(error_summary)
    return (
        f"UPDATE {HISTORY_TABLE} h JOIN {ATTEMPT_TABLE} a "
        "ON a.migration_key=h.migration_key AND a.attempt_no=h.attempt_no "
        f"SET h.state={sql_literal(state)}, h.finished_at=UTC_TIMESTAMP(6), "
        f"h.error_code={code}, h.error_digest={digest}, h.error_summary={summary}, "
        f"a.state={sql_literal(state)}, a.finished_at=UTC_TIMESTAMP(6), "
        f"a.error_code={code}, a.error_digest={digest}, a.error_summary={summary} "
        f"WHERE h.migration_key={key} AND h.attempt_no={attempt_no} "
        "AND h.state='APPLYING' AND a.state='APPLYING' "
        f"AND h.checksum_sha256={checksum} AND h.postcheck_sha256={postcheck};\n"
        "SELECT ROW_COUNT();\n"
    )
