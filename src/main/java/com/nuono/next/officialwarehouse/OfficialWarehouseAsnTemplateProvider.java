package com.nuono.next.officialwarehouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noonpull.NoonInterfacePullRequest;
import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullGatewaySessionFactory;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
@ConditionalOnBean(NoonPullGatewaySessionFactory.class)
public class OfficialWarehouseAsnTemplateProvider {

    static final String ELIGIBLE_TEMPLATE_URL =
            "https://fbn.noon.partners/_svc/inbound-partners/asn/export_eligible_lines_v2";
    private static final int MAX_TEMPLATE_BYTES = 20 * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final NoonPullStoreBindingResolver bindingResolver;
    private final NoonPullGatewaySessionFactory sessionFactory;

    public OfficialWarehouseAsnTemplateProvider(
            ObjectMapper objectMapper,
            NoonPullStoreBindingResolver bindingResolver,
            NoonPullGatewaySessionFactory sessionFactory
    ) {
        this.objectMapper = objectMapper;
        this.bindingResolver = bindingResolver;
        this.sessionFactory = sessionFactory;
    }

    public TemplateFile fetchLatest(PullRequest request) {
        requireScope(request);
        NoonPullStoreBinding binding = resolveBinding(request);
        ObjectNode body = objectMapper.createObjectNode().put("isFbn", 1);
        JsonNode response = sessionFactory.openOneShot(binding).postJsonOnce(
                ELIGIBLE_TEMPLATE_URL,
                body,
                false,
                headers(binding)
        );
        Boolean synced = firstBoolean(response, "is_synced", "isSynced");
        if (synced == null && response != null) {
            synced = firstBoolean(response.path("data"), "is_synced", "isSynced");
        }
        if (Boolean.FALSE.equals(synced)) {
            throw new IllegalStateException("Noon 最新约仓模板仍在生成，请稍后重试；如长时间未完成，请在 Noon 刷新模板。");
        }
        String mediaPath = firstText(response, "media_path", "mediaPath");
        if (mediaPath == null && response != null) {
            mediaPath = firstText(response.path("data"), "media_path", "mediaPath");
        }
        URI uri = validatedDownloadUri(mediaPath);
        byte[] content = sessionFactory.openOneShot(binding).getBytesOnce(
                uri.toString(),
                false,
                Map.of("Accept", "text/csv,*/*", "Accept-Encoding", "identity")
        );
        if (content == null || content.length == 0) {
            throw new IllegalStateException("Noon 最新约仓模板为空，请先在 Noon 刷新模板后重试。");
        }
        if (content.length > MAX_TEMPLATE_BYTES) {
            throw new IllegalStateException("Noon 约仓模板超过 20 MB，系统已停止导出。");
        }
        return new TemplateFile(content, fileName(uri));
    }

    private NoonPullStoreBinding resolveBinding(PullRequest request) {
        return bindingResolver.resolve(NoonInterfacePullRequest.builder()
                .ownerUserId(request.ownerUserId)
                .storeCode(request.storeCode)
                .siteCode(request.siteCode)
                .dataDomain(NoonPullDataDomain.PRODUCT)
                .requestName("official-warehouse-asn-eligible-template")
                .targetIdentity("official-warehouse-asn-eligible-template:" + request.storeCode)
                .build());
    }

    private Map<String, String> headers(NoonPullStoreBinding binding) {
        String site = firstNonBlank(binding.getSiteCode(), "SA").toLowerCase(Locale.ROOT);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("Content-Type", "application/json");
        headers.put("Country-Code", site);
        headers.put("Id-Partner", binding.getPartnerId());
        headers.put("Origin", "https://fbn.noon.partners");
        headers.put("Referer", "https://fbn.noon.partners/en-" + site + "/asn/createasn?project=" + binding.getProjectCode());
        headers.put("X-Locale", "en-" + site);
        headers.put("X-Platform", "web");
        headers.put("X-Project", binding.getProjectCode());
        return headers;
    }

    private URI validatedDownloadUri(String mediaPath) {
        if (!StringUtils.hasText(mediaPath)) {
            throw new IllegalStateException("Noon 最新约仓模板尚未就绪，请先在 Noon 刷新模板后重试。");
        }
        try {
            URI uri = URI.create(mediaPath.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"storage.googleapis.com".equalsIgnoreCase(uri.getHost())) {
                throw new IllegalStateException("Noon 返回了非预期的约仓模板下载地址，系统已停止导出。");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Noon 返回的约仓模板下载地址无效，系统已停止导出。", exception);
        }
    }

    private String fileName(URI uri) {
        String path = uri.getPath();
        String candidate = path == null ? null : path.substring(path.lastIndexOf('/') + 1);
        if (!StringUtils.hasText(candidate) || !candidate.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            return "fbn_eligible_items.csv";
        }
        String safe = candidate.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isEmpty() ? "fbn_eligible_items.csv" : safe;
    }

    private String firstText(JsonNode node, String... names) {
        if (node == null || names == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isMissingNode() && !value.isNull() && StringUtils.hasText(value.asText(null))) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private Boolean firstBoolean(JsonNode node, String... names) {
        if (node == null || names == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isBoolean()) {
                return value.asBoolean();
            }
            if (value.isInt() || value.isLong()) {
                return value.asInt() == 1;
            }
            String text = value.isMissingNode() || value.isNull() ? null : value.asText(null);
            if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
                return false;
            }
        }
        return null;
    }

    private void requireScope(PullRequest request) {
        if (request == null || request.ownerUserId == null || request.ownerUserId <= 0
                || !StringUtils.hasText(request.storeCode) || !StringUtils.hasText(request.siteCode)) {
            throw new IllegalArgumentException("约仓模板缺少 owner/store/site 范围。");
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
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

    public static class TemplateFile {
        public final byte[] content;
        public final String fileName;

        public TemplateFile(byte[] content, String fileName) {
            this.content = content;
            this.fileName = fileName;
        }
    }
}
