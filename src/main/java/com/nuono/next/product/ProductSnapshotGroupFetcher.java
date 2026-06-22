package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.product.noon.NoonProductGateway;
import com.nuono.next.product.noon.ProductNoonAdapter;
import java.util.List;
import org.springframework.util.StringUtils;

class ProductSnapshotGroupFetcher {

    private final ObjectMapper objectMapper;
    private final ProductNoonAdapter productNoonAdapter;
    private final ProductSnapshotSectionBuilder productSnapshotSectionBuilder;

    ProductSnapshotGroupFetcher(
            ObjectMapper objectMapper,
            ProductNoonAdapter productNoonAdapter,
            ProductSnapshotSectionBuilder productSnapshotSectionBuilder
    ) {
        this.objectMapper = objectMapper;
        this.productNoonAdapter = productNoonAdapter;
        this.productSnapshotSectionBuilder = productSnapshotSectionBuilder;
    }

    ProductSnapshotGroupFetchResult fetch(
            NoonSession session,
            String skuParent,
            String productFulltype,
            String brand,
            List<String> warnings,
            StageRecorder stageRecorder
    ) {
        long stageStartedAt = System.nanoTime();
        JsonNode groupCurrentNode = safeGet(
                session,
                NoonProductGateway.GROUP_CURRENT_URL_PREFIX + skuParent,
                true,
                warnings,
                "读取当前 group 失败"
        );
        recordStage(stageRecorder, "group.current", stageStartedAt);
        String resolvedSkuGroup = null;
        if (groupCurrentNode.isObject()) {
            resolvedSkuGroup = text(groupCurrentNode, "sku_group");
        }

        JsonNode groupDetailNode = MissingNode.getInstance();
        if (StringUtils.hasText(resolvedSkuGroup)) {
            ObjectNode groupBody = objectMapper.createObjectNode();
            ArrayNode groupCodes = groupBody.putArray("zskuGroup");
            groupCodes.add(resolvedSkuGroup);
            stageStartedAt = System.nanoTime();
            groupDetailNode = safePost(
                    session,
                    NoonProductGateway.GROUP_DETAIL_URL,
                    groupBody,
                    true,
                    warnings,
                    "读取 group 详情失败"
            );
            recordStage(stageRecorder, "group.detail", stageStartedAt);
        }

        JsonNode groupParentAttributesNode = MissingNode.getInstance();
        ObjectNode groupParentAttributesBody =
                productSnapshotSectionBuilder.buildGroupParentAttributeFetchBody(groupDetailNode, resolvedSkuGroup);
        if (groupParentAttributesBody != null) {
            stageStartedAt = System.nanoTime();
            groupParentAttributesNode = safePost(
                    session,
                    NoonProductGateway.ZSKU_RETRIEVE_URL,
                    groupParentAttributesBody,
                    true,
                    warnings,
                    "读取 group 轴属性失败"
            );
            recordStage(stageRecorder, "group.parentAttributes", stageStartedAt);
        }

        JsonNode groupListNode = MissingNode.getInstance();
        if (StringUtils.hasText(productFulltype) && StringUtils.hasText(brand)) {
            ObjectNode groupListBody = objectMapper.createObjectNode();
            groupListBody.put("fulltype", productFulltype);
            groupListBody.put("brand", brand);
            stageStartedAt = System.nanoTime();
            groupListNode = safePost(
                    session,
                    NoonProductGateway.GROUP_LIST_URL,
                    groupListBody,
                    true,
                    warnings,
                    "读取候选 group 列表失败"
            );
            recordStage(stageRecorder, "group.list", stageStartedAt);
        }

        return new ProductSnapshotGroupFetchResult(
                groupCurrentNode,
                groupDetailNode,
                groupParentAttributesNode,
                groupListNode,
                resolvedSkuGroup
        );
    }

    private JsonNode safeGet(
            NoonSession session,
            String url,
            boolean withProject,
            List<String> warnings,
            String warningPrefix
    ) {
        try {
            return productNoonAdapter.getJson(session, url, withProject);
        } catch (IllegalStateException exception) {
            warnings.add(warningPrefix + "：" + noonFailureMessage(exception));
            return MissingNode.getInstance();
        }
    }

    private JsonNode safePost(
            NoonSession session,
            String url,
            JsonNode body,
            boolean withProject,
            List<String> warnings,
            String warningPrefix
    ) {
        try {
            return productNoonAdapter.postJson(session, url, body, withProject);
        } catch (IllegalStateException exception) {
            warnings.add(warningPrefix + "：" + noonFailureMessage(exception));
            return MissingNode.getInstance();
        }
    }

    private String noonFailureMessage(RuntimeException exception) {
        if (productNoonAdapter == null) {
            return shrink(exception.getMessage());
        }
        return shrink(productNoonAdapter.userMessage(exception));
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode valueNode = node.path(field);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }
        String value = valueNode.isValueNode() ? valueNode.asText() : valueNode.toString();
        return StringUtils.hasText(value) ? value : null;
    }

    private void recordStage(StageRecorder stageRecorder, String stageName, long startedAt) {
        if (stageRecorder != null) {
            stageRecorder.record(stageName, startedAt);
        }
    }

    private String shrink(String value) {
        if (!StringUtils.hasText(value)) {
            return "未返回更多错误信息";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > 160 ? normalized.substring(0, 160) + "..." : normalized;
    }

    @FunctionalInterface
    interface StageRecorder {
        void record(String stageName, long startedAt);
    }
}
