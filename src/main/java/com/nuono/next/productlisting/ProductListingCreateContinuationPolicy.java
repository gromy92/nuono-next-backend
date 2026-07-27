package com.nuono.next.productlisting;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

final class ProductListingCreateContinuationPolicy {
    private ProductListingCreateContinuationPolicy() {
    }

    static void requireRecoverable(
            ProductListingNoonWriteResult result,
            Long realRunTaskId,
            String storeCode,
            String partnerSku
    ) {
        if (result == null || result.isSuccess()) {
            throw unsafe();
        }
        if (hasSucceededCreateReference(result)
                || hasResolvedUncertainCreateReference(
                result, realRunTaskId, storeCode, partnerSku)
                || hasUnresolvedUncertainCreate(
                result, realRunTaskId, storeCode, partnerSku)) {
            return;
        }
        throw unsafe();
    }

    static boolean needsReadOnlyReferenceResolution(
            ProductListingNoonWriteResult result,
            Long realRunTaskId,
            String storeCode,
            String partnerSku
    ) {
        return hasUnresolvedUncertainCreate(
                result, realRunTaskId, storeCode, partnerSku);
    }

    static void requireContinuationWriteAllowed(
            ProductListingNoonWriteResult result,
            Long realRunTaskId,
            String storeCode,
            String partnerSku
    ) {
        if (hasSucceededCreateReference(result)
                || hasResolvedUncertainCreateReference(
                result, realRunTaskId, storeCode, partnerSku)) {
            return;
        }
        throw unsafe();
    }

    static boolean hasDurableCreateReference(
            ProductListingNoonWriteResult result,
            Long realRunTaskId,
            String storeCode,
            String partnerSku
    ) {
        return hasSucceededCreateReference(result)
                || hasResolvedUncertainCreateReference(
                result, realRunTaskId, storeCode, partnerSku);
    }

    private static boolean hasSucceededCreateReference(ProductListingNoonWriteResult result) {
        ProductListingNoonWriteStepResult create = latest(result, "create_product");
        return create != null
                && "succeeded".equals(create.getStatus())
                && StringUtils.hasText(create.getExternalReference());
    }

    private static boolean hasResolvedUncertainCreateReference(
            ProductListingNoonWriteResult result,
            Long realRunTaskId,
            String storeCode,
            String partnerSku
    ) {
        int createIndex = uncertainCreateIndex(
                result, realRunTaskId, storeCode, partnerSku);
        int resolvedIndex = latestIndex(result, "resolve_create_reference");
        ProductListingNoonWriteStepResult resolved = stepAt(result, resolvedIndex);
        return createIndex >= 0
                && resolvedIndex > createIndex
                && resolved != null
                && "succeeded".equals(resolved.getStatus())
                && StringUtils.hasText(resolved.getExternalReference());
    }

    private static boolean hasUnresolvedUncertainCreate(
            ProductListingNoonWriteResult result,
            Long realRunTaskId,
            String storeCode,
            String partnerSku
    ) {
        int createIndex = uncertainCreateIndex(
                result, realRunTaskId, storeCode, partnerSku);
        int resolvedIndex = latestIndex(result, "resolve_create_reference");
        ProductListingNoonWriteStepResult resolved = stepAt(result, resolvedIndex);
        return createIndex >= 0
                && (resolvedIndex <= createIndex
                || resolved == null
                || !"succeeded".equals(resolved.getStatus())
                || !StringUtils.hasText(resolved.getExternalReference()));
    }

    private static int uncertainCreateIndex(
            ProductListingNoonWriteResult result,
            Long realRunTaskId,
            String storeCode,
            String partnerSku
    ) {
        int createIndex = latestIndex(result, "create_product");
        ProductListingNoonWriteStepResult create = stepAt(result, createIndex);
        ProductListingNoonWriteStepResult absence = stepAt(result, createIndex - 1);
        boolean safePair = absence != null
                && "pre_create_absence_verified".equals(absence.getStepKey())
                && "succeeded".equals(absence.getStatus())
                && Boolean.FALSE.equals(absence.getWriteMayHaveOccurred())
                && matchesAbsenceProof(
                absence.getExternalReference(),
                realRunTaskId,
                storeCode,
                partnerSku)
                && create != null
                && "failed".equals(create.getStatus())
                && "noon_create_outcome_unknown".equals(create.getFailureCode())
                && (Boolean.TRUE.equals(create.getWriteMayHaveOccurred())
                || Boolean.TRUE.equals(result.getWriteMayHaveOccurred()));
        return safePair ? createIndex : -1;
    }

    private static boolean matchesAbsenceProof(
            String externalReference,
            Long realRunTaskId,
            String storeCode,
            String partnerSku
    ) {
        if (!StringUtils.hasText(externalReference)
                || realRunTaskId == null
                || !StringUtils.hasText(storeCode)
                || !StringUtils.hasText(partnerSku)) {
            return false;
        }
        String[] parts = externalReference.split(";", -1);
        if (parts.length != 4) {
            return false;
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (String part : parts) {
            int separator = part.indexOf('=');
            if (separator <= 0 || separator == part.length() - 1) {
                return false;
            }
            String key = part.substring(0, separator);
            String value = part.substring(separator + 1);
            if (fields.putIfAbsent(key, value) != null) {
                return false;
            }
        }
        if (!storeCode.trim().equals(fields.get("storeCode"))
                || !partnerSku.trim().equals(fields.get("partnerSku"))
                || !String.valueOf(realRunTaskId).equals(fields.get("realRunTaskId"))
                || fields.size() != 4) {
            return false;
        }
        try {
            OffsetDateTime.parse(fields.get("checkedAt"));
            return true;
        } catch (DateTimeParseException | NullPointerException exception) {
            return false;
        }
    }

    private static ProductListingNoonWriteStepResult latest(
            ProductListingNoonWriteResult result,
            String stepKey
    ) {
        return stepAt(result, latestIndex(result, stepKey));
    }

    private static int latestIndex(
            ProductListingNoonWriteResult result,
            String stepKey
    ) {
        List<ProductListingNoonWriteStepResult> steps =
                result == null ? null : result.getSteps();
        if (steps == null) {
            return -1;
        }
        for (int index = steps.size() - 1; index >= 0; index--) {
            ProductListingNoonWriteStepResult step = steps.get(index);
            if (step != null && stepKey.equals(step.getStepKey())) {
                return index;
            }
        }
        return -1;
    }

    private static ProductListingNoonWriteStepResult stepAt(
            ProductListingNoonWriteResult result,
            int index
    ) {
        if (result == null
                || result.getSteps() == null
                || index < 0
                || index >= result.getSteps().size()) {
            return null;
        }
        return result.getSteps().get(index);
    }

    private static IllegalArgumentException unsafe() {
        return new IllegalArgumentException(
                "Product listing continuation requires durable evidence from this task's Noon create request."
        );
    }
}
