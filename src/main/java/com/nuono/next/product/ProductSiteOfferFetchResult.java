package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

class ProductSiteOfferFetchResult {

    private final List<Map<String, Object>> siteOffers;

    private final JsonNode referencePricingNode;

    private final JsonNode referenceStockNode;

    ProductSiteOfferFetchResult(
            List<Map<String, Object>> siteOffers,
            JsonNode referencePricingNode,
            JsonNode referenceStockNode
    ) {
        this.siteOffers = siteOffers;
        this.referencePricingNode = referencePricingNode;
        this.referenceStockNode = referenceStockNode;
    }

    List<Map<String, Object>> getSiteOffers() {
        return siteOffers;
    }

    JsonNode getReferencePricingNode() {
        return referencePricingNode;
    }

    JsonNode getReferenceStockNode() {
        return referenceStockNode;
    }
}
