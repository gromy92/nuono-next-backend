package com.nuono.next.product;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
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
    private final ObjectProvider<BusinessAccessResolver> businessAccessResolverProvider;

    public ProductSpecManagementController(
            ObjectProvider<ProductVariantSpecService> serviceProvider,
            AuthSessionTokenService sessionTokenService,
            ObjectProvider<BusinessAccessResolver> businessAccessResolverProvider
    ) {
        this.serviceProvider = serviceProvider;
        this.sessionTokenService = sessionTokenService;
        this.businessAccessResolverProvider = businessAccessResolverProvider;
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
            Long resolvedOwnerUserId = resolveWebSpecOwnerUserId(request, storeCode);
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
            Long resolvedOwnerUserId = resolveOwnerUserId(request, storeCode);
            return service.detail(resolvedOwnerUserId, storeCode, variantId);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/by-psku")
    public ProductVariantSpecDetailView detailByPsku(
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            @RequestParam String storeCode,
            @RequestParam String partnerSku,
            HttpServletRequest request
    ) {
        ProductVariantSpecService service = requireService();
        try {
            Long resolvedOwnerUserId = resolveWebSpecOwnerUserId(request, storeCode);
            return service.detailByPsku(resolvedOwnerUserId, storeCode, partnerSku);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @GetMapping("/by-identity")
    public ProductVariantSpecDetailView detailByIdentity(
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            @RequestParam String storeCode,
            @RequestParam String partnerSku,
            HttpServletRequest request
    ) {
        ProductVariantSpecService service = requireService();
        try {
            Long resolvedOwnerUserId = resolveOwnerUserId(request, storeCode, BusinessCapability.WAREHOUSE_DISPATCH);
            return service.detailByPsku(resolvedOwnerUserId, storeCode, partnerSku);
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
            requireSourceType(
                    sourceType,
                    ProductVariantSpecSourceType.ALI1688,
                    "Web 端只允许维护 1688 规格。"
            );
            ProductVariantSpecSourceCommand effectiveCommand = command == null ? new ProductVariantSpecSourceCommand() : command;
            Long resolvedOwnerUserId = resolveOwnerUserId(request, effectiveCommand.getStoreCode());
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

    @PutMapping("/by-psku/sources/{sourceType}")
    public ProductVariantSpecSourceView saveSourceByPsku(
            @PathVariable String sourceType,
            @RequestParam String storeCode,
            @RequestParam String partnerSku,
            @RequestBody(required = false) ProductVariantSpecSourceCommand command,
            HttpServletRequest request
    ) {
        ProductVariantSpecService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            requireSourceType(
                    sourceType,
                    ProductVariantSpecSourceType.ALI1688,
                    "Web 端只允许维护 1688 规格。"
            );
            ProductVariantSpecSourceCommand effectiveCommand = command == null ? new ProductVariantSpecSourceCommand() : command;
            Long resolvedOwnerUserId = resolveWebSpecOwnerUserId(request, storeCode);
            effectiveCommand.setOwnerUserId(resolvedOwnerUserId);
            effectiveCommand.setStoreCode(storeCode);
            effectiveCommand.setSourceType(sourceType);
            effectiveCommand.setOperatorUserId(session.getUserId());
            return service.saveSourceByPsku(partnerSku, effectiveCommand);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PutMapping("/by-identity/sources/{sourceType}")
    public ProductVariantSpecSourceView saveSourceByIdentity(
            @PathVariable String sourceType,
            @RequestParam String storeCode,
            @RequestParam String partnerSku,
            @RequestBody(required = false) ProductVariantSpecSourceCommand command,
            HttpServletRequest request
    ) {
        ProductVariantSpecService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            requireSourceType(
                    sourceType,
                    ProductVariantSpecSourceType.WAREHOUSE,
                    "仓管 App 只允许维护仓管规格。"
            );
            ProductVariantSpecSourceCommand effectiveCommand = command == null ? new ProductVariantSpecSourceCommand() : command;
            Long resolvedOwnerUserId = resolveOwnerUserId(request, storeCode, BusinessCapability.WAREHOUSE_DISPATCH);
            effectiveCommand.setOwnerUserId(resolvedOwnerUserId);
            effectiveCommand.setStoreCode(storeCode);
            effectiveCommand.setSourceType(sourceType);
            effectiveCommand.setOperatorUserId(session.getUserId());
            return service.saveSourceByPsku(partnerSku, effectiveCommand);
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
            Long resolvedOwnerUserId = resolveOwnerUserId(request, effectiveCommand.getStoreCode());
            return service.selectEffectiveSourceForType(
                    resolvedOwnerUserId,
                    effectiveCommand.getStoreCode(),
                    variantId,
                    effectiveCommand.getSourceId(),
                    session.getUserId(),
                    ProductVariantSpecSourceType.ALI1688
            );
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping("/by-identity/effective-source")
    public ProductVariantSpecDetailView selectEffectiveSourceByIdentity(
            @RequestParam String storeCode,
            @RequestParam String partnerSku,
            @RequestBody(required = false) ProductVariantSpecEffectiveSourceCommand command,
            HttpServletRequest request
    ) {
        ProductVariantSpecService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            ProductVariantSpecEffectiveSourceCommand effectiveCommand =
                    command == null ? new ProductVariantSpecEffectiveSourceCommand() : command;
            Long resolvedOwnerUserId = resolveOwnerUserId(request, storeCode, BusinessCapability.WAREHOUSE_DISPATCH);
            return service.selectEffectiveSourceByPskuForType(
                    resolvedOwnerUserId,
                    storeCode,
                    partnerSku,
                    null,
                    effectiveCommand.getSourceId(),
                    session.getUserId(),
                    ProductVariantSpecSourceType.WAREHOUSE
            );
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PutMapping("/by-psku/effective-source")
    public ProductVariantSpecDetailView selectEffectiveSourceByPsku(
            @RequestParam String storeCode,
            @RequestParam String partnerSku,
            @RequestBody(required = false) ProductVariantSpecEffectiveSourceCommand command,
            HttpServletRequest request
    ) {
        ProductVariantSpecService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            ProductVariantSpecEffectiveSourceCommand effectiveCommand =
                    command == null ? new ProductVariantSpecEffectiveSourceCommand() : command;
            Long resolvedOwnerUserId = resolveWebSpecOwnerUserId(request, storeCode);
            return service.selectEffectiveSourceByPskuForType(
                    resolvedOwnerUserId,
                    storeCode,
                    partnerSku,
                    null,
                    effectiveCommand.getSourceId(),
                    session.getUserId(),
                    ProductVariantSpecSourceType.ALI1688
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

    private void requireSourceType(String sourceType, String expectedSourceType, String message) {
        String normalizedSourceType = ProductVariantSpecSourceType.normalize(sourceType);
        if (!expectedSourceType.equals(normalizedSourceType)) {
            throw new IllegalArgumentException(message);
        }
    }

    private Long resolveOwnerUserId(HttpServletRequest request, String storeCode) {
        return resolveOwnerUserId(request, storeCode, BusinessCapability.PRODUCT_MASTER);
    }

    private Long resolveWebSpecOwnerUserId(HttpServletRequest request, String storeCode) {
        BusinessAccessContext access = businessAccessResolver().requireAnyStoreAccess(
                request,
                storeCode,
                BusinessCapability.PRODUCT_MASTER,
                BusinessCapability.PROCUREMENT
        );
        return resolveOwnerUserId(access, storeCode);
    }

    private Long resolveOwnerUserId(HttpServletRequest request, String storeCode, BusinessCapability capability) {
        BusinessAccessContext access = businessAccessResolver().requireStoreAccess(request, capability, storeCode);
        return resolveOwnerUserId(access, storeCode);
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

    private ResponseStatusException badRequest(IllegalArgumentException exception) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }
}
