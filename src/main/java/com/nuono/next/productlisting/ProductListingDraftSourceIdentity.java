package com.nuono.next.productlisting;

import java.util.Objects;
import org.springframework.util.StringUtils;

final class ProductListingDraftSourceIdentity {

    private ProductListingDraftSourceIdentity() {
    }

    static void preserveExisting(
            ProductListingDraftCommand command,
            ProductListingDraftRecord existing
    ) {
        if (command == null || existing == null) {
            return;
        }
        String incomingSourceType = normalized(command.getSourceType());
        String existingSourceType = normalized(existing.getSourceType());
        if (incomingSourceType != null
                && (existingSourceType == null
                || !incomingSourceType.equalsIgnoreCase(existingSourceType))) {
            throw immutableSource();
        }
        if (command.getSourceRefId() != null
                && !Objects.equals(
                        command.getSourceRefId(),
                        existing.getSourceRefId()
                )) {
            throw immutableSource();
        }
        command.setSourceType(existing.getSourceType());
        command.setSourceRefId(existing.getSourceRefId());
    }

    static String resolveType(
            ProductListingDraftCommand command,
            ProductListingDraftRecord existing
    ) {
        if (StringUtils.hasText(command.getSourceType())) {
            return command.getSourceType().trim();
        }
        return existing == null ? null : existing.getSourceType();
    }

    static Long resolveReferenceId(
            ProductListingDraftCommand command,
            ProductListingDraftRecord existing
    ) {
        if (command.getSourceRefId() != null) {
            return command.getSourceRefId();
        }
        return existing == null ? null : existing.getSourceRefId();
    }

    private static String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static IllegalArgumentException immutableSource() {
        return new IllegalArgumentException(
                "Product listing draft source cannot be changed."
        );
    }
}
