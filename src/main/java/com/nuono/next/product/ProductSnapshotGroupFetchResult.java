package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

class ProductSnapshotGroupFetchResult {

    private final JsonNode groupCurrentNode;
    private final JsonNode groupDetailNode;
    private final JsonNode groupParentAttributesNode;
    private final JsonNode groupListNode;
    private final String skuGroup;

    ProductSnapshotGroupFetchResult(
            JsonNode groupCurrentNode,
            JsonNode groupDetailNode,
            JsonNode groupParentAttributesNode,
            JsonNode groupListNode,
            String skuGroup
    ) {
        this.groupCurrentNode = nonNullNode(groupCurrentNode);
        this.groupDetailNode = nonNullNode(groupDetailNode);
        this.groupParentAttributesNode = nonNullNode(groupParentAttributesNode);
        this.groupListNode = nonNullNode(groupListNode);
        this.skuGroup = skuGroup;
    }

    JsonNode getGroupCurrentNode() {
        return groupCurrentNode;
    }

    JsonNode getGroupDetailNode() {
        return groupDetailNode;
    }

    JsonNode getGroupParentAttributesNode() {
        return groupParentAttributesNode;
    }

    JsonNode getGroupListNode() {
        return groupListNode;
    }

    String getSkuGroup() {
        return skuGroup;
    }

    private static JsonNode nonNullNode(JsonNode node) {
        return node == null ? MissingNode.getInstance() : node;
    }
}
