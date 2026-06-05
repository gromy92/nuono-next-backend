package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

class ProductSnapshotCoreFetchResult {

    private final JsonNode whoamiNode;
    private final JsonNode productNode;
    private final JsonNode variantInfoNode;

    ProductSnapshotCoreFetchResult(
            JsonNode whoamiNode,
            JsonNode productNode,
            JsonNode variantInfoNode
    ) {
        this.whoamiNode = nonNullNode(whoamiNode);
        this.productNode = nonNullNode(productNode);
        this.variantInfoNode = nonNullNode(variantInfoNode);
    }

    JsonNode getWhoamiNode() {
        return whoamiNode;
    }

    JsonNode getProductNode() {
        return productNode;
    }

    JsonNode getVariantInfoNode() {
        return variantInfoNode;
    }

    private static JsonNode nonNullNode(JsonNode node) {
        return node == null ? MissingNode.getInstance() : node;
    }
}
