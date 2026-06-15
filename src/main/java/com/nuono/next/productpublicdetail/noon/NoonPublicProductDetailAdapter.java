package com.nuono.next.productpublicdetail.noon;

public interface NoonPublicProductDetailAdapter {
    NoonPublicProductDetailResult fetch(NoonPublicProductDetailRequest request);

    default String adapterVersion() {
        return "noon-public-detail-search-v1";
    }
}
