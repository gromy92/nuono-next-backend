package com.nuono.next.productlisting;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import org.springframework.util.StringUtils;

final class HttpClientProductListingImageDownloader implements ProductListingImageDownloader {
    private static final String LOCAL_PRODUCT_IMAGE_ASSET_PREFIX = "/api/product-master/image-assets/";
    private static final Duration IMAGE_DOWNLOAD_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final String IMAGE_DOWNLOAD_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36";
    private static final String IMAGE_DOWNLOAD_ACCEPT =
            "image/jpeg,image/png;q=0.95,*/*;q=0.1";

    private final HttpClient httpClient;

    HttpClientProductListingImageDownloader() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    HttpClientProductListingImageDownloader(HttpClient httpClient) {
        this.httpClient = httpClient == null ? HttpClient.newHttpClient() : httpClient;
    }

    @Override
    public ProductListingImageDownload download(String imageUrl) {
        String source = StringUtils.hasText(imageUrl) ? imageUrl.trim() : "";
        if (source.startsWith(LOCAL_PRODUCT_IMAGE_ASSET_PREFIX)) {
            return downloadLocalProductImageAsset(source);
        }
        URI uri = URI.create(StringUtils.hasText(imageUrl) ? imageUrl.trim() : "");
        validateImageUri(uri);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(IMAGE_DOWNLOAD_TIMEOUT)
                .setHeader("User-Agent", IMAGE_DOWNLOAD_USER_AGENT)
                .setHeader("Accept", IMAGE_DOWNLOAD_ACCEPT)
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream bodyStream = response.body()) {
                URI finalUri = response.uri() == null ? uri : response.uri();
                validateImageUri(finalUri);
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("HTTP " + response.statusCode());
                }
                String contentType = response.headers().firstValue("content-type").orElse("");
                validateContentType(contentType);
                response.headers()
                        .firstValueAsLong("content-length")
                        .ifPresent(HttpClientProductListingImageDownloader::validateContentLength);
                byte[] body = readLimited(bodyStream);
                if (body.length == 0) {
                    throw new IllegalStateException("empty image response");
                }
                return new ProductListingImageDownload(fileName(finalUri, contentType), contentType, body);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Download product listing image failed: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Download product listing image interrupted: " + exception.getMessage(), exception);
        }
    }

    private ProductListingImageDownload downloadLocalProductImageAsset(String imageUrl) {
        String filename = imageUrl.substring(LOCAL_PRODUCT_IMAGE_ASSET_PREFIX.length()).trim();
        if (!StringUtils.hasText(filename)
                || filename.contains("..")
                || filename.contains("/")
                || filename.contains("\\")) {
            throw new IllegalArgumentException("Image URL is required.");
        }
        Path file = productImageUploadDir().resolve(filename).normalize();
        if (!file.startsWith(productImageUploadDir())) {
            throw new IllegalArgumentException("Image URL is required.");
        }
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            throw new IllegalStateException("Local product image asset does not exist.");
        }
        try {
            byte[] content = Files.readAllBytes(file);
            if (content.length == 0) {
                throw new IllegalStateException("empty image response");
            }
            validateContentLength(content.length);
            String contentType = Files.probeContentType(file);
            if (!StringUtils.hasText(contentType)) {
                contentType = contentTypeFromFileName(filename);
            }
            validateContentType(contentType);
            return new ProductListingImageDownload(filename, contentType, content);
        } catch (IOException exception) {
            throw new IllegalStateException("Read local product image asset failed: " + exception.getMessage(), exception);
        }
    }

    private static Path productImageUploadDir() {
        String configuredDir = System.getenv("NUONO_NEXT_PRODUCT_IMAGE_UPLOAD_DIR");
        if (StringUtils.hasText(configuredDir)) {
            return Paths.get(configuredDir).normalize();
        }
        return Paths.get(System.getProperty("java.io.tmpdir"), "nuono-next-product-images").normalize();
    }

    private static String contentTypeFromFileName(String filename) {
        String lower = StringUtils.hasText(filename) ? filename.toLowerCase(Locale.ROOT) : "";
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        return "image/jpeg";
    }

    private static void validateImageUri(URI uri) {
        if (uri == null || !StringUtils.hasText(uri.getScheme())) {
            throw new IllegalArgumentException("Image URL is required.");
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Unsupported image URL scheme.");
        }
        if (StringUtils.hasText(uri.getUserInfo())) {
            throw new IllegalArgumentException("Image URL user info is not allowed.");
        }
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new IllegalArgumentException("Image URL host is required.");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalizedHost) || normalizedHost.endsWith(".local")) {
            throw new IllegalArgumentException("Image URL host is not allowed.");
        }
        for (InetAddress address : resolveHost(host)) {
            if (isBlockedAddress(address)) {
                throw new IllegalArgumentException("Image URL host is not allowed.");
            }
        }
    }

    private static InetAddress[] resolveHost(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Image URL host cannot be resolved.", exception);
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
            throw new IllegalStateException("Unsupported image content type.");
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("image/jpeg") || normalized.startsWith("image/jpg") || normalized.startsWith("image/png")) {
            return;
        }
        throw new IllegalStateException("Unsupported image content type.");
    }

    private static void validateContentLength(long contentLength) {
        if (contentLength > MAX_IMAGE_BYTES) {
            throw new IllegalStateException("image response exceeds max size");
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
                    throw new IllegalStateException("image response exceeds max size");
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
        if (!name.contains(".")) {
            name += extension(contentType);
        }
        return name;
    }

    private static String extension(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return ".jpg";
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        if (normalized.contains("png")) {
            return ".png";
        }
        if (normalized.contains("webp")) {
            return ".webp";
        }
        if (normalized.contains("gif")) {
            return ".gif";
        }
        return ".jpg";
    }
}
