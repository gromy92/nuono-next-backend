package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.infrastructure.mapper.ProductGroupMapper;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.product.noon.NoonProductGateway;
import com.nuono.next.product.noon.ProductNoonAdapter;
import java.math.BigDecimal;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class ProductGroupPublishService {

    private static final String ZSKU_UPSERT_URL =
            NoonProductGateway.ZSKU_UPSERT_URL;
    private static final String CATPLAT_SKU_CACHE_URL =
            NoonProductGateway.CATPLAT_SKU_CACHE_URL;
    private static final String GROUP_UPSERT_URL =
            NoonProductGateway.GROUP_UPSERT_URL;

    private final ProductGroupMapper productGroupMapper;
    private final ObjectMapper objectMapper;
    private final ProductNoonAdapter productNoonAdapter;

    public ProductGroupPublishService(
            ProductGroupMapper productGroupMapper,
            ObjectMapper objectMapper,
            ProductNoonAdapter productNoonAdapter
    ) {
        this.productGroupMapper = productGroupMapper;
        this.objectMapper = objectMapper;
        this.productNoonAdapter = productNoonAdapter;
    }

    public void publishGroupChanges(
            NoonSession session,
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            Long ownerUserId,
            String storeCode
    ) {
        boolean writeSubmitted = false;
        try {
            writeSubmitted = publishGroupMemberCreates(session, draft, baseline, ownerUserId, storeCode) || writeSubmitted;
            writeSubmitted = publishGroupMemberDeletes(session, draft, baseline) || writeSubmitted;
            writeSubmitted = publishGroupAxisValuesForLanguage(session, draft.getGroup(), baseline.getGroup(), "en") || writeSubmitted;
            writeSubmitted = publishGroupAxisValuesForLanguage(session, draft.getGroup(), baseline.getGroup(), "ar") || writeSubmitted;
        } catch (ProductGroupPartialPublishException exception) {
            throw exception;
        } catch (IllegalStateException exception) {
            if (writeSubmitted) {
                throw partialPublishException(exception);
            }
            throw exception;
        }
    }

    private boolean publishGroupMemberCreates(
            NoonSession session,
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            Long ownerUserId,
            String storeCode
    ) {
        List<String> addedSkuParents = ProductGroupSnapshotSupport.addedGroupMembers(draft.getGroup(), baseline.getGroup());
        if (addedSkuParents.isEmpty()) {
            return false;
        }

        Map<String, Object> draftGroup = draft.getGroup() != null ? draft.getGroup() : Map.of();
        Map<String, Object> baselineGroup = baseline.getGroup() != null ? baseline.getGroup() : Map.of();
        String skuGroup = ProductGroupSnapshotSupport.firstNonBlank(
                ProductGroupSnapshotSupport.textValue(baselineGroup.get("skuGroup")),
                ProductGroupSnapshotSupport.textValue(draftGroup.get("skuGroup"))
        );
        String partnerRef = ProductGroupSnapshotSupport.firstNonBlank(
                ProductGroupSnapshotSupport.textValue(baselineGroup.get("groupRef")),
                ProductGroupSnapshotSupport.textValue(draftGroup.get("groupRef"))
        );
        requireText(skuGroup, "当前 Group 缺少 skuGroup，暂时不能新增成员。");
        requireText(partnerRef, "当前 Group 缺少 groupRef，暂时不能新增成员。");
        ensureAddedGroupMembersAreUngrouped(ownerUserId, storeCode, partnerRef, addedSkuParents);

        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode groupUpdate = body.putObject("groupUpdate");
        ObjectNode groupNode = groupUpdate.putObject("group");
        groupNode.put("skuGroup", skuGroup);
        groupNode.put("partnerRef", partnerRef);
        ArrayNode parentsCreate = groupUpdate.putArray("parentsCreate");
        for (String skuParent : addedSkuParents) {
            parentsCreate.add(skuParent);
        }
        groupUpdate.putArray("parentsDelete");

        JsonNode response = postWriteJson(session, GROUP_UPSERT_URL, body, "Group 新增成员");
        throwIfNoonBusinessError(response, "Group 新增成员", false);
        Map<String, Object> draftIdentity = draft.getIdentity() != null ? draft.getIdentity() : Map.of();
        cacheSkuCatplat(session, ProductGroupSnapshotSupport.textValue(draftIdentity.get("skuParent")));
        for (String skuParent : addedSkuParents) {
            cacheSkuCatplat(session, skuParent);
        }
        return true;
    }

    private void ensureAddedGroupMembersAreUngrouped(
            Long ownerUserId,
            String storeCode,
            String targetGroupRef,
            List<String> addedSkuParents
    ) {
        if (ownerUserId == null || !StringUtils.hasText(storeCode) || addedSkuParents == null || addedSkuParents.isEmpty()) {
            return;
        }
        String normalizedTargetGroupRef = ProductGroupSnapshotSupport.normalize(targetGroupRef);
        for (String skuParent : addedSkuParents) {
            ProductGroupMemberGuardRecord guardRecord = productGroupMapper.selectGroupMemberGuardBySkuParent(
                    ownerUserId,
                    ProductGroupSnapshotSupport.normalize(storeCode),
                    ProductGroupSnapshotSupport.normalize(skuParent)
            );
            if (guardRecord == null || !StringUtils.hasText(guardRecord.getSkuParent())) {
                throw new ProductGroupValidationException("只能添加当前店铺的未分组商品；" + skuParent + " 不在当前商品列表中。");
            }
            String existingGroupRef = ProductGroupSnapshotSupport.normalize(guardRecord.getGroupRef());
            if (StringUtils.hasText(existingGroupRef) && !existingGroupRef.equalsIgnoreCase(normalizedTargetGroupRef)) {
                throw new ProductGroupValidationException("只能添加未分组商品；" + skuParent + " 当前已属于 Group " + existingGroupRef + "。");
            }
        }
    }

    private boolean publishGroupMemberDeletes(
            NoonSession session,
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline
    ) {
        List<String> removedSkuParents = ProductGroupSnapshotSupport.removedGroupMembers(draft.getGroup(), baseline.getGroup());
        if (removedSkuParents.isEmpty()) {
            return false;
        }
        Map<String, Object> draftIdentity = draft.getIdentity() != null ? draft.getIdentity() : Map.of();
        String currentSkuParent = ProductGroupSnapshotSupport.textValue(draftIdentity.get("skuParent"));
        if (StringUtils.hasText(currentSkuParent) && removedSkuParents.contains(currentSkuParent)) {
            throw new IllegalStateException("当前详情商品暂不支持在本页直接 Unlink，请从同 Group 的其它商品详情移除它。");
        }

        Map<String, Object> draftGroup = draft.getGroup() != null ? draft.getGroup() : Map.of();
        Map<String, Object> baselineGroup = baseline.getGroup() != null ? baseline.getGroup() : Map.of();
        String skuGroup = ProductGroupSnapshotSupport.firstNonBlank(
                ProductGroupSnapshotSupport.textValue(baselineGroup.get("skuGroup")),
                ProductGroupSnapshotSupport.textValue(draftGroup.get("skuGroup"))
        );
        String partnerRef = ProductGroupSnapshotSupport.firstNonBlank(
                ProductGroupSnapshotSupport.textValue(baselineGroup.get("groupRef")),
                ProductGroupSnapshotSupport.textValue(draftGroup.get("groupRef"))
        );
        requireText(skuGroup, "当前 Group 缺少 skuGroup，暂时不能 Unlink。");
        requireText(partnerRef, "当前 Group 缺少 groupRef，暂时不能 Unlink。");

        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode groupUpdate = body.putObject("groupUpdate");
        ObjectNode groupNode = groupUpdate.putObject("group");
        groupNode.put("skuGroup", skuGroup);
        groupNode.put("partnerRef", partnerRef);
        ArrayNode parentsDelete = groupUpdate.putArray("parentsDelete");
        for (String skuParent : removedSkuParents) {
            parentsDelete.add(skuParent);
        }
        groupUpdate.putArray("parentsCreate");

        JsonNode response = postWriteJson(session, GROUP_UPSERT_URL, body, "Group Unlink");
        throwIfNoonBusinessError(response, "Group Unlink", false);
        cacheSkuCatplat(session, currentSkuParent);
        for (String skuParent : removedSkuParents) {
            cacheSkuCatplat(session, skuParent);
        }
        return true;
    }

    private boolean publishGroupAxisValuesForLanguage(
            NoonSession session,
            Map<String, Object> draftGroup,
            Map<String, Object> baselineGroup,
            String lang
    ) {
        boolean writeSubmitted = false;
        Map<String, Map<String, Object>> changesBySkuParent =
                ProductGroupSnapshotSupport.groupAxisValueChanges(draftGroup, baselineGroup, lang);
        for (Map.Entry<String, Map<String, Object>> entry : changesBySkuParent.entrySet()) {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("skuParent", entry.getKey());
            body.put("lang", lang);
            ObjectNode attributes = body.putObject("attributes");
            for (Map.Entry<String, Object> attribute : entry.getValue().entrySet()) {
                Object value = attribute.getValue();
                if (value == null) {
                    attributes.put(attribute.getKey(), "#del#");
                } else {
                    setObjectNodeValue(attributes, attribute.getKey(), value);
                }
            }
            body.putArray("variants");
            if (attributes.size() == 0) {
                continue;
            }
            boolean priorSuccessfulWrite = writeSubmitted;
            JsonNode response = postWriteJson(session, ZSKU_UPSERT_URL, body, "Group 轴属性写回");
            throwIfNoonBusinessError(response, "Group 轴属性写回", priorSuccessfulWrite);
            writeSubmitted = true;
            cacheSkuCatplat(session, entry.getKey());
        }
        return writeSubmitted;
    }

    private void cacheSkuCatplat(NoonSession session, String skuParent) {
        if (!StringUtils.hasText(skuParent)) {
            return;
        }
        ObjectNode cacheBody = objectMapper.createObjectNode();
        cacheBody.put("skuParent", skuParent);
        try {
            postWriteJsonThroughAdapter(session, CATPLAT_SKU_CACHE_URL, cacheBody);
        } catch (IllegalStateException exception) {
            throw partialPublishException("Group 写回后刷新 Noon 缓存结果未知：" + shrink(exception.getMessage()), exception);
        }
    }

    private JsonNode postWriteJson(NoonSession session, String url, ObjectNode body, String action) {
        try {
            return postWriteJsonThroughAdapter(session, url, body);
        } catch (IllegalStateException exception) {
            if (isNoonWriteResultUnknown(exception)) {
                throw partialPublishException(action + " 请求结果未知：" + shrink(exception.getMessage()), exception);
            }
            throw exception;
        }
    }

    private JsonNode postWriteJsonThroughAdapter(NoonSession session, String url, ObjectNode body) {
        if (productNoonAdapter == null) {
            return session.postWriteJson(url, body, true);
        }
        return productNoonAdapter.postWriteJson(session, url, body, true);
    }

    private void throwIfNoonBusinessError(JsonNode response, String action, boolean priorSuccessfulWrite) {
        String message = ProductGroupSnapshotSupport.firstNonBlank(
                text(response, "error"),
                text(response, "detail")
        );
        String normalizedMessage = StringUtils.hasText(message) ? message.trim() : null;
        if (StringUtils.hasText(normalizedMessage)
                && !"false".equalsIgnoreCase(normalizedMessage)
                && !"0".equals(normalizedMessage)
                && !"{}".equals(normalizedMessage)
                && !"[]".equals(normalizedMessage)) {
            if (priorSuccessfulWrite) {
                throw partialPublishException(action + " 失败：" + message, null);
            }
            throw new IllegalStateException(action + " 失败：" + message);
        }
    }

    private void setObjectNodeValue(ObjectNode target, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Boolean) {
            target.put(key, (Boolean) value);
            return;
        }
        if (value instanceof Integer) {
            target.put(key, (Integer) value);
            return;
        }
        if (value instanceof Long) {
            target.put(key, (Long) value);
            return;
        }
        if (value instanceof Float) {
            target.put(key, (Float) value);
            return;
        }
        if (value instanceof Double) {
            target.put(key, (Double) value);
            return;
        }
        if (value instanceof BigDecimal) {
            target.put(key, (BigDecimal) value);
            return;
        }
        target.put(key, String.valueOf(value));
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.path(field).isNull()) {
            return null;
        }
        String text = node.path(field).asText(null);
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private ProductGroupPartialPublishException partialPublishException(Throwable cause) {
        return partialPublishException("Group 写回可能已部分提交到 Noon，请先从 Noon 同步后确认结果。", cause);
    }

    private ProductGroupPartialPublishException partialPublishException(String message, Throwable cause) {
        return new ProductGroupPartialPublishException(message, cause);
    }

    private boolean isNoonWriteResultUnknown(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof HttpTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        String message = shrink(exception != null ? exception.getMessage() : null).toLowerCase();
        return message.contains("timed out")
                || message.contains("timeout")
                || message.contains("connection reset")
                || message.contains("broken pipe")
                || message.contains("eof")
                || message.contains("closed")
                || message.contains("goaway");
    }

    private String shrink(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.trim();
        return text.length() <= 220 ? text : text.substring(0, 220) + "...";
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }
}
