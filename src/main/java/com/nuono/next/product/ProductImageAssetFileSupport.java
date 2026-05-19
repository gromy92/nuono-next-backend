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
        String originalExtension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (originalExtension != null && originalExtension.matches("(?i)avif|gif|jpe?g|png|webp")) {
            return originalExtension.toLowerCase();
        }
        String contentType = String.valueOf(file.getContentType()).toLowerCase();
        if (contentType.contains("png")) {
            return "png";
        }
        if (contentType.contains("gif")) {
            return "gif";
        }
        if (contentType.contains("webp")) {
            return "webp";
        }
        if (contentType.contains("avif")) {
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
