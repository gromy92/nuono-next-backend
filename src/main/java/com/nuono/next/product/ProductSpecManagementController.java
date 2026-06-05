package com.nuono.next.product;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/product-specs")
public class ProductSpecManagementController {

    private final ObjectProvider<ProductVariantSpecService> serviceProvider;
    private final AuthSessionTokenService sessionTokenService;
    private final ObjectProvider<ProductMasterAccessGuard> accessGuardProvider;

    public ProductSpecManagementController(
            ObjectProvider<ProductVariantSpecService> serviceProvider,
            AuthSessionTokenService sessionTokenService,
            ObjectProvider<ProductMasterAccessGuard> accessGuardProvider
    ) {
        this.serviceProvider = serviceProvider;
        this.sessionTokenService = sessionTokenService;
        this.accessGuardProvider = accessGuardProvider;
    }

    @GetMapping
    public ProductVariantSpecOverviewView overview(
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            @RequestParam String storeCode,
            @RequestParam(value = "keyword", required = false) String keyword,
            HttpServletRequest request
    ) {
        ProductVariantSpecService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            Long resolvedOwnerUserId = accessGuard().resolveOwnerUserId(session, ownerUserId, storeCode);
            ProductVariantSpecOverviewCommand command = new ProductVariantSpecOverviewCommand();
            command.setOwnerUserId(resolvedOwnerUserId);
            command.setStoreCode(storeCode);
            command.setKeyword(keyword);
            return service.overview(command);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/{variantId}")
    public ProductVariantSpecDetailView detail(
            @PathVariable Long variantId,
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            @RequestParam String storeCode,
            HttpServletRequest request
    ) {
        ProductVariantSpecService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            Long resolvedOwnerUserId = accessGuard().resolveOwnerUserId(session, ownerUserId, storeCode);
            return service.detail(resolvedOwnerUserId, storeCode, variantId);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PutMapping("/{variantId}/sources/{sourceType}")
    public ProductVariantSpecSourceView saveSource(
            @PathVariable Long variantId,
            @PathVariable String sourceType,
            @RequestBody(required = false) ProductVariantSpecSourceCommand command,
            HttpServletRequest request
    ) {
        ProductVariantSpecService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            ProductVariantSpecSourceCommand effectiveCommand = command == null ? new ProductVariantSpecSourceCommand() : command;
            Long resolvedOwnerUserId = accessGuard().resolveOwnerUserId(
                    session,
                    effectiveCommand.getOwnerUserId(),
                    effectiveCommand.getStoreCode()
            );
            effectiveCommand.setOwnerUserId(resolvedOwnerUserId);
            effectiveCommand.setVariantId(variantId);
            effectiveCommand.setSourceType(sourceType);
            effectiveCommand.setOperatorUserId(session.getUserId());
            return service.saveSource(effectiveCommand);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/{variantId}/effective-source")
    public ProductVariantSpecDetailView selectEffectiveSource(
            @PathVariable Long variantId,
            @RequestBody(required = false) ProductVariantSpecEffectiveSourceCommand command,
            HttpServletRequest request
    ) {
        ProductVariantSpecService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            ProductVariantSpecEffectiveSourceCommand effectiveCommand =
                    command == null ? new ProductVariantSpecEffectiveSourceCommand() : command;
            Long resolvedOwnerUserId = accessGuard().resolveOwnerUserId(
                    session,
                    effectiveCommand.getOwnerUserId(),
                    effectiveCommand.getStoreCode()
            );
            return service.selectEffectiveSource(
                    resolvedOwnerUserId,
                    effectiveCommand.getStoreCode(),
                    variantId,
                    effectiveCommand.getSourceId(),
                    session.getUserId()
            );
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    private ProductVariantSpecService requireService() {
        ProductVariantSpecService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "商品规格维护暂时不可用。");
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

    private ResponseStatusException productAccessDenied(ProductMasterAccessDeniedException exception) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
    }

    private ResponseStatusException badRequest(IllegalArgumentException exception) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }
}
