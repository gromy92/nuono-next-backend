package com.nuono.next.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductImageProfileMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

@Service
class ProductImageWorkflowService {
    private static final List<ProductImageSuiteAssetRole> REQUIRED_ROLES = List.of(
            ProductImageSuiteAssetRole.MAIN,
            ProductImageSuiteAssetRole.SIZE,
            ProductImageSuiteAssetRole.CORE_FEATURE,
            ProductImageSuiteAssetRole.MATERIAL_DETAIL,
            ProductImageSuiteAssetRole.USAGE_SCENE,
            ProductImageSuiteAssetRole.PACKAGE_LIST
    );
    private final ProductImageProfileMapper mapper;
    private final ProductImageGenerator generator;
    private final ObjectProvider<ProductImageNoonPublisher> publisherProvider;
    private final ObjectMapper objectMapper;

    ProductImageWorkflowService(
            ProductImageProfileMapper mapper,
            ProductImageGenerator generator,
            ObjectProvider<ProductImageNoonPublisher> publisherProvider,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.generator = generator;
        this.publisherProvider = publisherProvider;
        this.objectMapper = objectMapper;
    }

    void generate(Long suiteId, Long ownerUserId, String storeCode, Long operatorUserId) {
        ProductImageSuiteRecord suite = requireSuite(suiteId);
        ProductImageProfileRecord profile = requireProfile(suite, ownerUserId, storeCode);
        boolean rework = suite.getParentSuiteId() != null;
        mapper.updateSuiteWorkflowStatus(
                suiteId,
                rework ? ProductImageSuiteStatus.REGENERATING : ProductImageSuiteStatus.GENERATING,
                null,
                null,
                operatorUserId
        );
        try {
            List<ProductImageSuiteAssetRecord> existing = mapper.selectSuiteAssets(suiteId);
            Set<ProductImageSuiteAssetRole> completed = existing.stream()
                    .map(ProductImageSuiteAssetRecord::getImageRole)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            List<String> baseReferences = baseReferences(profile);
            for (int roleIndex = 0; roleIndex < REQUIRED_ROLES.size(); roleIndex++) {
                ProductImageSuiteAssetRole role = REQUIRED_ROLES.get(roleIndex);
                if (completed.contains(role)) continue;
                List<String> references = new ArrayList<>(baseReferences);
                String previous = previousImage(suite, role);
                if (StringUtils.hasText(previous)) references.add(0, previous);
                GeneratedProductImage image = generator.generate(rolePrompt(suite, role), references);
                ProductImageSuiteAssetRecord asset = saveGeneratedImage(suite, role, image, storeCode, (roleIndex + 1) * 10);
                mapper.insertSuiteAsset(asset);
            }
            mapper.updateSuiteWorkflowStatus(suiteId, ProductImageSuiteStatus.PENDING_REVIEW, null, null, operatorUserId);
        } catch (RuntimeException exception) {
            mapper.updateSuiteWorkflowStatus(
                    suiteId, ProductImageSuiteStatus.FAILED, "GENERATION", safeMessage(exception), operatorUserId
            );
            throw exception;
        }
    }

    void publish(Long suiteId, Long ownerUserId, String storeCode, Long operatorUserId) {
        ProductImageSuiteRecord suite = requireSuite(suiteId);
        ProductImageProfileRecord profile = requireProfile(suite, ownerUserId, storeCode);
        try {
            ProductImageNoonPublisher publisher = publisherProvider.getIfAvailable();
            if (publisher == null) throw new IllegalStateException("Noon 图片发布服务暂时不可用。");
            List<String> images = mapper.selectSuiteAssets(suiteId).stream()
                    .map(ProductImageSuiteAssetRecord::getImageUrl)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList());
            if (images.isEmpty()) throw new IllegalArgumentException("套图没有可发布的图片。");
            String skuParent = mapper.selectSkuParentByProductMasterId(profile.getProductMasterId());
            List<String> noonUrls = publisher.publish(ownerUserId, storeCode, skuParent, images);
            mapper.markSuiteOnline(suiteId, writeManifest(noonUrls));
        } catch (RuntimeException exception) {
            mapper.updateSuiteWorkflowStatus(
                    suiteId, ProductImageSuiteStatus.FAILED, "PUBLISH", safeMessage(exception), operatorUserId
            );
            throw exception;
        }
    }

    private ProductImageSuiteRecord requireSuite(Long suiteId) {
        ProductImageSuiteRecord suite = mapper.selectSuiteByIdUnscoped(suiteId);
        if (suite == null) throw new ProductImageProfileNotFoundException("AI 套图不存在。");
        return suite;
    }

    private ProductImageProfileRecord requireProfile(ProductImageSuiteRecord suite, Long ownerUserId, String storeCode) {
        ProductImageProfileRecord profile = mapper.selectProfileById(suite.getProfileId(), ownerUserId, storeCode);
        if (profile == null) throw new ProductImageProfileNotFoundException("商品图资料不存在。");
        return profile;
    }

    private List<String> baseReferences(ProductImageProfileRecord profile) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (ProductImageProfileAssetRecord asset : mapper.selectAssets(profile.getId())) result.add(asset.getImageUrl());
        if (profile.getProductMasterId() != null) {
            for (ProductImageProfileAssetRecord asset : mapper.selectCurrentProductImages(profile.getProductMasterId())) result.add(asset.getImageUrl());
        }
        result.removeIf(value -> !StringUtils.hasText(value));
        return new ArrayList<>(result);
    }

    private String previousImage(ProductImageSuiteRecord suite, ProductImageSuiteAssetRole role) {
        if (suite.getParentSuiteId() == null) return null;
        return mapper.selectSuiteAssets(suite.getParentSuiteId()).stream()
                .filter(asset -> asset.getImageRole() == role)
                .map(ProductImageSuiteAssetRecord::getImageUrl)
                .findFirst()
                .orElse(null);
    }

    private String rolePrompt(ProductImageSuiteRecord suite, ProductImageSuiteAssetRole role) {
        String label;
        switch (role) {
            case MAIN: label = "主图1：商品主体清晰完整，使用选定店铺皮肤"; break;
            case SIZE: label = "尺寸图1：只展示已确认的尺寸与比例"; break;
            case CORE_FEATURE: label = "功能图1：展示一个已确认的核心卖点"; break;
            case MATERIAL_DETAIL: label = "细节图1：商品局部特写，不做多图拼版"; break;
            case USAGE_SCENE: label = "场景图1：使用场景必须符合商品真实用途"; break;
            default: label = "包装图1：只展示已确认包含的商品和配件";
        }
        String feedback = StringUtils.hasText(suite.getReviewComment())
                ? "\n审核返工意见：" + suite.getReviewComment()
                : "";
        return suite.getDraftPromptText()
                + "\n\n本次只输出一张 " + label
                + "。不得编造商品外观、规格、材质、数量、功能、认证或使用场景。"
                + feedback;
    }

    private ProductImageSuiteAssetRecord saveGeneratedImage(
            ProductImageSuiteRecord suite,
            ProductImageSuiteAssetRole role,
            GeneratedProductImage generated,
            String storeCode,
            int sortOrder
    ) {
        try {
            String normalizedStore = storeCode.trim().toUpperCase(Locale.ROOT);
            Path dir = ProductImageAssetFileSupport.productImageUploadDir().resolve("profiles").resolve(normalizedStore).normalize();
            Files.createDirectories(dir);
            String filename = "ai-" + suite.getId() + "-" + role.name().toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID() + ".png";
            Path file = dir.resolve(filename).normalize();
            if (!file.startsWith(dir)) throw new IllegalStateException("AI 图片保存路径不合法。");
            Files.write(file, generated.content());
            ProductImageSuiteAssetRecord asset = new ProductImageSuiteAssetRecord();
            asset.setSuiteId(suite.getId());
            asset.setImageRole(role);
            asset.setRoleOrdinal(1);
            asset.setImageUrl("/api/product-images/assets/"
                    + UriUtils.encodePathSegment(normalizedStore, java.nio.charset.StandardCharsets.UTF_8)
                    + "/" + UriUtils.encodePathSegment(filename, java.nio.charset.StandardCharsets.UTF_8));
            asset.setContentType(generated.contentType());
            asset.setSizeBytes((long) generated.content().length);
            asset.setSha256(sha256(generated.content()));
            asset.setSortOrder(sortOrder);
            return asset;
        } catch (IOException exception) {
            throw new IllegalStateException("AI 图片保存失败：" + exception.getMessage(), exception);
        }
    }

    private String writeManifest(List<String> noonUrls) {
        try { return objectMapper.writeValueAsString(noonUrls); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("发布清单保存失败。", exception); }
    }

    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder builder = new StringBuilder();
            for (byte value : digest) builder.append(String.format("%02x", value));
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256。", exception);
        }
    }

    private String safeMessage(Throwable throwable) {
        String value = throwable == null ? null : throwable.getMessage();
        if (!StringUtils.hasText(value)) return "任务执行失败，请重试。";
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 1900 ? normalized : normalized.substring(0, 1900) + "...";
    }
}
