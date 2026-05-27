package com.nuono.next.product;

@FunctionalInterface
public interface ProductNoonSnapshotFetcher {

    ProductMasterSnapshotView fetch(
            ProductMasterFetchCommand command,
            String reason,
            ProductMasterSnapshotView siteOfferReuseSeed
    );
}
