from __future__ import annotations


CLEANUP_TASK_ID = 499999


def run_cleanup_marker_scenario(test_case, database, exact, live):
    database.client.execute(
        "INSERT INTO dp_pull_task ("
        "id,operation_code,provider_channel,owner_user_id,account_key,"
        "scope_key,schedule_slot,business_window_key,state,step_code,checkpoint,"
        "attempt,fence_epoch,version_no,gmt_create,gmt_updated"
        ") VALUES ("
        f"{CLEANUP_TASK_ID},'DP10','ALI1688',307,'ali1688:member-307',"
        "'ali1688-owner:307:member-307','2026-08-03 03:00:00.000',"
        "'DP10:2026-08-03','QUEUED','DP10_CLEANUP','{\"generationNo\":4}',"
        "0,9,0,NOW(3),NOW(3));"
        "INSERT INTO dp_pull_dp10_stage_page ("
        "task_id,generation_no,scan_pass,partition_name,page_no,"
        "active_fence_epoch,page_size,total_record,expected_pages,raw_row_count,"
        "state,page_fingerprint,gmt_create,gmt_updated) VALUES ("
        f"{CLEANUP_TASK_ID},4,2,'CURRENT',1,9,100,1,1,1,'READY',"
        f"'{('0' * 64)}',NOW(3),NOW(3));"
    )
    _assert_contract(test_case, database, exact, live, "0")
    database.client.execute(
        "INSERT INTO dp_pull_dp10_stage_cleanup ("
        "task_id,generation_no,reason,active_fence_epoch,gmt_create,gmt_updated) "
        f"VALUES ({CLEANUP_TASK_ID},3,'CURRENT_GENERATION',9,NOW(3),NOW(3));"
    )
    _assert_contract(test_case, database, exact, live, "0")
    test_case.assert_mysql_rejects(
        database,
        "INSERT INTO dp_pull_dp10_stage_cleanup ("
        "task_id,generation_no,reason,active_fence_epoch,gmt_create,gmt_updated) "
        f"VALUES ({CLEANUP_TASK_ID},4,'CURRENT_GENERATION',9,NOW(3),NOW(3));",
        expected_error=1062,
    )
    database.client.execute(
        "UPDATE dp_pull_dp10_stage_cleanup SET generation_no=4 "
        f"WHERE task_id={CLEANUP_TASK_ID};"
    )
    _assert_contract(test_case, database, exact, live, "1")
    database.client.execute(
        "UPDATE dp_pull_task SET state='FAILED',sanitized_failure_code="
        f"'DP10_RETRY_EXHAUSTED',finished_at=NOW(3) WHERE id={CLEANUP_TASK_ID};"
    )
    _assert_contract(test_case, database, exact, live, "1")
    database.client.execute(
        f"UPDATE dp_pull_dp10_stage_cleanup SET generation_no=3 WHERE task_id={CLEANUP_TASK_ID};"
    )
    _assert_contract(test_case, database, exact, live, "0")
    database.client.execute(
        "UPDATE dp_pull_dp10_stage_cleanup SET generation_no=4,active_fence_epoch=10 "
        f"WHERE task_id={CLEANUP_TASK_ID};"
    )
    _assert_contract(test_case, database, exact, live, "0")
    database.client.execute(
        "UPDATE dp_pull_dp10_stage_cleanup SET active_fence_epoch=9,"
        f"reason='FAILED_RETENTION' WHERE task_id={CLEANUP_TASK_ID};"
    )
    _assert_contract(test_case, database, exact, live, "1")
    database.client.execute(
        f"UPDATE dp_pull_task SET state='SUCCEEDED',sanitized_failure_code=NULL "
        f"WHERE id={CLEANUP_TASK_ID};"
    )
    _assert_contract(test_case, database, exact, live, "0")
    database.client.execute(
        f"DELETE FROM dp_pull_dp10_stage_cleanup WHERE task_id={CLEANUP_TASK_ID};"
        f"DELETE FROM dp_pull_dp10_stage_page WHERE task_id={CLEANUP_TASK_ID};"
        f"DELETE FROM dp_pull_task WHERE id={CLEANUP_TASK_ID};"
    )


def _assert_contract(test_case, database, exact, live, expected):
    test_case.assertEqual(expected, database.client.execute_readonly(exact))
    test_case.assertEqual(expected, database.client.execute_readonly(live))
