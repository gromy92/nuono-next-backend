package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.infrastructure.mapper.ProductGroupMapper;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class ProductGroupPublishService {

    private static final String ZSKU_UPSERT_URL =
            "https://noon-catalog.noon.partners/_svc/mp-noon-catalog-api-content/catplat/zsku/upsert";
    private static final String CATPLAT_SKU_CACHE_URL =
            "https://noon-catalog.noon.partners/_svc/mp-noon-catalog-api-content/catplat/sku/cache";
    private static final String GROUP_UPSERT_URL =
            "https://noon-catalog.noon.partners/_svc/mp-noon-catalog-api-content/catalog/group/upsert";

    private final ProductGroupMapper productGroupMapper;
    private final ObjectMapper objectMapper;
    private final ProductNoonPublishPayloadBuilder productNoonPublishPayloadBuilder;
    private final ProductNoonWriteAdapter productNoonWriteAdapter;

    public ProductGroupPublishService(ProductGroupMapper productGroupMapper, ObjectMapper objectMapper) {
        this.productGroupMapper = productGroupMapper;
        this.objectMapper = objectMapper;
        this.productNoonPublishPayloadBuilder = new ProductNoonPublishPayloadBuilder(objectMapper);
        this.productNoonWriteAdapter = new ProductNoonWriteAdapter();
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

        ObjectNode body = productNoonPublishPayloadBuilder.buildGroupMemberCreateBody(
                draftGroup,
                baselineGroup,
                addedSkuParents
        );

        postWriteJson(session, GROUP_UPSERT_URL, body, "Group 新增成员", false);
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

        ObjectNode body = productNoonPublishPayloadBuilder.buildGroupMemberDeleteBody(
                draftGroup,
                baselineGroup,
                removedSkuParents
        );

        postWriteJson(session, GROUP_UPSERT_URL, body, "Group Unlink", false);
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
        for (ObjectNode body : productNoonPublishPayloadBuilder.buildGroupAxisValueBodies(draftGroup, baselineGroup, lang)) {
            boolean priorSuccessfulWrite = writeSubmitted;
            postWriteJson(session, ZSKU_UPSERT_URL, body, "Group 轴属性写回", priorSuccessfulWrite);
            writeSubmitted = true;
            cacheSkuCatplat(session, body.path("skuParent").asText(null));
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
            productNoonWriteAdapter.postWriteJson(
                    session,
                    "Group 缓存刷新",
                    CATPLAT_SKU_CACHE_URL,
                    cacheBody,
                    true
            );
        } catch (IllegalStateException exception) {
            throw partialPublishException("Group 写回后刷新 Noon 缓存结果未知：" + shrink(exception.getMessage()), exception);
        }
    }

    private JsonNode postWriteJson(
            NoonSession session,
            String url,
            ObjectNode body,
            String action,
            boolean priorSuccessfulWrite
    ) {
        try {
            return productNoonWriteAdapter.postWriteJson(session, action, url, body, true);
        } catch (ProductNoonWriteException exception) {
            if (exception.isReadbackOnly()) {
                throw partialPublishException(action + " 请求结果未知：" + shrink(exception.getMessage()), exception);
            }
            if (priorSuccessfulWrite) {
                throw partialPublishException(action + " 失败：" + shrink(exception.getMessage()), exception);
            }
            throw exception;
        }
    }

    private ProductGroupPartialPublishException partialPublishException(Throwable cause) {
        return partialPublishException("Group 写回可能已部分提交到 Noon，请先从 Noon 同步后确认结果。", cause);
    }

    private ProductGroupPartialPublishException partialPublishException(String message, Throwable cause) {
        return new ProductGroupPartialPublishException(message, cause);
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
