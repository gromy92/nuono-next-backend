package com.nuono.next.productlisting;

import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

final class ProductListingNoonReferences {
    private final String skuParent;
    private final String pskuCode;
    private List<String> uploadedImagePaths = List.of();

    private ProductListingNoonReferences(
            String skuParent,
            String pskuCode
    ) {
        this.skuParent = skuParent;
        this.pskuCode = pskuCode;
    }

    static ProductListingNoonReferences from(
            ProductListingNoonWriteResult result
    ) {
        ProductListingCreateReferenceEvidence.References confirmed =
                ProductListingCreateReferenceEvidence.latestConfirmed(result);
        ProductListingNoonReferences references =
                new ProductListingNoonReferences(
                        confirmed.skuParent(),
                        confirmed.pskuCode()
                );
        if (result == null || result.getSteps() == null) {
            return references;
        }
        for (ProductListingNoonWriteStepResult step : result.getSteps()) {
            references.acceptUploadedImagePaths(
                    step == null ? null : step.getExternalReference()
            );
        }
        return references;
    }

    static ProductListingNoonReferences requireComplete(
            ProductListingNoonWriteResult result
    ) {
        ProductListingNoonReferences references = from(result);
        if (!StringUtils.hasText(references.skuParent)
                || !StringUtils.hasText(references.pskuCode)) {
            throw new IllegalArgumentException(
                    "Product listing real-run task is missing a complete Noon create reference."
            );
        }
        return references;
    }

    String skuParent() {
        return skuParent;
    }

    String pskuCode() {
        return pskuCode;
    }

    List<String> uploadedImagePaths() {
        return uploadedImagePaths;
    }

    private void acceptUploadedImagePaths(String externalReference) {
        if (!StringUtils.hasText(externalReference)) {
            return;
        }
        for (String token : externalReference.split(";")) {
            int separator = token.indexOf('=');
            if (separator <= 0
                    || !"uploadedImagePaths".equals(
                    token.substring(0, separator).trim())) {
                continue;
            }
            String value = token.substring(separator + 1).trim();
            if (!StringUtils.hasText(value)) {
                continue;
            }
            List<String> paths = new ArrayList<>();
            for (String path : value.split(",")) {
                if (StringUtils.hasText(path)) {
                    paths.add(path.trim());
                }
            }
            uploadedImagePaths = List.copyOf(paths);
        }
    }
}
