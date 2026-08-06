package com.nuono.next.productpublicdetail.datapull;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.productpublicdetail.ProductPublicDetailCandidate;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailResult;
import java.time.LocalDate;

/** Short, idempotent local fact transaction for one product/day. */
public interface Dp05ProductDetailFactWriter {

    enum ApplyResult {
        APPLIED,
        STALE_FENCE
    }

    ApplyResult apply(
            DataPullTask task,
            ProductPublicDetailCandidate candidate,
            NoonPublicProductDetailResult result,
            LocalDate factDate,
            long actorUserId
    );
}
