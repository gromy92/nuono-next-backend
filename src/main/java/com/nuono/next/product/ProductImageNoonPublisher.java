package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.product.noon.NoonProductGateway;
import com.nuono.next.product.noon.ProductNoonAdapter;
import com.nuono.next.store.StoreSyncOwnerContext;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
class ProductImageNoonPublisher {
    private static final String ASSET_UPLOAD_URL =
            "https://noon-catalog.noon.partners/_svc/mp-partner-catalog/catalog/asset/upload";
    private static final int MAX_NOON_IMAGES = ProductImagePublishCheckpoint.MAX_IMAGES;
    private final StoreSyncMapper storeSyncMapper;
    private final ProductNoonAdapter noonAdapter;
    private final ObjectMapper objectMapper;
    private final ProductImagePublishAssetResolver assetResolver;

    ProductImageNoonPublisher(StoreSyncMapper storeSyncMapper, ProductNoonAdapter noonAdapter, ObjectMapper objectMapper) {
        this.storeSyncMapper = storeSyncMapper;
        this.noonAdapter = noonAdapter;
        this.objectMapper = objectMapper;
        this.assetResolver = new ProductImagePublishAssetResolver();
    }

    List<String> publish(Long ownerUserId, String storeCode, String skuParent, List<String> localImageUrls) {
        return publish(ownerUserId, storeCode, skuParent, localImageUrls, null, ignored -> {
        });
    }

    List<String> publish(
            Long ownerUserId,
            String storeCode,
            String skuParent,
            List<String> localImageUrls,
            String checkpointJson,
            Consumer<String> checkpointSaver
    ) {
        if (!StringUtils.hasText(skuParent)) throw new IllegalArgumentException("该商品尚未在 Noon 上线，不能发布图片。");
        if (localImageUrls != null && localImageUrls.size() > MAX_NOON_IMAGES) {
            throw new IllegalArgumentException("Noon 最多支持发布 20 张商品图片，请先精简套图。");
        }
        List<ProductImagePublishAsset> localImages = new ArrayList<>();
        for (String localUrl : localImageUrls) {
            localImages.add(assetResolver.resolve(localUrl));
        }
        ProductImagePublishCheckpoint checkpoint =
                ProductImagePublishCheckpoint.parse(objectMapper, checkpointJson);
        for (ProductImagePublishAsset localImage : localImages) {
            checkpoint.requireApprovedContent(localImage.sourceUrl, localImage.sha256);
        }
        List<String> checkpointUrls = checkpointUrls(checkpoint, localImages);
        StoreSyncStoreRecord store = storeSyncMapper.selectOwnerProject(ownerUserId, storeCode);
        StoreSyncOwnerContext owner = storeSyncMapper.selectOwnerContext(ownerUserId);
        if (store == null || owner == null) throw new IllegalArgumentException("当前店铺缺少 Noon 账号上下文，暂时不能发布。");
        String noonUser = first(store.getNoonPartnerProjectUser(), store.getNoonPartnerUser(), owner.getNoonPartnerProjectUser(), owner.getNoonPartnerUser());
        String projectCode = first(store.getProjectCode(), store.getNoonPartnerId(), owner.getNoonPartnerId());
        if (!StringUtils.hasText(noonUser) || !StringUtils.hasText(projectCode)) {
            throw new IllegalArgumentException("当前店铺缺少 Noon 账号或项目配置，暂时不能发布。");
        }
        NoonSession session = noonAdapter.loginWithPersistedCookie(
                ownerUserId, noonUser, store.getNoonPartnerCookie(), projectCode, storeCode
        );
        if (checkpoint.isWriteAttempted() && checkpointUrls != null) {
            try {
                if (readbackMatches(session, skuParent, checkpointUrls)) {
                    return checkpointUrls;
                }
            } catch (ProductWriteAuthRequiredException exception) {
                throw promoteWriteProgress(exception);
            }
        }

        List<String> noonUrls = new ArrayList<>();
        for (ProductImagePublishAsset localImage : localImages) {
            String noonUrl = checkpoint.noonUrl(localImage.sourceUrl, localImage.sha256);
            if (!StringUtils.hasText(noonUrl)) {
                noonUrl = upload(session, localImage);
                checkpoint.record(localImage.sourceUrl, localImage.sha256, noonUrl);
                saveCheckpoint(checkpoint, checkpointSaver);
            }
            noonUrls.add(noonUrl);
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("skuParent", skuParent);
        body.put("lang", "en");
        ObjectNode attributes = body.putObject("attributes");
        for (int index = 0; index < MAX_NOON_IMAGES; index++) {
            String key = "image_url_" + (index + 1);
            if (index < noonUrls.size()) attributes.put(key, noonUrls.get(index));
            else attributes.putNull(key);
        }
        body.putArray("variants");
        checkpoint.markWriteAttempted();
        saveCheckpoint(checkpoint, checkpointSaver);
        noonAdapter.postWriteJson(session, NoonProductGateway.ZSKU_UPSERT_URL, body, true);

        try {
            if (!readbackMatches(session, skuParent, noonUrls)) {
                throw new IllegalStateException("Noon 图片回读校验失败：数量或顺序与审核套图不一致。");
            }
        } catch (ProductWriteAuthRequiredException exception) {
            throw promoteWriteProgress(exception);
        }
        return noonUrls;
    }

    private boolean readbackMatches(NoonSession session, String skuParent, List<String> expectedUrls) {
        ObjectNode retrieve = objectMapper.createObjectNode();
        retrieve.putArray("skuParents").add(skuParent);
        ArrayNode codes = retrieve.putArray("attributeCodes");
        for (int index = 1; index <= MAX_NOON_IMAGES; index++) codes.add("image_url_" + index);
        JsonNode product = noonAdapter.postJson(session, NoonProductGateway.ZSKU_RETRIEVE_URL, retrieve, true).path(skuParent);
        if (product.isMissingNode() || product.isNull()) {
            return false;
        }
        JsonNode common = product.path("attributes").path("common");
        List<String> actual = new ArrayList<>();
        for (int index = 1; index <= MAX_NOON_IMAGES; index++) {
            String value = common.path("image_url_" + index).asText("").trim();
            if (StringUtils.hasText(value)) actual.add(value);
        }
        return actual.equals(expectedUrls);
    }

    private String upload(
            NoonSession session,
            ProductImagePublishAsset image
    ) {
        JsonNode response = noonAdapter.postMultipartFile(
                session,
                ASSET_UPLOAD_URL,
                "file",
                image.fileName,
                image.contentType,
                image.content,
                true,
                null
        );
        for (String key : List.of("upload_path", "uploadPath", "path", "url")) {
            String value = response.path(key).asText("").trim();
            if (StringUtils.hasText(value)) return value;
        }
        throw new IllegalStateException("Noon 图片上传响应缺少 upload_path。");
    }

    private List<String> checkpointUrls(
            ProductImagePublishCheckpoint checkpoint,
            List<ProductImagePublishAsset> localImages
    ) {
        List<String> urls = new ArrayList<>();
        for (ProductImagePublishAsset localImage : localImages) {
            String noonUrl = checkpoint.noonUrl(localImage.sourceUrl, localImage.sha256);
            if (!StringUtils.hasText(noonUrl)) {
                return null;
            }
            urls.add(noonUrl);
        }
        return urls;
    }

    private void saveCheckpoint(
            ProductImagePublishCheckpoint checkpoint,
            Consumer<String> checkpointSaver
    ) {
        if (checkpointSaver == null) {
            return;
        }
        checkpointSaver.accept(checkpoint.toJson(objectMapper));
    }

    private ProductWriteAuthRequiredException promoteWriteProgress(
            ProductWriteAuthRequiredException exception
    ) {
        if (exception.isWriteMayHaveOccurred()) {
            return exception;
        }
        return new ProductWriteAuthRequiredException(
                exception.getRecoveryId(),
                true,
                exception.getMessage(),
                exception
        );
    }

    private String first(String... values) {
        for (String value : values) if (StringUtils.hasText(value)) return value.trim();
        return null;
    }

}
