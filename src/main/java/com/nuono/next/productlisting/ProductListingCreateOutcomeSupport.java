package com.nuono.next.productlisting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.util.StringUtils;

final class ProductListingCreateOutcomeSupport {

    private static final int MIN_RELIABLE_NOT_FOUND_CHECKS = 3;
    private static final Duration MIN_NOT_FOUND_CONFIRMATION_AGE = Duration.ofMinutes(2);
    private static final Duration MIN_NOT_FOUND_LOOKUP_INTERVAL = Duration.ofSeconds(30);
    private static final Duration MIN_NOT_FOUND_EVIDENCE_WINDOW = Duration.ofMinutes(2);

    private final ObjectMapper objectMapper;

    ProductListingCreateOutcomeSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ProductListingNoonWriteRequest request(ProductListingTaskRecord task) {
        ProductListingNoonWriteRequest request = new ProductListingNoonWriteRequest();
        request.setOwnerUserId(task.getOwnerUserId());
        request.setStoreCode(task.getStoreCode());
        request.setDraftId(task.getDraftId());
        request.setDryRunTaskId(task.getSourceTaskId());
        request.setRealRunTaskId(task.getId());
        request.setSubmittedBy(task.getSubmittedBy());
        request.setDraft(readDraft(task.getInputSnapshotJson()));
        request.setValidationIssues(readIssues(task.getValidationJson()));
        request.setConfirmation(readConfirmation(task.getConfirmationJson()));
        return request;
    }

    ProductListingNoonWriteResult append(
            ProductListingNoonWriteResult previous,
            ProductListingNoonWriteStepResult lookup
    ) {
        List<ProductListingNoonWriteStepResult> steps = new ArrayList<>();
        if (previous != null && previous.getSteps() != null) {
            steps.addAll(previous.getSteps());
        }
        steps.add(lookup);
        return ProductListingNoonWriteResult.failed(
                previous == null ? "noon_uncertain_write" : previous.getFailureCategory(),
                previous == null ? "noon_create_outcome_unknown" : previous.getFailureCode(),
                previous == null ? "Noon create outcome was unknown." : previous.getFailureMessage(),
                steps
        );
    }

    int notFoundLookupCount(ProductListingNoonWriteResult result) {
        if (result == null || result.getSteps() == null) {
            return 0;
        }
        return (int) result.getSteps().stream()
                .filter(Objects::nonNull)
                .filter(step -> "resolve_create_reference".equalsIgnoreCase(step.getStepKey()))
                .filter(step -> "failed".equalsIgnoreCase(step.getStatus()))
                .filter(step -> "noon_create_reference_not_found".equalsIgnoreCase(
                        step.getFailureCode()))
                .count();
    }

    List<LocalDateTime> reliableNotFoundChecks(ProductListingNoonWriteResult result) {
        if (result == null || result.getSteps() == null) {
            return List.of();
        }
        List<LocalDateTime> reliable = new ArrayList<>();
        for (ProductListingNoonWriteStepResult step : result.getSteps()) {
            if (step == null
                    || !"resolve_create_reference".equalsIgnoreCase(step.getStepKey())
                    || !"failed".equalsIgnoreCase(step.getStatus())
                    || !"noon_create_reference_not_found".equalsIgnoreCase(
                    step.getFailureCode())) {
                continue;
            }
            LocalDateTime checkedAt = externalReferenceTime(
                    step.getExternalReference(), "lookupCheckedAt");
            if (checkedAt != null && (reliable.isEmpty()
                    || !checkedAt.isBefore(reliable.get(reliable.size() - 1)
                    .plus(MIN_NOT_FOUND_LOOKUP_INTERVAL)))) {
                reliable.add(checkedAt);
            }
        }
        return reliable;
    }

    boolean canConfirmNotCreated(
            ProductListingTaskRecord task,
            List<LocalDateTime> reliableChecks
    ) {
        if (task == null || reliableChecks == null
                || reliableChecks.size() < MIN_RELIABLE_NOT_FOUND_CHECKS
                || task.getCompletedAt() == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime first = reliableChecks.get(0);
        LocalDateTime last = reliableChecks.get(reliableChecks.size() - 1);
        return !task.getCompletedAt().plus(MIN_NOT_FOUND_CONFIRMATION_AGE).isAfter(now)
                && !first.plus(MIN_NOT_FOUND_EVIDENCE_WINDOW).isAfter(last)
                && !last.isAfter(now);
    }

    References references(ProductListingNoonWriteResult result) {
        References references = new References();
        if (result != null && result.getSteps() != null) {
            result.getSteps().forEach(step -> references.accept(
                    step == null ? null : step.getExternalReference()));
        }
        return references;
    }

    References references(ProductListingNoonWriteStepResult step) {
        References references = new References();
        references.accept(step == null ? null : step.getExternalReference());
        return references;
    }

    ProductListingDraftCommand readDraft(String json) {
        return readJson(json, ProductListingDraftCommand.class, "draft");
    }

    ProductListingRealRunCommand readConfirmation(String json) {
        return readJson(json, ProductListingRealRunCommand.class, "confirmation");
    }

    ProductListingNoonWriteResult readNoonResult(String json) {
        return !StringUtils.hasText(json)
                ? null
                : readJson(json, ProductListingNoonWriteResult.class, "Noon result");
    }

    List<ProductListingValidationIssue> readIssues(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    json, new TypeReference<List<ProductListingValidationIssue>>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to parse product listing validation payload.", exception);
        }
    }

    String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to persist recovered Noon reference.", exception);
        }
    }

    private <T> T readJson(String json, Class<T> type, String label) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to parse product listing " + label + ".", exception);
        }
    }

    private LocalDateTime externalReferenceTime(
            String externalReference,
            String expectedKey
    ) {
        if (!StringUtils.hasText(externalReference)) {
            return null;
        }
        for (String token : externalReference.split(";")) {
            int separator = token.indexOf('=');
            if (separator <= 0 || !expectedKey.equalsIgnoreCase(
                    token.substring(0, separator).trim())) {
                continue;
            }
            try {
                return LocalDateTime.parse(token.substring(separator + 1).trim());
            } catch (java.time.format.DateTimeParseException ignored) {
                return null;
            }
        }
        return null;
    }

    static final class References {
        private String skuParent;
        private String pskuCode;

        String skuParent() {
            return skuParent;
        }

        String pskuCode() {
            return pskuCode;
        }

        boolean complete() {
            return StringUtils.hasText(skuParent) && StringUtils.hasText(pskuCode);
        }

        private void accept(String value) {
            if (!StringUtils.hasText(value)) {
                return;
            }
            for (String token : value.split(";")) {
                int separator = token.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String key = token.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                String item = token.substring(separator + 1).trim();
                if ("skuparent".equals(key) && StringUtils.hasText(item)) {
                    skuParent = item;
                } else if ("pskucode".equals(key) && StringUtils.hasText(item)) {
                    pskuCode = item;
                }
            }
        }
    }
}
