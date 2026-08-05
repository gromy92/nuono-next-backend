package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.scope.DataPullScopeBindingEpoch;
import com.nuono.next.datapull.scope.ScheduleBindingCloseCommand;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** MyBatis statements for immutable temporal DP execution-scope bindings. */
public interface DataPullScopeBindingMapper {

    @Select({
            "SELECT operation_code",
            "FROM dp_pull_schedule_cutover",
            "WHERE operation_code = #{operationCode}",
            "  AND state = 'ACTIVE'",
            "FOR UPDATE"
    })
    String lockActiveOperation(@Param("operationCode") OperationCode operationCode);

    @Select("SELECT UTC_TIMESTAMP(3)")
    LocalDateTime selectDatabaseNowUtc();

    @Select({
            "SELECT binding_id AS bindingId, operation_code AS operationCode,",
            "       scope_key AS scopeKey, payload_type AS payloadType,",
            "       payload_sha256 AS payloadSha256, payload,",
            "       effective_from_utc AS effectiveFromUtc,",
            "       effective_until_utc AS effectiveUntilUtc,",
            "       source_observed_at_utc AS sourceObservedAtUtc,",
            "       gmt_create AS createdAtUtc, gmt_updated AS updatedAtUtc",
            "FROM dp_pull_scope_binding_epoch",
            "WHERE operation_code = #{operationCode}",
            "  AND effective_until_utc IS NULL",
            "ORDER BY BINARY scope_key ASC",
            "FOR UPDATE"
    })
    List<DataPullScopeBindingEpoch> lockOpenBindings(
            @Param("operationCode") OperationCode operationCode
    );

    @Select({
            "<script>",
            "SELECT binding_id AS bindingId, operation_code AS operationCode,",
            " scope_key AS scopeKey, payload_type AS payloadType,",
            " payload_sha256 AS payloadSha256, payload,",
            " effective_from_utc AS effectiveFromUtc, effective_until_utc AS effectiveUntilUtc,",
            " source_observed_at_utc AS sourceObservedAtUtc,",
            " gmt_create AS createdAtUtc, gmt_updated AS updatedAtUtc",
            "FROM dp_pull_scope_binding_epoch",
            "WHERE operation_code = #{operationCode} AND effective_until_utc IS NULL",
            " AND BINARY scope_key IN",
            "<foreach collection='scopeKeys' item='scopeKey' open='(' separator=',' close=')'>",
            " #{scopeKey}",
            "</foreach>",
            "ORDER BY BINARY scope_key FOR UPDATE",
            "</script>"
    })
    List<DataPullScopeBindingEpoch> lockOpenBindingsByScopeKeys(
            @Param("operationCode") OperationCode operationCode,
            @Param("scopeKeys") List<String> scopeKeys
    );

    @Select({
            "<script>",
            "SELECT binding.binding_id AS bindingId, binding.operation_code AS operationCode,",
            " binding.scope_key AS scopeKey, binding.payload_type AS payloadType,",
            " binding.payload_sha256 AS payloadSha256, binding.payload,",
            " binding.effective_from_utc AS effectiveFromUtc,",
            " binding.effective_until_utc AS effectiveUntilUtc,",
            " binding.source_observed_at_utc AS sourceObservedAtUtc,",
            " binding.gmt_create AS createdAtUtc, binding.gmt_updated AS updatedAtUtc",
            "FROM (",
            "<foreach collection='scopeKeys' item='scopeKey' separator=' UNION ALL '>",
            " SELECT #{scopeKey} scopeKey, (SELECT candidate.binding_id",
            "  FROM dp_pull_scope_binding_epoch candidate",
            "  WHERE candidate.operation_code = #{operationCode}",
            "   AND BINARY candidate.scope_key = BINARY #{scopeKey}",
            "  ORDER BY candidate.effective_from_utc DESC, candidate.binding_id DESC LIMIT 1",
            " ) bindingId",
            "</foreach>",
            ") request JOIN dp_pull_scope_binding_epoch binding",
            " ON BINARY binding.binding_id = BINARY request.bindingId",
            "ORDER BY BINARY request.scopeKey FOR UPDATE",
            "</script>"
    })
    List<DataPullScopeBindingEpoch> lockLatestBindingsByScopeKeys(
            @Param("operationCode") OperationCode operationCode,
            @Param("scopeKeys") List<String> scopeKeys
    );

    @Select({
            "<script>",
            "SELECT binding.binding_id AS bindingId, binding.operation_code AS operationCode,",
            " binding.scope_key AS scopeKey, binding.payload_type AS payloadType,",
            " binding.payload_sha256 AS payloadSha256, binding.payload,",
            " binding.effective_from_utc AS effectiveFromUtc,",
            " binding.effective_until_utc AS effectiveUntilUtc,",
            " binding.source_observed_at_utc AS sourceObservedAtUtc,",
            " binding.gmt_create AS createdAtUtc, binding.gmt_updated AS updatedAtUtc",
            "FROM dp_pull_scope_binding_epoch binding",
            "WHERE binding.operation_code = #{operationCode}",
            " AND binding.effective_until_utc IS NULL",
            "<if test='afterScopeKey != null'>",
            " AND BINARY binding.scope_key &gt; BINARY #{afterScopeKey}",
            "</if>",
            " AND NOT EXISTS (SELECT 1 FROM dp_pull_schedule_source_scope staged",
            "  WHERE staged.operation_code = #{operationCode} AND staged.epoch_no = #{epochNo}",
            "   AND BINARY staged.scope_key = BINARY binding.scope_key)",
            "ORDER BY BINARY binding.scope_key LIMIT #{limit} FOR UPDATE",
            "</script>"
    })
    List<DataPullScopeBindingEpoch> lockMissingOpenBindingsAfter(
            @Param("operationCode") OperationCode operationCode,
            @Param("epochNo") long epochNo,
            @Param("afterScopeKey") String afterScopeKey,
            @Param("limit") int limit
    );

    @Insert({
            "INSERT INTO dp_pull_scope_binding_epoch (",
            "  binding_id, operation_code, scope_key, payload_type, payload_sha256, payload,",
            "  effective_from_utc, effective_until_utc, source_observed_at_utc,",
            "  open_scope_slot, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{bindingId}, #{operationCode}, #{scopeKey}, #{payloadType}, #{payloadSha256}, #{payload},",
            "  #{effectiveFromUtc}, NULL, #{sourceObservedAtUtc},",
            "  CONCAT(#{operationCode}, ':', #{scopeKey}), #{createdAtUtc}, #{updatedAtUtc}",
            ") ON DUPLICATE KEY UPDATE binding_id = binding_id"
    })
    int insertOpenBinding(DataPullScopeBindingEpoch binding);

    @Insert({
            "<script>",
            "INSERT INTO dp_pull_scope_binding_epoch (",
            " binding_id, operation_code, scope_key, payload_type, payload_sha256, payload,",
            " effective_from_utc, effective_until_utc, source_observed_at_utc,",
            " open_scope_slot, gmt_create, gmt_updated) VALUES",
            "<foreach collection='bindings' item='item' separator=','>",
            "(#{item.bindingId},#{item.operationCode},#{item.scopeKey},#{item.payloadType},",
            " #{item.payloadSha256},#{item.payload},#{item.effectiveFromUtc},NULL,",
            " #{item.sourceObservedAtUtc},CONCAT(#{item.operationCode},':',#{item.scopeKey}),",
            " #{item.createdAtUtc},#{item.updatedAtUtc})",
            "</foreach>",
            "ON DUPLICATE KEY UPDATE binding_id = binding_id",
            "</script>"
    })
    int insertOpenBindings(@Param("bindings") List<DataPullScopeBindingEpoch> bindings);

    @Update({
            "<script>",
            "UPDATE dp_pull_scope_binding_epoch",
            "SET effective_until_utc = CASE BINARY binding_id",
            "<foreach collection='commands' item='item'>",
            " WHEN BINARY #{item.bindingId} THEN #{item.effectiveUntilUtc}",
            "</foreach>",
            " ELSE effective_until_utc END, open_scope_slot = NULL,",
            " gmt_updated = CASE BINARY binding_id",
            "<foreach collection='commands' item='item'>",
            " WHEN BINARY #{item.bindingId} THEN #{item.effectiveUntilUtc}",
            "</foreach>",
            " ELSE gmt_updated END",
            "WHERE operation_code = #{operationCode} AND effective_until_utc IS NULL AND (",
            "<foreach collection='commands' item='item' separator=' OR '>",
            " (BINARY binding_id = BINARY #{item.bindingId}",
            "  AND BINARY payload_sha256 = BINARY #{item.payloadSha256}",
            "  AND effective_from_utc &lt; #{item.effectiveUntilUtc})",
            "</foreach>",
            ")",
            "</script>"
    })
    int closeBindings(
            @Param("operationCode") OperationCode operationCode,
            @Param("commands") List<ScheduleBindingCloseCommand> commands
    );

    @Update({
            "UPDATE dp_pull_scope_binding_epoch",
            "SET effective_until_utc = #{effectiveUntilUtc}, open_scope_slot = NULL,",
            "    gmt_updated = #{updatedAtUtc}",
            "WHERE BINARY binding_id = BINARY #{bindingId}",
            "  AND BINARY payload_sha256 = BINARY #{payloadSha256}",
            "  AND effective_until_utc IS NULL",
            "  AND effective_from_utc < #{effectiveUntilUtc}"
    })
    int closeBinding(
            @Param("bindingId") String bindingId,
            @Param("payloadSha256") String payloadSha256,
            @Param("effectiveUntilUtc") LocalDateTime effectiveUntilUtc,
            @Param("updatedAtUtc") LocalDateTime updatedAtUtc
    );

    @Select({
            "SELECT binding_id AS bindingId, operation_code AS operationCode,",
            "       scope_key AS scopeKey, payload_type AS payloadType,",
            "       payload_sha256 AS payloadSha256, payload,",
            "       effective_from_utc AS effectiveFromUtc,",
            "       effective_until_utc AS effectiveUntilUtc,",
            "       source_observed_at_utc AS sourceObservedAtUtc,",
            "       gmt_create AS createdAtUtc, gmt_updated AS updatedAtUtc",
            "FROM dp_pull_scope_binding_epoch",
            "WHERE BINARY binding_id = BINARY #{bindingId}",
            "LIMIT 1"
    })
    DataPullScopeBindingEpoch selectById(@Param("bindingId") String bindingId);
}
