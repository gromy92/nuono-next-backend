package com.nuono.next.officialwarehouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseNoonInboundClient.NoonCallContext;
import com.nuono.next.sales.NoonSalesReportBinding;
import com.nuono.next.web.ApiProblemException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class OfficialWarehouseAsnPreflightAuditRecorder {
    private static final Logger LOGGER = LoggerFactory.getLogger(OfficialWarehouseAsnPreflightAuditRecorder.class);
    private static final String FAILURE_CODE = "OFFICIAL_WAREHOUSE_ASN_PRODUCT_PREFLIGHT_FAILED";

    private OfficialWarehouseAsnPreflightAuditRecorder() {
    }

    static void record(
            OfficialWarehouseMapper mapper,
            ObjectMapper objectMapper,
            NoonSalesReportBinding binding,
            NoonCallContext context,
            Long operatorUserId,
            int requestLineCount,
            ApiProblemException exception
    ) {
        if (exception == null || !FAILURE_CODE.equals(exception.getCode())) {
            return;
        }
        try {
            JsonNode invalidLines = objectMapper.valueToTree(
                    exception.getDetails() == null ? List.of() : exception.getDetails().get("invalidLines")
            );
            OfficialWarehouseAsnPreflightAuditRecord row = new OfficialWarehouseAsnPreflightAuditRecord();
            row.id = mapper.nextAsnPreflightAuditId();
            row.ownerUserId = binding.getOwnerUserId();
            row.operatorUserId = operatorUserId;
            row.logicalStoreId = binding.getLogicalStoreId();
            row.projectCode = binding.getProjectCode();
            row.storeCode = binding.getStoreCode();
            row.siteCode = binding.getSiteCode();
            row.partnerId = binding.getPartnerId();
            row.attemptAsnId = parseLongOrNull(context.businessId);
            row.attemptRef = context.businessRef;
            row.operation = "CREATE_ASN";
            row.requestLineCount = requestLineCount;
            row.invalidLineCount = invalidLines.isArray() ? invalidLines.size() : 0;
            row.failureCode = exception.getCode();
            row.failureMessage = shrinkMessage(exception);
            row.reasonSummary = reasonSummary(invalidLines);
            row.invalidLinesJson = writeJson(objectMapper, invalidLines);
            mapper.insertAsnPreflightAudit(row);
        } catch (RuntimeException auditException) {
            LOGGER.error(
                    "官方仓 ASN 商品预检失败审计写入异常: attemptAsnId={}, storeCode={}, siteCode={}",
                    context.businessId, binding.getStoreCode(), binding.getSiteCode(), auditException
            );
        }
    }

    private static String reasonSummary(JsonNode invalidLines) {
        if (invalidLines == null || !invalidLines.isArray()) return null;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (JsonNode line : invalidLines) {
            String reasonCode = text(line, "reasonCode");
            if (reasonCode != null) counts.merge(reasonCode, 1, Integer::sum);
        }
        String summary = counts.entrySet().stream()
                .map(entry -> entry.getKey() + " x" + entry.getValue())
                .collect(Collectors.joining("; "));
        return summary.length() > 1000 ? summary.substring(0, 1000) : summary;
    }

    private static String writeJson(ObjectMapper objectMapper, JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private static Long parseLongOrNull(String text) {
        try {
            return text == null ? null : Long.parseLong(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String shrinkMessage(Throwable throwable) {
        String message = throwable.getMessage();
        String normalized = message == null ? throwable.getClass().getSimpleName() : message.replaceAll("\\s+", " ").trim();
        return normalized.length() > 900 ? normalized.substring(0, 900) : normalized;
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.path(fieldName);
        if (value == null || value.isMissingNode() || value.isNull()) return null;
        String text = value.asText(null);
        return text == null || text.trim().isEmpty() ? null : text.trim();
    }
}
