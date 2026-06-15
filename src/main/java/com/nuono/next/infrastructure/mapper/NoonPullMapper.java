package com.nuono.next.infrastructure.mapper;

import com.nuono.next.noonpull.NoonPullPlanRecord;
import com.nuono.next.noonpull.NoonPullTaskRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface NoonPullMapper {

    @Insert({
            "INSERT INTO noon_pull_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, #{initialValue}, NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE",
            "  next_id = LAST_INSERT_ID(next_id + 1),",
            "  gmt_updated = NOW()"
    })
    @SelectKey(
            statement = "SELECT LAST_INSERT_ID()",
            keyProperty = "allocatedId",
            before = false,
            resultType = Long.class
    )
    void nextId(IdSequenceCommand command);

    @Insert({
            "INSERT INTO noon_pull_plan (",
            "  id, owner_user_id, store_code, site_code, pull_type, data_domain, trigger_mode,",
            "  schedule_expression, enabled, paused, pause_reason, latest_success_at, latest_failure_at,",
            "  latest_failure_type, next_retry_at, max_pages_per_run, max_products_per_run,",
            "  max_detail_fetches_per_run, max_requests_per_run, cooldown_seconds, concurrency_limit,",
            "  gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{storeCode}, #{siteCode}, #{pullType}, #{dataDomain}, #{triggerMode},",
            "  #{scheduleExpression}, #{enabled}, #{paused}, #{pauseReason}, #{latestSuccessAt}, #{latestFailureAt},",
            "  #{latestFailureType}, #{nextRetryAt}, #{maxPagesPerRun}, #{maxProductsPerRun},",
            "  #{maxDetailFetchesPerRun}, #{maxRequestsPerRun}, #{cooldownSeconds}, #{concurrencyLimit},",
            "  #{createdAt}, #{updatedAt}",
            ")"
    })
    void insertPlan(NoonPullPlanRecord plan);

    @Select({
            "SELECT",
            "  id, owner_user_id, store_code, site_code, pull_type, data_domain, trigger_mode,",
            "  schedule_expression, enabled, paused, pause_reason, latest_success_at, latest_failure_at,",
            "  latest_failure_type, next_retry_at, max_pages_per_run, max_products_per_run,",
            "  max_detail_fetches_per_run, max_requests_per_run, cooldown_seconds, concurrency_limit,",
            "  gmt_create AS created_at, gmt_updated AS updated_at",
            "FROM noon_pull_plan",
            "WHERE id = #{planId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    NoonPullPlanRecord selectPlan(@Param("planId") Long planId);

    @Update({
            "UPDATE noon_pull_plan",
            "SET",
            "  enabled = #{enabled},",
            "  paused = #{paused},",
            "  pause_reason = #{pauseReason},",
            "  latest_success_at = #{latestSuccessAt},",
            "  latest_failure_at = #{latestFailureAt},",
            "  latest_failure_type = #{latestFailureType},",
            "  next_retry_at = #{nextRetryAt},",
            "  max_pages_per_run = #{maxPagesPerRun},",
            "  max_products_per_run = #{maxProductsPerRun},",
            "  max_detail_fetches_per_run = #{maxDetailFetchesPerRun},",
            "  max_requests_per_run = #{maxRequestsPerRun},",
            "  cooldown_seconds = #{cooldownSeconds},",
            "  concurrency_limit = #{concurrencyLimit},",
            "  gmt_updated = #{updatedAt}",
            "WHERE id = #{id}",
            "  AND is_deleted = b'0'"
    })
    int updatePlan(NoonPullPlanRecord plan);

    @Insert({
            "INSERT INTO noon_pull_task (",
            "  id, plan_id, owner_user_id, store_code, site_code, pull_type, data_domain, trigger_mode,",
            "  target_identity, target_date_from, target_date_to, active_lock_key, status,",
            "  source_batch_id, failure_type, retry_action, retryable, requires_manual_action,",
            "  diagnostic_summary, checkpoint_cursor, processed_item_count, request_count,",
            "  next_resume_position, last_safe_response_summary, readiness_state,",
            "  locked_by, queued_at, started_at, finished_at,",
            "  gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{planId}, #{ownerUserId}, #{storeCode}, #{siteCode}, #{pullType}, #{dataDomain}, #{triggerMode},",
            "  #{targetIdentity}, #{targetDateFrom}, #{targetDateTo}, #{activeLockKey}, #{status},",
            "  #{sourceBatchId}, #{failureType}, #{retryAction}, #{retryable}, #{requiresManualAction},",
            "  #{diagnosticSummary}, #{checkpointCursor}, #{processedItemCount}, #{requestCount},",
            "  #{nextResumePosition}, #{lastSafeResponseSummary}, #{readinessState},",
            "  #{lockedBy}, #{queuedAt}, #{startedAt}, #{finishedAt},",
            "  #{createdAt}, #{updatedAt}",
            ")"
    })
    void insertTask(NoonPullTaskRecord task);

    @Select({
            "SELECT",
            "  id, plan_id, owner_user_id, store_code, site_code, pull_type, data_domain, trigger_mode,",
            "  target_identity, target_date_from, target_date_to, active_lock_key, status,",
            "  source_batch_id, failure_type, retry_action, retryable, requires_manual_action,",
            "  diagnostic_summary, checkpoint_cursor, processed_item_count, request_count,",
            "  next_resume_position, last_safe_response_summary, readiness_state,",
            "  locked_by, queued_at, started_at, finished_at,",
            "  gmt_create AS created_at, gmt_updated AS updated_at",
            "FROM noon_pull_task",
            "WHERE id = #{taskId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    NoonPullTaskRecord selectTask(@Param("taskId") Long taskId);

    @Select({
            "SELECT",
            "  id, plan_id, owner_user_id, store_code, site_code, pull_type, data_domain, trigger_mode,",
            "  target_identity, target_date_from, target_date_to, active_lock_key, status,",
            "  source_batch_id, failure_type, retry_action, retryable, requires_manual_action,",
            "  diagnostic_summary, checkpoint_cursor, processed_item_count, request_count,",
            "  next_resume_position, last_safe_response_summary, readiness_state,",
            "  locked_by, queued_at, started_at, finished_at,",
            "  gmt_create AS created_at, gmt_updated AS updated_at",
            "FROM noon_pull_task",
            "WHERE active_lock_key = #{activeLockKey}",
            "  AND status IN ('QUEUED', 'RUNNING')",
            "  AND is_deleted = b'0'",
            "ORDER BY id ASC",
            "LIMIT 1"
    })
    NoonPullTaskRecord selectActiveTaskByLockKey(@Param("activeLockKey") String activeLockKey);

    @Select({
            "SELECT",
            "  id, plan_id, owner_user_id, store_code, site_code, pull_type, data_domain, trigger_mode,",
            "  target_identity, target_date_from, target_date_to, active_lock_key, status,",
            "  source_batch_id, failure_type, retry_action, retryable, requires_manual_action,",
            "  diagnostic_summary, checkpoint_cursor, processed_item_count, request_count,",
            "  next_resume_position, last_safe_response_summary, readiness_state,",
            "  locked_by, queued_at, started_at, finished_at,",
            "  gmt_create AS created_at, gmt_updated AS updated_at",
            "FROM noon_pull_task",
            "WHERE active_lock_key = #{activeLockKey}",
            "  AND is_deleted = b'0'",
            "ORDER BY id DESC",
            "LIMIT 1"
    })
    NoonPullTaskRecord selectLatestTaskByLockKey(@Param("activeLockKey") String activeLockKey);

    @Update({
            "UPDATE noon_pull_task",
            "SET",
            "  status = #{status},",
            "  source_batch_id = #{sourceBatchId},",
            "  failure_type = #{failureType},",
            "  retry_action = #{retryAction},",
            "  retryable = #{retryable},",
            "  requires_manual_action = #{requiresManualAction},",
            "  diagnostic_summary = #{diagnosticSummary},",
            "  checkpoint_cursor = #{checkpointCursor},",
            "  processed_item_count = #{processedItemCount},",
            "  request_count = #{requestCount},",
            "  next_resume_position = #{nextResumePosition},",
            "  last_safe_response_summary = #{lastSafeResponseSummary},",
            "  readiness_state = #{readinessState},",
            "  locked_by = #{lockedBy},",
            "  queued_at = #{queuedAt},",
            "  started_at = #{startedAt},",
            "  finished_at = #{finishedAt},",
            "  gmt_updated = #{updatedAt}",
            "WHERE id = #{id}",
            "  AND is_deleted = b'0'"
    })
    int updateTask(NoonPullTaskRecord task);

    @Select({
            "SELECT",
            "  id, owner_user_id, store_code, site_code, pull_type, data_domain, trigger_mode,",
            "  schedule_expression, enabled, paused, pause_reason, latest_success_at, latest_failure_at,",
            "  latest_failure_type, next_retry_at, max_pages_per_run, max_products_per_run,",
            "  max_detail_fetches_per_run, max_requests_per_run, cooldown_seconds, concurrency_limit,",
            "  gmt_create AS created_at, gmt_updated AS updated_at",
            "FROM noon_pull_plan",
            "WHERE is_deleted = b'0'",
            "ORDER BY owner_user_id ASC, store_code ASC, site_code ASC, data_domain ASC, id ASC"
    })
    List<NoonPullPlanRecord> listPlans();

    @Select({
            "SELECT",
            "  id, plan_id, owner_user_id, store_code, site_code, pull_type, data_domain, trigger_mode,",
            "  target_identity, target_date_from, target_date_to, active_lock_key, status,",
            "  source_batch_id, failure_type, retry_action, retryable, requires_manual_action,",
            "  diagnostic_summary, checkpoint_cursor, processed_item_count, request_count,",
            "  next_resume_position, last_safe_response_summary, readiness_state,",
            "  locked_by, queued_at, started_at, finished_at,",
            "  gmt_create AS created_at, gmt_updated AS updated_at",
            "FROM noon_pull_task",
            "WHERE is_deleted = b'0'",
            "ORDER BY id DESC",
            "LIMIT 200"
    })
    List<NoonPullTaskRecord> listTasks();

    @Select({
            "SELECT",
            "  id, plan_id, owner_user_id, store_code, site_code, pull_type, data_domain, trigger_mode,",
            "  target_identity, target_date_from, target_date_to, active_lock_key, status,",
            "  source_batch_id, failure_type, retry_action, retryable, requires_manual_action,",
            "  diagnostic_summary, checkpoint_cursor, processed_item_count, request_count,",
            "  next_resume_position, last_safe_response_summary, readiness_state,",
            "  locked_by, queued_at, started_at, finished_at,",
            "  gmt_create AS created_at, gmt_updated AS updated_at",
            "FROM noon_pull_task",
            "WHERE status IN ('QUEUED', 'RUNNING')",
            "  AND is_deleted = b'0'",
            "ORDER BY queued_at ASC, started_at ASC, id ASC"
    })
    List<NoonPullTaskRecord> listActiveTasks();
}
