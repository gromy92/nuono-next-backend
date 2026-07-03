package com.nuono.next.productlisting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noonpull.NoonPullGatewaySession;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Primary
@ConditionalOnProperty(
        prefix = "nuono.product-listing.real-write",
        name = "offer-split-write-enabled",
        havingValue = "true"
)
public class RealProductListingOfferStockWriteAdapter implements ProductListingOfferStockWriteAdapter {

    private final ObjectMapper objectMapper;

    public RealProductListingOfferStockWriteAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    @Override
    public ProductListingNoonWriteStepResult writeOfferStock(
            ProductListingOfferStockWriteRequest request,
            NoonPullGatewaySession session,
            ProductListingRealWriteProperties.Endpoints endpoints,
            Map<String, String> headers
    ) {
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey("upsert_offer");
        List<String> written = new ArrayList<>();
        boolean hasUnsupportedWarehouseStock = hasWarehouseStockFields(request);
        try {
            if (request == null || session == null) {
                throw new IllegalArgumentException("Product listing offer/stock write request and session are required.");
            }
            ProductListingRealWriteProperties.Endpoints resolvedEndpoints =
                    endpoints == null ? new ProductListingRealWriteProperties.Endpoints() : endpoints;
            if (request.getOfferNote() != null) {
                postOfferStep(
                        session,
                        resolvedEndpoints.getUpsertOfferNoteUrl(),
                        offerNoteBody(request),
                        headers
                );
                written.add("offer_note");
            }
            if (request.getIsActive() != null) {
                postOfferStep(
                        session,
                        resolvedEndpoints.getUpsertIsActiveUrl(),
                        activationBody(request),
                        headers
                );
                written.add("is_active");
            }
            if (written.isEmpty()) {
                step.setStatus("skipped");
                step.setFailureCode(hasUnsupportedWarehouseStock
                        ? "noon_offer_stock_warehouse_stock_not_supported"
                        : "noon_offer_stock_no_supported_fields");
                step.setFailureMessage(hasUnsupportedWarehouseStock
                        ? "Warehouse, stock quantity, and FBP fields are not written by the current Noon split offer adapter."
                        : "No supported offer fields were present for the Noon split offer adapter.");
                return step;
            }
            step.setStatus("succeeded");
            step.setExternalReference("written=" + String.join(",", written)
                    + (hasUnsupportedWarehouseStock ? ";skipped=warehouse_stock" : ""));
            return step;
        } catch (RuntimeException exception) {
            step.setStatus("failed");
            step.setFailureCode("noon_offer_stock_write_failed");
            step.setFailureMessage(StringUtils.hasText(exception.getMessage())
                    ? exception.getMessage()
                    : "Noon offer split write failed.");
            return step;
        }
    }

    private void postOfferStep(
            NoonPullGatewaySession session,
            String url,
            ObjectNode body,
            Map<String, String> headers
    ) {
        JsonNode response = session.postWriteJson(url, body, false, headers);
        String failureMessage = failureMessage(response);
        if (StringUtils.hasText(failureMessage)) {
            throw new IllegalStateException(failureMessage);
        }
    }

    private ObjectNode offerNoteBody(ProductListingOfferStockWriteRequest request) {
        ObjectNode body = identityBody(request);
        body.put("offerNote", request.getOfferNote() == null ? "" : request.getOfferNote());
        return body;
    }

    private ObjectNode activationBody(ProductListingOfferStockWriteRequest request) {
        ObjectNode body = identityBody(request);
        body.put("isActive", Boolean.TRUE.equals(request.getIsActive()));
        return body;
    }

    private ObjectNode identityBody(ProductListingOfferStockWriteRequest request) {
        if (!StringUtils.hasText(request.getPskuCode())) {
            throw new IllegalArgumentException("Product listing offer write pskuCode is required.");
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("pskuCode", request.getPskuCode());
        putIfHasText(body, "partnerSku", request.getPartnerSku());
        putIfHasText(body, "countryCode", ProductListingNoonHeaders.upper(request.getSiteCode()));
        return body;
    }

    private boolean hasWarehouseStockFields(ProductListingOfferStockWriteRequest request) {
        return request != null
                && (request.getFbp() != null
                || StringUtils.hasText(request.getWarehouseId())
                || StringUtils.hasText(request.getWarehouseCode())
                || request.getQuantity() != null);
    }

    private String failureMessage(JsonNode response) {
        if (response == null || response.isMissingNode() || response.isNull()) {
            return null;
        }
        if (response.path("invalid").asInt(0) > 0) {
            return firstNonBlank(
                    response.path("error").path("partner_error").asText(null),
                    response.path("error").asText(null),
                    "Noon offer split write returned invalid response."
            );
        }
        return firstNonBlank(
                response.path("error").asText(null),
                response.path("message").asText(null)
        );
    }

    private void putIfHasText(ObjectNode target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
