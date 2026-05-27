package com.nuono.next.noonpull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

class NoonSalesDashboardExportProvider implements NoonReportProvider {
    @FunctionalInterface
    interface ExportBodyFactory {
        ObjectNode build(ObjectMapper objectMapper, NoonPullStoreBinding binding, NoonReportPullRequest request);
    }

    private final ObjectMapper objectMapper;
    private final NoonPullStoreBindingResolver bindingResolver;
    private final NoonPullGatewaySessionFactory sessionFactory;
    private final String generateUrl;
    private final String latestUrl;
    private final String downloadProxyUrlTemplate;
    private final String stageName;
    private final String exportIdPrefix;
    private final ExportBodyFactory bodyFactory;

    NoonSalesDashboardExportProvider(
            ObjectMapper objectMapper,
            NoonPullStoreBindingResolver bindingResolver,
            NoonPullGatewaySessionFactory sessionFactory,
            String generateUrl,
            String latestUrl,
            String downloadProxyUrlTemplate,
            String stageName,
            String exportIdPrefix,
            ExportBodyFactory bodyFactory
    ) {
        this.objectMapper = objectMapper;
        this.bindingResolver = bindingResolver;
        this.sessionFactory = sessionFactory;
        this.generateUrl = generateUrl;
        this.latestUrl = latestUrl;
        this.downloadProxyUrlTemplate = downloadProxyUrlTemplate;
        this.stageName = StringUtils.hasText(stageName) ? stageName.trim() : "sales dashboard export";
        this.exportIdPrefix = StringUtils.hasText(exportIdPrefix) ? exportIdPrefix.trim() : "sales-dashboard-export";
        this.bodyFactory = bodyFactory;
    }

    @Override
    public String createExport(NoonReportPullRequest request) {
        try {
            requireDateWindow(request);
            NoonPullStoreBinding binding = bindingResolver.resolve(request);
            JsonNode root = sessionFactory.login(binding).postJson(
                    generateUrl,
                    bodyFactory.build(objectMapper, binding, request),
                    false,
                    localeHeaders(binding)
            );
            String providerError = providerError(root);
            if (StringUtils.hasText(providerError)) {
                throw NoonPullProviderFailureMapper.explicit(
                        stageName + " generate " + safeRequestContext(request),
                        providerError
                );
            }
            return exportIdPrefix + ":" + request.getDateFrom() + ".." + request.getDateTo();
        } catch (RuntimeException exception) {
            throw NoonPullProviderFailureMapper.map(stageName + " generate " + safeRequestContext(request), exception);
        }
    }

    @Override
    public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
        try {
            requireDateWindow(request);
            NoonPullStoreBinding binding = bindingResolver.resolve(request);
            JsonNode root = sessionFactory.login(binding).postJson(
                    latestUrl,
                    bodyFactory.build(objectMapper, binding, request),
                    false,
                    localeHeaders(binding)
            );
            String providerError = providerError(root);
            if (StringUtils.hasText(providerError)) {
                throw NoonPullProviderFailureMapper.explicit(
                        stageName + " latest " + safeRequestContext(request),
                        providerError
                );
            }
            String status = text(root, "status");
            String downloadUrl = firstText(root.path("export_attachment"), "url", "download_url", "downloadUrl");
            if ("Success".equalsIgnoreCase(status) && StringUtils.hasText(downloadUrl)) {
                return NoonReportExportStatus.ready(downloadUrl);
            }
            if (isFailure(status)) {
                return NoonReportExportStatus.failed(status);
            }
            return NoonReportExportStatus.pending();
        } catch (RuntimeException exception) {
            throw NoonPullProviderFailureMapper.map(stageName + " latest " + safeRequestContext(request), exception);
        }
    }

    @Override
    public byte[] download(NoonReportPullRequest request, String downloadUrl) {
        try {
            NoonPullStoreBinding binding = bindingResolver.resolve(request);
            return sessionFactory.login(binding).getBytes(
                    effectiveDownloadUrl(downloadUrl),
                    false,
                    Map.of("Accept", "text/csv,*/*")
            );
        } catch (RuntimeException exception) {
            throw NoonPullProviderFailureMapper.map(stageName + " download " + safeRequestContext(request), exception);
        }
    }

    private void requireDateWindow(NoonReportPullRequest request) {
        if (request == null || request.getDateFrom() == null || request.getDateTo() == null) {
            throw new IllegalArgumentException("missing sales dashboard export date window");
        }
        if (request.getDateTo().isBefore(request.getDateFrom())) {
            throw new IllegalArgumentException("invalid sales dashboard export date window");
        }
    }

    private String providerError(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return "provider unavailable: empty sales dashboard export response";
        }
        JsonNode error = root.path("error");
        if (!error.isMissingNode() && !error.isNull() && StringUtils.hasText(error.asText())) {
            return error.asText();
        }
        JsonNode message = root.path("message");
        if (!message.isMissingNode() && !message.isNull() && StringUtils.hasText(message.asText())
                && !StringUtils.hasText(text(root, "status"))) {
            return message.asText();
        }
        return null;
    }

    private boolean isFailure(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("fail") || normalized.contains("error") || normalized.contains("cancel");
    }

    private Map<String, String> localeHeaders(NoonPullStoreBinding binding) {
        String site = siteCode(binding).toLowerCase(Locale.ROOT);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Project", binding.getProjectCode());
        headers.put("X-Locale", "en-" + site);
        headers.put("X-Lang", "en");
        return headers;
    }

    private String safeRequestContext(NoonReportPullRequest request) {
        if (request == null) {
            return "ownerUserId=null storeCode=null siteCode=null";
        }
        return "ownerUserId=" + request.getOwnerUserId()
                + " storeCode=" + request.getStoreCode()
                + " siteCode=" + request.getSiteCode();
    }

    private String firstText(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
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

    private String text(JsonNode node, String fieldName) {
        if (node == null || !StringUtils.hasText(fieldName)) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull() || !StringUtils.hasText(value.asText())) {
            return null;
        }
        return value.asText().trim();
    }

    private String siteCode(NoonPullStoreBinding binding) {
        return binding.getSiteCode() == null ? "AE" : binding.getSiteCode().toUpperCase(Locale.ROOT);
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
