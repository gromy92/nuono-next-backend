package com.nuono.next.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.util.StringUtils;

class ProductDeleteRequestBuilder {

    private final ObjectMapper objectMapper;

    ProductDeleteRequestBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ObjectNode buildDeleteBody(ProductPublishTaskRecord task, ProductMasterSnapshotView preDeleteSnapshot) {
        return buildDeleteBody(resolvePskuCode(task == null ? null : task.getPskuCode(), preDeleteSnapshot));
    }

    ObjectNode buildDeleteBody(String pskuCode) {
        ObjectNode body = objectMapper.createObjectNode();
        if (!StringUtils.hasText(pskuCode)) {
            throw new IllegalStateException("商品删除缺少 pskuCode，不能调用 Noon psku/delete。");
        }
        body.putArray("pskuDelete").addObject().put("pskuCode", pskuCode.trim());
        return body;
    }

    ObjectNode buildUnmapBody(ProductPublishTaskRecord task, ProductMasterSnapshotView preDeleteSnapshot) {
        ObjectNode body = objectMapper.createObjectNode();
        String pskuCode = resolvePskuCode(task == null ? null : task.getPskuCode(), preDeleteSnapshot);
        if (!StringUtils.hasText(pskuCode)) {
            throw new IllegalStateException("商品删除缺少 pskuCode，不能调用 Noon psku/map 解除映射。");
        }
        body.putArray("pskuMap").addObject()
                .put("pskuCode", pskuCode)
                .putNull("catalogSku");
        return body;
    }

    private String resolvePskuCode(String requestedPskuCode, ProductMasterSnapshotView preDeleteSnapshot) {
        return firstNonBlank(
                requestedPskuCode,
                preDeleteSnapshot == null || preDeleteSnapshot.getIdentity() == null
                        ? null
                        : textValue(preDeleteSnapshot.getIdentity().get("pskuCode"))
        );
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }
}
