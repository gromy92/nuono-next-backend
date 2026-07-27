package com.nuono.next.productlisting;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/product-listing/drafts")
public class ProductListingDraftLookupController {

    private final ProductListingService service;
    private final BusinessAccessResolver businessAccessResolver;

    public ProductListingDraftLookupController(
            ProductListingService service,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.service = service;
        this.businessAccessResolver = businessAccessResolver;
    }

    @GetMapping("/by-source")
    public List<ProductListingDraftView> activeDraftBySource(
            @RequestParam String storeCode,
            @RequestParam String sourceType,
            @RequestParam Long sourceRefId,
            HttpServletRequest request
    ) {
        try {
            BusinessAccessContext context = businessAccessResolver.requireStoreAccess(
                    request,
                    BusinessCapability.PRODUCT_LISTING,
                    storeCode
            );
            ProductListingDraftView draft = service.loadActiveSourceDraft(
                    context,
                    storeCode,
                    sourceType,
                    sourceRefId
            );
            return draft == null ? List.of() : List.of(draft);
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    exception.getMessage(),
                    exception
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage(),
                    exception
            );
        }
    }
}
