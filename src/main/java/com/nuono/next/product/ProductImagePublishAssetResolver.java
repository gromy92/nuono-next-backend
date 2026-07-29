package com.nuono.next.product;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Locale;
import org.springframework.util.StringUtils;

final class ProductImagePublishAssetResolver {
    static final URI DEFAULT_TRUSTED_STATIC_ROOT =
            URI.create("https://www.nuoon.com/ai/product-image-results/");
    private static final String MANAGED_ASSET_PREFIX = "/api/product-images/assets/";
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(30);

    private final Path managedAssetRoot;
    private final URI trustedStaticRoot;
    private final HttpClient httpClient;

    ProductImagePublishAssetResolver() {
        this(
                ProductImageAssetFileSupport.productImageUploadDir().resolve("profiles"),
                DEFAULT_TRUSTED_STATIC_ROOT,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build()
        );
    }
    ProductImagePublishAssetResolver(
            Path managedAssetRoot,
            URI trustedStaticRoot,
            HttpClient httpClient
    ) {
        if (managedAssetRoot == null || httpClient == null) {
            throw new IllegalArgumentException("商品图发布资产解析器配置不完整。");
        }
        this.managedAssetRoot = managedAssetRoot.toAbsolutePath().normalize();
        this.trustedStaticRoot = requireTrustedRoot(trustedStaticRoot);
        this.httpClient = httpClient;
    }
    static void validateDefaultAddress(String imageUrl) {
        validateAddress(imageUrl, DEFAULT_TRUSTED_STATIC_ROOT);
    }

    void validateAddress(String imageUrl) {
        validateAddress(imageUrl, trustedStaticRoot);
    }
    ProductImagePublishAsset resolve(String imageUrl) {
        validateAddress(imageUrl);
        String normalizedUrl = imageUrl.trim();
        if (normalizedUrl.startsWith(MANAGED_ASSET_PREFIX)) {
            return readManagedAsset(normalizedUrl);
        }
        return downloadTrustedStaticAsset(normalizedUrl);
    }
    private ProductImagePublishAsset readManagedAsset(String imageUrl) {
        String relative = decode(imageUrl.substring(MANAGED_ASSET_PREFIX.length()));
        try {
            Path root = managedAssetRoot.toRealPath();
            Path file = root.resolve(relative).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                throw new IllegalStateException("套图图片文件不存在。");
            }
            file = file.toRealPath();
            if (!file.startsWith(root)) {
                throw new IllegalStateException("套图图片文件不存在。");
            }
            byte[] content = readLimited(Files.newInputStream(file));
            String contentType = localContentType(
                    file.getFileName().toString(),
                    Files.probeContentType(file)
            );
            return resolved(imageUrl, file.getFileName().toString(), contentType, content);
        } catch (IOException exception) {
            throw new IllegalStateException("读取套图图片失败：" + exception.getMessage(), exception);
        }
    }
    private ProductImagePublishAsset downloadTrustedStaticAsset(String imageUrl) {
        URI uri = URI.create(imageUrl);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(DOWNLOAD_TIMEOUT)
                .header("Accept", "image/png,image/jpeg")
                .build();
        try {
            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                validateAddress(response.uri().toString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("受管套图图片返回 HTTP " + response.statusCode() + "。");
                }
                response.headers().firstValueAsLong("content-length")
                        .ifPresent(ProductImagePublishAssetResolver::requireAllowedLength);
                String fileName = fileName(response.uri());
                String contentType = remoteContentType(
                        response.headers().firstValue("content-type").orElse("")
                );
                return resolved(imageUrl, fileName, contentType, readLimited(body));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("读取受管套图图片失败：" + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("读取受管套图图片被中断。", exception);
        }
    }
    private static void validateAddress(String imageUrl, URI trustedRoot) {
        if (!StringUtils.hasText(imageUrl)) {
            throw unsupportedAddress();
        }
        String normalizedUrl = imageUrl.trim();
        if (normalizedUrl.startsWith(MANAGED_ASSET_PREFIX)) {
            validateRelativePath(
                    normalizedUrl.substring(MANAGED_ASSET_PREFIX.length()),
                    true
            );
            return;
        }
        URI uri;
        try {
            uri = URI.create(normalizedUrl);
        } catch (IllegalArgumentException exception) {
            throw unsupportedAddress();
        }
        if (!sameOrigin(uri, trustedRoot)
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || !StringUtils.hasText(uri.getRawPath())
                || !uri.getRawPath().startsWith(trustedRoot.getRawPath())) {
            throw unsupportedAddress();
        }
        validateRelativePath(
                uri.getRawPath().substring(trustedRoot.getRawPath().length()),
                false
        );
    }
    private static void validateRelativePath(String rawRelative, boolean exactTwoParts) {
        String relative = decode(rawRelative);
        String[] parts = relative.split("/", -1);
        if (parts.length < 2 || (exactTwoParts && parts.length != 2)) {
            throw unsupportedAddress();
        }
        for (String part : parts) {
            if (!StringUtils.hasText(part)
                    || ".".equals(part)
                    || "..".equals(part)
                    || part.contains("\\")
                    || !part.matches("[A-Za-z0-9._-]+")) {
                throw unsupportedAddress();
            }
        }
        String fileName = parts[parts.length - 1].toLowerCase(Locale.ROOT);
        if (!(fileName.endsWith(".png")
                || fileName.endsWith(".jpg")
                || fileName.endsWith(".jpeg"))) {
            throw new IllegalArgumentException("Noon 图片发布只支持 JPG/PNG 套图资产。");
        }
    }
    private static URI requireTrustedRoot(URI root) {
        if (root == null
                || !root.isAbsolute()
                || !StringUtils.hasText(root.getHost())
                || !StringUtils.hasText(root.getRawPath())
                || !root.getRawPath().endsWith("/")
                || root.getRawUserInfo() != null
                || root.getRawQuery() != null
                || root.getRawFragment() != null) {
            throw new IllegalArgumentException("受管套图静态根地址配置无效。");
        }
        return root;
    }
    private static boolean sameOrigin(URI left, URI right) {
        return left != null
                && right.getScheme().equalsIgnoreCase(left.getScheme())
                && right.getHost().equalsIgnoreCase(left.getHost())
                && effectivePort(right) == effectivePort(left);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw unsupportedAddress();
        }
    }

    private static String fileName(URI uri) {
        String path = decode(uri.getRawPath());
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String localContentType(String fileName, String declaredContentType) {
        String declared = String.valueOf(declaredContentType).toLowerCase(Locale.ROOT);
        String normalizedName = String.valueOf(fileName).toLowerCase(Locale.ROOT);
        if (declared.startsWith("image/png") || normalizedName.endsWith(".png")) {
            return "image/png";
        }
        if (declared.startsWith("image/jpeg")
                || declared.startsWith("image/jpg")
                || normalizedName.endsWith(".jpg")
                || normalizedName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        throw new IllegalStateException("Noon 图片发布只支持 JPG/PNG 套图资产。");
    }

    private static String remoteContentType(String declaredContentType) {
        String declared = String.valueOf(declaredContentType).toLowerCase(Locale.ROOT);
        if (declared.startsWith("image/png")) {
            return "image/png";
        }
        if (declared.startsWith("image/jpeg") || declared.startsWith("image/jpg")) {
            return "image/jpeg";
        }
        throw new IllegalStateException("受管套图图片返回的内容类型不是 JPG/PNG。");
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        if (input == null) {
            throw new IllegalStateException("套图图片内容为空。");
        }
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = source.read(buffer)) != -1) {
                total += read;
                requireAllowedLength(total);
                output.write(buffer, 0, read);
            }
            if (total == 0) {
                throw new IllegalStateException("套图图片内容为空。");
            }
            return output.toByteArray();
        }
    }

    private static void requireAllowedLength(long length) {
        if (length > MAX_IMAGE_BYTES) {
            throw new IllegalStateException("套图图片超过 10MB，不能发布到 Noon。");
        }
    }

    private static ProductImagePublishAsset resolved(
            String sourceUrl,
            String fileName,
            String contentType,
            byte[] content
    ) {
        return new ProductImagePublishAsset(
                sourceUrl,
                fileName,
                contentType,
                content,
                sha256(content)
        );
    }

    private static String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256。", exception);
        }
    }

    private static IllegalArgumentException unsupportedAddress() {
        return new IllegalArgumentException(
                "套图包含无法发布的图片地址；仅支持受管商品图资产或系统商品图结果。"
        );
    }

}
