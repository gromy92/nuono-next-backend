from __future__ import annotations

from schema_migrations.mysql_support import MySqlExecutionError


TRIGGERS = (
    "trg_fq_numeric_adjustment_retired_bi",
    "trg_fq_numeric_adjustment_retired_bu",
    "trg_fq_numeric_adjustment_retired_bd",
    "trg_fq_numeric_adjustment_log_retired_bi",
    "trg_fq_numeric_adjustment_log_retired_bu",
    "trg_fq_numeric_adjustment_log_retired_bd",
)


def verify_forwarder_trigger_repair(test_case, database, migration):
    for preserved_count in (1, 3, 5):
        _drop_triggers(database, TRIGGERS[preserved_count:])
        test_case.assertEqual(
            str(preserved_count),
            database.client.execute(_trigger_count_sql()),
        )
        database.run_script(migration)
        test_case.assertTrue(database.postcheck(migration))

    wrong_name = TRIGGERS[0]
    definitions = (
        "BEFORE INSERT ON forwarder_quote_numeric_adjustment FOR EACH ROW "
        "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='wrong migration 237 fence'",
        "BEFORE INSERT ON forwarder_quote_numeric_adjustment_log FOR EACH ROW "
        "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='legacy numeric adjustment writer fenced by migration 237'",
    )
    for definition in definitions:
        _drop_triggers(database, (wrong_name,))
        database.client.execute(f"CREATE TRIGGER `{wrong_name}` {definition};")
        with test_case.assertRaises(MySqlExecutionError) as caught:
            database.run_script(migration)
        test_case.assertEqual(3819, caught.exception.error_code)
        _drop_triggers(database, (wrong_name,))
        database.run_script(migration)
    test_case.assertTrue(database.postcheck(migration))


def _drop_triggers(database, names):
    database.client.execute("".join(
        f"DROP TRIGGER IF EXISTS `{name}`;" for name in names
    ))


def _trigger_count_sql():
    names = ",".join(f"'{name}'" for name in TRIGGERS)
    return (
        "SELECT COUNT(*) FROM information_schema.triggers "
        f"WHERE trigger_schema=DATABASE() AND trigger_name IN ({names});"
    )
