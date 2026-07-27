package com.nuono.next.productlisting;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/product-listing")
public class ProductListingDraftKeywordController {
    private final ProductListingService listingService;
    private final ProductListingDraftKeywordSaveService saveService;
    private final ProductListingKeywordSuggestionService suggestionService;
    private final BusinessAccessResolver accessResolver;

    public ProductListingDraftKeywordController(
            ProductListingService listingService,
            ProductListingDraftKeywordSaveService saveService,
            ProductListingKeywordSuggestionService suggestionService,
            BusinessAccessResolver accessResolver
    ) {
        this.listingService = listingService;
        this.saveService = saveService;
        this.suggestionService = suggestionService;
        this.accessResolver = accessResolver;
    }

    @PostMapping("/drafts/with-keyword-suggestions")
    public ProductListingDraftView save(
            @RequestBody ProductListingDraftKeywordSaveCommand command,
            HttpServletRequest request
    ) {
        try {
            String storeCode = command == null || command.getDraft() == null
                    ? null
                    : command.getDraft().getStoreCode();
            BusinessAccessContext context = accessResolver.requireStoreAccess(
                    request,
                    BusinessCapability.PRODUCT_LISTING,
                    storeCode
            );
            return saveService.save(context, command);
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/drafts/{draftId}/keyword-suggestions")
    public ProductListingKeywordSuggestionView suggestions(
            @PathVariable Long draftId,
            HttpServletRequest request
    ) {
        try {
            BusinessAccessContext context = accessResolver.requireBusinessContext(
                    request,
                    BusinessCapability.PRODUCT_LISTING
            );
            ProductListingDraftView draft = listingService.loadDraft(context, draftId);
            return suggestionService.listForDraft(context, draft);
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}
