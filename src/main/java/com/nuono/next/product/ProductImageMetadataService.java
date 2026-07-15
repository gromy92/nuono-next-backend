package com.nuono.next.product;

import com.nuono.next.infrastructure.mapper.ProductImageProfileMapper;
import java.awt.image.BufferedImage;
import java.awt.color.ColorSpace;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Locale;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
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
            applyImageMetadata(view, input.readAllBytes());
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
        view.setHorizontalPpi(record.getHorizontalPpi());
        view.setVerticalPpi(record.getVerticalPpi());
        view.setColorSpace(record.getColorSpace());
        return view;
    }

    private boolean hasMetadata(ProductImageAssetMetadataView view) {
        return view != null
                && (StringUtils.hasText(view.getContentType())
                || view.getSizeBytes() != null
                || view.getWidthPx() != null
                || view.getHeightPx() != null
                || view.getHorizontalPpi() != null
                || view.getVerticalPpi() != null
                || StringUtils.hasText(view.getColorSpace()));
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
                applyImageMetadata(view, bytes);
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

    private void applyImageMetadata(ProductImageAssetMetadataView view, byte[] bytes) throws IOException {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(input);
            if (image != null) {
                view.setWidthPx(image.getWidth());
                view.setHeightPx(image.getHeight());
                ColorSpace colorSpace = image.getColorModel().getColorSpace();
                if (colorSpace != null) {
                    if (colorSpace.isCS_sRGB()) {
                        view.setColorSpace("sRGB");
                    } else if (colorSpace.getType() == ColorSpace.TYPE_RGB) {
                        view.setColorSpace("RGB");
                    } else {
                        view.setColorSpace("OTHER");
                    }
                }
            }
        }
        applyResolutionMetadata(view, bytes);
    }

    private void applyResolutionMetadata(ProductImageAssetMetadataView view, byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                return;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                IIOMetadata metadata = reader.getImageMetadata(0);
                applyStandardResolution(view, metadata);
                if (view.getHorizontalPpi() == null || view.getVerticalPpi() == null) {
                    applyJpegResolution(view, metadata);
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            log.debug("Product image PPI metadata unavailable", exception);
        }
    }

    private void applyStandardResolution(ProductImageAssetMetadataView view, IIOMetadata metadata) {
        if (metadata == null || !metadata.isStandardMetadataFormatSupported()) {
            return;
        }
        Node dimension = findNode(metadata.getAsTree("javax_imageio_1.0"), "Dimension");
        BigDecimal horizontalMillimeters = decimalAttribute(findNode(dimension, "HorizontalPixelSize"), "value");
        BigDecimal verticalMillimeters = decimalAttribute(findNode(dimension, "VerticalPixelSize"), "value");
        view.setHorizontalPpi(ppiFromMillimeters(horizontalMillimeters));
        view.setVerticalPpi(ppiFromMillimeters(verticalMillimeters));
    }

    private void applyJpegResolution(ProductImageAssetMetadataView view, IIOMetadata metadata) {
        if (metadata == null) {
            return;
        }
        for (String formatName : metadata.getMetadataFormatNames()) {
            if (!"javax_imageio_jpeg_image_1.0".equals(formatName)) {
                continue;
            }
            Node jfif = findNode(metadata.getAsTree(formatName), "app0JFIF");
            BigDecimal xDensity = decimalAttribute(jfif, "Xdensity");
            BigDecimal yDensity = decimalAttribute(jfif, "Ydensity");
            String units = stringAttribute(jfif, "resUnits");
            if (xDensity == null || yDensity == null || units == null) {
                return;
            }
            if ("1".equals(units)) {
                view.setHorizontalPpi(xDensity);
                view.setVerticalPpi(yDensity);
            } else if ("2".equals(units)) {
                view.setHorizontalPpi(xDensity.multiply(BigDecimal.valueOf(2.54)));
                view.setVerticalPpi(yDensity.multiply(BigDecimal.valueOf(2.54)));
            }
        }
    }

    private BigDecimal ppiFromMillimeters(BigDecimal millimetersPerPixel) {
        if (millimetersPerPixel == null || millimetersPerPixel.signum() <= 0) {
            return null;
        }
        return BigDecimal.valueOf(25.4).divide(millimetersPerPixel, 2, RoundingMode.HALF_UP);
    }

    private Node findNode(Node root, String name) {
        if (root == null) {
            return null;
        }
        if (name.equals(root.getNodeName())) {
            return root;
        }
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            Node found = findNode(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private BigDecimal decimalAttribute(Node node, String name) {
        String value = stringAttribute(node, name);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String stringAttribute(Node node, String name) {
        NamedNodeMap attributes = node == null ? null : node.getAttributes();
        Node attribute = attributes == null ? null : attributes.getNamedItem(name);
        return attribute == null ? null : attribute.getNodeValue();
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
