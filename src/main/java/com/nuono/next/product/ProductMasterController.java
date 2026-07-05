package com.nuono.next.product;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/product-master")
public class ProductMasterController {

    private static final long MAX_IMAGE_BYTES = 8L * 1024L * 1024L;

    private final ObjectProvider<LocalDbProductMasterService> localDbProductMasterServiceProvider;
    private final ObjectProvider<ProductContentTranslationService> productContentTranslationServiceProvider;
    private final AuthSessionTokenService sessionTokenService;
    private final ObjectProvider<ProductMasterAccessGuard> productMasterAccessGuardProvider;
    private final ObjectProvider<BusinessAccessResolver> businessAccessResolverProvider;

    public ProductMasterController(
            ObjectProvider<LocalDbProductMasterService> localDbProductMasterServiceProvider,
            ObjectProvider<ProductContentTranslationService> productContentTranslationServiceProvider,
            AuthSessionTokenService sessionTokenService,
            ObjectProvider<ProductMasterAccessGuard> productMasterAccessGuardProvider,
            ObjectProvider<BusinessAccessResolver> businessAccessResolverProvider
    ) {
        this.localDbProductMasterServiceProvider = localDbProductMasterServiceProvider;
        this.productContentTranslationServiceProvider = productContentTranslationServiceProvider;
        this.sessionTokenService = sessionTokenService;
        this.productMasterAccessGuardProvider = productMasterAccessGuardProvider;
        this.businessAccessResolverProvider = businessAccessResolverProvider;
    }

    @PostMapping("/snapshot")
    public ProductMasterSnapshotView snapshot(
            @RequestBody ProductMasterFetchCommand command,
            HttpServletRequest request
    ) {
        LocalDbProductMasterService productMasterService = localDbProductMasterServiceProvider.getIfAvailable();
        if (productMasterService == null) {
            ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
            snapshot.setMode("bootstrap-only");
            snapshot.setReady(false);
            snapshot.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可读取商品主档快照。");
            return snapshot;
        }

        try {
            applyProductScope(command, request);
            return productMasterService.fetchSnapshot(command);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/open")
    public ProductMasterWorkbenchView open(
            @RequestBody ProductMasterFetchCommand command,
            HttpServletRequest request
    ) {
        LocalDbProductMasterService productMasterService = localDbProductMasterServiceProvider.getIfAvailable();
        if (productMasterService == null) {
            ProductMasterWorkbenchView snapshot = new ProductMasterWorkbenchView();
            snapshot.setMode("bootstrap-only");
            snapshot.setReady(false);
            snapshot.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可读取商品主档快照。");
            snapshot.setSyncStatus("failed");
            snapshot.setNote("商品详情工作台暂时不可用。");
            return snapshot;
        }

        try {
            applyProductScope(command, request);
            return productMasterService.openWorkbench(command);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/list-summary")
    public ProductListSummaryView listSummary(
            @RequestBody ProductMasterFetchCommand command,
            HttpServletRequest request
    ) {
        LocalDbProductMasterService productMasterService = localDbProductMasterServiceProvider.getIfAvailable();
        if (productMasterService == null) {
            ProductListSummaryView summary = new ProductListSummaryView();
            summary.setReady(false);
            summary.setSource("bootstrap-only");
            summary.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可读取商品列表摘要。");
            return summary;
        }

        try {
            applyProductScope(command, request);
            return productMasterService.loadListSummary(command);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/list")
    public ProductListDatasetView list(
            @RequestBody ProductMasterFetchCommand command,
            HttpServletRequest request
    ) {
        LocalDbProductMasterService productMasterService = localDbProductMasterServiceProvider.getIfAvailable();
        if (productMasterService == null) {
            ProductListDatasetView view = new ProductListDatasetView();
            view.setReady(false);
            view.setSource("bootstrap-only");
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可读取商品工作台数据面。");
            return view;
        }

        try {
            applyProductScope(command, request);
            return productMasterService.loadListDataset(command);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/delete")
    public ProductListDatasetView delete(
            @RequestBody ProductMasterFetchCommand command,
            HttpServletRequest request
    ) {
        LocalDbProductMasterService productMasterService = localDbProductMasterServiceProvider.getIfAvailable();
        if (productMasterService == null) {
            ProductListDatasetView view = new ProductListDatasetView();
            view.setReady(false);
            view.setSource("bootstrap-only");
            view.setMessage("当前仍在无数据库骨架模式，暂时不能删除商品。");
            return view;
        }

        try {
            applyProductScope(command, request);
            return productMasterService.deleteProduct(command);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/rebuild")
    public ProductListDatasetView rebuild(
            @RequestBody ProductMasterFetchCommand command,
            HttpServletRequest request
    ) {
        LocalDbProductMasterService productMasterService = localDbProductMasterServiceProvider.getIfAvailable();
        if (productMasterService == null) {
            ProductListDatasetView view = new ProductListDatasetView();
            view.setReady(false);
            view.setSource("bootstrap-only");
            view.setMessage("当前仍在无数据库骨架模式，暂时不能重建商品。");
            return view;
        }

        try {
            applyProductScope(command, request);
            return productMasterService.rebuildProduct(command);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/history")
    public ProductHistoryView history(
            @RequestBody ProductMasterFetchCommand command,
            HttpServletRequest request
    ) {
        LocalDbProductMasterService productMasterService = localDbProductMasterServiceProvider.getIfAvailable();
        if (productMasterService == null) {
            ProductHistoryView history = new ProductHistoryView();
            history.setReady(false);
            history.setSource("bootstrap-only");
            history.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可读取关键内容历史。");
            return history;
        }

        try {
            applyProductScope(command, request);
            return productMasterService.loadHistory(command);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/group-candidates")
    public ProductGroupCandidatesView groupCandidates(
            @RequestBody ProductMasterFetchCommand command,
            HttpServletRequest request
    ) {
        LocalDbProductMasterService productMasterService = localDbProductMasterServiceProvider.getIfAvailable();
        if (productMasterService == null) {
            ProductGroupCandidatesView view = new ProductGroupCandidatesView();
            view.setReady(false);
            view.setSource("bootstrap-only");
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可读取同类目候选商品。");
            return view;
        }

        try {
            applyProductScope(command, request);
            return productMasterService.loadGroupCandidates(command);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/classification-options")
    public ProductClassificationOptionsView classificationOptions(
            @RequestBody ProductClassificationOptionsCommand command,
            HttpServletRequest request
    ) {
        LocalDbProductMasterService productMasterService = localDbProductMasterServiceProvider.getIfAvailable();
        if (productMasterService == null) {
            ProductClassificationOptionsView view = new ProductClassificationOptionsView();
            view.setReady(false);
            view.setSource("bootstrap-only");
            view.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可读取品牌和类目候选。");
            return view;
        }

        try {
            applyProductScope(command, request);
            return productMasterService.loadClassificationOptions(command);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/action")
    public ProductMasterWorkbenchView action(
            @RequestBody ProductMasterActionCommand command,
            HttpServletRequest request
    ) {
        LocalDbProductMasterService productMasterService = localDbProductMasterServiceProvider.getIfAvailable();
        if (productMasterService == null) {
            ProductMasterWorkbenchView snapshot = new ProductMasterWorkbenchView();
            snapshot.setMode("bootstrap-only");
            snapshot.setReady(false);
            snapshot.setMessage("当前仍在无数据库骨架模式。切换到 local-db profile 后可进入商品详情工作台。");
            snapshot.setSyncStatus("failed");
            snapshot.setNote("商品详情动作暂时不可用。");
            return snapshot;
        }

        try {
            applyProductScope(command, request);
            return productMasterService.applyAction(command);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @GetMapping("/publish-tasks/{taskId}")
    public ProductPublishTaskView publishTask(
            @PathVariable Long taskId,
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            HttpServletRequest request
    ) {
        LocalDbProductMasterService productMasterService = localDbProductMasterServiceProvider.getIfAvailable();
        if (productMasterService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "商品发布任务暂时不可用。");
        }
        try {
            Long resolvedOwnerUserId = productAccessGuard().resolvePublishTaskOwnerUserId(
                    requireSession(request),
                    taskId,
                    ownerUserId
            );
            return productMasterService.loadPublishTask(taskId, resolvedOwnerUserId);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IllegalArgumentException exception) {
            throw publishTaskArgumentException(exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/publish-tasks/{taskId}/retry")
    public ProductPublishTaskView retryPublishTask(
            @PathVariable Long taskId,
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            HttpServletRequest request
    ) {
        LocalDbProductMasterService productMasterService = localDbProductMasterServiceProvider.getIfAvailable();
        if (productMasterService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "商品发布任务暂时不可用。");
        }
        try {
            Long resolvedOwnerUserId = productAccessGuard().resolvePublishTaskOwnerUserId(
                    requireSession(request),
                    taskId,
                    ownerUserId
            );
            return productMasterService.retryPublishTask(taskId, resolvedOwnerUserId);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IllegalArgumentException exception) {
            throw publishTaskArgumentException(exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PostMapping("/publish-tasks/{taskId}/cancel")
    public ProductPublishTaskView cancelPublishTask(
            @PathVariable Long taskId,
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            HttpServletRequest request
    ) {
        LocalDbProductMasterService productMasterService = localDbProductMasterServiceProvider.getIfAvailable();
        if (productMasterService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "商品发布任务暂时不可用。");
        }
        try {
            Long resolvedOwnerUserId = productAccessGuard().resolvePublishTaskOwnerUserId(
                    requireSession(request),
                    taskId,
                    ownerUserId
            );
            return productMasterService.cancelPublishTask(taskId, resolvedOwnerUserId);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IllegalArgumentException exception) {
            throw publishTaskArgumentException(exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    private ResponseStatusException publishTaskArgumentException(IllegalArgumentException exception) {
        String message = exception.getMessage();
        HttpStatus status = message != null && message.contains("不属于选中的老板上下文")
                ? HttpStatus.FORBIDDEN
                : HttpStatus.BAD_REQUEST;
        return new ResponseStatusException(status, message, exception);
    }

    @PostMapping("/translate")
    public ProductContentTranslateView translate(
            @RequestBody ProductContentTranslateCommand command,
            HttpServletRequest request
    ) {
        ProductContentTranslationService translationService = productContentTranslationServiceProvider.getIfAvailable();
        if (translationService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "翻译服务暂时不可用");
        }
        try {
            AuthenticatedSession session = requireSession(request);
            businessAccessResolver().requireBusinessContext(request, BusinessCapability.PRODUCT_MASTER);
            if (command == null) {
                command = new ProductContentTranslateCommand();
            }
            command.setOperatorUserId(session.getUserId());
            return translationService.translate(command);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping(value = "/image-assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadImageAsset(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            @RequestParam(value = "storeCode", required = false) String storeCode,
            @RequestParam(value = "skuParent", required = false) String skuParent,
            HttpServletRequest request
    ) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片文件不能为空");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片不能超过 8MB");
        }
        if (!String.valueOf(file.getContentType()).startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持图片文件");
        }

        try {
            LocalDbProductMasterService productMasterService = localDbProductMasterServiceProvider.getIfAvailable();
            Long resolvedOwnerUserId = null;
            if (productMasterService != null && StringUtils.hasText(storeCode) && StringUtils.hasText(skuParent)) {
                BusinessAccessContext access = businessAccessResolver().requireStoreAccess(
                        request,
                        BusinessCapability.PRODUCT_MASTER,
                        storeCode
                );
                resolvedOwnerUserId = resolveOwnerUserId(access, storeCode);
            }
            Path uploadDir = ProductImageAssetFileSupport.productImageUploadDir();
            Files.createDirectories(uploadDir);
            String filename = UUID.randomUUID() + "." + ProductImageAssetFileSupport.imageExtension(file);
            Path target = uploadDir.resolve(filename).normalize();
            file.transferTo(target);
            String url = "/api/product-master/image-assets/" + filename;
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("url", url);
            response.put("filename", filename);
            response.put("contentType", file.getContentType());
            response.put("size", file.getSize());
            List<String> warnings = new ArrayList<>();
            if (productMasterService != null && resolvedOwnerUserId != null
                    && StringUtils.hasText(storeCode) && StringUtils.hasText(skuParent)) {
                Long assetId = productMasterService.persistUploadedImageAsset(
                        resolvedOwnerUserId,
                        storeCode,
                        skuParent,
                        url,
                        filename,
                        file.getOriginalFilename(),
                        file.getContentType(),
                        file.getSize(),
                        ProductImageAssetFileSupport.sha256Hex(target),
                        warnings
                );
                if (assetId != null) {
                    response.put("assetId", assetId);
                }
            }
            if (!warnings.isEmpty()) {
                response.put("warnings", warnings);
            }
            return response;
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "保存图片失败", exception);
        }
    }

    @GetMapping("/image-assets/{filename:.+}")
    public ResponseEntity<Resource> imageAsset(@PathVariable String filename) {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片文件名不合法");
        }
        Path file = ProductImageAssetFileSupport.productImageUploadDir().resolve(filename).normalize();
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
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
                .body(new FileSystemResource(file));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取图片失败", exception);
        }
    }

    private void applyProductScope(ProductMasterFetchCommand command, HttpServletRequest request) {
        if (command == null) {
            throw new IllegalArgumentException("缺少商品请求参数。");
        }
        BusinessAccessContext access = businessAccessResolver().requireStoreAccess(
                request,
                BusinessCapability.PRODUCT_MASTER,
                command.getStoreCode()
        );
        command.setOwnerUserId(resolveOwnerUserId(access, command.getStoreCode()));
    }

    private void applyProductScope(ProductClassificationOptionsCommand command, HttpServletRequest request) {
        if (command == null) {
            throw new IllegalArgumentException("缺少商品分类候选请求参数。");
        }
        BusinessAccessContext access = businessAccessResolver().requireStoreAccess(
                request,
                BusinessCapability.PRODUCT_MASTER,
                command.getStoreCode()
        );
        command.setOwnerUserId(resolveOwnerUserId(access, command.getStoreCode()));
    }

    private Long resolveOwnerUserId(BusinessAccessContext access, String storeCode) {
        Long ownerUserId = access.resolveOwnerUserIdForStore(storeCode);
        if (ownerUserId != null) {
            return ownerUserId;
        }
        if (access.getBusinessOwnerUserId() != null) {
            return access.getBusinessOwnerUserId();
        }
        throw new ProductMasterAccessDeniedException("当前店铺未绑定业务老板上下文。");
    }

    private AuthenticatedSession requireSession(HttpServletRequest request) {
        return sessionTokenService.requireSession(request);
    }

    private ProductMasterAccessGuard productAccessGuard() {
        ProductMasterAccessGuard accessGuard = productMasterAccessGuardProvider.getIfAvailable();
        if (accessGuard == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "商品访问控制暂时不可用。");
        }
        return accessGuard;
    }

    private BusinessAccessResolver businessAccessResolver() {
        BusinessAccessResolver resolver = businessAccessResolverProvider.getIfAvailable();
        if (resolver == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "商品访问控制暂时不可用。");
        }
        return resolver;
    }

    private ResponseStatusException productAccessDenied(ProductMasterAccessDeniedException exception) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
    }
}
