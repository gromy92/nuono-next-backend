package com.nuono.next.product;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/product-variant-specs")
public class ProductVariantSpecController {

    private final ObjectProvider<ProductVariantSpecService> serviceProvider;
    private final AuthSessionTokenService sessionTokenService;
    private final ObjectProvider<ProductMasterAccessGuard> accessGuardProvider;

    public ProductVariantSpecController(
            ObjectProvider<ProductVariantSpecService> serviceProvider,
            AuthSessionTokenService sessionTokenService,
            ObjectProvider<ProductMasterAccessGuard> accessGuardProvider
    ) {
        this.serviceProvider = serviceProvider;
        this.sessionTokenService = sessionTokenService;
        this.accessGuardProvider = accessGuardProvider;
    }

    @GetMapping
    public ProductVariantSpecListView list(
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
            @RequestParam String storeCode,
            @RequestParam String skuParent,
            HttpServletRequest request
    ) {
        ProductVariantSpecService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            Long resolvedOwnerUserId = accessGuard().resolveOwnerUserId(session, ownerUserId, storeCode);
            ProductVariantSpecListCommand command = new ProductVariantSpecListCommand();
            command.setOwnerUserId(resolvedOwnerUserId);
            command.setStoreCode(storeCode);
            command.setSkuParent(skuParent);
            return service.list(command);
        } catch (ProductMasterAccessDeniedException exception) {
            throw productAccessDenied(exception);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception);
        }
    }

    @PostMapping
    public ProductVariantSpecView save(
            @RequestBody(required = false) ProductVariantSpecCommand command,
            HttpServletRequest request
    ) {
        ProductVariantSpecService service = requireService();
        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            if (command == null) {
                command = new ProductVariantSpecCommand();
            }
            Long resolvedOwnerUserId = accessGuard().resolveOwnerUserId(
                    session,
                    command.getOwnerUserId(),
                    command.getStoreCode()
            );
            command.setOwnerUserId(resolvedOwnerUserId);
            command.setOperatorUserId(session.getUserId());
            return service.save(command);
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
