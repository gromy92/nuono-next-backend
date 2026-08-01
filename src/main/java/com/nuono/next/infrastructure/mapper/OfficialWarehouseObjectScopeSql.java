package com.nuono.next.infrastructure.mapper;

public final class OfficialWarehouseObjectScopeSql {

    private static final String ASN_SELECT = String.join("\n",
            "SELECT id, owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId, store_code AS storeCode,",
            "       store_name AS storeName, site_code AS siteCode, project_code AS projectCode, partner_id AS partnerId,",
            "       local_asn_no AS localAsnNo, source_type AS sourceType, status, noon_asn_nr AS noonAsnNr,",
            "       noon_partner_asn_id AS noonPartnerAsnId, noon_total_qty AS noonTotalQty,",
            "       noon_asn_status AS noonAsnStatus, noon_updated_at AS noonUpdatedAt,",
            "       routing_response_json AS routingResponseJson, routing_is_transfer AS routingIsTransfer,",
            "       selected_warehouse_partner_code AS selectedWarehousePartnerCode,",
            "       selected_warehouse_code AS selectedWarehouseCode, selected_warehouse_name AS selectedWarehouseName,",
            "       product_count AS productCount, total_quantity AS totalQuantity,",
            "       error_stage AS errorStage, failure_type AS failureType, error_message AS errorMessage,",
            "       DATE_FORMAT(submitted_at, '%Y-%m-%d %H:%i:%s') AS submittedAt,",
            "       DATE_FORMAT(finished_at, '%Y-%m-%d %H:%i:%s') AS finishedAt,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i:%s') AS createdAt,",
            "       DATE_FORMAT(COALESCE(noon_updated_at, gmt_updated), '%Y-%m-%d %H:%i:%s') AS updatedAt",
            "FROM official_warehouse_asn"
    );

    private static final String APPOINTMENT_SELECT = String.join("\n",
            "SELECT id, asn_id AS asnId, owner_user_id AS ownerUserId, logical_store_id AS logicalStoreId,",
            "       store_code AS storeCode, store_name AS storeName, site_code AS siteCode,",
            "       project_code AS projectCode, partner_id AS partnerId, local_asn_no AS localAsnNo,",
            "       noon_asn_nr AS noonAsnNr, total_units AS totalUnits,",
            "       warehouse_to_partner_code AS warehouseToPartnerCode, warehouse_to_code AS warehouseToCode,",
            "       ap_start_date AS apStartDateValue, ap_end_date AS apEndDateValue,",
            "       DATE_FORMAT(ap_start_date, '%Y-%m-%d') AS apStartDate,",
            "       DATE_FORMAT(ap_end_date, '%Y-%m-%d') AS apEndDate,",
            "       ap_time_range AS apTimeRange, is_available_today AS availableToday, status,",
            "       DATE_FORMAT(appointment_date, '%Y-%m-%d') AS appointmentDate,",
            "       appointment_slot_id AS appointmentSlotId, appointment_time AS appointmentTime, gate, docks,",
            "       attempt_count AS attemptCount, execution_version AS executionVersion,",
            "       DATE_FORMAT(last_attempt_at, '%Y-%m-%d %H:%i:%s') AS lastAttemptAt,",
            "       DATE_FORMAT(next_attempt_at, '%Y-%m-%d %H:%i:%s') AS nextAttemptAt,",
            "       DATE_FORMAT(ap_success_time, '%Y-%m-%d %H:%i:%s') AS apSuccessTime,",
            "       error_stage AS errorStage, failure_type AS failureType, error_message AS errorMessage,",
            "       DATE_FORMAT(gmt_create, '%Y-%m-%d %H:%i:%s') AS createdAt,",
            "       DATE_FORMAT(gmt_updated, '%Y-%m-%d %H:%i:%s') AS updatedAt",
            "FROM official_warehouse_appointment"
    );

    private static final String APPOINTMENT_PARENT_MATCH = String.join("\n",
            "  AND EXISTS (",
            "      SELECT 1",
            "      FROM official_warehouse_asn parent_asn",
            "      WHERE parent_asn.id = official_warehouse_appointment.asn_id",
            "        AND parent_asn.owner_user_id = official_warehouse_appointment.owner_user_id",
            "        AND UPPER(parent_asn.store_code) = UPPER(official_warehouse_appointment.store_code)",
            "        AND UPPER(parent_asn.site_code) = UPPER(official_warehouse_appointment.site_code)",
            "        AND parent_asn.is_deleted = b'0'",
            "  )"
    );

    private OfficialWarehouseObjectScopeSql() {
    }

    public static String selectAsnByOwner() {
        return String.join("\n",
                ASN_SELECT,
                "WHERE id = #{asnId}",
                "  AND owner_user_id = #{ownerUserId}",
                "  AND is_deleted = b'0'",
                "LIMIT 1"
        );
    }

    public static String selectAuthorizedAsn() {
        return script(String.join("\n",
                ASN_SELECT,
                "WHERE id = #{asnId}",
                exactStoreOwnerPairs("official_warehouse_asn"),
                "  AND is_deleted = b'0'",
                "LIMIT 1"
        ));
    }

    public static String selectAppointmentByOwner() {
        return String.join("\n",
                APPOINTMENT_SELECT,
                "WHERE id = #{appointmentId}",
                "  AND owner_user_id = #{ownerUserId}",
                "  AND is_deleted = b'0'",
                APPOINTMENT_PARENT_MATCH,
                "LIMIT 1"
        );
    }

    public static String selectAuthorizedAppointment() {
        return script(String.join("\n",
                APPOINTMENT_SELECT,
                "WHERE id = #{appointmentId}",
                exactStoreOwnerPairs("official_warehouse_appointment"),
                "  AND is_deleted = b'0'",
                APPOINTMENT_PARENT_MATCH,
                "LIMIT 1"
        ));
    }

    public static String selectLatestAppointmentByAsn() {
        return String.join("\n",
                APPOINTMENT_SELECT,
                "WHERE asn_id = #{asnId}",
                "  AND owner_user_id = #{ownerUserId}",
                "  AND is_deleted = b'0'",
                APPOINTMENT_PARENT_MATCH,
                "ORDER BY id DESC",
                "LIMIT 1"
        );
    }

    public static String selectActiveAppointmentByAsnForUpdate() {
        return String.join("\n",
                APPOINTMENT_SELECT,
                "WHERE asn_id = #{asnId}",
                "  AND owner_user_id = #{ownerUserId}",
                "  AND is_deleted = b'0'",
                "  AND status <> 'CANCELED'",
                APPOINTMENT_PARENT_MATCH,
                "LIMIT 1",
                "FOR UPDATE"
        );
    }

    private static String exactStoreOwnerPairs(String tableName) {
        return String.join("\n",
                "<choose>",
                "  <when test='storeOwnerUserIds != null and storeOwnerUserIds.size() > 0'>",
                "    AND",
                "    <foreach collection='storeOwnerUserIds' index='storeCode' item='ownerUserId'",
                "             open='(' separator=' OR ' close=')'>",
                "      (" + tableName + ".owner_user_id = #{ownerUserId}",
                "       AND UPPER(" + tableName + ".store_code) = UPPER(#{storeCode}))",
                "    </foreach>",
                "  </when>",
                "  <otherwise>",
                "    AND 1 = 0",
                "  </otherwise>",
                "</choose>"
        );
    }

    private static String script(String sql) {
        return "<script>\n" + sql + "\n</script>";
    }
}
