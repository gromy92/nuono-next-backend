package com.nuono.next.noonpull;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
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
@ConditionalOnProperty(prefix = "nuono.noon.pull.real-provider", name = "enabled", havingValue = "true")
public class RealNoonProductInterfaceSmokeProvider implements NoonProductInterfaceSmokeProvider {
    private final ObjectMapper objectMapper;
    private final NoonPullStoreBindingResolver bindingResolver;
    private final NoonPullGatewaySessionFactory sessionFactory;
    private final String offerListUrl;
    private final int pageSize;

    public RealNoonProductInterfaceSmokeProvider(
            ObjectMapper objectMapper,
            NoonPullStoreBindingResolver bindingResolver,
            NoonPullGatewaySessionFactory sessionFactory,
            @Value("${nuono.noon.pull.real-provider.product.offer-list-url:https://noon-catalog.noon.partners/_svc/mp-noon-catalog-api-rocket/offer/list/noon}") String offerListUrl,
            @Value("${nuono.noon.pull.real-provider.product.page-size:100}") int pageSize
    ) {
        this.objectMapper = objectMapper;
        this.bindingResolver = bindingResolver;
        this.sessionFactory = sessionFactory;
        this.offerListUrl = offerListUrl;
        this.pageSize = Math.max(1, pageSize);
    }

    @Override
    public NoonInterfacePullPage fetchPage(NoonInterfacePullRequest request, int pageNumber) {
        try {
            NoonPullStoreBinding binding = bindingResolver.resolve(request);
            NoonPullGatewaySession session = sessionFactory.login(binding);
            ObjectNode body = objectMapper.createObjectNode();
            body.put("page", Math.max(1, pageNumber));
            body.put("per_page", pageSize);
            body.put("noon_store_code", binding.getStoreCode());
            body.put("noonChannelType", "noon");

            JsonNode root = session.postJson(offerListUrl, body, true, localeHeaders(binding));
            String providerError = providerError(root);
            if (StringUtils.hasText(providerError)) {
                throw NoonPullProviderFailureMapper.explicit("product offer list", providerError);
            }

            JsonNode data = root.path("data");
            JsonNode hits = data.path("hits");
            List<Map<String, Object>> items = new ArrayList<>();
            if (hits.isArray()) {
                for (JsonNode hit : hits) {
                    items.add(objectMapper.convertValue(hit, new TypeReference<Map<String, Object>>() {
                    }));
                }
            }
            int total = data.path("total").asInt(items.size());
            if (total <= 0) {
                total = items.size();
            }
            return NoonInterfacePullPage.builder()
                    .items(items)
                    .pageNumber(Math.max(1, pageNumber))
                    .totalItems(total)
                    .hasNextPage(Math.max(1, pageNumber) * pageSize < total)
                    .requestCount(1)
                    .build();
        } catch (RuntimeException exception) {
            throw NoonPullProviderFailureMapper.map("product offer list", exception);
        }
    }

    private String providerError(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return "provider unavailable: empty product list response";
        }
        JsonNode error = root.path("error");
        if (!error.isMissingNode() && !error.isNull() && StringUtils.hasText(error.asText())) {
            return error.asText();
        }
        return null;
    }

    private Map<String, String> localeHeaders(NoonPullStoreBinding binding) {
        String site = binding.getSiteCode() == null ? "ae" : binding.getSiteCode().toLowerCase(Locale.ROOT);
        return Map.of("X-Locale", "en-" + site, "X-Lang", "en");
    }
}
