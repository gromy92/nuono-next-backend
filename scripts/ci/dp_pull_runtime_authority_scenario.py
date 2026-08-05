from __future__ import annotations


OPERATIONS = ("DP04", "DP06", "DP07A")


def run_243_authority_scenario(
    test_case, database, exact, live, dp08_task_id: int
) -> None:
    task_ids = _insert_non_dp08_tasks(database)
    database.client.execute(
        f"UPDATE dp_pull_task SET fence_epoch=1 WHERE id={dp08_task_id};"
        + _stage_sql(dp08_task_id)
        + _page_sql(dp08_task_id)
    )
    _assert_contract(test_case, database, exact, live, "1")
    for task_id in task_ids:
        database.client.execute(_stage_sql(task_id))
        _assert_contract(test_case, database, exact, live, "1")
        database.client.execute(_page_sql(task_id))
        _assert_contract(test_case, database, exact, live, "0")
        database.client.execute(
            f"DELETE FROM dp_pull_snapshot_stage_page WHERE task_id={task_id};"
        )
        _assert_contract(test_case, database, exact, live, "1")
    _delete_stages_and_tasks(database, dp08_task_id, task_ids)
    _assert_contract(test_case, database, exact, live, "1")


def run_245_authority_scenario(
    test_case, database, exact, live, dp08_task_id: int
) -> None:
    task_ids = _insert_non_dp08_tasks(database)
    database.client.execute(_stage_sql(dp08_task_id) + _page_sql(dp08_task_id))
    _assert_install_and_live(test_case, database, exact, live, "0", "1")
    for task_id in task_ids:
        database.client.execute(_stage_sql(task_id))
        _assert_install_and_live(test_case, database, exact, live, "0", "1")
        database.client.execute(_page_sql(task_id))
        _assert_install_and_live(test_case, database, exact, live, "0", "0")
        database.client.execute(
            f"DELETE FROM dp_pull_snapshot_stage_page WHERE task_id={task_id};"
        )
        _assert_install_and_live(test_case, database, exact, live, "0", "1")
    _delete_stages_and_tasks(database, dp08_task_id, task_ids)
    _assert_contract(test_case, database, exact, live, "1")


def _insert_non_dp08_tasks(database) -> tuple[int, ...]:
    task_ids = (500002, 500003, 500004)
    values = []
    for task_id, operation in zip(task_ids, OPERATIONS):
        values.append(
            f"({task_id},'{operation}','NOON',307,'307::AUTHORITY-CI',"
            f"'AUTHORITY-CI::{operation}','2026-08-03 00:00:00.000',"
            f"'{operation}:2026-08-03','QUEUED','FETCH_PAGE',0,1,0,NOW(3),NOW(3))"
        )
    database.client.execute(
        "INSERT INTO dp_pull_task (id,operation_code,provider_channel,"
        "owner_user_id,account_key,scope_key,schedule_slot,business_window_key,"
        "state,step_code,attempt,fence_epoch,version_no,gmt_create,gmt_updated) "
        f"VALUES {','.join(values)};"
    )
    return task_ids


def _stage_sql(task_id: int) -> str:
    return (
        "INSERT INTO dp_pull_snapshot_stage (task_id,active_fence_epoch,"
        "version_no,gmt_create,gmt_updated) VALUES "
        f"({task_id},1,0,NOW(3),NOW(3));"
    )


def _page_sql(task_id: int) -> str:
    return (
        "INSERT INTO dp_pull_snapshot_stage_page (task_id,page_no,next_page,"
        "is_last_page,total_pages,item_count,source_item_count,"
        "business_skipped_item_count,gmt_create,gmt_updated) VALUES "
        f"({task_id},1,NULL,b'1',1,0,0,0,NOW(3),NOW(3));"
    )


def _delete_stages_and_tasks(database, dp08_task_id, task_ids) -> None:
    ids = ",".join(str(task_id) for task_id in task_ids)
    database.client.execute(
        "DELETE FROM dp_pull_snapshot_stage WHERE task_id IN "
        f"({dp08_task_id},{ids});"
        f"DELETE FROM dp_pull_task WHERE id IN ({ids});"
    )


def _assert_contract(test_case, database, exact, live, expected) -> None:
    test_case.assertEqual(expected, database.client.execute_readonly(exact))
    test_case.assertEqual(expected, database.client.execute_readonly(live))


def _assert_install_and_live(
    test_case, database, exact, live, exact_expected, live_expected
) -> None:
    test_case.assertEqual(exact_expected, database.client.execute_readonly(exact))
    test_case.assertEqual(live_expected, database.client.execute_readonly(live))
