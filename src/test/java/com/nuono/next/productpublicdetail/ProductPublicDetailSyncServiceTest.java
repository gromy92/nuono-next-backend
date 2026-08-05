package com.nuono.next.productpublicdetail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductPublicDetailMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailAdapter;
import com.nuono.next.system.task.OperationalTaskService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
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
    private OperationalTaskService operationalTasks;
    @Mock
    private NoonPublicProductDetailAdapter adapter;

    private ProductPublicDetailSyncService service;

    @BeforeEach
    void setUp() {
        service = new ProductPublicDetailSyncService(
                mapper,
                operationalTasks,
                adapter,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void runtimeFactInsertOwnsTheLatestPointer() {
        ProductPublicDetailSnapshot incoming = snapshot(ProductPublicDetailSyncStatus.PARTIAL);
        when(mapper.selectDailySnapshot(1001L, 2001L, "SA", "NOON", incoming.getFactDate()))
                .thenReturn(null);
        when(mapper.nextSnapshotId()).thenReturn(300001L);

        service.upsertSnapshot(incoming);

        ArgumentCaptor<ProductPublicDetailSnapshot> inserted =
                ArgumentCaptor.forClass(ProductPublicDetailSnapshot.class);
        verify(mapper).insertSnapshot(inserted.capture());
        assertEquals(300001L, inserted.getValue().getId());
        assertEquals(Boolean.TRUE, inserted.getValue().getLatest());
        verify(mapper).clearLatestForProduct(
                1001L, 2001L, "SA", "NOON", 300001L, 901L
        );
        verify(mapper).markLatest(300001L, 901L);
    }

    @Test
    void failedAuditCannotOverwriteAnExistingTrustedLatestFact() {
        ProductPublicDetailSnapshot incoming = snapshot(ProductPublicDetailSyncStatus.FAILED);
        ProductPublicDetailSnapshot existing = snapshot(ProductPublicDetailSyncStatus.PARTIAL);
        existing.setId(300000L);
        existing.setLatest(Boolean.TRUE);
        when(mapper.selectDailySnapshot(1001L, 2001L, "SA", "NOON", incoming.getFactDate()))
                .thenReturn(existing);

        service.upsertSnapshot(incoming);

        verify(mapper, never()).insertSnapshot(any());
        verify(mapper, never()).updateSnapshotPreservingTrustedData(any());
        verify(mapper, never()).markLatest(any(), any());
    }

    @Test
    void retainedStatusQueryUsesTheAccessiblePreferredScope() {
        ProductPublicDetailScope requested = scope("STR108065-NAE", "AE");
        ProductPublicDetailScope preferred = scope("STR108065-NSA", "SA");
        when(mapper.selectActiveScope(501L, "STR108065-NAE", "AE"))
                .thenReturn(requested);
        when(mapper.selectPreferredScope(501L, 601L, 0)).thenReturn(preferred);
        when(mapper.countCandidates(501L, "STR108065-NSA", "SA", 0, true))
                .thenReturn(3);

        ProductPublicDetailStatusView view = service.syncStatus(
                context(Set.of("STR108065-NAE", "STR108065-NSA")),
                "STR108065-NAE",
                "AE"
        );

        assertEquals("STR108065-NSA", view.getStoreCode());
        assertEquals("SA", view.getSiteCode());
        assertEquals(3, view.getCandidateCount());
        verify(mapper).countCandidates(501L, "STR108065-NSA", "SA", 0, true);
    }

    @Test
    void retainedStatusQueryNeverCrossesAnInaccessibleSiblingStore() {
        ProductPublicDetailScope requested = scope("STR108065-NAE", "AE");
        ProductPublicDetailScope inaccessible = scope("STR108065-NSA", "SA");
        when(mapper.selectActiveScope(501L, "STR108065-NAE", "AE"))
                .thenReturn(requested);
        when(mapper.selectPreferredScope(501L, 601L, 0)).thenReturn(inaccessible);

        ProductPublicDetailStatusView view = service.syncStatus(
                context(Set.of("STR108065-NAE")),
                "STR108065-NAE",
                "AE"
        );

        assertEquals("STR108065-NAE", view.getStoreCode());
        assertEquals("AE", view.getSiteCode());
        verify(mapper).countCandidates(501L, "STR108065-NAE", "AE", 0, false);
    }

    private ProductPublicDetailSnapshot snapshot(ProductPublicDetailSyncStatus status) {
        ProductPublicDetailSnapshot value = new ProductPublicDetailSnapshot();
        value.setOwnerUserId(501L);
        value.setStoreCode("CANMAN");
        value.setSiteCode("SA");
        value.setProductMasterId(1001L);
        value.setProductVariantId(2001L);
        value.setSourcePlatform("NOON");
        value.setFactDate(LocalDate.of(2026, 8, 3));
        value.setSyncStatus(status);
        value.setUpdatedBy(901L);
        return value;
    }

    private ProductPublicDetailScope scope(String store, String site) {
        ProductPublicDetailScope value = new ProductPublicDetailScope();
        value.setOwnerUserId(501L);
        value.setLogicalStoreId(601L);
        value.setStoreCode(store);
        value.setSiteCode(site);
        return value;
    }

    private BusinessAccessContext context(Set<String> stores) {
        Map<String, Long> owners = stores.stream().collect(
                java.util.stream.Collectors.toMap((store) -> store, (store) -> 501L)
        );
        return BusinessAccessContext.builder()
                .sessionUserId(901L)
                .businessOwnerUserId(501L)
                .storeCodes(stores)
                .storeOwnerUserIds(owners)
                .build();
    }
}
