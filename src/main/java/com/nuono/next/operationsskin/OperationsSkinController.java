package com.nuono.next.operationsskin;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

@RestController
@RequestMapping("/api/operations/skin-management")
public class OperationsSkinController {
    private static final long MAX_IMAGE_BYTES = 8L * 1024L * 1024L;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/gif",
            "image/webp",
            "image/avif"
    );

    private final BusinessAccessResolver accessResolver;
    private final OperationsSkinService service;

    public OperationsSkinController(
            BusinessAccessResolver accessResolver,
            OperationsSkinService service
    ) {
        this.accessResolver = accessResolver;
        this.service = service;
    }

    @GetMapping("/skins")
    public List<OperationsSkinView> list(
            @RequestParam(value = "storeCode", required = false) String storeCode,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            HttpServletRequest request
    ) {
        String normalizedStoreCode = requireStoreCode(storeCode);
        BusinessAccessContext context = requireStoreAccess(request, normalizedStoreCode);
        try {
            return service.list(context, normalizedStoreCode, keyword, status);
        } catch (OperationsSkinNotFoundException exception) {
            throw notFound(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/skins")
    public OperationsSkinView create(
            @RequestBody(required = false) OperationsSkinSaveRequest body,
            HttpServletRequest request
    ) {
        String storeCode = requireStoreCode(body == null ? null : body.getStoreCode());
        BusinessAccessContext context = requireStoreAccess(request, storeCode);
        try {
            return service.create(context, body);
        } catch (OperationsSkinNotFoundException exception) {
            throw notFound(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PutMapping("/skins/{id}")
    public OperationsSkinView update(
            @PathVariable Long id,
            @RequestBody(required = false) OperationsSkinSaveRequest body,
            HttpServletRequest request
    ) {
        String storeCode = requireStoreCode(body == null ? null : body.getStoreCode());
        BusinessAccessContext context = requireStoreAccess(request, storeCode);
        try {
            return service.update(context, id, body);
        } catch (OperationsSkinNotFoundException exception) {
            throw notFound(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/skins/{id}")
    public OperationsSkinView detail(
            @PathVariable Long id,
            @RequestParam(value = "storeCode", required = false) String storeCode,
            HttpServletRequest request
    ) {
        String normalizedStoreCode = requireStoreCode(storeCode);
        BusinessAccessContext context = requireStoreAccess(request, normalizedStoreCode);
        try {
            return service.detail(context, id, normalizedStoreCode);
        } catch (OperationsSkinNotFoundException exception) {
            throw notFound(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PutMapping("/skins/{id}/components")
    public OperationsSkinView updateComponents(
            @PathVariable Long id,
            @RequestBody(required = false) OperationsSkinComponentsSaveRequest body,
            HttpServletRequest request
    ) {
        String storeCode = requireStoreCode(body == null ? null : body.getStoreCode());
        BusinessAccessContext context = requireStoreAccess(request, storeCode);
        try {
            return service.saveComponents(context, id, body);
        } catch (OperationsSkinNotFoundException exception) {
            throw notFound(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/skins/{id}/status")
    public OperationsSkinView updateStatus(
            @PathVariable Long id,
            @RequestBody(required = false) OperationsSkinStatusRequest body,
            HttpServletRequest request
    ) {
        String storeCode = requireStoreCode(body == null ? null : body.getStoreCode());
        BusinessAccessContext context = requireStoreAccess(request, storeCode);
        try {
            return service.updateStatus(context, id, body);
        } catch (OperationsSkinNotFoundException exception) {
            throw notFound(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @DeleteMapping("/skins/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestParam(value = "storeCode", required = false) String storeCode,
            HttpServletRequest request
    ) {
        String normalizedStoreCode = requireStoreCode(storeCode);
        BusinessAccessContext context = requireStoreAccess(request, normalizedStoreCode);
        try {
            service.delete(context, id, normalizedStoreCode);
            return ResponseEntity.noContent().build();
        } catch (OperationsSkinNotFoundException exception) {
            throw notFound(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping(value = "/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadAsset(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "storeCode", required = false) String storeCode,
            HttpServletRequest request
    ) {
        String normalizedStoreCode = requireStoreCode(storeCode);
        requireStoreAccess(request, normalizedStoreCode);
        validateImage(file);

        try {
            Path uploadDir = ensureStoreUploadDir(normalizedStoreCode);
            String filename = OperationsSkinAssetFileSupport.safeRandomFilename(file);
            Path target = uploadDir.resolve(filename).normalize();
            file.transferTo(target);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("url", "/api/operations/skin-management/assets/"
                    + filename
                    + "?storeCode="
                    + UriUtils.encodeQueryParam(normalizedStoreCode, StandardCharsets.UTF_8));
            response.put("filename", filename);
            response.put("contentType", file.getContentType());
            response.put("size", file.getSize());
            return response;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "保存图片失败", exception);
        }
    }

    @GetMapping("/assets/{filename:.+}")
    public ResponseEntity<Resource> asset(
            @PathVariable String filename,
            @RequestParam(value = "storeCode", required = false) String storeCode,
            HttpServletRequest request
    ) {
        if (OperationsSkinAssetFileSupport.isUnsafeFilename(filename)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片文件名不合法");
        }
        String normalizedStoreCode = requireStoreCode(storeCode);
        Path storeDir = storeUploadDir(normalizedStoreCode);
        BusinessAccessContext context = accessResolver.requireBusinessContext(
                request,
                BusinessCapability.OPERATIONS_SKIN_MANAGEMENT
        );
        try {
            service.verifyReadableAssetStore(context, normalizedStoreCode);
        } catch (OperationsSkinNotFoundException exception) {
            throw notFound(exception);
        }

        Path file = storeDir.resolve(filename).normalize();
        if (!file.startsWith(storeDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片文件名不合法");
        }
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(file)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在");
        }

        try {
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
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取图片失败", exception);
        }
    }

    private BusinessAccessContext requireStoreAccess(HttpServletRequest request, String storeCode) {
        return accessResolver.requireStoreAccess(
                request,
                BusinessCapability.OPERATIONS_SKIN_MANAGEMENT,
                storeCode
        );
    }

    private String requireStoreCode(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "店铺编码不能为空。");
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("图片文件不能为空");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw badRequest("图片不能超过 8MB");
        }
        String contentType = file.getContentType();
        if (!ALLOWED_IMAGE_TYPES.contains(normalizeContentType(contentType))) {
            throw badRequest("仅支持图片文件");
        }
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "";
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        int parameterIndex = normalized.indexOf(';');
        return parameterIndex >= 0 ? normalized.substring(0, parameterIndex).trim() : normalized;
    }

    private Path storeUploadDir(String storeCode) {
        try {
            return OperationsSkinAssetFileSupport.storeUploadDir(storeCode);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    private Path ensureStoreUploadDir(String storeCode) throws IOException {
        try {
            return OperationsSkinAssetFileSupport.ensureStoreUploadDir(storeCode);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    private ResponseStatusException badRequest(String message) {
        IllegalArgumentException exception = new IllegalArgumentException(message);
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message, exception);
    }

    private ResponseStatusException badRequest(IllegalArgumentException exception) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }

    private ResponseStatusException notFound(OperationsSkinNotFoundException exception) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
    }
}
