package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import com.nuono.next.infrastructure.mapper.ProductKeywordMapper;
import com.nuono.next.infrastructure.mapper.ProductListingKeywordSuggestionMapper;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.productkeyword.ProductKeywordNormalizer;
import com.nuono.next.productkeyword.ProductKeywordRecord;
import com.nuono.next.productkeyword.ProductKeywordUsageEventRecord;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductListingKeywordSuggestionServiceTest {
    @Mock
    private ProductKeywordMapper keywordMapper;
    @Mock
    private ProductListingKeywordSuggestionMapper suggestionMapper;
    private ProductListingKeywordSuggestionService service;
    private BusinessAccessContext context;

    @BeforeEach
    void setUp() {
        service = new ProductListingKeywordSuggestionService(
                keywordMapper,
                suggestionMapper,
                new ProductKeywordNormalizer()
        );
        context = BusinessAccessContext.builder()
                .sessionUserId(601L)
                .businessOwnerUserId(501L)
                .accountType(BusinessAccountType.OPERATOR)
                .storeCodes(Set.of("STR245027-NAE"))
                .storeOwnerUserIds(Map.of("STR245027-NAE", 501L))
                .menuPaths(Set.of("/purchase/listing"))
                .build();
    }

    @Test
    void syncCapsAndDeduplicatesBilingualSuggestionsWithoutActivatingKeywords() {
        when(suggestionMapper.listDraftSuggestionEvents(10003L)).thenReturn(List.of());
        when(keywordMapper.nextKeywordId()).thenReturn(300001L, 300002L, 300003L, 300004L, 300005L, 300006L, 300007L);
        when(keywordMapper.nextUsageEventId()).thenReturn(320001L, 320002L, 320003L, 320004L, 320005L, 320006L, 320007L);

        ProductListingKeywordSuggestionCommand command = new ProductListingKeywordSuggestionCommand();
        command.setEnglish(List.of("Vintage Paper", "vintage paper", "Lace Edge", "Blue Paper", "Scrapbook Paper", "Craft Paper", "Decorative Paper"));
        command.setArabic(List.of("ورق سكراب بوك", "ورق سكراب بوك"));

        service.sync(context, draftView(), command);

        ArgumentCaptor<ProductKeywordRecord> keywords = ArgumentCaptor.forClass(ProductKeywordRecord.class);
        verify(keywordMapper, times(7)).upsertKeyword(keywords.capture());
        assertEquals(7, keywords.getAllValues().size());
        keywords.getAllValues().forEach(keyword -> assertEquals("OBSERVED", keyword.getStatus()));

        ArgumentCaptor<ProductKeywordUsageEventRecord> events = ArgumentCaptor.forClass(ProductKeywordUsageEventRecord.class);
        verify(keywordMapper, times(7)).upsertUsageEvent(events.capture());
        events.getAllValues().forEach(event -> {
            assertEquals("LISTING_DRAFT", event.getSourceType());
            assertEquals("SUGGESTED", event.getEventStatus());
            assertEquals(10003L, event.getSourceRefId());
        });
    }

    @Test
    void syncMarksRemovedDraftSuggestionsWithoutDeletingOrDowngradingTheKeyword() {
        ProductKeywordUsageEventRecord previous = new ProductKeywordUsageEventRecord();
        previous.setKeywordId(300111L);
        previous.setKeyword("Vintage Paper");
        previous.setKeywordNorm("vintage paper");
        previous.setSourceRefKey("10003:en-AE:vintage paper");
        previous.setEventStatus("SUGGESTED");
        ProductKeywordRecord activeKeyword = new ProductKeywordRecord();
        activeKeyword.setId(300111L);
        activeKeyword.setOwnerUserId(501L);
        activeKeyword.setStoreCode("STR245027-NAE");
        activeKeyword.setSiteCode("AE");
        activeKeyword.setPartnerSku("PAPERSAYS440");
        activeKeyword.setKeyword("Vintage Paper");
        activeKeyword.setKeywordNorm("vintage paper");
        activeKeyword.setStatus("ACTIVE");

        when(suggestionMapper.listDraftSuggestionEvents(10003L)).thenReturn(List.of(previous));
        when(keywordMapper.selectByScopeAndNorm(
                501L, "STR245027-NAE", "AE", "PAPERSAYS440", "vintage paper"
        )).thenReturn(activeKeyword);
        when(keywordMapper.nextUsageEventId()).thenReturn(320111L);

        service.sync(context, draftView(), new ProductListingKeywordSuggestionCommand());

        verify(keywordMapper, never()).upsertKeyword(any());
        ArgumentCaptor<ProductKeywordUsageEventRecord> event = ArgumentCaptor.forClass(ProductKeywordUsageEventRecord.class);
        verify(keywordMapper).upsertUsageEvent(event.capture());
        assertEquals("REMOVED", event.getValue().getEventStatus());
        assertEquals(300111L, event.getValue().getKeywordId());
        assertEquals("ACTIVE", activeKeyword.getStatus());
    }

    @Test
    void syncAllowsAnIncompleteDraftWithoutPskuWhenNoSuggestionsAreSubmitted() {
        ProductListingDraftView view = draftView();
        view.getDraft().setPsku(null);

        service.sync(context, view, new ProductListingKeywordSuggestionCommand());

        verify(keywordMapper, never()).upsertKeyword(any());
        verify(keywordMapper, never()).upsertUsageEvent(any());
    }

    @Test
    void syncDefersSuggestionsWhenTheIncompleteDraftHasNoPsku() {
        ProductListingDraftView view = draftView();
        view.getDraft().setPsku(null);
        ProductListingKeywordSuggestionCommand command = new ProductListingKeywordSuggestionCommand();
        command.setEnglish(List.of("Phone Case"));

        service.sync(context, view, command);

        verify(keywordMapper, never()).upsertKeyword(any());
        verify(keywordMapper, never()).upsertUsageEvent(any());
    }

    private ProductListingDraftView draftView() {
        ProductListingDraftCommand draft = new ProductListingDraftCommand();
        draft.setPsku("PAPERSAYS440");
        ProductListingDraftView view = new ProductListingDraftView();
        view.setDraftId(10003L);
        view.setOwnerUserId(501L);
        view.setStoreCode("STR245027-NAE");
        view.setDraft(draft);
        return view;
    }
}
