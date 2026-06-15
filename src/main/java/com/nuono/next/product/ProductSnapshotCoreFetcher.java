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

class ProductSnapshotCoreFetcher {

    private final ObjectMapper objectMapper;
    private final ProductNoonAdapter productNoonAdapter;

    ProductSnapshotCoreFetcher(ObjectMapper objectMapper, ProductNoonAdapter productNoonAdapter) {
        this.objectMapper = objectMapper;
        this.productNoonAdapter = productNoonAdapter;
    }

    ProductSnapshotCoreFetchResult fetch(
            NoonSession session,
            String skuParent,
            List<String> warnings,
            StageRecorder stageRecorder
    ) {
        long stageStartedAt = System.nanoTime();
        JsonNode whoamiNode = safeGet(
                session,
                NoonProductGateway.WHOAMI_URL,
                false,
                warnings,
                "读取 whoami 失败"
        );
        recordStage(stageRecorder, "whoami", stageStartedAt);

        ObjectNode retrieveBody = objectMapper.createObjectNode();
        ArrayNode skuParentsNode = retrieveBody.putArray("skuParents");
        skuParentsNode.add(skuParent);
        retrieveBody.putArray("attributeCodes");
        stageStartedAt = System.nanoTime();
        JsonNode retrieveRoot = productNoonAdapter.postJson(
                session,
                NoonProductGateway.ZSKU_RETRIEVE_URL,
                retrieveBody,
                true
        );
        recordStage(stageRecorder, "zsku.retrieve", stageStartedAt);
        JsonNode productNode = retrieveRoot.path(skuParent);
        if (productNode.isMissingNode() || productNode.isNull()) {
            throw new IllegalStateException("Noon 没有返回 skuParent=" + skuParent + " 的商品快照。");
        }

        ObjectNode variantBody = objectMapper.createObjectNode();
        variantBody.put("zskuParent", skuParent);
        stageStartedAt = System.nanoTime();
        JsonNode variantInfoNode = safePost(
                session,
                NoonProductGateway.VARIANT_INFO_URL,
                variantBody,
                true,
                warnings,
                "读取尺码与变体信息失败"
        );
        recordStage(stageRecorder, "variant.info", stageStartedAt);

        return new ProductSnapshotCoreFetchResult(whoamiNode, productNode, variantInfoNode);
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
