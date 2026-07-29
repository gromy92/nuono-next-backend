package com.nuono.next.productlisting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.noonpull.NoonPullGatewaySession;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.util.StringUtils;

final class ProductListingNoonCreateExecutor {

    private final ObjectMapper objectMapper;
    private final ProductListingWriteAuthRecovery writeAuthRecovery;
    private final Function<JsonNode, String> failureMessageResolver;

    ProductListingNoonCreateExecutor(
            ObjectMapper objectMapper,
            ProductListingWriteAuthRecovery writeAuthRecovery,
            Function<JsonNode, String> failureMessageResolver
    ) {
        this.objectMapper = objectMapper;
        this.writeAuthRecovery = writeAuthRecovery;
        this.failureMessageResolver = failureMessageResolver;
    }

    Result execute(
            ProductListingNoonWriteRequest request,
            NoonPullGatewaySession session,
            String url,
            JsonNode body,
            Map<String, String> headers,
            List<ProductListingNoonWriteStepResult> steps
    ) {
        ProductListingNoonWriteStepResult createStep =
                ProductListingNoonCheckpoint.pendingCreateStep();
        steps.add(createStep);
        ProductListingNoonCheckpoint.persistUnknownCreateIntent(
                request, steps);
        JsonNode response = post(createStep, session, url, body, headers);
        String skuParent;
        String pskuCode;
        try {
            skuParent = requiredText(
                    response, "/products/0/parent/skuParent", "skuParent");
            pskuCode = requiredText(
                    response, "/products/0/children/0/pskuCode", "pskuCode");
        } catch (RuntimeException exception) {
            ProductListingNoonCheckpoint.markCreateOutcomeUnknown(
                    createStep, exception.getMessage());
            throw exception;
        }
        createStep.setExternalReference(
                externalReference(skuParent, pskuCode));
        ProductListingNoonCheckpoint.persistWriteProgress(request, steps);
        return new Result(skuParent, pskuCode);
    }

    private JsonNode post(
            ProductListingNoonWriteStepResult step,
            NoonPullGatewaySession session,
            String url,
            JsonNode body,
            Map<String, String> headers
    ) {
        try {
            JsonNode response = ProductListingNoonCallGuard.requireAuthorized(
                    session.postWriteJson(url, body, true, headers));
            String failureMessage =
                    failureMessageResolver.apply(response);
            if (StringUtils.hasText(failureMessage)) {
                step.setStatus("failed");
                step.setFailureCode("noon_create_rejected");
                step.setFailureMessage(failureMessage);
                step.setWriteMayHaveOccurred(false);
                throw new IllegalStateException(failureMessage);
            }
            step.setStatus("succeeded");
            step.setFailureCode(null);
            step.setFailureMessage(null);
            step.setWriteMayHaveOccurred(true);
            return response == null
                    ? objectMapper.createObjectNode()
                    : response;
        } catch (RuntimeException exception) {
            if (!"noon_create_rejected".equals(step.getFailureCode())) {
                writeAuthRecovery.markWriteFailure(
                        step, "create_product", exception);
            }
            throw exception;
        }
    }

    private String requiredText(
            JsonNode node,
            String pointer,
            String label
    ) {
        String value = node == null
                ? null
                : node.at(pointer).asText(null);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    "Noon create_product response missing " + label + ".");
        }
        return value;
    }

    private String externalReference(
            String skuParent,
            String pskuCode
    ) {
        return "skuParent=" + normalized(skuParent)
                + ";pskuCode=" + normalized(pskuCode);
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    static final class Result {
        private final String skuParent;
        private final String pskuCode;

        private Result(String skuParent, String pskuCode) {
            this.skuParent = skuParent;
            this.pskuCode = pskuCode;
        }

        String skuParent() {
            return skuParent;
        }

        String pskuCode() {
            return pskuCode;
        }
    }
}
