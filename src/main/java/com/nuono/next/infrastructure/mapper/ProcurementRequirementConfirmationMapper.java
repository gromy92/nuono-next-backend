package com.nuono.next.infrastructure.mapper;

import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.CandidateRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.DemandDetailRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.DemandListRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.FinalCandidateRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.OperationLogRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.OperatorContextRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.PoolItemLockRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.PoolItemRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.PoolLockRow;
import com.nuono.next.procurement.ProcurementRequirementConfirmationRecords.PoolRow;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ProcurementRequirementConfirmationMapper {

    @Select({
            "SELECT",
            "  u.id AS user_id,",
            "  u.account_no,",
            "  COALESCE(NULLIF(TRIM(u.real_name), ''), u.account_no) AS real_name,",
            "  u.role AS user_role,",
            "  u.role_id,",
            "  r.name AS role_name,",
            "  r.code AS role_code,",
            "  u.level AS user_level,",
            "  r.level AS role_level,",
            "  u.status",
            "FROM `user` u",
            "LEFT JOIN role r ON r.id = u.role_id AND r.is_deleted = 0",
            "WHERE u.id = #{userId}",
            "  AND u.is_deleted = 0",
            "LIMIT 1"
    })
    OperatorContextRow selectOperatorContext(@Param("userId") Long userId);

    @Select({
            "<script>",
            "SELECT TABLE_NAME",
            "FROM information_schema.TABLES",
            "WHERE TABLE_SCHEMA = DATABASE()",
            "  AND TABLE_NAME IN",
            "  <foreach collection='tableNames' item='tableName' open='(' separator=',' close=')'>",
            "    #{tableName}",
            "  </foreach>",
            "</script>"
    })
    List<String> listExistingFeatureTables(@Param("tableNames") List<String> tableNames);

    @Select({
            "<script>",
            "SELECT",
            "  di.id AS demand_item_id,",
            "  po.id AS order_id,",
            "  po.owner_user_id AS owner_user_id,",
            "  po.order_no,",
            "  po.title AS order_title,",
            "  COALESCE(NULLIF(di.source_title, ''), po.title, CONCAT('采购需求#', di.id)) AS demand_title,",
            "  di.status AS demand_status,",
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
            "  di.assigned_buyer_id,",
            "  buyer.real_name AS assigned_buyer_name,",
            "  pool.id AS pool_id,",
            "  pool.pool_no,",
            "  pool.status AS pool_status,",
            "  COALESCE((",
            "    SELECT COUNT(1)",
            "    FROM procurement_candidate_pool_item item",
            "    WHERE item.pool_id = pool.id",
            "      AND item.is_current = b'1'",
            "      AND item.is_deleted = b'0'",
            "      AND item.status &lt;&gt; 'REMOVED_TERMINATED'",
            "  ), 0) AS pool_count,",
            "  COALESCE(pool.max_pool_size, 5) AS max_pool_size,",
            "  COALESCE((",
            "    SELECT COUNT(1)",
            "    FROM procurement_final_candidate final_item",
            "    WHERE final_item.demand_item_id = di.id",
            "      AND final_item.is_deleted = b'0'",
            "  ), 0) AS final_candidate_count,",
            "  COALESCE((",
            "    SELECT COUNT(1)",
            "    FROM procurement_candidate candidate",
            "    WHERE candidate.demand_item_id = di.id",
            "      AND candidate.is_deleted = b'0'",
            "  ), 0) AS candidate_count,",
            "  latest_task.id AS match_task_id,",
            "  latest_task.status AS match_task_status,",
            "  latest_task.progress_percent AS match_task_progress_percent,",
            "  latest_task.search_mode AS match_task_search_mode,",
            "  latest_task.selected_image_count AS match_task_selected_image_count,",
            "  latest_task.search_path AS match_task_search_path,",
            "  latest_task.result_count AS match_task_result_count,",
            "  latest_task.recommended_count AS match_task_recommended_count,",
            "  latest_task.message AS match_task_message,",
            "  DATE_FORMAT(latest_task.started_at, '%Y-%m-%d %H:%i:%s') AS match_task_started_at,",
            "  DATE_FORMAT(latest_task.finished_at, '%Y-%m-%d %H:%i:%s') AS match_task_finished_at,",
            "  DATE_FORMAT(COALESCE(pool.gmt_updated, di.gmt_updated, po.gmt_updated), '%Y-%m-%d %H:%i:%s') AS updated_at",
            "FROM procurement_demand_item di",
            "JOIN procurement_order po ON po.id = di.order_id AND po.is_deleted = b'0'",
            "LEFT JOIN procurement_candidate_pool pool ON pool.id = COALESCE(",
            "  di.current_pool_id,",
            "  (",
            "    SELECT latest_pool.id",
            "    FROM procurement_candidate_pool latest_pool",
            "    WHERE latest_pool.demand_item_id = di.id",
            "      AND latest_pool.is_deleted = b'0'",
            "    ORDER BY latest_pool.gmt_updated DESC, latest_pool.id DESC",
            "    LIMIT 1",
            "  )",
            ")",
            "LEFT JOIN procurement_match_task latest_task ON latest_task.id = (",
            "  SELECT task.id",
            "  FROM procurement_match_task task",
            "  WHERE task.demand_item_id = di.id",
            "    AND task.is_deleted = b'0'",
            "  ORDER BY task.gmt_updated DESC, task.id DESC",
            "  LIMIT 1",
            ")",
            "LEFT JOIN `user` buyer ON buyer.id = di.assigned_buyer_id AND buyer.is_deleted = 0",
            "WHERE di.is_deleted = b'0'",
            "  AND COALESCE(po.source_type, '') &lt;&gt; 'VALIDATION_SAMPLE'",
            "  <if test='ownerUserId != null'>",
            "    AND po.owner_user_id = #{ownerUserId}",
            "  </if>",
            "  <if test='status != null and status != \"\"'>",
            "    AND (pool.status = #{status} OR di.status = #{status})",
            "  </if>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      po.order_no LIKE CONCAT('%', #{keyword}, '%')",
            "      OR po.title LIKE CONCAT('%', #{keyword}, '%')",
            "      OR di.source_title LIKE CONCAT('%', #{keyword}, '%')",
            "      OR di.source_url LIKE CONCAT('%', #{keyword}, '%')",
            "      OR di.target_material LIKE CONCAT('%', #{keyword}, '%')",
            "    )",
            "  </if>",
            "ORDER BY COALESCE(pool.gmt_updated, di.gmt_updated, po.gmt_updated) DESC, di.id DESC",
            "LIMIT #{pageSize} OFFSET #{offset}",
            "</script>"
    })
    List<DemandListRow> listDemandRows(
            @Param("ownerUserId") Long ownerUserId,
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("offset") Integer offset,
            @Param("pageSize") Integer pageSize
    );

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM procurement_demand_item di",
            "JOIN procurement_order po ON po.id = di.order_id AND po.is_deleted = b'0'",
            "LEFT JOIN procurement_candidate_pool pool ON pool.id = COALESCE(",
            "  di.current_pool_id,",
            "  (",
            "    SELECT latest_pool.id",
            "    FROM procurement_candidate_pool latest_pool",
            "    WHERE latest_pool.demand_item_id = di.id",
            "      AND latest_pool.is_deleted = b'0'",
            "    ORDER BY latest_pool.gmt_updated DESC, latest_pool.id DESC",
            "    LIMIT 1",
            "  )",
            ")",
            "WHERE di.is_deleted = b'0'",
            "  AND COALESCE(po.source_type, '') &lt;&gt; 'VALIDATION_SAMPLE'",
            "  <if test='ownerUserId != null'>",
            "    AND po.owner_user_id = #{ownerUserId}",
            "  </if>",
            "  <if test='status != null and status != \"\"'>",
            "    AND (pool.status = #{status} OR di.status = #{status})",
            "  </if>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      po.order_no LIKE CONCAT('%', #{keyword}, '%')",
            "      OR po.title LIKE CONCAT('%', #{keyword}, '%')",
            "      OR di.source_title LIKE CONCAT('%', #{keyword}, '%')",
            "      OR di.source_url LIKE CONCAT('%', #{keyword}, '%')",
            "      OR di.target_material LIKE CONCAT('%', #{keyword}, '%')",
            "    )",
            "  </if>",
            "</script>"
    })
    Integer countDemandRows(
            @Param("ownerUserId") Long ownerUserId,
            @Param("status") String status,
            @Param("keyword") String keyword
    );

    @Select({
            "<script>",
            "SELECT",
            "  di.id AS demand_item_id,",
            "  po.id AS order_id,",
            "  po.owner_user_id AS owner_user_id,",
            "  po.order_no,",
            "  po.title AS order_title,",
            "  COALESCE(NULLIF(di.source_title, ''), po.title, CONCAT('采购需求#', di.id)) AS demand_title,",
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
            "  di.status AS demand_status,",
            "  di.status AS status,",
            "  di.assigned_buyer_id,",
            "  buyer.real_name AS assigned_buyer_name,",
            "  di.current_pool_id,",
            "  DATE_FORMAT(di.gmt_create, '%Y-%m-%d %H:%i:%s') AS created_at,",
            "  DATE_FORMAT(di.gmt_updated, '%Y-%m-%d %H:%i:%s') AS updated_at",
            "FROM procurement_demand_item di",
            "JOIN procurement_order po ON po.id = di.order_id AND po.is_deleted = b'0'",
            "LEFT JOIN `user` buyer ON buyer.id = di.assigned_buyer_id AND buyer.is_deleted = 0",
            "WHERE di.id = #{demandItemId}",
            "  AND di.is_deleted = b'0'",
            "  <if test='ownerUserId != null'>",
            "    AND po.owner_user_id = #{ownerUserId}",
            "  </if>",
            "LIMIT 1",
            "</script>"
    })
    DemandDetailRow selectDemandDetail(
            @Param("demandItemId") Long demandItemId,
            @Param("ownerUserId") Long ownerUserId
    );

    @Select({
            "SELECT",
            "  pool.id AS pool_id,",
            "  pool.pool_no,",
            "  pool.status,",
            "  COALESCE((",
            "    SELECT COUNT(1)",
            "    FROM procurement_candidate_pool_item item",
            "    WHERE item.pool_id = pool.id",
            "      AND item.is_current = b'1'",
            "      AND item.is_deleted = b'0'",
            "      AND item.status <> 'REMOVED_TERMINATED'",
            "  ), 0) AS pool_count,",
            "  pool.max_pool_size,",
            "  pool.candidate_source_limit,",
            "  pool.current_snapshot_id,",
            "  DATE_FORMAT(pool.auto_created_at, '%Y-%m-%d %H:%i:%s') AS auto_created_at,",
            "  DATE_FORMAT(pool.inquiry_started_at, '%Y-%m-%d %H:%i:%s') AS inquiry_started_at,",
            "  DATE_FORMAT(pool.inquiry_finished_at, '%Y-%m-%d %H:%i:%s') AS inquiry_finished_at,",
            "  DATE_FORMAT(pool.final_confirmed_at, '%Y-%m-%d %H:%i:%s') AS final_confirmed_at,",
            "  DATE_FORMAT(pool.summary_ready_at, '%Y-%m-%d %H:%i:%s') AS summary_ready_at,",
            "  pool.summary_text,",
            "  pool.summary_input_snapshot_id",
            "FROM procurement_candidate_pool pool",
            "WHERE pool.id = COALESCE(",
            "  (SELECT current_pool_id FROM procurement_demand_item WHERE id = #{demandItemId}),",
            "  (",
            "    SELECT latest_pool.id",
            "    FROM procurement_candidate_pool latest_pool",
            "    WHERE latest_pool.demand_item_id = #{demandItemId}",
            "      AND latest_pool.is_deleted = b'0'",
            "    ORDER BY latest_pool.gmt_updated DESC, latest_pool.id DESC",
            "    LIMIT 1",
            "  )",
            ")",
            "  AND pool.is_deleted = b'0'",
            "LIMIT 1"
    })
    PoolRow selectCurrentPool(@Param("demandItemId") Long demandItemId);

    @Select({
            "SELECT",
            "  candidate.id AS candidate_id,",
            "  candidate.rank_no,",
            "  candidate.total_score,",
            "  candidate.fit_score,",
            "  candidate.spec_score,",
            "  candidate.price_score,",
            "  candidate.supplier_score,",
            "  candidate.logistics_score,",
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
            "  candidate.badges_text,",
            "  candidate.reasons_text,",
            "  candidate.warnings_text",
            "FROM procurement_candidate candidate",
            "WHERE candidate.demand_item_id = #{demandItemId}",
            "  AND candidate.is_deleted = b'0'",
            "ORDER BY candidate.total_score DESC, candidate.rank_no ASC, candidate.id ASC",
            "LIMIT #{limit}"
    })
    List<CandidateRow> listTopCandidates(
            @Param("demandItemId") Long demandItemId,
            @Param("limit") Integer limit
    );

    @Select({
            "SELECT",
            "  item.id AS pool_item_id,",
            "  item.candidate_id,",
            "  COALESCE(item.source_rank_no, candidate.rank_no) AS source_rank_no,",
            "  item.pool_rank_no,",
            "  item.status,",
            "  item.join_source,",
            "  item.inquiry_task_id,",
            "  DATE_FORMAT(item.joined_at, '%Y-%m-%d %H:%i:%s') AS joined_at,",
            "  DATE_FORMAT(item.first_sent_at, '%Y-%m-%d %H:%i:%s') AS first_sent_at,",
            "  DATE_FORMAT(item.no_reply_deadline_at, '%Y-%m-%d %H:%i:%s') AS no_reply_deadline_at,",
            "  DATE_FORMAT(item.last_follow_up_at, '%Y-%m-%d %H:%i:%s') AS last_follow_up_at,",
            "  DATE_FORMAT(item.last_reply_at, '%Y-%m-%d %H:%i:%s') AS last_reply_at,",
            "  DATE_FORMAT(item.closed_at, '%Y-%m-%d %H:%i:%s') AS closed_at,",
            "  DATE_FORMAT(item.removed_at, '%Y-%m-%d %H:%i:%s') AS removed_at,",
            "  item.removed_by,",
            "  item.remove_reason,",
            "  item.quote_price_text,",
            "  item.quote_moq_text,",
            "  item.quote_delivery_text,",
            "  item.reply_summary,",
            "  item.risk_note,",
            "  task.status AS inquiry_task_status,",
            "  task.execution_stage AS inquiry_execution_stage,",
            "  task.planned_channel,",
            "  task.active_channel,",
            "  task.channel_fallback_reason,",
            "  task.external_inquiry_id,",
            "  task.external_inquiry_url,",
            "  task.external_result_status,",
            "  task.reply_source,",
            "  task.reply_parse_status,",
            "  task.reply_parse_error,",
            "  candidate.rank_no,",
            "  candidate.total_score,",
            "  candidate.fit_score,",
            "  candidate.spec_score,",
            "  candidate.price_score,",
            "  candidate.supplier_score,",
            "  candidate.logistics_score,",
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
            "  candidate.badges_text,",
            "  candidate.reasons_text,",
            "  candidate.warnings_text",
            "FROM procurement_candidate_pool_item item",
            "JOIN procurement_candidate candidate ON candidate.id = item.candidate_id AND candidate.is_deleted = b'0'",
            "LEFT JOIN procurement_auto_inquiry_task task ON task.id = item.inquiry_task_id AND task.is_deleted = b'0'",
            "WHERE item.pool_id = #{poolId}",
            "  AND item.is_current = b'1'",
            "  AND item.is_deleted = b'0'",
            "  AND item.status <> 'REMOVED_TERMINATED'",
            "ORDER BY item.pool_rank_no ASC, item.joined_at ASC, item.id ASC"
    })
    List<PoolItemRow> listCurrentPoolItems(@Param("poolId") Long poolId);

    @Select({
            "SELECT",
            "  final_item.id,",
            "  final_item.pool_item_id,",
            "  final_item.candidate_id,",
            "  final_item.final_pick_type,",
            "  final_item.snapshot_id,",
            "  final_item.decision_note,",
            "  final_item.confirmed_by,",
            "  DATE_FORMAT(final_item.confirmed_at, '%Y-%m-%d %H:%i:%s') AS confirmed_at,",
            "  candidate.rank_no,",
            "  candidate.total_score,",
            "  candidate.fit_score,",
            "  candidate.spec_score,",
            "  candidate.price_score,",
            "  candidate.supplier_score,",
            "  candidate.logistics_score,",
            "  candidate.candidate_url,",
            "  candidate.title,",
            "  candidate.supplier_name,",
            "  COALESCE(NULLIF(pool_item.quote_price_text, ''), candidate.price_text) AS price_text,",
            "  COALESCE(NULLIF(pool_item.quote_moq_text, ''), candidate.moq_text) AS moq_text,",
            "  candidate.location_text,",
            "  candidate.material_text,",
            "  candidate.power_mode_text,",
            "  candidate.size_text,",
            "  candidate.package_text,",
            "  COALESCE(NULLIF(pool_item.quote_delivery_text, ''), candidate.delivery_timeline_text) AS delivery_timeline_text,",
            "  candidate.result_card_text,",
            "  candidate.detail_highlight_text,",
            "  candidate.attribute_snapshot_text,",
            "  candidate.shipping_snapshot_text,",
            "  candidate.package_snapshot_text,",
            "  candidate.main_image_url,",
            "  candidate.detail_image_url,",
            "  candidate.delivery_image_url,",
            "  candidate.badges_text,",
            "  candidate.reasons_text,",
            "  candidate.warnings_text",
            "FROM procurement_final_candidate final_item",
            "JOIN procurement_candidate candidate ON candidate.id = final_item.candidate_id AND candidate.is_deleted = b'0'",
            "JOIN procurement_candidate_pool_item pool_item ON pool_item.id = final_item.pool_item_id AND pool_item.is_deleted = b'0'",
            "WHERE final_item.demand_item_id = #{demandItemId}",
            "  AND final_item.is_deleted = b'0'",
            "ORDER BY FIELD(final_item.final_pick_type, 'PRIMARY', 'BACKUP'), final_item.id ASC"
    })
    List<FinalCandidateRow> listFinalCandidates(@Param("demandItemId") Long demandItemId);

    @Select({
            "SELECT",
            "  log.id,",
            "  log.pool_id,",
            "  log.pool_item_id,",
            "  log.candidate_id,",
            "  log.offer_id,",
            "  log.operation_type,",
            "  log.operator_user_id,",
            "  log.operator_role,",
            "  log.before_status,",
            "  log.after_status,",
            "  log.snapshot_id,",
            "  log.operation_reason,",
            "  log.detail_json,",
            "  DATE_FORMAT(log.gmt_create, '%Y-%m-%d %H:%i:%s') AS created_at",
            "FROM procurement_pool_operation_log log",
            "WHERE log.demand_item_id = #{demandItemId}",
            "ORDER BY log.id DESC",
            "LIMIT #{limit}"
    })
    List<OperationLogRow> listOperationLogs(
            @Param("demandItemId") Long demandItemId,
            @Param("limit") Integer limit
    );

    @Select({
            "SELECT COALESCE(MAX(id), 90000) + 1 FROM procurement_candidate_pool"
    })
    Long nextCandidatePoolId();

    @Select({
            "SELECT COALESCE(MAX(id), 91000) + 1 FROM procurement_candidate_pool_item"
    })
    Long nextCandidatePoolItemId();

    @Select({
            "SELECT COALESCE(MAX(id), 92000) + 1 FROM procurement_candidate_pool_snapshot"
    })
    Long nextCandidatePoolSnapshotId();

    @Select({
            "SELECT COALESCE(MAX(id), 93000) + 1 FROM procurement_final_candidate"
    })
    Long nextFinalCandidateId();

    @Select({
            "SELECT COALESCE(MAX(id), 94000) + 1 FROM procurement_pool_operation_log"
    })
    Long nextPoolOperationLogId();

    @Select({
            "SELECT COALESCE(MAX(id), 45000) + 1 FROM procurement_auto_inquiry_task"
    })
    Long nextAutoInquiryTaskId();

    @Select({
            "<script>",
            "SELECT",
            "  di.id AS demand_item_id,",
            "  po.id AS order_id,",
            "  po.owner_user_id AS owner_user_id,",
            "  po.order_no,",
            "  po.title AS order_title,",
            "  COALESCE(NULLIF(di.source_title, ''), po.title, CONCAT('采购需求#', di.id)) AS demand_title,",
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
            "  di.status AS demand_status,",
            "  di.status AS status,",
            "  di.assigned_buyer_id,",
            "  buyer.real_name AS assigned_buyer_name,",
            "  di.current_pool_id,",
            "  DATE_FORMAT(di.gmt_create, '%Y-%m-%d %H:%i:%s') AS created_at,",
            "  DATE_FORMAT(di.gmt_updated, '%Y-%m-%d %H:%i:%s') AS updated_at",
            "FROM procurement_demand_item di",
            "JOIN procurement_order po ON po.id = di.order_id AND po.is_deleted = b'0'",
            "LEFT JOIN `user` buyer ON buyer.id = di.assigned_buyer_id AND buyer.is_deleted = 0",
            "WHERE di.id = #{demandItemId}",
            "  AND di.is_deleted = b'0'",
            "  <if test='ownerUserId != null'>",
            "    AND po.owner_user_id = #{ownerUserId}",
            "  </if>",
            "LIMIT 1",
            "FOR UPDATE",
            "</script>"
    })
    DemandDetailRow selectDemandDetailForUpdate(
            @Param("demandItemId") Long demandItemId,
            @Param("ownerUserId") Long ownerUserId
    );

    @Select({
            "SELECT",
            "  pool.id AS pool_id,",
            "  pool.owner_user_id,",
            "  pool.demand_item_id,",
            "  pool.pool_no,",
            "  pool.status,",
            "  COALESCE((",
            "    SELECT COUNT(1)",
            "    FROM procurement_candidate_pool_item item",
            "    WHERE item.pool_id = pool.id",
            "      AND item.is_current = b'1'",
            "      AND item.is_deleted = b'0'",
            "      AND item.status <> 'REMOVED_TERMINATED'",
            "  ), 0) AS pool_count,",
            "  pool.max_pool_size,",
            "  pool.candidate_source_limit",
            "FROM procurement_candidate_pool pool",
            "JOIN procurement_demand_item di ON di.id = #{demandItemId} AND di.is_deleted = b'0'",
            "WHERE pool.id = COALESCE(",
            "  di.current_pool_id,",
            "  (",
            "    SELECT latest_pool.id",
            "    FROM procurement_candidate_pool latest_pool",
            "    WHERE latest_pool.demand_item_id = #{demandItemId}",
            "      AND latest_pool.is_deleted = b'0'",
            "    ORDER BY latest_pool.gmt_updated DESC, latest_pool.id DESC",
            "    LIMIT 1",
            "  )",
            ")",
            "  AND pool.is_deleted = b'0'",
            "LIMIT 1",
            "FOR UPDATE"
    })
    PoolLockRow selectCurrentPoolForUpdate(@Param("demandItemId") Long demandItemId);

    @Select({
            "SELECT",
            "  item.id AS pool_item_id,",
            "  item.pool_id,",
            "  item.owner_user_id,",
            "  item.demand_item_id,",
            "  item.candidate_id,",
            "  item.source_rank_no,",
            "  item.pool_rank_no,",
            "  item.status,",
            "  item.join_source,",
            "  item.inquiry_task_id",
            "FROM procurement_candidate_pool_item item",
            "WHERE item.pool_id = #{poolId}",
            "  AND item.id = #{poolItemId}",
            "  AND item.is_deleted = b'0'",
            "LIMIT 1",
            "FOR UPDATE"
    })
    PoolItemLockRow selectPoolItemForUpdate(
            @Param("poolId") Long poolId,
            @Param("poolItemId") Long poolItemId
    );

    @Select({
            "SELECT",
            "  item.id AS pool_item_id,",
            "  item.pool_id,",
            "  item.owner_user_id,",
            "  item.demand_item_id,",
            "  item.candidate_id,",
            "  item.source_rank_no,",
            "  item.pool_rank_no,",
            "  item.status,",
            "  item.join_source,",
            "  item.inquiry_task_id",
            "FROM procurement_candidate_pool_item item",
            "WHERE item.pool_id = #{poolId}",
            "  AND item.is_current = b'1'",
            "  AND item.is_deleted = b'0'",
            "  AND item.status <> 'REMOVED_TERMINATED'",
            "ORDER BY item.pool_rank_no ASC, item.id ASC",
            "FOR UPDATE"
    })
    List<PoolItemLockRow> listCurrentPoolItemsForUpdate(@Param("poolId") Long poolId);

    @Select({
            "SELECT COUNT(1)",
            "FROM procurement_candidate_pool_item item",
            "WHERE item.pool_id = #{poolId}",
            "  AND item.candidate_id = #{candidateId}",
            "  AND item.is_current = b'1'",
            "  AND item.is_deleted = b'0'",
            "  AND item.status <> 'REMOVED_TERMINATED'"
    })
    Integer countCurrentPoolItemByCandidate(
            @Param("poolId") Long poolId,
            @Param("candidateId") Long candidateId
    );

    @Select({
            "SELECT COALESCE(MAX(snapshot_version), 0)",
            "FROM procurement_candidate_pool_snapshot",
            "WHERE pool_id = #{poolId}",
            "  AND snapshot_type = #{snapshotType}"
    })
    Integer selectMaxSnapshotVersion(
            @Param("poolId") Long poolId,
            @Param("snapshotType") String snapshotType
    );

    @Insert({
            "INSERT INTO procurement_candidate_pool (",
            "  id, owner_user_id, demand_item_id, pool_no, status, max_pool_size, candidate_source_limit, assigned_buyer_id,",
            "  auto_created_at, inquiry_started_at, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{demandItemId}, #{poolNo}, #{status}, #{maxPoolSize}, #{candidateSourceLimit}, #{assignedBuyerId},",
            "  NOW(), CASE WHEN #{status} = 'POOL_INQUIRY_RUNNING' THEN NOW() ELSE NULL END, b'0', #{createdBy}, #{createdBy}, NOW(), NOW()",
            ")"
    })
    int insertCandidatePool(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("demandItemId") Long demandItemId,
            @Param("poolNo") String poolNo,
            @Param("status") String status,
            @Param("maxPoolSize") Integer maxPoolSize,
            @Param("candidateSourceLimit") Integer candidateSourceLimit,
            @Param("assignedBuyerId") Long assignedBuyerId,
            @Param("createdBy") Long createdBy
    );

    @Insert({
            "INSERT INTO procurement_candidate_pool_item (",
            "  id, pool_id, owner_user_id, demand_item_id, candidate_id, source_rank_no, pool_rank_no, status, join_source,",
            "  joined_at, is_current, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{poolId}, #{ownerUserId}, #{demandItemId}, #{candidateId}, #{sourceRankNo}, #{poolRankNo}, #{status}, #{joinSource},",
            "  NOW(), b'1', b'0', #{createdBy}, #{createdBy}, NOW(), NOW()",
            ")"
    })
    int insertCandidatePoolItem(
            @Param("id") Long id,
            @Param("poolId") Long poolId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("demandItemId") Long demandItemId,
            @Param("candidateId") Long candidateId,
            @Param("sourceRankNo") Integer sourceRankNo,
            @Param("poolRankNo") Integer poolRankNo,
            @Param("status") String status,
            @Param("joinSource") String joinSource,
            @Param("createdBy") Long createdBy
    );

    @Insert({
            "INSERT INTO procurement_auto_inquiry_task (",
            "  id, owner_user_id, demand_item_id, candidate_id, pool_id, pool_item_id, planned_channel, active_channel, channel_fallback_reason,",
            "  external_inquiry_id, external_inquiry_url, external_result_status, external_result_payload, reply_source, reply_parse_status, reply_parse_error,",
            "  session_id, platform, status, execution_stage, attempt_no, max_attempts,",
            "  target_offer_id, target_supplier_identity, target_entry_url, target_locator_text, input_preview_text, input_payload_text, input_payload_hash,",
            "  input_locator, send_channel, send_evidence, thread_checkpoint, last_message_digest,",
            "  failure_code, failure_message, handoff_reason, message, started_at, sent_at, no_reply_deadline_at, confirmed_at, finished_at, last_event_at,",
            "  is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{demandItemId}, #{candidateId}, #{poolId}, #{poolItemId}, 'ALI_UNPAID_ORDER_INQUIRY', 'NUONO_CHAT_INQUIRY',",
            "  '1688 unpaid order adapter is not enabled; fallback to Nuono chat inquiry.', NULL, NULL, NULL, NULL, 'CHAT_THREAD', 'PENDING', NULL,",
            "  NULL, #{platform}, #{status}, #{executionStage}, #{attemptNo}, #{maxAttempts},",
            "  NULL, NULL, NULL, NULL, NULL, NULL, NULL,",
            "  NULL, NULL, NULL, NULL, NULL,",
            "  NULL, NULL, NULL, #{message}, NULL, NULL, NULL, NULL, NULL, NOW(),",
            "  b'0', #{createdBy}, #{createdBy}, NOW(), NOW()",
            ")"
    })
    int insertPoolAutoInquiryTask(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("demandItemId") Long demandItemId,
            @Param("candidateId") Long candidateId,
            @Param("poolId") Long poolId,
            @Param("poolItemId") Long poolItemId,
            @Param("platform") String platform,
            @Param("status") String status,
            @Param("executionStage") String executionStage,
            @Param("attemptNo") Integer attemptNo,
            @Param("maxAttempts") Integer maxAttempts,
            @Param("message") String message,
            @Param("createdBy") Long createdBy
    );

    @Insert({
            "INSERT INTO procurement_candidate_pool_snapshot (",
            "  id, pool_id, owner_user_id, demand_item_id, snapshot_type, snapshot_version, pool_status, item_count,",
            "  snapshot_json, remark, created_by, gmt_create",
            ") VALUES (",
            "  #{id}, #{poolId}, #{ownerUserId}, #{demandItemId}, #{snapshotType}, #{snapshotVersion}, #{poolStatus}, #{itemCount},",
            "  #{snapshotJson}, #{remark}, #{createdBy}, NOW()",
            ")"
    })
    int insertCandidatePoolSnapshot(
            @Param("id") Long id,
            @Param("poolId") Long poolId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("demandItemId") Long demandItemId,
            @Param("snapshotType") String snapshotType,
            @Param("snapshotVersion") Integer snapshotVersion,
            @Param("poolStatus") String poolStatus,
            @Param("itemCount") Integer itemCount,
            @Param("snapshotJson") String snapshotJson,
            @Param("remark") String remark,
            @Param("createdBy") Long createdBy
    );

    @Insert({
            "INSERT INTO procurement_pool_operation_log (",
            "  id, pool_id, pool_item_id, owner_user_id, demand_item_id, candidate_id, offer_id, operation_type, operator_user_id,",
            "  operator_role, before_status, after_status, snapshot_id, operation_reason, detail_json, gmt_create",
            ") VALUES (",
            "  #{id}, #{poolId}, #{poolItemId}, #{ownerUserId}, #{demandItemId}, #{candidateId}, #{offerId}, #{operationType}, #{operatorUserId},",
            "  #{operatorRole}, #{beforeStatus}, #{afterStatus}, #{snapshotId}, #{operationReason}, #{detailJson}, NOW()",
            ")"
    })
    int insertPoolOperationLog(
            @Param("id") Long id,
            @Param("poolId") Long poolId,
            @Param("poolItemId") Long poolItemId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("demandItemId") Long demandItemId,
            @Param("candidateId") Long candidateId,
            @Param("offerId") String offerId,
            @Param("operationType") String operationType,
            @Param("operatorUserId") Long operatorUserId,
            @Param("operatorRole") String operatorRole,
            @Param("beforeStatus") String beforeStatus,
            @Param("afterStatus") String afterStatus,
            @Param("snapshotId") Long snapshotId,
            @Param("operationReason") String operationReason,
            @Param("detailJson") String detailJson
    );

    @Insert({
            "INSERT INTO procurement_final_candidate (",
            "  id, pool_id, pool_item_id, owner_user_id, demand_item_id, candidate_id, final_pick_type, snapshot_id,",
            "  decision_note, confirmed_by, confirmed_at, is_deleted, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{poolId}, #{poolItemId}, #{ownerUserId}, #{demandItemId}, #{candidateId}, #{finalPickType}, #{snapshotId},",
            "  #{decisionNote}, #{confirmedBy}, NOW(), b'0', #{confirmedBy}, #{confirmedBy}, NOW(), NOW()",
            ")"
    })
    int insertFinalCandidate(
            @Param("id") Long id,
            @Param("poolId") Long poolId,
            @Param("poolItemId") Long poolItemId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("demandItemId") Long demandItemId,
            @Param("candidateId") Long candidateId,
            @Param("finalPickType") String finalPickType,
            @Param("snapshotId") Long snapshotId,
            @Param("decisionNote") String decisionNote,
            @Param("confirmedBy") Long confirmedBy
    );

    @Update({
            "UPDATE procurement_demand_item",
            "SET current_pool_id = #{poolId},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{demandItemId}",
            "  AND is_deleted = b'0'"
    })
    int updateDemandCurrentPool(
            @Param("demandItemId") Long demandItemId,
            @Param("poolId") Long poolId,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_candidate_pool_item",
            "SET inquiry_task_id = #{taskId},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{poolItemId}",
            "  AND is_deleted = b'0'"
    })
    int updatePoolItemInquiryTask(
            @Param("poolItemId") Long poolItemId,
            @Param("taskId") Long taskId,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_candidate_pool_item",
            "SET status = 'REMOVED_TERMINATED',",
            "    is_current = b'0',",
            "    removed_at = NOW(),",
            "    removed_by = #{removedBy},",
            "    remove_reason = #{removeReason},",
            "    closed_at = COALESCE(closed_at, NOW()),",
            "    updated_by = #{removedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{poolItemId}",
            "  AND pool_id = #{poolId}",
            "  AND is_deleted = b'0'"
    })
    int terminatePoolItem(
            @Param("poolId") Long poolId,
            @Param("poolItemId") Long poolItemId,
            @Param("removeReason") String removeReason,
            @Param("removedBy") Long removedBy
    );

    @Update({
            "UPDATE procurement_auto_inquiry_task",
            "SET status = 'CLOSED',",
            "    execution_stage = 'TERMINATED',",
            "    handoff_reason = 'POOL_ITEM_REMOVED',",
            "    message = #{message},",
            "    finished_at = NOW(),",
            "    last_event_at = NOW(),",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND status IN ('PENDING', 'RUNNING', 'RETRYING', 'SENT', 'CHATTING')",
            "  AND is_deleted = b'0'"
    })
    int terminateAutoInquiryTask(
            @Param("taskId") Long taskId,
            @Param("message") String message,
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
            "WHERE leased_task_id = #{taskId}",
            "  AND is_deleted = b'0'"
    })
    int releaseAutoInquirySessionByTask(
            @Param("taskId") Long taskId,
            @Param("note") String note,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_candidate_pool_item item",
            "JOIN procurement_auto_inquiry_task task ON task.id = #{taskId}",
            "SET item.status = CASE",
            "      WHEN task.status = 'SENT' THEN 'IN_POOL_WAITING_REPLY'",
            "      WHEN task.status = 'HANDOFF' THEN 'SEND_FAILED'",
            "      ELSE item.status",
            "    END,",
            "    item.first_sent_at = CASE WHEN task.status = 'SENT' THEN COALESCE(item.first_sent_at, task.sent_at, NOW()) ELSE item.first_sent_at END,",
            "    item.no_reply_deadline_at = CASE WHEN task.status = 'SENT' THEN DATE_ADD(COALESCE(item.first_sent_at, task.sent_at, NOW()), INTERVAL 24 HOUR) ELSE item.no_reply_deadline_at END,",
            "    item.closed_at = CASE WHEN task.status = 'HANDOFF' THEN COALESCE(item.closed_at, NOW()) ELSE item.closed_at END,",
            "    item.updated_by = #{updatedBy},",
            "    item.gmt_updated = NOW()",
            "WHERE item.id = #{poolItemId}",
            "  AND item.is_deleted = b'0'"
    })
    int syncPoolItemFromAutoInquiryTask(
            @Param("poolItemId") Long poolItemId,
            @Param("taskId") Long taskId,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_candidate_pool_item",
            "SET status = #{status},",
            "    last_reply_at = NOW(),",
            "    quote_price_text = #{quotePriceText},",
            "    quote_moq_text = #{quoteMoqText},",
            "    quote_delivery_text = #{quoteDeliveryText},",
            "    reply_summary = #{replySummary},",
            "    risk_note = #{riskNote},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{poolItemId}",
            "  AND pool_id = #{poolId}",
            "  AND is_deleted = b'0'",
            "  AND is_current = b'1'"
    })
    int updatePoolItemReply(
            @Param("poolId") Long poolId,
            @Param("poolItemId") Long poolItemId,
            @Param("status") String status,
            @Param("quotePriceText") String quotePriceText,
            @Param("quoteMoqText") String quoteMoqText,
            @Param("quoteDeliveryText") String quoteDeliveryText,
            @Param("replySummary") String replySummary,
            @Param("riskNote") String riskNote,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_candidate_pool_item",
            "SET status = #{status},",
            "    last_follow_up_at = NOW(),",
            "    reply_summary = #{replySummary},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{poolItemId}",
            "  AND pool_id = #{poolId}",
            "  AND is_deleted = b'0'",
            "  AND is_current = b'1'"
    })
    int updatePoolItemFollowUp(
            @Param("poolId") Long poolId,
            @Param("poolItemId") Long poolItemId,
            @Param("status") String status,
            @Param("replySummary") String replySummary,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_candidate_pool_item",
            "SET status = #{status},",
            "    closed_at = NOW(),",
            "    reply_summary = #{replySummary},",
            "    risk_note = #{riskNote},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{poolItemId}",
            "  AND pool_id = #{poolId}",
            "  AND is_deleted = b'0'",
            "  AND is_current = b'1'"
    })
    int updatePoolItemHandoff(
            @Param("poolId") Long poolId,
            @Param("poolItemId") Long poolItemId,
            @Param("status") String status,
            @Param("replySummary") String replySummary,
            @Param("riskNote") String riskNote,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_auto_inquiry_task",
            "SET status = 'CHATTING',",
            "    execution_stage = 'REPLY_RECORDED',",
            "    last_message_digest = #{lastMessageDigest},",
            "    message = #{message},",
            "    last_event_at = NOW(),",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND status IN ('PENDING', 'RUNNING', 'RETRYING', 'SENT', 'CHATTING')",
            "  AND is_deleted = b'0'"
    })
    int markPoolAutoInquiryTaskChatting(
            @Param("taskId") Long taskId,
            @Param("lastMessageDigest") String lastMessageDigest,
            @Param("message") String message,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_auto_inquiry_task",
            "SET status = CASE WHEN status = 'PENDING' THEN 'SENT' ELSE status END,",
            "    execution_stage = #{executionStage},",
            "    message = #{message},",
            "    last_event_at = NOW(),",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}",
            "  AND status IN ('PENDING', 'RUNNING', 'RETRYING', 'SENT', 'CHATTING')",
            "  AND is_deleted = b'0'"
    })
    int markPoolAutoInquiryTaskFollowUp(
            @Param("taskId") Long taskId,
            @Param("executionStage") String executionStage,
            @Param("message") String message,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            "SELECT",
            "  item.id AS pool_item_id,",
            "  item.pool_id,",
            "  item.owner_user_id,",
            "  item.demand_item_id,",
            "  item.candidate_id,",
            "  item.source_rank_no,",
            "  item.pool_rank_no,",
            "  item.status,",
            "  item.join_source,",
            "  item.inquiry_task_id",
            "FROM procurement_candidate_pool_item item",
            "JOIN procurement_candidate_pool pool ON pool.id = item.pool_id AND pool.is_deleted = b'0'",
            "JOIN procurement_auto_inquiry_task task ON task.id = item.inquiry_task_id AND task.is_deleted = b'0'",
            "WHERE item.is_current = b'1'",
            "  AND item.is_deleted = b'0'",
            "  AND item.status = 'IN_POOL_WAITING_SEND'",
            "  AND pool.status IN ('POOL_CREATED', 'POOL_INQUIRY_RUNNING', 'POOL_PARTIAL_HANDOFF', 'POOL_EMPTY_REQUIRES_ACTION')",
            "  AND task.status IN ('PENDING', 'RETRYING')",
            "ORDER BY item.gmt_create ASC, item.id ASC",
            "LIMIT #{limit}"
    })
    List<PoolItemLockRow> listPendingPoolItemsForAutoInquiryDispatch(@Param("limit") Integer limit);

    @Select({
            "SELECT",
            "  item.id AS pool_item_id,",
            "  item.pool_id,",
            "  item.owner_user_id,",
            "  item.demand_item_id,",
            "  item.candidate_id,",
            "  item.source_rank_no,",
            "  item.pool_rank_no,",
            "  item.status,",
            "  item.join_source,",
            "  item.inquiry_task_id",
            "FROM procurement_candidate_pool_item item",
            "JOIN procurement_candidate_pool pool ON pool.id = item.pool_id AND pool.is_deleted = b'0'",
            "WHERE item.is_current = b'1'",
            "  AND item.is_deleted = b'0'",
            "  AND pool.status IN ('POOL_INQUIRY_RUNNING', 'POOL_PARTIAL_HANDOFF')",
            "  AND item.no_reply_deadline_at IS NOT NULL",
            "  AND item.no_reply_deadline_at <= NOW()",
            "  AND item.status IN ('IN_POOL_WAITING_REPLY', 'FOLLOW_UP_1_SENT', 'FOLLOW_UP_2_SENT', 'FOLLOW_UP_3_SENT')",
            "ORDER BY item.no_reply_deadline_at ASC, item.id ASC",
            "LIMIT #{limit}"
    })
    List<PoolItemLockRow> listDuePoolItemsForNoReplyHandoff(@Param("limit") Integer limit);

    @Select({
            "SELECT",
            "  item.id AS pool_item_id,",
            "  item.pool_id,",
            "  item.owner_user_id,",
            "  item.demand_item_id,",
            "  item.candidate_id,",
            "  item.source_rank_no,",
            "  item.pool_rank_no,",
            "  item.status,",
            "  item.join_source,",
            "  item.inquiry_task_id",
            "FROM procurement_candidate_pool_item item",
            "JOIN procurement_candidate_pool pool ON pool.id = item.pool_id AND pool.is_deleted = b'0'",
            "WHERE item.is_current = b'1'",
            "  AND item.is_deleted = b'0'",
            "  AND pool.status IN ('POOL_INQUIRY_RUNNING', 'POOL_PARTIAL_HANDOFF')",
            "  AND (item.no_reply_deadline_at IS NULL OR item.no_reply_deadline_at > NOW())",
            "  AND (",
            "    (item.status = 'IN_POOL_WAITING_REPLY' AND item.first_sent_at IS NOT NULL AND item.first_sent_at <= DATE_SUB(NOW(), INTERVAL 15 MINUTE))",
            "    OR (item.status = 'FOLLOW_UP_1_SENT' AND item.last_follow_up_at IS NOT NULL AND item.last_follow_up_at <= DATE_SUB(NOW(), INTERVAL 30 MINUTE))",
            "    OR (item.status = 'FOLLOW_UP_2_SENT' AND item.last_follow_up_at IS NOT NULL AND item.last_follow_up_at <= DATE_SUB(NOW(), INTERVAL 3 HOUR))",
            "  )",
            "ORDER BY COALESCE(item.last_follow_up_at, item.first_sent_at, item.gmt_create) ASC, item.id ASC",
            "LIMIT #{limit}"
    })
    List<PoolItemLockRow> listDuePoolItemsForFollowUp(@Param("limit") Integer limit);

    @Update({
            "UPDATE procurement_candidate_pool",
            "SET status = #{status},",
            "    current_snapshot_id = #{snapshotId},",
            "    inquiry_started_at = CASE WHEN #{status} = 'POOL_INQUIRY_RUNNING' THEN COALESCE(inquiry_started_at, NOW()) ELSE inquiry_started_at END,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{poolId}",
            "  AND is_deleted = b'0'"
    })
    int updatePoolAfterItemChange(
            @Param("poolId") Long poolId,
            @Param("status") String status,
            @Param("snapshotId") Long snapshotId,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_candidate_pool",
            "SET status = 'POOL_INQUIRY_FINISHED',",
            "    current_snapshot_id = #{snapshotId},",
            "    inquiry_finished_at = NOW(),",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{poolId}",
            "  AND is_deleted = b'0'"
    })
    int updatePoolInquiryFinished(
            @Param("poolId") Long poolId,
            @Param("snapshotId") Long snapshotId,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_candidate_pool",
            "SET status = 'FINAL_TWO_CONFIRMED',",
            "    current_snapshot_id = #{snapshotId},",
            "    final_confirmed_at = NOW(),",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{poolId}",
            "  AND is_deleted = b'0'"
    })
    int updatePoolFinalConfirmed(
            @Param("poolId") Long poolId,
            @Param("snapshotId") Long snapshotId,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_candidate_pool",
            "SET status = 'SUMMARY_READY',",
            "    current_snapshot_id = #{snapshotId},",
            "    summary_text = #{summaryText},",
            "    summary_input_snapshot_id = #{snapshotId},",
            "    summary_ready_at = NOW(),",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{poolId}",
            "  AND is_deleted = b'0'"
    })
    int updatePoolSummaryReady(
            @Param("poolId") Long poolId,
            @Param("snapshotId") Long snapshotId,
            @Param("summaryText") String summaryText,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE procurement_final_candidate",
            "SET is_deleted = b'1',",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE pool_id = #{poolId}",
            "  AND demand_item_id = #{demandItemId}",
            "  AND is_deleted = b'0'"
    })
    int softDeleteFinalCandidates(
            @Param("poolId") Long poolId,
            @Param("demandItemId") Long demandItemId,
            @Param("updatedBy") Long updatedBy
    );
}
