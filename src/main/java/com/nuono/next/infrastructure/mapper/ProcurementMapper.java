package com.nuono.next.infrastructure.mapper;

import com.nuono.next.procurement.ProcurementAutoInquiryWorkbenchView.AutoInquiryEventView;
import com.nuono.next.procurement.ProcurementAutoInquiryWorkbenchView.AutoInquirySessionView;
import com.nuono.next.procurement.ProcurementAutoInquiryWorkbenchView.AutoInquiryTaskView;
import com.nuono.next.procurement.ProcurementCandidatePoolView.CandidateView;
import com.nuono.next.procurement.ProcurementCandidatePoolView.DemandItemView;
import com.nuono.next.procurement.ProcurementCandidatePoolView.OrderView;
import com.nuono.next.procurement.ProcurementCandidatePoolView.TaskView;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ProcurementMapper {

    @Select({
            "<script>",
            "SELECT",
            "  po.id,",
            "  po.owner_user_id AS owner_user_id,",
            "  po.order_no,",
            "  po.title,",
            "  po.status,",
            "  po.target_market,",
            "  po.priority,",
            "  po.source_type,",
            "  po.item_count,",
            "  po.selected_candidate_count,",
            "  DATE_FORMAT(po.gmt_create, '%Y-%m-%d %H:%i:%s') AS created_at,",
            "  DATE_FORMAT(po.gmt_updated, '%Y-%m-%d %H:%i:%s') AS updated_at",
            "FROM procurement_order po",
            "WHERE po.owner_user_id = #{ownerUserId}",
            "  AND po.is_deleted = b'0'",
            "  <if test='orderNo != null and orderNo != \"\"'>",
            "    AND po.order_no = #{orderNo}",
            "  </if>",
            "ORDER BY po.gmt_updated DESC, po.id DESC",
            "LIMIT 1",
            "</script>"
    })
    OrderView selectLatestOrder(@Param("ownerUserId") Long ownerUserId, @Param("orderNo") String orderNo);

    @Select({
            "SELECT",
            "  di.id,",
            "  di.line_no,",
            "  di.source_platform,",
            "  di.source_url,",
            "  di.source_title,",
            "  di.source_image_url,",
            "  di.source_detail_image_url,",
            "  di.source_package_image_url,",
            "  di.target_price_min,",
            "  di.target_price_max,",
            "  di.target_quantity,",
            "  di.target_site,",
            "  di.special_requirement,",
            "  di.target_material,",
            "  di.target_power_mode,",
            "  di.target_size_text,",
            "  di.target_package_type,",
            "  di.delivery_expectation,",
            "  di.status,",
            "  di.selected_candidate_id,",
            "  DATE_FORMAT(di.gmt_create, '%Y-%m-%d %H:%i:%s') AS created_at,",
            "  DATE_FORMAT(di.gmt_updated, '%Y-%m-%d %H:%i:%s') AS updated_at",
            "FROM procurement_demand_item di",
            "WHERE di.order_id = #{orderId}",
            "  AND di.is_deleted = b'0'",
            "ORDER BY di.line_no ASC, di.id ASC"
    })
    List<DemandItemView> listDemandItems(@Param("orderId") Long orderId);

    @Select({
            "SELECT",
            "  di.id,",
            "  di.line_no,",
            "  di.source_platform,",
            "  di.source_url,",
            "  di.source_title,",
            "  di.source_image_url,",
            "  di.source_detail_image_url,",
            "  di.source_package_image_url,",
            "  di.target_price_min,",
            "  di.target_price_max,",
            "  di.target_quantity,",
            "  di.target_site,",
            "  di.special_requirement,",
            "  di.target_material,",
            "  di.target_power_mode,",
            "  di.target_size_text,",
            "  di.target_package_type,",
            "  di.delivery_expectation,",
            "  di.status,",
            "  di.selected_candidate_id,",
            "  DATE_FORMAT(di.gmt_create, '%Y-%m-%d %H:%i:%s') AS created_at,",
            "  DATE_FORMAT(di.gmt_updated, '%Y-%m-%d %H:%i:%s') AS updated_at",
            "FROM procurement_demand_item di",
            "JOIN procurement_order po ON po.id = di.order_id",
            "WHERE di.id = #{demandItemId}",
            "  AND po.owner_user_id = #{ownerUserId}",
            "  AND di.is_deleted = b'0'",
            "  AND po.is_deleted = b'0'",
            "LIMIT 1"
    })
    DemandItemView selectOwnedDemandItem(
            @Param("ownerUserId") Long ownerUserId,
            @Param("demandItemId") Long demandItemId
    );

    @Select({
            "SELECT",
            "  candidate.id,",
            "  candidate.demand_item_id,",
            "  candidate.task_id,",
            "  candidate.rank_no,",
            "  candidate.level,",
            "  candidate.total_score,",
            "  candidate.fit_score,",
            "  candidate.spec_score,",
            "  candidate.price_score,",
            "  candidate.supplier_score,",
            "  candidate.logistics_score,",
            "  candidate.candidate_platform,",
            "  candidate.candidate_url,",
            "  candidate.title,",
            "  candidate.supplier_name,",
            "  candidate.price_text,",
            "  candidate.moq_text,",
            "  candidate.location_text,",
            "  candidate.material_text,",
            "  candidate.power_mode_text,",
            "  candidate.size_text,",
            "  candidate.package_text,",
            "  candidate.delivery_timeline_text,",
            "  candidate.result_card_text,",
            "  candidate.detail_highlight_text,",
            "  candidate.attribute_snapshot_text,",
            "  candidate.shipping_snapshot_text,",
            "  candidate.package_snapshot_text,",
            "  candidate.main_image_url,",
            "  candidate.detail_image_url,",
            "  candidate.delivery_image_url,",
            "  candidate.manual_review_note,",
            "  candidate.inquiry_summary,",
            "  candidate.next_action,",
            "  candidate.badges_text,",
            "  candidate.reasons_text,",
            "  candidate.warnings_text,",
            "  candidate.is_selected AS selected,",
            "  candidate.decision_status,",
            "  DATE_FORMAT(candidate.gmt_create, '%Y-%m-%d %H:%i:%s') AS created_at,",
            "  DATE_FORMAT(candidate.gmt_updated, '%Y-%m-%d %H:%i:%s') AS updated_at",
            "FROM procurement_candidate candidate",
            "JOIN procurement_demand_item di ON di.id = candidate.demand_item_id AND di.is_deleted = b'0'",
            "JOIN procurement_order po ON po.id = di.order_id AND po.is_deleted = b'0'",
            "WHERE po.owner_user_id = #{ownerUserId}",
            "  AND candidate.demand_item_id = #{demandItemId}",
            "  AND candidate.id = #{candidateId}",
            "  AND candidate.is_deleted = b'0'",
            "LIMIT 1"
    })
    CandidateView selectOwnedCandidate(
            @Param("ownerUserId") Long ownerUserId,
            @Param("demandItemId") Long demandItemId,
            @Param("candidateId") Long candidateId
    );

    @Select({
            "SELECT",
            "  candidate.id,",
            "  candidate.demand_item_id,",
            "  candidate.task_id,",
            "  candidate.rank_no,",
            "  candidate.level,",
            "  candidate.total_score,",
            "  candidate.fit_score,",
            "  candidate.spec_score,",
            "  candidate.price_score,",
            "  candidate.supplier_score,",
            "  candidate.logistics_score,",
            "  candidate.candidate_platform,",
            "  candidate.candidate_url,",
            "  candidate.title,",
            "  candidate.supplier_name,",
            "  candidate.price_text,",
            "  candidate.moq_text,",
            "  candidate.location_text,",
            "  candidate.material_text,",
            "  candidate.power_mode_text,",
            "  candidate.size_text,",
            "  candidate.package_text,",
            "  candidate.delivery_timeline_text,",
            "  candidate.result_card_text,",
            "  candidate.detail_highlight_text,",
            "  candidate.attribute_snapshot_text,",
            "  candidate.shipping_snapshot_text,",
            "  candidate.package_snapshot_text,",
            "  candidate.main_image_url,",
            "  candidate.detail_image_url,",
            "  candidate.delivery_image_url,",
            "  candidate.manual_review_note,",
            "  candidate.inquiry_summary,",
            "  candidate.next_action,",
            "  candidate.badges_text,",
            "  candidate.reasons_text,",
            "  candidate.warnings_text,",
            "  candidate.is_selected AS selected,",
            "  candidate.decision_status,",
            "  DATE_FORMAT(candidate.gmt_create, '%Y-%m-%d %H:%i:%s') AS created_at,",
            "  DATE_FORMAT(candidate.gmt_updated, '%Y-%m-%d %H:%i:%s') AS updated_at",
            "FROM procurement_candidate candidate",
            "WHERE candidate.demand_item_id = #{demandItemId}",
            "  AND candidate.is_deleted = b'0'",
            "ORDER BY candidate.is_selected DESC, candidate.total_score DESC, candidate.rank_no ASC, candidate.id ASC"
    })
    List<CandidateView> listCandidatesByDemandItem(@Param("demandItemId") Long demandItemId);

    @Select({
            "SELECT",
            "  task.id,",
            "  task.demand_item_id,",
            "  task.status,",
            "  task.progress_percent,",
            "  task.search_mode,",
            "  task.selected_image_count,",
            "  task.search_path,",
            "  task.result_count,",
            "  task.recommended_count,",
            "  task.message,",
            "  DATE_FORMAT(task.started_at, '%Y-%m-%d %H:%i:%s') AS started_at,",
            "  DATE_FORMAT(task.finished_at, '%Y-%m-%d %H:%i:%s') AS finished_at",
            "FROM procurement_match_task task",
            "JOIN procurement_demand_item di ON di.id = task.demand_item_id",
            "WHERE di.order_id = #{orderId}",
            "  AND di.is_deleted = b'0'",
            "  AND task.is_deleted = b'0'",
            "ORDER BY task.gmt_updated DESC, task.id DESC"
    })
    List<TaskView> listTasks(@Param("orderId") Long orderId);

    @Select({
            "SELECT COALESCE(MAX(id), 42000) + 1 FROM procurement_match_task"
    })
    Long nextTaskId();

    @Select({
            "SELECT COALESCE(MAX(id), 45000) + 1 FROM procurement_auto_inquiry_task"
    })
    Long nextAutoInquiryTaskId();

    @Select({
            "SELECT COALESCE(MAX(id), 46000) + 1 FROM procurement_auto_inquiry_event"
    })
    Long nextAutoInquiryEventId();

    @Select({
            "SELECT",
            "  candidate.id,",
            "  candidate.demand_item_id,",
            "  candidate.task_id,",
            "  candidate.rank_no,",
            "  candidate.level,",
            "  candidate.total_score,",
            "  candidate.fit_score,",
            "  candidate.spec_score,",
            "  candidate.price_score,",
            "  candidate.supplier_score,",
            "  candidate.logistics_score,",
            "  candidate.candidate_platform,",
            "  candidate.candidate_url,",
            "  candidate.title,",
            "  candidate.supplier_name,",
            "  candidate.price_text,",
            "  candidate.moq_text,",
            "  candidate.location_text,",
            "  candidate.material_text,",
            "  candidate.power_mode_text,",
            "  candidate.size_text,",
            "  candidate.package_text,",
            "  candidate.delivery_timeline_text,",
            "  candidate.result_card_text,",
            "  candidate.detail_highlight_text,",
            "  candidate.attribute_snapshot_text,",
            "  candidate.shipping_snapshot_text,",
            "  candidate.package_snapshot_text,",
            "  candidate.main_image_url,",
            "  candidate.detail_image_url,",
            "  candidate.delivery_image_url,",
            "  candidate.manual_review_note,",
            "  candidate.inquiry_summary,",
            "  candidate.next_action,",
            "  candidate.badges_text,",
            "  candidate.reasons_text,",
            "  candidate.warnings_text,",
            "  candidate.is_selected AS selected,",
            "  candidate.decision_status,",
            "  DATE_FORMAT(candidate.gmt_create, '%Y-%m-%d %H:%i:%s') AS created_at,",
            "  DATE_FORMAT(candidate.gmt_updated, '%Y-%m-%d %H:%i:%s') AS updated_at",
            "FROM procurement_candidate candidate",
            "JOIN procurement_demand_item di ON di.id = candidate.demand_item_id",
            "WHERE di.order_id = #{orderId}",
            "  AND di.is_deleted = b'0'",
            "  AND candidate.is_deleted = b'0'",
            "ORDER BY candidate.demand_item_id ASC, candidate.total_score DESC, candidate.rank_no ASC, candidate.id ASC"
    })
    List<CandidateView> listCandidates(@Param("orderId") Long orderId);

    @Select({
            "<script>",
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
            "JOIN procurement_demand_item di ON di.id = task.demand_item_id AND di.is_deleted = b'0'",
            "JOIN procurement_order po ON po.id = di.order_id AND po.is_deleted = b'0'",
            "WHERE po.owner_user_id = #{ownerUserId}",
            "  AND task.demand_item_id = #{demandItemId}",
            "  <if test='candidateId != null'>",
            "    AND task.candidate_id = #{candidateId}",
            "  </if>",
            "  AND task.is_deleted = b'0'",
            "ORDER BY task.gmt_updated DESC, task.id DESC",
            "</script>"
    })
    List<AutoInquiryTaskView> listAutoInquiryTasks(
            @Param("ownerUserId") Long ownerUserId,
            @Param("demandItemId") Long demandItemId,
            @Param("candidateId") Long candidateId
    );

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
            "JOIN procurement_demand_item di ON di.id = task.demand_item_id AND di.is_deleted = b'0'",
            "JOIN procurement_order po ON po.id = di.order_id AND po.is_deleted = b'0'",
            "WHERE po.owner_user_id = #{ownerUserId}",
            "  AND task.demand_item_id = #{demandItemId}",
            "  AND task.is_deleted = b'0'",
            "ORDER BY task.gmt_updated DESC, task.id DESC",
            "LIMIT 1"
    })
    AutoInquiryTaskView selectLatestAutoInquiryTaskByDemandItem(
            @Param("ownerUserId") Long ownerUserId,
            @Param("demandItemId") Long demandItemId
    );

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
            "JOIN procurement_demand_item di ON di.id = task.demand_item_id AND di.is_deleted = b'0'",
            "JOIN procurement_order po ON po.id = di.order_id AND po.is_deleted = b'0'",
            "WHERE po.owner_user_id = #{ownerUserId}",
            "  AND task.demand_item_id = #{demandItemId}",
            "  AND task.candidate_id = #{candidateId}",
            "  AND task.status IN ('PENDING', 'RUNNING', 'RETRYING', 'SENT', 'CHATTING')",
            "  AND task.is_deleted = b'0'",
            "ORDER BY task.gmt_updated DESC, task.id DESC",
            "LIMIT 1"
    })
    AutoInquiryTaskView selectLatestActiveAutoInquiryTask(
            @Param("ownerUserId") Long ownerUserId,
            @Param("demandItemId") Long demandItemId,
            @Param("candidateId") Long candidateId
    );

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
            "  AND task.is_deleted = b'0'",
            "LIMIT 1"
    })
    AutoInquiryTaskView selectAutoInquiryTask(@Param("taskId") Long taskId);

    @Select({
            "SELECT",
            "  session.id,",
            "  session.platform,",
            "  session.session_key,",
            "  session.account_label,",
            "  session.status,",
            "  session.risk_code,",
            "  session.leased_task_id,",
            "  session.profile_path,",
            "  session.browser_endpoint,",
            "  session.note,",
            "  DATE_FORMAT(session.last_checked_at, '%Y-%m-%d %H:%i:%s') AS last_checked_at,",
            "  DATE_FORMAT(session.lease_updated_at, '%Y-%m-%d %H:%i:%s') AS lease_updated_at",
            "FROM procurement_auto_inquiry_session session",
            "WHERE session.platform = #{platform}",
            "  AND session.is_deleted = b'0'",
            "ORDER BY FIELD(session.status, 'READY', 'DEGRADED', 'BUSY', 'BLOCKED', 'EXPIRED'), session.id ASC"
    })
    List<AutoInquirySessionView> listAutoInquirySessions(@Param("platform") String platform);

    @Select({
            "SELECT",
            "  session.id,",
            "  session.platform,",
            "  session.session_key,",
            "  session.account_label,",
            "  session.status,",
            "  session.risk_code,",
            "  session.leased_task_id,",
            "  session.profile_path,",
            "  session.browser_endpoint,",
            "  session.note,",
            "  DATE_FORMAT(session.last_checked_at, '%Y-%m-%d %H:%i:%s') AS last_checked_at,",
            "  DATE_FORMAT(session.lease_updated_at, '%Y-%m-%d %H:%i:%s') AS lease_updated_at",
            "FROM procurement_auto_inquiry_session session",
            "WHERE session.platform = #{platform}",
            "  AND session.status = 'READY'",
            "  AND session.is_deleted = b'0'",
            "ORDER BY session.id ASC",
            "LIMIT 1"
    })
    AutoInquirySessionView selectFirstAvailableAutoInquirySession(@Param("platform") String platform);

    @Select({
            "SELECT",
            "  session.id,",
            "  session.platform,",
            "  session.session_key,",
            "  session.account_label,",
            "  session.status,",
            "  session.risk_code,",
            "  session.leased_task_id,",
            "  session.profile_path,",
            "  session.browser_endpoint,",
            "  session.note,",
            "  DATE_FORMAT(session.last_checked_at, '%Y-%m-%d %H:%i:%s') AS last_checked_at,",
            "  DATE_FORMAT(session.lease_updated_at, '%Y-%m-%d %H:%i:%s') AS lease_updated_at",
            "FROM procurement_auto_inquiry_session session",
            "WHERE session.id = #{sessionId}",
            "  AND session.is_deleted = b'0'",
            "LIMIT 1"
    })
    AutoInquirySessionView selectAutoInquirySession(@Param("sessionId") Long sessionId);

    @Select({
            "SELECT",
            "  event.id,",
            "  event.task_id,",
            "  event.event_type,",
            "  event.status_before,",
            "  event.status_after,",
            "  event.execution_stage,",
            "  event.event_message,",
            "  event.event_payload,",
            "  DATE_FORMAT(event.gmt_create, '%Y-%m-%d %H:%i:%s') AS created_at",
            "FROM procurement_auto_inquiry_event event",
            "WHERE event.task_id = #{taskId}",
            "  AND event.is_deleted = b'0'",
            "ORDER BY event.id DESC"
    })
    List<AutoInquiryEventView> listAutoInquiryEvents(@Param("taskId") Long taskId);

    @Select({
            "SELECT COALESCE(MAX(id), 43000) + 1 FROM procurement_candidate"
    })
    Long nextCandidateId();

    @Select({
            "SELECT COUNT(1)",
            "FROM procurement_candidate candidate",
            "JOIN procurement_demand_item di ON di.id = candidate.demand_item_id AND di.is_deleted = b'0'",
            "JOIN procurement_order po ON po.id = di.order_id AND po.is_deleted = b'0'",
            "WHERE po.owner_user_id = #{ownerUserId}",
            "  AND candidate.demand_item_id = #{demandItemId}",
            "  AND candidate.id = #{candidateId}",
            "  AND candidate.is_deleted = b'0'"
    })
    Integer countOwnedCandidate(
            @Param("ownerUserId") Long ownerUserId,
            @Param("demandItemId") Long demandItemId,
            @Param("candidateId") Long candidateId
    );

    @Select({
            "SELECT COALESCE(MAX(rank_no), 0)",
            "FROM procurement_candidate",
            "WHERE demand_item_id = #{demandItemId}",
            "  AND is_deleted = b'0'"
    })
    Integer selectMaxRankNoByDemandItem(@Param("demandItemId") Long demandItemId);

    @Insert({
            "INSERT INTO procurement_auto_inquiry_task (",
            "  id, owner_user_id, demand_item_id, candidate_id, planned_channel, active_channel, channel_fallback_reason,",
            "  external_inquiry_id, external_inquiry_url, external_result_status, external_result_payload, reply_source, reply_parse_status, reply_parse_error,",
            "  session_id, platform, status, execution_stage, attempt_no, max_attempts,",
            "  target_offer_id, target_supplier_identity, target_entry_url, target_locator_text, input_preview_text, input_payload_text, input_payload_hash,",
            "  input_locator, send_channel, send_evidence, thread_checkpoint, last_message_digest,",
            "  failure_code, failure_message, handoff_reason, message, started_at, sent_at, confirmed_at, finished_at, last_event_at, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{demandItemId}, #{candidateId}, 'ALI_UNPAID_ORDER_INQUIRY', 'NUONO_CHAT_INQUIRY',",
            "  '1688 unpaid order adapter is not enabled; fallback to Nuono chat inquiry.', NULL, NULL, NULL, NULL, 'CHAT_THREAD', 'PENDING', NULL,",
            "  NULL, #{platform}, #{status}, #{executionStage}, #{attemptNo}, #{maxAttempts},",
            "  NULL, NULL, NULL, NULL, NULL, NULL, NULL,",
            "  NULL, NULL, NULL, NULL, NULL,",
            "  NULL, NULL, NULL, #{message}, NULL, NULL, NULL, NULL, NOW(), b'0', #{createdBy}, #{createdBy}, NOW(), NOW()",
            ")"
    })
    int insertAutoInquiryTask(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("demandItemId") Long demandItemId,
            @Param("candidateId") Long candidateId,
            @Param("platform") String platform,
            @Param("status") String status,
            @Param("executionStage") String executionStage,
            @Param("attemptNo") Integer attemptNo,
            @Param("maxAttempts") Integer maxAttempts,
            @Param("message") String message,
            @Param("createdBy") Long createdBy
    );

    @Insert({
            "INSERT INTO procurement_auto_inquiry_event (",
            "  id, task_id, event_type, status_before, status_after, execution_stage, event_message, event_payload,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{taskId}, #{eventType}, #{statusBefore}, #{statusAfter}, #{executionStage}, #{eventMessage}, #{eventPayload},",
            "  b'0', #{createdBy}, #{createdBy}, NOW(), NOW()",
            ")"
    })
    int insertAutoInquiryEvent(
            @Param("id") Long id,
            @Param("taskId") Long taskId,
            @Param("eventType") String eventType,
            @Param("statusBefore") String statusBefore,
            @Param("statusAfter") String statusAfter,
            @Param("executionStage") String executionStage,
            @Param("eventMessage") String eventMessage,
            @Param("eventPayload") String eventPayload,
            @Param("createdBy") Long createdBy
    );

    @Update({
            "UPDATE procurement_candidate",
            "SET is_selected = b'0',",
            "    decision_status = CASE WHEN decision_status = 'SELECTED' THEN 'PENDING' ELSE decision_status END,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE demand_item_id = #{demandItemId}",
            "  AND is_deleted = b'0'"
    })
    int clearSelectedCandidates(@Param("demandItemId") Long demandItemId, @Param("updatedBy") Long updatedBy);

    @Update({
            "UPDATE procurement_auto_inquiry_task",
            "SET status = 'RUNNING',",
            "    execution_stage = 'CLAIMED',",
            "    attempt_no = attempt_no + 1,",
            "    message = '执行器已接手，开始准备会话与聊天目标。',",
            "    started_at = COALESCE(started_at, NOW()),",
            "    last_event_at = NOW(),",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND status IN ('PENDING', 'RETRYING')",
            "  AND is_deleted = b'0'"
    })
    int claimAutoInquiryTask(@Param("taskId") Long taskId, @Param("updatedBy") Long updatedBy);

    @Update({
            "UPDATE procurement_auto_inquiry_task",
            "SET session_id = #{sessionId},",
            "    execution_stage = 'SESSION_READY',",
            "    message = #{message},",
            "    last_event_at = NOW(),",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND is_deleted = b'0'"
    })
    int updateAutoInquiryTaskSessionReady(
            @Param("taskId") Long taskId,
            @Param("sessionId") Long sessionId,
            @Param("message") String message,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_auto_inquiry_task",
            "SET platform = #{platform},",
            "    execution_stage = 'TARGET_RESOLVED',",
            "    target_offer_id = #{targetOfferId},",
            "    target_supplier_identity = #{targetSupplierIdentity},",
            "    target_entry_url = #{targetEntryUrl},",
            "    target_locator_text = #{targetLocatorText},",
            "    message = #{message},",
            "    last_event_at = NOW(),",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND is_deleted = b'0'"
    })
    int updateAutoInquiryTaskTargetResolved(
            @Param("taskId") Long taskId,
            @Param("platform") String platform,
            @Param("targetOfferId") String targetOfferId,
            @Param("targetSupplierIdentity") String targetSupplierIdentity,
            @Param("targetEntryUrl") String targetEntryUrl,
            @Param("targetLocatorText") String targetLocatorText,
            @Param("message") String message,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_auto_inquiry_task",
            "SET execution_stage = 'INPUT_READY',",
            "    input_preview_text = #{inputPreviewText},",
            "    input_payload_text = #{inputPayloadText},",
            "    input_payload_hash = #{inputPayloadHash},",
            "    message = #{message},",
            "    last_event_at = NOW(),",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND is_deleted = b'0'"
    })
    int updateAutoInquiryTaskInputReady(
            @Param("taskId") Long taskId,
            @Param("inputPreviewText") String inputPreviewText,
            @Param("inputPayloadText") String inputPayloadText,
            @Param("inputPayloadHash") String inputPayloadHash,
            @Param("message") String message,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_auto_inquiry_task",
            "SET execution_stage = 'SEND_PREPARED',",
            "    input_locator = #{inputLocator},",
            "    active_channel = 'NUONO_CHAT_INQUIRY',",
            "    send_channel = #{sendChannel},",
            "    send_evidence = #{sendEvidence},",
            "    message = #{message},",
            "    last_event_at = NOW(),",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND is_deleted = b'0'"
    })
    int updateAutoInquiryTaskSendPrepared(
            @Param("taskId") Long taskId,
            @Param("inputLocator") String inputLocator,
            @Param("sendChannel") String sendChannel,
            @Param("sendEvidence") String sendEvidence,
            @Param("message") String message,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_auto_inquiry_task",
            "SET status = 'SENT',",
            "    execution_stage = 'SEND_CONFIRMED',",
            "    active_channel = 'NUONO_CHAT_INQUIRY',",
            "    send_channel = #{sendChannel},",
            "    send_evidence = #{sendEvidence},",
            "    thread_checkpoint = #{threadCheckpoint},",
            "    last_message_digest = #{lastMessageDigest},",
            "    sent_at = COALESCE(sent_at, NOW()),",
            "    no_reply_deadline_at = DATE_ADD(COALESCE(sent_at, NOW()), INTERVAL 24 HOUR),",
            "    confirmed_at = NOW(),",
            "    message = #{message},",
            "    last_event_at = NOW(),",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND is_deleted = b'0'"
    })
    int markAutoInquiryTaskSent(
            @Param("taskId") Long taskId,
            @Param("sendChannel") String sendChannel,
            @Param("sendEvidence") String sendEvidence,
            @Param("threadCheckpoint") String threadCheckpoint,
            @Param("lastMessageDigest") String lastMessageDigest,
            @Param("message") String message,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_auto_inquiry_task",
            "SET status = 'HANDOFF',",
            "    execution_stage = #{executionStage},",
            "    failure_code = #{failureCode},",
            "    failure_message = #{failureMessage},",
            "    handoff_reason = #{handoffReason},",
            "    reply_parse_status = CASE WHEN #{executionStage} = 'REPLY_PARSE_FAILED' THEN 'FAILED' ELSE reply_parse_status END,",
            "    reply_parse_error = CASE WHEN #{executionStage} = 'REPLY_PARSE_FAILED' THEN #{failureMessage} ELSE reply_parse_error END,",
            "    message = #{message},",
            "    finished_at = NOW(),",
            "    last_event_at = NOW(),",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND is_deleted = b'0'"
    })
    int markAutoInquiryTaskHandoff(
            @Param("taskId") Long taskId,
            @Param("executionStage") String executionStage,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("handoffReason") String handoffReason,
            @Param("message") String message,
            @Param("updatedBy") Long updatedBy
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
            "  AND is_deleted = b'0'"
    })
    int updateAutoInquiryTaskAliAiResult(
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
            "  AND is_deleted = b'0'"
    })
    int updateAutoInquiryTaskAliAiCreatePlan(
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
            "  AND is_deleted = b'0'"
    })
    int updateAutoInquiryTaskUnpaidOrderPlan(
            @Param("taskId") Long taskId,
            @Param("orderPlanPayload") String orderPlanPayload,
            @Param("message") String message,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_auto_inquiry_session",
            "SET status = 'BUSY',",
            "    leased_task_id = #{taskId},",
            "    lease_updated_at = NOW(),",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{sessionId}",
            "  AND status = 'READY'",
            "  AND is_deleted = b'0'"
    })
    int reserveAutoInquirySession(
            @Param("sessionId") Long sessionId,
            @Param("taskId") Long taskId,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_auto_inquiry_session",
            "SET status = 'READY',",
            "    leased_task_id = NULL,",
            "    note = #{note},",
            "    lease_updated_at = NOW(),",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{sessionId}",
            "  AND is_deleted = b'0'"
    })
    int releaseAutoInquirySession(
            @Param("sessionId") Long sessionId,
            @Param("updatedBy") Long updatedBy,
            @Param("note") String note
    );

    @Update({
            "UPDATE procurement_candidate",
            "SET is_deleted = b'1',",
            "    is_selected = b'0',",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE demand_item_id = #{demandItemId}",
            "  AND is_deleted = b'0'"
    })
    int archiveCandidatesByDemandItem(@Param("demandItemId") Long demandItemId, @Param("updatedBy") Long updatedBy);

    @Update({
            "UPDATE procurement_candidate",
            "SET is_selected = b'1',",
            "    decision_status = 'SELECTED',",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{candidateId}",
            "  AND demand_item_id = #{demandItemId}",
            "  AND is_deleted = b'0'"
    })
    int selectCandidate(
            @Param("demandItemId") Long demandItemId,
            @Param("candidateId") Long candidateId,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_candidate",
            "SET manual_review_note = #{manualReviewNote},",
            "    inquiry_summary = #{inquirySummary},",
            "    next_action = #{nextAction},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{candidateId}",
            "  AND demand_item_id = #{demandItemId}",
            "  AND is_deleted = b'0'"
    })
    int updateCandidateReview(
            @Param("demandItemId") Long demandItemId,
            @Param("candidateId") Long candidateId,
            @Param("manualReviewNote") String manualReviewNote,
            @Param("inquirySummary") String inquirySummary,
            @Param("nextAction") String nextAction,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_demand_item",
            "SET status = 'SCREENING',",
            "    selected_candidate_id = NULL,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{demandItemId}",
            "  AND is_deleted = b'0'"
    })
    int markDemandItemScreening(@Param("demandItemId") Long demandItemId, @Param("updatedBy") Long updatedBy);

    @Update({
            "UPDATE procurement_demand_item",
            "SET status = #{status},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{demandItemId}",
            "  AND is_deleted = b'0'"
    })
    int updateDemandItemStatus(
            @Param("demandItemId") Long demandItemId,
            @Param("status") String status,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_demand_item",
            "SET status = 'DECIDED',",
            "    selected_candidate_id = #{candidateId},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{demandItemId}",
            "  AND is_deleted = b'0'"
    })
    int markDemandItemDecided(
            @Param("demandItemId") Long demandItemId,
            @Param("candidateId") Long candidateId,
            @Param("updatedBy") Long updatedBy
    );

    @Insert({
            "INSERT INTO procurement_match_task (",
            "  id, demand_item_id, status, progress_percent, search_mode, selected_image_count, search_path,",
            "  result_count, recommended_count, message, started_at, finished_at, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{demandItemId}, #{status}, #{progressPercent}, #{searchMode}, #{selectedImageCount}, #{searchPath},",
            "  #{resultCount}, #{recommendedCount}, #{message}, #{startedAt}, #{finishedAt}, b'0', #{createdBy}, #{updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertMatchTask(
            @Param("id") Long id,
            @Param("demandItemId") Long demandItemId,
            @Param("status") String status,
            @Param("progressPercent") Integer progressPercent,
            @Param("searchMode") String searchMode,
            @Param("selectedImageCount") Integer selectedImageCount,
            @Param("searchPath") String searchPath,
            @Param("resultCount") Integer resultCount,
            @Param("recommendedCount") Integer recommendedCount,
            @Param("message") String message,
            @Param("startedAt") LocalDateTime startedAt,
            @Param("finishedAt") LocalDateTime finishedAt,
            @Param("createdBy") Long createdBy,
            @Param("updatedBy") Long updatedBy
    );

    @Insert({
            "INSERT INTO procurement_candidate (",
            "  id, demand_item_id, task_id, rank_no, level, total_score, fit_score, spec_score, price_score,",
            "  supplier_score, logistics_score, candidate_platform, candidate_url, title, supplier_name, price_text, moq_text,",
            "  location_text, material_text, power_mode_text, size_text, package_text, delivery_timeline_text, result_card_text,",
            "  detail_highlight_text, attribute_snapshot_text, shipping_snapshot_text, package_snapshot_text, main_image_url,",
            "  detail_image_url, delivery_image_url, manual_review_note, inquiry_summary, next_action, badges_text, reasons_text,",
            "  warnings_text, is_selected, decision_status, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{demandItemId}, #{taskId}, #{rankNo}, #{level}, #{totalScore}, #{fitScore}, #{specScore}, #{priceScore},",
            "  #{supplierScore}, #{logisticsScore}, #{candidatePlatform}, #{candidateUrl}, #{title}, #{supplierName}, #{priceText}, #{moqText},",
            "  #{locationText}, #{materialText}, #{powerModeText}, #{sizeText}, #{packageText}, #{deliveryTimelineText}, #{resultCardText},",
            "  #{detailHighlightText}, #{attributeSnapshotText}, #{shippingSnapshotText}, #{packageSnapshotText}, #{mainImageUrl},",
            "  #{detailImageUrl}, #{deliveryImageUrl}, NULL, NULL, #{nextAction}, #{badgesText}, #{reasonsText},",
            "  #{warningsText}, b'0', 'PENDING', b'0', #{createdBy}, #{updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertCandidate(
            @Param("id") Long id,
            @Param("demandItemId") Long demandItemId,
            @Param("taskId") Long taskId,
            @Param("rankNo") Integer rankNo,
            @Param("level") String level,
            @Param("totalScore") Integer totalScore,
            @Param("fitScore") Integer fitScore,
            @Param("specScore") Integer specScore,
            @Param("priceScore") Integer priceScore,
            @Param("supplierScore") Integer supplierScore,
            @Param("logisticsScore") Integer logisticsScore,
            @Param("candidatePlatform") String candidatePlatform,
            @Param("candidateUrl") String candidateUrl,
            @Param("title") String title,
            @Param("supplierName") String supplierName,
            @Param("priceText") String priceText,
            @Param("moqText") String moqText,
            @Param("locationText") String locationText,
            @Param("materialText") String materialText,
            @Param("powerModeText") String powerModeText,
            @Param("sizeText") String sizeText,
            @Param("packageText") String packageText,
            @Param("deliveryTimelineText") String deliveryTimelineText,
            @Param("resultCardText") String resultCardText,
            @Param("detailHighlightText") String detailHighlightText,
            @Param("attributeSnapshotText") String attributeSnapshotText,
            @Param("shippingSnapshotText") String shippingSnapshotText,
            @Param("packageSnapshotText") String packageSnapshotText,
            @Param("mainImageUrl") String mainImageUrl,
            @Param("detailImageUrl") String detailImageUrl,
            @Param("deliveryImageUrl") String deliveryImageUrl,
            @Param("nextAction") String nextAction,
            @Param("badgesText") String badgesText,
            @Param("reasonsText") String reasonsText,
            @Param("warningsText") String warningsText,
            @Param("createdBy") Long createdBy,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_order po",
            "LEFT JOIN (",
            "  SELECT",
            "    order_id,",
            "    COUNT(*) AS item_count,",
            "    SUM(CASE WHEN selected_candidate_id IS NOT NULL THEN 1 ELSE 0 END) AS selected_count",
            "  FROM procurement_demand_item",
            "  WHERE order_id = #{orderId}",
            "    AND is_deleted = b'0'",
            "  GROUP BY order_id",
            ") stats ON stats.order_id = po.id",
            "SET po.item_count = COALESCE(stats.item_count, po.item_count),",
            "    po.selected_candidate_count = COALESCE(stats.selected_count, 0),",
            "    po.status = CASE",
            "      WHEN COALESCE(stats.selected_count, 0) = 0 THEN 'SCREENING'",
            "      WHEN COALESCE(stats.selected_count, 0) < COALESCE(stats.item_count, 0) THEN 'PARTIAL_DECIDED'",
            "      ELSE 'DECIDED'",
            "    END,",
            "    po.updated_by = #{updatedBy},",
            "    po.gmt_updated = NOW()",
            "WHERE po.id = #{orderId}",
            "  AND po.is_deleted = b'0'"
    })
    int syncOrderDecisionSummary(@Param("orderId") Long orderId, @Param("updatedBy") Long updatedBy);
}
