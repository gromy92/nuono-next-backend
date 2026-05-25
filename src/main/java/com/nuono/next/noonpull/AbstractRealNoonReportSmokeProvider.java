package com.nuono.next.noonpull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

abstract class AbstractRealNoonReportSmokeProvider implements NoonReportProvider {
    private static final String EMPTY_REPORT_URL_PREFIX = "memory://noon-empty-report/";
    private static final Set<String> FAILED_STATUS_CODES = Set.of(
            "FAILED",
            "FAILURE",
            "ERROR",
            "CANCELLED",
            "CANCELED"
    );

    private final ObjectMapper objectMapper;
    private final NoonPullStoreBindingResolver bindingResolver;
    private final NoonPullGatewaySessionFactory sessionFactory;
    private final String createUrl;
    private final String statusUrl;
    private final String downloadProxyUrlTemplate;

    AbstractRealNoonReportSmokeProvider(
            ObjectMapper objectMapper,
            NoonPullStoreBindingResolver bindingResolver,
            NoonPullGatewaySessionFactory sessionFactory,
            String createUrl,
            String statusUrl
    ) {
        this(objectMapper, bindingResolver, sessionFactory, createUrl, statusUrl, null);
    }

    AbstractRealNoonReportSmokeProvider(
            ObjectMapper objectMapper,
            NoonPullStoreBindingResolver bindingResolver,
            NoonPullGatewaySessionFactory sessionFactory,
            String createUrl,
            String statusUrl,
            String downloadProxyUrlTemplate
    ) {
        this.objectMapper = objectMapper;
        this.bindingResolver = bindingResolver;
        this.sessionFactory = sessionFactory;
        this.createUrl = createUrl;
        this.statusUrl = statusUrl;
        this.downloadProxyUrlTemplate = downloadProxyUrlTemplate;
    }

    @Override
    public String createExport(NoonReportPullRequest request) {
        try {
            NoonPullStoreBinding binding = bindingResolver.resolve(request);
            ObjectNode body = objectMapper.createObjectNode();
            body.put("channelCode", "web");
            body.put("exportCategoryCode", exportCategoryCode(request));
            body.put("params", writeParams(buildParams(binding, request)));

            JsonNode root = sessionFactory.login(binding).postJson(createUrl, body, false, reportHeaders(binding));
            String providerError = providerError(root);
            if (StringUtils.hasText(providerError)) {
                throw NoonPullProviderFailureMapper.explicit(
                        "report export create " + safeRequestContext(request),
                        providerError
                );
            }
            String exportId = firstText(root, "export", "exportCode", "export_code");
            if (!StringUtils.hasText(exportId)) {
                throw new NoonInterfacePullException("mapping failed: report export create response missing export code");
            }
            return exportId;
        } catch (RuntimeException exception) {
            throw NoonPullProviderFailureMapper.map("report export create " + safeRequestContext(request), exception);
        }
    }

    @Override
    public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
        try {
            NoonPullStoreBinding binding = bindingResolver.resolve(request);
            ObjectNode body = objectMapper.createObjectNode();
            body.put("exportCode", exportId);
            body.put("log", false);

            JsonNode root = sessionFactory.login(binding).postJson(statusUrl, body, false, reportHeaders(binding));
            String providerError = providerError(root);
            if (StringUtils.hasText(providerError)) {
                throw NoonPullProviderFailureMapper.explicit("report export status", providerError);
            }

            JsonNode exportNode = root.path("export");
            String statusCode = firstText(exportNode, "status_code", "statusCode", "status");
            if ("COMPLETE".equalsIgnoreCase(statusCode) || "COMPLETED".equalsIgnoreCase(statusCode)) {
                String downloadUrl = firstText(exportNode, "download_url", "downloadUrl", "download");
                int totalRows = totalRows(exportNode.path("result"));
                if (!StringUtils.hasText(downloadUrl) && totalRows <= 0) {
                    return NoonReportExportStatus.ready(EMPTY_REPORT_URL_PREFIX + exportCategoryCode(request) + "/" + exportId);
                }
                if (!StringUtils.hasText(downloadUrl)) {
                    throw new NoonInterfacePullException("mapping failed: report export completed without download url");
                }
                return NoonReportExportStatus.ready(downloadUrl);
            }
            if (FAILED_STATUS_CODES.contains(statusCode == null ? "" : statusCode.toUpperCase(Locale.ROOT))) {
                throw new NoonInterfacePullException("provider unavailable: report export failed " + statusCode);
            }
            return NoonReportExportStatus.pending();
        } catch (RuntimeException exception) {
            throw NoonPullProviderFailureMapper.map("report export status " + safeRequestContext(request), exception);
        }
    }

    @Override
    public byte[] download(NoonReportPullRequest request, String downloadUrl) {
        try {
            if (StringUtils.hasText(downloadUrl) && downloadUrl.startsWith(EMPTY_REPORT_URL_PREFIX)) {
                return emptyReportCsv().getBytes(StandardCharsets.UTF_8);
            }
            NoonPullStoreBinding binding = bindingResolver.resolve(request);
            return sessionFactory.login(binding).getBytes(effectiveDownloadUrl(downloadUrl), false, Map.of("Accept", "text/csv,*/*"));
        } catch (RuntimeException exception) {
            throw NoonPullProviderFailureMapper.map("report download " + safeRequestContext(request), exception);
        }
    }

    protected ObjectNode buildParams(NoonPullStoreBinding binding, NoonReportPullRequest request) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("id_partner", binding.getPartnerId());
        params.put("lang", "en");
        params.put("country", binding.getSiteCode().toLowerCase(Locale.ROOT));
        params.put("from_date", request.getDateFrom().toString());
        params.put("to_date", request.getDateTo().toString());
        return params;
    }

    protected String exportCategoryCode(NoonReportPullRequest request) {
        return request.getReportType();
    }

    protected abstract String emptyReportCsv();

    private String safeRequestContext(NoonReportPullRequest request) {
        return "category=" + request.getReportType()
                + " ownerUserId=" + request.getOwnerUserId()
                + " storeCode=" + request.getStoreCode()
                + " siteCode=" + request.getSiteCode();
    }

    private String writeParams(ObjectNode params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException exception) {
            throw new NoonInterfacePullException("mapping failed: unable to build report export params", exception);
        }
    }

    private String providerError(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return "provider unavailable: empty report provider response";
        }
        JsonNode error = root.path("error");
        if (!error.isMissingNode() && !error.isNull() && StringUtils.hasText(error.asText())) {
            return error.asText();
        }
        return null;
    }

    private int totalRows(JsonNode resultNode) {
        if (resultNode == null || resultNode.isMissingNode() || resultNode.isNull()) {
            return 0;
        }
        if (resultNode.isObject()) {
            return resultNode.path("total_rows").asInt(0);
        }
        if (!StringUtils.hasText(resultNode.asText())) {
            return 0;
        }
        try {
            return objectMapper.readTree(resultNode.asText()).path("total_rows").asInt(0);
        } catch (JsonProcessingException exception) {
            return 0;
        }
    }

    private String firstText(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (!value.isMissingNode() && !value.isNull() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private Map<String, String> reportHeaders(NoonPullStoreBinding binding) {
        String site = binding.getSiteCode() == null ? "ae" : binding.getSiteCode().toLowerCase(Locale.ROOT);
        return Map.of(
                "X-Project", binding.getProjectCode(),
                "X-Locale", "en-" + site,
                "X-Lang", "en"
        );
    }

    private String effectiveDownloadUrl(String downloadUrl) {
        if (!StringUtils.hasText(downloadProxyUrlTemplate)) {
            return downloadUrl;
        }
        String encodedUrl = URLEncoder.encode(downloadUrl, StandardCharsets.UTF_8);
        if (downloadProxyUrlTemplate.contains("{encodedUrl}") || downloadProxyUrlTemplate.contains("{url}")) {
            return downloadProxyUrlTemplate
                    .replace("{encodedUrl}", encodedUrl)
                    .replace("{url}", downloadUrl);
        }
        return downloadProxyUrlTemplate + encodedUrl;
    }
}
