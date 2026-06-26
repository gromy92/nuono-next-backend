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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/product-logistics-profiles")
public class ProductVariantLogisticsProfileController {

    private final ObjectProvider<ProductVariantLogisticsProfileService> serviceProvider;
    private final AuthSessionTokenService sessionTokenService;
    private final ObjectProvider<BusinessAccessResolver> businessAccessResolverProvider;

    public ProductVariantLogisticsProfileController(
            ObjectProvider<ProductVariantLogisticsProfileService> serviceProvider,
            AuthSessionTokenService sessionTokenService,
            ObjectProvider<BusinessAccessResolver> businessAccessResolverProvider
    ) {
        this.serviceProvider = serviceProvider;
        this.sessionTokenService = sessionTokenService;
        this.businessAccessResolverProvider = businessAccessResolverProvider;
    }

    @GetMapping
    public ProductVariantLogisticsProfileListView list(
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            @RequestParam String storeCode,
            @RequestParam String skuParent,
            HttpServletRequest request
    ) {
        try {
            Long resolvedOwnerUserId = resolveOwnerUserId(request, storeCode);
            return requireService().list(resolvedOwnerUserId, storeCode, skuParent);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PutMapping("/{variantId}")
    public ProductVariantLogisticsProfileView save(
            @PathVariable Long variantId,
            @RequestBody(required = false) ProductVariantLogisticsProfileCommand command,
            HttpServletRequest request
    ) {
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            ProductVariantLogisticsProfileCommand effectiveCommand =
                    command == null ? new ProductVariantLogisticsProfileCommand() : command;
            Long resolvedOwnerUserId = resolveOwnerUserId(request, effectiveCommand.storeCode);
            effectiveCommand.ownerUserId = resolvedOwnerUserId;
            effectiveCommand.variantId = variantId;
            effectiveCommand.operatorUserId = session.getUserId();
            return requireService().save(effectiveCommand);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    private ProductVariantLogisticsProfileService requireService() {
        ProductVariantLogisticsProfileService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "商品物流属性维护暂时不可用。");
        }
        return service;
    }

    private Long resolveOwnerUserId(HttpServletRequest request, String storeCode) {
        BusinessAccessContext access = businessAccessResolver().requireStoreAccess(
                request,
                BusinessCapability.PRODUCT_MASTER,
                storeCode
        );
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
