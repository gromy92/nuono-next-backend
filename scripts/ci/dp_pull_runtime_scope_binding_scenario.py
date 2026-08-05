from __future__ import annotations


def insert_valid_scope_binding_scenario(database, task_id: int) -> None:
    payload = '{"keyword":"paper","site":"SA"}'
    payload_digest = "ea5cc593b5117ca07e363dcb1d12dd08ee5f8a2624bea0a959d7d0469a6e3230"
    binding_id = "e" * 64
    database.client.execute(
        "INSERT INTO dp_pull_scope_admission ("
        "scope_key,scope_namespace,owner_user_id,account_key,admission_kind,"
        "source_binding_sha256,cutover_key,gmt_create"
        ") VALUES ("
        "'DP08A_SCOPE-ci','DP08A_SCOPE',307,'307::DP08','CUTOVER_EXISTING',"
        f"'{('f' * 64)}','cutover-dp08-ci','2026-08-03 00:00:00.000');"
        "INSERT INTO dp_pull_scope_binding_epoch ("
        "binding_id,operation_code,scope_key,payload_type,payload_sha256,payload,"
        "effective_from_utc,effective_until_utc,source_observed_at_utc,open_scope_slot,"
        "gmt_create,gmt_updated"
        ") VALUES ("
        f"'{binding_id}','DP08A','DP08A_SCOPE-ci','DP08_KEYWORD_V1',"
        f"'{payload_digest}','{payload}','2026-08-03 00:00:00.000',NULL,"
        "'2026-08-03 00:00:00.000','DP08A:DP08A_SCOPE-ci',"
        "'2026-08-03 00:00:00.000','2026-08-03 00:00:00.000');"
        "INSERT INTO dp_pull_task ("
        "id,operation_code,provider_channel,owner_user_id,account_key,scope_key,"
        "schedule_slot,scope_binding_id,scope_payload_type,scope_payload_sha256,"
        "scope_payload,scope_binding_effective_from_utc,business_window_key,state,"
        "step_code,attempt,fence_epoch,version_no,gmt_create,gmt_updated"
        ") VALUES ("
        f"{task_id},'DP08A','NOON',307,'307::DP08','DP08A_SCOPE-ci',"
        f"'2026-08-03 00:00:00.000','{binding_id}','DP08_KEYWORD_V1',"
        f"'{payload_digest}','{payload}','2026-08-03 00:00:00.000',"
        "'2026-08-03T00:00:00Z','QUEUED','FETCH_PAGE',0,0,0,NOW(3),NOW(3));"
    )
