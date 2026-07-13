package com.nuono.next.product;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

@RestController
@RequestMapping("/api/product-images")
public class ProductImageProfileController {
    private static final long MAX_IMAGE_BYTES = 8L * 1024L * 1024L;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/gif",
            "image/webp",
            "image/avif"
    );

    private final ObjectProvider<ProductImageProfileService> serviceProvider;
    private final ObjectProvider<ProductImageMetadataService> metadataServiceProvider;
    private final AuthSessionTokenService sessionTokenService;
    private final ObjectProvider<ProductMasterAccessGuard> accessGuardProvider;

    public ProductImageProfileController(
            ObjectProvider<ProductImageProfileService> serviceProvider,
            ObjectProvider<ProductImageMetadataService> metadataServiceProvider,
            AuthSessionTokenService sessionTokenService,
            ObjectProvider<ProductMasterAccessGuard> accessGuardProvider
    ) {
        this.serviceProvider = serviceProvider;
        this.metadataServiceProvider = metadataServiceProvider;
        this.sessionTokenService = sessionTokenService;
        this.accessGuardProvider = accessGuardProvider;
    }

    @GetMapping("/profiles")
    public ProductImageProfileListView list(
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            @RequestParam(value = "storeCode", required = false) String storeCode,
            @RequestParam(value = "keyword", required = false) String keyword,
            HttpServletRequest request
    ) {
        ProductImageProfileService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            Long resolvedOwnerUserId = accessGuard().resolveOwnerUserId(session, ownerUserId, storeCode);
            ProductImageProfileListCommand command = new ProductImageProfileListCommand();
            command.setOwnerUserId(resolvedOwnerUserId);
            command.setStoreCode(storeCode);
            command.setKeyword(keyword);
            command.setOperatorUserId(session.getUserId());
            return service.list(command);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (ProductImageProfileNotFoundException exception) {
            throw notFound(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/profile-summaries")
    public ProductImageProfileSummaryListView listSummaries(
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            @RequestParam(value = "storeCode", required = false) String storeCode,
            @RequestParam(value = "keyword", required = false) String keyword,
            HttpServletRequest request
    ) {
        ProductImageProfileService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            Long resolvedOwnerUserId = accessGuard().resolveOwnerUserId(session, ownerUserId, storeCode);
            ProductImageProfileListCommand command = new ProductImageProfileListCommand();
            command.setOwnerUserId(resolvedOwnerUserId);
            command.setStoreCode(storeCode);
            command.setKeyword(keyword);
            command.setOperatorUserId(session.getUserId());
            return service.listSummaries(command);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (ProductImageProfileNotFoundException exception) {
            throw notFound(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/profiles/{profileId}")
    public ProductImageProfileDetailView detail(
            @PathVariable Long profileId,
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            @RequestParam String storeCode,
            HttpServletRequest request
    ) {
        ProductImageProfileService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            Long resolvedOwnerUserId = accessGuard().resolveOwnerUserId(session, ownerUserId, storeCode);
            return service.detail(resolvedOwnerUserId, storeCode, profileId);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (ProductImageProfileNotFoundException exception) {
            throw notFound(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/profiles")
    public ProductImageProfileDetailView save(
            @RequestBody(required = false) ProductImageProfileSaveCommand command,
            HttpServletRequest request
    ) {
        ProductImageProfileService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            ProductImageProfileSaveCommand effectiveCommand = command == null
                    ? new ProductImageProfileSaveCommand()
                    : command;
            Long resolvedOwnerUserId = accessGuard().resolveOwnerUserId(
                    session,
                    effectiveCommand.getOwnerUserId(),
                    effectiveCommand.getStoreCode()
            );
            effectiveCommand.setOwnerUserId(resolvedOwnerUserId);
            effectiveCommand.setOperatorUserId(session.getUserId());
            return service.save(effectiveCommand);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (ProductImageProfileNotFoundException exception) {
            throw notFound(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping(value = "/profiles/{profileId}/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductImageProfileDetailView uploadAsset(
            @PathVariable Long profileId,
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            @RequestParam String storeCode,
            @RequestParam(value = "imageRole", required = false) ProductImageRole imageRole,
            @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
            @RequestParam(value = "file", required = false) MultipartFile file,
            HttpServletRequest request
    ) {
        ProductImageProfileService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            Long resolvedOwnerUserId = accessGuard().resolveOwnerUserId(session, ownerUserId, storeCode);
            validateImage(file);
            ProductImageAssetMetadataView metadata = requireMetadataService().uploadedImageMetadata(file);
            String imageUrl = saveUploadedFile(storeCode, file);

            ProductImageAssetCreateCommand command = new ProductImageAssetCreateCommand();
            command.setOwnerUserId(resolvedOwnerUserId);
            command.setStoreCode(storeCode);
            command.setProfileId(profileId);
            command.setImageUrl(imageUrl);
            command.setContentType(metadata.getContentType());
            command.setSizeBytes(metadata.getSizeBytes());
            command.setWidthPx(metadata.getWidthPx());
            command.setHeightPx(metadata.getHeightPx());
            command.setImageRole(imageRole);
            command.setSortOrder(sortOrder);
            command.setOperatorUserId(session.getUserId());
            return service.addAsset(command);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (ProductImageProfileNotFoundException exception) {
            throw notFound(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "保存图片失败。", exception);
        }
    }

    @DeleteMapping("/profiles/{profileId}/assets/{assetId}")
    public ProductImageProfileDetailView removeAsset(
            @PathVariable Long profileId,
            @PathVariable Long assetId,
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            @RequestParam String storeCode,
            HttpServletRequest request
    ) {
        ProductImageProfileService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            Long resolvedOwnerUserId = accessGuard().resolveOwnerUserId(session, ownerUserId, storeCode);
            return service.removeAsset(resolvedOwnerUserId, storeCode, profileId, assetId, session.getUserId());
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (ProductImageProfileNotFoundException exception) {
            throw notFound(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PatchMapping("/profiles/{profileId}/assets/role")
    public ProductImageProfileDetailView updateAssetRole(
            @PathVariable Long profileId,
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            @RequestParam String storeCode,
            @RequestBody(required = false) ProductImageAssetRoleUpdateCommand command,
            HttpServletRequest request
    ) {
        ProductImageProfileService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            Long resolvedOwnerUserId = accessGuard().resolveOwnerUserId(session, ownerUserId, storeCode);
            return service.updateAssetRole(resolvedOwnerUserId, storeCode, profileId, command, session.getUserId());
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (ProductImageProfileNotFoundException exception) {
            throw notFound(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/profiles/{profileId}/assets/batch-remove")
    public ProductImageProfileDetailView removeAssets(
            @PathVariable Long profileId,
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            @RequestParam String storeCode,
            @RequestBody(required = false) ProductImageAssetBatchRemoveCommand command,
            HttpServletRequest request
    ) {
        ProductImageProfileService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            Long resolvedOwnerUserId = accessGuard().resolveOwnerUserId(session, ownerUserId, storeCode);
            return service.removeAssets(resolvedOwnerUserId, storeCode, profileId, command, session.getUserId());
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (ProductImageProfileNotFoundException exception) {
            throw notFound(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/profiles/{profileId}/assets/url-import")
    public ProductImageProfileDetailView importAssetUrls(
            @PathVariable Long profileId,
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            @RequestParam String storeCode,
            @RequestBody(required = false) ProductImageAssetUrlImportCommand command,
            HttpServletRequest request
    ) {
        ProductImageProfileService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            Long resolvedOwnerUserId = accessGuard().resolveOwnerUserId(session, ownerUserId, storeCode);
            return service.addAssetsFromUrls(resolvedOwnerUserId, storeCode, profileId, command, session.getUserId());
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (ProductImageProfileNotFoundException exception) {
            throw notFound(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/assets/metadata")
    public ProductImageAssetMetadataView assetMetadata(
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            @RequestParam String storeCode,
            @RequestParam Long productMasterId,
            @RequestParam String imageUrl,
            HttpServletRequest request
    ) {
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            Long resolvedOwnerUserId = accessGuard().resolveOwnerUserId(session, ownerUserId, storeCode);
            return requireMetadataService().assetMetadata(resolvedOwnerUserId, storeCode, productMasterId, imageUrl);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (ProductImageProfileNotFoundException exception) {
            throw notFound(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/profiles/{profileId}/suites/{suiteId}/adopt")
    public ProductImageProfileDetailView adoptSuite(
            @PathVariable Long profileId,
            @PathVariable Long suiteId,
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            @RequestParam String storeCode,
            HttpServletRequest request
    ) {
        ProductImageProfileService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            Long resolvedOwnerUserId = accessGuard().resolveOwnerUserId(session, ownerUserId, storeCode);
            return service.adoptSuite(resolvedOwnerUserId, storeCode, profileId, suiteId, session.getUserId());
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (ProductImageProfileNotFoundException exception) {
            throw notFound(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/profiles/{profileId}/suites/ai-draft")
    public ProductImageProfileDetailView createAiSuiteDraft(
            @PathVariable Long profileId,
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            @RequestParam String storeCode,
            HttpServletRequest request
    ) {
        ProductImageProfileService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            Long resolvedOwnerUserId = accessGuard().resolveOwnerUserId(session, ownerUserId, storeCode);
            return service.createAiSuiteDraft(resolvedOwnerUserId, storeCode, profileId, session.getUserId());
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (ProductImageProfileNotFoundException exception) {
            throw notFound(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/profiles/{profileId}/ai-extract")
    public ProductImageAiExtractionSuggestionView extractImageFacts(
            @PathVariable Long profileId,
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            @RequestParam String storeCode,
            HttpServletRequest request
    ) {
        ProductImageProfileService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            Long resolvedOwnerUserId = accessGuard().resolveOwnerUserId(session, ownerUserId, storeCode);
            return service.extractImageFacts(resolvedOwnerUserId, storeCode, profileId, session.getUserId());
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (ProductImageProfileNotFoundException exception) {
            throw notFound(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/profiles/{profileId}/suites/{suiteId}/discard")
    public ProductImageProfileDetailView discardSuite(
            @PathVariable Long profileId,
            @PathVariable Long suiteId,
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            @RequestParam String storeCode,
            HttpServletRequest request
    ) {
        ProductImageProfileService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            Long resolvedOwnerUserId = accessGuard().resolveOwnerUserId(session, ownerUserId, storeCode);
            return service.discardSuite(resolvedOwnerUserId, storeCode, profileId, suiteId, session.getUserId());
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (ProductImageProfileNotFoundException exception) {
            throw notFound(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @DeleteMapping("/profiles/{profileId}/suites/{suiteId}")
    public ProductImageProfileDetailView deleteSuite(
            @PathVariable Long profileId,
            @PathVariable Long suiteId,
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            @RequestParam String storeCode,
            HttpServletRequest request
    ) {
        ProductImageProfileService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            Long resolvedOwnerUserId = accessGuard().resolveOwnerUserId(session, ownerUserId, storeCode);
            return service.deleteSuite(resolvedOwnerUserId, storeCode, profileId, suiteId, session.getUserId());
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (ProductImageProfileNotFoundException exception) {
            throw notFound(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PatchMapping("/profiles/{profileId}/suites/{suiteId}/assets/{assetId}/move")
    public ProductImageProfileDetailView moveSuiteAsset(
            @PathVariable Long profileId,
            @PathVariable Long suiteId,
            @PathVariable Long assetId,
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            @RequestParam String storeCode,
            @RequestBody(required = false) ProductImageSuiteAssetMoveCommand command,
            HttpServletRequest request
    ) {
        ProductImageProfileService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            Long resolvedOwnerUserId = accessGuard().resolveOwnerUserId(session, ownerUserId, storeCode);
            return service.moveSuiteAsset(resolvedOwnerUserId, storeCode, profileId, suiteId, assetId, command, session.getUserId());
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (ProductImageProfileNotFoundException exception) {
            throw notFound(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @DeleteMapping("/profiles/{profileId}/suites/{suiteId}/assets/{assetId}")
    public ProductImageProfileDetailView deleteSuiteAsset(
            @PathVariable Long profileId,
            @PathVariable Long suiteId,
            @PathVariable Long assetId,
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            @RequestParam String storeCode,
            HttpServletRequest request
    ) {
        ProductImageProfileService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            Long resolvedOwnerUserId = accessGuard().resolveOwnerUserId(session, ownerUserId, storeCode);
            return service.deleteSuiteAsset(resolvedOwnerUserId, storeCode, profileId, suiteId, assetId, session.getUserId());
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (ProductImageProfileNotFoundException exception) {
            throw notFound(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/assets/{storeCode}/{filename:.+}")
    public ResponseEntity<Resource> asset(
            @PathVariable String storeCode,
            @PathVariable String filename,
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            HttpServletRequest request
    ) {
        if (!isSafeFilename(filename)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片文件名不合法。");
        }
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            accessGuard().resolveOwnerUserId(session, ownerUserId, storeCode);
            Path storeDir = storeUploadDir(storeCode);
            Path file = storeDir.resolve(filename).normalize();
            if (!file.startsWith(storeDir)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片文件名不合法。");
            }
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(file)
                    || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在。");
            }
            String contentType = Files.probeContentType(file);
            MediaType mediaType = StringUtils.hasText(contentType)
                    ? MediaType.parseMediaType(contentType)
                    : MediaType.APPLICATION_OCTET_STREAM;
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .cacheControl(CacheControl.noCache())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(new FileSystemResource(file));
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取图片失败。", exception);
        }
    }

    private ProductImageProfileService requireService() {
        ProductImageProfileService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "商品图资料暂时不可用。");
        }
        return service;
    }

    private ProductImageMetadataService requireMetadataService() {
        ProductImageMetadataService service = metadataServiceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "商品图元数据服务暂时不可用。");
        }
        return service;
    }

    private ProductMasterAccessGuard accessGuard() {
        ProductMasterAccessGuard accessGuard = accessGuardProvider.getIfAvailable();
        if (accessGuard == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "商品访问控制暂时不可用。");
        }
        return accessGuard;
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("图片文件不能为空。");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("图片不能超过 8MB。");
        }
        if (!ALLOWED_IMAGE_TYPES.contains(normalizeContentType(file.getContentType()))) {
            throw new IllegalArgumentException("仅支持图片文件。");
        }
    }

    private String saveUploadedFile(String storeCode, MultipartFile file) throws IOException {
        String normalizedStoreCode = normalizeStoreCode(storeCode);
        Path dir = storeUploadDir(normalizedStoreCode);
        Files.createDirectories(dir);
        String extension = ProductImageAssetFileSupport.imageExtension(file);
        String filename = UUID.randomUUID() + "." + extension;
        Path target = dir.resolve(filename).normalize();
        file.transferTo(target);
        return "/api/product-images/assets/"
                + UriUtils.encodePathSegment(normalizedStoreCode, StandardCharsets.UTF_8)
                + "/"
                + UriUtils.encodePathSegment(filename, StandardCharsets.UTF_8);
    }

    private Path storeUploadDir(String storeCode) {
        String normalizedStoreCode = normalizeStoreCode(storeCode);
        return ProductImageAssetFileSupport.productImageUploadDir()
                .resolve("profiles")
                .resolve(normalizedStoreCode)
                .normalize();
    }

    private String normalizeStoreCode(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("店铺编码不能为空。");
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isSafeFilename(String filename) {
        return StringUtils.hasText(filename)
                && !filename.contains("/")
                && !filename.contains("\\")
                && !filename.contains("..");
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "";
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        int parameterIndex = normalized.indexOf(';');
        return parameterIndex >= 0 ? normalized.substring(0, parameterIndex).trim() : normalized;
    }

    private ResponseStatusException productAccessDenied(ProductMasterAccessDeniedException exception) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
    }

    private ResponseStatusException badRequest(IllegalArgumentException exception) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }

    private ResponseStatusException notFound(ProductImageProfileNotFoundException exception) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
    }
}
