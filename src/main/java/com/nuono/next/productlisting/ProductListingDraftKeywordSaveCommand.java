package com.nuono.next.productlisting;

public class ProductListingDraftKeywordSaveCommand {
    private ProductListingDraftCommand draft;
    private ProductListingKeywordSuggestionCommand keywordSuggestions;

    public ProductListingDraftCommand getDraft() {
        return draft;
    }

    public void setDraft(ProductListingDraftCommand draft) {
        this.draft = draft;
    }

    public ProductListingKeywordSuggestionCommand getKeywordSuggestions() {
        return keywordSuggestions;
    }

    public void setKeywordSuggestions(ProductListingKeywordSuggestionCommand keywordSuggestions) {
        this.keywordSuggestions = keywordSuggestions;
    }
}
