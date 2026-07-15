package com.nuono.next.product;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import org.springframework.util.StringUtils;

final class ProductImageAssetRemoteDownloader {
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36";
    private static final String ACCEPT = "image/jpeg,image/png,image/webp,image/gif,image/avif;q=0.95,*/*;q=0.1";

    private final HttpClient httpClient;

    ProductImageAssetRemoteDownloader() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    ProductImageAssetRemoteDownloader(HttpClient httpClient) {
        this.httpClient = httpClient == null ? HttpClient.newHttpClient() : httpClient;
    }

    ProductImageAssetRemoteDownload download(String imageUrl) {
        URI uri = URI.create(normalizeImageUrl(imageUrl));
        validateImageUri(uri);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(DOWNLOAD_TIMEOUT)
                .setHeader("User-Agent", USER_AGENT)
                .setHeader("Accept", ACCEPT)
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                URI finalUri = response.uri() == null ? uri : response.uri();
                validateImageUri(finalUri);
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("远程图片返回 HTTP " + response.statusCode());
                }
                String contentType = response.headers().firstValue("content-type").orElse("");
                validateContentType(contentType);
                response.headers().firstValueAsLong("content-length").ifPresent(ProductImageAssetRemoteDownloader::validateContentLength);
                byte[] content = readLimited(body);
                if (content.length == 0) {
                    throw new IllegalStateException("远程图片内容为空");
                }
                return new ProductImageAssetRemoteDownload(fileName(finalUri, contentType), contentType, content);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("下载远程图片失败：" + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("下载远程图片被中断：" + exception.getMessage(), exception);
        }
    }

    private static String normalizeImageUrl(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return "";
        }
        return imageUrl
                .trim()
                .replaceAll("[\\p{Cntrl}\\s\\u200B\\u200C\\u200D\\uFEFF]+", "");
    }

    private static void validateImageUri(URI uri) {
        if (uri == null || !StringUtils.hasText(uri.getScheme())) {
            throw new IllegalArgumentException("图片 URL 不能为空");
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("仅支持 http/https 图片 URL");
        }
        if (StringUtils.hasText(uri.getUserInfo())) {
            throw new IllegalArgumentException("图片 URL 不允许包含用户信息");
        }
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new IllegalArgumentException("图片 URL 缺少域名");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalizedHost) || normalizedHost.endsWith(".local")) {
            throw new IllegalArgumentException("图片 URL 域名不允许访问本机或内网");
        }
        for (InetAddress address : resolveHost(host)) {
            if (isBlockedAddress(address)) {
                throw new IllegalArgumentException("图片 URL 域名不允许访问本机或内网");
            }
        }
    }

    private static InetAddress[] resolveHost(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("图片 URL 域名无法解析", exception);
        }
    }

    private static boolean isBlockedAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress();
    }

    private static void validateContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            throw new IllegalStateException("远程资源不是图片");
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("image/jpeg")
                || normalized.startsWith("image/jpg")
                || normalized.startsWith("image/png")
                || normalized.startsWith("image/webp")
                || normalized.startsWith("image/gif")
                || normalized.startsWith("image/avif")) {
            return;
        }
        throw new IllegalStateException("远程资源不是图片");
    }

    private static void validateContentLength(long contentLength) {
        if (contentLength > MAX_IMAGE_BYTES) {
            throw new IllegalStateException("远程图片不能超过 8MB");
        }
    }

    private static byte[] readLimited(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return new byte[0];
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > MAX_IMAGE_BYTES) {
                    throw new IllegalStateException("远程图片不能超过 8MB");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static String fileName(URI uri, String contentType) {
        String path = uri == null ? "" : uri.getPath();
        String name = StringUtils.hasText(path) && path.contains("/")
                ? path.substring(path.lastIndexOf('/') + 1)
                : path;
        if (!StringUtils.hasText(name)) {
            name = "image";
        }
        String baseName = name.contains(".")
                ? name.substring(0, name.lastIndexOf('.'))
                : name;
        if (!StringUtils.hasText(baseName)) {
            baseName = "image";
        }
        return baseName + "." + extensionFromContentType(contentType);
    }

    private static String extensionFromContentType(String contentType) {
        String normalized = String.valueOf(contentType).toLowerCase(Locale.ROOT);
        if (normalized.startsWith("image/png")) {
            return "png";
        }
        if (normalized.startsWith("image/gif")) {
            return "gif";
        }
        if (normalized.startsWith("image/webp")) {
            return "webp";
        }
        if (normalized.startsWith("image/avif")) {
            return "avif";
        }
        return "jpg";
    }
}
