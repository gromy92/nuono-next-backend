package com.nuono.next.productlisting;

import com.nuono.next.permission.access.BusinessAccessContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductListingDraftKeywordSaveService {
    private final ProductListingService listingService;
    private final ProductListingKeywordSuggestionService suggestionService;

    public ProductListingDraftKeywordSaveService(
            ProductListingService listingService,
            ProductListingKeywordSuggestionService suggestionService
    ) {
        this.listingService = listingService;
        this.suggestionService = suggestionService;
    }

    @Transactional
    public ProductListingDraftView save(
            BusinessAccessContext context,
            ProductListingDraftKeywordSaveCommand command
    ) {
        if (command == null || command.getDraft() == null) {
            throw new IllegalArgumentException("Product listing draft is required.");
        }
        ProductListingDraftView saved = listingService.saveDraft(context, command.getDraft());
        suggestionService.sync(context, saved, command.getKeywordSuggestions());
        return saved;
    }
}
