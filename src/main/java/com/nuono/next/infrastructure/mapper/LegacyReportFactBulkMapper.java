package com.nuono.next.infrastructure.mapper;

import com.nuono.next.datapull.report.ReportFactIdBlock;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;

/** DP-01/02/03 bounded set-based Fact Writer mapper. */
public interface LegacyReportFactBulkMapper {
    @Insert({
            "INSERT INTO sales_data_id_sequence (sequence_name,next_id,gmt_create,gmt_updated)",
            "VALUES (#{sequenceName},LAST_INSERT_ID(#{initialValue}+#{blockSize}),NOW(),NOW())",
            "ON DUPLICATE KEY UPDATE next_id=LAST_INSERT_ID(next_id+#{blockSize}),gmt_updated=NOW()"
    })
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "lastId",
            before = false, resultType = Long.class)
    void reserveSalesFactIds(ReportFactIdBlock block);

    @Insert({
            "INSERT INTO noon_order_id_sequence (sequence_name,next_id,gmt_create,gmt_updated)",
            "VALUES (#{sequenceName},LAST_INSERT_ID(#{initialValue}+#{blockSize}),NOW(),NOW())",
            "ON DUPLICATE KEY UPDATE next_id=LAST_INSERT_ID(next_id+#{blockSize}),gmt_updated=NOW()"
    })
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "lastId",
            before = false, resultType = Long.class)
    void reserveOrderFactIds(ReportFactIdBlock block);

    @Insert({
            "INSERT INTO noon_finance_transaction_id_sequence (sequence_name,next_id,gmt_create,gmt_updated)",
            "VALUES (#{sequenceName},LAST_INSERT_ID(#{initialValue}+#{blockSize}),NOW(),NOW())",
            "ON DUPLICATE KEY UPDATE next_id=LAST_INSERT_ID(next_id+#{blockSize}),gmt_updated=NOW()"
    })
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "lastId",
            before = false, resultType = Long.class)
    void reserveFinanceFactIds(ReportFactIdBlock block);

    @Insert({
            "INSERT INTO daily_sales_fact (id,source_system,source_batch_id,owner_user_id,logical_store_id,",
            "store_code,site_code,fact_date,partner_sku,sku,currency_code,shipped_units,net_units,",
            "revenue_shipped,gmt_create,gmt_updated)",
            "SELECT #{firstId}+ROW_NUMBER() OVER (ORDER BY staged.`row_number`)-1,",
            "'noon_productviewsandsalesdata',NULL,fact.ownerUserId,NULL,fact.storeCode,fact.siteCode,",
            "fact.salesDate,fact.skuParent,fact.sku,fact.currency,fact.unitsSold,fact.unitsSold,",
            "fact.salesAmount,#{nowUtc},#{nowUtc}",
            "FROM dp_pull_report_stage_row staged",
            "JOIN JSON_TABLE(staged.payload_json,'$' COLUMNS(",
            " ownerUserId BIGINT PATH '$.ownerUserId',storeCode VARCHAR(100) PATH '$.storeCode',",
            " siteCode VARCHAR(20) PATH '$.siteCode',salesDate DATE PATH '$.salesDate',",
            " skuParent VARCHAR(160) PATH '$.skuParent',sku VARCHAR(160) PATH '$.sku',",
            " unitsSold BIGINT PATH '$.unitsSold',salesAmount DECIMAL(18,6) PATH '$.salesAmount',",
            " currency VARCHAR(20) PATH '$.currency')) fact",
            "WHERE staged.task_id=#{taskId} AND staged.decision='ACCEPTED'",
            " AND staged.`row_number`>#{afterRowNumber} AND staged.`row_number`<=#{throughRowNumber}",
            "ON DUPLICATE KEY UPDATE currency_code=VALUES(currency_code),shipped_units=VALUES(shipped_units),",
            "net_units=VALUES(net_units),revenue_shipped=VALUES(revenue_shipped),gmt_updated=VALUES(gmt_updated)"
    })
    @Options(timeout = 10)
    int applySalesFacts(
            @Param("taskId") long taskId,
            @Param("afterRowNumber") long afterRowNumber,
            @Param("throughRowNumber") long throughRowNumber,
            @Param("firstId") long firstId,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    @Select({
            "SELECT COUNT(*) FROM dp_pull_report_stage_row staged",
            "JOIN JSON_TABLE(staged.payload_json,'$' COLUMNS(",
            " ownerUserId BIGINT PATH '$.ownerUserId',storeCode VARCHAR(100) PATH '$.storeCode',",
            " siteCode VARCHAR(20) PATH '$.siteCode',salesDate DATE PATH '$.salesDate',",
            " skuParent VARCHAR(160) PATH '$.skuParent',sku VARCHAR(160) PATH '$.sku')) fact",
            "JOIN daily_sales_fact target ON target.source_system='noon_productviewsandsalesdata'",
            " AND target.owner_user_id=fact.ownerUserId AND BINARY target.store_code=BINARY fact.storeCode",
            " AND BINARY target.site_code=BINARY fact.siteCode AND target.fact_date=fact.salesDate",
            " AND BINARY target.partner_sku=BINARY fact.skuParent AND BINARY target.sku=BINARY fact.sku",
            "WHERE staged.task_id=#{taskId} AND staged.decision='ACCEPTED'",
            " AND staged.`row_number`>#{afterRowNumber} AND staged.`row_number`<=#{throughRowNumber}"
    })
    long countAppliedSalesFacts(
            @Param("taskId") long taskId,
            @Param("afterRowNumber") long afterRowNumber,
            @Param("throughRowNumber") long throughRowNumber
    );

    @Insert({
            "INSERT INTO noon_order_line_fact (id,source_system,source_batch_id,owner_user_id,store_code,site_code,",
            "id_partner,src_country,country_code,dest_country,bayan_nr,item_nr,order_identity,partner_sku,sku,status,",
            "offer_price,gmv_lcy,currency_code,brand_code,family,fulfillment_model,order_timestamp,shipment_timestamp,",
            "delivered_timestamp,report_date_from,report_date_to,gmt_create,gmt_updated)",
            "SELECT #{firstId}+ROW_NUMBER() OVER (ORDER BY staged.`row_number`)-1,'noon_order_report',",
            "fact.sourceBatchId,fact.ownerUserId,fact.storeCode,fact.siteCode,fact.idPartner,fact.sourceCountry,",
            "fact.countryCode,fact.destinationCountry,fact.bayanNr,fact.orderLineIdentity,fact.orderIdentity,",
            "fact.partnerSku,fact.sku,fact.status,fact.offerPrice,fact.gmvLcy,fact.currencyCode,fact.brandCode,",
            "fact.family,fact.fulfillmentModel,fact.orderTimestamp,fact.shipmentTimestamp,fact.deliveredTimestamp,",
            "fact.reportDateFrom,fact.reportDateTo,#{nowUtc},#{nowUtc}",
            "FROM dp_pull_report_stage_row staged",
            "JOIN JSON_TABLE(staged.payload_json,'$' COLUMNS(",
            " ownerUserId BIGINT PATH '$.ownerUserId',storeCode VARCHAR(100) PATH '$.storeCode',",
            " siteCode VARCHAR(20) PATH '$.siteCode',idPartner VARCHAR(80) PATH '$.idPartner',",
            " sourceCountry VARCHAR(20) PATH '$.sourceCountry',countryCode VARCHAR(20) PATH '$.countryCode',",
            " destinationCountry VARCHAR(20) PATH '$.destinationCountry',bayanNr VARCHAR(120) PATH '$.bayanNr',",
            " orderLineIdentity VARCHAR(160) PATH '$.orderLineIdentity',orderIdentity VARCHAR(160) PATH '$.orderIdentity',",
            " partnerSku VARCHAR(160) PATH '$.partnerSku',sku VARCHAR(160) PATH '$.sku',",
            " status VARCHAR(80) PATH '$.status',offerPrice DECIMAL(18,6) PATH '$.offerPrice',",
            " gmvLcy DECIMAL(18,6) PATH '$.gmvLcy',currencyCode VARCHAR(20) PATH '$.currencyCode',",
            " brandCode VARCHAR(160) PATH '$.brandCode',family VARCHAR(255) PATH '$.family',",
            " fulfillmentModel VARCHAR(160) PATH '$.fulfillmentModel',",
            " orderTimestamp DATETIME PATH '$.orderTimestamp',shipmentTimestamp DATETIME PATH '$.shipmentTimestamp',",
            " deliveredTimestamp DATETIME PATH '$.deliveredTimestamp',reportDateFrom DATE PATH '$.reportDateFrom',",
            " reportDateTo DATE PATH '$.reportDateTo',sourceBatchId VARCHAR(160) PATH '$.sourceBatchId')) fact",
            "WHERE staged.task_id=#{taskId} AND staged.decision='ACCEPTED'",
            " AND staged.`row_number`>#{afterRowNumber} AND staged.`row_number`<=#{throughRowNumber}",
            "ON DUPLICATE KEY UPDATE source_batch_id=VALUES(source_batch_id),owner_user_id=VALUES(owner_user_id),",
            "store_code=VALUES(store_code),site_code=VALUES(site_code),src_country=VALUES(src_country),",
            "dest_country=VALUES(dest_country),bayan_nr=VALUES(bayan_nr),order_identity=VALUES(order_identity),",
            "partner_sku=VALUES(partner_sku),sku=VALUES(sku),status=VALUES(status),offer_price=VALUES(offer_price),",
            "gmv_lcy=VALUES(gmv_lcy),currency_code=VALUES(currency_code),brand_code=VALUES(brand_code),",
            "family=VALUES(family),fulfillment_model=VALUES(fulfillment_model),",
            "order_timestamp=VALUES(order_timestamp),shipment_timestamp=VALUES(shipment_timestamp),",
            "delivered_timestamp=VALUES(delivered_timestamp),report_date_from=VALUES(report_date_from),",
            "report_date_to=VALUES(report_date_to),gmt_updated=VALUES(gmt_updated)"
    })
    @Options(timeout = 10)
    int applyOrderFacts(
            @Param("taskId") long taskId,
            @Param("afterRowNumber") long afterRowNumber,
            @Param("throughRowNumber") long throughRowNumber,
            @Param("firstId") long firstId,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    @Select({
            "SELECT COUNT(*) FROM dp_pull_report_stage_row staged",
            "JOIN JSON_TABLE(staged.payload_json,'$' COLUMNS(idPartner VARCHAR(80) PATH '$.idPartner',",
            "countryCode VARCHAR(20) PATH '$.countryCode',orderLineIdentity VARCHAR(160) PATH '$.orderLineIdentity')) fact",
            "JOIN noon_order_line_fact target ON target.source_system='noon_order_report'",
            " AND BINARY target.id_partner=BINARY fact.idPartner AND BINARY target.country_code=BINARY fact.countryCode",
            " AND BINARY target.item_nr=BINARY fact.orderLineIdentity",
            "WHERE staged.task_id=#{taskId} AND staged.decision='ACCEPTED'",
            " AND staged.`row_number`>#{afterRowNumber} AND staged.`row_number`<=#{throughRowNumber}"
    })
    long countAppliedOrderFacts(
            @Param("taskId") long taskId,
            @Param("afterRowNumber") long afterRowNumber,
            @Param("throughRowNumber") long throughRowNumber
    );

    @Insert({
            "INSERT INTO noon_finance_transaction_fact (id,source_system,source_batch_id,file_digest_sha256,row_hash,",
            "owner_user_id,store_code,site_code,contract_code,contract_title,reference_nr,order_nr,item_nr,order_date,",
            "transaction_date,title,sku,partner_sku,transaction_type,currency,net_proceeds,referral_fee_including_vat,",
            "fulfillment_logistics_fees_including_vat,shipping_credits_including_vat,other_order_fees_including_vat,",
            "order_subsidies_including_vat,non_order_fees_including_vat,non_order_subsidies_including_vat,",
            "others_including_vat,total_amount,report_date_from,report_date_to,gmt_create,gmt_updated)",
            "SELECT #{firstId}+ROW_NUMBER() OVER (ORDER BY staged.`row_number`)-1,",
            "'noon_finance_transaction_report',fact.sourceBatchId,fact.fileDigestSha256,fact.rowHash,fact.ownerUserId,",
            "fact.storeCode,fact.siteCode,fact.contractCode,fact.contractTitle,fact.referenceNr,fact.orderNr,fact.itemNr,",
            "fact.orderDate,fact.transactionDate,fact.title,fact.sku,fact.partnerSku,fact.transactionType,fact.currency,",
            "fact.netProceeds,fact.referralFeeIncludingVat,fact.fulfillmentLogisticsFeesIncludingVat,",
            "fact.shippingCreditsIncludingVat,fact.otherOrderFeesIncludingVat,fact.orderSubsidiesIncludingVat,",
            "fact.nonOrderFeesIncludingVat,fact.nonOrderSubsidiesIncludingVat,fact.othersIncludingVat,fact.totalAmount,",
            "fact.reportDateFrom,fact.reportDateTo,#{nowUtc},#{nowUtc}",
            "FROM dp_pull_report_stage_row staged JOIN JSON_TABLE(staged.payload_json,'$' COLUMNS(",
            " sourceBatchId VARCHAR(160) PATH '$.sourceBatchId',fileDigestSha256 VARCHAR(128) PATH '$.fileDigestSha256',",
            " rowHash VARCHAR(128) PATH '$.rowHash',ownerUserId BIGINT PATH '$.ownerUserId',",
            " storeCode VARCHAR(100) PATH '$.storeCode',siteCode VARCHAR(20) PATH '$.siteCode',",
            " contractCode VARCHAR(80) PATH '$.contractCode',contractTitle VARCHAR(160) PATH '$.contractTitle',",
            " referenceNr VARCHAR(160) PATH '$.referenceNr',orderNr VARCHAR(160) PATH '$.orderNr',",
            " itemNr VARCHAR(160) PATH '$.itemNr',orderDate DATE PATH '$.orderDate',transactionDate DATE PATH '$.transactionDate',",
            " title VARCHAR(1024) PATH '$.title',sku VARCHAR(160) PATH '$.sku',partnerSku VARCHAR(160) PATH '$.partnerSku',",
            " transactionType VARCHAR(80) PATH '$.transactionType',currency VARCHAR(20) PATH '$.currency',",
            " netProceeds DECIMAL(18,6) PATH '$.netProceeds',referralFeeIncludingVat DECIMAL(18,6) PATH '$.referralFeeIncludingVat',",
            " fulfillmentLogisticsFeesIncludingVat DECIMAL(18,6) PATH '$.fulfillmentLogisticsFeesIncludingVat',",
            " shippingCreditsIncludingVat DECIMAL(18,6) PATH '$.shippingCreditsIncludingVat',",
            " otherOrderFeesIncludingVat DECIMAL(18,6) PATH '$.otherOrderFeesIncludingVat',",
            " orderSubsidiesIncludingVat DECIMAL(18,6) PATH '$.orderSubsidiesIncludingVat',",
            " nonOrderFeesIncludingVat DECIMAL(18,6) PATH '$.nonOrderFeesIncludingVat',",
            " nonOrderSubsidiesIncludingVat DECIMAL(18,6) PATH '$.nonOrderSubsidiesIncludingVat',",
            " othersIncludingVat DECIMAL(18,6) PATH '$.othersIncludingVat',totalAmount DECIMAL(18,6) PATH '$.totalAmount',",
            " reportDateFrom DATE PATH '$.reportDateFrom',reportDateTo DATE PATH '$.reportDateTo')) fact",
            "WHERE staged.task_id=#{taskId} AND staged.decision='ACCEPTED'",
            " AND staged.`row_number`>#{afterRowNumber} AND staged.`row_number`<=#{throughRowNumber}",
            "ON DUPLICATE KEY UPDATE source_batch_id=VALUES(source_batch_id),file_digest_sha256=VALUES(file_digest_sha256),",
            "row_hash=VALUES(row_hash),contract_code=VALUES(contract_code),contract_title=VALUES(contract_title),",
            "order_date=VALUES(order_date),title=VALUES(title),sku=VALUES(sku),partner_sku=VALUES(partner_sku),",
            "currency=VALUES(currency),net_proceeds=VALUES(net_proceeds),",
            "referral_fee_including_vat=VALUES(referral_fee_including_vat),",
            "fulfillment_logistics_fees_including_vat=VALUES(fulfillment_logistics_fees_including_vat),",
            "shipping_credits_including_vat=VALUES(shipping_credits_including_vat),",
            "other_order_fees_including_vat=VALUES(other_order_fees_including_vat),",
            "order_subsidies_including_vat=VALUES(order_subsidies_including_vat),",
            "non_order_fees_including_vat=VALUES(non_order_fees_including_vat),",
            "non_order_subsidies_including_vat=VALUES(non_order_subsidies_including_vat),",
            "others_including_vat=VALUES(others_including_vat),total_amount=VALUES(total_amount),",
            "report_date_from=VALUES(report_date_from),report_date_to=VALUES(report_date_to),",
            "gmt_updated=VALUES(gmt_updated)"
    })
    @Options(timeout = 10)
    int applyFinanceFacts(
            @Param("taskId") long taskId,
            @Param("afterRowNumber") long afterRowNumber,
            @Param("throughRowNumber") long throughRowNumber,
            @Param("firstId") long firstId,
            @Param("nowUtc") LocalDateTime nowUtc
    );

    @Select({
            "SELECT COUNT(*) FROM dp_pull_report_stage_row staged",
            "JOIN JSON_TABLE(staged.payload_json,'$' COLUMNS(ownerUserId BIGINT PATH '$.ownerUserId',",
            "storeCode VARCHAR(100) PATH '$.storeCode',siteCode VARCHAR(20) PATH '$.siteCode',",
            "rowHash VARCHAR(128) PATH '$.rowHash')) fact",
            "JOIN noon_finance_transaction_fact target ON target.source_system='noon_finance_transaction_report'",
            " AND target.owner_user_id=fact.ownerUserId AND BINARY target.store_code=BINARY fact.storeCode",
            " AND BINARY target.site_code=BINARY fact.siteCode AND BINARY target.row_hash=BINARY fact.rowHash",
            "WHERE staged.task_id=#{taskId} AND staged.decision='ACCEPTED'",
            " AND staged.`row_number`>#{afterRowNumber} AND staged.`row_number`<=#{throughRowNumber}"
    })
    long countAppliedFinanceFacts(
            @Param("taskId") long taskId,
            @Param("afterRowNumber") long afterRowNumber,
            @Param("throughRowNumber") long throughRowNumber
    );
}
