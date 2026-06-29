package com.nuono.next.officialwarehouse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noonpull.NoonInterfacePullRequest;
import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullGatewaySessionFactory;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
@ConditionalOnBean(NoonPullGatewaySessionFactory.class)
public class OfficialWarehouseFbnExportProvider {

    static final String FBN_EXPORT_LIST_URL = "https://fbn.noon.partners/_svc/export/list";
    static final String FBN_EXPORT_STATUS_URL = "https://fbn.noon.partners/_svc/export/status";
    static final String FBN_EXPORT_CREATE_URL = "https://fbn.noon.partners/_svc/export/create";

    private final ObjectMapper objectMapper;
    private final NoonPullStoreBindingResolver bindingResolver;
    private final NoonPullGatewaySessionFactory sessionFactory;

    public OfficialWarehouseFbnExportProvider(
            ObjectMapper objectMapper,
            NoonPullStoreBindingResolver bindingResolver,
            NoonPullGatewaySessionFactory sessionFactory
    ) {
        this.objectMapper = objectMapper;
        this.bindingResolver = bindingResolver;
        this.sessionFactory = sessionFactory;
    }

    public ExportListPage listExports(PullRequest request, int page, int perPage) {
        requireScope(request);
        int safePage = Math.max(1, page);
        int safePerPage = perPage <= 0 ? 20 : Math.min(perPage, 100);
        NoonPullStoreBinding binding = resolveBinding(request, "official-warehouse-fbn-export-list");
        JsonNode response = sessionFactory.login(binding).postJson(
                FBN_EXPORT_LIST_URL,
                listBody(safePage, safePerPage),
                false,
                fbnReportHeaders(binding)
        );
        List<ExportItem> items = new ArrayList<>();
        for (JsonNode row : readRowNodes(response)) {
            items.add(ExportItem.from(row));
        }
        return new ExportListPage(safePage, safePerPage, hasNextPage(response, safePage), items, response);
    }

    public ExportStatus exportStatus(PullRequest request, String exportCode, boolean log) {
        requireScope(request);
        String safeExportCode = trimToNull(exportCode);
        if (safeExportCode == null) {
            throw new IllegalArgumentException("缺少官方仓 FBN 报表 exportCode。");
        }
        NoonPullStoreBinding binding = resolveBinding(request, "official-warehouse-fbn-export-status");
        JsonNode response = sessionFactory.login(binding).postJson(
                FBN_EXPORT_STATUS_URL,
                statusBody(safeExportCode, log),
                false,
                fbnReportHeaders(binding)
        );
        return ExportStatus.from(objectMapper, statusNode(response), safeExportCode, response);
    }

    public CreateExportResult createExport(PullRequest request, CreateExportRequest createRequest) {
        requireScope(request);
        if (createRequest == null || !StringUtils.hasText(createRequest.exportCategoryCode)) {
            throw new IllegalArgumentException("缺少官方仓 FBN 报表类型。");
        }
        NoonPullStoreBinding binding = resolveBinding(request, "official-warehouse-fbn-export-create");
        JsonNode response = sessionFactory.login(binding).postJson(
                FBN_EXPORT_CREATE_URL,
                createBody(binding, createRequest),
                false,
                fbnReportHeaders(binding)
        );
        return CreateExportResult.from(createNode(response), createRequest.exportCategoryCode, response);
    }

    public byte[] download(PullRequest request, String downloadUrl) {
        requireScope(request);
        String safeDownloadUrl = trimToNull(downloadUrl);
        if (safeDownloadUrl == null) {
            throw new IllegalArgumentException("缺少官方仓 FBN 报表下载地址。");
        }
        NoonPullStoreBinding binding = resolveBinding(request, "official-warehouse-fbn-export-download");
        return sessionFactory.login(binding).getBytes(
                safeDownloadUrl,
                false,
                Map.of("Accept", "text/csv,*/*")
        );
    }

    private NoonPullStoreBinding resolveBinding(PullRequest request, String requestName) {
        return bindingResolver.resolve(NoonInterfacePullRequest.builder()
                .ownerUserId(request.ownerUserId)
                .storeCode(request.storeCode)
                .siteCode(request.siteCode)
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .requestName(requestName)
                .targetIdentity(requestName + ":" + request.storeCode)
                .build());
    }

    private ObjectNode listBody(int page, int perPage) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("page", page);
        body.put("perPage", perPage);
        body.put("tenantCode", "fbn");
        return body;
    }

    private ObjectNode createBody(NoonPullStoreBinding binding, CreateExportRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("exportCategoryCode", request.exportCategoryCode.trim());
        body.put("params", createParamsJson(binding, request));
        body.put("channelCode", "web");
        return body;
    }

    private String createParamsJson(NoonPullStoreBinding binding, CreateExportRequest request) {
        ObjectNode params = objectMapper.createObjectNode();
        putPartnerId(params, binding.getPartnerId());
        params.put("country", siteCode(binding).toLowerCase(Locale.ROOT));
        params.put("from_date", request.fromDate);
        params.put("to_date", request.toDate);
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("官方仓 FBN 报表创建参数序列化失败。", exception);
        }
    }

    private void putPartnerId(ObjectNode params, String partnerId) {
        String safePartnerId = trimToNull(partnerId);
        if (safePartnerId == null) {
            return;
        }
        try {
            params.put("id_partner", Long.parseLong(safePartnerId));
        } catch (NumberFormatException exception) {
            params.put("id_partner", safePartnerId);
        }
    }

    private ObjectNode statusBody(String exportCode, boolean log) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("exportCode", exportCode);
        body.put("log", log);
        return body;
    }

    private Map<String, String> fbnReportHeaders(NoonPullStoreBinding binding) {
        String site = siteCode(binding).toLowerCase(Locale.ROOT);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("Content-Type", "application/json");
        headers.put("Country-Code", site);
        headers.put("Id-Partner", binding.getPartnerId());
        headers.put("Origin", "https://fbn.noon.partners");
        headers.put("Referer", "https://fbn.noon.partners/en-" + site + "/fbnreports?project=" + binding.getProjectCode());
        headers.put("X-Locale", "en-" + site);
        headers.put("X-Platform", "web");
        headers.put("X-Project", binding.getProjectCode());
        return headers;
    }

    private List<JsonNode> readRowNodes(JsonNode response) {
        List<JsonNode> rows = new ArrayList<>();
        JsonNode array = firstArray(
                response,
                response == null ? null : response.path("data"),
                response == null ? null : response.path("data").path("rows"),
                response == null ? null : response.path("data").path("items"),
                response == null ? null : response.path("data").path("hits"),
                response == null ? null : response.path("data").path("exports"),
                response == null ? null : response.path("data").path("list"),
                response == null ? null : response.path("data").path("export_code"),
                response == null ? null : response.path("rows"),
                response == null ? null : response.path("items"),
                response == null ? null : response.path("hits"),
                response == null ? null : response.path("exports"),
                response == null ? null : response.path("list"),
                response == null ? null : response.path("export_code")
        );
        if (array != null && array.isArray()) {
            for (JsonNode item : array) {
                rows.add(item);
            }
        }
        return rows;
    }

    private JsonNode statusNode(JsonNode response) {
        JsonNode node = firstObject(
                response == null ? null : response.path("export"),
                response == null ? null : response.path("data").path("export"),
                response == null ? null : response.path("data"),
                response
        );
        return node == null ? objectMapper.createObjectNode() : node;
    }

    private JsonNode createNode(JsonNode response) {
        JsonNode node = firstObject(
                response == null ? null : response.path("export"),
                response == null ? null : response.path("data").path("export"),
                response == null ? null : response.path("data"),
                response
        );
        return node == null ? objectMapper.createObjectNode() : node;
    }

    private boolean hasNextPage(JsonNode response, int currentPage) {
        Boolean explicit = firstBoolean(
                response == null ? null : response.path("has_next"),
                response == null ? null : response.path("hasNext"),
                response == null ? null : response.path("data").path("has_next"),
                response == null ? null : response.path("data").path("hasNext"),
                response == null ? null : response.path("pagination").path("has_next"),
                response == null ? null : response.path("pagination").path("hasNext"),
                response == null ? null : response.path("data").path("pagination").path("has_next"),
                response == null ? null : response.path("data").path("pagination").path("hasNext")
        );
        if (explicit != null) {
            return explicit;
        }
        Integer totalPages = firstInteger(
                response == null ? null : response.path("total_pages"),
                response == null ? null : response.path("totalPages"),
                response == null ? null : response.path("pagination").path("total_pages"),
                response == null ? null : response.path("pagination").path("totalPages"),
                response == null ? null : response.path("data").path("total_pages"),
                response == null ? null : response.path("data").path("totalPages"),
                response == null ? null : response.path("data").path("pagination").path("total_pages"),
                response == null ? null : response.path("data").path("pagination").path("totalPages")
        );
        return totalPages != null && currentPage < totalPages;
    }

    private JsonNode firstArray(JsonNode... nodes) {
        if (nodes == null) {
            return null;
        }
        for (JsonNode node : nodes) {
            if (node != null && node.isArray()) {
                return node;
            }
        }
        return null;
    }

    private JsonNode firstObject(JsonNode... nodes) {
        if (nodes == null) {
            return null;
        }
        for (JsonNode node : nodes) {
            if (node != null && node.isObject()) {
                return node;
            }
        }
        return null;
    }

    private Boolean firstBoolean(JsonNode... nodes) {
        if (nodes == null) {
            return null;
        }
        for (JsonNode node : nodes) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                continue;
            }
            if (node.isBoolean()) {
                return node.asBoolean();
            }
            String text = trimToNull(node.asText(null));
            if ("true".equalsIgnoreCase(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text)) {
                return false;
            }
        }
        return null;
    }

    private Integer firstInteger(JsonNode... nodes) {
        if (nodes == null) {
            return null;
        }
        for (JsonNode node : nodes) {
            Integer value = integer(node);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String siteCode(NoonPullStoreBinding binding) {
        return firstNonBlank(binding.getSiteCode(), "SA").toUpperCase(Locale.ROOT);
    }

    private void requireScope(PullRequest request) {
        if (request == null || request.ownerUserId == null || !StringUtils.hasText(request.storeCode)) {
            throw new IllegalArgumentException("缺少官方仓 FBN 报表店铺范围。");
        }
    }

    private static String downloadUrl(JsonNode node) {
        return firstNonBlank(
                text(node, "download_url", "downloadUrl", "download", "url"),
                text(node == null ? null : node.path("export_attachment"), "url", "download_url", "downloadUrl"),
                text(node == null ? null : node.path("file"), "url", "download_url", "downloadUrl"),
                text(node == null ? null : node.path("attachment"), "url", "download_url", "downloadUrl")
        );
    }

    private static String text(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = trimToNull(value.asText(null));
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    private static Integer integer(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isInt() || node.isLong()) {
            return node.asInt();
        }
        try {
            String text = trimToNull(node.asText(null));
            return text == null ? null : Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Integer totalRows(ObjectMapper objectMapper, JsonNode resultNode) {
        if (resultNode == null || resultNode.isMissingNode() || resultNode.isNull()) {
            return null;
        }
        if (resultNode.isObject()) {
            return firstIntegerStatic(
                    resultNode.path("total_rows"),
                    resultNode.path("totalRows"),
                    resultNode.path("row_count"),
                    resultNode.path("rowCount")
            );
        }
        String text = trimToNull(resultNode.asText(null));
        if (text == null) {
            return null;
        }
        try {
            JsonNode parsed = objectMapper.readTree(text);
            return totalRows(objectMapper, parsed);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private static Integer firstIntegerStatic(JsonNode... nodes) {
        if (nodes == null) {
            return null;
        }
        for (JsonNode node : nodes) {
            Integer value = integer(node);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static class PullRequest {
        public final Long ownerUserId;
        public final String storeCode;
        public final String siteCode;

        public PullRequest(Long ownerUserId, String storeCode, String siteCode) {
            this.ownerUserId = ownerUserId;
            this.storeCode = storeCode;
            this.siteCode = siteCode;
        }
    }

    public static class ExportListPage {
        public final int page;
        public final int perPage;
        public final boolean hasNextPage;
        public final List<ExportItem> items;
        public final JsonNode rawResponse;

        public ExportListPage(int page, int perPage, boolean hasNextPage, List<ExportItem> items, JsonNode rawResponse) {
            this.page = page;
            this.perPage = perPage;
            this.hasNextPage = hasNextPage;
            this.items = items;
            this.rawResponse = rawResponse;
        }
    }

    public static class ExportItem {
        public final String exportCode;
        public final String status;
        public final String reportType;
        public final String fileName;
        public final String createdAt;
        public final String downloadUrl;
        public final JsonNode rawPayload;

        private ExportItem(
                String exportCode,
                String status,
                String reportType,
                String fileName,
                String createdAt,
                String downloadUrl,
                JsonNode rawPayload
        ) {
            this.exportCode = exportCode;
            this.status = status;
            this.reportType = reportType;
            this.fileName = fileName;
            this.createdAt = createdAt;
            this.downloadUrl = downloadUrl;
            this.rawPayload = rawPayload;
        }

        static ExportItem from(JsonNode row) {
            return new ExportItem(
                    text(row, "exportCode", "export_code", "export", "code", "id_export"),
                    text(row, "status", "status_code", "statusCode"),
                    text(row, "reportType", "report_type", "exportCategoryCode", "export_category_code", "category", "category_code"),
                    text(row, "fileName", "file_name", "filename", "name", "name_en"),
                    text(row, "createdAt", "created_at", "createdTime", "created_time"),
                    downloadUrl(row),
                    row
            );
        }
    }

    public static class ExportStatus {
        public final String exportCode;
        public final String status;
        public final String fileName;
        public final String downloadUrl;
        public final String message;
        public final Integer totalRows;
        public final JsonNode rawResponse;

        private ExportStatus(
                String exportCode,
                String status,
                String fileName,
                String downloadUrl,
                String message,
                Integer totalRows,
                JsonNode rawResponse
        ) {
            this.exportCode = exportCode;
            this.status = status;
            this.fileName = fileName;
            this.downloadUrl = downloadUrl;
            this.message = message;
            this.totalRows = totalRows;
            this.rawResponse = rawResponse;
        }

        static ExportStatus from(ObjectMapper objectMapper, JsonNode node, String fallbackExportCode, JsonNode rawResponse) {
            return new ExportStatus(
                    firstNonBlank(text(node, "exportCode", "export_code", "export", "code"), fallbackExportCode),
                    text(node, "status", "status_code", "statusCode"),
                    text(node, "fileName", "file_name", "filename", "name"),
                    downloadUrl(node),
                    text(node, "message", "error", "error_message", "errorMessage"),
                    totalRows(objectMapper, node == null ? null : node.path("result")),
                    rawResponse
            );
        }
    }

    public static class CreateExportRequest {
        public final String exportCategoryCode;
        public final String fromDate;
        public final String toDate;

        public CreateExportRequest(String exportCategoryCode, String fromDate, String toDate) {
            this.exportCategoryCode = exportCategoryCode;
            this.fromDate = fromDate;
            this.toDate = toDate;
        }
    }

    public static class CreateExportResult {
        public final String exportCode;
        public final String status;
        public final String reportType;
        public final JsonNode rawResponse;

        public CreateExportResult(String exportCode, String status, String reportType, JsonNode rawResponse) {
            this.exportCode = exportCode;
            this.status = status;
            this.reportType = reportType;
            this.rawResponse = rawResponse;
        }

        static CreateExportResult from(JsonNode node, String fallbackReportType, JsonNode rawResponse) {
            return new CreateExportResult(
                    text(node, "exportCode", "export_code", "export", "code", "id_export"),
                    text(node, "status", "status_code", "statusCode"),
                    firstNonBlank(
                            text(node, "reportType", "report_type", "exportCategoryCode", "export_category_code", "category", "category_code"),
                            fallbackReportType
                    ),
                    rawResponse
            );
        }
    }
}
