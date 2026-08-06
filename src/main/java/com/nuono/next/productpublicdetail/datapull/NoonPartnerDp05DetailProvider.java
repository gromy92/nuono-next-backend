package com.nuono.next.productpublicdetail.datapull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.noon.NoonCatalogApiRoutes;
import com.nuono.next.noon.NoonShanghaiBusinessTime;
import com.nuono.next.noonpull.NoonInterfacePullRequest;
import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullGatewaySession;
import com.nuono.next.noonpull.NoonPullGatewaySessionFactory;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import com.nuono.next.noonpull.NoonPullStoreBindingResolver;
import com.nuono.next.productpublicdetail.ProductPublicDetailCandidate;
import com.nuono.next.productpublicdetail.ProductPublicDetailSyncStatus;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailResult;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.StringUtils;

/** One-call exact `offer/list/noon` Adapter used only after frontend NOT_FOUND. */
public final class NoonPartnerDp05DetailProvider implements Dp05ProductDetailProvider {
    public static final String PROVIDER_URL = NoonCatalogApiRoutes.OFFER_LIST_NOON;
    private static final int EXACT_SEARCH_PAGE_SIZE = 100;
    private final ObjectMapper objectMapper;
    private final NoonPullStoreBindingResolver bindingResolver;
    private final NoonPullGatewaySessionFactory sessionFactory;

    public NoonPartnerDp05DetailProvider(ObjectMapper objectMapper,
            NoonPullStoreBindingResolver bindingResolver, NoonPullGatewaySessionFactory sessionFactory) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.bindingResolver = Objects.requireNonNull(bindingResolver, "bindingResolver");
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
    }

    @Override
    public ProviderOutcome<Dp05ProviderValue> fetch(Dp05FetchRequest request) {
        Dp05FetchRequest nonNull = Objects.requireNonNull(request, "request");
        ProductPublicDetailCandidate candidate = nonNull.getCandidate();
        String searchIdentity = firstText(candidate.getPartnerSku(), candidate.getNoonProductCode());
        if (!StringUtils.hasText(searchIdentity)) {
            return ProviderOutcome.success(
                    Dp05ProviderValue.skipBusinessItem("DP05_PARTNER_MISSING_SEARCH_IDENTITY")
            );
        }
        try {
            NoonInterfacePullRequest pullRequest = NoonInterfacePullRequest.builder()
                    .ownerUserId(nonNull.getScope().getOwnerUserId())
                    .storeCode(nonNull.getScope().getStoreCode())
                    .siteCode(nonNull.getScope().getSiteCode())
                    .dataDomain(NoonPullDataDomain.PRODUCT)
                    .requestName("dp05-partner-exact-offer")
                    .targetIdentity(searchIdentity)
                    .build();
            NoonPullStoreBinding binding = bindingResolver.resolve(pullRequest);
            NoonPullGatewaySession session = sessionFactory.openOneShot(binding);
            JsonNode root = session.postJsonOnce(
                    PROVIDER_URL,
                    requestBody(binding, searchIdentity),
                    true,
                    headers(binding)
            );
            return mapResponse(candidate, root);
        } catch (RuntimeException failure) {
            return Dp05PartnerFailureClassifier.classify(failure);
        }
    }

    private ProviderOutcome<Dp05ProviderValue> mapResponse(
            ProductPublicDetailCandidate candidate,
            JsonNode root
    ) {
        if (root == null || !root.isObject()) {
            return ProviderOutcome.contractError("DP05_PARTNER_EMPTY_RESPONSE");
        }
        JsonNode error = root.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            String errorText = error.isTextual() ? error.asText() : error.toString();
            if (StringUtils.hasText(errorText)) {
                return Dp05PartnerFailureClassifier.classifyText(errorText);
            }
        }
        JsonNode data = root.path("data");
        JsonNode hits = data.path("hits");
        if (!data.isObject() || !hits.isArray() || !data.path("total").canConvertToInt()) {
            return ProviderOutcome.contractError("DP05_PARTNER_CONTRACT_ERROR");
        }
        int total = data.path("total").asInt();
        if (total < 0 || total > hits.size()) {
            return ProviderOutcome.contractError("DP05_PARTNER_INCOMPLETE_EXACT_SEARCH");
        }
        List<JsonNode> exact = new ArrayList<>();
        for (JsonNode hit : hits) {
            if (!hit.isObject()) {
                return ProviderOutcome.contractError("DP05_PARTNER_INVALID_HIT");
            }
            if (matches(candidate, hit)) {
                exact.add(hit);
            }
        }
        if (exact.isEmpty()) {
            return ProviderOutcome.notFound("DP05_PARTNER_NOT_FOUND");
        }
        if (exact.size() > 1) {
            return ProviderOutcome.success(
                    Dp05ProviderValue.skipBusinessItem("DP05_PARTNER_IDENTITY_AMBIGUOUS")
            );
        }
        return ProviderOutcome.success(Dp05ProviderValue.fact(toPartial(candidate, exact.get(0))));
    }

    private NoonPublicProductDetailResult toPartial(
            ProductPublicDetailCandidate candidate,
            JsonNode hit
    ) {
        NoonPublicProductDetailResult result = new NoonPublicProductDetailResult();
        result.setStatus(ProductPublicDetailSyncStatus.PARTIAL);
        result.setFailureCode("PARTNER_PARTIAL_DETAIL");
        result.setFailureMessage("Noon Partner exact offer returned the downlisted product facts.");
        result.setNoonProductCode(firstText(
                candidate.getNoonProductCode(),
                text(hit, "csku_parent", "zsku_parent", "sku_parent", "skuParent", "catalog_sku")
        ));
        result.setTitleEn(text(hit, "title", "title_en", "product_title", "name"));
        result.setBrand(text(hit, "brand", "brand_name", "brandName"));
        result.setPriceAmount(decimal(hit, "sale_price", "salePrice", "price", "final_price", "finalPrice"));
        result.setCurrencyCode(text(hit, "currency", "currency_code", "currencyCode"));
        result.setAvailabilityText(text(hit, "status", "status_code", "live_status"));
        result.setMainImageUrl(text(hit, "image_url", "imageUrl", "main_image_url"));
        result.setRawPayloadJson(hit.toString());
        result.setProviderHttpStatus(200);
        result.setProviderSourceUrl(PROVIDER_URL);
        result.setProviderResponseHash(sha256(hit.toString()));
        result.setProviderParserVersion("noon-partner-offer-list-exact-v1");
        result.setFetchedAt(NoonShanghaiBusinessTime.now());
        return result;
    }

    private ObjectNode requestBody(NoonPullStoreBinding binding, String searchIdentity) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("page", 1);
        body.put("per_page", EXACT_SEARCH_PAGE_SIZE);
        body.put("noon_store_code", binding.getStoreCode());
        body.put("noonChannelType", "noon");
        body.put("search", searchIdentity);
        return body;
    }

    private Map<String, String> headers(NoonPullStoreBinding binding) {
        String site = normalize(binding.getSiteCode());
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Project", binding.getProjectCode());
        headers.put("X-Locale", "en-" + site.toLowerCase(Locale.ROOT));
        headers.put("X-Lang", "en");
        headers.put("Country-Code", site);
        headers.put("Id-Partner", binding.getPartnerId());
        return headers;
    }

    private boolean matches(ProductPublicDetailCandidate candidate, JsonNode hit) {
        String partnerSku = normalize(candidate.getPartnerSku());
        String productCode = normalize(firstText(candidate.getNoonProductCode(), candidate.getSkuParent()));
        return (StringUtils.hasText(partnerSku)
                && partnerSku.equals(normalize(text(hit, "partner_sku", "partnerSku", "psku"))))
                || (StringUtils.hasText(productCode)
                && productCode.equals(normalize(text(
                        hit, "csku_parent", "zsku_parent", "sku_parent", "skuParent", "catalog_sku"
                ))));
    }

    private String text(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isContainerNode() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private BigDecimal decimal(JsonNode node, String... fields) {
        String value = text(node, fields);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                hex.append(String.format("%02x", item));
            }
            return hex.toString();
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 is required", failure);
        }
    }
}
