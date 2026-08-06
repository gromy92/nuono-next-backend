package com.nuono.next.noonpull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noon.NoonBinaryDownloadContractException;
import com.nuono.next.noon.NoonBinaryDownloadSink;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

abstract class AbstractRealNoonReportSmokeProvider implements NoonReportProvider {
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

            JsonNode root = sessionFactory.openOneShot(binding).postJsonOnce(
                    createUrl, body, true, reportHeaders(binding)
            );
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

            JsonNode root = sessionFactory.openOneShot(binding).postJsonOnce(
                    statusUrl, body, true, reportHeaders(binding)
            );
            String providerError = providerError(root);
            if (StringUtils.hasText(providerError)) {
                throw NoonPullProviderFailureMapper.explicit("report export status", providerError);
            }

            JsonNode exportNode = root.path("export");
            String statusCode = firstText(exportNode, "status_code", "statusCode", "status");
            if ("COMPLETE".equalsIgnoreCase(statusCode) || "COMPLETED".equalsIgnoreCase(statusCode)) {
                String downloadUrl = firstText(exportNode, "download_url", "downloadUrl", "download");
                Integer totalRows = totalRows(exportNode.path("result"));
                if (!StringUtils.hasText(downloadUrl)
                        && (totalRows == null || totalRows != 0)) {
                    throw new NoonInterfacePullException("mapping failed: report export completed without download url");
                }
                String providerExportId = firstText(
                        exportNode,
                        "export_code",
                        "exportCode",
                        "export",
                        "code"
                );
                return StringUtils.hasText(providerExportId)
                        ? NoonReportExportStatus.readyForProviderExport(
                                providerExportId,
                                downloadUrl,
                                totalRows
                        )
                        : NoonReportExportStatus.ready(downloadUrl, totalRows);
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
            NoonPullStoreBinding binding = bindingResolver.resolve(request);
            return sessionFactory.openOneShot(binding).getBytesOnce(
                    effectiveDownloadUrl(downloadUrl),
                    false,
                    Map.of("Accept", "text/csv,*/*")
            );
        } catch (RuntimeException exception) {
            throw NoonPullProviderFailureMapper.map("report download " + safeRequestContext(request), exception);
        }
    }

    @Override
    public void download(
            NoonReportPullRequest request,
            String downloadUrl,
            NoonBinaryDownloadSink sink
    ) {
        try {
            NoonPullStoreBinding binding = bindingResolver.resolve(request);
            sessionFactory.openOneShot(binding).getBytesOnce(
                    effectiveDownloadUrl(downloadUrl),
                    false,
                    Map.of("Accept", "text/csv,*/*", "Accept-Encoding", "identity"),
                    sink
            );
        } catch (NoonBinaryDownloadContractException contractFailure) {
            sink.abort(contractFailure);
            throw contractFailure;
        } catch (RuntimeException exception) {
            sink.abort(exception);
            throw NoonPullProviderFailureMapper.map(
                    "report download " + safeRequestContext(request), exception
            );
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

    private Integer totalRows(JsonNode resultNode) {
        if (resultNode == null || resultNode.isMissingNode() || resultNode.isNull()) {
            return null;
        }
        if (resultNode.isObject()) {
            return firstInteger(
                    resultNode.path("total_rows"),
                    resultNode.path("totalRows"),
                    resultNode.path("row_count"),
                    resultNode.path("rowCount")
            );
        }
        if (!StringUtils.hasText(resultNode.asText())) {
            return null;
        }
        try {
            return totalRows(objectMapper.readTree(resultNode.asText()));
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private Integer firstInteger(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                continue;
            }
            if (node.isIntegralNumber() && node.canConvertToInt()) {
                return node.asInt();
            }
            String text = node.asText(null);
            if (StringUtils.hasText(text)) {
                try {
                    return Integer.parseInt(text.trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
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
