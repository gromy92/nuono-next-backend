package com.nuono.next.infrastructure.mapper;

/** MySQL 8 set-based SQL for one bounded DP-07-B accepted-row slice. */
public final class FbnReportApplySql {
    private static final String CTE = lines(
            "WITH accepted AS (",
            " SELECT staged.row_number, staged.identity_sha256, staged.payload_json, fact.*",
            " FROM dp_pull_report_stage_row staged",
            " JOIN JSON_TABLE(staged.payload_json, '$' COLUMNS(",
            "  businessKey VARCHAR(500) PATH '$.businessKey', partnerSku VARCHAR(100) PATH '$.partnerSku',",
            "  noonSku VARCHAR(100) PATH '$.noonSku', pbarcodeCanonical VARCHAR(120) PATH '$.pbarcodeCanonical',",
            "  noonAsnNr VARCHAR(120) PATH '$.noonAsnNr', partnerWarehouse VARCHAR(100) PATH '$.partnerWarehouse',",
            "  noonWarehouse VARCHAR(100) PATH '$.noonWarehouse', countryCode VARCHAR(20) PATH '$.countryCode',",
            "  qtyExpected INT PATH '$.qtyExpected', receivedQty INT PATH '$.receivedQty',",
            "  qcFailedQty INT PATH '$.qcFailedQty', unidentifiedQty INT PATH '$.unidentifiedQty',",
            "  qcFailedReason VARCHAR(1000) PATH '$.qcFailedReason',",
            "  asnCreatedAt DATETIME PATH '$.asnCreatedAt', asnScheduleDate DATE PATH '$.asnScheduleDate',",
            "  asnCompletedAt DATETIME PATH '$.asnCompletedAt')) fact",
            " WHERE staged.task_id=#{taskId} AND staged.decision='ACCEPTED'",
            "   AND staged.row_number>#{afterRowNumber} AND staged.row_number<=#{throughRowNumber}",
            "), scope AS (",
            " SELECT task.owner_user_id, task.logical_store_id, task.store_code, task.site_code,",
            "        task.project_code",
            " FROM dp_pull_task task",
            " WHERE task.id=#{taskId}",
            "), source_rows AS (",
            " SELECT fact.*, scope.owner_user_id, scope.logical_store_id, scope.store_code, scope.site_code,",
            "  scope.project_code,",
            "  CASE WHEN fact.qcFailedQty>0 THEN 'QC_FAILED' WHEN fact.unidentifiedQty>0 THEN 'UNIDENTIFIED'",
            "       WHEN fact.receivedQty<fact.qtyExpected THEN 'SHORT_RECEIVED'",
            "       WHEN fact.receivedQty>fact.qtyExpected THEN 'OVER_RECEIVED' ELSE 'NORMAL' END AS receipt_status",
            " FROM accepted fact CROSS JOIN scope",
            ")"
    );

    private FbnReportApplySql() { }

    public static String insertReportRows() {
        return lines(
                "INSERT INTO official_warehouse_report_row (id,import_id,report_type,row_no,business_key,",
                " business_key_hash,row_status,warning_code,error_message,raw_row_json,normalized_row_json,",
                " is_deleted,created_by,updated_by,gmt_create,gmt_updated)"
        ) + "\n" + CTE + lines(
                " SELECT #{firstRowId}+ROW_NUMBER() OVER (ORDER BY row_number)-1,#{importId},",
                " 'FBN_INBOUND_FBNRECEIVEDREPORT',row_number,businessKey,identity_sha256,",
                " IF(receipt_status='NORMAL','VALID','WARNING'),",
                " IF(receipt_status='NORMAL',NULL,receipt_status),NULL,",
                " JSON_EXTRACT(payload_json,'$.rawFields'),",
                " JSON_OBJECT('partnerSku',partnerSku,'noonSku',noonSku,'noonAsnNr',noonAsnNr,",
                "  'pbarcodeCanonical',pbarcodeCanonical,'partnerWarehouse',partnerWarehouse,",
                "  'noonWarehouse',noonWarehouse,'countryCode',countryCode,'qtyExpected',qtyExpected,",
                "  'receivedQty',receivedQty,'qcFailedQty',qcFailedQty,'unidentifiedQty',unidentifiedQty,",
                "  'receiptStatus',receipt_status,'matchStatus','SOURCE_ONLY'),",
                " b'0',owner_user_id,owner_user_id,#{nowUtc},#{nowUtc} FROM source_rows"
        );
    }

    public static String insertReceiptLines() {
        return lines(
                "INSERT INTO official_warehouse_inbound_receipt_line (id,import_id,report_row_id,owner_user_id,",
                " logical_store_id,store_code,site_code,project_code,partner_id,asn_id,asn_line_id,noon_asn_nr,",
                " product_master_id,product_variant_id,product_site_offer_id,partner_sku,psku_code,noon_sku,",
                " pbarcode_canonical,partner_warehouse,noon_warehouse,country_code,qty_expected,received_qty,",
                " qc_failed_qty,unidentified_qty,qc_failed_reason,receipt_status,match_status,anomaly_flags_json,",
                " asn_created_at,asn_schedule_date,asn_completed_at,raw_payload_json,is_deleted,created_by,updated_by,",
                " gmt_create,gmt_updated)"
        ) + "\n" + CTE + lines(
                " SELECT #{firstReceiptId}+ROW_NUMBER() OVER (ORDER BY row_number)-1,#{importId},",
                " #{firstRowId}+ROW_NUMBER() OVER (ORDER BY row_number)-1,owner_user_id,logical_store_id,",
                " store_code,site_code,project_code,NULL,NULL,NULL,noonAsnNr,NULL,",
                " NULL,NULL,partnerSku,NULL,noonSku,",
                " pbarcodeCanonical,partnerWarehouse,noonWarehouse,countryCode,qtyExpected,receivedQty,qcFailedQty,",
                " unidentifiedQty,qcFailedReason,receipt_status,'SOURCE_ONLY',",
                " IF(receipt_status='NORMAL',JSON_ARRAY(),JSON_ARRAY(receipt_status)),",
                " asnCreatedAt,asnScheduleDate,asnCompletedAt,JSON_EXTRACT(payload_json,'$.rawFields'),",
                " b'0',owner_user_id,owner_user_id,#{nowUtc},#{nowUtc} FROM source_rows"
        );
    }

    private static String lines(String... lines) {
        return String.join("\n", lines);
    }
}
