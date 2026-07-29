package com.nuono.next.productlisting;

import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * Parses the only durable evidence that a Noon create returned a usable
 * product identity.
 */
public final class ProductListingCreateReferenceEvidence {

    private ProductListingCreateReferenceEvidence() {
    }

    public static References latestConfirmed(
            ProductListingNoonWriteResult result
    ) {
        References latest = References.empty();
        List<ProductListingNoonWriteStepResult> steps =
                result == null ? null : result.getSteps();
        if (steps == null) {
            return latest;
        }
        for (ProductListingNoonWriteStepResult step : steps) {
            References candidate = confirmedStep(step);
            if (candidate.complete()) {
                latest = candidate;
            }
        }
        return latest;
    }

    public static References confirmedStep(
            ProductListingNoonWriteStepResult step
    ) {
        return step == null
                ? References.empty()
                : confirmedStep(
                        step.getStepKey(),
                        step.getStatus(),
                        step.getExternalReference()
                );
    }

    public static References confirmedStep(
            String stepKey,
            String status,
            String externalReference
    ) {
        if (!isCreateReferenceStep(stepKey)
                || !"succeeded".equalsIgnoreCase(normalize(status))
                || !StringUtils.hasText(externalReference)) {
            return References.empty();
        }
        String skuParent = null;
        String pskuCode = null;
        boolean sawSkuParent = false;
        boolean sawPskuCode = false;
        for (String token : externalReference.split(";", -1)) {
            int separator = token == null ? -1 : token.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = normalize(token.substring(0, separator));
            String value = token.substring(separator + 1).trim();
            if ("skuparent".equals(key)) {
                if (sawSkuParent || !StringUtils.hasText(value)) {
                    return References.empty();
                }
                sawSkuParent = true;
                skuParent = value;
            } else if ("pskucode".equals(key)) {
                if (sawPskuCode || !StringUtils.hasText(value)) {
                    return References.empty();
                }
                sawPskuCode = true;
                pskuCode = value;
            }
        }
        if (!sawSkuParent || !sawPskuCode) {
            return References.empty();
        }
        return new References(skuParent, pskuCode);
    }

    private static boolean isCreateReferenceStep(String stepKey) {
        String normalized = normalize(stepKey);
        return "create_product".equals(normalized)
                || "resolve_create_reference".equals(normalized);
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }

    public static final class References {
        private static final References EMPTY = new References(null, null);

        private final String skuParent;
        private final String pskuCode;

        private References(String skuParent, String pskuCode) {
            this.skuParent = skuParent;
            this.pskuCode = pskuCode;
        }

        public static References empty() {
            return EMPTY;
        }

        public String skuParent() {
            return skuParent;
        }

        public String pskuCode() {
            return pskuCode;
        }

        public boolean complete() {
            return StringUtils.hasText(skuParent)
                    && StringUtils.hasText(pskuCode);
        }
    }
}
