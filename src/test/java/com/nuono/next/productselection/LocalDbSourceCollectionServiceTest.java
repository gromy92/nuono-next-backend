package com.nuono.next.productselection;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import com.nuono.next.noonpull.InMemoryNoonRiskBackoffRepository;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.noonpull.NoonRiskBackoffHold;
import com.nuono.next.noonpull.NoonRiskBackoffScope;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LocalDbSourceCollectionServiceTest {

    @Mock
    private ProductSelectionMapper productSelectionMapper;

    @Mock
    private ProductSelectionPermissionGuard permissionGuard;

    @Mock
    private ProductSelectionSourceCollectionCollector sourceCollectionCollector;

    @Mock
    private ProductSelectionSourceCollectionLocalizer sourceCollectionLocalizer;

    @Mock
    private SourceCollectionCompletenessCalculator completenessCalculator;

    @Mock
    private LocalDbAli1688CollectionService ali1688CollectionService;

    private InMemoryNoonRiskBackoffRepository riskBackoffRepository;
    private NoonRiskBackoffGuard riskBackoffGuard;
    private LocalDbSourceCollectionService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-22T09:00:00Z"), ZoneOffset.UTC);
        riskBackoffRepository = new InMemoryNoonRiskBackoffRepository();
        riskBackoffGuard = new NoonRiskBackoffGuard(riskBackoffRepository, clock);
        service = new LocalDbSourceCollectionService(
                productSelectionMapper,
                permissionGuard,
                sourceCollectionCollector,
                sourceCollectionLocalizer,
                completenessCalculator,
                ali1688CollectionService,
                new ObjectMapper(),
                riskBackoffGuard
        );
        ReflectionTestUtils.setField(service, "sourceCollectionSchedulerEnabled", true);
        ReflectionTestUtils.setField(service, "sourceCollectionSchedulerMaxItems", 3);
    }

    @Test
    void noonSourceCollectionWaitsForExistingNoonRiskHoldInsteadOfCallingCollector() {
        ProductSelectionSourceCollectionRow row = noonSourceCollection();
        NoonRiskBackoffHold hold = riskBackoffGuard.recordRiskSignal(
                NoonRiskBackoffScope.report(row.getOwnerUserId(), row.getStoreCode(), "SA"),
                "blocked_by_risk_control",
                "SALES",
                130001L,
                null,
                "sales blocked"
        );
        when(productSelectionMapper.listRunningSourceCollections(3)).thenReturn(List.of(row));
        when(productSelectionMapper.claimSourceCollection(eq(row.getId()), anyString())).thenReturn(1);
        when(productSelectionMapper.selectSourceCollectionById(row.getId())).thenReturn(row);

        service.processRunningSourceCollections();

        verify(sourceCollectionCollector, never()).collect(any(ProductSelectionSourceCollectionRow.class));
        verify(ali1688CollectionService, never()).markSourceCollectionFailed(any(), anyString(), any());
        verify(productSelectionMapper).markSourceCollectionRiskBackoff(
                eq(row.getId()),
                eq("noon_risk_backoff"),
                anyString(),
                eq(row.getUpdatedBy()),
                anyString(),
                eq(hold.getBlockedUntil())
        );
    }

    @Test
    void noonSourceCollectionRecordsRiskHoldWhenCollectorHitsHttp403() {
        ProductSelectionSourceCollectionRow row = noonSourceCollection();
        when(productSelectionMapper.listRunningSourceCollections(3)).thenReturn(List.of(row));
        when(productSelectionMapper.claimSourceCollection(eq(row.getId()), anyString())).thenReturn(1);
        when(productSelectionMapper.selectSourceCollectionById(row.getId())).thenReturn(row);
        when(sourceCollectionCollector.collect(row)).thenThrow(new IllegalStateException("HTTP error fetching URL. Status=403"));

        service.processRunningSourceCollections();

        NoonRiskBackoffHold sourceHold = riskBackoffRepository.selectLatestHold(
                NoonRiskBackoffScope.sourceCollection(row.getOwnerUserId(), row.getStoreCode(), null).getScopeKey()
        );
        ArgumentCaptor<LocalDateTime> nextRunAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(productSelectionMapper).markSourceCollectionRiskBackoff(
                eq(row.getId()),
                eq("noon_risk_backoff"),
                anyString(),
                eq(row.getUpdatedBy()),
                anyString(),
                nextRunAtCaptor.capture()
        );
        verify(ali1688CollectionService, never()).markSourceCollectionFailed(any(), anyString(), any());
        org.junit.jupiter.api.Assertions.assertEquals("blocked_by_risk_control", sourceHold.getRiskType());
        org.junit.jupiter.api.Assertions.assertEquals(LocalDateTime.of(2026, 5, 22, 9, 2), nextRunAtCaptor.getValue());
    }

    private ProductSelectionSourceCollectionRow noonSourceCollection() {
        ProductSelectionSourceCollectionRow row = new ProductSelectionSourceCollectionRow();
        row.setId(86002L);
        row.setOwnerUserId(307L);
        row.setLogicalStoreId(301L);
        row.setStoreCode("STR108065-NSA");
        row.setCollectionNo("PSC-86002");
        row.setSourceType("marketplace-url");
        row.setSourcePlatform("Noon");
        row.setPageUrl("https://noon-catalog.noon.partners/en/catalog/Z123/d?project=PRJ108065");
        row.setStatus("running");
        row.setUpdatedBy(307L);
        return row;
    }
}
