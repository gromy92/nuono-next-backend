package com.nuono.next.infrastructure.mapper;

import com.nuono.next.sales.ProductLifecycleCurrentState;
import com.nuono.next.sales.ProductLifecycleHistoryRecord;
import com.nuono.next.sales.ProductLifecycleJobQuery;
import com.nuono.next.sales.ProductLifecycleJobRecord;
import com.nuono.next.sales.ProductLifecycleStateQuery;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;

public interface ProductLifecycleStateMapper {

    @Insert({
            "INSERT INTO sales_data_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue} + 1), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE",
            "  next_id = LAST_INSERT_ID(next_id + 1),",
            "  gmt_updated = NOW()"
    })
    @SelectKey(
            statement = {
                    "SELECT LAST_INSERT_ID()"
            },
            keyProperty = "allocatedId",
            before = false,
            resultType = Long.class
    )
    int allocateLifecycleId(IdSequenceCommand command);

    default Long nextLifecycleId(String sequenceName, long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        allocateLifecycleId(command);
        Long id = command.getAllocatedId();
        if (id == null || id <= 0) {
            throw new IllegalStateException("商品生命周期 ID 序列分配失败：" + sequenceName);
        }
        return id;
    }

    default Long nextProductLifecycleCurrentStateId() {
        return nextLifecycleId("product_lifecycle_current_state", 70000L);
    }

    default Long nextProductLifecycleHistoryId() {
        return nextLifecycleId("product_lifecycle_history", 71000L);
    }

    default Long nextProductLifecycleJobId() {
        return nextLifecycleId("product_lifecycle_job", 72000L);
    }

    @Insert({
            "INSERT INTO product_lifecycle_current_state (",
            "  id, owner_user_id, store_code, site_code, partner_sku, sku,",
            "  lifecycle_code, lifecycle_label, rule_version, analysis_date, listing_date, listing_date_source,",
            "  quality_state, explanation, evidence_json, last_job_id, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{state.ownerUserId}, #{state.storeCode}, #{state.siteCode}, #{state.partnerSku}, #{state.sku},",
            "  #{state.lifecycleCode}, #{state.lifecycleLabel}, #{state.ruleVersion}, #{state.analysisDate},",
            "  #{state.listingDate}, #{state.listingDateSource}, #{state.qualityState}, #{state.explanation},",
            "  #{state.evidenceJson}, #{state.lastJobId}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  lifecycle_code = VALUES(lifecycle_code),",
            "  lifecycle_label = VALUES(lifecycle_label),",
            "  rule_version = VALUES(rule_version),",
            "  analysis_date = VALUES(analysis_date),",
            "  listing_date = VALUES(listing_date),",
            "  listing_date_source = VALUES(listing_date_source),",
            "  quality_state = VALUES(quality_state),",
            "  explanation = VALUES(explanation),",
            "  evidence_json = VALUES(evidence_json),",
            "  last_job_id = VALUES(last_job_id),",
            "  gmt_updated = NOW()"
    })
    int upsertCurrentState(
            @Param("id") Long id,
            @Param("state") ProductLifecycleCurrentState state
    );

    @Insert({
            "INSERT INTO product_lifecycle_history (",
            "  id, current_state_id, owner_user_id, store_code, site_code, partner_sku, sku,",
            "  previous_lifecycle_code, previous_lifecycle_label, lifecycle_code, lifecycle_label,",
            "  rule_version, analysis_date, transition_reason, evidence_json, changed_at, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{record.currentStateId}, #{record.ownerUserId}, #{record.storeCode}, #{record.siteCode},",
            "  #{record.partnerSku}, #{record.sku}, #{record.previousLifecycleCode}, #{record.previousLifecycleLabel},",
            "  #{record.lifecycleCode}, #{record.lifecycleLabel}, #{record.ruleVersion}, #{record.analysisDate},",
            "  #{record.transitionReason}, #{record.evidenceJson}, #{record.changedAt}, NOW(), NOW()",
            ")"
    })
    int insertHistory(
            @Param("id") Long id,
            @Param("record") ProductLifecycleHistoryRecord record
    );

    @Insert({
            "INSERT INTO product_lifecycle_job (",
            "  id, owner_user_id, store_code, site_code, anchor_date, rule_version, status,",
            "  processed_count, changed_count, held_count, data_insufficient_count, failure_summary_json,",
            "  started_at, finished_at, triggered_by_user_id, trigger_source, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{job.ownerUserId}, #{job.storeCode}, #{job.siteCode}, #{job.anchorDate}, #{job.ruleVersion},",
            "  #{job.status}, #{job.processedCount}, #{job.changedCount}, #{job.heldCount},",
            "  #{job.dataInsufficientCount}, #{job.failureSummaryJson}, #{job.startedAt}, #{job.finishedAt},",
            "  #{job.triggeredByUserId}, #{job.triggerSource},",
            "  NOW(), NOW()",
            ")"
    })
    int insertJob(
            @Param("id") Long id,
            @Param("job") ProductLifecycleJobRecord job
    );

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class),
            @Arg(column = "partnerSku", javaType = String.class),
            @Arg(column = "sku", javaType = String.class),
            @Arg(column = "lifecycleCode", javaType = String.class),
            @Arg(column = "lifecycleLabel", javaType = String.class),
            @Arg(column = "ruleVersion", javaType = String.class),
            @Arg(column = "analysisDate", javaType = LocalDate.class),
            @Arg(column = "listingDate", javaType = LocalDate.class),
            @Arg(column = "listingDateSource", javaType = String.class),
            @Arg(column = "qualityState", javaType = String.class),
            @Arg(column = "explanation", javaType = String.class),
            @Arg(column = "evidenceJson", javaType = String.class),
            @Arg(column = "lastJobId", javaType = Long.class),
            @Arg(column = "updatedAt", javaType = LocalDateTime.class)
    })
    @Select({
            "SELECT",
            "  id,",
            "  owner_user_id AS ownerUserId,",
            "  store_code AS storeCode,",
            "  site_code AS siteCode,",
            "  partner_sku AS partnerSku,",
            "  sku,",
            "  lifecycle_code AS lifecycleCode,",
            "  lifecycle_label AS lifecycleLabel,",
            "  rule_version AS ruleVersion,",
            "  analysis_date AS analysisDate,",
            "  listing_date AS listingDate,",
            "  listing_date_source AS listingDateSource,",
            "  quality_state AS qualityState,",
            "  explanation,",
            "  evidence_json AS evidenceJson,",
            "  last_job_id AS lastJobId,",
            "  gmt_updated AS updatedAt",
            "FROM product_lifecycle_current_state",
            "WHERE owner_user_id = #{query.ownerUserId}",
            "  AND store_code = #{query.storeCode}",
            "  AND site_code = #{query.siteCode}",
            "  AND partner_sku = #{query.partnerSku}",
            "  AND sku = #{query.sku}"
    })
    ProductLifecycleCurrentState selectCurrentState(@Param("query") ProductLifecycleStateQuery query);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "currentStateId", javaType = Long.class),
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class),
            @Arg(column = "partnerSku", javaType = String.class),
            @Arg(column = "sku", javaType = String.class),
            @Arg(column = "previousLifecycleCode", javaType = String.class),
            @Arg(column = "previousLifecycleLabel", javaType = String.class),
            @Arg(column = "lifecycleCode", javaType = String.class),
            @Arg(column = "lifecycleLabel", javaType = String.class),
            @Arg(column = "ruleVersion", javaType = String.class),
            @Arg(column = "analysisDate", javaType = LocalDate.class),
            @Arg(column = "transitionReason", javaType = String.class),
            @Arg(column = "evidenceJson", javaType = String.class),
            @Arg(column = "changedAt", javaType = LocalDateTime.class)
    })
    @Select({
            "SELECT",
            "  id,",
            "  current_state_id AS currentStateId,",
            "  owner_user_id AS ownerUserId,",
            "  store_code AS storeCode,",
            "  site_code AS siteCode,",
            "  partner_sku AS partnerSku,",
            "  sku,",
            "  previous_lifecycle_code AS previousLifecycleCode,",
            "  previous_lifecycle_label AS previousLifecycleLabel,",
            "  lifecycle_code AS lifecycleCode,",
            "  lifecycle_label AS lifecycleLabel,",
            "  rule_version AS ruleVersion,",
            "  analysis_date AS analysisDate,",
            "  transition_reason AS transitionReason,",
            "  evidence_json AS evidenceJson,",
            "  changed_at AS changedAt",
            "FROM product_lifecycle_history",
            "WHERE owner_user_id = #{query.ownerUserId}",
            "  AND store_code = #{query.storeCode}",
            "  AND site_code = #{query.siteCode}",
            "  AND partner_sku = #{query.partnerSku}",
            "  AND sku = #{query.sku}",
            "ORDER BY changed_at DESC, id DESC",
            "LIMIT 20"
    })
    List<ProductLifecycleHistoryRecord> selectHistory(@Param("query") ProductLifecycleStateQuery query);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class),
            @Arg(column = "anchorDate", javaType = LocalDate.class),
            @Arg(column = "ruleVersion", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "processedCount", javaType = int.class),
            @Arg(column = "changedCount", javaType = int.class),
            @Arg(column = "heldCount", javaType = int.class),
            @Arg(column = "dataInsufficientCount", javaType = int.class),
            @Arg(column = "failureSummaryJson", javaType = String.class),
            @Arg(column = "startedAt", javaType = LocalDateTime.class),
            @Arg(column = "finishedAt", javaType = LocalDateTime.class),
            @Arg(column = "triggeredByUserId", javaType = Long.class),
            @Arg(column = "triggerSource", javaType = String.class)
    })
    @Select({
            "SELECT",
            "  id,",
            "  owner_user_id AS ownerUserId,",
            "  store_code AS storeCode,",
            "  site_code AS siteCode,",
            "  anchor_date AS anchorDate,",
            "  rule_version AS ruleVersion,",
            "  status,",
            "  processed_count AS processedCount,",
            "  changed_count AS changedCount,",
            "  held_count AS heldCount,",
            "  data_insufficient_count AS dataInsufficientCount,",
            "  failure_summary_json AS failureSummaryJson,",
            "  started_at AS startedAt,",
            "  finished_at AS finishedAt,",
            "  triggered_by_user_id AS triggeredByUserId,",
            "  trigger_source AS triggerSource",
            "FROM product_lifecycle_job",
            "WHERE owner_user_id = #{query.ownerUserId}",
            "  AND store_code = #{query.storeCode}",
            "  AND site_code = #{query.siteCode}",
            "ORDER BY started_at DESC, id DESC",
            "LIMIT 10"
    })
    List<ProductLifecycleJobRecord> selectJobs(@Param("query") ProductLifecycleJobQuery query);
}
