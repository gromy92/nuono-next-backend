package com.nuono.next.product;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

final class ProductImageAssetFileSupport {

    private ProductImageAssetFileSupport() {
    }

    static Path productImageUploadDir() {
        String configuredDir = System.getenv("NUONO_NEXT_PRODUCT_IMAGE_UPLOAD_DIR");
        if (StringUtils.hasText(configuredDir)) {
            return Paths.get(configuredDir);
        }
        return Paths.get(System.getProperty("java.io.tmpdir"), "nuono-next-product-images");
    }

    static String imageExtension(MultipartFile file) {
        return imageExtension(file.getOriginalFilename(), file.getContentType());
    }

    static String imageExtension(String originalFileName, String contentType) {
        String originalExtension = StringUtils.getFilenameExtension(originalFileName);
        if (originalExtension != null && originalExtension.matches("(?i)avif|gif|jpe?g|png|webp")) {
            return originalExtension.toLowerCase();
        }
        String normalizedContentType = String.valueOf(contentType).toLowerCase();
        if (normalizedContentType.contains("png")) {
            return "png";
        }
        if (normalizedContentType.contains("gif")) {
            return "gif";
        }
        if (normalizedContentType.contains("webp")) {
            return "webp";
        }
        if (normalizedContentType.contains("avif")) {
            return "avif";
        }
        return "jpg";
    }

    static String sha256Hex(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(file));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                hex.append(String.format("%02x", item));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }
}
