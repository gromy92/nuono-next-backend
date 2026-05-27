package com.nuono.next.productselection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public class Ali1688ImageSearchRequest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public Long taskId;
    public Long sourceCollectionId;
    public String requestId;
    public Integer attemptCount;
    public String lockToken;
    public Long ownerUserId;
    public Long logicalStoreId;
    public String collectionNo;
    public String storeCode;
    public String sourceType;
    public String sourcePlatform;
    public String sourceUrl;
    public String pageUrl;
    public String sourceTitle;
    public String sourceTitleCn;
    public String sourceImageUrl;
    public List<String> imageUrls = new ArrayList<>();
    public String priceSummary;
    public String moqHint;
    public String shippingFrom;
    public String brandName;
    public String unitCount;
    public String colorName;
    public List<String> specHints = new ArrayList<>();
    public String sourceDescriptionEn;
    public String selectedText;
    public Integer maxCandidates;

    public static Ali1688ImageSearchRequest fromTask(
            String lockToken,
            Ali1688CollectionRecords.TaskRecord task,
            ProductSelectionSourceCollectionRow sourceCollection,
            int maxCandidates
    ) {
        Ali1688ImageSearchRequest request = new Ali1688ImageSearchRequest();
        request.taskId = task == null ? null : task.id;
        request.sourceCollectionId = task == null ? sourceId(sourceCollection) : task.sourceCollectionId;
        request.attemptCount = task == null || task.attemptCount == null ? 1 : task.attemptCount;
        request.lockToken = StringUtils.hasText(lockToken) ? lockToken.trim() : task == null ? null : task.lockedBy;
        request.requestId = buildRequestId(request.taskId, request.attemptCount, request.lockToken);
        request.ownerUserId = task == null ? ownerId(sourceCollection) : task.ownerUserId;
        request.logicalStoreId = task == null ? storeId(sourceCollection) : task.logicalStoreId;
        request.maxCandidates = Math.max(1, Math.min(maxCandidates, 10));
        applySource(request, sourceCollection);
        return request;
    }

    private static void applySource(Ali1688ImageSearchRequest request, ProductSelectionSourceCollectionRow sourceCollection) {
        if (sourceCollection == null) {
            return;
        }
        request.collectionNo = trim(sourceCollection.getCollectionNo());
        request.storeCode = trim(sourceCollection.getStoreCode());
        request.sourceType = trim(sourceCollection.getSourceType());
        request.sourcePlatform = trim(sourceCollection.getSourcePlatform());
        request.sourceUrl = trim(sourceCollection.getSourceUrl());
        request.pageUrl = trim(sourceCollection.getPageUrl());
        request.sourceTitle = trim(sourceCollection.getSourceTitle());
        request.sourceTitleCn = trim(sourceCollection.getSourceTitleCn());
        request.sourceImageUrl = trim(sourceCollection.getSourceImageUrl());
        request.imageUrls = readStringListJson(sourceCollection.getImageUrlsJson());
        request.priceSummary = trim(sourceCollection.getPriceSummary());
        request.moqHint = trim(sourceCollection.getMoqHint());
        request.shippingFrom = trim(sourceCollection.getShippingFrom());
        request.brandName = trim(sourceCollection.getBrandName());
        request.unitCount = trim(sourceCollection.getUnitCount());
        request.colorName = trim(sourceCollection.getColorName());
        request.specHints = readStringListJson(sourceCollection.getSpecHintsJson());
        request.sourceDescriptionEn = trim(sourceCollection.getSourceDescriptionEn());
        request.selectedText = trim(sourceCollection.getSelectedText());
    }

    private static String buildRequestId(Long taskId, Integer attemptCount, String lockToken) {
        return "ali1688-task-" + (taskId == null ? "unknown" : taskId)
                + "-attempt-" + (attemptCount == null ? 1 : attemptCount)
                + "-" + shortLockToken(lockToken);
    }

    private static String shortLockToken(String lockToken) {
        String value = trim(lockToken);
        if (!StringUtils.hasText(value)) {
            return "nolock";
        }
        int dashIndex = value.indexOf('-');
        return dashIndex > 0 ? value.substring(0, dashIndex) : value;
    }

    private static List<String> readStringListJson(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<String>>() {
            }).stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        } catch (JsonProcessingException exception) {
            return new ArrayList<>();
        }
    }

    private static Long sourceId(ProductSelectionSourceCollectionRow sourceCollection) {
        return sourceCollection == null ? null : sourceCollection.getId();
    }

    private static Long ownerId(ProductSelectionSourceCollectionRow sourceCollection) {
        return sourceCollection == null ? null : sourceCollection.getOwnerUserId();
    }

    private static Long storeId(ProductSelectionSourceCollectionRow sourceCollection) {
        return sourceCollection == null ? null : sourceCollection.getLogicalStoreId();
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
