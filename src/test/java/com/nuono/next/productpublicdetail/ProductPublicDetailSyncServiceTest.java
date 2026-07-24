package com.nuono.next.productpublicdetail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductPublicDetailMapper;
import com.nuono.next.noonpull.NoonPullFailurePolicy;
import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.noonpull.NoonRiskBackoffHold;
import com.nuono.next.noonpull.NoonRiskBackoffRepository;
import com.nuono.next.noonpull.NoonRiskBackoffScope;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailAdapter;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailRequest;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailResult;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskRepository;
import com.nuono.next.system.task.OperationalTaskService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductPublicDetailSyncServiceTest {
    @Mock
    private ProductPublicDetailMapper mapper;

    @Mock
    private NoonPublicProductDetailAdapter adapter;

    private InMemoryOperationalTaskRepository taskRepository;
    private ProductPublicDetailSyncService service;

    @BeforeEach
    void setUp() {
        taskRepository = new InMemoryOperationalTaskRepository();
        service = new ProductPublicDetailSyncService(
                mapper,
                new OperationalTaskService(taskRepository, fixedClock()),
                adapter,
                (accountKey, task) -> task.run(),
                new ObjectMapper(),
                fixedClock()
        );
    }

    @Test
    void partialResultInsertsDailySnapshotAndMarksLatest() {
        when(mapper.selectActiveScope(501L, "CANMAN", "SA")).thenReturn(scope());
        when(mapper.listCandidates(eq(501L), eq("CANMAN"), eq("SA"), anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(List.of(candidate()));
        when(adapter.adapterVersion()).thenReturn("test-adapter");
        when(adapter.fetch(any(NoonPublicProductDetailRequest.class))).thenReturn(partialResult());
        when(mapper.selectDailySnapshot(eq(1001L), eq(2001L), eq("SA"), eq("NOON"), any(LocalDate.class))).thenReturn(null);
        when(mapper.nextSnapshotId()).thenReturn(300001L);

        ProductPublicDetailTaskView task = service.submitManual(context(), "canman", "sa");

        assertEquals("SUCCEEDED", taskRepository.selectById(task.getId()).getStatus().name());
        ArgumentCaptor<ProductPublicDetailSnapshot> snapshotCaptor = ArgumentCaptor.forClass(ProductPublicDetailSnapshot.class);
        verify(mapper).insertSnapshot(snapshotCaptor.capture());
        ProductPublicDetailSnapshot snapshot = snapshotCaptor.getValue();
        assertEquals(300001L, snapshot.getId());
        assertEquals(ProductPublicDetailSyncStatus.PARTIAL, snapshot.getSyncStatus());
        assertEquals(Boolean.TRUE, snapshot.getLatest());
        assertEquals("ZCANMAN12", snapshot.getNoonProductCode());
        assertEquals("Canman public title", snapshot.getTitleEn());
        assertEquals(new BigDecimal("19.90"), snapshot.getPriceAmount());
        verify(mapper).clearLatestForProduct(1001L, 2001L, "SA", "NOON", 300001L, 901L);
        verify(mapper).markLatest(300001L, 901L);
    }

    @Test
    void failedSameDayResultDoesNotOverwriteExistingLatestPartial() {
        when(mapper.selectActiveScope(501L, "CANMAN", "SA")).thenReturn(scope());
        when(mapper.listCandidates(eq(501L), eq("CANMAN"), eq("SA"), anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(List.of(candidate()));
        when(adapter.adapterVersion()).thenReturn("test-adapter");
        when(adapter.fetch(any(NoonPublicProductDetailRequest.class))).thenReturn(failedResult());
        when(mapper.selectDailySnapshot(eq(1001L), eq(2001L), eq("SA"), eq("NOON"), any(LocalDate.class)))
                .thenReturn(existingLatestPartialSnapshot());

        service.submitManual(context(), "CANMAN", "SA");

        verify(mapper, never()).insertSnapshot(any(ProductPublicDetailSnapshot.class));
        verify(mapper, never()).updateSnapshotPreservingTrustedData(any(ProductPublicDetailSnapshot.class));
        verify(mapper, never()).clearLatestForProduct(any(), any(), any(), any(), any(), any());
        verify(mapper, never()).markLatest(any(), any());
    }

    @Test
    void failedResultWritesNonLatestAuditSnapshotWhenNoExistingDetail() {
        when(mapper.selectActiveScope(501L, "CANMAN", "SA")).thenReturn(scope());
        when(mapper.listCandidates(eq(501L), eq("CANMAN"), eq("SA"), anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(List.of(candidate()));
        when(adapter.adapterVersion()).thenReturn("test-adapter");
        when(adapter.fetch(any(NoonPublicProductDetailRequest.class))).thenReturn(failedResult());
        when(mapper.selectDailySnapshot(eq(1001L), eq(2001L), eq("SA"), eq("NOON"), any(LocalDate.class))).thenReturn(null);
        when(mapper.nextSnapshotId()).thenReturn(300002L);

        service.submitManual(context(), "CANMAN", "SA");

        ArgumentCaptor<ProductPublicDetailSnapshot> snapshotCaptor = ArgumentCaptor.forClass(ProductPublicDetailSnapshot.class);
        verify(mapper).insertSnapshot(snapshotCaptor.capture());
        ProductPublicDetailSnapshot snapshot = snapshotCaptor.getValue();
        assertEquals(300002L, snapshot.getId());
        assertEquals(ProductPublicDetailSyncStatus.FAILED, snapshot.getSyncStatus());
        assertEquals(Boolean.FALSE, snapshot.getLatest());
        assertEquals("RATE_LIMITED", snapshot.getFailureCode());
        verify(mapper, never()).updateSnapshotPreservingTrustedData(any(ProductPublicDetailSnapshot.class));
        verify(mapper, never()).clearLatestForProduct(any(), any(), any(), any(), any(), any());
        verify(mapper, never()).markLatest(any(), any());
    }

    @Test
    void notFoundResultWritesNonLatestAuditSnapshotWhenNoExistingDetail() {
        when(mapper.selectActiveScope(501L, "CANMAN", "SA")).thenReturn(scope());
        when(mapper.listCandidates(eq(501L), eq("CANMAN"), eq("SA"), anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(List.of(candidate()));
        when(adapter.adapterVersion()).thenReturn("test-adapter");
        when(adapter.fetch(any(NoonPublicProductDetailRequest.class))).thenReturn(notFoundResult());
        when(mapper.selectDailySnapshot(eq(1001L), eq(2001L), eq("SA"), eq("NOON"), any(LocalDate.class))).thenReturn(null);
        when(mapper.nextSnapshotId()).thenReturn(300003L);

        service.submitManual(context(), "CANMAN", "SA");

        ArgumentCaptor<ProductPublicDetailSnapshot> snapshotCaptor = ArgumentCaptor.forClass(ProductPublicDetailSnapshot.class);
        verify(mapper).insertSnapshot(snapshotCaptor.capture());
        ProductPublicDetailSnapshot snapshot = snapshotCaptor.getValue();
        assertEquals(300003L, snapshot.getId());
        assertEquals(ProductPublicDetailSyncStatus.NOT_FOUND, snapshot.getSyncStatus());
        assertEquals(Boolean.FALSE, snapshot.getLatest());
        assertEquals("PUBLIC_DETAIL_NOT_FOUND", snapshot.getFailureCode());
        verify(mapper, never()).updateSnapshotPreservingTrustedData(any(ProductPublicDetailSnapshot.class));
        verify(mapper, never()).clearLatestForProduct(any(), any(), any(), any(), any(), any());
        verify(mapper, never()).markLatest(any(), any());
    }

    @Test
    void riskFailureRecordsBackoffAndStopsCurrentScope() {
        InMemoryRiskBackoffRepository riskRepository = new InMemoryRiskBackoffRepository();
        ProductPublicDetailSyncService riskAwareService = new ProductPublicDetailSyncService(
                mapper,
                new OperationalTaskService(taskRepository, fixedClock()),
                () -> adapter,
                (accountKey, task) -> task.run(),
                new ObjectMapper(),
                fixedClock(),
                100,
                1,
                12,
                new NoonRiskBackoffGuard(riskRepository),
                new NoonPullFailurePolicy(fixedClock())
        );
        when(mapper.selectActiveScope(501L, "CANMAN", "SA")).thenReturn(scope());
        ProductPublicDetailCandidate first = candidate();
        ProductPublicDetailCandidate second = candidate();
        second.setProductMasterId(1002L);
        second.setProductVariantId(2002L);
        second.setProductSiteOfferId(2502L);
        second.setPartnerSku("CANMAN-SKU-2");
        second.setSkuParent("ZCANMAN13");
        second.setNoonProductCode("ZCANMAN13");
        when(mapper.listCandidates(eq(501L), eq("CANMAN"), eq("SA"), anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(List.of(first, second));
        when(adapter.adapterVersion()).thenReturn("test-adapter");
        when(adapter.fetch(any(NoonPublicProductDetailRequest.class))).thenReturn(blockedResult());

        ProductPublicDetailTaskView task = riskAwareService.submitScheduled(501L, "CANMAN", "SA");

        assertEquals("FAILED", taskRepository.selectById(task.getId()).getStatus().name());
        assertEquals("PRODUCT_PUBLIC_DETAIL_RISK_BACKOFF", taskRepository.selectById(task.getId()).getErrorCode());
        verify(adapter, times(1)).fetch(any(NoonPublicProductDetailRequest.class));
        NoonRiskBackoffHold hold = riskRepository.selectLatestHold(
                NoonRiskBackoffScope.publicDetail(501L, "CANMAN", "SA").getScopeKey()
        );
        assertEquals("blocked_by_risk_control", hold.getRiskType());
        assertEquals("PUBLIC_DETAIL", hold.getOperationGroup());
    }

    @Test
    void runningTaskStopsBeforeNextCandidateWhenAccountWideNoonBackoffAppears() {
        InMemoryRiskBackoffRepository riskRepository = new InMemoryRiskBackoffRepository();
        NoonRiskBackoffGuard riskBackoffGuard = new NoonRiskBackoffGuard(riskRepository, fixedClock());
        ProductPublicDetailSyncService riskAwareService = new ProductPublicDetailSyncService(
                mapper,
                new OperationalTaskService(taskRepository, fixedClock()),
                () -> adapter,
                (accountKey, task) -> task.run(),
                new ObjectMapper(),
                fixedClock(),
                100,
                1,
                12,
                riskBackoffGuard,
                new NoonPullFailurePolicy(fixedClock())
        );
        when(mapper.selectActiveScope(501L, "CANMAN", "SA")).thenReturn(scope());
        ProductPublicDetailCandidate first = candidate();
        ProductPublicDetailCandidate second = candidate();
        second.setProductMasterId(1002L);
        second.setProductVariantId(2002L);
        second.setProductSiteOfferId(2502L);
        second.setPartnerSku("CANMAN-SKU-2");
        second.setSkuParent("ZCANMAN13");
        second.setNoonProductCode("ZCANMAN13");
        when(mapper.listCandidates(eq(501L), eq("CANMAN"), eq("SA"), anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(List.of(first, second));
        when(adapter.adapterVersion()).thenReturn("test-adapter");
        when(adapter.fetch(any(NoonPublicProductDetailRequest.class))).thenAnswer(invocation -> {
            riskBackoffGuard.recordRiskSignal(
                    NoonRiskBackoffScope.allNoon(501L, "CANMAN", "SA"),
                    "blocked_by_risk_control",
                    "REPORT",
                    130001L,
                    null,
                    "Sales report hit Noon risk control"
            );
            return partialResult();
        });
        when(mapper.selectDailySnapshot(eq(1001L), eq(2001L), eq("SA"), eq("NOON"), any(LocalDate.class))).thenReturn(null);
        when(mapper.nextSnapshotId()).thenReturn(300001L);

        ProductPublicDetailTaskView task = riskAwareService.submitScheduled(501L, "CANMAN", "SA");

        assertEquals("FAILED", taskRepository.selectById(task.getId()).getStatus().name());
        assertEquals("PRODUCT_PUBLIC_DETAIL_RISK_BACKOFF", taskRepository.selectById(task.getId()).getErrorCode());
        verify(adapter, times(1)).fetch(any(NoonPublicProductDetailRequest.class));
        verify(mapper).insertSnapshot(any(ProductPublicDetailSnapshot.class));
    }

    @Test
    void submitManualKeepsRequestedScopeAndAppliesPreferredSiteFilter() {
        ProductPublicDetailScope aeScope = scope("STR108065-NAE", "AE");
        when(mapper.selectActiveScope(501L, "STR108065-NAE", "AE")).thenReturn(aeScope);
        when(mapper.listCandidates(eq(501L), eq("STR108065-NAE"), eq("AE"), anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(List.of(candidate("STR108065-NAE", "AE")));
        when(adapter.adapterVersion()).thenReturn("test-adapter");
        when(adapter.fetch(any(NoonPublicProductDetailRequest.class))).thenReturn(partialResult());
        when(mapper.selectDailySnapshot(eq(1001L), eq(2001L), eq("AE"), eq("NOON"), any(LocalDate.class))).thenReturn(null);
        when(mapper.nextSnapshotId()).thenReturn(300001L);

        ProductPublicDetailTaskView task = service.submitManual(contextWith108065Stores(), "STR108065-NAE", "AE");

        assertEquals("STR108065-NAE", task.getStoreCode());
        assertEquals("AE", task.getSiteCode());
        verify(mapper, never()).selectPreferredScope(any(), any(), anyInt());
        verify(mapper).listCandidates(eq(501L), eq("STR108065-NAE"), eq("AE"), anyInt(), anyInt(), anyInt(), eq(false), eq(true));
    }

    @Test
    void submitManualUsesRequestedScopeWhenPreferredScopeHasNoCandidates() {
        ProductPublicDetailScope aeScope = scope("STR108065-NAE", "AE");
        when(mapper.selectActiveScope(501L, "STR108065-NAE", "AE")).thenReturn(aeScope);
        when(mapper.listCandidates(eq(501L), eq("STR108065-NAE"), eq("AE"), anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(List.of(candidate("STR108065-NAE", "AE")));
        when(adapter.adapterVersion()).thenReturn("test-adapter");
        when(adapter.fetch(any(NoonPublicProductDetailRequest.class))).thenReturn(partialResult());
        when(mapper.selectDailySnapshot(eq(1001L), eq(2001L), eq("AE"), eq("NOON"), any(LocalDate.class))).thenReturn(null);
        when(mapper.nextSnapshotId()).thenReturn(300001L);

        ProductPublicDetailTaskView task = service.submitManual(contextWith108065Stores(), "STR108065-NAE", "AE");

        assertEquals("STR108065-NAE", task.getStoreCode());
        assertEquals("AE", task.getSiteCode());
        verify(mapper, never()).selectPreferredScope(any(), any(), anyInt());
        verify(mapper).listCandidates(eq(501L), eq("STR108065-NAE"), eq("AE"), anyInt(), anyInt(), anyInt(), eq(false), eq(true));
    }

    @Test
    void submitManualUsesRequestedScopeWhenPreferredScopeIsNotAccessible() {
        ProductPublicDetailScope aeScope = scope("STR108065-NAE", "AE");
        when(mapper.selectActiveScope(501L, "STR108065-NAE", "AE")).thenReturn(aeScope);
        when(mapper.listCandidates(eq(501L), eq("STR108065-NAE"), eq("AE"), anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(List.of(candidate("STR108065-NAE", "AE")));
        when(adapter.adapterVersion()).thenReturn("test-adapter");
        when(adapter.fetch(any(NoonPublicProductDetailRequest.class))).thenReturn(partialResult());
        when(mapper.selectDailySnapshot(eq(1001L), eq(2001L), eq("AE"), eq("NOON"), any(LocalDate.class))).thenReturn(null);
        when(mapper.nextSnapshotId()).thenReturn(300001L);

        ProductPublicDetailTaskView task = service.submitManual(contextWith108065AeStore(), "STR108065-NAE", "AE");

        assertEquals("STR108065-NAE", task.getStoreCode());
        assertEquals("AE", task.getSiteCode());
        verify(mapper, never()).selectPreferredScope(any(), any(), anyInt());
        verify(mapper).listCandidates(eq(501L), eq("STR108065-NAE"), eq("AE"), anyInt(), anyInt(), anyInt(), eq(false), eq(true));
        verify(mapper, never()).listCandidates(eq(501L), eq("STR108065-NSA"), eq("SA"), anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean());
    }

    @Test
    void submitScheduledUsesRequestedScopeEvenWhenSiblingSiteCouldBePreferred() {
        ProductPublicDetailScope aeScope = scope("STR108065-NAE", "AE");
        when(mapper.selectActiveScope(501L, "STR108065-NAE", "AE")).thenReturn(aeScope);
        when(mapper.listCandidates(eq(501L), eq("STR108065-NAE"), eq("AE"), anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(List.of(candidate("STR108065-NAE", "AE")));
        when(adapter.adapterVersion()).thenReturn("test-adapter");
        when(adapter.fetch(any(NoonPublicProductDetailRequest.class))).thenReturn(partialResult());
        when(mapper.selectDailySnapshot(eq(1001L), eq(2001L), eq("AE"), eq("NOON"), any(LocalDate.class))).thenReturn(null);
        when(mapper.nextSnapshotId()).thenReturn(300001L);

        ProductPublicDetailTaskView task = service.submitScheduled(501L, "STR108065-NAE", "AE");

        assertEquals("STR108065-NAE", task.getStoreCode());
        assertEquals("AE", task.getSiteCode());
        verify(mapper, never()).selectPreferredScope(any(), any(), anyInt());
        verify(mapper).listCandidates(eq(501L), eq("STR108065-NAE"), eq("AE"), anyInt(), anyInt(), anyInt(), eq(true), eq(true));
    }

    @Test
    void syncStatusUsesPreferredScopeForSiblingSiteRequest() {
        ProductPublicDetailScope aeScope = scope("STR108065-NAE", "AE");
        ProductPublicDetailScope saScope = scope("STR108065-NSA", "SA");
        when(mapper.selectActiveScope(501L, "STR108065-NAE", "AE")).thenReturn(aeScope);
        when(mapper.selectPreferredScope(501L, 601L, 0)).thenReturn(saScope);
        when(mapper.countCandidates(501L, "STR108065-NSA", "SA", 0, true)).thenReturn(3);

        ProductPublicDetailStatusView status = service.syncStatus(contextWith108065Stores(), "STR108065-NAE", "AE");

        assertEquals("STR108065-NSA", status.getStoreCode());
        assertEquals("SA", status.getSiteCode());
        assertEquals(3, status.getCandidateCount());
    }

    @Test
    void syncStatusFallsBackToRequestedScopeWhenPreferredScopeIsNotAccessible() {
        ProductPublicDetailScope aeScope = scope("STR108065-NAE", "AE");
        ProductPublicDetailScope saScope = scope("STR108065-NSA", "SA");
        when(mapper.selectActiveScope(501L, "STR108065-NAE", "AE")).thenReturn(aeScope);
        when(mapper.selectPreferredScope(501L, 601L, 0)).thenReturn(saScope);
        when(mapper.countCandidates(501L, "STR108065-NAE", "AE", 0, false)).thenReturn(5);

        ProductPublicDetailStatusView status = service.syncStatus(contextWith108065AeStore(), "STR108065-NAE", "AE");

        assertEquals("STR108065-NAE", status.getStoreCode());
        assertEquals("AE", status.getSiteCode());
        assertEquals(5, status.getCandidateCount());
        verify(mapper).countCandidates(501L, "STR108065-NAE", "AE", 0, false);
        verify(mapper, never()).countCandidates(501L, "STR108065-NSA", "SA", 0, true);
    }

    private static BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .sessionUserId(901L)
                .businessOwnerUserId(501L)
                .storeCodes(java.util.Set.of("CANMAN"))
                .storeOwnerUserIds(Map.of("CANMAN", 501L))
                .build();
    }

    private static BusinessAccessContext contextWith108065Stores() {
        return BusinessAccessContext.builder()
                .sessionUserId(901L)
                .businessOwnerUserId(501L)
                .storeCodes(java.util.Set.of("STR108065-NAE", "STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NAE", 501L, "STR108065-NSA", 501L))
                .build();
    }

    private static BusinessAccessContext contextWith108065AeStore() {
        return BusinessAccessContext.builder()
                .sessionUserId(901L)
                .businessOwnerUserId(501L)
                .storeCodes(java.util.Set.of("STR108065-NAE"))
                .storeOwnerUserIds(Map.of("STR108065-NAE", 501L))
                .build();
    }

    private static ProductPublicDetailScope scope() {
        ProductPublicDetailScope scope = new ProductPublicDetailScope();
        scope.setOwnerUserId(501L);
        scope.setLogicalStoreId(601L);
        scope.setStoreCode("CANMAN");
        scope.setSiteCode("SA");
        return scope;
    }

    private static ProductPublicDetailScope scope(String storeCode, String siteCode) {
        ProductPublicDetailScope scope = new ProductPublicDetailScope();
        scope.setOwnerUserId(501L);
        scope.setLogicalStoreId(601L);
        scope.setStoreCode(storeCode);
        scope.setSiteCode(siteCode);
        return scope;
    }

    private static ProductPublicDetailCandidate candidate() {
        ProductPublicDetailCandidate candidate = new ProductPublicDetailCandidate();
        candidate.setOwnerUserId(501L);
        candidate.setLogicalStoreId(601L);
        candidate.setStoreCode("CANMAN");
        candidate.setSiteCode("SA");
        candidate.setProductMasterId(1001L);
        candidate.setProductVariantId(2001L);
        candidate.setProductSiteOfferId(2501L);
        candidate.setPartnerSku("CANMAN-SKU-1");
        candidate.setSkuParent("ZCANMAN12");
        candidate.setNoonProductCode("ZCANMAN12");
        return candidate;
    }

    private static ProductPublicDetailCandidate candidate(String storeCode, String siteCode) {
        ProductPublicDetailCandidate candidate = candidate();
        candidate.setStoreCode(storeCode);
        candidate.setSiteCode(siteCode);
        return candidate;
    }

    private static ProductPublicDetailSnapshot existingLatestPartialSnapshot() {
        ProductPublicDetailSnapshot snapshot = new ProductPublicDetailSnapshot();
        snapshot.setId(300001L);
        snapshot.setOwnerUserId(501L);
        snapshot.setLogicalStoreId(601L);
        snapshot.setStoreCode("CANMAN");
        snapshot.setSiteCode("SA");
        snapshot.setProductMasterId(1001L);
        snapshot.setProductVariantId(2001L);
        snapshot.setProductSiteOfferId(2501L);
        snapshot.setNoonProductCode("ZCANMAN12");
        snapshot.setSourcePlatform("NOON");
        snapshot.setSyncStatus(ProductPublicDetailSyncStatus.PARTIAL);
        snapshot.setFactDate(LocalDate.parse("2026-06-15"));
        snapshot.setLatest(true);
        return snapshot;
    }

    private static NoonPublicProductDetailResult partialResult() {
        NoonPublicProductDetailResult result = new NoonPublicProductDetailResult();
        result.setStatus(ProductPublicDetailSyncStatus.PARTIAL);
        result.setFailureCode("PARTIAL_DETAIL");
        result.setFailureMessage("search result only");
        result.setNoonProductCode("ZCANMAN12");
        result.setCodeType("Z_CODE");
        result.setTitleEn("Canman public title");
        result.setBrand("Canman");
        result.setPriceAmount(new BigDecimal("19.90"));
        result.setCurrencyCode("SAR");
        result.setProviderHttpStatus(200);
        result.setProviderSourceUrl("https://www.noon.com/_vs/nc/search");
        result.setFetchedAt(LocalDateTime.parse("2026-06-15T08:00:00"));
        return result;
    }

    private static NoonPublicProductDetailResult failedResult() {
        NoonPublicProductDetailResult result = new NoonPublicProductDetailResult();
        result.setStatus(ProductPublicDetailSyncStatus.FAILED);
        result.setFailureCode("RATE_LIMITED");
        result.setFailureMessage("Noon 前台限流");
        result.setNoonProductCode("ZCANMAN12");
        result.setCodeType("Z_CODE");
        result.setProviderHttpStatus(429);
        result.setFetchedAt(LocalDateTime.parse("2026-06-15T08:05:00"));
        return result;
    }

    private static NoonPublicProductDetailResult notFoundResult() {
        NoonPublicProductDetailResult result = new NoonPublicProductDetailResult();
        result.setStatus(ProductPublicDetailSyncStatus.NOT_FOUND);
        result.setFailureCode("PUBLIC_DETAIL_NOT_FOUND");
        result.setFailureMessage("Noon 前台公开详情未找到。");
        result.setNoonProductCode("ZCANMAN12");
        result.setCodeType("Z_CODE");
        result.setProviderHttpStatus(404);
        result.setFetchedAt(LocalDateTime.parse("2026-06-15T08:05:00"));
        return result;
    }

    private static NoonPublicProductDetailResult blockedResult() {
        NoonPublicProductDetailResult result = new NoonPublicProductDetailResult();
        result.setStatus(ProductPublicDetailSyncStatus.FAILED);
        result.setFailureCode("BLOCKED_BY_RISK_CONTROL");
        result.setFailureMessage("Noon 前台公开搜索返回 HTTP 403。");
        result.setNoonProductCode("ZCANMAN12");
        result.setCodeType("Z_CODE");
        result.setProviderHttpStatus(403);
        result.setFetchedAt(LocalDateTime.parse("2026-06-15T08:05:00"));
        return result;
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC);
    }

    private static final class InMemoryRiskBackoffRepository implements NoonRiskBackoffRepository {
        private final Map<String, NoonRiskBackoffHold> holds = new LinkedHashMap<>();

        @Override
        public void upsert(NoonRiskBackoffHold hold) {
            holds.put(hold.getScopeKey(), hold.copy());
        }

        @Override
        public NoonRiskBackoffHold selectActiveHold(String scopeKey, LocalDateTime now) {
            NoonRiskBackoffHold hold = holds.get(scopeKey);
            if (hold == null || hold.getBlockedUntil() == null || hold.getBlockedUntil().isBefore(now)) {
                return null;
            }
            return hold.copy();
        }

        @Override
        public NoonRiskBackoffHold selectLatestHold(String scopeKey) {
            NoonRiskBackoffHold hold = holds.get(scopeKey);
            return hold == null ? null : hold.copy();
        }
    }

    private static final class InMemoryOperationalTaskRepository implements OperationalTaskRepository {
        private final Map<Long, OperationalTask> tasks = new LinkedHashMap<>();
        private long nextId = 150000L;

        @Override
        public Long nextId(String sequenceName, Long initialValue) {
            return ++nextId;
        }

        @Override
        public void insert(OperationalTask task) {
            tasks.put(task.getId(), task.copy());
        }

        @Override
        public OperationalTask selectById(Long taskId) {
            OperationalTask task = tasks.get(taskId);
            return task == null ? null : task.copy();
        }

        @Override
        public OperationalTask selectActiveByNaturalKey(String taskType, String naturalKey) {
            return tasks.values().stream()
                    .filter(task -> taskType.equals(task.getTaskType()))
                    .filter(task -> naturalKey.equals(task.getNaturalKey()))
                    .filter(task -> task.getStatus() != null && task.getStatus().isActive())
                    .findFirst()
                    .map(OperationalTask::copy)
                    .orElse(null);
        }

        @Override
        public OperationalTask selectLatestByNaturalKey(String taskType, String naturalKey) {
            return tasks.values().stream()
                    .filter(task -> taskType.equals(task.getTaskType()))
                    .filter(task -> naturalKey.equals(task.getNaturalKey()))
                    .max(Comparator.comparing(OperationalTask::getId))
                    .map(OperationalTask::copy)
                    .orElse(null);
        }

        @Override
        public void update(OperationalTask task) {
            tasks.put(task.getId(), task.copy());
        }

        @Override
        public List<OperationalTask> listActiveByTaskType(String taskType, int limit) {
            return tasks.values().stream()
                    .filter(task -> taskType.equals(task.getTaskType()))
                    .filter(task -> task.getStatus() != null && task.getStatus().isActive())
                    .limit(limit)
                    .map(OperationalTask::copy)
                    .collect(Collectors.toList());
        }

        @Override
        public List<OperationalTask> listRecent(String taskType, int limit) {
            List<OperationalTask> result = new ArrayList<>();
            tasks.values().stream()
                    .filter(task -> taskType.equals(task.getTaskType()))
                    .sorted(Comparator.comparing(OperationalTask::getId).reversed())
                    .limit(limit)
                    .map(OperationalTask::copy)
                    .forEach(result::add);
            return result;
        }
    }
}
