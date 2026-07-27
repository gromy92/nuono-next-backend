package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.product.noon.ProductNoonAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

final class ProductPublishLocalImageAssetResolver {

    private static final String NOON_ASSET_UPLOAD_URL =
            "https://noon-catalog.noon.partners/_svc/mp-partner-catalog/catalog/asset/upload";
    private static final String LOCAL_PRODUCT_IMAGE_ASSET_PREFIX = "/api/product-master/image-assets/";
    private static final int MAX_IMAGE_UPLOAD_BYTES = 10 * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final ProductNoonAdapter productNoonAdapter;

    ProductPublishLocalImageAssetResolver(
            ObjectMapper objectMapper,
            ProductNoonAdapter productNoonAdapter
    ) {
        this.objectMapper = objectMapper;
        this.productNoonAdapter = productNoonAdapter;
    }

    ProductMasterSnapshotView resolveChangedLocalImageAssets(
            NoonSession session,
            ProductMasterSnapshotView draft,
            ProductMasterSnapshotView baseline,
            List<String> actionWarnings
    ) {
        List<String> draftImages = stringList(draft.getContent().get("images"));
        List<String> baselineImages = stringList(baseline.getContent().get("images"));
        if (draftImages.equals(baselineImages) || draftImages.stream().noneMatch(this::isLocalProductImageAssetUrl)) {
            return draft;
        }
        ProductMasterSnapshotView publishDraft = objectMapper.convertValue(draft, ProductMasterSnapshotView.class);
        List<String> publishImages = new ArrayList<>();
        int uploadedCount = 0;
        try {
            for (String image : draftImages) {
                if (!isLocalProductImageAssetUrl(image)) {
                    publishImages.add(image);
                    continue;
                }
                publishImages.add(uploadLocalProductImageAsset(session, image));
                uploadedCount++;
            }
        } catch (ProductWriteAuthRequiredException exception) {
            throw uploadedCount > 0 ? exception.withWriteMayHaveOccurred() : exception;
        } catch (ProductPublishWriteOutcomeUnknownException exception) {
            throw uploadedCount > 0 ? exception.withPriorWriteCompleted() : exception;
        } catch (RuntimeException exception) {
            if (uploadedCount > 0) {
                throw ProductPublishWriteOutcomeUnknownException.forProviderFailure(
                        "商品图片素材后续处理",
                        true,
                        exception
                );
            }
            throw exception;
        }
        publishDraft.getContent().put("images", publishImages);
        if (uploadedCount > 0 && actionWarnings != null) {
            actionWarnings.add("本地上传图片已先上传为 Noon 可访问图片，再写入商品详情。");
        }
        return publishDraft;
    }

    private boolean isLocalProductImageAssetUrl(String value) {
        return StringUtils.hasText(value) && value.trim().startsWith(LOCAL_PRODUCT_IMAGE_ASSET_PREFIX);
    }

    private String uploadLocalProductImageAsset(NoonSession session, String imageUrl) {
        ProductImageAssetContent image = readLocalProductImageAsset(imageUrl);
        try {
            JsonNode response = productNoonAdapter.postMultipartFile(
                    session,
                    NOON_ASSET_UPLOAD_URL,
                    "file",
                    uploadFileName(image),
                    uploadContentType(image),
                    image.content,
                    true,
                    null
            );
            String uploadPath = firstNonBlank(
                    jsonText(response, "upload_path"),
                    jsonText(response, "uploadPath"),
                    jsonText(response, "path"),
                    jsonText(response, "url")
            );
            if (!StringUtils.hasText(uploadPath)) {
                throw new IllegalStateException("Noon 图片上传响应缺少 upload_path。");
            }
            return uploadPath;
        } catch (ProductWriteAuthRequiredException exception) {
            throw exception;
        } catch (ProductPublishWriteOutcomeUnknownException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            ProductWriteAuthRequiredException authRequired =
                    ProductWriteAuthRequiredException.find(exception);
            if (authRequired != null) {
                throw authRequired;
            }
            throw ProductPublishWriteOutcomeUnknownException.forProviderFailure(
                    "商品图片素材上传",
                    false,
                    exception
            );
        }
    }

    private ProductImageAssetContent readLocalProductImageAsset(String imageUrl) {
        String filename = imageUrl.trim().substring(LOCAL_PRODUCT_IMAGE_ASSET_PREFIX.length()).trim();
        if (!StringUtils.hasText(filename)
                || filename.contains("..")
                || filename.contains("/")
                || filename.contains("\\")) {
            throw new IllegalArgumentException("本地上传图片地址无效。");
        }
        Path uploadDir = ProductImageAssetFileSupport.productImageUploadDir().normalize();
        Path file = uploadDir.resolve(filename).normalize();
        if (!file.startsWith(uploadDir)) {
            throw new IllegalArgumentException("本地上传图片地址无效。");
        }
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            throw new IllegalStateException("本地上传图片文件不存在，请重新上传或重新自动适配。");
        }
        try {
            byte[] content = Files.readAllBytes(file);
            if (content.length == 0) {
                throw new IllegalStateException("本地上传图片文件为空，请重新上传。");
            }
            if (content.length > MAX_IMAGE_UPLOAD_BYTES) {
                throw new IllegalStateException("本地上传图片超过 10MB，不能发布到 Noon。");
            }
            String contentType = Files.probeContentType(file);
            if (!StringUtils.hasText(contentType)) {
                contentType = contentTypeFromFileName(filename);
            }
            ProductImageAssetContent image = new ProductImageAssetContent(filename, contentType, content);
            supportedUploadFileType(image);
            return image;
        } catch (IOException exception) {
            throw new IllegalStateException("读取本地上传图片失败：" + exception.getMessage(), exception);
        }
    }

    private String contentTypeFromFileName(String filename) {
        String lower = StringUtils.hasText(filename) ? filename.toLowerCase(Locale.ROOT) : "";
        return lower.endsWith(".png") ? "image/png" : "image/jpeg";
    }

    private String uploadFileName(ProductImageAssetContent image) {
        String fileType = supportedUploadFileType(image);
        String fileName = textValue(image.fileName);
        if (!StringUtils.hasText(fileName)) {
            return "image." + fileType;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if ("png".equals(fileType) && !lower.endsWith(".png")) {
            return stripKnownImageExtension(fileName) + ".png";
        }
        if ("jpg".equals(fileType) && !(lower.endsWith(".jpg") || lower.endsWith(".jpeg"))) {
            return stripKnownImageExtension(fileName) + ".jpg";
        }
        if ("jpg".equals(fileType) && lower.endsWith(".jpeg")) {
            return fileName.substring(0, fileName.length() - 5) + ".jpg";
        }
        return fileName;
    }

    private String uploadContentType(ProductImageAssetContent image) {
        return "png".equals(supportedUploadFileType(image)) ? "image/png" : "image/jpeg";
    }

    private String jsonText(JsonNode node, String fieldName) {
        if (node == null || !StringUtils.hasText(fieldName)) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.isTextual() ? value.asText() : value.toString();
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String stripKnownImageExtension(String fileName) {
        String normalized = textValue(fileName);
        String lower = normalized.toLowerCase(Locale.ROOT);
        for (String extension : List.of(".jpg", ".jpeg", ".png", ".webp", ".avif", ".gif")) {
            if (lower.endsWith(extension)) {
                return normalized.substring(0, normalized.length() - extension.length());
            }
        }
        return StringUtils.hasText(normalized) ? normalized : "image";
    }

    private String supportedUploadFileType(ProductImageAssetContent image) {
        String fileName = textValue(image.fileName).toLowerCase(Locale.ROOT);
        String contentType = textValue(image.contentType).toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".png") || contentType.contains("png")) {
            return "png";
        }
        if (fileName.endsWith(".jpg")
                || fileName.endsWith(".jpeg")
                || contentType.contains("jpeg")
                || contentType.contains("jpg")) {
            return "jpg";
        }
        throw new IllegalStateException("Noon 图片上传只支持 JPG/PNG，请先点击自动适配后再发布。");
    }

    private List<String> stringList(Object value) {
        List<String> values = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                String text = textValue(item);
                if (StringUtils.hasText(text)) {
                    values.add(text);
                }
            }
        } else if (StringUtils.hasText(textValue(value))) {
            values.add(textValue(value));
        }
        return values;
    }

    private String textValue(Object value) {
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
                return value;
            }
        }
        return null;
    }

    private static final class ProductImageAssetContent {
        private final String fileName;
        private final String contentType;
        private final byte[] content;

        private ProductImageAssetContent(String fileName, String contentType, byte[] content) {
            this.fileName = fileName;
            this.contentType = contentType;
            this.content = content == null ? new byte[0] : content;
        }
    }
}
