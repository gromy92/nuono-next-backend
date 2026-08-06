package com.nuono.next.productpublicdetail.datapull;

import com.nuono.next.datapull.runtime.ProviderOutcome;

/** One physical provider action returning the runtime's typed outcome model. */
public interface Dp05ProductDetailProvider {

    ProviderOutcome<Dp05ProviderValue> fetch(Dp05FetchRequest request);
}
