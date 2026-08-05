package com.nuono.next.productpublicdetail.datapull;

import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.infrastructure.mapper.Dp05RuntimeMapper;
import com.nuono.next.productpublicdetail.ProductPublicDetailCandidate;
import java.util.Objects;

/** Production cursor Adapter backed by the product projection. */
public final class MyBatisDp05ProductCursor implements Dp05ProductCursor {

    private final Dp05RuntimeMapper mapper;

    public MyBatisDp05ProductCursor(Dp05RuntimeMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public ProductPublicDetailCandidate next(DataPullScope scope, long afterOfferId) {
        DataPullScope nonNullScope = Objects.requireNonNull(scope, "scope");
        if (afterOfferId < 0L) {
            throw new IllegalArgumentException("afterOfferId must not be negative");
        }
        Long logicalStoreId = Objects.requireNonNull(
                nonNullScope.getLogicalStoreId(),
                "DP05 scope.logicalStoreId"
        );
        return mapper.selectCandidateAfter(
                nonNullScope.getOwnerUserId(),
                logicalStoreId,
                nonNullScope.getStoreCode(),
                nonNullScope.getSiteCode(),
                afterOfferId
        );
    }
}
