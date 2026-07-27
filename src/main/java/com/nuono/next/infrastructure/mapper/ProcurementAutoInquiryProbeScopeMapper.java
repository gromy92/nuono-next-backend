package com.nuono.next.infrastructure.mapper;

import com.nuono.next.procurement.ProcurementAutoInquiryWorkbenchView.AutoInquiryTaskView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProcurementAutoInquiryProbeScopeMapper {

    @Select({
            "SELECT",
            "  task.id,",
            "  task.owner_user_id,",
            "  task.demand_item_id,",
            "  task.candidate_id,",
            "  task.pool_id,",
            "  task.pool_item_id,",
            "  task.session_id,",
            "  task.platform,",
            "  task.status,",
            "  task.execution_stage,",
            "  task.planned_channel,",
            "  task.active_channel,",
            "  task.channel_fallback_reason,",
            "  task.external_inquiry_id,",
            "  task.external_inquiry_url,",
            "  task.external_result_status,",
            "  task.reply_source,",
            "  task.reply_parse_status,",
            "  task.reply_parse_error,",
            "  task.attempt_no,",
            "  task.max_attempts,",
            "  task.target_offer_id,",
            "  task.target_supplier_identity,",
            "  task.target_entry_url,",
            "  task.target_locator_text,",
            "  task.input_preview_text,",
            "  task.input_payload_text,",
            "  task.input_payload_hash,",
            "  task.input_locator,",
            "  task.send_channel,",
            "  task.send_evidence,",
            "  task.thread_checkpoint,",
            "  task.last_message_digest,",
            "  task.failure_code,",
            "  task.failure_message,",
            "  task.handoff_reason,",
            "  task.message,",
            "  DATE_FORMAT(task.started_at, '%Y-%m-%d %H:%i:%s') AS started_at,",
            "  DATE_FORMAT(task.sent_at, '%Y-%m-%d %H:%i:%s') AS sent_at,",
            "  DATE_FORMAT(task.confirmed_at, '%Y-%m-%d %H:%i:%s') AS confirmed_at,",
            "  DATE_FORMAT(task.finished_at, '%Y-%m-%d %H:%i:%s') AS finished_at,",
            "  DATE_FORMAT(task.gmt_create, '%Y-%m-%d %H:%i:%s') AS created_at,",
            "  DATE_FORMAT(task.gmt_updated, '%Y-%m-%d %H:%i:%s') AS updated_at",
            "FROM procurement_auto_inquiry_task task",
            "WHERE task.id = #{taskId}",
            "  AND task.owner_user_id = #{ownerUserId}",
            "  AND task.is_deleted = b'0'",
            "LIMIT 1"
    })
    AutoInquiryTaskView selectOwnedAutoInquiryTask(
            @Param("ownerUserId") Long ownerUserId,
            @Param("taskId") Long taskId
    );

    @Update({
            "UPDATE procurement_auto_inquiry_task",
            "SET active_channel = 'ALI_AI_BULK_INQUIRY',",
            "    external_inquiry_id = #{externalInquiryId},",
            "    external_inquiry_url = #{externalInquiryUrl},",
            "    external_result_status = #{externalResultStatus},",
            "    external_result_payload = #{externalResultPayload},",
            "    reply_source = #{replySource},",
            "    reply_parse_status = #{replyParseStatus},",
            "    reply_parse_error = #{replyParseError},",
            "    message = #{message},",
            "    last_event_at = NOW(),",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'"
    })
    int updateOwnedAutoInquiryTaskAliAiResult(
            @Param("ownerUserId") Long ownerUserId,
            @Param("taskId") Long taskId,
            @Param("externalInquiryId") String externalInquiryId,
            @Param("externalInquiryUrl") String externalInquiryUrl,
            @Param("externalResultStatus") String externalResultStatus,
            @Param("externalResultPayload") String externalResultPayload,
            @Param("replySource") String replySource,
            @Param("replyParseStatus") String replyParseStatus,
            @Param("replyParseError") String replyParseError,
            @Param("message") String message,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_auto_inquiry_task",
            "SET planned_channel = 'ALI_AI_BULK_INQUIRY',",
            "    external_result_payload = #{createPlanPayload},",
            "    message = #{message},",
            "    last_event_at = NOW(),",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'"
    })
    int updateOwnedAutoInquiryTaskAliAiCreatePlan(
            @Param("ownerUserId") Long ownerUserId,
            @Param("taskId") Long taskId,
            @Param("createPlanPayload") String createPlanPayload,
            @Param("message") String message,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_auto_inquiry_task",
            "SET planned_channel = 'ALI_UNPAID_ORDER_INQUIRY',",
            "    external_result_payload = #{orderPlanPayload},",
            "    message = #{message},",
            "    last_event_at = NOW(),",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'"
    })
    int updateOwnedAutoInquiryTaskUnpaidOrderPlan(
            @Param("ownerUserId") Long ownerUserId,
            @Param("taskId") Long taskId,
            @Param("orderPlanPayload") String orderPlanPayload,
            @Param("message") String message,
            @Param("updatedBy") Long updatedBy
    );
}
