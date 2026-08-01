from __future__ import annotations

import re
import sys

from ci.release_schema_mysql_forwarder_source_contract import execute_group_concat
from schema_migrations.model import MigrationError


def apply_with_diagnostics(runner, approvals, database, forwarder):
    try:
        return runner.apply(approved_managed=approvals)
    except MigrationError as error:
        if forwarder.key in str(error) and "postcheck returned false" in str(error):
            failed = failing_predicate_indexes(database, forwarder.postcheck_sql)
            print(
                "forwarder postcheck false predicate indexes: "
                + (",".join(failed) or "none"),
                file=sys.stderr,
            )
            print(
                "forwarder active-scope metadata: "
                + eligibility_expression_diagnostic(database),
                file=sys.stderr,
            )
            trigger_metadata, check_metadata = guard_metadata_diagnostics(database)
            print("forwarder trigger metadata: " + trigger_metadata, file=sys.stderr)
            print("forwarder check metadata: " + check_metadata, file=sys.stderr)
        raise


def failing_predicate_indexes(database, postcheck_sql):
    predicates = outer_if_predicates(postcheck_sql)
    diagnostics = ",".join(
        f"IF(({predicate}),NULL,'{index:03d}')"
        for index, predicate in enumerate(predicates, start=1)
    )
    output = execute_group_concat(
        database,
        f"SELECT CONCAT_WS(',',{diagnostics});",
    )
    return tuple(output.split(",")) if output else ()


def eligibility_expression_diagnostic(database):
    normalized = (
        "REPLACE(REGEXP_REPLACE(REGEXP_REPLACE(REPLACE(REPLACE(REPLACE(REPLACE("
        "LOWER(generation_expression),'`',''),CONCAT(CHAR(92),CHAR(39)),"
        "CHAR(39)),CONCAT(CHAR(92),'0'),'0'),'_utf8mb4',''),"
        "'((_binary|b)?''0''|0x00|0b0)','0'),'[()[:space:]]+',''),"
        "'charsetutf8mb4','')"
    )
    return execute_group_concat(
        database,
        "SELECT CONCAT('raw_hex=',HEX(generation_expression),"
        f"';normalized_hex=',HEX({normalized})) "
        "FROM information_schema.columns WHERE table_schema=DATABASE() "
        "AND table_name='product_forwarder_transport_eligibility' "
        "AND column_name='active_scope_slot';",
    )


def guard_metadata_diagnostics(database):
    trigger_normalized = (
        "LOWER(REGEXP_REPLACE(action_statement,'[[:space:]]+',' '))"
    )
    trigger_metadata = execute_group_concat(
        database,
        "SELECT GROUP_CONCAT(CONCAT(trigger_name,'=',HEX(action_statement),"
        f"':',HEX({trigger_normalized})) ORDER BY trigger_name SEPARATOR '|') "
        "FROM information_schema.triggers WHERE trigger_schema=DATABASE() "
        "AND trigger_name LIKE 'trg_fq_numeric_adjustment%retired_b_';",
    )
    check_normalized = (
        "REPLACE(REPLACE(REGEXP_REPLACE(REPLACE(REPLACE(REPLACE("
        "LOWER(check_clause),'`',''),CONCAT(CHAR(92),CHAR(39)),CHAR(39)),"
        "'_utf8mb4',''),'[()[:space:]]+',''),'charcharsetbinary','binary'),"
        "'octet_length','length')"
    )
    check_metadata = execute_group_concat(
        database,
        "SELECT GROUP_CONCAT(CONCAT(constraint_name,'=',HEX(check_clause),"
        f"':',HEX({check_normalized})) ORDER BY constraint_name SEPARATOR '|') "
        "FROM information_schema.check_constraints WHERE constraint_schema=DATABASE() "
        "AND constraint_name IN ('chk_pfte_status','chk_pfte_scope_codes',"
        "'chk_shipping_line_eligibility_snapshot');",
    )
    return trigger_metadata, check_metadata


def outer_if_predicates(statement):
    match = re.search(r"\bIF\s*\(", statement, re.IGNORECASE)
    if match is None:
        raise ValueError("postcheck must contain an outer IF expression")
    opening = statement.find("(", match.start())
    closing = _matching_parenthesis(statement, opening)
    arguments = _split_top_level(statement[opening + 1:closing], ",")
    if len(arguments) != 3:
        raise ValueError("postcheck outer IF must have three arguments")
    predicates = _split_top_level(arguments[0], "AND")
    if not predicates:
        raise ValueError("postcheck outer IF has no predicates")
    return predicates


def _matching_parenthesis(value, opening):
    depth = 0
    quote = None
    index = opening
    while index < len(value):
        character = value[index]
        if quote is not None:
            if character == quote:
                if index + 1 < len(value) and value[index + 1] == quote:
                    index += 2
                    continue
                quote = None
        elif character in ("'", '"', "`"):
            quote = character
        elif character == "(":
            depth += 1
        elif character == ")":
            depth -= 1
            if depth == 0:
                return index
        index += 1
    raise ValueError("postcheck outer IF is not closed")


def _split_top_level(value, delimiter):
    parts = []
    start = 0
    depth = 0
    quote = None
    index = 0
    while index < len(value):
        character = value[index]
        if quote is not None:
            if character == quote:
                if index + 1 < len(value) and value[index + 1] == quote:
                    index += 2
                    continue
                quote = None
        elif character in ("'", '"', "`"):
            quote = character
        elif character == "(":
            depth += 1
        elif character == ")":
            depth -= 1
        elif depth == 0 and _delimiter_at(value, delimiter, index):
            parts.append(value[start:index].strip())
            index += len(delimiter)
            start = index
            continue
        index += 1
    parts.append(value[start:].strip())
    return tuple(part for part in parts if part)


def _delimiter_at(value, delimiter, index):
    candidate = value[index:index + len(delimiter)]
    if candidate.upper() != delimiter:
        return False
    if delimiter == ",":
        return True
    before = value[index - 1] if index else " "
    after_index = index + len(delimiter)
    after = value[after_index] if after_index < len(value) else " "
    return not (before.isalnum() or before == "_") and not (
        after.isalnum() or after == "_"
    )
