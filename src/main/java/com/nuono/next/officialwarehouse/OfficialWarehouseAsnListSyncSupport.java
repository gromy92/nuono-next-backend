package com.nuono.next.officialwarehouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.util.StringUtils;

final class OfficialWarehouseAsnListSyncSupport {

    private OfficialWarehouseAsnListSyncSupport() {
    }

    static ObjectNode buildListRequest(
            ObjectMapper objectMapper,
            String partnerId,
            int page,
            int perPage,
            int totalPages
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        Long parsedPartnerId = parseLongOrNull(partnerId);
        if (parsedPartnerId == null) {
            body.put("idPartnerSource", partnerId);
        } else {
            body.put("idPartnerSource", parsedPartnerId);
        }
        body.put("asnNr", "");
        ObjectNode pagination = body.putObject("pagination");
        pagination.put("page", Math.max(1, page));
        pagination.put("perPage", Math.max(1, perPage));
        pagination.put("totalPages", Math.max(1, totalPages));
        body.putObject("filters").putArray("status_list");
        body.put("orderBy", "DESC");
        return body;
    }

    static ObjectNode buildSearchRequest(
            ObjectMapper objectMapper,
            String partnerId,
            String asnNr
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        Long parsedPartnerId = parseLongOrNull(partnerId);
        if (parsedPartnerId == null) {
            body.put("idPartnerSource", partnerId);
        } else {
            body.put("idPartnerSource", parsedPartnerId);
        }
        body.put("asnNr", trimToNull(asnNr));
        ObjectNode pagination = body.putObject("pagination");
        pagination.put("page", 1);
        pagination.put("perPage", 10);
        pagination.put("totalPages", 10);
        body.putObject("filters");
        body.put("orderBy", "DESC");
        return body;
    }

    static NoonAsnListRow parseRow(JsonNode row) {
        NoonAsnListRow parsed = new NoonAsnListRow();
        parsed.partnerAsnId = longValue(row, "id_partner_asn");
        parsed.asnNr = firstText(row, "asn_nr", "asnNr");
        parsed.totalQty = intValue(row, "total_qty");
        parsed.remoteStatus = firstText(row, "status", "asn_status");
        parsed.localAsnStatus = OfficialWarehouseStatusPolicy.localAsnStatusFromNoon(parsed.remoteStatus);
        parsed.warehouseToPartnerCode = firstText(row, "warehouse_to", "warehouseTo");
        parsed.warehouseToCode = firstText(row, "warehouse_code_to", "warehouseCodeTo");
        parsed.countryCode = firstText(row, "country_code_to", "country_code", "countryCode");
        parsed.noonUpdatedAt = parseDateTimeOrNull(firstText(row, "updated_at", "updatedAt"));
        parsed.scheduleDateRaw = firstText(row, "schedule_date", "scheduleDate");
        parsed.appointmentDate = parseDateOrNull(parsed.scheduleDateRaw);
        parsed.appointmentTime = firstText(row, "schedule_slot", "scheduleSlot");
        parsed.gate = firstText(row, "gate");
        parsed.docks = firstText(row, "docks");
        parsed.inboundStatus = firstText(row, "inbound_status", "inboundStatus");
        parsed.statusCode = firstText(row, "status_code", "statusCode");
        parsed.discrepancyReason = firstText(row, "discrepancy_reason", "discrepancyReason");
        return parsed;
    }

    static boolean isRemoteFailureStatus(String status) {
        return OfficialWarehouseStatusPolicy.isNoonAsnFailureStatus(status);
    }

    private static String firstText(JsonNode node, String... fieldNames) {
        if (fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            String value = text(node, fieldName);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String fieldName) {
        if (node == null || !StringUtils.hasText(fieldName)) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return trimToNull(value.asText(null));
    }

    private static Integer intValue(JsonNode node, String fieldName) {
        Long value = longValue(node, fieldName);
        return value == null ? null : value.intValue();
    }

    private static Long longValue(JsonNode node, String fieldName) {
        return parseLongOrNull(text(node, fieldName));
    }

    private static Long parseLongOrNull(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static LocalDate parseDateOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception exception) {
            return null;
        }
    }

    private static LocalDateTime parseDateTimeOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replace(' ', 'T');
        try {
            return LocalDateTime.parse(normalized);
        } catch (Exception exception) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static final class NoonAsnListRow {
        Long partnerAsnId;
        String asnNr;
        Integer totalQty;
        String remoteStatus;
        String localAsnStatus;
        String warehouseToPartnerCode;
        String warehouseToCode;
        String countryCode;
        LocalDateTime noonUpdatedAt;
        String scheduleDateRaw;
        LocalDate appointmentDate;
        String appointmentTime;
        String gate;
        String docks;
        String inboundStatus;
        String statusCode;
        String discrepancyReason;

        boolean hasConfirmedAppointment() {
            return appointmentDate != null && StringUtils.hasText(appointmentTime);
        }

        boolean remoteFailed() {
            return isRemoteFailureStatus(remoteStatus);
        }
    }
}
