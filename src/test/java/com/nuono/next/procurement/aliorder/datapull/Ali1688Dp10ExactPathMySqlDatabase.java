package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRow;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.JdbcTemplate;

/** Collision-resistant rows and assertions in the already migrated CI schema. */
final class Ali1688Dp10ExactPathMySqlDatabase {
    private final HikariDataSource pool;
    private final JdbcTemplate jdbc;
    private final String suffix = UUID.randomUUID().toString().replace("-", "");
    private final long ownerUserId = 8_000_000_000L
            + Integer.toUnsignedLong(suffix.hashCode());
    private final long authorizationId = 8_100_000_000_000L
            + Integer.toUnsignedLong(suffix.substring(0, 16).hashCode());
    private final long tombstoneHeaderId = 8_150_000_000_000L
            + Integer.toUnsignedLong(suffix.substring(8, 24).hashCode());
    private final AtomicLong taskIds = new AtomicLong(
            8_200_000_000_000L + Integer.toUnsignedLong(suffix.hashCode()) * 100L);
    private final Ali1688HistoricalOrderAuthorizationRow authorization = authorizationRow();
    private final String accountKey = Ali1688Dp10ScopeIdentity.accountKey(authorization);
    private final String scopeKey = Ali1688Dp10ScopeIdentity.scopeKey(authorization);

    Ali1688Dp10ExactPathMySqlDatabase(HikariDataSource pool) {
        this.pool = pool;
        this.jdbc = new JdbcTemplate(pool);
    }

    Ali1688HistoricalOrderAuthorizationRow authorization() {
        return authorization;
    }

    String suffix() {
        return suffix;
    }

    void prepare() {
        cleanup();
        jdbc.update(
                "INSERT INTO procurement_ali1688_order_authorization ("
                        + "id,owner_user_id,provider_code,provider_account_id,account_label,"
                        + "status,scope_summary,is_deleted,created_by,updated_by,gmt_create,gmt_updated) "
                        + "VALUES (?,?, 'ALI1688_OPEN_API',?,?, 'authorized',?,b'0',?,?,NOW(),NOW())",
                authorizationId, ownerUserId, authorization.getProviderAccountId(),
                "DP10 exact-path CI", "isolated exact-path fixture",
                ownerUserId, ownerUserId);
    }

    DataPullTask task(String step, String label) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        long id = taskIds.incrementAndGet();
        String window = "DP10:exact-ci:" + suffix.substring(0, 12) + ":" + label;
        DataPullTask task = DataPullTask.queued(
                id, OperationCode.DP10, Ali1688Dp10ScopeIdentity.PROVIDER_CHANNEL,
                ownerUserId, null, accountKey, null, null, null, null, scopeKey,
                now.minusMinutes(1), window, step, now.minusMinutes(1));
        task.setState(TaskState.RUNNING);
        task.setLeaseOwner("dp10-exact-" + suffix.substring(0, 12));
        task.setLeaseUntil(now.plusMinutes(10));
        task.setFenceEpoch(4L);
        task.setVersion(7L);
        jdbc.update(
                "INSERT INTO dp_pull_task (id,operation_code,provider_channel,owner_user_id,"
                        + "account_key,scope_key,schedule_slot,business_window_key,state,step_code,"
                        + "attempt,lease_owner,lease_until,fence_epoch,version_no,gmt_create,gmt_updated) "
                        + "VALUES (?,'DP10',?,?,?,?,?,?,'RUNNING',?,0,?,?,4,7,?,?)",
                id, task.getProviderChannel(), ownerUserId, accountKey, scopeKey,
                task.getScheduleSlot(), window, step, task.getLeaseOwner(),
                task.getLeaseUntil(), now, now);
        jdbc.update(
                "INSERT INTO dp_pull_scope_progress (operation_code,scope_key,"
                        + "initial_full_completed,official_modified_high_water_utc,"
                        + "last_applied_business_window_key,version_no,gmt_create,gmt_updated) "
                        + "VALUES ('DP10',?,b'0',NULL,NULL,0,?,?) "
                        + "ON DUPLICATE KEY UPDATE operation_code=operation_code",
                scopeKey, now, now);
        return task;
    }

    void switchToApply(DataPullTask task) {
        task.setStepCode("DP10_APPLY");
        jdbc.update("UPDATE dp_pull_task SET step_code='DP10_APPLY' WHERE id=?", task.getId());
    }

    void insertManualTombstone(String providerOrderNo) {
        jdbc.update(
                "INSERT INTO procurement_ali1688_order_header ("
                        + "id,owner_user_id,authorization_id,order_natural_key,"
                        + "provider_order_no,order_status,raw_snapshot_json,is_deleted,"
                        + "deleted_by,deleted_at,delete_reason,gmt_create,gmt_updated) "
                        + "VALUES (?,?,?,?,?,'LEGACY_DELETED','{\"legacy\":true}',b'1',?,"
                        + "'2026-08-04 02:00:00.123','CI_MANUAL_TOMBSTONE',"
                        + "'2026-08-04 01:00:00.000','2026-08-04 02:00:00.123')",
                tombstoneHeaderId, ownerUserId, authorizationId,
                authorizationId + ":" + providerOrderNo, providerOrderNo, ownerUserId);
    }

    String headerAudit(String providerOrderNo) {
        return jdbc.queryForObject(
                "SELECT CONCAT_WS('|',id,order_natural_key,provider_order_no,"
                        + "HEX(is_deleted),deleted_by,"
                        + "DATE_FORMAT(deleted_at,'%Y-%m-%d %H:%i:%s.%f'),delete_reason,"
                        + "order_status,raw_snapshot_json,"
                        + "DATE_FORMAT(gmt_updated,'%Y-%m-%d %H:%i:%s.%f')) "
                        + "FROM procurement_ali1688_order_header "
                        + "WHERE owner_user_id=? AND provider_order_no=?",
                String.class, ownerUserId, providerOrderNo);
    }

    String stageOutcome(long taskId, long generationNo, String providerOrderNo) {
        return jdbc.queryForObject(
                "SELECT CONCAT_WS('|',state,verification_state,apply_state,"
                        + "COALESCE(validation_code,'<null>'),apply_item_cursor) "
                        + "FROM dp_pull_dp10_stage_item WHERE task_id=? "
                        + "AND generation_no=? AND scan_pass=2 AND provider_order_no=?",
                String.class, taskId, generationNo, providerOrderNo);
    }

    int activeFactCountForOrder(String table, String providerOrderNo) {
        String predicate = table.equals("procurement_ali1688_order_header")
                ? "owner_user_id=? AND provider_order_no=? AND is_deleted=b'0'"
                : "order_id IN (SELECT id FROM procurement_ali1688_order_header "
                + "WHERE owner_user_id=? AND provider_order_no=?) AND is_deleted=b'0'";
        return count(table, predicate, ownerUserId, providerOrderNo);
    }

    int factRowCountForOrder(String table, String providerOrderNo) {
        String predicate = table.equals("procurement_ali1688_order_header")
                ? "owner_user_id=? AND provider_order_no=?"
                : "order_id IN (SELECT id FROM procurement_ali1688_order_header "
                + "WHERE owner_user_id=? AND provider_order_no=?)";
        return count(table, predicate, ownerUserId, providerOrderNo);
    }

    int count(String table, String where, Object... args) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + where,
                Integer.class, args);
        return value == null ? 0 : value;
    }

    int factCount(String table) {
        String join = table.equals("procurement_ali1688_order_header")
                ? "owner_user_id=? AND is_deleted=b'0'" : "order_id IN (SELECT id FROM "
                + "procurement_ali1688_order_header WHERE owner_user_id=? "
                + "AND is_deleted=b'0') AND is_deleted=b'0'";
        return count(table, join, ownerUserId);
    }

    String headerStatus(String providerOrderNo) {
        return jdbc.queryForObject(
                "SELECT order_status FROM procurement_ali1688_order_header "
                        + "WHERE owner_user_id=? AND provider_order_no=?",
                String.class, ownerUserId, providerOrderNo);
    }

    List<String> logisticsCompanies(String providerOrderNo) {
        return jdbc.queryForList(
                "SELECT logistics.logistics_company "
                        + "FROM procurement_ali1688_order_logistics logistics "
                        + "JOIN procurement_ali1688_order_header head "
                        + "ON head.id=logistics.order_id "
                        + "WHERE head.owner_user_id=? AND head.provider_order_no=? "
                        + "AND logistics.is_deleted=b'0' ORDER BY logistics.id",
                String.class, ownerUserId, providerOrderNo);
    }

    List<Integer> quantities(String providerOrderNo) {
        return jdbc.queryForList(
                "SELECT item.quantity FROM procurement_ali1688_order_item item "
                        + "JOIN procurement_ali1688_order_header head ON head.id=item.order_id "
                        + "WHERE head.owner_user_id=? AND head.provider_order_no=? "
                        + "AND item.is_deleted=b'0' ORDER BY item.id",
                Integer.class, ownerUserId, providerOrderNo);
    }

    int applyCursor(long taskId, long generationNo) {
        Integer value = jdbc.queryForObject(
                "SELECT apply_item_cursor FROM dp_pull_dp10_stage_item "
                        + "WHERE task_id=? AND generation_no=? AND scan_pass=2 "
                        + "AND partition_name='CURRENT' AND item_ordinal=0",
                Integer.class, taskId, generationNo);
        return value == null ? -1 : value;
    }

    Long progressVersion() {
        return jdbc.queryForObject(
                "SELECT version_no FROM dp_pull_scope_progress "
                        + "WHERE operation_code='DP10' AND BINARY scope_key=BINARY ?",
                Long.class, scopeKey);
    }

    LocalDateTime highWater() {
        return jdbc.queryForObject(
                "SELECT official_modified_high_water_utc FROM dp_pull_scope_progress "
                        + "WHERE operation_code='DP10' AND BINARY scope_key=BINARY ?",
                LocalDateTime.class, scopeKey);
    }

    Connection lockLastLogistics(String providerOrderNo) throws Exception {
        Long logisticsId = jdbc.queryForObject(
                "SELECT logistics.id FROM procurement_ali1688_order_logistics logistics "
                        + "JOIN procurement_ali1688_order_header head "
                        + "ON head.id=logistics.order_id "
                        + "WHERE head.owner_user_id=? AND head.provider_order_no=? "
                        + "AND logistics.tracking_no=? AND logistics.is_deleted=b'0'",
                Long.class, ownerUserId, providerOrderNo,
                "CI-TRACK-" + providerOrderNo + "-9");
        if (logisticsId == null) {
            throw new IllegalStateException("DP10 logistics lock target missing");
        }
        Connection connection = pool.getConnection();
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id FROM procurement_ali1688_order_logistics "
                            + "WHERE id=? FOR UPDATE")) {
                statement.setLong(1, logisticsId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        throw new IllegalStateException("DP10 logistics lock target disappeared");
                    }
                }
            }
            return connection;
        } catch (Exception failure) {
            connection.close();
            throw failure;
        }
    }

    void cleanup() {
        jdbc.update("DELETE identity_row FROM dp_pull_dp10_stage_identity identity_row "
                + "JOIN dp_pull_task task ON task.id=identity_row.task_id WHERE task.owner_user_id=?",
                ownerUserId);
        jdbc.update("DELETE fingerprint FROM dp_pull_dp10_stage_fingerprint_count fingerprint "
                + "JOIN dp_pull_task task ON task.id=fingerprint.task_id WHERE task.owner_user_id=?",
                ownerUserId);
        jdbc.update("DELETE item FROM dp_pull_dp10_stage_item item JOIN dp_pull_task task "
                + "ON task.id=item.task_id WHERE task.owner_user_id=?", ownerUserId);
        jdbc.update("DELETE page FROM dp_pull_dp10_stage_page page JOIN dp_pull_task task "
                + "ON task.id=page.task_id WHERE task.owner_user_id=?", ownerUserId);
        jdbc.update("DELETE cleanup_row FROM dp_pull_dp10_stage_cleanup cleanup_row "
                + "JOIN dp_pull_task task ON task.id=cleanup_row.task_id "
                + "WHERE task.owner_user_id=?", ownerUserId);
        jdbc.update("DELETE FROM dp_pull_scope_progress WHERE operation_code='DP10' "
                + "AND BINARY scope_key=BINARY ?", scopeKey);
        jdbc.update("DELETE FROM dp_pull_task WHERE owner_user_id=? AND BINARY account_key=BINARY ?",
                ownerUserId, accountKey);
        jdbc.update("DELETE logistics FROM procurement_ali1688_order_logistics logistics "
                + "JOIN procurement_ali1688_order_header head ON head.id=logistics.order_id "
                + "WHERE head.owner_user_id=?", ownerUserId);
        jdbc.update("DELETE item FROM procurement_ali1688_order_item item "
                + "JOIN procurement_ali1688_order_header head ON head.id=item.order_id "
                + "WHERE head.owner_user_id=?", ownerUserId);
        jdbc.update("DELETE FROM procurement_ali1688_order_header WHERE owner_user_id=?",
                ownerUserId);
        jdbc.update("DELETE FROM procurement_ali1688_order_authorization WHERE id=? "
                + "AND owner_user_id=?", authorizationId, ownerUserId);
    }

    private Ali1688HistoricalOrderAuthorizationRow authorizationRow() {
        Ali1688HistoricalOrderAuthorizationRow row =
                new Ali1688HistoricalOrderAuthorizationRow();
        row.setId(authorizationId);
        row.setOwnerUserId(ownerUserId);
        row.setProviderCode("ALI1688_OPEN_API");
        row.setProviderAccountId("dp10-exact-" + suffix.substring(0, 24));
        row.setStatus("authorized");
        row.setCreatedBy(ownerUserId);
        row.setUpdatedBy(ownerUserId);
        return row;
    }
}
