package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseBatchSummaryMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseBatchSummaryRecords.ShippingBatchRawLineRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseBatchSummaryViews.BatchProductSummaryView;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.ShippingBatchSourceAllocationRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.ProductCandidateView;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfficialWarehouseBatchSummaryServiceTest {

    private static final long BATCH_ID = 901235L;
    private static final String CURRENT_STORE = "STR108065-NSA";
    private static final String OTHER_STORE = "STR69486-NSA";

    @Mock
    private LocalDbOfficialWarehouseService warehouseService;
    @Mock
    private OfficialWarehouseMapper warehouseMapper;
    @Mock
    private OfficialWarehouseBatchSummaryMapper summaryMapper;

    private OfficialWarehouseBatchSummaryService service;

    @BeforeEach
    void setUp() {
        service = new OfficialWarehouseBatchSummaryService(
                warehouseService,
                warehouseMapper,
                summaryMapper
        );
    }

    @Test
    void summarizesWholeBatchCurrentReadinessAndOtherAccessibleStores() {
        Fixture fixture = ticketFixture();
        BusinessAccessContext access = access();
        when(warehouseMapper.listShippingBatchSourceAllocations(
                307L, CURRENT_STORE, "SA", List.of(BATCH_ID), List.of(), List.of()
        )).thenReturn(fixture.currentAllocations);
        when(warehouseMapper.listShippingBatchSourceAllocations(
                307L, OTHER_STORE, "SA", List.of(BATCH_ID), List.of(), List.of()
        )).thenReturn(fixture.otherAllocations);
        when(summaryMapper.listRawLines(307L, List.of(BATCH_ID))).thenReturn(fixture.rawLines);
        when(warehouseService.listProductCandidates(
                access, CURRENT_STORE, "SA", null, List.of("901235"), List.of()
        )).thenReturn(fixture.currentCandidates);

        BatchProductSummaryView result = service.summarize(
                access, CURRENT_STORE, "sa", List.of("901235")
        );

        assertThat(result.totalQuantity).isEqualTo(3016);
        assertThat(result.totalSkuCount).isEqualTo(64);
        assertThat(result.totalLineCount).isEqualTo(71);
        assertThat(result.currentStore.totalQuantity).isEqualTo(2263);
        assertThat(result.currentStore.totalSkuCount).isEqualTo(42);
        assertThat(result.currentStore.bookableQuantity).isEqualTo(2223);
        assertThat(result.currentStore.bookableSkuCount).isEqualTo(41);
        assertThat(result.currentStore.missingDimensionQuantity).isEqualTo(40);
        assertThat(result.currentStore.missingDimensionSkuCount).isEqualTo(1);
        assertThat(result.currentStore.missingDimensionItems)
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.partnerSku).isEqualTo("PAPERSAYSB372");
                    assertThat(item.quantity).isEqualTo(40);
                    assertThat(item.reasons).containsExactly("缺尺寸");
                });
        assertThat(result.otherStores)
                .singleElement()
                .satisfies(store -> {
                    assertThat(store.storeCode).isEqualTo(OTHER_STORE);
                    assertThat(store.storeName).isEqualTo("另一家店");
                    assertThat(store.totalQuantity).isEqualTo(753);
                    assertThat(store.totalSkuCount).isEqualTo(22);
                });
        assertThat(result.unassignedQuantity).isZero();
        assertThat(result.unassignedSkuCount).isZero();
        assertThat(result.attributionWarning).isFalse();
        verify(warehouseMapper, never()).listShippingBatchSourceAllocations(
                eq(307L), eq("SECRET-STORE"), any(), anyList(), anyList(), anyList()
        );
    }

    @Test
    void rejectsBatchThatCannotBeAttributedToCurrentStoreBeforeExposingRawTotals() {
        when(warehouseMapper.listShippingBatchSourceAllocations(
                307L, CURRENT_STORE, "SA", List.of(BATCH_ID), List.of(), List.of()
        )).thenReturn(List.of());
        when(warehouseMapper.listShippingBatchSourceAllocations(
                307L, OTHER_STORE, "SA", List.of(BATCH_ID), List.of(), List.of()
        )).thenReturn(List.of());

        assertThatThrownBy(() -> service.summarize(
                access(), CURRENT_STORE, "SA", List.of("901235")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不属于当前店铺/站点");

        verify(summaryMapper, never()).listRawLines(any(), anyList());
    }

    @Test
    void rejectsStoreWithoutAnAuthoritativeOwnerMapping() {
        BusinessAccessContext access = BusinessAccessContext.builder()
                .sessionUserId(900L)
                .businessOwnerUserId(307L)
                .storeCodes(Set.of(CURRENT_STORE))
                .build();

        assertThatThrownBy(() -> service.summarize(
                access, CURRENT_STORE, "SA", List.of("901235")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("业务老板账号");

        verify(warehouseMapper, never()).listShippingBatchSourceAllocations(
                any(), any(), any(), anyList(), anyList(), anyList()
        );
        verify(summaryMapper, never()).listRawLines(any(), anyList());
    }

    private static BusinessAccessContext access() {
        return BusinessAccessContext.builder()
                .sessionUserId(900L)
                .businessOwnerUserId(307L)
                .storeCodes(Set.of(CURRENT_STORE, OTHER_STORE, "SECRET-STORE"))
                .storeOwnerUserIds(Map.of(
                        CURRENT_STORE, 307L,
                        OTHER_STORE, 307L,
                        "SECRET-STORE", 999L
                ))
                .build();
    }

    private static Fixture ticketFixture() {
        Fixture fixture = new Fixture();
        List<Integer> candidateQuantities = new ArrayList<>();
        candidateQuantities.add(40);
        candidateQuantities.addAll(distribute(2223, 41));
        long lineId = 1L;
        for (int index = 0; index < candidateQuantities.size(); index++) {
            String sku = index == 0 ? "PAPERSAYSB372" : "CURRENT-" + index;
            int quantity = candidateQuantities.get(index);
            ProductCandidateView candidate = candidate(sku, quantity);
            if (index == 0) {
                candidate.missingTags.add("缺尺寸");
            }
            fixture.currentCandidates.add(candidate);
            int firstPart = index < 7 ? quantity / 2 : quantity;
            lineId = addRawAndAllocation(
                    fixture, lineId, sku, firstPart, CURRENT_STORE, "当前店铺"
            );
            if (index < 7) {
                lineId = addRawAndAllocation(
                        fixture, lineId, sku, quantity - firstPart, CURRENT_STORE, "当前店铺"
                );
            }
        }
        List<Integer> otherQuantities = distribute(753, 22);
        for (int index = 0; index < otherQuantities.size(); index++) {
            lineId = addRawAndAllocation(
                    fixture,
                    lineId,
                    "OTHER-" + index,
                    otherQuantities.get(index),
                    OTHER_STORE,
                    "另一家店"
            );
        }
        return fixture;
    }

    private static long addRawAndAllocation(
            Fixture fixture,
            long lineId,
            String sku,
            int quantity,
            String storeCode,
            String storeName
    ) {
        ShippingBatchRawLineRecord raw = new ShippingBatchRawLineRecord();
        raw.batchId = BATCH_ID;
        raw.goodsLineId = lineId;
        raw.psku = sku;
        raw.quantity = quantity;
        fixture.rawLines.add(raw);

        ShippingBatchSourceAllocationRecord allocation = new ShippingBatchSourceAllocationRecord();
        allocation.inTransitBatchId = BATCH_ID;
        allocation.inTransitGoodsLineId = lineId;
        allocation.partnerSku = sku;
        allocation.quantity = quantity;
        allocation.sourceStoreCode = storeCode;
        allocation.sourceStoreName = storeName;
        if (CURRENT_STORE.equals(storeCode)) {
            fixture.currentAllocations.add(allocation);
        } else {
            fixture.otherAllocations.add(allocation);
        }
        return lineId + 1;
    }

    private static ProductCandidateView candidate(String sku, int quantity) {
        ProductCandidateView candidate = new ProductCandidateView();
        candidate.partnerSku = sku;
        candidate.productVariantId = sku;
        candidate.title = sku + " 商品";
        candidate.storeName = "当前店铺";
        candidate.batchAvailableQuantity = quantity;
        return candidate;
    }

    private static List<Integer> distribute(int total, int count) {
        List<Integer> result = new ArrayList<>();
        int base = total / count;
        int remainder = total % count;
        for (int index = 0; index < count; index++) {
            result.add(base + (index < remainder ? 1 : 0));
        }
        return result;
    }

    private static final class Fixture {
        private final List<ShippingBatchRawLineRecord> rawLines = new ArrayList<>();
        private final List<ProductCandidateView> currentCandidates = new ArrayList<>();
        private final List<ShippingBatchSourceAllocationRecord> currentAllocations = new ArrayList<>();
        private final List<ShippingBatchSourceAllocationRecord> otherAllocations = new ArrayList<>();
    }
}
