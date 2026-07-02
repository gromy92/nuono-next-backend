package com.nuono.next.product;

import com.nuono.next.infrastructure.mapper.ProductImageProfileMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductImageMetadataService {
    private static final Logger log = LoggerFactory.getLogger(ProductImageMetadataService.class);
    private static final int REMOTE_METADATA_TIMEOUT_MS = 5000;
    private static final long MAX_METADATA_DOWNLOAD_BYTES = 20L * 1024L * 1024L;

    private final ProductImageProfileMapper mapper;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "product-image-metadata");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<String> runningJobs = ConcurrentHashMap.newKeySet();

    public ProductImageMetadataService(ProductImageProfileMapper mapper) {
        this.mapper = mapper;
    }

    public ProductImageAssetMetadataView uploadedImageMetadata(MultipartFile file) throws IOException {
        ProductImageAssetMetadataView view = new ProductImageAssetMetadataView();
        view.setContentType(normalizeContentType(file.getContentType()));
        view.setSizeBytes(file.getSize());
        try (InputStream input = file.getInputStream()) {
            BufferedImage image = ImageIO.read(input);
            if (image != null) {
                view.setWidthPx(image.getWidth());
                view.setHeightPx(image.getHeight());
            }
        }
        return view;
    }

    public ProductImageAssetMetadataView assetMetadata(
            Long ownerUserId,
            String storeCode,
            Long productMasterId,
            String imageUrl
    ) {
        Long resolvedOwnerUserId = requireOwnerUserId(ownerUserId);
        String normalizedStoreCode = requireStoreCode(storeCode);
        Long resolvedProductMasterId = requireProductMasterId(productMasterId);
        String normalizedImageUrl = requireImageUrl(imageUrl);
        if (mapper.countAccessibleProductImage(
                resolvedOwnerUserId,
                normalizedStoreCode,
                resolvedProductMasterId,
                normalizedImageUrl
        ) == 0) {
            throw new ProductImageProfileNotFoundException("商品图资料不存在或无权访问。");
        }

        ProductImageAssetMetadataView view = toMetadataView(mapper.selectCurrentProductImageByUrl(
                resolvedProductMasterId,
                normalizedImageUrl
        ));
        if (!isCompleteMetadata(view)) {
            requestCompletion(resolvedProductMasterId, normalizedImageUrl);
        }
        return view;
    }

    public void requestCompletion(Long productMasterId, String imageUrl) {
        String key = productMasterId + "::" + imageUrl;
        if (!runningJobs.add(key)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                ProductImageAssetMetadataView remote = readRemoteImageMetadata(imageUrl);
                if (hasMetadata(remote)) {
                    mapper.updateCurrentProductImageMetadata(productMasterId, imageUrl, remote);
                }
            } catch (RuntimeException exception) {
                log.warn("Product image metadata completion failed. productMasterId={}, imageUrl={}",
                        productMasterId,
                        imageUrl,
                        exception
                );
            } finally {
                runningJobs.remove(key);
            }
        }, executor);
    }

    @PreDestroy
    public void shutdown() {
        runningJobs.clear();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private ProductImageAssetMetadataView toMetadataView(ProductImageProfileAssetRecord record) {
        ProductImageAssetMetadataView view = new ProductImageAssetMetadataView();
        if (record == null) {
            return view;
        }
        view.setContentType(record.getContentType());
        view.setSizeBytes(record.getSizeBytes());
        view.setWidthPx(record.getWidthPx());
        view.setHeightPx(record.getHeightPx());
        return view;
    }

    private boolean hasMetadata(ProductImageAssetMetadataView view) {
        return view != null
                && (StringUtils.hasText(view.getContentType())
                || view.getSizeBytes() != null
                || view.getWidthPx() != null
                || view.getHeightPx() != null);
    }

    private boolean isCompleteMetadata(ProductImageAssetMetadataView view) {
        return view != null
                && view.getSizeBytes() != null
                && view.getWidthPx() != null
                && view.getHeightPx() != null;
    }

    private ProductImageAssetMetadataView readRemoteImageMetadata(String imageUrl) {
        try {
            ProductImageAssetMetadataView head;
            try {
                head = requestRemoteImageMetadata(imageUrl, "HEAD", false);
            } catch (IOException exception) {
                head = new ProductImageAssetMetadataView();
            }
            ProductImageAssetMetadataView get = requestRemoteImageMetadata(imageUrl, "GET", true);
            if (get.getContentType() == null) {
                get.setContentType(head.getContentType());
            }
            if (get.getSizeBytes() == null) {
                get.setSizeBytes(head.getSizeBytes());
            }
            return get;
        } catch (IOException exception) {
            throw new IllegalArgumentException("图片大小读取失败。", exception);
        }
    }

    private ProductImageAssetMetadataView requestRemoteImageMetadata(
            String rawUrl,
            String method,
            boolean countBodyWhenNeeded
    ) throws IOException {
        URL url = URI.create(rawUrl).toURL();
        String protocol = url.getProtocol();
        if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
            throw new IllegalArgumentException("图片链接协议不支持。");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(REMOTE_METADATA_TIMEOUT_MS);
        connection.setReadTimeout(REMOTE_METADATA_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod(method);
        connection.setRequestProperty("User-Agent", "NuonoProductImageMetadata/1.0");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 400) {
                throw new IOException("图片服务返回 " + status);
            }
            ProductImageAssetMetadataView view = new ProductImageAssetMetadataView();
            view.setContentType(trimToNull(connection.getContentType()));
            long contentLength = connection.getContentLengthLong();
            if (countBodyWhenNeeded) {
                byte[] bytes = readResponseBody(connection);
                view.setSizeBytes(contentLength >= 0 ? contentLength : (long) bytes.length);
                applyImageDimensions(view, bytes);
            } else if (contentLength >= 0) {
                view.setSizeBytes(contentLength);
            }
            return view;
        } finally {
            connection.disconnect();
        }
    }

    private byte[] readResponseBody(HttpURLConnection connection) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try (InputStream input = connection.getInputStream()) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                if ((long) output.size() + read > MAX_METADATA_DOWNLOAD_BYTES) {
                    throw new IOException("图片文件超过元数据读取上限");
                }
                output.write(buffer, 0, read);
            }
        }
        return output.toByteArray();
    }

    private void applyImageDimensions(ProductImageAssetMetadataView view, byte[] bytes) throws IOException {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(input);
            if (image != null) {
                view.setWidthPx(image.getWidth());
                view.setHeightPx(image.getHeight());
            }
        }
    }

    private Long requireOwnerUserId(Long value) {
        if (value == null) {
            throw new IllegalArgumentException("老板上下文不能为空。");
        }
        return value;
    }

    private Long requireProductMasterId(Long value) {
        if (value == null) {
            throw new IllegalArgumentException("商品 ID 不能为空。");
        }
        return value;
    }

    private String requireStoreCode(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            throw new IllegalArgumentException("店铺编码不能为空。");
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private String requireImageUrl(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            throw new IllegalArgumentException("图片链接不能为空。");
        }
        return value;
    }

    private String normalizeContentType(String contentType) {
        String normalized = trimToNull(contentType);
        if (normalized == null) {
            return null;
        }
        int parameterIndex = normalized.indexOf(';');
        return (parameterIndex >= 0 ? normalized.substring(0, parameterIndex) : normalized)
                .trim()
                .toLowerCase();
    }

    private String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
