package com.nuono.next.operationsskin;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

final class OperationsSkinAssetFileSupport {
    static final String UPLOAD_DIR_PROPERTY = "nuono.next.operationSkinUploadDir";
    private static final String INVALID_STORE_CODE = "店铺编码不合法。";
    private static final String INVALID_UPLOAD_DIRECTORY = "图片目录不合法。";

    private OperationsSkinAssetFileSupport() {
    }

    static Path uploadDir() {
        String configuredProperty = System.getProperty(UPLOAD_DIR_PROPERTY);
        if (StringUtils.hasText(configuredProperty)) {
            return Paths.get(configuredProperty);
        }
        String configuredDir = System.getenv("NUONO_NEXT_OPERATION_SKIN_UPLOAD_DIR");
        if (StringUtils.hasText(configuredDir)) {
            return Paths.get(configuredDir);
        }
        return Paths.get(System.getProperty("java.io.tmpdir"), "nuono-next-operation-skins");
    }

    static Path storeUploadDir(String storeCode) {
        Path root = uploadRoot();
        Path storeDir = resolveStoreUploadDir(root, storeCode);
        validateExistingDirectory(root);
        validateExistingDirectory(storeDir);
        return storeDir;
    }

    static Path ensureStoreUploadDir(String storeCode) throws IOException {
        Path root = uploadRoot();
        Path storeDir = resolveStoreUploadDir(root, storeCode);
        ensureDirectory(root, true);
        ensureDirectory(storeDir, false);
        validateExistingDirectory(root);
        validateExistingDirectory(storeDir);
        return storeDir;
    }

    private static Path uploadRoot() {
        return uploadDir().toAbsolutePath().normalize();
    }

    private static Path resolveStoreUploadDir(Path root, String storeCode) {
        String trimmed = storeCode == null ? "" : storeCode.trim();
        if (".".equals(trimmed) || "..".equals(trimmed)) {
            throw new IllegalArgumentException(INVALID_STORE_CODE);
        }
        Path resolved = root.resolve(safeStoreCode(storeCode)).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException(INVALID_STORE_CODE);
        }
        return resolved;
    }

    private static void ensureDirectory(Path directory, boolean allowParents) throws IOException {
        validateExistingDirectory(directory);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            try {
                if (allowParents) {
                    Files.createDirectories(directory);
                } else {
                    Files.createDirectory(directory);
                }
            } catch (FileAlreadyExistsException ignored) {
                // Validate the directory state below; an existing symlink or file is not acceptable.
            }
        }
        validateExistingDirectory(directory);
    }

    private static void validateExistingDirectory(Path directory) {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(INVALID_UPLOAD_DIRECTORY);
        }
    }

    static String safeStoreCode(String storeCode) {
        if (!StringUtils.hasText(storeCode)) {
            return "_";
        }
        StringBuilder safe = new StringBuilder();
        String normalized = storeCode.trim().toUpperCase(Locale.ROOT);
        for (int index = 0; index < normalized.length(); index++) {
            char value = normalized.charAt(index);
            if ((value >= 'A' && value <= 'Z')
                    || (value >= '0' && value <= '9')
                    || value == '-'
                    || value == '_'
                    || value == '.') {
                safe.append(value);
            } else {
                safe.append('_');
            }
        }
        String value = safe.toString();
        return value.isEmpty() || ".".equals(value) || "..".equals(value) ? "_" : value;
    }

    static String imageExtension(MultipartFile file) {
        String originalExtension = file == null ? null : StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (originalExtension != null && originalExtension.matches("(?i)avif|gif|jpe?g|png|webp")) {
            return originalExtension.toLowerCase(Locale.ROOT);
        }
        String contentType = file == null ? "" : String.valueOf(file.getContentType()).toLowerCase(Locale.ROOT);
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

    static String safeRandomFilename(MultipartFile file) {
        return UUID.randomUUID() + "." + imageExtension(file);
    }

    static boolean isUnsafeFilename(String filename) {
        return filename == null || filename.contains("..") || filename.contains("/") || filename.contains("\\");
    }
}
