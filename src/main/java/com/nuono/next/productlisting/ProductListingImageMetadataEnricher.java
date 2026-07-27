package com.nuono.next.productlisting;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
final class ProductListingImageMetadataEnricher {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ProductListingImageMetadataEnricher.class);

    private final ProductListingImageDownloader imageDownloader;

    ProductListingImageMetadataEnricher() {
        this(new HttpClientProductListingImageDownloader());
    }

    ProductListingImageMetadataEnricher(ProductListingImageDownloader imageDownloader) {
        this.imageDownloader = imageDownloader == null
                ? new HttpClientProductListingImageDownloader()
                : imageDownloader;
    }

    boolean enrichMissingDimensions(ProductListingDraftCommand command) {
        if (command == null || command.getImageUrls() == null || command.getImageUrls().isEmpty()) {
            return false;
        }

        List<Map<String, Object>> metadata = copyMetadata(command.getImageAssetMetadata());
        Map<String, Map<String, Object>> metadataByUrl = indexMetadata(metadata);
        boolean changed = false;
        for (String imageUrlValue : command.getImageUrls()) {
            String imageUrl = normalizeText(imageUrlValue);
            if (!StringUtils.hasText(imageUrl)) {
                continue;
            }
            Map<String, Object> current = metadataByUrl.get(imageUrl);
            if (hasDimensions(current)) {
                continue;
            }
            try {
                int[] dimensions = readDimensions(imageDownloader.download(imageUrl));
                Map<String, Object> enriched = current;
                if (enriched == null) {
                    enriched = new LinkedHashMap<>();
                    enriched.put("imageUrl", imageUrl);
                    metadata.add(enriched);
                    metadataByUrl.put(imageUrl, enriched);
                }
                enriched.put("width", dimensions[0]);
                enriched.put("height", dimensions[1]);
                changed = true;
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Unable to read product listing image dimensions for {}: {}",
                        imageUrl,
                        exception.getMessage()
                );
            }
        }
        if (changed) {
            command.setImageAssetMetadata(metadata);
        }
        return changed;
    }

    private static List<Map<String, Object>> copyMetadata(List<Map<String, Object>> source) {
        List<Map<String, Object>> copy = new ArrayList<>();
        if (source == null) {
            return copy;
        }
        for (Map<String, Object> item : source) {
            if (item != null) {
                copy.add(new LinkedHashMap<>(item));
            }
        }
        return copy;
    }

    private static Map<String, Map<String, Object>> indexMetadata(List<Map<String, Object>> metadata) {
        Map<String, Map<String, Object>> byUrl = new LinkedHashMap<>();
        for (Map<String, Object> item : metadata) {
            String imageUrl = normalizeText(item.get("imageUrl"));
            if (StringUtils.hasText(imageUrl)) {
                byUrl.put(imageUrl, item);
            }
        }
        return byUrl;
    }

    private static boolean hasDimensions(Map<String, Object> metadata) {
        return metadata != null
                && positiveInteger(metadata.get("width")) != null
                && positiveInteger(metadata.get("height")) != null;
    }

    private static int[] readDimensions(ProductListingImageDownload download) {
        byte[] content = download == null ? null : download.content;
        if (content == null || content.length == 0) {
            throw new IllegalStateException("empty image response");
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(content);
             ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) {
                throw new IllegalStateException("unsupported image data");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new IllegalStateException("unsupported image data");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw new IllegalStateException("invalid image dimensions");
                }
                return new int[]{width, height};
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "read image dimensions failed: " + exception.getMessage(),
                    exception
            );
        }
    }

    private static Integer positiveInteger(Object value) {
        if (value instanceof Number) {
            int number = ((Number) value).intValue();
            return number > 0 ? number : null;
        }
        String text = normalizeText(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            int number = (int) Math.round(Double.parseDouble(text));
            return number > 0 ? number : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalizeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
