package com.nuono.next.infrastructure.mapper;

import com.nuono.next.sales.DailySalesFact;
import com.nuono.next.sales.NoonSalesCsvImportResult;
import com.nuono.next.sales.SalesImportExceptionRecord;
import com.nuono.next.sales.SalesFactQuery;
import com.nuono.next.sales.SalesImportBatch;
import com.nuono.next.sales.SalesImportBatchQuery;
import com.nuono.next.sales.SalesImportBatchRecord;
import com.nuono.next.sales.SalesProductDimensionSnapshot;
import com.nuono.next.sales.SalesActivityWindowRecord;
import com.nuono.next.sales.SalesActivityWindowScope;
import com.nuono.next.sales.SalesSyncTaskCommand;
import com.nuono.next.sales.SalesSyncTaskRecord;
import com.nuono.next.salesforecast.SalesForecastFollowUpCommand;
import com.nuono.next.salesforecast.SalesForecastFollowUpRecord;
import com.nuono.next.salesforecast.SalesForecastResultRecord;
import com.nuono.next.salesforecast.SalesForecastRunRecord;
import com.nuono.next.salesforecast.SalesForecastStockSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.ConstructorArgs;

public interface SalesDataMapper {

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
    int allocateSalesDataId(IdSequenceCommand command);

    default Long nextSalesDataId(String sequenceName, long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        allocateSalesDataId(command);
        Long id = command.getAllocatedId();
        if (id == null || id <= 0) {
            throw new IllegalStateException("销量数据 ID 序列分配失败：" + sequenceName);
        }
        return id;
    }

    default Long nextSalesImportBatchId() {
        return nextSalesDataId("sales_import_batch", 10000L);
    }

    default Long nextDailySalesFactId() {
        return nextSalesDataId("daily_sales_fact", 100000L);
    }

    default Long nextSalesImportExceptionId() {
        return nextSalesDataId("sales_import_exception", 30000L);
    }

    default Long nextSalesSyncTaskId() {
        return nextSalesDataId("sales_sync_task", 20000L);
    }

    default Long nextSalesActivityWindowId() {
        return nextSalesDataId("sales_activity_window", 40000L);
    }

    default Long nextSalesForecastRunId() {
        return nextSalesDataId("sales_forecast_run", 50000L);
    }

    default Long nextSalesForecastResultId() {
        return nextSalesDataId("sales_forecast_result", 60000L);
    }

    default Long nextSalesForecastFollowUpId() {
        return nextSalesDataId("sales_forecast_follow_up", 61000L);
    }

    @Insert({
            "INSERT INTO sales_import_batch (",
            "  id, source_system, source_filename, owner_user_id, logical_store_id, store_code, site_code,",
            "  report_date_from, report_date_to, total_rows, success_rows, failure_rows, status, failure_summary_json, imported_at,",
            "  gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{batch.sourceSystem}, #{batch.sourceFilename}, #{batch.ownerUserId}, #{batch.logicalStoreId},",
            "  #{batch.storeCode}, #{batch.siteCode}, #{batch.reportDateFrom}, #{batch.reportDateTo},",
            "  #{batch.totalRows}, #{batch.successRows}, #{batch.failureRows}, #{batch.status},",
            "  #{batch.failureSummary}, NOW(), NOW(), NOW()",
            ")"
    })
    int insertImportBatch(@Param("id") Long id, @Param("batch") SalesImportBatch batch);

    @Insert({
            "INSERT INTO daily_sales_fact (",
            "  id, source_system, source_batch_id, owner_user_id, logical_store_id, store_code, site_code,",
            "  fact_date, partner_sku, sku, sku_config, country_code, currency_code, product_title,",
            "  your_visitors, total_visitors, gross_units, shipped_units, cancelled_units, net_units,",
            "  revenue_shipped, buy_box_visitor_percentage, conversion_visitors_percentage, asp_shipped_percentage,",
            "  gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{fact.sourceSystem}, #{fact.sourceBatchId}, #{fact.ownerUserId}, #{fact.logicalStoreId},",
            "  #{fact.storeCode}, #{fact.siteCode}, #{fact.factDate}, #{fact.partnerSku}, #{fact.sku},",
            "  #{fact.skuConfig}, #{fact.countryCode}, #{fact.currencyCode}, #{fact.productTitle},",
            "  #{fact.yourVisitors}, #{fact.totalVisitors}, #{fact.grossUnits}, #{fact.shippedUnits},",
            "  #{fact.cancelledUnits}, #{fact.netUnits}, #{fact.revenueShipped}, #{fact.buyBoxVisitorPercentage},",
            "  #{fact.conversionVisitorsPercentage}, #{fact.aspShippedPercentage}, NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  source_batch_id = VALUES(source_batch_id),",
            "  logical_store_id = VALUES(logical_store_id),",
            "  sku_config = VALUES(sku_config),",
            "  country_code = VALUES(country_code),",
            "  currency_code = VALUES(currency_code),",
            "  product_title = VALUES(product_title),",
            "  your_visitors = VALUES(your_visitors),",
            "  total_visitors = VALUES(total_visitors),",
            "  gross_units = VALUES(gross_units),",
            "  shipped_units = VALUES(shipped_units),",
            "  cancelled_units = VALUES(cancelled_units),",
            "  net_units = VALUES(net_units),",
            "  revenue_shipped = VALUES(revenue_shipped),",
            "  buy_box_visitor_percentage = VALUES(buy_box_visitor_percentage),",
            "  conversion_visitors_percentage = VALUES(conversion_visitors_percentage),",
            "  asp_shipped_percentage = VALUES(asp_shipped_percentage),",
            "  gmt_updated = NOW()"
    })
    int upsertDailySalesFact(@Param("id") Long id, @Param("fact") DailySalesFact fact);

    @Insert({
            "INSERT INTO sales_import_exception (",
            "  id, source_batch_id, source_filename, owner_user_id, store_code, site_code,",
            "  row_number, exception_type, field_name, source_value, source_context, message,",
            "  resolution_hint, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{record.sourceBatchId}, #{record.sourceFilename}, #{record.ownerUserId},",
            "  #{record.storeCode}, #{record.siteCode}, #{record.rowNumber}, #{record.exceptionType},",
            "  #{record.fieldName}, #{record.sourceValue}, #{record.sourceContext}, #{record.message},",
            "  #{record.resolutionHint}, NOW(), NOW()",
            ")"
    })
    int insertSalesImportException(
            @Param("id") Long id,
            @Param("record") SalesImportExceptionRecord record
    );

    @Select({
            "<script>",
            "SELECT",
            "  source_system AS sourceSystem,",
            "  source_batch_id AS sourceBatchId,",
            "  owner_user_id AS ownerUserId,",
            "  logical_store_id AS logicalStoreId,",
            "  store_code AS storeCode,",
            "  site_code AS siteCode,",
            "  fact_date AS factDate,",
            "  partner_sku AS partnerSku,",
            "  sku,",
            "  sku_config AS skuConfig,",
            "  country_code AS countryCode,",
            "  currency_code AS currencyCode,",
            "  product_title AS productTitle,",
            "  your_visitors AS yourVisitors,",
            "  total_visitors AS totalVisitors,",
            "  gross_units AS grossUnits,",
            "  shipped_units AS shippedUnits,",
            "  cancelled_units AS cancelledUnits,",
            "  net_units AS netUnits,",
            "  revenue_shipped AS revenueShipped,",
            "  buy_box_visitor_percentage AS buyBoxVisitorPercentage,",
            "  conversion_visitors_percentage AS conversionVisitorsPercentage,",
            "  asp_shipped_percentage AS aspShippedPercentage",
            "FROM daily_sales_fact",
            "WHERE owner_user_id = #{query.ownerUserId}",
            "  AND store_code = #{query.storeCode}",
            "  AND site_code = #{query.siteCode}",
            "  AND fact_date &gt;= #{query.dateFrom}",
            "  AND fact_date &lt;= #{query.dateTo}",
            "<if test='query.partnerSku != null and query.partnerSku != \"\"'>",
            "  AND partner_sku = #{query.partnerSku}",
            "</if>",
            "<if test='query.partnerSkuList != null and query.partnerSkuList.size() &gt; 0'>",
            "  AND partner_sku IN",
            "  <foreach item='partnerSku' collection='query.partnerSkuList' open='(' separator=',' close=')'>",
            "    #{partnerSku}",
            "  </foreach>",
            "</if>",
            "<if test='query.sku != null and query.sku != \"\"'>",
            "  AND sku = #{query.sku}",
            "</if>",
            "<if test='query.searchKeyword != null and query.searchKeyword != \"\"'>",
            "  AND (",
            "    LOWER(partner_sku) LIKE CONCAT('%', LOWER(#{query.searchKeyword}), '%')",
            "    OR LOWER(sku) LIKE CONCAT('%', LOWER(#{query.searchKeyword}), '%')",
            "    OR LOWER(sku_config) LIKE CONCAT('%', LOWER(#{query.searchKeyword}), '%')",
            "    OR LOWER(product_title) LIKE CONCAT('%', LOWER(#{query.searchKeyword}), '%')",
            "  )",
            "</if>",
            "ORDER BY fact_date ASC, partner_sku ASC, sku ASC",
            "</script>"
    })
    List<DailySalesFact> selectDailySalesFacts(@Param("query") SalesFactQuery query);

    @Select({
            "SELECT MAX(fact_date)",
            "FROM daily_sales_fact",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND store_code = #{storeCode}",
            "  AND site_code = #{siteCode}"
    })
    LocalDate selectLatestDailySalesFactDate(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );

    @Insert({
            "INSERT INTO sales_forecast_run (",
            "  id, owner_user_id, store_code, site_code, source_data_date,",
            "  calculation_version, config_version, calendar_version_no, calendar_version_name,",
            "  calendar_version_source_label, lifecycle_version_no, lifecycle_version_name,",
            "  lifecycle_version_source_label, status, result_count, calculated_at,",
            "  gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{run.ownerUserId}, #{run.storeCode}, #{run.siteCode}, #{run.sourceDataDate},",
            "  #{run.calculationVersion}, #{run.configVersion}, #{run.calendarVersionNo}, #{run.calendarVersionName},",
            "  #{run.calendarVersionSourceLabel}, #{run.lifecycleVersionNo}, #{run.lifecycleVersionName},",
            "  #{run.lifecycleVersionSourceLabel}, #{run.status}, #{run.resultCount}, NOW(),",
            "  NOW(), NOW()",
            ")"
    })
    int insertSalesForecastRun(@Param("id") Long id, @Param("run") SalesForecastRunRecord run);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class),
            @Arg(column = "sourceDataDate", javaType = LocalDate.class),
            @Arg(column = "calculationVersion", javaType = String.class),
            @Arg(column = "configVersion", javaType = String.class),
            @Arg(column = "calendarVersionNo", javaType = String.class),
            @Arg(column = "calendarVersionName", javaType = String.class),
            @Arg(column = "calendarVersionSourceLabel", javaType = String.class),
            @Arg(column = "lifecycleVersionNo", javaType = String.class),
            @Arg(column = "lifecycleVersionName", javaType = String.class),
            @Arg(column = "lifecycleVersionSourceLabel", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "resultCount", javaType = int.class),
            @Arg(column = "calculatedAt", javaType = LocalDateTime.class)
    })
    @Select({
            "SELECT",
            "  id,",
            "  owner_user_id AS ownerUserId,",
            "  store_code AS storeCode,",
            "  site_code AS siteCode,",
            "  source_data_date AS sourceDataDate,",
            "  calculation_version AS calculationVersion,",
            "  config_version AS configVersion,",
            "  calendar_version_no AS calendarVersionNo,",
            "  calendar_version_name AS calendarVersionName,",
            "  calendar_version_source_label AS calendarVersionSourceLabel,",
            "  lifecycle_version_no AS lifecycleVersionNo,",
            "  lifecycle_version_name AS lifecycleVersionName,",
            "  lifecycle_version_source_label AS lifecycleVersionSourceLabel,",
            "  status,",
            "  result_count AS resultCount,",
            "  calculated_at AS calculatedAt",
            "FROM sales_forecast_run",
            "WHERE id = #{id}"
    })
    SalesForecastRunRecord selectSalesForecastRunById(@Param("id") Long id);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class),
            @Arg(column = "sourceDataDate", javaType = LocalDate.class),
            @Arg(column = "calculationVersion", javaType = String.class),
            @Arg(column = "configVersion", javaType = String.class),
            @Arg(column = "calendarVersionNo", javaType = String.class),
            @Arg(column = "calendarVersionName", javaType = String.class),
            @Arg(column = "calendarVersionSourceLabel", javaType = String.class),
            @Arg(column = "lifecycleVersionNo", javaType = String.class),
            @Arg(column = "lifecycleVersionName", javaType = String.class),
            @Arg(column = "lifecycleVersionSourceLabel", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "resultCount", javaType = int.class),
            @Arg(column = "calculatedAt", javaType = LocalDateTime.class)
    })
    @Select({
            "SELECT",
            "  id,",
            "  owner_user_id AS ownerUserId,",
            "  store_code AS storeCode,",
            "  site_code AS siteCode,",
            "  source_data_date AS sourceDataDate,",
            "  calculation_version AS calculationVersion,",
            "  config_version AS configVersion,",
            "  calendar_version_no AS calendarVersionNo,",
            "  calendar_version_name AS calendarVersionName,",
            "  calendar_version_source_label AS calendarVersionSourceLabel,",
            "  lifecycle_version_no AS lifecycleVersionNo,",
            "  lifecycle_version_name AS lifecycleVersionName,",
            "  lifecycle_version_source_label AS lifecycleVersionSourceLabel,",
            "  status,",
            "  result_count AS resultCount,",
            "  calculated_at AS calculatedAt",
            "FROM sales_forecast_run",
            "WHERE owner_user_id = #{query.ownerUserId}",
            "  AND store_code = #{query.storeCode}",
            "  AND site_code = #{query.siteCode}",
            "  AND status = 'succeeded'",
            "ORDER BY source_data_date DESC, calculated_at DESC, id DESC",
            "LIMIT 1"
    })
    SalesForecastRunRecord selectLatestSalesForecastRun(
            @Param("query") com.nuono.next.salesforecast.SalesForecastQuery query
    );

    @ConstructorArgs({
            @Arg(column = "partnerSku", javaType = String.class),
            @Arg(column = "sku", javaType = String.class),
            @Arg(column = "currentStock", javaType = Integer.class)
    })
    @Select({
            "SELECT",
            "  pv.partner_sku AS partnerSku,",
            "  COALESCE(NULLIF(pv.child_sku, ''), NULLIF(pso.offer_code, ''), NULLIF(pso.psku_code, '')) AS sku,",
            "  CASE",
            "    WHEN pso.fbn_stock IS NULL AND pso.supermall_stock IS NULL AND pso.fbp_stock IS NULL THEN NULL",
            "    ELSE COALESCE(pso.fbn_stock, 0) + COALESCE(pso.supermall_stock, 0) + COALESCE(pso.fbp_stock, 0)",
            "  END AS currentStock",
            "FROM product_site_offer pso",
            "JOIN product_variant pv ON pv.id = pso.variant_id AND pv.is_deleted = 0",
            "JOIN product_master pm ON pm.id = pv.product_master_id AND pm.is_deleted = 0",
            "JOIN logical_store ls ON ls.id = pm.logical_store_id AND ls.is_deleted = 0",
            "JOIN logical_store_site lss ON lss.id = pso.site_id",
            "  AND lss.logical_store_id = ls.id",
            "  AND lss.is_deleted = 0",
            "WHERE ls.owner_user_id = #{query.ownerUserId}",
            "  AND lss.store_code = #{query.storeCode}",
            "  AND lss.site = #{query.siteCode}",
            "  AND pso.is_deleted = 0",
            "ORDER BY pv.partner_sku ASC, sku ASC"
    })
    List<SalesForecastStockSnapshot> selectSalesForecastCurrentStock(
            @Param("query") com.nuono.next.salesforecast.SalesForecastQuery query
    );

    @Insert({
            "INSERT INTO sales_forecast_result (",
            "  id, run_id, owner_user_id, store_code, site_code, partner_sku, sku, product_title,",
            "  latest_fact_date, history_units_7, history_units_30, history_units_60, history_units_90,",
            "  observed_days, current_stock, stock_cover_days, forecast_units_30, forecast_units_60, forecast_units_90,",
            "  lifecycle_code, lifecycle_label, calculation_version, config_version, base_daily_sales, recent_daily_trend_rate,",
            "  trend_factor, lifecycle_factor, future_factor, lifecycle_explanation, confidence_level, confidence_label,",
            "  confidence_explanation, warning_codes, risk_codes, activity_window_summary, activity_explanation, short_reason,",
            "  feature_snapshot_json, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{runId}, #{record.ownerUserId}, #{record.storeCode}, #{record.siteCode},",
            "  #{record.partnerSku}, #{record.sku}, #{record.productTitle}, #{record.latestFactDate},",
            "  #{record.historyUnits7}, #{record.historyUnits30}, #{record.historyUnits60},",
            "  #{record.historyUnits90}, #{record.observedDays}, #{record.currentStock}, #{record.stockCoverDays}, #{record.forecastUnits30},",
            "  #{record.forecastUnits60}, #{record.forecastUnits90}, #{record.lifecycleCode},",
            "  #{record.lifecycleLabel}, #{record.calculationVersion}, #{record.configVersion},",
            "  #{record.baseDailySales}, #{record.recentDailyTrendRate}, #{record.trendFactor},",
            "  #{record.lifecycleFactor}, #{record.futureFactor}, #{record.lifecycleExplanation},",
            "  #{record.confidenceLevel}, #{record.confidenceLabel}, #{record.confidenceExplanation},",
            "  #{record.warningCodes}, #{record.riskCodes},",
            "  #{record.activityWindowSummary}, #{record.activityExplanation},",
            "  #{record.shortReason},",
            "  #{record.featureSnapshotJson}, NOW(), NOW()",
            ")"
    })
    int insertSalesForecastResult(
            @Param("id") Long id,
            @Param("runId") Long runId,
            @Param("record") SalesForecastResultRecord record
    );

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "runId", javaType = Long.class),
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class),
            @Arg(column = "partnerSku", javaType = String.class),
            @Arg(column = "sku", javaType = String.class),
            @Arg(column = "productTitle", javaType = String.class),
            @Arg(column = "latestFactDate", javaType = LocalDate.class),
            @Arg(column = "historyUnits7", javaType = int.class),
            @Arg(column = "historyUnits30", javaType = int.class),
            @Arg(column = "historyUnits60", javaType = int.class),
            @Arg(column = "historyUnits90", javaType = int.class),
            @Arg(column = "observedDays", javaType = int.class),
            @Arg(column = "currentStock", javaType = Integer.class),
            @Arg(column = "stockCoverDays", javaType = BigDecimal.class),
            @Arg(column = "forecastUnits30", javaType = int.class),
            @Arg(column = "forecastUnits60", javaType = int.class),
            @Arg(column = "forecastUnits90", javaType = int.class),
            @Arg(column = "lifecycleCode", javaType = String.class),
            @Arg(column = "lifecycleLabel", javaType = String.class),
            @Arg(column = "calculationVersion", javaType = String.class),
            @Arg(column = "configVersion", javaType = String.class),
            @Arg(column = "baseDailySales", javaType = BigDecimal.class),
            @Arg(column = "recentDailyTrendRate", javaType = BigDecimal.class),
            @Arg(column = "trendFactor", javaType = BigDecimal.class),
            @Arg(column = "lifecycleFactor", javaType = BigDecimal.class),
            @Arg(column = "futureFactor", javaType = BigDecimal.class),
            @Arg(column = "lifecycleExplanation", javaType = String.class),
            @Arg(column = "confidenceLevel", javaType = String.class),
            @Arg(column = "confidenceLabel", javaType = String.class),
            @Arg(column = "confidenceExplanation", javaType = String.class),
            @Arg(column = "warningCodes", javaType = String.class),
            @Arg(column = "riskCodes", javaType = String.class),
            @Arg(column = "activityWindowSummary", javaType = String.class),
            @Arg(column = "activityExplanation", javaType = String.class),
            @Arg(column = "shortReason", javaType = String.class),
            @Arg(column = "featureSnapshotJson", javaType = String.class)
    })
    @Select({
            "SELECT",
            "  id,",
            "  run_id AS runId,",
            "  owner_user_id AS ownerUserId,",
            "  store_code AS storeCode,",
            "  site_code AS siteCode,",
            "  partner_sku AS partnerSku,",
            "  sku,",
            "  product_title AS productTitle,",
            "  latest_fact_date AS latestFactDate,",
            "  history_units_7 AS historyUnits7,",
            "  history_units_30 AS historyUnits30,",
            "  history_units_60 AS historyUnits60,",
            "  history_units_90 AS historyUnits90,",
            "  observed_days AS observedDays,",
            "  current_stock AS currentStock,",
            "  stock_cover_days AS stockCoverDays,",
            "  forecast_units_30 AS forecastUnits30,",
            "  forecast_units_60 AS forecastUnits60,",
            "  forecast_units_90 AS forecastUnits90,",
            "  lifecycle_code AS lifecycleCode,",
            "  lifecycle_label AS lifecycleLabel,",
            "  calculation_version AS calculationVersion,",
            "  config_version AS configVersion,",
            "  base_daily_sales AS baseDailySales,",
            "  recent_daily_trend_rate AS recentDailyTrendRate,",
            "  trend_factor AS trendFactor,",
            "  lifecycle_factor AS lifecycleFactor,",
            "  future_factor AS futureFactor,",
            "  lifecycle_explanation AS lifecycleExplanation,",
            "  confidence_level AS confidenceLevel,",
            "  confidence_label AS confidenceLabel,",
            "  confidence_explanation AS confidenceExplanation,",
            "  warning_codes AS warningCodes,",
            "  risk_codes AS riskCodes,",
            "  activity_window_summary AS activityWindowSummary,",
            "  activity_explanation AS activityExplanation,",
            "  short_reason AS shortReason,",
            "  feature_snapshot_json AS featureSnapshotJson",
            "FROM sales_forecast_result",
            "WHERE run_id = #{runId}",
            "ORDER BY forecast_units_30 DESC, partner_sku ASC, sku ASC"
    })
    List<SalesForecastResultRecord> selectSalesForecastResults(@Param("runId") Long runId);

    @Insert({
            "INSERT INTO sales_forecast_follow_up (",
            "  id, owner_user_id, store_code, site_code, partner_sku, sku, marked, marked_by, marked_at,",
            "  gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{command.ownerUserId}, #{command.storeCode}, #{command.siteCode},",
            "  #{command.partnerSku}, #{command.sku}, #{command.marked}, #{command.operatorUserId}, NOW(),",
            "  NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  sku = VALUES(sku),",
            "  marked = VALUES(marked),",
            "  marked_by = VALUES(marked_by),",
            "  marked_at = VALUES(marked_at),",
            "  gmt_updated = NOW()"
    })
    int upsertSalesForecastFollowUp(
            @Param("id") Long id,
            @Param("command") SalesForecastFollowUpCommand command
    );

    @ConstructorArgs({
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class),
            @Arg(column = "partnerSku", javaType = String.class),
            @Arg(column = "sku", javaType = String.class),
            @Arg(column = "marked", javaType = boolean.class),
            @Arg(column = "markedBy", javaType = Long.class)
    })
    @Select({
            "SELECT",
            "  owner_user_id AS ownerUserId,",
            "  store_code AS storeCode,",
            "  site_code AS siteCode,",
            "  partner_sku AS partnerSku,",
            "  sku,",
            "  marked,",
            "  marked_by AS markedBy",
            "FROM sales_forecast_follow_up",
            "WHERE owner_user_id = #{query.ownerUserId}",
            "  AND store_code = #{query.storeCode}",
            "  AND site_code = #{query.siteCode}",
            "  AND marked = 1",
            "ORDER BY gmt_updated DESC, id DESC"
    })
    List<SalesForecastFollowUpRecord> selectSalesForecastFollowUps(
            @Param("query") com.nuono.next.salesforecast.SalesForecastQuery query
    );

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "sourceSystem", javaType = String.class),
            @Arg(column = "sourceFilename", javaType = String.class),
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "logicalStoreId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class),
            @Arg(column = "reportDateFrom", javaType = LocalDate.class),
            @Arg(column = "reportDateTo", javaType = LocalDate.class),
            @Arg(column = "totalRows", javaType = int.class),
            @Arg(column = "successRows", javaType = int.class),
            @Arg(column = "failureRows", javaType = int.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "failureSummary", javaType = String.class),
            @Arg(column = "importedAt", javaType = LocalDateTime.class)
    })
    @Select({
            "<script>",
            "SELECT",
            "  id,",
            "  source_system AS sourceSystem,",
            "  source_filename AS sourceFilename,",
            "  owner_user_id AS ownerUserId,",
            "  logical_store_id AS logicalStoreId,",
            "  store_code AS storeCode,",
            "  site_code AS siteCode,",
            "  report_date_from AS reportDateFrom,",
            "  report_date_to AS reportDateTo,",
            "  total_rows AS totalRows,",
            "  success_rows AS successRows,",
            "  failure_rows AS failureRows,",
            "  status,",
            "  failure_summary_json AS failureSummary,",
            "  imported_at AS importedAt",
            "FROM sales_import_batch",
            "WHERE owner_user_id = #{query.ownerUserId}",
            "  AND store_code = #{query.storeCode}",
            "  AND site_code = #{query.siteCode}",
            "<if test='query.dateFrom != null'>",
            "  AND (report_date_to IS NULL OR report_date_to &gt;= #{query.dateFrom})",
            "</if>",
            "<if test='query.dateTo != null'>",
            "  AND (report_date_from IS NULL OR report_date_from &lt;= #{query.dateTo})",
            "</if>",
            "ORDER BY imported_at DESC, id DESC",
            "LIMIT 200",
            "</script>"
    })
    List<SalesImportBatchRecord> selectSalesImportBatches(@Param("query") SalesImportBatchQuery query);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "sourceSystem", javaType = String.class),
            @Arg(column = "sourceFilename", javaType = String.class),
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "logicalStoreId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class),
            @Arg(column = "reportDateFrom", javaType = LocalDate.class),
            @Arg(column = "reportDateTo", javaType = LocalDate.class),
            @Arg(column = "totalRows", javaType = int.class),
            @Arg(column = "successRows", javaType = int.class),
            @Arg(column = "failureRows", javaType = int.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "failureSummary", javaType = String.class),
            @Arg(column = "importedAt", javaType = LocalDateTime.class)
    })
    @Select({
            "SELECT",
            "  id,",
            "  source_system AS sourceSystem,",
            "  source_filename AS sourceFilename,",
            "  owner_user_id AS ownerUserId,",
            "  logical_store_id AS logicalStoreId,",
            "  store_code AS storeCode,",
            "  site_code AS siteCode,",
            "  report_date_from AS reportDateFrom,",
            "  report_date_to AS reportDateTo,",
            "  total_rows AS totalRows,",
            "  success_rows AS successRows,",
            "  failure_rows AS failureRows,",
            "  status,",
            "  failure_summary_json AS failureSummary,",
            "  imported_at AS importedAt",
            "FROM sales_import_batch",
            "WHERE id = #{batchId}"
    })
    SalesImportBatchRecord selectSalesImportBatchById(@Param("batchId") Long batchId);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "sourceBatchId", javaType = Long.class),
            @Arg(column = "sourceFilename", javaType = String.class),
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class),
            @Arg(column = "rowNumber", javaType = int.class),
            @Arg(column = "exceptionType", javaType = String.class),
            @Arg(column = "fieldName", javaType = String.class),
            @Arg(column = "sourceValue", javaType = String.class),
            @Arg(column = "sourceContext", javaType = String.class),
            @Arg(column = "message", javaType = String.class),
            @Arg(column = "resolutionHint", javaType = String.class)
    })
    @Select({
            "SELECT",
            "  id,",
            "  source_batch_id AS sourceBatchId,",
            "  source_filename AS sourceFilename,",
            "  owner_user_id AS ownerUserId,",
            "  store_code AS storeCode,",
            "  site_code AS siteCode,",
            "  `row_number` AS `rowNumber`,",
            "  exception_type AS exceptionType,",
            "  field_name AS fieldName,",
            "  source_value AS sourceValue,",
            "  source_context AS sourceContext,",
            "  message,",
            "  resolution_hint AS resolutionHint",
            "FROM sales_import_exception",
            "WHERE source_batch_id = #{batchId}",
            "ORDER BY `row_number` ASC, id ASC"
    })
    List<SalesImportExceptionRecord> selectSalesImportExceptions(@Param("batchId") Long batchId);

    @Insert({
            "INSERT INTO sales_sync_task (",
            "  id, owner_user_id, logical_store_id, store_code, site_code, date_from, date_to,",
            "  requested_by, trigger_type, status, queued_at, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{command.ownerUserId}, #{command.logicalStoreId}, #{command.storeCode},",
            "  #{command.siteCode}, #{command.dateFrom}, #{command.dateTo}, #{command.requestedBy},",
            "  #{command.triggerType}, 'queued', NOW(), NOW(), NOW()",
            ")"
    })
    int insertSalesSyncTask(@Param("id") Long id, @Param("command") SalesSyncTaskCommand command);

    @Update({
            "UPDATE sales_sync_task",
            "SET status = 'running',",
            "    started_at = COALESCE(started_at, NOW()),",
            "    failure_reason = NULL,",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}"
    })
    int updateSalesSyncTaskRunning(@Param("taskId") Long taskId);

    @Update({
            "UPDATE sales_sync_task",
            "SET status = #{result.taskStatus},",
            "    source_batch_id = #{result.sourceBatchId},",
            "    total_rows = #{result.totalRows},",
            "    success_rows = #{result.successRows},",
            "    failure_rows = #{result.failureRows},",
            "    latest_fact_date = #{result.reportDateTo},",
            "    failure_reason = #{result.taskFailureReason},",
            "    finished_at = NOW(),",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}"
    })
    int updateSalesSyncTaskSucceeded(
            @Param("taskId") Long taskId,
            @Param("result") NoonSalesCsvImportResult result
    );

    @Update({
            "UPDATE sales_sync_task",
            "SET status = 'failed',",
            "    failure_reason = #{failureReason},",
            "    finished_at = NOW(),",
            "    gmt_updated = NOW()",
            "WHERE id = #{taskId}"
    })
    int updateSalesSyncTaskFailed(@Param("taskId") Long taskId, @Param("failureReason") String failureReason);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "logicalStoreId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class),
            @Arg(column = "dateFrom", javaType = LocalDate.class),
            @Arg(column = "dateTo", javaType = LocalDate.class),
            @Arg(column = "requestedBy", javaType = Long.class),
            @Arg(column = "triggerType", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "sourceBatchId", javaType = Long.class),
            @Arg(column = "totalRows", javaType = Integer.class),
            @Arg(column = "successRows", javaType = Integer.class),
            @Arg(column = "failureRows", javaType = Integer.class),
            @Arg(column = "latestFactDate", javaType = LocalDate.class),
            @Arg(column = "failureReason", javaType = String.class)
    })
    @Select({
            "SELECT",
            "  id,",
            "  owner_user_id AS ownerUserId,",
            "  logical_store_id AS logicalStoreId,",
            "  store_code AS storeCode,",
            "  site_code AS siteCode,",
            "  date_from AS dateFrom,",
            "  date_to AS dateTo,",
            "  requested_by AS requestedBy,",
            "  trigger_type AS triggerType,",
            "  status,",
            "  source_batch_id AS sourceBatchId,",
            "  total_rows AS totalRows,",
            "  success_rows AS successRows,",
            "  failure_rows AS failureRows,",
            "  latest_fact_date AS latestFactDate,",
            "  failure_reason AS failureReason",
            "FROM sales_sync_task",
            "WHERE id = #{taskId}"
    })
    SalesSyncTaskRecord selectSalesSyncTaskById(@Param("taskId") Long taskId);

    @Insert({
            "INSERT INTO sales_activity_window (",
            "  id, owner_user_id, store_code, site_code, name, activity_type, category_scope,",
            "  date_from, date_to, factor, enabled, version_no, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, #{record.ownerUserId}, #{record.storeCode}, #{record.siteCode}, #{record.name},",
            "  #{record.activityType}, #{record.categoryScope}, #{record.dateFrom}, #{record.dateTo},",
            "  #{record.factor}, #{record.enabled}, #{record.versionNo}, #{record.createdBy}, #{record.updatedBy}, NOW(), NOW()",
            ")"
    })
    int insertSalesActivityWindow(@Param("id") Long id, @Param("record") SalesActivityWindowRecord record);

    @Update({
            "UPDATE sales_activity_window",
            "SET enabled = #{enabled}, updated_by = #{updatedBy}, gmt_updated = NOW()",
            "WHERE id = #{id}"
    })
    int updateSalesActivityWindowEnabled(
            @Param("id") Long id,
            @Param("enabled") boolean enabled,
            @Param("updatedBy") Long updatedBy
    );

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class),
            @Arg(column = "name", javaType = String.class),
            @Arg(column = "activityType", javaType = String.class),
            @Arg(column = "categoryScope", javaType = String.class),
            @Arg(column = "dateFrom", javaType = LocalDate.class),
            @Arg(column = "dateTo", javaType = LocalDate.class),
            @Arg(column = "factor", javaType = java.math.BigDecimal.class),
            @Arg(column = "enabled", javaType = boolean.class),
            @Arg(column = "versionNo", javaType = int.class),
            @Arg(column = "createdBy", javaType = Long.class),
            @Arg(column = "updatedBy", javaType = Long.class)
    })
    @Select({
            "SELECT",
            "  id, owner_user_id AS ownerUserId, store_code AS storeCode, site_code AS siteCode,",
            "  name, activity_type AS activityType, category_scope AS categoryScope,",
            "  date_from AS dateFrom, date_to AS dateTo, factor, enabled, version_no AS versionNo,",
            "  created_by AS createdBy, updated_by AS updatedBy",
            "FROM sales_activity_window",
            "WHERE id = #{id}"
    })
    SalesActivityWindowRecord selectSalesActivityWindowById(@Param("id") Long id);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class),
            @Arg(column = "name", javaType = String.class),
            @Arg(column = "activityType", javaType = String.class),
            @Arg(column = "categoryScope", javaType = String.class),
            @Arg(column = "dateFrom", javaType = LocalDate.class),
            @Arg(column = "dateTo", javaType = LocalDate.class),
            @Arg(column = "factor", javaType = java.math.BigDecimal.class),
            @Arg(column = "enabled", javaType = boolean.class),
            @Arg(column = "versionNo", javaType = int.class),
            @Arg(column = "createdBy", javaType = Long.class),
            @Arg(column = "updatedBy", javaType = Long.class)
    })
    @Select({
            "SELECT",
            "  id, owner_user_id AS ownerUserId, store_code AS storeCode, site_code AS siteCode,",
            "  name, activity_type AS activityType, category_scope AS categoryScope,",
            "  date_from AS dateFrom, date_to AS dateTo, factor, enabled, version_no AS versionNo,",
            "  created_by AS createdBy, updated_by AS updatedBy",
            "FROM sales_activity_window",
            "WHERE owner_user_id = #{scope.ownerUserId}",
            "  AND store_code = #{scope.storeCode}",
            "  AND site_code = #{scope.siteCode}",
            "ORDER BY version_no DESC, id DESC"
    })
    List<SalesActivityWindowRecord> selectSalesActivityWindowHistory(@Param("scope") SalesActivityWindowScope scope);

    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class),
            @Arg(column = "name", javaType = String.class),
            @Arg(column = "activityType", javaType = String.class),
            @Arg(column = "categoryScope", javaType = String.class),
            @Arg(column = "dateFrom", javaType = LocalDate.class),
            @Arg(column = "dateTo", javaType = LocalDate.class),
            @Arg(column = "factor", javaType = java.math.BigDecimal.class),
            @Arg(column = "enabled", javaType = boolean.class),
            @Arg(column = "versionNo", javaType = int.class),
            @Arg(column = "createdBy", javaType = Long.class),
            @Arg(column = "updatedBy", javaType = Long.class)
    })
    @Select({
            "SELECT",
            "  id, owner_user_id AS ownerUserId, store_code AS storeCode, site_code AS siteCode,",
            "  name, activity_type AS activityType, category_scope AS categoryScope,",
            "  date_from AS dateFrom, date_to AS dateTo, factor, enabled, version_no AS versionNo,",
            "  created_by AS createdBy, updated_by AS updatedBy",
            "FROM sales_activity_window",
            "WHERE owner_user_id = #{scope.ownerUserId}",
            "  AND store_code = #{scope.storeCode}",
            "  AND site_code = #{scope.siteCode}",
            "  AND enabled = 1",
            "  AND date_to >= #{scope.dateFrom}",
            "  AND date_from <= #{scope.dateTo}",
            "ORDER BY date_from ASC, id ASC"
    })
    List<SalesActivityWindowRecord> selectActiveSalesActivityWindows(@Param("scope") SalesActivityWindowScope scope);

    @ConstructorArgs({
            @Arg(column = "partnerSku", javaType = String.class),
            @Arg(column = "sku", javaType = String.class),
            @Arg(column = "brand", javaType = String.class),
            @Arg(column = "productFulltype", javaType = String.class),
            @Arg(column = "imageUrl", javaType = String.class),
            @Arg(column = "currentStock", javaType = Integer.class),
            @Arg(column = "fbnStock", javaType = Integer.class),
            @Arg(column = "supermallStock", javaType = Integer.class),
            @Arg(column = "fbpStock", javaType = Integer.class)
    })
    @Select({
            "<script>",
            "SELECT",
            "  pv.partner_sku AS partnerSku,",
            "  COALESCE(NULLIF(pv.child_sku, ''), NULLIF(pso.offer_code, ''), NULLIF(pso.psku_code, ''), '') AS sku,",
            "  pm.brand_cache AS brand,",
            "  pm.product_fulltype_cache AS productFulltype,",
            "  pm.cover_image_url AS imageUrl,",
            "  CASE",
            "    WHEN pso.fbn_stock IS NULL AND pso.supermall_stock IS NULL AND pso.fbp_stock IS NULL THEN NULL",
            "    ELSE COALESCE(pso.fbn_stock, 0) + COALESCE(pso.supermall_stock, 0) + COALESCE(pso.fbp_stock, 0)",
            "  END AS currentStock,",
            "  pso.fbn_stock AS fbnStock,",
            "  pso.supermall_stock AS supermallStock,",
            "  pso.fbp_stock AS fbpStock",
            "FROM logical_store_site lss",
            "JOIN logical_store ls ON ls.id = lss.logical_store_id AND ls.is_deleted = b'0'",
            "JOIN product_master pm ON pm.logical_store_id = ls.id",
            "  AND pm.is_deleted = b'0'",
            "JOIN product_variant pv ON pv.product_master_id = pm.id AND pv.is_deleted = b'0'",
            "LEFT JOIN product_site_offer pso ON pso.site_id = lss.id",
            "  AND pso.variant_id = pv.id",
            "  AND pso.is_deleted = b'0'",
            "WHERE ls.owner_user_id = #{query.ownerUserId}",
            "  AND lss.store_code = #{query.storeCode}",
            "  AND lss.site = #{query.siteCode}",
            "  AND lss.is_deleted = b'0'",
            "<if test='query.partnerSku != null and query.partnerSku != \"\"'>",
            "  AND pv.partner_sku = #{query.partnerSku}",
            "</if>",
            "<if test='query.sku != null and query.sku != \"\"'>",
            "  AND COALESCE(NULLIF(pv.child_sku, ''), NULLIF(pso.offer_code, ''), NULLIF(pso.psku_code, ''), '') = #{query.sku}",
            "</if>",
            "ORDER BY pv.partner_sku ASC, sku ASC",
            "</script>"
    })
    List<SalesProductDimensionSnapshot> selectSalesProductDimensions(@Param("query") SalesFactQuery query);
}
