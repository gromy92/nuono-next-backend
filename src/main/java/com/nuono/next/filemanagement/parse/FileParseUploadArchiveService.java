package com.nuono.next.filemanagement.parse;

import com.nuono.next.infrastructure.mapper.FileManagementParseMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("local-db")
public class FileParseUploadArchiveService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String STORAGE_BUCKET = "local-file-management-parse";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "xlsx", "xls", "csv", "txt", "png", "jpg", "jpeg", "webp", "doc", "docx"
    );
    private final FileManagementParseMapper mapper;
    private final FileParseStorageProperties storageProperties;

    public FileParseUploadArchiveService(
            FileManagementParseMapper mapper,
            FileParseStorageProperties storageProperties
    ) {
        this.mapper = mapper;
        this.storageProperties = storageProperties;
    }

    FileParseUploadView archive(
            FileParseTargetPlanRow targetPlan,
            Long userId,
            MultipartFile file
    ) {
        validateUpload(file);
        String originalFileName = sanitizeFileName(file.getOriginalFilename());
        String extension = extractExtension(originalFileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("暂不支持该文件格式：" + extension);
        }

        Long fileId = mapper.nextFileAssetId();
        String uploadId = "UP-" + LocalDate.now().format(DATE_FORMATTER) + "-" + fileId;
        Path rootDir = storageRoot();
        String storageKey = buildStorageKey(fileId, originalFileName);
        Path storagePath = rootDir.resolve(storageKey).normalize();
        if (!storagePath.startsWith(rootDir)) {
            throw new IllegalArgumentException("文件路径不合法。");
        }

        copyToArchive(file, storagePath);
        String sha256Hash = hashArchivedFile(storagePath);
        FileParseFileAssetRow row = toAssetRow(
                fileId,
                uploadId,
                targetPlan,
                userId,
                file,
                originalFileName,
                extension,
                storageKey,
                sha256Hash
        );
        insertAssetOrDeleteArchive(row, storagePath);
        return toUploadView(row);
    }

    FileParseArchivedFile resolve(Long fileId, Long userId, boolean systemAdmin) {
        if (fileId == null) {
            throw new IllegalArgumentException("文件 ID 不能为空。");
        }
        FileParseFileAssetRow row = mapper.selectFileAsset(fileId);
        if (row == null) {
            throw new IllegalArgumentException("文件不存在或已删除。");
        }
        if (!systemAdmin && !userId.equals(row.getUploadedBy())) {
            throw new FileParseAccessDeniedException("当前账号不能访问该文件。");
        }
        if (row.getBoundTaskId() == null
                && row.getExpiresAt() != null
                && row.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("上传文件已过期，请重新上传。");
        }

        Path rootDir = storageRoot();
        Path filePath = rootDir.resolve(row.getStorageKey()).normalize();
        if (!filePath.startsWith(rootDir)) {
            throw new IllegalArgumentException("文件路径不合法。");
        }
        if (!Files.exists(filePath)) {
            throw new IllegalStateException("归档文件不存在。");
        }
        return new FileParseArchivedFile(filePath, row.getOriginalFileName(), row.getContentType());
    }

    String downloadUrl(Long fileId) {
        return "/api/file-management/parse/files/" + fileId + "/download";
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空。");
        }
        long maxFileBytes = Math.max(storageProperties.getMaxFileBytes(), 1L);
        if (file.getSize() > maxFileBytes) {
            throw new IllegalArgumentException("上传文件超过大小限制。");
        }
    }

    private void copyToArchive(MultipartFile file, Path storagePath) {
        try {
            Files.createDirectories(storagePath.getParent());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, storagePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            deleteQuietly(storagePath);
            throw new IllegalStateException("文件归档失败。", error);
        }
    }

    private String hashArchivedFile(Path storagePath) {
        try {
            return sha256Hex(storagePath);
        } catch (IOException error) {
            deleteQuietly(storagePath);
            throw new IllegalStateException("文件校验失败。", error);
        }
    }

    private FileParseFileAssetRow toAssetRow(
            Long fileId,
            String uploadId,
            FileParseTargetPlanRow targetPlan,
            Long userId,
            MultipartFile file,
            String originalFileName,
            String extension,
            String storageKey,
            String sha256Hash
    ) {
        FileParseFileAssetRow row = new FileParseFileAssetRow();
        row.setId(fileId);
        row.setUploadId(uploadId);
        row.setTargetPlanId(targetPlan.getId());
        row.setStandardVersionId(targetPlan.getStandardVersionId());
        row.setOriginalFileName(originalFileName);
        row.setContentType(normalizeContentType(file.getContentType()));
        row.setFileExtension(extension);
        row.setFileSizeBytes(file.getSize());
        row.setSha256Hash(sha256Hash);
        row.setStorageBucket(STORAGE_BUCKET);
        row.setStorageKey(storageKey);
        row.setUploadedBy(userId);
        row.setExpiresAt(LocalDateTime.now().plusHours(Math.max(storageProperties.getUploadExpiresHours(), 1L)));
        return row;
    }

    private void insertAssetOrDeleteArchive(FileParseFileAssetRow row, Path storagePath) {
        try {
            int inserted = mapper.insertFileAsset(row);
            if (inserted != 1) {
                throw new IllegalStateException("文件归档记录写入失败。");
            }
        } catch (RuntimeException error) {
            deleteQuietly(storagePath);
            throw error;
        }
    }

    private FileParseUploadView toUploadView(FileParseFileAssetRow row) {
        FileParseUploadView view = new FileParseUploadView();
        view.setFileId(row.getId());
        view.setUploadId(row.getUploadId());
        view.setTargetPlanId(row.getTargetPlanId());
        view.setStandardVersionId(row.getStandardVersionId());
        view.setOriginalFileName(row.getOriginalFileName());
        view.setContentType(row.getContentType());
        view.setFileExtension(row.getFileExtension());
        view.setSizeBytes(row.getFileSizeBytes());
        view.setSha256Hash(row.getSha256Hash());
        view.setDownloadUrl(downloadUrl(row.getId()));
        return view;
    }

    private Path storageRoot() {
        return storageProperties.getRootDir().toAbsolutePath().normalize();
    }

    private String buildStorageKey(Long fileId, String originalFileName) {
        return LocalDate.now().format(DATE_FORMATTER)
                + "/"
                + fileId
                + "-"
                + UUID.randomUUID()
                + "-"
                + originalFileName;
    }

    private String sanitizeFileName(String fileName) {
        String cleaned = StringUtils.cleanPath(StringUtils.hasText(fileName) ? fileName : "upload-file");
        int slashIndex = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        if (slashIndex >= 0) {
            cleaned = cleaned.substring(slashIndex + 1);
        }
        cleaned = cleaned
                .replaceAll("[\\r\\n\\t]", "_")
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .trim();
        if (!StringUtils.hasText(cleaned)) {
            return "upload-file";
        }
        return cleaned.length() > 180 ? cleaned.substring(cleaned.length() - 180) : cleaned;
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            throw new IllegalArgumentException("上传文件必须包含文件扩展名。");
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType.trim() : "application/octet-stream";
    }

    private String sha256Hex(Path filePath) throws IOException {
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[8192];
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return toHex(digest.digest());
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256。", error);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private void deleteQuietly(Path filePath) {
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
            // Best-effort cleanup only; the database write failure is the actionable error.
        }
    }
}
