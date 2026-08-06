package com.nuono.next.productpublicdetail.datapull;

import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.productpublicdetail.ProductPublicDetailCandidate;

/** One-row local cursor read for the current active DP-05 product set. */
public interface Dp05ProductCursor {

    ProductPublicDetailCandidate next(DataPullScope scope, long afterOfferId);
}
