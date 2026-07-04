package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.productkeyword.ProductKeywordCompetitorIndexer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CompetitorAnalysisMaintenanceServiceTest {

    @Mock
    private CompetitorAnalysisMapper mapper;

    @Mock
    private ProductKeywordCompetitorIndexer productKeywordCompetitorIndexer;

    private CompetitorAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new CompetitorAnalysisService(mapper);
        service.setProductKeywordCompetitorIndexer(productKeywordCompetitorIndexer);
    }

    @Test
    void addKeywordNormalizesAndDoesNotCreateDuplicateActiveKeyword() {
        CompetitorKeywordCommand command = new CompetitorKeywordCommand();
        command.setKeyword(" Laundry   Basket ");
        when(mapper.selectWatchProductById(501L, 180123L)).thenReturn(watchProduct("ZSELF001"));
        when(mapper.selectKeywordByNorm(180123L, "laundry basket")).thenReturn(keyword(190001L, "laundry basket"));
        mockDetail();

        service.addKeyword(operatorContext(), 180123L, command);

        verify(mapper, never()).nextKeywordId();
        verify(mapper, never()).insertKeyword(any());
    }

    @Test
    void addKeywordWritesCollapsedLowercaseKeywordNorm() {
        CompetitorKeywordCommand command = new CompetitorKeywordCommand();
        command.setKeyword(" Foldable   Hamper ");
        command.setLocale("en-SA");
        when(mapper.selectWatchProductById(501L, 180123L)).thenReturn(watchProduct("ZSELF001"));
        when(mapper.selectKeywordByNorm(180123L, "foldable hamper")).thenReturn(null);
        when(mapper.nextKeywordId()).thenReturn(190123L);
        mockDetail();

        service.addKeyword(operatorContext(), 180123L, command);

        ArgumentCaptor<CompetitorKeywordInsertCommand> captor =
                ArgumentCaptor.forClass(CompetitorKeywordInsertCommand.class);
        verify(mapper).insertKeyword(captor.capture());
        assertEquals(190123L, captor.getValue().getId());
        assertEquals("Foldable Hamper", captor.getValue().getKeyword());
        assertEquals("foldable hamper", captor.getValue().getKeywordNorm());
        assertEquals("en-SA", captor.getValue().getLocale());
    }

    @Test
    void addKeywordIndexesActiveKeywordIntoProductKeywordHistory() {
        CompetitorKeywordCommand command = new CompetitorKeywordCommand();
        command.setKeyword(" Milk Bottle ");
        when(mapper.selectWatchProductById(501L, 180123L)).thenReturn(watchProduct("ZSELF001"));
        when(mapper.selectKeywordByNorm(180123L, "milk bottle")).thenReturn(null);
        when(mapper.nextKeywordId()).thenReturn(190123L);
        mockDetail();

        service.addKeyword(operatorContext(), 180123L, command);

        ArgumentCaptor<ProductKeywordCompetitorIndexer.CompetitorKeywordIndexCommand> captor =
                ArgumentCaptor.forClass(ProductKeywordCompetitorIndexer.CompetitorKeywordIndexCommand.class);
        verify(productKeywordCompetitorIndexer).indexKeyword(captor.capture());
        assertEquals(501L, captor.getValue().getOwnerUserId());
        assertEquals(180123L, captor.getValue().getWatchProductId());
        assertEquals(190123L, captor.getValue().getKeywordId());
        assertEquals("STR108065-NSA", captor.getValue().getStoreCode());
        assertEquals("SA", captor.getValue().getSiteCode());
        assertEquals("BASKET-SA-001-BLUE", captor.getValue().getPartnerSku());
        assertEquals("Milk Bottle", captor.getValue().getKeyword());
        assertEquals("ACTIVE", captor.getValue().getStatus());
        assertEquals(601L, captor.getValue().getActorUserId());
    }

    @Test
    void updateKeywordIndexesLatestPersistedKeywordIntoProductKeywordHistory() {
        CompetitorKeywordCommand command = new CompetitorKeywordCommand();
        command.setKeyword(" Milk Bottle ");
        CompetitorKeywordRow updated = keyword(190001L, "milk bottle");
        updated.setKeyword("Milk Bottle");
        when(mapper.selectKeywordScopeById(190001L)).thenReturn(keywordScope(190001L, 180123L));
        when(mapper.selectKeywordById(190001L)).thenReturn(updated);
        when(mapper.selectWatchProductById(501L, 180123L)).thenReturn(watchProduct("ZSELF001"));
        mockDetail();

        service.updateKeyword(operatorContext(), 190001L, command);

        ArgumentCaptor<ProductKeywordCompetitorIndexer.CompetitorKeywordIndexCommand> captor =
                ArgumentCaptor.forClass(ProductKeywordCompetitorIndexer.CompetitorKeywordIndexCommand.class);
        verify(productKeywordCompetitorIndexer).indexKeyword(captor.capture());
        assertEquals(501L, captor.getValue().getOwnerUserId());
        assertEquals(180123L, captor.getValue().getWatchProductId());
        assertEquals(190001L, captor.getValue().getKeywordId());
        assertEquals("STR108065-NSA", captor.getValue().getStoreCode());
        assertEquals("SA", captor.getValue().getSiteCode());
        assertEquals("BASKET-SA-001-BLUE", captor.getValue().getPartnerSku());
        assertEquals("Milk Bottle", captor.getValue().getKeyword());
        assertEquals("ACTIVE", captor.getValue().getStatus());
        assertEquals(601L, captor.getValue().getActorUserId());
    }

    @Test
    void manualCompetitorUpgradesPendingCandidateAndAppliesSelectedKeywordOnly() {
        CompetitorManualCompetitorCommand command = new CompetitorManualCompetitorCommand();
        command.setInput("https://www.noon.com/saudi-en/example/N51004211A/p/");
        command.setKeywordId(190001L);
        when(mapper.selectWatchProductById(501L, 180123L)).thenReturn(watchProduct("ZSELF001"));
        when(mapper.selectKeywordScopeById(190001L)).thenReturn(keywordScope(190001L, 180123L));
        CompetitorProductRow pending = competitorProduct(200123L, "N51004211A", "PENDING");
        when(mapper.selectCompetitorProductByCode(180123L, "N51004211A")).thenReturn(pending);
        mockDetail();

        service.addManualCompetitor(operatorContext(), 180123L, command);

        verify(mapper).markCompetitorProductConfirmed(200123L, 601L);
        verify(mapper).upsertKeywordProductRelation(190001L, 200123L, "CONFIRMED", 601L);
        verify(mapper, never()).upsertKeywordProductRelation(190002L, 200123L, "CONFIRMED", 601L);
        verify(mapper, never()).listActiveKeywordsByWatchProductId(180123L);
    }

    @Test
    void manualCompetitorRequiresKeywordId() {
        CompetitorManualCompetitorCommand command = new CompetitorManualCompetitorCommand();
        command.setInput("N51004211A");
        when(mapper.selectWatchProductById(501L, 180123L)).thenReturn(watchProduct("ZSELF001"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.addManualCompetitor(operatorContext(), 180123L, command)
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("COMPETITOR_KEYWORD_REQUIRED", error.getReason());
        verify(mapper, never()).selectCompetitorProductByCode(any(), any());
    }

    @Test
    void confirmRejectsKeywordAndCandidateFromDifferentWatchProducts() {
        when(mapper.selectKeywordScopeById(190001L)).thenReturn(keywordScope(190001L, 180123L));
        when(mapper.selectCompetitorProductScopeById(200001L))
                .thenReturn(productScope(200001L, 180999L, "PENDING", "ZCOMP001"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.confirmCandidate(operatorContext(), 190001L, 200001L)
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("COMPETITOR_SCOPE_MISMATCH", error.getReason());
        verify(mapper, never()).markCompetitorProductConfirmed(any(), any());
    }

    @Test
    void ignoreRejectsAlreadyConfirmedCompetitor() {
        when(mapper.selectKeywordScopeById(190001L)).thenReturn(keywordScope(190001L, 180123L));
        when(mapper.selectCompetitorProductScopeById(200001L))
                .thenReturn(productScope(200001L, 180123L, "CONFIRMED", "ZCOMP001"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.ignoreCandidate(operatorContext(), 190001L, 200001L)
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("COMPETITOR_CONFIRMED_CANNOT_IGNORE", error.getReason());
        verify(mapper, never()).softDeleteKeywordProductRelation(any(), any(), any());
    }

    @Test
    void removeConfirmedCandidateOnlyRemovesCurrentKeywordRelation() {
        when(mapper.selectKeywordScopeById(190001L)).thenReturn(keywordScope(190001L, 180123L));
        when(mapper.selectCompetitorProductScopeById(200001L))
                .thenReturn(productScope(200001L, 180123L, "CONFIRMED", "ZCOMP001"));
        when(mapper.selectWatchProductById(501L, 180123L)).thenReturn(watchProduct("ZSELF001"));
        mockDetail();

        service.removeCandidateFromKeyword(operatorContext(), 190001L, 200001L);

        verify(mapper).softDeleteKeywordProductRelation(190001L, 200001L, 601L);
        verify(mapper, never()).markCompetitorProductConfirmed(any(), any());
        verify(mapper, never()).upsertKeywordProductRelation(any(), any(), any(), any());
    }

    @Test
    void ignorePendingCandidateDeletesCurrentKeywordRelation() {
        when(mapper.selectKeywordScopeById(190001L)).thenReturn(keywordScope(190001L, 180123L));
        when(mapper.selectCompetitorProductScopeById(200001L))
                .thenReturn(productScope(200001L, 180123L, "PENDING", "ZCOMP001"));
        when(mapper.selectWatchProductById(501L, 180123L)).thenReturn(watchProduct("ZSELF001"));
        mockDetail();

        service.ignoreCandidate(operatorContext(), 190001L, 200001L);

        verify(mapper).softDeleteKeywordProductRelation(190001L, 200001L, 601L);
        verify(mapper, never()).markCompetitorProductConfirmed(any(), any());
        verify(mapper, never()).upsertKeywordProductRelation(any(), any(), any(), any());
    }

    private void mockDetail() {
        when(mapper.listKeywordsByWatchProductId(180123L)).thenReturn(List.of());
        when(mapper.listProductsByWatchProductId(180123L)).thenReturn(List.of());
        when(mapper.listKeywordProductRelationsByWatchProductId(180123L)).thenReturn(List.of());
        when(mapper.listLatestRankPointsByWatchProductId(180123L)).thenReturn(List.of());
    }

    private static CompetitorWatchProductRow watchProduct(String selfNoonProductCode) {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(180123L);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setPartnerSku("BASKET-SA-001-BLUE");
        row.setSelfNoonProductCode(selfNoonProductCode);
        row.setSelfCodeType("Z_CODE");
        row.setStatus("ACTIVE");
        return row;
    }

    private static CompetitorKeywordRow keyword(Long id, String norm) {
        CompetitorKeywordRow row = new CompetitorKeywordRow();
        row.setId(id);
        row.setWatchProductId(180123L);
        row.setKeyword(norm);
        row.setKeywordNorm(norm);
        row.setStatus("ACTIVE");
        return row;
    }

    private static CompetitorProductRow competitorProduct(Long id, String noonCode, String status) {
        CompetitorProductRow row = new CompetitorProductRow();
        row.setId(id);
        row.setWatchProductId(180123L);
        row.setNoonProductCode(noonCode);
        row.setCodeType(noonCode.startsWith("N") ? "N_CODE" : "Z_CODE");
        row.setReviewStatus(status);
        return row;
    }

    private static CompetitorKeywordScopeRow keywordScope(Long keywordId, Long watchProductId) {
        CompetitorKeywordScopeRow row = new CompetitorKeywordScopeRow();
        row.setKeywordId(keywordId);
        row.setWatchProductId(watchProductId);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setPartnerSku("BASKET-SA-001-BLUE");
        row.setStatus("ACTIVE");
        return row;
    }

    private static CompetitorProductScopeRow productScope(
            Long productId,
            Long watchProductId,
            String reviewStatus,
            String noonCode
    ) {
        CompetitorProductScopeRow row = new CompetitorProductScopeRow();
        row.setCompetitorProductId(productId);
        row.setWatchProductId(watchProductId);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setReviewStatus(reviewStatus);
        row.setNoonProductCode(noonCode);
        return row;
    }

    private static BusinessAccessContext operatorContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(601L)
                .businessOwnerUserId(501L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleLevel(3)
                .roleName("运营")
                .storeCodes(Set.of("STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NSA", 501L))
                .menuPaths(Set.of("/operations/competitor-analysis"))
                .build();
    }
}
