package com.nuono.next.infrastructure.mapper;

import com.nuono.next.system.task.OperationalTask;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface OperationalTaskMapper {

    @Insert({
            "INSERT INTO operational_task_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
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
            "INSERT INTO operational_task (",
            "  id, task_type, owner_user_id, store_code, site_code, natural_key, status,",
            "  progress_percent, message, payload_json, result_json, error_code,",
            "  started_at, finished_at, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{taskType}, #{ownerUserId}, #{storeCode}, #{siteCode}, #{naturalKey}, #{status},",
            "  #{progressPercent}, #{message}, #{payloadJson}, #{resultJson}, #{errorCode},",
            "  #{startedAt}, #{finishedAt}, #{createdAt}, #{updatedAt}",
            ")"
    })
    void insert(OperationalTask task);

    @Select({
            "SELECT",
            "  id, task_type, owner_user_id, store_code, site_code, natural_key, status,",
            "  progress_percent, message, payload_json, result_json, error_code,",
            "  started_at, finished_at, gmt_create AS created_at, gmt_updated AS updated_at",
            "FROM operational_task",
            "WHERE id = #{taskId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    OperationalTask selectById(@Param("taskId") Long taskId);

    @Select({
            "SELECT",
            "  id, task_type, owner_user_id, store_code, site_code, natural_key, status,",
            "  progress_percent, message, payload_json, result_json, error_code,",
            "  started_at, finished_at, gmt_create AS created_at, gmt_updated AS updated_at",
            "FROM operational_task",
            "WHERE task_type = #{taskType}",
            "  AND natural_key = #{naturalKey}",
            "  AND status IN ('QUEUED', 'RUNNING')",
            "  AND is_deleted = b'0'",
            "ORDER BY id ASC",
            "LIMIT 1"
    })
    OperationalTask selectActiveByNaturalKey(
            @Param("taskType") String taskType,
            @Param("naturalKey") String naturalKey
    );

    @Select({
            "SELECT",
            "  id, task_type, owner_user_id, store_code, site_code, natural_key, status,",
            "  progress_percent, message, payload_json, result_json, error_code,",
            "  started_at, finished_at, gmt_create AS created_at, gmt_updated AS updated_at",
            "FROM operational_task",
            "WHERE task_type = #{taskType}",
            "  AND natural_key = #{naturalKey}",
            "  AND is_deleted = b'0'",
            "ORDER BY id DESC",
            "LIMIT 1"
    })
    OperationalTask selectLatestByNaturalKey(
            @Param("taskType") String taskType,
            @Param("naturalKey") String naturalKey
    );

    @Update({
            "UPDATE operational_task",
            "SET",
            "  status = #{status},",
            "  progress_percent = #{progressPercent},",
            "  message = #{message},",
            "  payload_json = #{payloadJson},",
            "  result_json = #{resultJson},",
            "  error_code = #{errorCode},",
            "  started_at = #{startedAt},",
            "  finished_at = #{finishedAt},",
            "  gmt_updated = #{updatedAt}",
            "WHERE id = #{id}",
            "  AND is_deleted = b'0'"
    })
    int update(OperationalTask task);

    @Update({
            "UPDATE operational_task",
            "SET status = 'RUNNING',",
            "    message = #{message},",
            "    started_at = #{startedAt},",
            "    gmt_updated = #{startedAt}",
            "WHERE id = #{taskId}",
            "  AND status = 'QUEUED'",
            "  AND is_deleted = b'0'"
    })
    int claimQueued(
            @Param("taskId") Long taskId,
            @Param("message") String message,
            @Param("startedAt") java.time.LocalDateTime startedAt
    );

    @Select({
            "SELECT",
            "  id, task_type, owner_user_id, store_code, site_code, natural_key, status,",
            "  progress_percent, message, payload_json, result_json, error_code,",
            "  started_at, finished_at, gmt_create AS created_at, gmt_updated AS updated_at",
            "FROM operational_task",
            "WHERE task_type = #{taskType}",
            "  AND status IN ('QUEUED', 'RUNNING')",
            "  AND is_deleted = b'0'",
            "ORDER BY id ASC",
            "LIMIT #{limit}"
    })
    List<OperationalTask> listActiveByTaskType(@Param("taskType") String taskType, @Param("limit") int limit);

    @Select({
            "<script>",
            "SELECT",
            "  id, task_type, owner_user_id, store_code, site_code, natural_key, status,",
            "  progress_percent, message, payload_json, result_json, error_code,",
            "  started_at, finished_at, gmt_create AS created_at, gmt_updated AS updated_at",
            "FROM operational_task",
            "WHERE is_deleted = b'0'",
            "<if test='taskType != null'>",
            "  AND task_type = #{taskType}",
            "</if>",
            "ORDER BY id DESC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<OperationalTask> listRecent(@Param("taskType") String taskType, @Param("limit") int limit);
}
