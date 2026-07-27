package com.nuono.next.productlisting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noon.NoonAuthenticationFailureClassifier;
import com.nuono.next.noonpull.NoonPullGatewaySession;
import com.nuono.next.noonpull.NoonPullStoreBinding;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

final class ProductListingNoonReadBackVerifier {

    private final ObjectMapper objectMapper;
    private final ProductListingRealWriteProperties properties;
    private final ProductListingNoonReadBackComparator comparator;
    private final ProductListingNoonReadBackValueSupport values;

    ProductListingNoonReadBackVerifier(
            ObjectMapper objectMapper,
            ProductListingRealWriteProperties properties
    ) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.properties = properties == null
                ? new ProductListingRealWriteProperties()
                : properties;
        this.values = new ProductListingNoonReadBackValueSupport();
        this.comparator = new ProductListingNoonReadBackComparator(
                this.properties, this.values);
    }

    ProductListingNoonWriteStepResult verify(
            NoonPullGatewaySession session,
            ProductListingRealWriteProperties.Endpoints endpoints,
            ProductListingDraftCommand draft,
            List<String> expectedImageValues,
            String skuParent,
            String pskuCode,
            NoonPullStoreBinding binding,
            Map<String, String> headers
    ) {
        ProductListingNoonWriteStepResult step =
                new ProductListingNoonWriteStepResult();
        step.setStepKey("verify_noon_readback");
        int maxAttempts = Math.max(1, properties.getReadBackMaxAttempts());
        long retryDelayMillis =
                Math.max(0L, properties.getReadBackRetryDelayMillis());
        RuntimeException lastException = null;
        List<String> lastMismatches = List.of();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            step.setExternalReference(
                    externalReference(skuParent, pskuCode)
                            + ";readBackAttempts=" + attempt);
            try {
                JsonNode product = loadProduct(session, endpoints, skuParent, headers);
                JsonNode offer = loadOffer(session, endpoints, draft, binding, headers);
                JsonNode pricing = loadPricing(
                        session, endpoints, draft, binding, headers);
                List<String> mismatches = comparator.mismatches(
                        draft, expectedImageValues, product, offer, pricing);
                if (mismatches.isEmpty()) {
                    step.setStatus("succeeded");
                    return step;
                }
                lastMismatches = mismatches;
                lastException = null;
            } catch (RuntimeException exception) {
                if (NoonAuthenticationFailureClassifier
                        .isAuthenticationFailure(exception)) {
                    step.setStatus("failed");
                    step.setFailureCode("noon_auth_required");
                    step.setFailureMessage(StringUtils.hasText(exception.getMessage())
                            ? exception.getMessage()
                            : "Noon authorization is required for listing read-back.");
                    return step;
                }
                lastException = exception;
                lastMismatches = List.of();
            }
            if (attempt < maxAttempts) {
                sleep(retryDelayMillis);
            }
        }
        step.setStatus("failed");
        if (!lastMismatches.isEmpty()) {
            step.setFailureCode("noon_listing_readback_incomplete");
            step.setFailureMessage(
                    "Noon listing read-back missing or mismatched fields: "
                            + String.join(", ", lastMismatches));
            return step;
        }
        step.setFailureCode("noon_listing_readback_failed");
        step.setFailureMessage(lastException != null
                && StringUtils.hasText(lastException.getMessage())
                ? lastException.getMessage()
                : "Noon listing read-back failed.");
        return step;
    }

    private JsonNode loadProduct(
            NoonPullGatewaySession session,
            ProductListingRealWriteProperties.Endpoints endpoints,
            String skuParent,
            Map<String, String> headers
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.putArray("skuParents").add(skuParent);
        body.putArray("attributeCodes");
        JsonNode root = session.postJson(
                endpoints.getRetrieveZskuUrl(), body, true, headers);
        JsonNode direct = root.path(skuParent);
        if (!direct.isMissingNode() && !direct.isNull()) {
            return direct;
        }
        JsonNode nested = root.path("data").path(skuParent);
        return nested.isMissingNode() ? direct : nested;
    }

    private JsonNode loadOffer(
            NoonPullGatewaySession session,
            ProductListingRealWriteProperties.Endpoints endpoints,
            ProductListingDraftCommand draft,
            NoonPullStoreBinding binding,
            Map<String, String> headers
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("page", 1);
        body.put("per_page", 100);
        body.put("noon_store_code", binding.getStoreCode());
        body.put("noonChannelType", "noon");
        body.put("search", draft.getPsku());
        JsonNode hits = session.postJson(
                endpoints.getOfferListUrl(), body, true, headers)
                .path("data").path("hits");
        if (hits.isArray()) {
            for (JsonNode hit : hits) {
                if (values.sameText(
                        draft.getPsku(), values.text(hit, "partner_sku"), true)) {
                    return hit;
                }
            }
        }
        return objectMapper.missingNode();
    }

    private JsonNode loadPricing(
            NoonPullGatewaySession session,
            ProductListingRealWriteProperties.Endpoints endpoints,
            ProductListingDraftCommand draft,
            NoonPullStoreBinding binding,
            Map<String, String> headers
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode pskuList = body.putArray("psku_list");
        ObjectNode item = pskuList.addObject();
        item.put("psku", draft.getPsku());
        item.put("country_code", values.upper(binding.getSiteCode()));
        item.put("id_partner", binding.getPartnerId());
        JsonNode root = session.postJson(
                endpoints.getPricingInformationUrl(), body, true, headers);
        JsonNode data = root.path("data");
        if (data.isArray() && !data.isEmpty()) {
            return data.get(0);
        }
        if (root.isArray() && !root.isEmpty()) {
            return root.get(0);
        }
        return objectMapper.missingNode();
    }

    private void sleep(long millis) {
        if (millis <= 0L) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Noon listing read-back retry interrupted.", exception);
        }
    }

    private String externalReference(String skuParent, String pskuCode) {
        return "skuParent=" + values.normalize(skuParent)
                + ";pskuCode=" + values.normalize(pskuCode);
    }
}
