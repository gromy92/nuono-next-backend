package com.nuono.next.infrastructure.mapper;

import com.nuono.next.logisticsquote.LogisticsQuoteOperationPriceItemView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.BundleDetailView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.BundleListItemView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.EvidenceView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.ForwarderView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.QuoteVersionView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.RestrictionView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.RuleView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.ServiceView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.SourceFileView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.SourceNoteView;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface LogisticsQuoteMapper {

    @Select({
            "SELECT COUNT(DISTINCT table_name)",
            "FROM information_schema.tables",
            "WHERE table_schema = #{schema}",
            "  AND table_name IN ('forwarder', 'quote_source_bundle', 'quote_source_file', 'quote_source_note')"
    })
    Integer countExistingSourceTables(@Param("schema") String schema);

    @Select({
            "SELECT COUNT(DISTINCT table_name)",
            "FROM information_schema.tables",
            "WHERE table_schema = #{schema}",
            "  AND table_name IN (",
            "    'forwarder_quote_version',",
            "    'forwarder_service',",
            "    'forwarder_quote_rule',",
            "    'forwarder_restriction_rule',",
            "    'quote_evidence_ref'",
            "  )"
    })
    Integer countExistingQuoteDraftTables(@Param("schema") String schema);

    @Select({
            "SELECT COUNT(DISTINCT table_name)",
            "FROM information_schema.tables",
            "WHERE table_schema = #{schema}",
            "  AND table_name IN (",
            "    'forwarder_quote_service_line',",
            "    'forwarder_quote_cargo_category',",
            "    'forwarder_quote_base_price',",
            "    'forwarder_quote_transport_fee',",
            "    'forwarder_quote_billing_rule',",
            "    'forwarder_warehouse_processing_fee',",
            "    'forwarder_quote_prohibited_item',",
            "    'forwarder_quote_numeric_adjustment',",
            "    'forwarder_quote_numeric_adjustment_log'",
            "  )"
    })
    Integer countExistingOperationQuoteTables(@Param("schema") String schema);

    @Select({
            "SELECT COALESCE(MAX(id), 70000) + 1 FROM forwarder"
    })
    Long nextForwarderId();

    @Select({
            "SELECT COALESCE(MAX(id), 71000) + 1 FROM quote_source_bundle"
    })
    Long nextBundleId();

    @Select({
            "SELECT COALESCE(MAX(id), 72000) + 1 FROM quote_source_file"
    })
    Long nextFileId();

    @Select({
            "SELECT COALESCE(MAX(id), 73000) + 1 FROM quote_source_note"
    })
    Long nextNoteId();

    @Select({
            "SELECT COALESCE(MAX(id), 74000) + 1 FROM forwarder_quote_version"
    })
    Long nextQuoteVersionId();

    @Select({
            "SELECT COALESCE(MAX(id), 75000) + 1 FROM forwarder_service"
    })
    Long nextServiceId();

    @Select({
            "SELECT COALESCE(MAX(id), 76000) + 1 FROM forwarder_quote_rule"
    })
    Long nextQuoteRuleId();

    @Select({
            "SELECT COALESCE(MAX(id), 77000) + 1 FROM forwarder_restriction_rule"
    })
    Long nextRestrictionId();

    @Select({
            "SELECT COALESCE(MAX(id), 78000) + 1 FROM quote_evidence_ref"
    })
    Long nextEvidenceId();

    @Select({
            "SELECT COALESCE(MAX(id), 930000) + 1 FROM forwarder_quote_numeric_adjustment"
    })
    Long nextNumericAdjustmentId();

    @Select({
            "SELECT COALESCE(MAX(id), 940000) + 1 FROM forwarder_quote_numeric_adjustment_log"
    })
    Long nextNumericAdjustmentLogId();

    @Select({
            "SELECT id",
            "FROM forwarder",
            "WHERE name = #{name}",
            "LIMIT 1"
    })
    Long selectForwarderIdByName(@Param("name") String name);

    @Insert({
            "INSERT INTO forwarder (",
            "  id, name, alias, company_name, status, notes, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{name}, #{alias}, #{companyName}, #{status}, #{notes}, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  alias = VALUES(alias),",
            "  company_name = VALUES(company_name),",
            "  status = VALUES(status),",
            "  notes = VALUES(notes),",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertForwarder(
            @Param("id") Long id,
            @Param("name") String name,
            @Param("alias") String alias,
            @Param("companyName") String companyName,
            @Param("status") String status,
            @Param("notes") String notes,
            @Param("updatedBy") Long updatedBy
    );

    @Insert({
            "INSERT INTO quote_source_bundle (",
            "  id, forwarder_id, bundle_name, analysis_status, analysis_summary, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{forwarderId}, #{bundleName}, #{analysisStatus}, #{analysisSummary}, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertBundle(
            @Param("id") Long id,
            @Param("forwarderId") Long forwarderId,
            @Param("bundleName") String bundleName,
            @Param("analysisStatus") String analysisStatus,
            @Param("analysisSummary") String analysisSummary,
            @Param("updatedBy") Long updatedBy
    );

    @Insert({
            "INSERT INTO quote_source_file (",
            "  id, bundle_id, file_name, file_type, file_path, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{bundleId}, #{fileName}, #{fileType}, #{filePath}, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertSourceFile(
            @Param("id") Long id,
            @Param("bundleId") Long bundleId,
            @Param("fileName") String fileName,
            @Param("fileType") String fileType,
            @Param("filePath") String filePath,
            @Param("updatedBy") Long updatedBy
    );

    @Insert({
            "INSERT INTO quote_source_note (",
            "  id, bundle_id, note_type, source_channel, content, author_name, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{bundleId}, #{noteType}, #{sourceChannel}, #{content}, #{authorName}, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertSourceNote(
            @Param("id") Long id,
            @Param("bundleId") Long bundleId,
            @Param("noteType") String noteType,
            @Param("sourceChannel") String sourceChannel,
            @Param("content") String content,
            @Param("authorName") String authorName,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE quote_source_note",
            "SET content = #{content},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{noteId}",
            "  AND bundle_id = #{bundleId}"
    })
    int updateSourceNoteContent(
            @Param("bundleId") Long bundleId,
            @Param("noteId") Long noteId,
            @Param("content") String content,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE quote_source_file",
            "SET file_name = #{fileName},",
            "    file_type = #{fileType},",
            "    file_path = #{filePath},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{fileId}",
            "  AND bundle_id = #{bundleId}"
    })
    int updateSourceFileMetadata(
            @Param("bundleId") Long bundleId,
            @Param("fileId") Long fileId,
            @Param("fileName") String fileName,
            @Param("fileType") String fileType,
            @Param("filePath") String filePath,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE quote_source_bundle",
            "SET analysis_summary = #{analysisSummary},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{bundleId}"
    })
    int updateBundleAnalysisSummary(
            @Param("bundleId") Long bundleId,
            @Param("analysisSummary") String analysisSummary,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE quote_source_bundle",
            "SET analysis_status = #{analysisStatus},",
            "    updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{bundleId}"
    })
    int updateBundleAnalysisStatus(
            @Param("bundleId") Long bundleId,
            @Param("analysisStatus") String analysisStatus,
            @Param("updatedBy") Long updatedBy
    );

    @Update({
            "UPDATE quote_source_bundle",
            "SET updated_by = #{updatedBy},",
            "    gmt_updated = NOW()",
            "WHERE id = #{bundleId}"
    })
    int touchBundle(
            @Param("bundleId") Long bundleId,
            @Param("updatedBy") Long updatedBy
    );

    @Delete({
            "DELETE evidence",
            "FROM quote_evidence_ref evidence",
            "JOIN forwarder_quote_version version ON version.id = evidence.quote_version_id",
            "WHERE version.bundle_id = #{bundleId}",
            "  AND version.status = 'DRAFT'"
    })
    int deleteDraftEvidencesForBundle(@Param("bundleId") Long bundleId);

    @Delete({
            "DELETE restriction",
            "FROM forwarder_restriction_rule restriction",
            "JOIN forwarder_service service ON service.id = restriction.service_id",
            "JOIN forwarder_quote_version version ON version.id = service.quote_version_id",
            "WHERE version.bundle_id = #{bundleId}",
            "  AND version.status = 'DRAFT'"
    })
    int deleteDraftRestrictionsForBundle(@Param("bundleId") Long bundleId);

    @Delete({
            "DELETE rule_item",
            "FROM forwarder_quote_rule rule_item",
            "JOIN forwarder_service service ON service.id = rule_item.service_id",
            "JOIN forwarder_quote_version version ON version.id = service.quote_version_id",
            "WHERE version.bundle_id = #{bundleId}",
            "  AND version.status = 'DRAFT'"
    })
    int deleteDraftRulesForBundle(@Param("bundleId") Long bundleId);

    @Delete({
            "DELETE service",
            "FROM forwarder_service service",
            "JOIN forwarder_quote_version version ON version.id = service.quote_version_id",
            "WHERE version.bundle_id = #{bundleId}",
            "  AND version.status = 'DRAFT'"
    })
    int deleteDraftServicesForBundle(@Param("bundleId") Long bundleId);

    @Delete({
            "DELETE FROM forwarder_quote_version",
            "WHERE bundle_id = #{bundleId}",
            "  AND status = 'DRAFT'"
    })
    int deleteDraftVersionsForBundle(@Param("bundleId") Long bundleId);

    @Insert({
            "INSERT INTO forwarder_quote_version (",
            "  id, forwarder_id, bundle_id, version_no, effective_from, status, summary, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{forwarderId}, #{bundleId}, #{versionNo}, #{effectiveFrom}, #{status}, #{summary}, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertQuoteVersion(
            @Param("id") Long id,
            @Param("forwarderId") Long forwarderId,
            @Param("bundleId") Long bundleId,
            @Param("versionNo") String versionNo,
            @Param("effectiveFrom") String effectiveFrom,
            @Param("status") String status,
            @Param("summary") String summary,
            @Param("updatedBy") Long updatedBy
    );

    @Insert({
            "INSERT INTO forwarder_service (",
            "  id, quote_version_id, service_name, country_code, route_code, transport_mode, business_type, service_scope, remarks, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{quoteVersionId}, #{serviceName}, #{countryCode}, #{routeCode}, #{transportMode}, #{businessType}, #{serviceScope}, #{remarks}, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertForwarderService(
            @Param("id") Long id,
            @Param("quoteVersionId") Long quoteVersionId,
            @Param("serviceName") String serviceName,
            @Param("countryCode") String countryCode,
            @Param("routeCode") String routeCode,
            @Param("transportMode") String transportMode,
            @Param("businessType") String businessType,
            @Param("serviceScope") String serviceScope,
            @Param("remarks") String remarks,
            @Param("updatedBy") Long updatedBy
    );

    @Insert({
            "INSERT INTO forwarder_quote_rule (",
            "  id, service_id, rule_name, rule_type, cargo_category_l1, billing_unit, currency, unit_price, calc_basis, remarks, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{serviceId}, #{ruleName}, #{ruleType}, #{cargoCategory}, #{billingUnit}, #{currency}, #{unitPrice}, #{calcBasis}, #{remarks}, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertQuoteRule(
            @Param("id") Long id,
            @Param("serviceId") Long serviceId,
            @Param("ruleName") String ruleName,
            @Param("ruleType") String ruleType,
            @Param("cargoCategory") String cargoCategory,
            @Param("billingUnit") String billingUnit,
            @Param("currency") String currency,
            @Param("unitPrice") Double unitPrice,
            @Param("calcBasis") String calcBasis,
            @Param("remarks") String remarks,
            @Param("updatedBy") Long updatedBy
    );

    @Insert({
            "INSERT INTO forwarder_restriction_rule (",
            "  id, service_id, restriction_type, restriction_operator, restriction_value, unit, description, severity, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{serviceId}, #{restrictionType}, #{operator}, #{value}, #{unit}, #{description}, #{severity}, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertRestrictionRule(
            @Param("id") Long id,
            @Param("serviceId") Long serviceId,
            @Param("restrictionType") String restrictionType,
            @Param("operator") String operator,
            @Param("value") String value,
            @Param("unit") String unit,
            @Param("description") String description,
            @Param("severity") String severity,
            @Param("updatedBy") Long updatedBy
    );

    @Insert({
            "INSERT INTO quote_evidence_ref (",
            "  id, quote_version_id, target_type, target_id, source_type, source_id, locator, evidence_text, confidence_score, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{quoteVersionId}, #{targetType}, #{targetId}, #{sourceType}, #{sourceId}, #{locator}, #{evidenceText}, #{confidenceScore}, #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertEvidenceRef(
            @Param("id") Long id,
            @Param("quoteVersionId") Long quoteVersionId,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            @Param("sourceType") String sourceType,
            @Param("sourceId") Long sourceId,
            @Param("locator") String locator,
            @Param("evidenceText") String evidenceText,
            @Param("confidenceScore") Double confidenceScore,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            "SELECT",
            "  bundle.id,",
            "  bundle.bundle_name AS bundle_name,",
            "  forwarder.name AS forwarder_name,",
            "  bundle.analysis_status AS analysis_status,",
            "  COALESCE(latest_version.version_no, '未生成') AS latest_version_no,",
            "  COALESCE(latest_version.status, 'SOURCE_ONLY') AS latest_version_status,",
            "  NULL AS recommendation_level,",
            "  COALESCE(file_summary.file_count, 0) AS file_count,",
            "  COALESCE(note_summary.note_count, 0) AS note_count,",
            "  DATE_FORMAT(bundle.gmt_updated, '%Y-%m-%d %H:%i:%s') AS updated_at",
            "FROM quote_source_bundle bundle",
            "JOIN forwarder ON forwarder.id = bundle.forwarder_id",
            "LEFT JOIN (",
            "  SELECT version.*",
            "  FROM forwarder_quote_version version",
            "  JOIN (",
            "    SELECT bundle_id, MAX(id) AS latest_version_id",
            "    FROM forwarder_quote_version",
            "    GROUP BY bundle_id",
            "  ) latest ON latest.latest_version_id = version.id",
            ") latest_version ON latest_version.bundle_id = bundle.id",
            "LEFT JOIN (",
            "  SELECT bundle_id, COUNT(1) AS file_count",
            "  FROM quote_source_file",
            "  GROUP BY bundle_id",
            ") file_summary ON file_summary.bundle_id = bundle.id",
            "LEFT JOIN (",
            "  SELECT bundle_id, COUNT(1) AS note_count",
            "  FROM quote_source_note",
            "  GROUP BY bundle_id",
            ") note_summary ON note_summary.bundle_id = bundle.id",
            "ORDER BY bundle.gmt_updated DESC, bundle.id DESC"
    })
    List<BundleListItemView> listBundles();

    @Select({
            "SELECT COUNT(1)",
            "FROM forwarder_quote_version",
            "WHERE status = 'PUBLISHED'"
    })
    Integer countPublishedQuoteVersions();

    @Select({
            "SELECT COUNT(1)",
            "FROM forwarder_quote_rule rule_item",
            "JOIN forwarder_service service ON service.id = rule_item.service_id",
            "JOIN forwarder_quote_version version ON version.id = service.quote_version_id"
    })
    Integer countQuoteRules();

    @Insert({
            "INSERT INTO forwarder_quote_numeric_adjustment (",
            "  id, quote_version_id, target_type, target_id, field_name, original_value, adjusted_value, currency, reason, adjustment_status, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{quoteVersionId}, #{targetType}, #{targetId}, #{fieldName}, #{originalValue}, #{adjustedValue}, #{currency}, #{reason}, 'ACTIVE', #{updatedBy}, #{updatedBy}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  original_value = VALUES(original_value),",
            "  adjusted_value = VALUES(adjusted_value),",
            "  currency = VALUES(currency),",
            "  reason = VALUES(reason),",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertNumericAdjustment(
            @Param("id") Long id,
            @Param("quoteVersionId") Long quoteVersionId,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            @Param("fieldName") String fieldName,
            @Param("originalValue") Double originalValue,
            @Param("adjustedValue") Double adjustedValue,
            @Param("currency") String currency,
            @Param("reason") String reason,
            @Param("updatedBy") Long updatedBy
    );

    @Select({
            "SELECT id",
            "FROM forwarder_quote_numeric_adjustment",
            "WHERE target_type = #{targetType}",
            "  AND target_id = #{targetId}",
            "  AND field_name = #{fieldName}",
            "  AND adjustment_status = 'ACTIVE'",
            "LIMIT 1"
    })
    Long selectActiveNumericAdjustmentId(
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            @Param("fieldName") String fieldName
    );

    @Insert({
            "INSERT INTO forwarder_quote_numeric_adjustment_log (",
            "  id, adjustment_id, quote_version_id, target_type, target_id, field_name, before_value, after_value, action_type, reason, operated_by, gmt_create",
            ") VALUES (",
            "  #{id}, #{adjustmentId}, #{quoteVersionId}, #{targetType}, #{targetId}, #{fieldName}, #{beforeValue}, #{afterValue}, #{actionType}, #{reason}, #{operatedBy}, NOW()",
            ")"
    })
    int insertNumericAdjustmentLog(
            @Param("id") Long id,
            @Param("adjustmentId") Long adjustmentId,
            @Param("quoteVersionId") Long quoteVersionId,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            @Param("fieldName") String fieldName,
            @Param("beforeValue") Double beforeValue,
            @Param("afterValue") Double afterValue,
            @Param("actionType") String actionType,
            @Param("reason") String reason,
            @Param("operatedBy") Long operatedBy
    );

    @Select({
            "<script>",
            "SELECT *",
            "FROM (",
            "  SELECT",
            "    base_price.id AS targetId,",
            "    'BASE_PRICE' AS targetType,",
            "    'unit_price' AS numericField,",
            "    quote_version.id AS quoteVersionId,",
            "    quote_version.version_no AS quoteVersionNo,",
            "    forwarder.id AS forwarderId,",
            "    forwarder.name AS forwarderName,",
            "    service_line.service_code AS serviceCode,",
            "    service_line.service_name AS serviceName,",
            "    service_line.transport_mode AS transportMode,",
            "    base_price.target_platform AS targetPlatform,",
            "    base_price.delivery_city AS deliveryCity,",
            "    base_price.cargo_category_code AS cargoCategoryCode,",
            "    base_price.cargo_category_name AS cargoCategoryName,",
            "    cargo_category.category_level_1 AS categoryLevel1,",
            "    cargo_category.category_level_2 AS categoryLevel2,",
            "    base_price.pricing_model AS pricingModel,",
            "    base_price.currency AS currency,",
            "    base_price.unit_price AS standardValue,",
            "    adjustment.adjusted_value AS adjustedValue,",
            "    COALESCE(adjustment.adjusted_value, base_price.unit_price) AS effectiveValue,",
            "    base_price.billing_unit AS billingUnit,",
            "    base_price.billing_basis AS billingBasis,",
            "    base_price.min_charge AS minCharge,",
            "    base_price.min_billable_unit AS minBillableUnit,",
            "    base_price.price_status AS priceStatus,",
            "    base_price.source_file_name AS sourceFileName,",
            "    TRIM(CONCAT(COALESCE(base_price.source_sheet_or_page, ''), ' ', COALESCE(base_price.source_row_or_locator, ''))) AS sourceLocator,",
            "    base_price.remark AS remark,",
            "    CASE WHEN adjustment.id IS NULL THEN 0 ELSE 1 END AS hasAdjustment,",
            "    adjustment.reason AS adjustmentReason,",
            "    DATE_FORMAT(COALESCE(adjustment.gmt_updated, base_price.gmt_updated), '%Y-%m-%d %H:%i:%s') AS updatedAt",
            "  FROM forwarder_quote_base_price base_price",
            "  JOIN forwarder_quote_service_line service_line ON service_line.service_code = base_price.service_code",
            "  JOIN forwarder_quote_version quote_version ON quote_version.id = base_price.quote_version_id",
            "  JOIN forwarder ON forwarder.id = quote_version.forwarder_id",
            "  LEFT JOIN forwarder_quote_cargo_category cargo_category ON cargo_category.cargo_category_code = base_price.cargo_category_code",
            "  LEFT JOIN forwarder_quote_numeric_adjustment adjustment",
            "    ON adjustment.target_type = 'BASE_PRICE'",
            "   AND adjustment.target_id = base_price.id",
            "   AND adjustment.field_name = 'unit_price'",
            "   AND adjustment.adjustment_status = 'ACTIVE'",
            "  UNION ALL",
            "  SELECT",
            "    transport_fee.id AS targetId,",
            "    'TRANSPORT_FEE' AS targetType,",
            "    CASE WHEN transport_fee.amount IS NOT NULL THEN 'amount' ELSE 'rate' END AS numericField,",
            "    quote_version.id AS quoteVersionId,",
            "    quote_version.version_no AS quoteVersionNo,",
            "    forwarder.id AS forwarderId,",
            "    forwarder.name AS forwarderName,",
            "    service_line.service_code AS serviceCode,",
            "    service_line.service_name AS serviceName,",
            "    service_line.transport_mode AS transportMode,",
            "    transport_fee.target_platform AS targetPlatform,",
            "    transport_fee.delivery_city AS deliveryCity,",
            "    NULL AS cargoCategoryCode,",
            "    transport_fee.fee_name AS cargoCategoryName,",
            "    transport_fee.fee_type AS categoryLevel1,",
            "    transport_fee.trigger_condition AS categoryLevel2,",
            "    transport_fee.pricing_model AS pricingModel,",
            "    transport_fee.currency AS currency,",
            "    COALESCE(transport_fee.amount, transport_fee.rate) AS standardValue,",
            "    adjustment.adjusted_value AS adjustedValue,",
            "    COALESCE(adjustment.adjusted_value, COALESCE(transport_fee.amount, transport_fee.rate)) AS effectiveValue,",
            "    transport_fee.billing_unit AS billingUnit,",
            "    transport_fee.billing_basis AS billingBasis,",
            "    transport_fee.min_charge AS minCharge,",
            "    transport_fee.min_billable_unit AS minBillableUnit,",
            "    CASE WHEN transport_fee.included_in_base_price = b'1' THEN 'INCLUDED' ELSE 'NORMAL' END AS priceStatus,",
            "    transport_fee.source_file_name AS sourceFileName,",
            "    TRIM(CONCAT(COALESCE(transport_fee.source_sheet_or_page, ''), ' ', COALESCE(transport_fee.source_row_or_locator, ''))) AS sourceLocator,",
            "    transport_fee.remark AS remark,",
            "    CASE WHEN adjustment.id IS NULL THEN 0 ELSE 1 END AS hasAdjustment,",
            "    adjustment.reason AS adjustmentReason,",
            "    DATE_FORMAT(COALESCE(adjustment.gmt_updated, transport_fee.gmt_updated), '%Y-%m-%d %H:%i:%s') AS updatedAt",
            "  FROM forwarder_quote_transport_fee transport_fee",
            "  JOIN forwarder_quote_service_line service_line ON service_line.service_code = transport_fee.service_code",
            "  JOIN forwarder_quote_version quote_version ON quote_version.id = transport_fee.quote_version_id",
            "  JOIN forwarder ON forwarder.id = quote_version.forwarder_id",
            "  LEFT JOIN forwarder_quote_numeric_adjustment adjustment",
            "    ON adjustment.target_type = 'TRANSPORT_FEE'",
            "   AND adjustment.target_id = transport_fee.id",
            "   AND adjustment.field_name = CASE WHEN transport_fee.amount IS NOT NULL THEN 'amount' ELSE 'rate' END",
            "   AND adjustment.adjustment_status = 'ACTIVE'",
            "  UNION ALL",
            "  SELECT",
            "    processing_fee.id AS targetId,",
            "    'WAREHOUSE_PROCESSING_FEE' AS targetType,",
            "    'amount' AS numericField,",
            "    quote_version.id AS quoteVersionId,",
            "    quote_version.version_no AS quoteVersionNo,",
            "    forwarder.id AS forwarderId,",
            "    forwarder.name AS forwarderName,",
            "    service_line.service_code AS serviceCode,",
            "    service_line.service_name AS serviceName,",
            "    service_line.transport_mode AS transportMode,",
            "    processing_fee.target_platform AS targetPlatform,",
            "    processing_fee.warehouse_city AS deliveryCity,",
            "    NULL AS cargoCategoryCode,",
            "    processing_fee.fee_name AS cargoCategoryName,",
            "    processing_fee.fee_type AS categoryLevel1,",
            "    processing_fee.processing_scope AS categoryLevel2,",
            "    processing_fee.pricing_model AS pricingModel,",
            "    processing_fee.currency AS currency,",
            "    processing_fee.amount AS standardValue,",
            "    adjustment.adjusted_value AS adjustedValue,",
            "    COALESCE(adjustment.adjusted_value, processing_fee.amount) AS effectiveValue,",
            "    processing_fee.billing_unit AS billingUnit,",
            "    processing_fee.condition_text AS billingBasis,",
            "    processing_fee.min_charge AS minCharge,",
            "    NULL AS minBillableUnit,",
            "    CASE WHEN processing_fee.amount = 0 THEN 'FREE' WHEN processing_fee.amount IS NULL THEN 'INQUIRY' ELSE 'NORMAL' END AS priceStatus,",
            "    processing_fee.source_file_name AS sourceFileName,",
            "    TRIM(CONCAT(COALESCE(processing_fee.source_sheet_or_page, ''), ' ', COALESCE(processing_fee.source_row_or_locator, ''))) AS sourceLocator,",
            "    processing_fee.remark AS remark,",
            "    CASE WHEN adjustment.id IS NULL THEN 0 ELSE 1 END AS hasAdjustment,",
            "    adjustment.reason AS adjustmentReason,",
            "    DATE_FORMAT(COALESCE(adjustment.gmt_updated, processing_fee.gmt_updated), '%Y-%m-%d %H:%i:%s') AS updatedAt",
            "  FROM forwarder_warehouse_processing_fee processing_fee",
            "  JOIN forwarder_quote_service_line service_line ON service_line.service_code = processing_fee.service_code",
            "  JOIN forwarder_quote_version quote_version ON quote_version.id = processing_fee.quote_version_id",
            "  JOIN forwarder ON forwarder.id = quote_version.forwarder_id",
            "  LEFT JOIN forwarder_quote_numeric_adjustment adjustment",
            "    ON adjustment.target_type = 'WAREHOUSE_PROCESSING_FEE'",
            "   AND adjustment.target_id = processing_fee.id",
            "   AND adjustment.field_name = 'amount'",
            "   AND adjustment.adjustment_status = 'ACTIVE'",
            ") operation_item",
            "WHERE 1 = 1",
            "<if test='transportMode != null and transportMode != \"\"'>",
            "  AND operation_item.transportMode = #{transportMode}",
            "</if>",
            "<if test='forwarderId != null'>",
            "  AND operation_item.forwarderId = #{forwarderId}",
            "</if>",
            "<if test='priceStatus != null and priceStatus != \"\"'>",
            "  AND operation_item.priceStatus = #{priceStatus}",
            "</if>",
            "ORDER BY FIELD(operation_item.transportMode, 'AIR', 'SEA', 'WAREHOUSE'),",
            "         operation_item.forwarderName,",
            "         operation_item.serviceCode,",
            "         FIELD(operation_item.targetType, 'BASE_PRICE', 'TRANSPORT_FEE', 'WAREHOUSE_PROCESSING_FEE'),",
            "         operation_item.targetId",
            "</script>"
    })
    List<LogisticsQuoteOperationPriceItemView> listOperationPriceItems(
            @Param("transportMode") String transportMode,
            @Param("forwarderId") Long forwarderId,
            @Param("priceStatus") String priceStatus
    );

    @Select({
            "SELECT",
            "  bundle.id,",
            "  bundle.bundle_name AS bundle_name,",
            "  bundle.analysis_status AS analysis_status,",
            "  bundle.analysis_summary AS analysis_summary,",
            "  forwarder.id AS `forwarder.id`,",
            "  forwarder.name AS `forwarder.name`,",
            "  forwarder.alias AS `forwarder.alias`,",
            "  forwarder.company_name AS `forwarder.company_name`,",
            "  forwarder.notes AS `forwarder.notes`",
            "FROM quote_source_bundle bundle",
            "JOIN forwarder ON forwarder.id = bundle.forwarder_id",
            "WHERE bundle.id = #{bundleId}",
            "LIMIT 1"
    })
    BundleDetailView selectBundleDetail(@Param("bundleId") Long bundleId);

    @Select({
            "SELECT",
            "  id,",
            "  version_no AS versionNo,",
            "  status,",
            "  summary,",
            "  DATE_FORMAT(effective_from, '%Y-%m-%d') AS effectiveFrom",
            "FROM forwarder_quote_version",
            "WHERE bundle_id = #{bundleId}",
            "ORDER BY id DESC",
            "LIMIT 1"
    })
    QuoteVersionView selectLatestQuoteVersionForBundle(@Param("bundleId") Long bundleId);

    @Select({
            "SELECT",
            "  id,",
            "  file_name,",
            "  COALESCE(file_type, 'unknown') AS file_type,",
            "  file_path AS file_path,",
            "  CASE",
            "    WHEN file_path LIKE 'archive://logistics-quotes/%' THEN '已归档原件'",
            "    ELSE COALESCE(file_path, '录入元数据')",
            "  END AS source_label,",
            "  CASE WHEN file_path LIKE 'archive://logistics-quotes/%' THEN 1 ELSE 0 END AS archived,",
            "  CASE",
            "    WHEN file_path LIKE 'archive://logistics-quotes/%' THEN CONCAT('/api/logistics-quote/source-files/', id, '/archive')",
            "    ELSE NULL",
            "  END AS archive_url",
            "FROM quote_source_file",
            "WHERE bundle_id = #{bundleId}",
            "ORDER BY id ASC"
    })
    List<SourceFileView> listSourceFiles(@Param("bundleId") Long bundleId);

    @Select({
            "SELECT",
            "  id,",
            "  file_name,",
            "  COALESCE(file_type, 'unknown') AS file_type,",
            "  file_path AS file_path,",
            "  CASE",
            "    WHEN file_path LIKE 'archive://logistics-quotes/%' THEN '已归档原件'",
            "    ELSE COALESCE(file_path, '录入元数据')",
            "  END AS source_label,",
            "  CASE WHEN file_path LIKE 'archive://logistics-quotes/%' THEN 1 ELSE 0 END AS archived,",
            "  CASE",
            "    WHEN file_path LIKE 'archive://logistics-quotes/%' THEN CONCAT('/api/logistics-quote/source-files/', id, '/archive')",
            "    ELSE NULL",
            "  END AS archive_url",
            "FROM quote_source_file",
            "WHERE id = #{fileId}",
            "LIMIT 1"
    })
    SourceFileView selectSourceFileById(@Param("fileId") Long fileId);

    @Select({
            "SELECT",
            "  id,",
            "  file_name,",
            "  COALESCE(file_type, 'unknown') AS file_type,",
            "  file_path AS file_path,",
            "  CASE",
            "    WHEN file_path LIKE 'archive://logistics-quotes/%' THEN '已归档原件'",
            "    ELSE COALESCE(file_path, '录入元数据')",
            "  END AS source_label,",
            "  CASE WHEN file_path LIKE 'archive://logistics-quotes/%' THEN 1 ELSE 0 END AS archived,",
            "  CASE",
            "    WHEN file_path LIKE 'archive://logistics-quotes/%' THEN CONCAT('/api/logistics-quote/source-files/', id, '/archive')",
            "    ELSE NULL",
            "  END AS archive_url",
            "FROM quote_source_file",
            "WHERE bundle_id = #{bundleId}",
            "  AND id = #{fileId}",
            "LIMIT 1"
    })
    SourceFileView selectSourceFile(
            @Param("bundleId") Long bundleId,
            @Param("fileId") Long fileId
    );

    @Select({
            "SELECT",
            "  id,",
            "  COALESCE(note_type, 'manual_note') AS note_type,",
            "  COALESCE(source_channel, 'manual') AS source_channel,",
            "  content",
            "FROM quote_source_note",
            "WHERE bundle_id = #{bundleId}",
            "ORDER BY id ASC"
    })
    List<SourceNoteView> listSourceNotes(@Param("bundleId") Long bundleId);

    @Select({
            "SELECT",
            "  id,",
            "  COALESCE(note_type, 'manual_note') AS note_type,",
            "  COALESCE(source_channel, 'manual') AS source_channel,",
            "  content",
            "FROM quote_source_note",
            "WHERE bundle_id = #{bundleId}",
            "  AND id = #{noteId}",
            "LIMIT 1"
    })
    SourceNoteView selectSourceNote(
            @Param("bundleId") Long bundleId,
            @Param("noteId") Long noteId
    );

    @Select({
            "SELECT",
            "  service_name AS serviceName,",
            "  country_code AS countryCode,",
            "  route_code AS routeCode,",
            "  transport_mode AS transportMode,",
            "  business_type AS businessType,",
            "  service_scope AS serviceScope,",
            "  transit_time_text AS transitTimeText,",
            "  remarks",
            "FROM forwarder_service",
            "WHERE quote_version_id = #{quoteVersionId}",
            "ORDER BY id ASC"
    })
    List<ServiceView> listServicesForQuoteVersion(@Param("quoteVersionId") Long quoteVersionId);

    @Select({
            "SELECT",
            "  service.service_name AS serviceName,",
            "  rule_item.rule_name AS ruleName,",
            "  rule_item.rule_type AS ruleType,",
            "  rule_item.cargo_category_l1 AS cargoCategory,",
            "  rule_item.billing_unit AS billingUnit,",
            "  rule_item.currency AS currency,",
            "  rule_item.unit_price AS unitPrice,",
            "  rule_item.calc_basis AS calcBasis,",
            "  rule_item.remarks AS summary",
            "FROM forwarder_quote_rule rule_item",
            "JOIN forwarder_service service ON service.id = rule_item.service_id",
            "WHERE service.quote_version_id = #{quoteVersionId}",
            "ORDER BY rule_item.priority ASC, rule_item.id ASC"
    })
    List<RuleView> listRulesForQuoteVersion(@Param("quoteVersionId") Long quoteVersionId);

    @Select({
            "SELECT",
            "  service.service_name AS serviceName,",
            "  restriction.restriction_type AS restrictionType,",
            "  restriction.restriction_operator AS operator,",
            "  restriction.restriction_value AS value,",
            "  restriction.unit AS unit,",
            "  restriction.severity AS severity,",
            "  restriction.description AS description",
            "FROM forwarder_restriction_rule restriction",
            "JOIN forwarder_service service ON service.id = restriction.service_id",
            "WHERE service.quote_version_id = #{quoteVersionId}",
            "ORDER BY restriction.id ASC"
    })
    List<RestrictionView> listRestrictionsForQuoteVersion(@Param("quoteVersionId") Long quoteVersionId);

    @Select({
            "SELECT",
            "  evidence.target_type AS targetType,",
            "  COALESCE(rule_item.rule_name, restriction.restriction_type, CAST(evidence.target_id AS CHAR)) AS targetName,",
            "  evidence.source_type AS sourceType,",
            "  COALESCE(source_file.file_name, source_note.source_channel, CAST(evidence.source_id AS CHAR)) AS sourceName,",
            "  evidence.locator AS locator,",
            "  evidence.evidence_text AS evidenceText",
            "FROM quote_evidence_ref evidence",
            "LEFT JOIN forwarder_quote_rule rule_item",
            "  ON evidence.target_type = 'RULE' AND rule_item.id = evidence.target_id",
            "LEFT JOIN forwarder_restriction_rule restriction",
            "  ON evidence.target_type = 'RESTRICTION' AND restriction.id = evidence.target_id",
            "LEFT JOIN quote_source_file source_file",
            "  ON evidence.source_type = 'FILE' AND source_file.id = evidence.source_id",
            "LEFT JOIN quote_source_note source_note",
            "  ON evidence.source_type = 'NOTE' AND source_note.id = evidence.source_id",
            "WHERE evidence.quote_version_id = #{quoteVersionId}",
            "ORDER BY evidence.id ASC"
    })
    List<EvidenceView> listEvidencesForQuoteVersion(@Param("quoteVersionId") Long quoteVersionId);
}
