package com.nuono.next.productpublicdetail.datapull;

import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.productpublicdetail.ProductPublicDetailCandidate;
import java.util.Objects;

/** Exact scope and product identity passed to one DP-05 provider call. */
public final class Dp05FetchRequest {

    private final DataPullScope scope;
    private final ProductPublicDetailCandidate candidate;

    public Dp05FetchRequest(DataPullScope scope, ProductPublicDetailCandidate candidate) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.candidate = Objects.requireNonNull(candidate, "candidate");
    }

    public DataPullScope getScope() {
        return scope;
    }

    public ProductPublicDetailCandidate getCandidate() {
        return candidate;
    }
}
