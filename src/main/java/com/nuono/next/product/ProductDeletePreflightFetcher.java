package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

class ProductDeletePreflightFetcher {

    private final ProductSnapshotCoreFetcher coreFetcher;
    private final ProductSnapshotSectionBuilder sectionBuilder;

    ProductDeletePreflightFetcher(
            ProductSnapshotCoreFetcher coreFetcher,
            ProductSnapshotSectionBuilder sectionBuilder
    ) {
        this.coreFetcher = coreFetcher;
        this.sectionBuilder = sectionBuilder;
    }

    ProductMasterSnapshotView fetch(NoonSession session, ProductMasterFetchCommand command) {
        if (command == null || !StringUtils.hasText(command.getSkuParent())) {
            throw new IllegalArgumentException("商品删除预检缺少 skuParent。");
        }

        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setMode("product-delete-preflight");
        ProductSnapshotCoreFetchResult core = coreFetcher.fetch(
                session,
                command.getSkuParent().trim(),
                snapshot.getWarnings(),
                null
        );
        JsonNode productNode = core.getProductNode();
        JsonNode commonNode = productNode.path("attributes").path("common");
        Map<String, Object> identity = sectionBuilder.buildIdentity(
                productNode,
                commonNode,
                MissingNode.getInstance(),
                command.getSkuParent(),
                command.getPartnerSku(),
                command.getPskuCode()
        );
        List<Map<String, Object>> variants =
                sectionBuilder.buildVariants(core.getVariantInfoNode(), productNode);
        promoteMappingEvidence(identity, variants);

        putIfHasText(snapshot.getStoreContext(), "storeCode", session.getStoreCode());
        putIfHasText(snapshot.getStoreContext(), "projectCode", session.getProjectCode());
        snapshot.setIdentity(identity);
        snapshot.setVariants(variants);
        snapshot.setReady(true);
        snapshot.setMessage("已读取商品删除所需的身份、映射和存在性证据。");
        return snapshot;
    }

    private void promoteMappingEvidence(
            Map<String, Object> identity,
            List<Map<String, Object>> variants
    ) {
        if (StringUtils.hasText(textValue(identity.get("childSku"))) || variants == null) {
            return;
        }
        for (Map<String, Object> variant : variants) {
            String childSku = variant == null ? null : textValue(variant.get("childSku"));
            if (StringUtils.hasText(childSku)) {
                identity.put("childSku", childSku);
                return;
            }
        }
    }

    private void putIfHasText(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value.trim());
        }
    }

    private String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }
}
