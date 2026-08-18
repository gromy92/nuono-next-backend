package com.nuono.next.procurement.aliorder;

import java.util.Set;

/** Business identity shared by all supported ingestion paths for the same 1688 order. */
final class Ali1688HistoricalOrderSourceIdentity {
    private static final Set<String> PROVIDER_FAMILY = Set.of(
            "ALI1688_OPEN_API",
            "ALI1688_EXCEL_LOCAL",
            "ALI1688_EXCEL_UPLOAD"
    );

    private Ali1688HistoricalOrderSourceIdentity() {}

    static boolean compatible(String existingProviderCode, String incomingProviderCode) {
        return PROVIDER_FAMILY.contains(existingProviderCode)
                && PROVIDER_FAMILY.contains(incomingProviderCode);
    }
}
