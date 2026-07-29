package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.util.StringUtils;

final class ProductImagePublishCheckpoint {
    static final int MAX_IMAGES = 20;
    private static final int VERSION = 2;

    private final Map<String, UploadedImage> uploads = new LinkedHashMap<>();
    private final List<ApprovedImage> approvedImages = new ArrayList<>();
    private String attemptId;
    private boolean writeAttempted;

    static ProductImagePublishCheckpoint start(List<ProductImageSuiteAssetRecord> assets) {
        List<ProductImageSuiteAssetRecord> approvedAssets = assets == null ? List.of() : assets;
        if (approvedAssets.isEmpty()) {
            throw new IllegalArgumentException("套图没有可发布的图片。");
        }
        if (approvedAssets.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("Noon 最多支持发布 20 张商品图片，请先精简套图。");
        }
        ProductImagePublishCheckpoint checkpoint = new ProductImagePublishCheckpoint();
        checkpoint.attemptId = UUID.randomUUID().toString();
        for (ProductImageSuiteAssetRecord asset : approvedAssets) {
            String imageUrl = asset == null ? null : normalize(asset.getImageUrl());
            String sha256 = asset == null ? null : normalize(asset.getSha256());
            if (asset == null
                    || asset.getId() == null
                    || !StringUtils.hasText(imageUrl)
                    || !StringUtils.hasText(sha256)) {
                throw new IllegalArgumentException("套图图片缺少资产编号、地址或 SHA-256，暂时不能发布。");
            }
            ProductImagePublishAssetResolver.validateDefaultAddress(imageUrl);
            checkpoint.approvedImages.add(new ApprovedImage(
                    asset.getId(),
                    imageUrl,
                    sha256,
                    asset.getSortOrder()
            ));
        }
        return checkpoint;
    }

    static ProductImagePublishCheckpoint renew(
            ObjectMapper objectMapper,
            String checkpointJson,
            List<ProductImageSuiteAssetRecord> currentAssets
    ) {
        ProductImagePublishCheckpoint previous = parse(objectMapper, checkpointJson);
        ProductImagePublishCheckpoint renewed = start(currentAssets);
        if (previous.approvedImages.equals(renewed.approvedImages)) {
            renewed.writeAttempted = previous.writeAttempted;
            renewed.uploads.putAll(previous.uploads);
        }
        return renewed;
    }

    static ProductImagePublishCheckpoint parse(ObjectMapper objectMapper, String checkpointJson) {
        ProductImagePublishCheckpoint checkpoint = new ProductImagePublishCheckpoint();
        if (!StringUtils.hasText(checkpointJson)) {
            return checkpoint;
        }
        try {
            JsonNode root = objectMapper.readTree(checkpointJson);
            int version = root == null ? 0 : root.path("version").asInt(0);
            if (root == null || !root.isObject() || (version != 1 && version != VERSION)) {
                return checkpoint;
            }
            checkpoint.attemptId = normalize(root.path("attemptId").asText(null));
            checkpoint.writeAttempted = root.path("writeAttempted").asBoolean(false);
            for (JsonNode approved : root.path("approvedImages")) {
                String imageUrl = normalize(approved.path("imageUrl").asText(null));
                if (StringUtils.hasText(imageUrl)) {
                    checkpoint.approvedImages.add(new ApprovedImage(
                            approved.path("assetId").isIntegralNumber()
                                    ? approved.path("assetId").asLong()
                                    : null,
                            imageUrl,
                            normalize(approved.path("sha256").asText(null)),
                            approved.path("sortOrder").isIntegralNumber()
                                    ? approved.path("sortOrder").asInt()
                                    : null
                    ));
                }
            }
            for (JsonNode upload : root.path("uploads")) {
                String localImageUrl = normalize(upload.path("localImageUrl").asText(null));
                String sha256 = normalize(upload.path("sha256").asText(null));
                String noonUrl = normalize(upload.path("noonUrl").asText(null));
                if (StringUtils.hasText(localImageUrl)
                        && StringUtils.hasText(sha256)
                        && StringUtils.hasText(noonUrl)) {
                    checkpoint.uploads.put(localImageUrl, new UploadedImage(sha256, noonUrl));
                }
            }
            return checkpoint;
        } catch (IOException ignored) {
            return new ProductImagePublishCheckpoint();
        }
    }

    String attemptId() {
        return attemptId;
    }

    boolean matchesAttempt(String expectedAttemptId) {
        return StringUtils.hasText(attemptId)
                && attemptId.equals(expectedAttemptId);
    }

    List<String> approvedImageUrls() {
        List<String> result = new ArrayList<>();
        for (ApprovedImage image : approvedImages) {
            result.add(image.imageUrl);
        }
        return result;
    }

    void requireApprovedContent(String imageUrl, String sha256) {
        if (approvedImages.isEmpty()) {
            return;
        }
        for (ApprovedImage image : approvedImages) {
            if (image.imageUrl.equals(imageUrl) && image.sha256.equals(sha256)) {
                return;
            }
        }
        throw new IllegalStateException("审核通过后的套图文件已发生变化，请重新审核后再发布。");
    }

    boolean isWriteAttempted() {
        return writeAttempted;
    }

    void markWriteAttempted() {
        writeAttempted = true;
    }

    String noonUrl(String localImageUrl, String sha256) {
        UploadedImage uploaded = uploads.get(localImageUrl);
        if (uploaded == null || !uploaded.sha256.equals(sha256)) {
            return null;
        }
        return uploaded.noonUrl;
    }

    void record(String localImageUrl, String sha256, String noonUrl) {
        uploads.put(localImageUrl, new UploadedImage(sha256, noonUrl));
    }

    String toJson(ObjectMapper objectMapper) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("version", VERSION);
        if (StringUtils.hasText(attemptId)) {
            root.put("attemptId", attemptId);
        }
        root.put("writeAttempted", writeAttempted);
        ArrayNode approvedNodes = root.putArray("approvedImages");
        for (ApprovedImage image : approvedImages) {
            ObjectNode approved = approvedNodes.addObject();
            if (image.assetId != null) {
                approved.put("assetId", image.assetId);
            }
            approved.put("imageUrl", image.imageUrl);
            if (StringUtils.hasText(image.sha256)) {
                approved.put("sha256", image.sha256);
            }
            if (image.sortOrder != null) {
                approved.put("sortOrder", image.sortOrder);
            }
        }
        ArrayNode uploadNodes = root.putArray("uploads");
        for (Map.Entry<String, UploadedImage> entry : uploads.entrySet()) {
            ObjectNode upload = uploadNodes.addObject();
            upload.put("localImageUrl", entry.getKey());
            upload.put("sha256", entry.getValue().sha256);
            upload.put("noonUrl", entry.getValue().noonUrl);
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (IOException exception) {
            throw new IllegalStateException("商品图片发布检查点保存失败。", exception);
        }
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static final class ApprovedImage {
        private final Long assetId;
        private final String imageUrl;
        private final String sha256;
        private final Integer sortOrder;

        private ApprovedImage(Long assetId, String imageUrl, String sha256, Integer sortOrder) {
            this.assetId = assetId;
            this.imageUrl = imageUrl;
            this.sha256 = sha256;
            this.sortOrder = sortOrder;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ApprovedImage)) {
                return false;
            }
            ApprovedImage that = (ApprovedImage) other;
            return Objects.equals(assetId, that.assetId)
                    && Objects.equals(imageUrl, that.imageUrl)
                    && Objects.equals(sha256, that.sha256)
                    && Objects.equals(sortOrder, that.sortOrder);
        }

        @Override
        public int hashCode() {
            return Objects.hash(assetId, imageUrl, sha256, sortOrder);
        }
    }

    private static final class UploadedImage {
        private final String sha256;
        private final String noonUrl;

        private UploadedImage(String sha256, String noonUrl) {
            this.sha256 = sha256;
            this.noonUrl = noonUrl;
        }
    }
}
