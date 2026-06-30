package com.nuono.next.outboundfee;

import java.util.Optional;

public interface OfficialOutboundFeeProductContextReader {

    Optional<OfficialOutboundFeeProductContext> findContext(
            Long ownerUserId,
            String storeCode,
            String site,
            String skuId
    );

    default Optional<OfficialOutboundFeeProductContext> findContextBySpecSource(
            Long ownerUserId,
            String storeCode,
            String site,
            String skuId,
            String sourceType
    ) {
        return Optional.empty();
    }
}
