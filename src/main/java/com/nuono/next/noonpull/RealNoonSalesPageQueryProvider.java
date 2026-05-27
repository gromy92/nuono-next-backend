package com.nuono.next.noonpull;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnBean(NoonPullGatewaySessionFactory.class)
@ConditionalOnProperty(
        prefix = "nuono.noon.pull.real-provider",
        name = {"enabled", "sales-page-query.enabled"},
        havingValue = "true"
)
public class RealNoonSalesPageQueryProvider implements NoonSalesPageQueryProvider {
    private static final String DEFAULT_LIST_URL =
            "https://reports.noon.partners/_vs/mp/mp-inventory-health-api-sales-dashboard/sales-dashboard/list";

    private final ObjectMapper objectMapper;
    private final NoonPullStoreBindingResolver bindingResolver;
    private final NoonPullGatewaySessionFactory sessionFactory;
    private final String listUrl;
    private final int pageSize;

    public RealNoonSalesPageQueryProvider(
            ObjectMapper objectMapper,
            NoonPullStoreBindingResolver bindingResolver,
            NoonPullGatewaySessionFactory sessionFactory,
            @Value("${nuono.noon.pull.real-provider.sales-page-query.list-url:" + DEFAULT_LIST_URL + "}") String listUrl,
            @Value("${nuono.noon.pull.real-provider.sales-page-query.page-size:20}") int pageSize
    ) {
        this.objectMapper = objectMapper;
        this.bindingResolver = bindingResolver;
        this.sessionFactory = sessionFactory;
        this.listUrl = StringUtils.hasText(listUrl) ? listUrl.trim() : DEFAULT_LIST_URL;
        this.pageSize = Math.max(1, pageSize);
    }

    @Override
    public NoonInterfacePullPage fetchPage(NoonInterfacePullRequest request, int pageNumber) {
        try {
            requireDateWindow(request);
            NoonPullStoreBinding binding = bindingResolver.resolve(request);
            NoonPullGatewaySession session = sessionFactory.login(binding);
            ObjectNode body = objectMapper.createObjectNode();
            body.put("country_code", siteCode(binding));
            body.put("page", Math.max(1, pageNumber));
            body.put("per_page", pageSize);
            body.set("filters", objectMapper.createObjectNode());
            body.put("search", "");
            body.put("from_date", request.getDateFrom().toString());
            body.put("to_date", request.getDateTo().toString());

            JsonNode root = session.postJson(listUrl, body, false, localeHeaders(binding));
            String providerError = providerError(root);
            if (StringUtils.hasText(providerError)) {
                throw NoonPullProviderFailureMapper.explicit("sales page query", providerError);
            }

            JsonNode hits = hitsNode(root);
            List<Map<String, Object>> items = new ArrayList<>();
            if (hits.isArray()) {
                for (JsonNode hit : hits) {
                    items.add(objectMapper.convertValue(hit, new TypeReference<Map<String, Object>>() {
                    }));
                }
            }
            int total = totalItems(root, items.size());
            return NoonInterfacePullPage.builder()
                    .items(items)
                    .pageNumber(Math.max(1, pageNumber))
                    .totalItems(total)
                    .hasNextPage(Math.max(1, pageNumber) * pageSize < total)
                    .requestCount(1)
                    .build();
        } catch (RuntimeException exception) {
            throw NoonPullProviderFailureMapper.map("sales page query", exception);
        }
    }

    private void requireDateWindow(NoonInterfacePullRequest request) {
        if (request == null || request.getDateFrom() == null || request.getDateTo() == null) {
            throw new IllegalArgumentException("missing sales page query date window");
        }
        LocalDate dateFrom = request.getDateFrom();
        LocalDate dateTo = request.getDateTo();
        if (dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("invalid sales page query date window");
        }
    }

    private JsonNode hitsNode(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return objectMapper.createArrayNode();
        }
        JsonNode hits = root.path("hits");
        if (hits.isArray()) {
            return hits;
        }
        return root.path("data").path("hits");
    }

    private int totalItems(JsonNode root, int fallback) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return fallback;
        }
        JsonNode total = root.path("total");
        if (!total.isMissingNode() && total.canConvertToInt()) {
            return Math.max(fallback, total.asInt(fallback));
        }
        JsonNode nestedTotal = root.path("data").path("total");
        if (!nestedTotal.isMissingNode() && nestedTotal.canConvertToInt()) {
            return Math.max(fallback, nestedTotal.asInt(fallback));
        }
        return fallback;
    }

    private String providerError(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return "provider unavailable: empty sales page query response";
        }
        JsonNode error = root.path("error");
        if (!error.isMissingNode() && !error.isNull() && StringUtils.hasText(error.asText())) {
            return error.asText();
        }
        JsonNode message = root.path("message");
        if (!message.isMissingNode() && !message.isNull() && StringUtils.hasText(message.asText())
                && !hitsNode(root).isArray()) {
            return message.asText();
        }
        return null;
    }

    private Map<String, String> localeHeaders(NoonPullStoreBinding binding) {
        String site = siteCode(binding).toLowerCase(Locale.ROOT);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Project", binding.getProjectCode());
        headers.put("X-Locale", "en-" + site);
        headers.put("X-Lang", "en");
        return headers;
    }

    private String siteCode(NoonPullStoreBinding binding) {
        return binding.getSiteCode() == null ? "AE" : binding.getSiteCode().toUpperCase(Locale.ROOT);
    }
}
