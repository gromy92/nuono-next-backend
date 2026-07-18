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
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
class ProductImageNoonPublisher {
    private static final String ASSET_UPLOAD_URL =
            "https://noon-catalog.noon.partners/_svc/mp-partner-catalog/catalog/asset/upload";
    private static final int MAX_NOON_IMAGES = 20;
    private final StoreSyncMapper storeSyncMapper;
    private final ProductNoonAdapter noonAdapter;
    private final ObjectMapper objectMapper;

    ProductImageNoonPublisher(StoreSyncMapper storeSyncMapper, ProductNoonAdapter noonAdapter, ObjectMapper objectMapper) {
        this.storeSyncMapper = storeSyncMapper;
        this.noonAdapter = noonAdapter;
        this.objectMapper = objectMapper;
    }

    List<String> publish(Long ownerUserId, String storeCode, String skuParent, List<String> localImageUrls) {
        if (!StringUtils.hasText(skuParent)) throw new IllegalArgumentException("该商品尚未在 Noon 上线，不能发布图片。");
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
        List<String> noonUrls = new ArrayList<>();
        for (String localUrl : localImageUrls) noonUrls.add(upload(session, localUrl));

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
        noonAdapter.postWriteJson(session, NoonProductGateway.ZSKU_UPSERT_URL, body, true);

        ObjectNode retrieve = objectMapper.createObjectNode();
        retrieve.putArray("skuParents").add(skuParent);
        ArrayNode codes = retrieve.putArray("attributeCodes");
        for (int index = 1; index <= MAX_NOON_IMAGES; index++) codes.add("image_url_" + index);
        JsonNode product = noonAdapter.postJson(session, NoonProductGateway.ZSKU_RETRIEVE_URL, retrieve, true).path(skuParent);
        if (product.isMissingNode() || product.isNull()) throw new IllegalStateException("Noon 未返回商品图片回读结果。");
        JsonNode common = product.path("attributes").path("common");
        List<String> actual = new ArrayList<>();
        for (int index = 1; index <= MAX_NOON_IMAGES; index++) {
            String value = common.path("image_url_" + index).asText("").trim();
            if (StringUtils.hasText(value)) actual.add(value);
        }
        if (!actual.equals(noonUrls)) {
            throw new IllegalStateException("Noon 图片回读校验失败：数量或顺序与审核套图不一致。");
        }
        return noonUrls;
    }

    private String upload(NoonSession session, String imageUrl) {
        LocalImage image = readLocal(imageUrl);
        JsonNode response = noonAdapter.postMultipartFile(
                session, ASSET_UPLOAD_URL, "file", image.fileName, "image/png", image.content, true, null
        );
        for (String key : List.of("upload_path", "uploadPath", "path", "url")) {
            String value = response.path(key).asText("").trim();
            if (StringUtils.hasText(value)) return value;
        }
        throw new IllegalStateException("Noon 图片上传响应缺少 upload_path。");
    }

    private LocalImage readLocal(String imageUrl) {
        String prefix = "/api/product-images/assets/";
        if (!StringUtils.hasText(imageUrl) || !imageUrl.startsWith(prefix)) {
            throw new IllegalArgumentException("套图包含无法发布的图片地址。");
        }
        String relative = URLDecoder.decode(imageUrl.substring(prefix.length()), StandardCharsets.UTF_8);
        String[] parts = relative.split("/", 2);
        if (parts.length != 2 || parts[0].contains("..") || parts[1].contains("..") || parts[1].contains("/")) {
            throw new IllegalArgumentException("套图图片地址不合法。");
        }
        Path root = ProductImageAssetFileSupport.productImageUploadDir().resolve("profiles").normalize();
        Path file = root.resolve(parts[0]).resolve(parts[1]).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new IllegalStateException("套图图片文件不存在。");
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length == 0 || bytes.length > 10 * 1024 * 1024) throw new IllegalStateException("套图图片为空或超过 10MB。");
            return new LocalImage(parts[1], bytes);
        } catch (IOException exception) {
            throw new IllegalStateException("读取套图图片失败：" + exception.getMessage(), exception);
        }
    }

    private String first(String... values) {
        for (String value : values) if (StringUtils.hasText(value)) return value.trim();
        return null;
    }

    private static final class LocalImage {
        private final String fileName;
        private final byte[] content;
        private LocalImage(String fileName, byte[] content) { this.fileName = fileName; this.content = content; }
    }
}
