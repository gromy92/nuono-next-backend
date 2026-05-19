package com.nuono.next.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductGroupMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class ProductGroupProjectionService {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProductGroupMapper productGroupMapper;
    private final ObjectMapper objectMapper;

    public ProductGroupProjectionService(ProductGroupMapper productGroupMapper, ObjectMapper objectMapper) {
        this.productGroupMapper = productGroupMapper;
        this.objectMapper = objectMapper;
    }

    public void persistGroupProjection(
            Long logicalStoreId,
            Long productMasterId,
            Long sourceSnapshotId,
            ProductMasterSnapshotView snapshot,
            String lastSyncedAt,
            Long updatedBy
    ) {
        if (logicalStoreId == null || productMasterId == null || snapshot == null) {
            return;
        }
        Map<String, Object> group = snapshot.getGroup() != null ? snapshot.getGroup() : Map.of();
        String skuGroup = normalize(text(group.get("skuGroup")));
        if (!StringUtils.hasText(skuGroup)) {
            if (isConfirmedUngroupedSnapshot(group)) {
                clearGroupProjectionForProduct(productMasterId, updatedBy);
            }
            return;
        }
        String groupRef = normalize(text(group.get("groupRef")));
        String groupRefCanonical = normalize(text(group.get("groupRefCanonical")));
        String groupName = firstNonBlank(
                text(group.get("groupName")),
                groupRef,
                groupRefCanonical,
                skuGroup
        );
        Map<String, Object> conditions = new LinkedHashMap<>();
        putIfNotBlank(conditions, "brand", firstNonBlank(text(group.get("conditionsBrand")), text(snapshot.getIdentity().get("brand"))));
        putIfNotBlank(
                conditions,
                "fulltype",
                firstNonBlank(text(group.get("conditionsFulltype")), text(snapshot.getTaxonomy().get("productFulltype")))
        );

        Long existingGroupId = productGroupMapper.selectProductGroupId(logicalStoreId, skuGroup);
        Long productGroupId = existingGroupId != null ? existingGroupId : productGroupMapper.nextProductGroupId();
        productGroupMapper.upsertProductGroup(
                productGroupId,
                logicalStoreId,
                skuGroup,
                groupRef,
                groupRefCanonical,
                normalize(groupName),
                normalize(text(conditions.get("brand"))),
                normalize(text(conditions.get("fulltype"))),
                toJson(group.get("axes")),
                toJson(conditions),
                asInteger(group.get("memberCount")),
                sourceSnapshotId,
                "synced",
                parseDateTime(lastSyncedAt),
                updatedBy
        );

        Long persistedGroupId = productGroupMapper.selectProductGroupId(logicalStoreId, skuGroup);
        if (persistedGroupId == null) {
            return;
        }

        List<Map<String, Object>> members = objectList(group.get("members"));
        if (members.isEmpty()) {
            Map<String, Object> currentMember = new LinkedHashMap<>();
            putIfNotBlank(currentMember, "skuParent", text(snapshot.getIdentity().get("skuParent")));
            members.add(currentMember);
        }

        List<String> activeSkuParents = new ArrayList<>();
        for (int index = 0; index < members.size(); index++) {
            Map<String, Object> member = members.get(index);
            String memberSkuParent = normalize(ProductGroupSnapshotSupport.groupMemberSkuParent(member));
            if (!StringUtils.hasText(memberSkuParent)) {
                continue;
            }
            activeSkuParents.add(memberSkuParent);
            Long memberMasterId = productMasterId;
            String currentSkuParent = text(snapshot.getIdentity().get("skuParent"));
            if (StringUtils.hasText(currentSkuParent) && !currentSkuParent.equalsIgnoreCase(memberSkuParent)) {
                memberMasterId = productGroupMapper.selectProductMasterId(logicalStoreId, memberSkuParent);
            }
            Long existingMemberId = productGroupMapper.selectProductGroupMemberId(persistedGroupId, memberSkuParent);
            Long memberId = existingMemberId != null ? existingMemberId : productGroupMapper.nextProductGroupMemberId();
            productGroupMapper.upsertProductGroupMember(
                    memberId,
                    persistedGroupId,
                    memberMasterId,
                    memberSkuParent,
                    normalize(firstNonBlank(text(member.get("memberSku")), text(member.get("sku")))),
                    normalize(text(member.get("childSku"))),
                    normalize(text(member.get("partnerSku"))),
                    toJson(member),
                    index,
                    "active",
                    sourceSnapshotId,
                    parseDateTime(lastSyncedAt),
                    updatedBy
            );
        }

        if (!activeSkuParents.isEmpty()) {
            productGroupMapper.markStaleProductGroupMembersDeleted(persistedGroupId, activeSkuParents, updatedBy);
            String effectiveGroupRef = normalize(firstNonBlank(groupRef, groupRefCanonical, skuGroup));
            String effectiveGroupName = normalize(firstNonBlank(groupName, effectiveGroupRef));
            Integer effectiveMemberCount = activeSkuParents.size();
            productGroupMapper.syncProductMasterGroupFieldsForActiveMembers(
                    persistedGroupId,
                    skuGroup,
                    effectiveGroupRef,
                    effectiveGroupName,
                    effectiveMemberCount,
                    updatedBy
            );
            productGroupMapper.clearProductMasterGroupFieldsForInactiveMembers(
                    logicalStoreId,
                    skuGroup,
                    effectiveGroupRef,
                    groupRefCanonical,
                    activeSkuParents,
                    updatedBy
            );
        }
    }

    public void hydrateSnapshotGroupFromCurrentProjection(
            Long ownerUserId,
            String storeCode,
            String skuParent,
            ProductMasterSnapshotView snapshot,
            List<String> warnings
    ) {
        if (
                ownerUserId == null
                        || !StringUtils.hasText(storeCode)
                        || !StringUtils.hasText(skuParent)
                        || snapshot == null
        ) {
            return;
        }
        ProductGroupProjectionRecord groupRecord = productGroupMapper.selectCurrentProductGroupProjection(
                ownerUserId,
                storeCode.trim(),
                skuParent.trim()
        );
        if (groupRecord == null || !StringUtils.hasText(groupRecord.getSkuGroup())) {
            Map<String, Object> ungrouped = new LinkedHashMap<>();
            ungrouped.put("state", "当前商品未挂 group");
            snapshot.setGroup(ungrouped);
            return;
        }

        List<ProductGroupMemberProjectionRecord> memberRecords = productGroupMapper.selectActiveProductGroupMembers(
                groupRecord.getProductGroupId()
        );
        if (memberRecords == null) {
            memberRecords = List.of();
        }
        Map<String, Object> group = new LinkedHashMap<>();
        putIfNotBlank(group, "skuGroup", groupRecord.getSkuGroup());
        putIfNotBlank(group, "groupRef", groupRecord.getGroupRef());
        putIfNotBlank(group, "groupRefCanonical", groupRecord.getGroupRefCanonical());
        putIfNotBlank(group, "groupName", firstNonBlank(
                groupRecord.getGroupName(),
                groupRecord.getGroupRef(),
                groupRecord.getGroupRefCanonical(),
                groupRecord.getSkuGroup()
        ));
        putIfNotBlank(group, "conditionsBrand", groupRecord.getBrand());
        putIfNotBlank(group, "conditionsFulltype", groupRecord.getProductFulltype());
        List<Map<String, Object>> axes = jsonObjectList(groupRecord.getAxesJson(), warnings);
        if (!axes.isEmpty()) {
            group.put("axes", axes);
        }
        Map<String, Object> conditions = jsonObjectMap(groupRecord.getConditionsJson(), warnings);
        if (!conditions.isEmpty()) {
            group.put("conditions", conditions);
        }
        List<Map<String, Object>> members = new ArrayList<>();
        for (ProductGroupMemberProjectionRecord memberRecord : memberRecords) {
            members.add(toSnapshotMember(memberRecord, warnings));
        }
        group.put("members", members);
        group.put(
                "memberCount",
                groupRecord.getMemberCount() != null ? groupRecord.getMemberCount() : members.size()
        );
        snapshot.setGroup(group);
    }

    private boolean isConfirmedUngroupedSnapshot(Map<String, Object> group) {
        if (group == null || group.isEmpty()) {
            return false;
        }
        String state = text(group.get("state"));
        return StringUtils.hasText(state) && state.contains("未挂 group");
    }

    private void clearGroupProjectionForProduct(Long productMasterId, Long updatedBy) {
        productGroupMapper.markActiveGroupMembersDeletedByProductMasterId(productMasterId, updatedBy);
        productGroupMapper.refreshProductGroupMemberCountsByProductMasterId(productMasterId, updatedBy);
        productGroupMapper.clearProductMasterGroupFieldsById(productMasterId, updatedBy);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private Map<String, Object> toSnapshotMember(
            ProductGroupMemberProjectionRecord record,
            List<String> warnings
    ) {
        Map<String, Object> member = jsonObjectMap(record.getAxisValuesJson(), warnings);
        putIfNotBlank(member, "skuParent", firstNonBlank(
                text(member.get("skuParent")),
                record.getSkuParent()
        ));
        putIfNotBlank(member, "memberSku", firstNonBlank(
                text(member.get("memberSku")),
                record.getMemberSku()
        ));
        putIfNotBlank(member, "childSku", firstNonBlank(
                text(member.get("childSku")),
                record.getChildSku()
        ));
        putIfNotBlank(member, "partnerSku", firstNonBlank(
                text(member.get("partnerSku")),
                record.getPartnerSku()
        ));
        putIfNotBlank(member, "title", firstNonBlank(text(member.get("title")), record.getTitle()));
        String imageUrl = firstNonBlank(
                text(member.get("imageUrl")),
                text(member.get("image")),
                record.getImageUrl()
        );
        putIfNotBlank(member, "imageUrl", imageUrl);
        if (!StringUtils.hasText(text(member.get("image")))) {
            putIfNotBlank(member, "image", imageUrl);
        }
        return member;
    }

    private Map<String, Object> jsonObjectMap(String json, List<String> warnings) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> value = objectMapper.readValue(
                    json,
                    new TypeReference<Map<String, Object>>() {
                    }
            );
            return new LinkedHashMap<>(value);
        } catch (Exception exception) {
            if (warnings != null) {
                warnings.add("本地 Group 投影 JSON 解析失败，已使用最小字段恢复。");
            }
            return new LinkedHashMap<>();
        }
    }

    private List<Map<String, Object>> jsonObjectList(String json, List<String> warnings) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(
                    json,
                    new TypeReference<List<Map<String, Object>>>() {
                    }
            );
        } catch (Exception exception) {
            if (warnings != null) {
                warnings.add("本地 Group 轴投影 JSON 解析失败，已跳过轴信息。");
            }
            return new ArrayList<>();
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        try {
            return LocalDateTime.parse(text, TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = text(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> objectList(Object value) {
        if (!(value instanceof List<?>)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> values = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof Map<?, ?>) {
                values.add(new LinkedHashMap<>((Map<String, Object>) item));
            }
        }
        return values;
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value.trim());
        }
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
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

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
