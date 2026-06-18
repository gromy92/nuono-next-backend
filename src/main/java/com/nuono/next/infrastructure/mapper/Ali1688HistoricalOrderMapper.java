package com.nuono.next.infrastructure.mapper;

import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRow;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderExcelImportBatchRow;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderExcelImportRow;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderItemAssignmentRow;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderItemAssignmentSummaryRow;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderItemRow;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderLogisticsRow;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProductLinkAuditRow;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProductLinkCandidateRow;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProductLinkRow;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderQuery;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderRow;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderSyncTaskRow;
import com.nuono.next.procurement.aliorder.Ali1688SkuPurchaseBatchRow;
import com.nuono.next.procurement.aliorder.Ali1688SkuPurchaseBatchSourceRow;
import com.nuono.next.procurement.aliorder.Ali1688SkuPurchaseHistoryRow;
import com.nuono.next.procurement.aliorder.Ali1688SkuPurchaseHistoryProductRow;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface Ali1688HistoricalOrderMapper {

    @Insert({
            "INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, #{initialValue}, NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE next_id = LAST_INSERT_ID(next_id + 1), gmt_updated = NOW()"
    })
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "allocatedId", before = false, resultType = Long.class)
    int allocateId(IdSequenceCommand command);

    default Long nextId(String sequenceName, Long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        allocateId(command);
        return command.getAllocatedId();
    }

    default Long nextAuthorizationId() {
        return nextId("procurement_ali1688_order_authorization", 91000L);
    }

    default Long nextSyncTaskId() {
        return nextId("procurement_ali1688_order_sync_task", 92000L);
    }

    default Long nextOrderId() {
        return nextId("procurement_ali1688_order_header", 93000L);
    }

    default Long nextOrderItemId() {
        return nextId("procurement_ali1688_order_item", 94000L);
    }

    default Long nextOrderLogisticsId() {
        return nextId("procurement_ali1688_order_logistics", 95000L);
    }

    default Long nextOrderStoreBindingId() {
        return nextId("procurement_ali1688_order_store_binding", 96000L);
    }

    default Long nextExcelImportBatchId() {
        return nextId("procurement_ali1688_order_excel_import_batch", 97000L);
    }

    default Long nextExcelImportRowId() {
        return nextId("procurement_ali1688_order_excel_import_row", 98000L);
    }

    default Long nextOrderItemAssignmentId() {
        return nextId("procurement_ali1688_order_item_assignment", 99000L);
    }

    default Long nextOrderItemProductLinkId() {
        return nextId("procurement_ali1688_order_item_product_link", 100000L);
    }

    default Long nextOrderItemProductLinkAuditId() {
        return nextId("procurement_ali1688_order_item_product_link_audit", 101000L);
    }

    default Long nextSkuPurchaseBatchId() {
        return nextId("procurement_ali1688_sku_purchase_batch", 102000L);
    }

    default Long nextSkuPurchaseBatchSourceId() {
        return nextId("procurement_ali1688_sku_purchase_batch_source", 103000L);
    }

    @Select({
            "SELECT",
            "  id, owner_user_id, provider_code, provider_account_id, account_label, status,",
            "  scope_summary, access_token_cipher, refresh_token_cipher, expires_at, revoked_at, created_by, updated_by",
            "FROM procurement_ali1688_order_authorization",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'",
            "  AND status = 'authorized'",
            "ORDER BY gmt_updated DESC, id DESC",
            "LIMIT 1"
    })
    Ali1688HistoricalOrderAuthorizationRow selectCurrentAuthorization(@Param("ownerUserId") Long ownerUserId);

    @Select({
            "SELECT",
            "  id, owner_user_id, provider_code, provider_account_id, account_label, status,",
            "  scope_summary, access_token_cipher, refresh_token_cipher, expires_at, revoked_at, created_by, updated_by",
            "FROM procurement_ali1688_order_authorization",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND id = #{authorizationId}",
            "  AND is_deleted = b'0'",
            "  AND status = 'authorized'",
            "LIMIT 1"
    })
    Ali1688HistoricalOrderAuthorizationRow selectAuthorizationById(
            @Param("ownerUserId") Long ownerUserId,
            @Param("authorizationId") Long authorizationId
    );

    @Select({
            "SELECT",
            "  id, owner_user_id, provider_code, provider_account_id, account_label, status,",
            "  scope_summary, access_token_cipher, refresh_token_cipher, expires_at, revoked_at, created_by, updated_by",
            "FROM procurement_ali1688_order_authorization",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND provider_code = #{providerCode}",
            "  AND provider_account_id = #{providerAccountId}",
            "  AND is_deleted = b'0'",
            "  AND status = 'authorized'",
            "ORDER BY gmt_updated DESC, id DESC",
            "LIMIT 1"
    })
    Ali1688HistoricalOrderAuthorizationRow selectAuthorizationByProviderAccount(
            @Param("ownerUserId") Long ownerUserId,
            @Param("providerCode") String providerCode,
            @Param("providerAccountId") String providerAccountId
    );

    @Select({
            "<script>",
            "SELECT auth.id",
            "FROM procurement_ali1688_order_authorization auth",
            "LEFT JOIN procurement_ali1688_order_store_binding binding",
            "  ON binding.authorization_id = auth.id",
            "  AND binding.owner_user_id = auth.owner_user_id",
            "  AND binding.status = 'active'",
            "  AND binding.is_deleted = b'0'",
            "WHERE auth.owner_user_id = #{ownerUserId}",
            "  AND auth.is_deleted = b'0'",
            "  AND auth.status = 'authorized'",
            "  <choose>",
            "    <when test='storeCode != null and storeCode != \"\"'>",
            "      AND binding.id IS NOT NULL",
            "      AND (binding.store_code = '*' OR UPPER(binding.store_code) = UPPER(#{storeCode}))",
            "      <if test='siteCode != null and siteCode != \"\"'>",
            "        AND (binding.site_code = '*' OR UPPER(binding.site_code) = UPPER(#{siteCode}))",
            "      </if>",
            "      AND (",
            "        binding.assignment_mode != 'owner_wide_default'",
            "        OR auth.id = (",
            "          SELECT current_auth.id",
            "          FROM procurement_ali1688_order_authorization current_auth",
            "          WHERE current_auth.owner_user_id = #{ownerUserId}",
            "            AND current_auth.is_deleted = b'0'",
            "            AND current_auth.status = 'authorized'",
            "          ORDER BY current_auth.gmt_updated DESC, current_auth.id DESC",
            "          LIMIT 1",
            "        )",
            "      )",
            "    </when>",
            "    <otherwise>",
            "      AND 1 = 1",
            "    </otherwise>",
            "  </choose>",
            "GROUP BY auth.id, auth.gmt_updated",
            "ORDER BY MIN(COALESCE(binding.priority, 100)), auth.gmt_updated DESC, auth.id DESC",
            "</script>"
    })
    List<Long> listVisibleAuthorizationIds(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );

    @Select({
            "<script>",
            "SELECT",
            "  auth.id, auth.owner_user_id, auth.provider_code, auth.provider_account_id, auth.account_label, auth.status,",
            "  auth.scope_summary, auth.access_token_cipher, auth.refresh_token_cipher,",
            "  auth.expires_at, auth.revoked_at, auth.created_by, auth.updated_by",
            "FROM procurement_ali1688_order_authorization auth",
            "JOIN procurement_ali1688_order_store_binding binding",
            "  ON binding.authorization_id = auth.id",
            "  AND binding.owner_user_id = auth.owner_user_id",
            "  AND binding.status = 'active'",
            "  AND binding.is_deleted = b'0'",
            "WHERE auth.owner_user_id = #{ownerUserId}",
            "  AND auth.provider_code = #{providerCode}",
            "  AND auth.is_deleted = b'0'",
            "  AND auth.status = 'authorized'",
            "  AND (binding.store_code = '*' OR UPPER(binding.store_code) = UPPER(#{storeCode}))",
            "  <if test='siteCode != null and siteCode != \"\"'>",
            "    AND (binding.site_code = '*' OR UPPER(binding.site_code) = UPPER(#{siteCode}))",
            "  </if>",
            "GROUP BY auth.id, auth.gmt_updated",
            "ORDER BY MIN(COALESCE(binding.priority, 100)), auth.gmt_updated DESC, auth.id DESC",
            "</script>"
    })
    List<Ali1688HistoricalOrderAuthorizationRow> listExcelUploadAuthorizations(
            @Param("ownerUserId") Long ownerUserId,
            @Param("providerCode") String providerCode,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );

    @Insert({
            "INSERT INTO procurement_ali1688_order_authorization (",
            "  id, owner_user_id, provider_code, provider_account_id, account_label, status,",
            "  scope_summary, access_token_cipher, refresh_token_cipher, expires_at, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{providerCode}, #{providerAccountId}, #{accountLabel}, #{status},",
            "  #{scopeSummary}, #{accessTokenCipher}, #{refreshTokenCipher}, #{expiresAt}, #{createdBy}, #{updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertAuthorization(Ali1688HistoricalOrderAuthorizationRow row);

    @Update({
            "UPDATE procurement_ali1688_order_authorization",
            "SET provider_account_id = #{providerAccountId},",
            "    account_label = #{accountLabel},",
            "    status = #{status},",
            "    scope_summary = #{scopeSummary},",
            "    access_token_cipher = #{accessTokenCipher},",
            "    refresh_token_cipher = #{refreshTokenCipher},",
            "    expires_at = #{expiresAt},",
            "    revoked_at = NULL,",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{id}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'"
    })
    int updateAuthorizationTokens(Ali1688HistoricalOrderAuthorizationRow row);

    @Insert({
            "INSERT INTO procurement_ali1688_order_store_binding (",
            "  id, owner_user_id, authorization_id, store_code, site_code, status, priority,",
            "  assignment_mode, remark, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{authorizationId}, '*', '*', 'active', 100,",
            "  'owner_wide_default', '授权默认老板级可见；配置具体店铺绑定后按店铺过滤。',",
            "  #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ") ON DUPLICATE KEY UPDATE",
            "  status = 'active',",
            "  assignment_mode = VALUES(assignment_mode),",
            "  remark = VALUES(remark),",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int insertOwnerWideStoreBinding(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("authorizationId") Long authorizationId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO procurement_ali1688_order_store_binding (",
            "  id, owner_user_id, authorization_id, store_code, site_code, status, priority,",
            "  assignment_mode, remark, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{authorizationId}, #{storeCode}, COALESCE(#{siteCode}, '*'), 'active', 10,",
            "  'explicit', #{remark},",
            "  #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ") ON DUPLICATE KEY UPDATE",
            "  status = 'active',",
            "  priority = VALUES(priority),",
            "  assignment_mode = VALUES(assignment_mode),",
            "  remark = VALUES(remark),",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int insertExplicitStoreBinding(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("authorizationId") Long authorizationId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("operatorUserId") Long operatorUserId,
            @Param("remark") String remark
    );

    @Insert({
            "INSERT INTO procurement_ali1688_order_excel_import_batch (",
            "  id, owner_user_id, authorization_id, store_code, site_code, file_name, file_size, file_hash,",
            "  status, header_version, order_header_row_count, product_line_count, logistics_line_count,",
            "  valid_row_count, duplicate_candidate_count, error_count, warning_count,",
            "  failure_code, failure_message, error_summary_json, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{authorizationId}, #{storeCode}, COALESCE(#{siteCode}, '*'),",
            "  #{fileName}, #{fileSize}, #{fileHash}, #{status}, #{headerVersion},",
            "  #{orderHeaderRowCount}, #{productLineCount}, #{logisticsLineCount}, #{validRowCount},",
            "  #{duplicateCandidateCount}, #{errorCount}, #{warningCount}, #{failureCode}, #{failureMessage},",
            "  #{errorSummaryJson}, #{createdBy}, #{updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertExcelImportBatch(Ali1688HistoricalOrderExcelImportBatchRow row);

    @Insert({
            "INSERT INTO procurement_ali1688_order_excel_import_row (",
            "  id, batch_id, owner_user_id, authorization_id, `row_number`, continuation_row, order_no,",
            "  buyer_company_name, buyer_member_name, supplier_name, seller_member_name, goods_total_text,",
            "  freight_text, adjustment_text, paid_amount_text, order_status, order_time, paid_at, shipper_name,",
            "  receiver_name, receiver_postal_code, receiver_telephone, receiver_mobile, receiver_address, buyer_remark,",
            "  title, offer_id, sku_id, product_code, model_text, single_product_code, quantity_text, unit, unit_price_text,",
            "  logistics_company, tracking_no, source_batch_no, downstream_channel, downstream_order_no, initiator_login_name,",
            "  raw_snapshot_json, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{batchId}, #{ownerUserId}, #{authorizationId}, #{rowNumber}, #{continuationRow}, #{orderNo},",
            "  #{buyerCompanyName}, #{buyerMemberName}, #{supplierName}, #{sellerMemberName}, #{goodsTotalText},",
            "  #{freightText}, #{adjustmentText}, #{paidAmountText}, #{orderStatus}, #{orderTime}, #{paidAt}, #{shipperName},",
            "  #{receiverName}, #{receiverPostalCode}, #{receiverTelephone}, #{receiverMobile}, #{receiverAddress}, #{buyerRemark},",
            "  #{title}, #{offerId}, #{skuId}, #{productCode}, #{modelText}, #{singleProductCode}, #{quantityText}, #{unit}, #{unitPriceText},",
            "  #{logisticsCompany}, #{trackingNo}, #{sourceBatchNo}, #{downstreamChannel}, #{downstreamOrderNo}, #{initiatorLoginName},",
            "  #{rawSnapshotJson}, NOW(), NOW()",
            ")"
    })
    int insertExcelImportRow(Ali1688HistoricalOrderExcelImportRow row);

    @Select({
            "SELECT id, owner_user_id, authorization_id, store_code, site_code, file_name, file_size, file_hash,",
            "  status, header_version, order_header_row_count, product_line_count, logistics_line_count,",
            "  valid_row_count, duplicate_candidate_count, error_count, warning_count,",
            "  failure_code, failure_message, error_summary_json, created_by, updated_by",
            "FROM procurement_ali1688_order_excel_import_batch",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND id = #{batchId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    Ali1688HistoricalOrderExcelImportBatchRow selectExcelImportBatch(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId
    );

    @Select({
            "SELECT id, batch_id, owner_user_id, authorization_id, `row_number`, continuation_row, order_no,",
            "  buyer_company_name, buyer_member_name, supplier_name, seller_member_name, goods_total_text,",
            "  freight_text, adjustment_text, paid_amount_text, order_status, order_time, paid_at, shipper_name,",
            "  receiver_name, receiver_postal_code, receiver_telephone, receiver_mobile, receiver_address, buyer_remark,",
            "  title, offer_id, sku_id, product_code, model_text, single_product_code, quantity_text, unit, unit_price_text,",
            "  logistics_company, tracking_no, source_batch_no, downstream_channel, downstream_order_no, initiator_login_name,",
            "  raw_snapshot_json",
            "FROM procurement_ali1688_order_excel_import_row",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND batch_id = #{batchId}",
            "  AND is_deleted = b'0'",
            "ORDER BY `row_number` ASC, id ASC"
    })
    List<Ali1688HistoricalOrderExcelImportRow> listExcelImportRows(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId
    );

    @Select({
            "<script>",
            "SELECT",
            "  batch.id, batch.owner_user_id, batch.authorization_id, batch.store_code, batch.site_code,",
            "  batch.file_name, batch.file_size, batch.file_hash, batch.status, batch.header_version,",
            "  batch.order_header_row_count, batch.product_line_count, batch.logistics_line_count,",
            "  batch.valid_row_count, batch.duplicate_candidate_count, batch.error_count, batch.warning_count,",
            "  batch.failure_code, batch.failure_message, batch.error_summary_json, batch.created_by, batch.updated_by,",
            "  DATE_FORMAT(batch.gmt_create, '%Y-%m-%d %H:%i:%s') AS created_at,",
            "  DATE_FORMAT(batch.gmt_updated, '%Y-%m-%d %H:%i:%s') AS updated_at,",
            "  auth.provider_code, auth.account_label",
            "FROM procurement_ali1688_order_excel_import_batch batch",
            "JOIN procurement_ali1688_order_authorization auth",
            "  ON auth.id = batch.authorization_id",
            "  AND auth.owner_user_id = batch.owner_user_id",
            "  AND auth.is_deleted = b'0'",
            "WHERE batch.owner_user_id = #{ownerUserId}",
            "  AND batch.authorization_id IN",
            "  <foreach collection='authorizationIds' item='authorizationId' open='(' separator=',' close=')'>",
            "    #{authorizationId}",
            "  </foreach>",
            "  AND UPPER(batch.store_code) = UPPER(#{storeCode})",
            "  <if test='siteCode != null and siteCode != \"\"'>",
            "    AND (batch.site_code = '*' OR UPPER(batch.site_code) = UPPER(#{siteCode}))",
            "  </if>",
            "  AND batch.is_deleted = b'0'",
            "ORDER BY batch.gmt_updated DESC, batch.id DESC",
            "LIMIT #{limit}",
            "</script>"
    })
    List<Ali1688HistoricalOrderExcelImportBatchRow> listExcelImportBatches(
            @Param("ownerUserId") Long ownerUserId,
            @Param("authorizationIds") List<Long> authorizationIds,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("limit") Integer limit
    );

    @Select({
            "<script>",
            "SELECT",
            "  batch.id, batch.owner_user_id, batch.authorization_id, batch.store_code, batch.site_code,",
            "  batch.file_name, batch.file_size, batch.file_hash, batch.status, batch.header_version,",
            "  batch.order_header_row_count, batch.product_line_count, batch.logistics_line_count,",
            "  batch.valid_row_count, batch.duplicate_candidate_count, batch.error_count, batch.warning_count,",
            "  batch.failure_code, batch.failure_message, batch.error_summary_json, batch.created_by, batch.updated_by,",
            "  DATE_FORMAT(batch.gmt_create, '%Y-%m-%d %H:%i:%s') AS created_at,",
            "  DATE_FORMAT(batch.gmt_updated, '%Y-%m-%d %H:%i:%s') AS updated_at,",
            "  auth.provider_code, auth.account_label",
            "FROM procurement_ali1688_order_excel_import_batch batch",
            "JOIN procurement_ali1688_order_authorization auth",
            "  ON auth.id = batch.authorization_id",
            "  AND auth.owner_user_id = batch.owner_user_id",
            "  AND auth.is_deleted = b'0'",
            "WHERE batch.owner_user_id = #{ownerUserId}",
            "  AND batch.id = #{batchId}",
            "  AND batch.authorization_id IN",
            "  <foreach collection='authorizationIds' item='authorizationId' open='(' separator=',' close=')'>",
            "    #{authorizationId}",
            "  </foreach>",
            "  AND UPPER(batch.store_code) = UPPER(#{storeCode})",
            "  <if test='siteCode != null and siteCode != \"\"'>",
            "    AND (batch.site_code = '*' OR UPPER(batch.site_code) = UPPER(#{siteCode}))",
            "  </if>",
            "  AND batch.is_deleted = b'0'",
            "LIMIT 1",
            "</script>"
    })
    Ali1688HistoricalOrderExcelImportBatchRow selectExcelImportBatchForDetail(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchId") Long batchId,
            @Param("authorizationIds") List<Long> authorizationIds,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );

    @Update({
            "UPDATE procurement_ali1688_order_excel_import_batch",
            "SET status = 'committed',",
            "    order_header_row_count = #{orderCount},",
            "    product_line_count = #{itemCount},",
            "    logistics_line_count = #{logisticsCount},",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{batchId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'"
    })
    int markExcelImportBatchCommitted(
            @Param("batchId") Long batchId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("orderCount") Integer orderCount,
            @Param("itemCount") Integer itemCount,
            @Param("logisticsCount") Integer logisticsCount,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE procurement_ali1688_order_authorization",
            "SET status = 'revoked',",
            "    revoked_at = NOW(),",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{authorizationId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'"
    })
    int revokeAuthorization(
            @Param("authorizationId") Long authorizationId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO procurement_ali1688_order_sync_task (",
            "  id, owner_user_id, authorization_id, task_type, status, processed_count, imported_count,",
            "  failed_count, progress_percent, checkpoint_json,",
            "  created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{authorizationId}, #{taskType}, #{status}, #{processedCount}, #{importedCount},",
            "  #{failedCount}, #{progressPercent}, #{checkpointJson},",
            "  #{createdBy}, #{updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertSyncTask(Ali1688HistoricalOrderSyncTaskRow row);

    @Select({
            "SELECT",
            "  id, owner_user_id, authorization_id, task_type, status, processed_count, imported_count,",
            "  failed_count, progress_percent, checkpoint_json, failure_code, failure_message,",
            "  retryable, requires_manual_action, created_by, updated_by",
            "FROM procurement_ali1688_order_sync_task",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND authorization_id = #{authorizationId}",
            "  AND task_type = 'initial_backfill'",
            "  AND status IN ('running', 'failed')",
            "  AND COALESCE(retryable, b'1') = b'1'",
            "  AND is_deleted = b'0'",
            "ORDER BY gmt_updated DESC, id DESC",
            "LIMIT 1"
    })
    Ali1688HistoricalOrderSyncTaskRow selectLatestResumableTask(
            @Param("ownerUserId") Long ownerUserId,
            @Param("authorizationId") Long authorizationId
    );

    @Select({
            "SELECT",
            "  id, owner_user_id, authorization_id, task_type, status, processed_count, imported_count,",
            "  failed_count, progress_percent, checkpoint_json, failure_code, failure_message,",
            "  retryable, requires_manual_action, created_by, updated_by",
            "FROM procurement_ali1688_order_sync_task",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND authorization_id = #{authorizationId}",
            "  AND is_deleted = b'0'",
            "ORDER BY gmt_updated DESC, id DESC",
            "LIMIT 1"
    })
    Ali1688HistoricalOrderSyncTaskRow selectLatestTask(
            @Param("ownerUserId") Long ownerUserId,
            @Param("authorizationId") Long authorizationId
    );

    @Update({
            "UPDATE procurement_ali1688_order_sync_task",
            "SET checkpoint_json = #{checkpointJson},",
            "    progress_percent = #{progressPercent},",
            "    processed_count = #{processedCount},",
            "    imported_count = #{importedCount},",
            "    failed_count = #{failedCount},",
            "    status = 'running',",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}"
    })
    int updateSyncTaskCheckpoint(
            @Param("taskId") Long taskId,
            @Param("checkpointJson") String checkpointJson,
            @Param("progressPercent") Integer progressPercent,
            @Param("processedCount") Integer processedCount,
            @Param("importedCount") Integer importedCount,
            @Param("failedCount") Integer failedCount
    );

    @Update({
            "UPDATE procurement_ali1688_order_sync_task",
            "SET status = 'success',",
            "    processed_count = #{processedCount},",
            "    imported_count = #{importedCount},",
            "    failed_count = #{failedCount},",
            "    progress_percent = 100,",
            "    checkpoint_json = #{checkpointJson},",
            "    failure_code = NULL,",
            "    failure_message = NULL,",
            "    retryable = b'0',",
            "    requires_manual_action = b'0',",
            "    finished_at = NOW(),",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}"
    })
    int markSyncTaskSuccess(
            @Param("taskId") Long taskId,
            @Param("processedCount") Integer processedCount,
            @Param("importedCount") Integer importedCount,
            @Param("failedCount") Integer failedCount,
            @Param("checkpointJson") String checkpointJson
    );

    @Update({
            "UPDATE procurement_ali1688_order_sync_task",
            "SET status = 'partial_success',",
            "    processed_count = #{processedCount},",
            "    imported_count = #{importedCount},",
            "    failed_count = #{failedCount},",
            "    progress_percent = 100,",
            "    failure_code = #{failureCode},",
            "    failure_message = #{failureMessage},",
            "    checkpoint_json = #{checkpointJson},",
            "    retryable = #{retryable},",
            "    requires_manual_action = #{requiresManualAction},",
            "    finished_at = NOW(),",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}"
    })
    int markSyncTaskPartialSuccess(
            @Param("taskId") Long taskId,
            @Param("processedCount") Integer processedCount,
            @Param("importedCount") Integer importedCount,
            @Param("failedCount") Integer failedCount,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("checkpointJson") String checkpointJson,
            @Param("retryable") Boolean retryable,
            @Param("requiresManualAction") Boolean requiresManualAction
    );

    @Update({
            "UPDATE procurement_ali1688_order_sync_task",
            "SET status = 'failed',",
            "    processed_count = #{processedCount},",
            "    imported_count = #{importedCount},",
            "    failed_count = #{failedCount},",
            "    progress_percent = 100,",
            "    failure_code = #{failureCode},",
            "    failure_message = #{failureMessage},",
            "    checkpoint_json = #{checkpointJson},",
            "    retryable = #{retryable},",
            "    requires_manual_action = #{requiresManualAction},",
            "    finished_at = NOW(),",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}"
    })
    int markSyncTaskFailed(
            @Param("taskId") Long taskId,
            @Param("processedCount") Integer processedCount,
            @Param("importedCount") Integer importedCount,
            @Param("failedCount") Integer failedCount,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("checkpointJson") String checkpointJson,
            @Param("retryable") Boolean retryable,
            @Param("requiresManualAction") Boolean requiresManualAction
    );

    @Insert({
            "INSERT INTO procurement_ali1688_order_header (",
            "  id, owner_user_id, authorization_id, order_natural_key, provider_order_no, order_time, supplier_name,",
            "  paid_at, buyer_company_name, buyer_member_name, seller_member_name, goods_total_text, freight_text,",
            "  adjustment_text, paid_amount_text, amount_text, amount_value, currency, order_status, logistics_status,",
            "  shipper_name, original_url, receiver_name, receiver_postal_code, receiver_telephone, receiver_mobile,",
            "  receiver_phone, receiver_address, buyer_remark, supplier_contact, initiator_login_name,",
            "  source_batch_no, downstream_order_no, raw_snapshot_json,",
            "  gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{authorizationId}, #{orderNaturalKey}, #{providerOrderNo}, #{orderTime}, #{supplierName},",
            "  #{paidAt}, #{buyerCompanyName}, #{buyerMemberName}, #{sellerMemberName}, #{goodsTotalText}, #{freightText},",
            "  #{adjustmentText}, #{paidAmountText}, #{amountText}, #{amountValue}, #{currency}, #{orderStatus}, #{logisticsStatus},",
            "  #{shipperName}, #{originalUrl}, #{receiverName}, #{receiverPostalCode}, #{receiverTelephone}, #{receiverMobile},",
            "  #{receiverPhone}, #{receiverAddress}, #{buyerRemark}, #{supplierContact}, #{initiatorLoginName},",
            "  #{sourceBatchNo}, #{downstreamOrderNo}, #{rawSnapshotJson},",
            "  NOW(), NOW()",
            ") ON DUPLICATE KEY UPDATE",
            "  order_time = VALUES(order_time),",
            "  supplier_name = VALUES(supplier_name),",
            "  paid_at = VALUES(paid_at),",
            "  buyer_company_name = VALUES(buyer_company_name),",
            "  buyer_member_name = VALUES(buyer_member_name),",
            "  seller_member_name = VALUES(seller_member_name),",
            "  goods_total_text = VALUES(goods_total_text),",
            "  freight_text = VALUES(freight_text),",
            "  adjustment_text = VALUES(adjustment_text),",
            "  paid_amount_text = VALUES(paid_amount_text),",
            "  amount_text = VALUES(amount_text),",
            "  amount_value = VALUES(amount_value),",
            "  currency = VALUES(currency),",
            "  order_status = VALUES(order_status),",
            "  logistics_status = VALUES(logistics_status),",
            "  shipper_name = VALUES(shipper_name),",
            "  original_url = VALUES(original_url),",
            "  receiver_name = VALUES(receiver_name),",
            "  receiver_postal_code = VALUES(receiver_postal_code),",
            "  receiver_telephone = VALUES(receiver_telephone),",
            "  receiver_mobile = VALUES(receiver_mobile),",
            "  receiver_phone = VALUES(receiver_phone),",
            "  receiver_address = VALUES(receiver_address),",
            "  buyer_remark = VALUES(buyer_remark),",
            "  supplier_contact = VALUES(supplier_contact),",
            "  initiator_login_name = VALUES(initiator_login_name),",
            "  source_batch_no = VALUES(source_batch_no),",
            "  downstream_order_no = VALUES(downstream_order_no),",
            "  raw_snapshot_json = VALUES(raw_snapshot_json),",
            "  is_deleted = b'0',",
            "  gmt_updated = NOW()"
    })
    int upsertOrder(Ali1688HistoricalOrderRow row);

    @Insert({
            "INSERT INTO procurement_ali1688_order_item (",
            "  id, order_id, item_natural_key, offer_id, sku_id, title, sku_text, model_text, product_code,",
            "  single_product_code, quantity, unit, unit_price_text, amount_text, image_url, raw_snapshot_json,",
            "  gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{orderId}, #{itemNaturalKey}, #{offerId}, #{skuId}, #{title}, #{skuText}, #{modelText}, #{productCode},",
            "  #{singleProductCode}, #{quantity}, #{unit}, #{unitPriceText}, #{amountText}, #{imageUrl}, #{rawSnapshotJson},",
            "  NOW(), NOW()",
            ") ON DUPLICATE KEY UPDATE",
            "  offer_id = VALUES(offer_id),",
            "  sku_id = VALUES(sku_id),",
            "  title = VALUES(title),",
            "  sku_text = VALUES(sku_text),",
            "  model_text = VALUES(model_text),",
            "  product_code = VALUES(product_code),",
            "  single_product_code = VALUES(single_product_code),",
            "  quantity = VALUES(quantity),",
            "  unit = VALUES(unit),",
            "  unit_price_text = VALUES(unit_price_text),",
            "  amount_text = VALUES(amount_text),",
            "  image_url = VALUES(image_url),",
            "  raw_snapshot_json = VALUES(raw_snapshot_json),",
            "  is_deleted = b'0',",
            "  gmt_updated = NOW()"
    })
    int upsertOrderItem(Ali1688HistoricalOrderItemRow row);

    @Insert({
            "INSERT INTO procurement_ali1688_order_logistics (",
            "  id, order_id, item_id, logistics_natural_key, logistics_company, tracking_no, raw_snapshot_json,",
            "  gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{orderId}, #{itemId}, #{logisticsNaturalKey}, #{logisticsCompany}, #{trackingNo}, #{rawSnapshotJson},",
            "  NOW(), NOW()",
            ") ON DUPLICATE KEY UPDATE",
            "  logistics_company = VALUES(logistics_company),",
            "  tracking_no = VALUES(tracking_no),",
            "  raw_snapshot_json = VALUES(raw_snapshot_json),",
            "  is_deleted = b'0',",
            "  gmt_updated = NOW()"
    })
    int upsertOrderLogistics(Ali1688HistoricalOrderLogisticsRow row);

    @Select({
            "SELECT id",
            "FROM procurement_ali1688_order_header",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND order_natural_key = #{naturalKey}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    Long selectOrderIdByNaturalKey(
            @Param("ownerUserId") Long ownerUserId,
            @Param("naturalKey") String naturalKey
    );

    @Select({
            "SELECT raw_snapshot_json",
            "FROM procurement_ali1688_order_header",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND order_natural_key = #{naturalKey}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    String selectOrderRawSnapshotByNaturalKey(
            @Param("ownerUserId") Long ownerUserId,
            @Param("naturalKey") String naturalKey
    );

    @Select({
            "SELECT oi.id",
            "FROM procurement_ali1688_order_item oi",
            "JOIN procurement_ali1688_order_header oh ON oh.id = oi.order_id",
            "  AND oh.owner_user_id = #{ownerUserId}",
            "  AND oh.is_deleted = b'0'",
            "WHERE oi.item_natural_key = #{naturalKey}",
            "  AND oi.is_deleted = b'0'",
            "LIMIT 1"
    })
    Long selectOrderItemIdByNaturalKey(
            @Param("ownerUserId") Long ownerUserId,
            @Param("naturalKey") String naturalKey
    );

    @Select({
            "SELECT oi.raw_snapshot_json",
            "FROM procurement_ali1688_order_item oi",
            "JOIN procurement_ali1688_order_header oh ON oh.id = oi.order_id",
            "  AND oh.owner_user_id = #{ownerUserId}",
            "  AND oh.is_deleted = b'0'",
            "WHERE oi.item_natural_key = #{naturalKey}",
            "  AND oi.is_deleted = b'0'",
            "LIMIT 1"
    })
    String selectOrderItemRawSnapshotByNaturalKey(
            @Param("ownerUserId") Long ownerUserId,
            @Param("naturalKey") String naturalKey
    );

    @Select({
            "SELECT logistics.id",
            "FROM procurement_ali1688_order_logistics logistics",
            "JOIN procurement_ali1688_order_header oh ON oh.id = logistics.order_id",
            "  AND oh.owner_user_id = #{ownerUserId}",
            "  AND oh.is_deleted = b'0'",
            "WHERE logistics.logistics_natural_key = #{naturalKey}",
            "  AND logistics.is_deleted = b'0'",
            "LIMIT 1"
    })
    Long selectOrderLogisticsIdByNaturalKey(
            @Param("ownerUserId") Long ownerUserId,
            @Param("naturalKey") String naturalKey
    );

    @Select({
            "SELECT logistics.raw_snapshot_json",
            "FROM procurement_ali1688_order_logistics logistics",
            "JOIN procurement_ali1688_order_header oh ON oh.id = logistics.order_id",
            "  AND oh.owner_user_id = #{ownerUserId}",
            "  AND oh.is_deleted = b'0'",
            "WHERE logistics.logistics_natural_key = #{naturalKey}",
            "  AND logistics.is_deleted = b'0'",
            "LIMIT 1"
    })
    String selectOrderLogisticsRawSnapshotByNaturalKey(
            @Param("ownerUserId") Long ownerUserId,
            @Param("naturalKey") String naturalKey
    );

    @Select({
            "<script>",
            "SELECT",
            "  id, owner_user_id, authorization_id, order_natural_key, provider_order_no, order_time, supplier_name,",
            "  paid_at, buyer_company_name, buyer_member_name, seller_member_name, goods_total_text, freight_text,",
            "  adjustment_text, paid_amount_text, amount_text, amount_value, currency, order_status, logistics_status,",
            "  shipper_name, original_url, receiver_name, receiver_postal_code, receiver_telephone, receiver_mobile,",
            "  initiator_login_name, source_batch_no, downstream_order_no",
            "FROM procurement_ali1688_order_header",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND authorization_id IN",
            "  <foreach collection='authorizationIds' item='authorizationId' open='(' separator=',' close=')'>",
            "    #{authorizationId}",
            "  </foreach>",
            "  AND is_deleted = b'0'",
            "  AND NOT EXISTS (",
            "    SELECT 1",
            "    FROM procurement_ali1688_order_authorization duplicate_source",
            "    JOIN procurement_ali1688_order_header openapi_duplicate_order",
            "      ON openapi_duplicate_order.owner_user_id = procurement_ali1688_order_header.owner_user_id",
            "     AND openapi_duplicate_order.provider_order_no = procurement_ali1688_order_header.provider_order_no",
            "     AND openapi_duplicate_order.is_deleted = b'0'",
            "    JOIN procurement_ali1688_order_authorization openapi_duplicate",
            "      ON openapi_duplicate.id = openapi_duplicate_order.authorization_id",
            "     AND openapi_duplicate.owner_user_id = #{ownerUserId}",
            "     AND openapi_duplicate.provider_code = 'ALI1688_OPEN_API'",
            "     AND openapi_duplicate.is_deleted = b'0'",
            "    WHERE duplicate_source.id = procurement_ali1688_order_header.authorization_id",
            "      AND duplicate_source.owner_user_id = #{ownerUserId}",
            "      AND duplicate_source.provider_code IN ('ALI1688_EXCEL_LOCAL', 'ALI1688_EXCEL_UPLOAD')",
            "      AND duplicate_source.is_deleted = b'0'",
            "      AND openapi_duplicate_order.authorization_id IN",
            "      <foreach collection='authorizationIds' item='authorizationId' open='(' separator=',' close=')'>",
            "        #{authorizationId}",
            "      </foreach>",
            "  )",
            "  <if test='query.placedTimeFrom != null and query.placedTimeFrom != \"\"'>",
            "    AND order_time &gt;= #{query.placedTimeFrom}",
            "  </if>",
            "  <if test='query.placedTimeTo != null and query.placedTimeTo != \"\"'>",
            "    AND order_time &lt;= #{query.placedTimeTo}",
            "  </if>",
            "  <if test='query.orderStatus != null and query.orderStatus != \"\"'>",
            "    AND order_status = #{query.orderStatus}",
            "  </if>",
            "  <if test='query.supplierKeyword != null and query.supplierKeyword != \"\"'>",
            "    AND supplier_name LIKE CONCAT('%', #{query.supplierKeyword}, '%')",
            "  </if>",
            "  <if test=\"query.assignmentState == 'unassigned'\">",
            "    AND EXISTS (",
            "      SELECT 1",
            "      FROM procurement_ali1688_order_item unassigned_filter_item",
            "      LEFT JOIN procurement_ali1688_order_item_assignment unassigned_filter_assignment",
            "        ON unassigned_filter_assignment.item_id = unassigned_filter_item.id",
            "        AND unassigned_filter_assignment.owner_user_id = #{ownerUserId}",
            "        AND unassigned_filter_assignment.status = 'active'",
            "        AND unassigned_filter_assignment.is_deleted = b'0'",
            "      WHERE unassigned_filter_item.order_id = procurement_ali1688_order_header.id",
            "        AND unassigned_filter_item.is_deleted = b'0'",
            "      GROUP BY unassigned_filter_item.id",
            "      HAVING COALESCE(SUM(unassigned_filter_assignment.assigned_quantity), 0) = 0",
            "    )",
            "  </if>",
            "  <if test=\"query.assignmentState == 'consumable'\">",
            "    AND EXISTS (",
            "      SELECT 1",
            "      FROM procurement_ali1688_order_item_assignment consumable_filter_assignment",
            "      WHERE consumable_filter_assignment.order_id = procurement_ali1688_order_header.id",
            "        AND consumable_filter_assignment.owner_user_id = #{ownerUserId}",
            "        AND consumable_filter_assignment.target_type = 'CONSUMABLE'",
            "        AND consumable_filter_assignment.status = 'active'",
            "        AND consumable_filter_assignment.is_deleted = b'0'",
            "    )",
            "  </if>",
            "  <if test='query.assignmentTargetStoreCode != null and query.assignmentTargetStoreCode != \"\"'>",
            "    AND EXISTS (",
            "      SELECT 1",
            "      FROM procurement_ali1688_order_item_assignment assignment_target_filter",
            "      WHERE assignment_target_filter.order_id = procurement_ali1688_order_header.id",
            "        AND assignment_target_filter.owner_user_id = #{ownerUserId}",
            "        AND assignment_target_filter.target_type = 'STORE_SITE'",
            "        AND assignment_target_filter.target_store_code = #{query.assignmentTargetStoreCode}",
            "        <if test='query.assignmentTargetSiteCode != null and query.assignmentTargetSiteCode != \"\"'>",
            "          AND assignment_target_filter.target_site_code = #{query.assignmentTargetSiteCode}",
            "        </if>",
            "        AND assignment_target_filter.status = 'active'",
            "        AND assignment_target_filter.is_deleted = b'0'",
            "    )",
            "  </if>",
            "  <if test=\"query.productLinkState == 'linked'\">",
            "    AND EXISTS (",
            "      SELECT 1",
            "      FROM procurement_ali1688_order_item_assignment product_link_filter_assignment",
            "      JOIN procurement_ali1688_order_item_product_link product_link_filter_link",
            "        ON product_link_filter_link.assignment_id = product_link_filter_assignment.id",
            "        AND product_link_filter_link.owner_user_id = #{ownerUserId}",
            "        AND product_link_filter_link.status = 'active'",
            "        AND product_link_filter_link.is_deleted = b'0'",
            "      WHERE product_link_filter_assignment.order_id = procurement_ali1688_order_header.id",
            "        AND product_link_filter_assignment.owner_user_id = #{ownerUserId}",
            "        AND product_link_filter_assignment.target_type = 'STORE_SITE'",
            "        AND product_link_filter_assignment.status = 'active'",
            "        AND product_link_filter_assignment.is_deleted = b'0'",
            "    )",
            "  </if>",
            "  <if test=\"query.productLinkState == 'unlinked'\">",
            "    AND EXISTS (",
            "      SELECT 1",
            "      FROM procurement_ali1688_order_item_assignment product_link_filter_assignment",
            "      LEFT JOIN procurement_ali1688_order_item_product_link product_link_filter_unlinked",
            "        ON product_link_filter_unlinked.assignment_id = product_link_filter_assignment.id",
            "        AND product_link_filter_unlinked.owner_user_id = #{ownerUserId}",
            "        AND product_link_filter_unlinked.status = 'active'",
            "        AND product_link_filter_unlinked.is_deleted = b'0'",
            "      WHERE product_link_filter_assignment.order_id = procurement_ali1688_order_header.id",
            "        AND product_link_filter_assignment.owner_user_id = #{ownerUserId}",
            "        AND product_link_filter_assignment.target_type = 'STORE_SITE'",
            "        AND product_link_filter_assignment.status = 'active'",
            "        AND product_link_filter_assignment.is_deleted = b'0'",
            "        AND product_link_filter_unlinked.id IS NULL",
            "    )",
            "  </if>",
            "  <if test='query.keyword != null and query.keyword != \"\"'>",
            "    AND (",
            "      provider_order_no LIKE CONCAT('%', #{query.keyword}, '%')",
            "      OR supplier_name LIKE CONCAT('%', #{query.keyword}, '%')",
            "      OR buyer_company_name LIKE CONCAT('%', #{query.keyword}, '%')",
            "      OR seller_member_name LIKE CONCAT('%', #{query.keyword}, '%')",
            "      OR EXISTS (",
            "        SELECT 1",
            "        FROM procurement_ali1688_order_item item_filter",
            "        WHERE item_filter.order_id = procurement_ali1688_order_header.id",
            "          AND item_filter.is_deleted = b'0'",
            "          AND (",
            "            item_filter.title LIKE CONCAT('%', #{query.keyword}, '%')",
            "            OR item_filter.offer_id LIKE CONCAT('%', #{query.keyword}, '%')",
            "            OR item_filter.sku_id LIKE CONCAT('%', #{query.keyword}, '%')",
            "            OR item_filter.sku_text LIKE CONCAT('%', #{query.keyword}, '%')",
            "            OR item_filter.model_text LIKE CONCAT('%', #{query.keyword}, '%')",
            "            OR item_filter.product_code LIKE CONCAT('%', #{query.keyword}, '%')",
            "            OR item_filter.single_product_code LIKE CONCAT('%', #{query.keyword}, '%')",
            "          )",
            "      )",
            "      OR EXISTS (",
            "        SELECT 1",
            "        FROM procurement_ali1688_order_logistics logistics_filter",
            "        WHERE logistics_filter.order_id = procurement_ali1688_order_header.id",
            "          AND logistics_filter.is_deleted = b'0'",
            "          AND (",
            "            logistics_filter.logistics_company LIKE CONCAT('%', #{query.keyword}, '%')",
            "            OR logistics_filter.tracking_no LIKE CONCAT('%', #{query.keyword}, '%')",
            "          )",
            "      )",
            "    )",
            "  </if>",
            "ORDER BY order_time IS NULL ASC, order_time DESC, id DESC",
            "LIMIT #{query.pageSize} OFFSET #{query.offset}",
            "</script>"
    })
    List<Ali1688HistoricalOrderRow> listOrders(
            @Param("ownerUserId") Long ownerUserId,
            @Param("authorizationIds") List<Long> authorizationIds,
            @Param("query") Ali1688HistoricalOrderQuery query
    );

    @Select({
            "<script>",
            "SELECT",
            "  id, owner_user_id, authorization_id, order_natural_key, provider_order_no, order_time, supplier_name,",
            "  paid_at, buyer_company_name, buyer_member_name, seller_member_name, goods_total_text, freight_text,",
            "  adjustment_text, paid_amount_text, amount_text, amount_value, currency, order_status, logistics_status,",
            "  shipper_name, original_url, receiver_name, receiver_postal_code, receiver_telephone, receiver_mobile,",
            "  receiver_phone, receiver_address, buyer_remark, supplier_contact, initiator_login_name,",
            "  source_batch_no, downstream_order_no, raw_snapshot_json",
            "FROM procurement_ali1688_order_header",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND authorization_id IN",
            "  <foreach collection='authorizationIds' item='authorizationId' open='(' separator=',' close=')'>",
            "    #{authorizationId}",
            "  </foreach>",
            "  AND id = #{orderId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1",
            "</script>"
    })
    Ali1688HistoricalOrderRow selectOrderById(
            @Param("ownerUserId") Long ownerUserId,
            @Param("authorizationIds") List<Long> authorizationIds,
            @Param("orderId") Long orderId
    );

    @Select({
            "<script>",
            "SELECT oi.id, oi.order_id, oi.item_natural_key, oi.offer_id, oi.sku_id, oi.title, oi.sku_text,",
            "  oi.model_text, oi.product_code, oi.single_product_code, oi.quantity, oi.unit,",
            "  oi.unit_price_text, oi.amount_text, oi.image_url, oi.raw_snapshot_json",
            "FROM procurement_ali1688_order_item oi",
            "JOIN procurement_ali1688_order_header oh ON oh.id = oi.order_id",
            "  AND oh.owner_user_id = #{ownerUserId}",
            "  AND oh.is_deleted = b'0'",
            "WHERE oi.is_deleted = b'0'",
            "  AND oi.order_id IN",
            "  <foreach collection='orderIds' item='orderId' open='(' separator=',' close=')'>#{orderId}</foreach>",
            "ORDER BY oi.id ASC",
            "</script>"
    })
    List<Ali1688HistoricalOrderItemRow> listOrderItems(
            @Param("ownerUserId") Long ownerUserId,
            @Param("orderIds") List<Long> orderIds
    );

    @Select({
            "SELECT oi.id, oi.order_id, oh.authorization_id, oi.item_natural_key, oi.offer_id, oi.sku_id,",
            "  oi.title, oi.sku_text, oi.model_text, oi.product_code, oi.single_product_code,",
            "  oi.quantity, oi.unit, oi.unit_price_text, oi.amount_text, oi.image_url, oi.raw_snapshot_json",
            "FROM procurement_ali1688_order_item oi",
            "JOIN procurement_ali1688_order_header oh ON oh.id = oi.order_id",
            "  AND oh.owner_user_id = #{ownerUserId}",
            "  AND oh.is_deleted = b'0'",
            "WHERE oi.id = #{itemId}",
            "  AND oi.is_deleted = b'0'",
            "LIMIT 1",
            "FOR UPDATE"
    })
    Ali1688HistoricalOrderItemRow selectOrderItemForAssignment(
            @Param("ownerUserId") Long ownerUserId,
            @Param("itemId") Long itemId
    );

    @Select({
            "<script>",
            "SELECT",
            "  grouped.item_id,",
            "  COALESCE(SUM(grouped.target_quantity), 0) AS assigned_quantity,",
            "  SUM(CASE WHEN grouped.target_type = 'CONSUMABLE' THEN 1 ELSE 0 END) AS consumable_assignment_count,",
            "  SUM(CASE WHEN grouped.target_type != 'CONSUMABLE' THEN 1 ELSE 0 END) AS store_site_assignment_count,",
            "  GROUP_CONCAT(",
            "    CONCAT(grouped.target_label, ' ', grouped.target_quantity)",
            "    ORDER BY grouped.target_type ASC, grouped.target_store_code ASC, grouped.target_site_code ASC",
            "    SEPARATOR ' / '",
            "  ) AS assignment_breakdown_text",
            "FROM (",
            "  SELECT",
            "    item_id, target_type, target_store_code, target_site_code,",
            "    CASE",
            "      WHEN target_type = 'CONSUMABLE' THEN '耗材'",
            "      WHEN target_site_code = '*' THEN target_store_code",
            "      ELSE CONCAT(target_store_code, ' ', target_site_code)",
            "    END AS target_label,",
            "    SUM(assigned_quantity) AS target_quantity",
            "  FROM procurement_ali1688_order_item_assignment",
            "  WHERE owner_user_id = #{ownerUserId}",
            "    AND status = 'active'",
            "    AND is_deleted = b'0'",
            "    AND item_id IN",
            "    <foreach collection='itemIds' item='itemId' open='(' separator=',' close=')'>#{itemId}</foreach>",
            "  GROUP BY item_id, target_type, target_store_code, target_site_code",
            ") grouped",
            "GROUP BY grouped.item_id",
            "</script>"
    })
    List<Ali1688HistoricalOrderItemAssignmentSummaryRow> listOrderItemAssignmentSummaries(
            @Param("ownerUserId") Long ownerUserId,
            @Param("itemIds") List<Long> itemIds
    );

    @Insert({
            "INSERT INTO procurement_ali1688_order_item_assignment (",
            "  id, owner_user_id, authorization_id, order_id, item_id, target_type, target_store_code, target_site_code,",
            "  assigned_quantity, status, remark, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{authorizationId}, #{orderId}, #{itemId}, COALESCE(#{targetType}, 'STORE_SITE'),",
            "  #{targetStoreCode}, #{targetSiteCode}, #{assignedQuantity}, #{status}, #{remark},",
            "  #{createdBy}, #{updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertOrderItemAssignment(Ali1688HistoricalOrderItemAssignmentRow row);

    @Select({
            "SELECT",
            "  id, owner_user_id, authorization_id, order_id, item_id, target_type, target_store_code, target_site_code,",
            "  assigned_quantity, status, remark, created_by, updated_by,",
            "  DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i:%s') AS created_at,",
            "  DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i:%s') AS updated_at",
            "FROM procurement_ali1688_order_item_assignment",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND item_id = #{itemId}",
            "  AND is_deleted = b'0'",
            "ORDER BY CASE WHEN status = 'active' THEN 0 ELSE 1 END, gmt_updated DESC, id DESC"
    })
    List<Ali1688HistoricalOrderItemAssignmentRow> listOrderItemAssignments(
            @Param("ownerUserId") Long ownerUserId,
            @Param("itemId") Long itemId
    );

    @Select({
            "<script>",
            "SELECT",
            "  id, owner_user_id, authorization_id, order_id, item_id, target_type, target_store_code, target_site_code,",
            "  assigned_quantity, status, remark, created_by, updated_by,",
            "  DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i:%s') AS created_at,",
            "  DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i:%s') AS updated_at",
            "FROM procurement_ali1688_order_item_assignment",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND status = 'active'",
            "  AND is_deleted = b'0'",
            "  AND item_id IN",
            "  <foreach collection='itemIds' item='itemId' open='(' separator=',' close=')'>#{itemId}</foreach>",
            "ORDER BY item_id ASC, target_type ASC, target_store_code ASC, target_site_code ASC, id ASC",
            "</script>"
    })
    List<Ali1688HistoricalOrderItemAssignmentRow> listActiveOrderItemAssignments(
            @Param("ownerUserId") Long ownerUserId,
            @Param("itemIds") List<Long> itemIds
    );

    @Select({
            "<script>",
            "SELECT",
            "  ls.project_code AS storeCode,",
            "  lss.site AS siteCode,",
            "  pm.sku_parent AS skuParent,",
            "  MIN(NULLIF(pv.partner_sku, '')) AS partnerSku,",
            "  MIN(NULLIF(pso.psku_code, '')) AS pskuCode,",
            "  pm.title_cache AS productTitle,",
            "  MAX(COALESCE(",
            "    NULLIF(pm.title_cn_cache, ''),",
            "    NULLIF(JSON_UNQUOTE(JSON_EXTRACT(pmd.draft_json, '$.content.titleCn')), ''),",
            "    NULLIF(JSON_UNQUOTE(JSON_EXTRACT(pmd.draft_json, '$.content.titleZh')), ''),",
            "    NULLIF(JSON_UNQUOTE(JSON_EXTRACT(pms.snapshot_json, '$.content.titleCn')), ''),",
            "    NULLIF(JSON_UNQUOTE(JSON_EXTRACT(pms.snapshot_json, '$.content.titleZh')), '')",
            "  )) AS productTitleCn,",
            "  pm.cover_image_url AS productImageUrl",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND lss.is_deleted = b'0'",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = b'0'",
            "LEFT JOIN product_master_draft pmd",
            "  ON pmd.product_master_id = pm.id",
            " AND pmd.is_deleted = b'0'",
            "LEFT JOIN product_master_snapshot pms",
            "  ON pms.id = (",
            "    SELECT pms_latest.id",
            "    FROM product_master_snapshot pms_latest",
            "    WHERE pms_latest.product_master_id = pm.id",
            "      AND pms_latest.snapshot_type = 'baseline'",
            "      AND pms_latest.is_deleted = b'0'",
            "    ORDER BY pms_latest.fetched_at DESC, pms_latest.id DESC",
            "    LIMIT 1",
            "  )",
            "LEFT JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.is_deleted = b'0'",
            "LEFT JOIN product_site_offer pso",
            "  ON pso.variant_id = pv.id",
            " AND pso.site_id = lss.id",
            " AND pso.is_deleted = b'0'",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = b'0'",
            "  AND (ls.project_code = #{storeCode} OR lss.store_code = #{storeCode})",
            "  <if test='siteCode != null and siteCode != \"\"'>",
            "    AND UPPER(lss.site) = UPPER(#{siteCode})",
            "  </if>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      LOWER(pm.sku_parent) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(pm.title_cache, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(pm.title_cn_cache, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(pv.partner_sku, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(pso.psku_code, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "    )",
            "  </if>",
            "GROUP BY ls.project_code, lss.site, pm.sku_parent, pm.title_cache, pm.cover_image_url",
            "ORDER BY pm.sku_parent ASC, lss.site ASC",
            "</script>"
    })
    List<Ali1688SkuPurchaseHistoryProductRow> listSkuPurchaseHistoryProducts(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("keyword") String keyword
    );

    @Select({
            "<script>",
            "SELECT",
            "  link.owner_user_id AS ownerUserId,",
            "  link.order_id AS orderId,",
            "  link.item_id AS itemId,",
            "  link.assignment_id AS assignmentId,",
            "  link.id AS productLinkId,",
            "  link.target_store_code AS storeCode,",
            "  link.target_site_code AS siteCode,",
            "  link.sku_parent AS skuParent,",
            "  link.partner_sku AS partnerSku,",
            "  link.psku_code AS pskuCode,",
            "  link.product_title AS productTitle,",
            "  link.product_image_url AS productImageUrl,",
            "  item.offer_id AS sourceOfferId,",
            "  item.sku_id AS sourceSkuId,",
            "  item.product_code AS sourceProductCode,",
            "  item.single_product_code AS sourceSingleProductCode,",
            "  header.provider_order_no AS orderNo,",
            "  DATE_FORMAT(header.order_time, '%Y-%m-%d %H:%i:%s') AS orderTime,",
            "  header.supplier_name AS supplierName,",
            "  assignment.assigned_quantity AS assignedQuantity,",
            "  item.quantity AS itemQuantity,",
            "  item.amount_text AS itemAmountText,",
            "  (",
            "    SELECT SUM(CAST(NULLIF(REPLACE(REPLACE(REPLACE(sibling.amount_text, '¥', ''), ',', ''), ' ', ''), '') AS DECIMAL(18, 4)))",
            "    FROM procurement_ali1688_order_item sibling",
            "    WHERE sibling.order_id = header.id",
            "      AND sibling.is_deleted = b'0'",
            "  ) AS orderItemAmountTotalText,",
            "  header.goods_total_text AS goodsTotalText,",
            "  header.paid_amount_text AS paidAmountText",
            "FROM procurement_ali1688_order_item_product_link link",
            "JOIN procurement_ali1688_order_item_assignment assignment",
            "  ON assignment.id = link.assignment_id",
            " AND assignment.owner_user_id = link.owner_user_id",
            " AND assignment.target_type = 'STORE_SITE'",
            " AND assignment.status = 'active'",
            " AND assignment.is_deleted = b'0'",
            "JOIN procurement_ali1688_order_header header",
            "  ON header.id = link.order_id",
            " AND header.owner_user_id = link.owner_user_id",
            " AND header.is_deleted = b'0'",
            "JOIN procurement_ali1688_order_item item",
            "  ON item.id = link.item_id",
            " AND item.order_id = header.id",
            " AND item.is_deleted = b'0'",
            "WHERE link.owner_user_id = #{ownerUserId}",
            "  AND link.status = 'active'",
            "  AND link.is_deleted = b'0'",
            "  AND link.target_store_code = #{storeCode}",
            "  <if test='siteCode != null and siteCode != \"\"'>",
            "    AND link.target_site_code = #{siteCode}",
            "  </if>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      link.sku_parent LIKE CONCAT('%', #{keyword}, '%')",
            "      OR link.partner_sku LIKE CONCAT('%', #{keyword}, '%')",
            "      OR link.psku_code LIKE CONCAT('%', #{keyword}, '%')",
            "      OR link.product_title LIKE CONCAT('%', #{keyword}, '%')",
            "    )",
            "  </if>",
            "  <if test='purchaseTimeFrom != null and purchaseTimeFrom != \"\"'>",
            "    AND header.order_time &gt;= CONCAT(#{purchaseTimeFrom}, ' 00:00:00')",
            "  </if>",
            "  <if test='purchaseTimeTo != null and purchaseTimeTo != \"\"'>",
            "    AND header.order_time &lt;= CONCAT(#{purchaseTimeTo}, ' 23:59:59')",
            "  </if>",
            "ORDER BY link.sku_parent ASC, header.order_time DESC, link.id DESC",
            "</script>"
    })
    List<Ali1688SkuPurchaseHistoryRow> listSkuPurchaseHistoryRows(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("keyword") String keyword,
            @Param("purchaseTimeFrom") String purchaseTimeFrom,
            @Param("purchaseTimeTo") String purchaseTimeTo
    );

    @Select({
            "<script>",
            "SELECT",
            "  assignment.owner_user_id AS ownerUserId,",
            "  assignment.order_id AS orderId,",
            "  assignment.item_id AS itemId,",
            "  assignment.id AS assignmentId,",
            "  NULL AS productLinkId,",
            "  assignment.target_store_code AS storeCode,",
            "  assignment.target_site_code AS siteCode,",
            "  NULL AS skuParent,",
            "  NULL AS partnerSku,",
            "  NULL AS pskuCode,",
            "  item.title AS productTitle,",
            "  item.image_url AS productImageUrl,",
            "  item.offer_id AS sourceOfferId,",
            "  item.sku_id AS sourceSkuId,",
            "  item.product_code AS sourceProductCode,",
            "  item.single_product_code AS sourceSingleProductCode,",
            "  header.provider_order_no AS orderNo,",
            "  DATE_FORMAT(header.order_time, '%Y-%m-%d %H:%i:%s') AS orderTime,",
            "  header.supplier_name AS supplierName,",
            "  assignment.assigned_quantity AS assignedQuantity,",
            "  item.quantity AS itemQuantity,",
            "  item.amount_text AS itemAmountText,",
            "  header.goods_total_text AS goodsTotalText,",
            "  header.paid_amount_text AS paidAmountText",
            "FROM procurement_ali1688_order_item_assignment assignment",
            "JOIN procurement_ali1688_order_header header",
            "  ON header.id = assignment.order_id",
            " AND header.owner_user_id = assignment.owner_user_id",
            " AND header.is_deleted = b'0'",
            "JOIN procurement_ali1688_order_item item",
            "  ON item.id = assignment.item_id",
            " AND item.order_id = header.id",
            " AND item.is_deleted = b'0'",
            "LEFT JOIN procurement_ali1688_order_item_product_link link",
            "  ON link.assignment_id = assignment.id",
            " AND link.owner_user_id = assignment.owner_user_id",
            " AND link.status = 'active'",
            " AND link.is_deleted = b'0'",
            "WHERE assignment.owner_user_id = #{ownerUserId}",
            "  AND assignment.target_type = 'STORE_SITE'",
            "  AND assignment.status = 'active'",
            "  AND assignment.is_deleted = b'0'",
            "  AND assignment.target_store_code = #{storeCode}",
            "  <if test='siteCode != null and siteCode != \"\"'>",
            "    AND assignment.target_site_code = #{siteCode}",
            "  </if>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      header.provider_order_no LIKE CONCAT('%', #{keyword}, '%')",
            "      OR header.supplier_name LIKE CONCAT('%', #{keyword}, '%')",
            "      OR item.title LIKE CONCAT('%', #{keyword}, '%')",
            "      OR item.offer_id LIKE CONCAT('%', #{keyword}, '%')",
            "      OR item.sku_id LIKE CONCAT('%', #{keyword}, '%')",
            "      OR item.product_code LIKE CONCAT('%', #{keyword}, '%')",
            "      OR item.single_product_code LIKE CONCAT('%', #{keyword}, '%')",
            "    )",
            "  </if>",
            "  <if test='purchaseTimeFrom != null and purchaseTimeFrom != \"\"'>",
            "    AND header.order_time &gt;= CONCAT(#{purchaseTimeFrom}, ' 00:00:00')",
            "  </if>",
            "  <if test='purchaseTimeTo != null and purchaseTimeTo != \"\"'>",
            "    AND header.order_time &lt;= CONCAT(#{purchaseTimeTo}, ' 23:59:59')",
            "  </if>",
            "  AND link.id IS NULL",
            "ORDER BY header.order_time IS NULL ASC, header.order_time DESC, assignment.id DESC",
            "</script>"
    })
    List<Ali1688SkuPurchaseHistoryRow> listUnlinkedSkuPurchaseHistoryRows(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("keyword") String keyword,
            @Param("purchaseTimeFrom") String purchaseTimeFrom,
            @Param("purchaseTimeTo") String purchaseTimeTo
    );

    @Select({
            "<script>",
            "SELECT COUNT(DISTINCT assignment.id)",
            "FROM procurement_ali1688_order_item_assignment assignment",
            "JOIN procurement_ali1688_order_header header",
            "  ON header.id = assignment.order_id",
            " AND header.owner_user_id = assignment.owner_user_id",
            " AND header.is_deleted = b'0'",
            "JOIN procurement_ali1688_order_item item",
            "  ON item.id = assignment.item_id",
            " AND item.order_id = header.id",
            " AND item.is_deleted = b'0'",
            "LEFT JOIN procurement_ali1688_order_item_product_link link",
            "  ON link.assignment_id = assignment.id",
            " AND link.owner_user_id = assignment.owner_user_id",
            " AND link.status = 'active'",
            " AND link.is_deleted = b'0'",
            "WHERE assignment.owner_user_id = #{ownerUserId}",
            "  AND assignment.target_type = 'STORE_SITE'",
            "  AND assignment.status = 'active'",
            "  AND assignment.is_deleted = b'0'",
            "  AND assignment.target_store_code = #{storeCode}",
            "  <if test='siteCode != null and siteCode != \"\"'>",
            "    AND assignment.target_site_code = #{siteCode}",
            "  </if>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      header.provider_order_no LIKE CONCAT('%', #{keyword}, '%')",
            "      OR header.supplier_name LIKE CONCAT('%', #{keyword}, '%')",
            "      OR item.title LIKE CONCAT('%', #{keyword}, '%')",
            "      OR item.offer_id LIKE CONCAT('%', #{keyword}, '%')",
            "      OR item.sku_id LIKE CONCAT('%', #{keyword}, '%')",
            "      OR item.product_code LIKE CONCAT('%', #{keyword}, '%')",
            "      OR item.single_product_code LIKE CONCAT('%', #{keyword}, '%')",
            "    )",
            "  </if>",
            "  <if test='purchaseTimeFrom != null and purchaseTimeFrom != \"\"'>",
            "    AND header.order_time &gt;= CONCAT(#{purchaseTimeFrom}, ' 00:00:00')",
            "  </if>",
            "  <if test='purchaseTimeTo != null and purchaseTimeTo != \"\"'>",
            "    AND header.order_time &lt;= CONCAT(#{purchaseTimeTo}, ' 23:59:59')",
            "  </if>",
            "  AND link.id IS NULL",
            "</script>"
    })
    int countUnlinkedAssignedStoreSiteLines(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("keyword") String keyword,
            @Param("purchaseTimeFrom") String purchaseTimeFrom,
            @Param("purchaseTimeTo") String purchaseTimeTo
    );

    @Select({
            "<script>",
            "SELECT",
            "  id, owner_user_id AS ownerUserId, target_store_code AS storeCode, target_site_code AS siteCode,",
            "  sku_parent AS skuParent, partner_sku AS partnerSku, psku_code AS pskuCode,",
            "  batch_label AS batchLabel, batch_sequence AS batchSequence, counted_quantity AS countedQuantity,",
            "  counted_cost AS countedCost, note, status, created_by AS createdBy, updated_by AS updatedBy,",
            "  DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i:%s') AS createdAt,",
            "  DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i:%s') AS updatedAt",
            "FROM procurement_ali1688_sku_purchase_batch batch",
            "WHERE batch.owner_user_id = #{ownerUserId}",
            "  AND batch.target_store_code = #{storeCode}",
            "  <if test='siteCode != null and siteCode != \"\"'>",
            "    AND batch.target_site_code = #{siteCode}",
            "  </if>",
            "  AND batch.status = 'active'",
            "  AND batch.is_deleted = b'0'",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      batch.sku_parent LIKE CONCAT('%', #{keyword}, '%')",
            "      OR batch.partner_sku LIKE CONCAT('%', #{keyword}, '%')",
            "      OR batch.psku_code LIKE CONCAT('%', #{keyword}, '%')",
            "    )",
            "  </if>",
            "  <if test='purchaseTimeFrom != null and purchaseTimeFrom != \"\"'>",
            "    AND EXISTS (",
            "      SELECT 1",
            "      FROM procurement_ali1688_sku_purchase_batch_source source_filter",
            "      JOIN procurement_ali1688_order_header header_filter",
            "        ON header_filter.id = source_filter.order_id",
            "       AND header_filter.owner_user_id = source_filter.owner_user_id",
            "       AND header_filter.is_deleted = b'0'",
            "      WHERE source_filter.owner_user_id = batch.owner_user_id",
            "        AND source_filter.batch_id = batch.id",
            "        AND source_filter.status = 'active'",
            "        AND source_filter.is_deleted = b'0'",
            "        AND header_filter.order_time &gt;= CONCAT(#{purchaseTimeFrom}, ' 00:00:00')",
            "    )",
            "  </if>",
            "  <if test='purchaseTimeTo != null and purchaseTimeTo != \"\"'>",
            "    AND EXISTS (",
            "      SELECT 1",
            "      FROM procurement_ali1688_sku_purchase_batch_source source_filter",
            "      JOIN procurement_ali1688_order_header header_filter",
            "        ON header_filter.id = source_filter.order_id",
            "       AND header_filter.owner_user_id = source_filter.owner_user_id",
            "       AND header_filter.is_deleted = b'0'",
            "      WHERE source_filter.owner_user_id = batch.owner_user_id",
            "        AND source_filter.batch_id = batch.id",
            "        AND source_filter.status = 'active'",
            "        AND source_filter.is_deleted = b'0'",
            "        AND header_filter.order_time &lt;= CONCAT(#{purchaseTimeTo}, ' 23:59:59')",
            "    )",
            "  </if>",
            "ORDER BY batch.sku_parent ASC, batch.batch_sequence ASC, batch.id ASC",
            "</script>"
    })
    List<Ali1688SkuPurchaseBatchRow> listSkuPurchaseBatches(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("keyword") String keyword,
            @Param("purchaseTimeFrom") String purchaseTimeFrom,
            @Param("purchaseTimeTo") String purchaseTimeTo
    );

    @Select({
            "<script>",
            "SELECT",
            "  id, batch_id AS batchId, owner_user_id AS ownerUserId, order_id AS orderId, item_id AS itemId,",
            "  assignment_id AS assignmentId, source_order_no AS sourceOrderNo,",
            "  DATE_FORMAT(source_order_time, '%Y-%m-%d %H:%i:%s') AS sourceOrderTime,",
            "  supplier_name AS supplierName, status, created_by AS createdBy, updated_by AS updatedBy,",
            "  DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i:%s') AS createdAt,",
            "  DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i:%s') AS updatedAt",
            "FROM procurement_ali1688_sku_purchase_batch_source",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND status = 'active'",
            "  AND is_deleted = b'0'",
            "  AND batch_id IN",
            "  <foreach collection='batchIds' item='batchId' open='(' separator=',' close=')'>#{batchId}</foreach>",
            "ORDER BY batch_id ASC, id ASC",
            "</script>"
    })
    List<Ali1688SkuPurchaseBatchSourceRow> listSkuPurchaseBatchSources(
            @Param("ownerUserId") Long ownerUserId,
            @Param("batchIds") List<Long> batchIds
    );

    @Update({
            "UPDATE procurement_ali1688_sku_purchase_batch",
            "SET status = 'replaced',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND target_store_code = #{storeCode}",
            "  AND target_site_code = #{siteCode}",
            "  AND sku_parent = #{skuParent}",
            "  AND status = 'active'",
            "  AND is_deleted = b'0'"
    })
    int softDeleteSkuPurchaseBatchesForSku(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("skuParent") String skuParent,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO procurement_ali1688_sku_purchase_batch (",
            "  id, owner_user_id, target_store_code, target_site_code, sku_parent, partner_sku, psku_code,",
            "  batch_label, batch_sequence, counted_quantity, counted_cost, note, status, created_by, updated_by",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{storeCode}, #{siteCode}, #{skuParent}, #{partnerSku}, #{pskuCode},",
            "  #{batchLabel}, #{batchSequence}, #{countedQuantity}, #{countedCost}, #{note}, #{status}, #{createdBy}, #{updatedBy}",
            ")"
    })
    int insertSkuPurchaseBatch(Ali1688SkuPurchaseBatchRow row);

    @Insert({
            "INSERT INTO procurement_ali1688_sku_purchase_batch_source (",
            "  id, batch_id, owner_user_id, order_id, item_id, assignment_id, source_order_no, source_order_time,",
            "  supplier_name, status, created_by, updated_by",
            ") VALUES (",
            "  #{id}, #{batchId}, #{ownerUserId}, #{orderId}, #{itemId}, #{assignmentId}, #{sourceOrderNo}, #{sourceOrderTime},",
            "  #{supplierName}, #{status}, #{createdBy}, #{updatedBy}",
            ")"
    })
    int insertSkuPurchaseBatchSource(Ali1688SkuPurchaseBatchSourceRow row);

    @Select({
            "SELECT",
            "  id, owner_user_id, authorization_id, order_id, item_id, target_type, target_store_code, target_site_code,",
            "  assigned_quantity, status, remark, created_by, updated_by,",
            "  DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i:%s') AS created_at,",
            "  DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i:%s') AS updated_at",
            "FROM procurement_ali1688_order_item_assignment",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND id = #{assignmentId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    Ali1688HistoricalOrderItemAssignmentRow selectOrderItemAssignmentById(
            @Param("ownerUserId") Long ownerUserId,
            @Param("assignmentId") Long assignmentId
    );

    @Select({
            "SELECT COALESCE(SUM(assigned_quantity), 0)",
            "FROM procurement_ali1688_order_item_assignment",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND item_id = #{itemId}",
            "  AND id != #{assignmentId}",
            "  AND status = 'active'",
            "  AND is_deleted = b'0'"
    })
    Integer sumAssignedQuantityExcludingAssignment(
            @Param("ownerUserId") Long ownerUserId,
            @Param("itemId") Long itemId,
            @Param("assignmentId") Long assignmentId
    );

    @Update({
            "UPDATE procurement_ali1688_order_item_assignment",
            "SET assigned_quantity = #{assignedQuantity},",
            "    status = 'active',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{assignmentId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'"
    })
    int updateOrderItemAssignmentQuantity(
            @Param("assignmentId") Long assignmentId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("assignedQuantity") Integer assignedQuantity,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE procurement_ali1688_order_item_assignment",
            "SET status = 'revoked',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{assignmentId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'"
    })
    int revokeOrderItemAssignment(
            @Param("assignmentId") Long assignmentId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Update({
            "UPDATE procurement_ali1688_order_item_product_link",
            "SET status = 'replaced',",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND assignment_id = #{assignmentId}",
            "  AND status = 'active'",
            "  AND is_deleted = b'0'"
    })
    int deactivateActiveOrderItemProductLinks(
            @Param("ownerUserId") Long ownerUserId,
            @Param("assignmentId") Long assignmentId,
            @Param("operatorUserId") Long operatorUserId
    );

    @Select({
            "SELECT",
            "  id, owner_user_id, authorization_id, order_id, item_id, assignment_id, target_store_code, target_site_code,",
            "  sku_parent, partner_sku, psku_code, product_title, product_image_url, status, created_by, updated_by,",
            "  DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i:%s') AS created_at,",
            "  DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i:%s') AS updated_at",
            "FROM procurement_ali1688_order_item_product_link",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND assignment_id = #{assignmentId}",
            "  AND status = 'active'",
            "  AND is_deleted = b'0'",
            "ORDER BY gmt_updated DESC, id DESC",
            "LIMIT 1"
    })
    Ali1688HistoricalOrderProductLinkRow selectActiveOrderItemProductLinkByAssignment(
            @Param("ownerUserId") Long ownerUserId,
            @Param("assignmentId") Long assignmentId
    );

    @Select({
            "SELECT COUNT(1)",
            "FROM logical_store ls",
            "JOIN logical_store_site target_site",
            "  ON target_site.logical_store_id = ls.id",
            " AND target_site.is_deleted = 0",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.sku_parent = #{skuParent}",
            " AND pm.is_deleted = 0",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = 0",
            "  AND (ls.project_code = #{targetStoreCode} OR target_site.store_code = #{targetStoreCode})",
            "  AND (#{targetSiteCode} IS NULL OR UPPER(target_site.site) = UPPER(#{targetSiteCode}))",
            "  AND EXISTS (",
            "    SELECT 1",
            "    FROM product_variant pv",
            "    JOIN product_site_offer pso",
            "      ON pso.variant_id = pv.id",
            "     AND pso.site_id = target_site.id",
            "     AND pso.is_deleted = 0",
            "    WHERE pv.product_master_id = pm.id",
            "      AND pv.is_deleted = 0",
            "  )"
    })
    int countProductSkuInAssignmentTarget(
            @Param("ownerUserId") Long ownerUserId,
            @Param("targetStoreCode") String targetStoreCode,
            @Param("targetSiteCode") String targetSiteCode,
            @Param("skuParent") String skuParent
    );

    @Select({
            "<script>",
            "SELECT",
            "  ls.project_code AS storeCode,",
            "  lss.site AS siteCode,",
            "  pm.sku_parent AS skuParent,",
            "  MAX(pv.partner_sku) AS partnerSku,",
            "  MAX(pso.psku_code) AS pskuCode,",
            "  COALESCE(NULLIF(pm.title_cn_cache, ''), NULLIF(pm.title_cache, '')) AS productTitle,",
            "  pm.cover_image_url AS productImageUrl,",
            "  CASE WHEN COUNT(DISTINCT link.id) > 0 THEN 'linked' ELSE 'unlinked' END AS linkStatus,",
            "  COUNT(DISTINCT link.assignment_id) AS linkedAssignmentCount",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND lss.is_deleted = b'0'",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = b'0'",
            "LEFT JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.is_deleted = b'0'",
            "LEFT JOIN product_site_offer pso",
            "  ON pso.variant_id = pv.id",
            " AND pso.site_id = lss.id",
            " AND pso.is_deleted = b'0'",
            "LEFT JOIN procurement_ali1688_order_item_product_link link",
            "  ON link.owner_user_id = ls.owner_user_id",
            " AND link.target_store_code = ls.project_code",
            " AND link.target_site_code = lss.site",
            " AND link.sku_parent = pm.sku_parent",
            " AND link.status = 'active'",
            " AND link.is_deleted = b'0'",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = b'0'",
            "  AND (ls.project_code = #{targetStoreCode} OR lss.store_code = #{targetStoreCode})",
            "  AND UPPER(lss.site) = UPPER(#{targetSiteCode})",
            "  AND pm.sku_parent = #{skuParent}",
            "GROUP BY ls.project_code, lss.site, pm.sku_parent, pm.title_cn_cache, pm.title_cache, pm.cover_image_url",
            "LIMIT 1",
            "</script>"
    })
    Ali1688HistoricalOrderProductLinkCandidateRow selectOrderItemProductLinkCandidateBySkuParent(
            @Param("ownerUserId") Long ownerUserId,
            @Param("targetStoreCode") String targetStoreCode,
            @Param("targetSiteCode") String targetSiteCode,
            @Param("skuParent") String skuParent
    );

    @Select({
            "<script>",
            "SELECT",
            "  ls.project_code AS storeCode,",
            "  lss.site AS siteCode,",
            "  pm.sku_parent AS skuParent,",
            "  MIN(NULLIF(pv.partner_sku, '')) AS partnerSku,",
            "  MIN(NULLIF(pso.psku_code, '')) AS pskuCode,",
            "  MIN(NULLIF(pso.offer_code, '')) AS offerCode,",
            "  COALESCE(NULLIF(pm.title_cn_cache, ''), NULLIF(pm.title_cache, '')) AS productTitle,",
            "  pm.cover_image_url AS productImageUrl,",
            "  CASE WHEN COUNT(DISTINCT link.id) > 0 THEN 'linked' ELSE 'unlinked' END AS linkStatus,",
            "  COUNT(DISTINCT link.assignment_id) AS linkedAssignmentCount",
            "FROM logical_store ls",
            "JOIN logical_store_site lss",
            "  ON lss.logical_store_id = ls.id",
            " AND lss.is_deleted = b'0'",
            "JOIN product_master pm",
            "  ON pm.logical_store_id = ls.id",
            " AND pm.is_deleted = b'0'",
            "LEFT JOIN product_variant pv",
            "  ON pv.product_master_id = pm.id",
            " AND pv.is_deleted = b'0'",
            "LEFT JOIN product_site_offer pso",
            "  ON pso.variant_id = pv.id",
            " AND pso.site_id = lss.id",
            " AND pso.is_deleted = b'0'",
            "LEFT JOIN procurement_ali1688_order_item_product_link link",
            "  ON link.owner_user_id = ls.owner_user_id",
            " AND link.target_store_code = ls.project_code",
            " AND link.target_site_code = lss.site",
            " AND link.sku_parent = pm.sku_parent",
            " AND link.status = 'active'",
            " AND link.is_deleted = b'0'",
            "WHERE ls.owner_user_id = #{ownerUserId}",
            "  AND ls.is_deleted = b'0'",
            "  AND (ls.project_code = #{storeCode} OR lss.store_code = #{storeCode})",
            "  <if test='siteCode != null and siteCode != \"\"'>",
            "    AND UPPER(lss.site) = UPPER(#{siteCode})",
            "  </if>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      LOWER(pm.sku_parent) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(pm.title_cache, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(pm.title_cn_cache, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(pv.partner_sku, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(pso.psku_code, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "    )",
            "  </if>",
            "GROUP BY ls.project_code, lss.site, pm.sku_parent, pm.title_cn_cache, pm.title_cache, pm.cover_image_url",
            "<choose>",
            "  <when test='linkStatus == \"linked\"'>",
            "    HAVING COUNT(DISTINCT link.id) > 0",
            "  </when>",
            "  <when test='linkStatus == \"unlinked\"'>",
            "    HAVING COUNT(DISTINCT link.id) = 0",
            "  </when>",
            "</choose>",
            "ORDER BY pm.sku_parent ASC, lss.site ASC",
            "LIMIT 500",
            "</script>"
    })
    List<Ali1688HistoricalOrderProductLinkCandidateRow> listOrderItemProductLinkCandidates(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode,
            @Param("linkStatus") String linkStatus,
            @Param("keyword") String keyword
    );

    @Update({
            "UPDATE procurement_ali1688_order_item_product_link",
            "SET status = #{status},",
            "    updated_by = #{operatorUserId},",
            "    gmt_updated = NOW()",
            "WHERE id = #{linkId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'"
    })
    int updateOrderItemProductLinkStatus(
            @Param("linkId") Long linkId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("status") String status,
            @Param("operatorUserId") Long operatorUserId
    );

    @Insert({
            "INSERT INTO procurement_ali1688_order_item_product_link (",
            "  id, owner_user_id, authorization_id, order_id, item_id, assignment_id, target_store_code, target_site_code,",
            "  sku_parent, partner_sku, psku_code, product_title, product_image_url,",
            "  status, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{authorizationId}, #{orderId}, #{itemId}, #{assignmentId},",
            "  #{targetStoreCode}, #{targetSiteCode}, #{skuParent}, #{partnerSku}, #{pskuCode}, #{productTitle},",
            "  #{productImageUrl}, #{status}, #{createdBy}, #{updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertOrderItemProductLink(Ali1688HistoricalOrderProductLinkRow row);

    @Insert({
            "INSERT INTO procurement_ali1688_order_item_product_link_audit (",
            "  id, owner_user_id, assignment_id, old_link_id, new_link_id, action_type,",
            "  old_sku_parent, old_partner_sku, old_psku_code, old_product_title,",
            "  new_sku_parent, new_partner_sku, new_psku_code, new_product_title,",
            "  remark, created_by, gmt_create",
            ") VALUES (",
            "  #{id}, #{ownerUserId}, #{assignmentId}, #{oldLinkId}, #{newLinkId}, #{actionType},",
            "  #{oldSkuParent}, #{oldPartnerSku}, #{oldPskuCode}, #{oldProductTitle},",
            "  #{newSkuParent}, #{newPartnerSku}, #{newPskuCode}, #{newProductTitle},",
            "  #{remark}, #{createdBy}, NOW()",
            ")"
    })
    int insertOrderItemProductLinkAudit(Ali1688HistoricalOrderProductLinkAuditRow row);

    @Select({
            "SELECT",
            "  id, owner_user_id, assignment_id, old_link_id, new_link_id, action_type,",
            "  old_sku_parent, old_partner_sku, old_psku_code, old_product_title,",
            "  new_sku_parent, new_partner_sku, new_psku_code, new_product_title,",
            "  remark, created_by,",
            "  DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i:%s') AS created_at",
            "FROM procurement_ali1688_order_item_product_link_audit",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND assignment_id = #{assignmentId}",
            "  AND is_deleted = b'0'",
            "ORDER BY gmt_create DESC, id DESC"
    })
    List<Ali1688HistoricalOrderProductLinkAuditRow> listOrderItemProductLinkAudits(
            @Param("ownerUserId") Long ownerUserId,
            @Param("assignmentId") Long assignmentId
    );

    @Select({
            "<script>",
            "SELECT",
            "  id, owner_user_id, authorization_id, order_id, item_id, assignment_id, target_store_code, target_site_code,",
            "  sku_parent, partner_sku, psku_code, product_title, product_image_url, status, created_by, updated_by,",
            "  DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i:%s') AS created_at,",
            "  DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i:%s') AS updated_at",
            "FROM procurement_ali1688_order_item_product_link",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND status = 'active'",
            "  AND is_deleted = b'0'",
            "  AND assignment_id IN",
            "  <foreach collection='assignmentIds' item='assignmentId' open='(' separator=',' close=')'>#{assignmentId}</foreach>",
            "ORDER BY assignment_id ASC, gmt_updated DESC, id DESC",
            "</script>"
    })
    List<Ali1688HistoricalOrderProductLinkRow> listActiveOrderItemProductLinks(
            @Param("ownerUserId") Long ownerUserId,
            @Param("assignmentIds") List<Long> assignmentIds
    );

    @Select({
            "SELECT COUNT(1)",
            "FROM procurement_ali1688_order_item_assignment",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND order_id = #{orderId}",
            "  AND status = 'active'",
            "  AND is_deleted = b'0'"
    })
    int countActiveOrderAssignments(
            @Param("ownerUserId") Long ownerUserId,
            @Param("orderId") Long orderId
    );

    @Update({
            "UPDATE procurement_ali1688_order_header",
            "SET is_deleted = b'1',",
            "    deleted_by = #{operatorUserId},",
            "    deleted_at = NOW(),",
            "    delete_reason = #{deleteReason},",
            "    gmt_updated = NOW()",
            "WHERE id = #{orderId}",
            "  AND owner_user_id = #{ownerUserId}",
            "  AND is_deleted = b'0'"
    })
    int softDeleteOrderHeader(
            @Param("orderId") Long orderId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("operatorUserId") Long operatorUserId,
            @Param("deleteReason") String deleteReason
    );

    @Update({
            "UPDATE procurement_ali1688_order_item",
            "SET is_deleted = b'1',",
            "    gmt_updated = NOW()",
            "WHERE order_id = #{orderId}",
            "  AND is_deleted = b'0'"
    })
    int softDeleteOrderItems(@Param("orderId") Long orderId);

    @Update({
            "UPDATE procurement_ali1688_order_logistics",
            "SET is_deleted = b'1',",
            "    gmt_updated = NOW()",
            "WHERE order_id = #{orderId}",
            "  AND is_deleted = b'0'"
    })
    int softDeleteOrderLogistics(@Param("orderId") Long orderId);

    @Select({
            "<script>",
            "SELECT logistics.id, logistics.order_id, logistics.item_id, logistics.logistics_natural_key,",
            "  logistics.logistics_company, logistics.tracking_no, logistics.raw_snapshot_json",
            "FROM procurement_ali1688_order_logistics logistics",
            "JOIN procurement_ali1688_order_header oh ON oh.id = logistics.order_id",
            "  AND oh.owner_user_id = #{ownerUserId}",
            "  AND oh.is_deleted = b'0'",
            "WHERE logistics.is_deleted = b'0'",
            "  AND logistics.order_id IN",
            "  <foreach collection='orderIds' item='orderId' open='(' separator=',' close=')'>#{orderId}</foreach>",
            "ORDER BY logistics.id ASC",
            "</script>"
    })
    List<Ali1688HistoricalOrderLogisticsRow> listOrderLogistics(
            @Param("ownerUserId") Long ownerUserId,
            @Param("orderIds") List<Long> orderIds
    );

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM procurement_ali1688_order_header",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND authorization_id IN",
            "  <foreach collection='authorizationIds' item='authorizationId' open='(' separator=',' close=')'>",
            "    #{authorizationId}",
            "  </foreach>",
            "  AND is_deleted = b'0'",
            "  AND NOT EXISTS (",
            "    SELECT 1",
            "    FROM procurement_ali1688_order_authorization duplicate_source",
            "    JOIN procurement_ali1688_order_header openapi_duplicate_order",
            "      ON openapi_duplicate_order.owner_user_id = procurement_ali1688_order_header.owner_user_id",
            "     AND openapi_duplicate_order.provider_order_no = procurement_ali1688_order_header.provider_order_no",
            "     AND openapi_duplicate_order.is_deleted = b'0'",
            "    JOIN procurement_ali1688_order_authorization openapi_duplicate",
            "      ON openapi_duplicate.id = openapi_duplicate_order.authorization_id",
            "     AND openapi_duplicate.owner_user_id = #{ownerUserId}",
            "     AND openapi_duplicate.provider_code = 'ALI1688_OPEN_API'",
            "     AND openapi_duplicate.is_deleted = b'0'",
            "    WHERE duplicate_source.id = procurement_ali1688_order_header.authorization_id",
            "      AND duplicate_source.owner_user_id = #{ownerUserId}",
            "      AND duplicate_source.provider_code IN ('ALI1688_EXCEL_LOCAL', 'ALI1688_EXCEL_UPLOAD')",
            "      AND duplicate_source.is_deleted = b'0'",
            "      AND openapi_duplicate_order.authorization_id IN",
            "      <foreach collection='authorizationIds' item='authorizationId' open='(' separator=',' close=')'>",
            "        #{authorizationId}",
            "      </foreach>",
            "  )",
            "  <if test='query.placedTimeFrom != null and query.placedTimeFrom != \"\"'>",
            "    AND order_time &gt;= #{query.placedTimeFrom}",
            "  </if>",
            "  <if test='query.placedTimeTo != null and query.placedTimeTo != \"\"'>",
            "    AND order_time &lt;= #{query.placedTimeTo}",
            "  </if>",
            "  <if test='query.orderStatus != null and query.orderStatus != \"\"'>",
            "    AND order_status = #{query.orderStatus}",
            "  </if>",
            "  <if test='query.supplierKeyword != null and query.supplierKeyword != \"\"'>",
            "    AND supplier_name LIKE CONCAT('%', #{query.supplierKeyword}, '%')",
            "  </if>",
            "  <if test=\"query.assignmentState == 'unassigned'\">",
            "    AND EXISTS (",
            "      SELECT 1",
            "      FROM procurement_ali1688_order_item unassigned_filter_item",
            "      LEFT JOIN procurement_ali1688_order_item_assignment unassigned_filter_assignment",
            "        ON unassigned_filter_assignment.item_id = unassigned_filter_item.id",
            "        AND unassigned_filter_assignment.owner_user_id = #{ownerUserId}",
            "        AND unassigned_filter_assignment.status = 'active'",
            "        AND unassigned_filter_assignment.is_deleted = b'0'",
            "      WHERE unassigned_filter_item.order_id = procurement_ali1688_order_header.id",
            "        AND unassigned_filter_item.is_deleted = b'0'",
            "      GROUP BY unassigned_filter_item.id",
            "      HAVING COALESCE(SUM(unassigned_filter_assignment.assigned_quantity), 0) = 0",
            "    )",
            "  </if>",
            "  <if test=\"query.assignmentState == 'consumable'\">",
            "    AND EXISTS (",
            "      SELECT 1",
            "      FROM procurement_ali1688_order_item_assignment consumable_filter_assignment",
            "      WHERE consumable_filter_assignment.order_id = procurement_ali1688_order_header.id",
            "        AND consumable_filter_assignment.owner_user_id = #{ownerUserId}",
            "        AND consumable_filter_assignment.target_type = 'CONSUMABLE'",
            "        AND consumable_filter_assignment.status = 'active'",
            "        AND consumable_filter_assignment.is_deleted = b'0'",
            "    )",
            "  </if>",
            "  <if test='query.assignmentTargetStoreCode != null and query.assignmentTargetStoreCode != \"\"'>",
            "    AND EXISTS (",
            "      SELECT 1",
            "      FROM procurement_ali1688_order_item_assignment assignment_target_filter",
            "      WHERE assignment_target_filter.order_id = procurement_ali1688_order_header.id",
            "        AND assignment_target_filter.owner_user_id = #{ownerUserId}",
            "        AND assignment_target_filter.target_type = 'STORE_SITE'",
            "        AND assignment_target_filter.target_store_code = #{query.assignmentTargetStoreCode}",
            "        <if test='query.assignmentTargetSiteCode != null and query.assignmentTargetSiteCode != \"\"'>",
            "          AND assignment_target_filter.target_site_code = #{query.assignmentTargetSiteCode}",
            "        </if>",
            "        AND assignment_target_filter.status = 'active'",
            "        AND assignment_target_filter.is_deleted = b'0'",
            "    )",
            "  </if>",
            "  <if test=\"query.productLinkState == 'linked'\">",
            "    AND EXISTS (",
            "      SELECT 1",
            "      FROM procurement_ali1688_order_item_assignment product_link_filter_assignment",
            "      JOIN procurement_ali1688_order_item_product_link product_link_filter_link",
            "        ON product_link_filter_link.assignment_id = product_link_filter_assignment.id",
            "        AND product_link_filter_link.owner_user_id = #{ownerUserId}",
            "        AND product_link_filter_link.status = 'active'",
            "        AND product_link_filter_link.is_deleted = b'0'",
            "      WHERE product_link_filter_assignment.order_id = procurement_ali1688_order_header.id",
            "        AND product_link_filter_assignment.owner_user_id = #{ownerUserId}",
            "        AND product_link_filter_assignment.target_type = 'STORE_SITE'",
            "        AND product_link_filter_assignment.status = 'active'",
            "        AND product_link_filter_assignment.is_deleted = b'0'",
            "    )",
            "  </if>",
            "  <if test=\"query.productLinkState == 'unlinked'\">",
            "    AND EXISTS (",
            "      SELECT 1",
            "      FROM procurement_ali1688_order_item_assignment product_link_filter_assignment",
            "      LEFT JOIN procurement_ali1688_order_item_product_link product_link_filter_unlinked",
            "        ON product_link_filter_unlinked.assignment_id = product_link_filter_assignment.id",
            "        AND product_link_filter_unlinked.owner_user_id = #{ownerUserId}",
            "        AND product_link_filter_unlinked.status = 'active'",
            "        AND product_link_filter_unlinked.is_deleted = b'0'",
            "      WHERE product_link_filter_assignment.order_id = procurement_ali1688_order_header.id",
            "        AND product_link_filter_assignment.owner_user_id = #{ownerUserId}",
            "        AND product_link_filter_assignment.target_type = 'STORE_SITE'",
            "        AND product_link_filter_assignment.status = 'active'",
            "        AND product_link_filter_assignment.is_deleted = b'0'",
            "        AND product_link_filter_unlinked.id IS NULL",
            "    )",
            "  </if>",
            "  <if test='query.keyword != null and query.keyword != \"\"'>",
            "    AND (",
            "      provider_order_no LIKE CONCAT('%', #{query.keyword}, '%')",
            "      OR supplier_name LIKE CONCAT('%', #{query.keyword}, '%')",
            "      OR buyer_company_name LIKE CONCAT('%', #{query.keyword}, '%')",
            "      OR seller_member_name LIKE CONCAT('%', #{query.keyword}, '%')",
            "      OR EXISTS (",
            "        SELECT 1",
            "        FROM procurement_ali1688_order_item item_filter",
            "        WHERE item_filter.order_id = procurement_ali1688_order_header.id",
            "          AND item_filter.is_deleted = b'0'",
            "          AND (",
            "            item_filter.title LIKE CONCAT('%', #{query.keyword}, '%')",
            "            OR item_filter.offer_id LIKE CONCAT('%', #{query.keyword}, '%')",
            "            OR item_filter.sku_id LIKE CONCAT('%', #{query.keyword}, '%')",
            "            OR item_filter.sku_text LIKE CONCAT('%', #{query.keyword}, '%')",
            "            OR item_filter.model_text LIKE CONCAT('%', #{query.keyword}, '%')",
            "            OR item_filter.product_code LIKE CONCAT('%', #{query.keyword}, '%')",
            "            OR item_filter.single_product_code LIKE CONCAT('%', #{query.keyword}, '%')",
            "          )",
            "      )",
            "      OR EXISTS (",
            "        SELECT 1",
            "        FROM procurement_ali1688_order_logistics logistics_filter",
            "        WHERE logistics_filter.order_id = procurement_ali1688_order_header.id",
            "          AND logistics_filter.is_deleted = b'0'",
            "          AND (",
            "            logistics_filter.logistics_company LIKE CONCAT('%', #{query.keyword}, '%')",
            "            OR logistics_filter.tracking_no LIKE CONCAT('%', #{query.keyword}, '%')",
            "          )",
            "      )",
            "    )",
            "  </if>",
            "</script>"
    })
    int countOrders(
            @Param("ownerUserId") Long ownerUserId,
            @Param("authorizationIds") List<Long> authorizationIds,
            @Param("query") Ali1688HistoricalOrderQuery query
    );

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM procurement_ali1688_order_item oi",
            "JOIN procurement_ali1688_order_header oh ON oh.id = oi.order_id",
            "  AND oh.is_deleted = b'0'",
            "WHERE oh.owner_user_id = #{ownerUserId}",
            "  AND oh.authorization_id IN",
            "  <foreach collection='authorizationIds' item='authorizationId' open='(' separator=',' close=')'>",
            "    #{authorizationId}",
            "  </foreach>",
            "  AND oi.is_deleted = b'0'",
            "  AND NOT EXISTS (",
            "    SELECT 1",
            "    FROM procurement_ali1688_order_authorization duplicate_source",
            "    JOIN procurement_ali1688_order_header openapi_duplicate_order",
            "      ON openapi_duplicate_order.owner_user_id = oh.owner_user_id",
            "     AND openapi_duplicate_order.provider_order_no = oh.provider_order_no",
            "     AND openapi_duplicate_order.is_deleted = b'0'",
            "    JOIN procurement_ali1688_order_authorization openapi_duplicate",
            "      ON openapi_duplicate.id = openapi_duplicate_order.authorization_id",
            "     AND openapi_duplicate.owner_user_id = #{ownerUserId}",
            "     AND openapi_duplicate.provider_code = 'ALI1688_OPEN_API'",
            "     AND openapi_duplicate.is_deleted = b'0'",
            "    WHERE duplicate_source.id = oh.authorization_id",
            "      AND duplicate_source.owner_user_id = #{ownerUserId}",
            "      AND duplicate_source.provider_code IN ('ALI1688_EXCEL_LOCAL', 'ALI1688_EXCEL_UPLOAD')",
            "      AND duplicate_source.is_deleted = b'0'",
            "      AND openapi_duplicate_order.authorization_id IN",
            "      <foreach collection='authorizationIds' item='authorizationId' open='(' separator=',' close=')'>",
            "        #{authorizationId}",
            "      </foreach>",
            "  )",
            "</script>"
    })
    int countOrderItems(
            @Param("ownerUserId") Long ownerUserId,
            @Param("authorizationIds") List<Long> authorizationIds
    );
}
