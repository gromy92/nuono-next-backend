package com.nuono.next.competitoranalysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CompetitorProductListingLog {
    private static final Logger log =
            LoggerFactory.getLogger(CompetitorProductListingIO.class);

    private CompetitorProductListingLog() {
    }

    static void failure(
            String phase,
            CompetitorWatchProductRow watchProduct,
            CompetitorProductDetailTarget target,
            CompetitorProductRow product,
            Long taskId,
            RuntimeException error
    ) {
        log.warn(
                "competitor list coverage {} failed watchProductId={} subjectType={} competitorProductId={} noonProductCode={} taskId={} error={}",
                phase,
                watchProduct == null ? null : watchProduct.getId(),
                target == null ? null : target.getSubjectType(),
                product == null ? null : product.getId(),
                target == null ? null : target.getNoonProductCode(),
                taskId,
                error.getMessage(),
                error
        );
    }
}
