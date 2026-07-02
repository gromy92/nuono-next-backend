package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.product.LocalDbProductMasterService;
import com.nuono.next.product.ProductListSummaryView;
import com.nuono.next.product.ProductMasterFetchCommand;
import com.nuono.next.product.ProductMasterSnapshotView;
import com.nuono.next.product.ProductProjectionPersistenceService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocalDbNoonProductDetailBaselineSyncerTest {

    @Test
    void shouldProcessOnlyMissingProductsWithinBatchAndReportRemainingCursor() {
        ProductProjectionPersistenceService projectionService = mock(ProductProjectionPersistenceService.class);
        LocalDbProductMasterService productMasterService = mock(LocalDbProductMasterService.class);
        when(projectionService.loadProductListSummaries(eq(307L), eq("STORE-AE"), anyList()))
                .thenReturn(List.of(
                        summary("SKU-1", "ready"),
                        summary("SKU-2", "missing"),
                        summary("SKU-3", "missing"),
                        summary("SKU-4", "missing"),
                        summary("SKU-5", "missing")
                ));
        List<String> fetchedSkuParents = new ArrayList<>();
        when(productMasterService.fetchSnapshot(any(ProductMasterFetchCommand.class))).thenAnswer((invocation) -> {
            ProductMasterFetchCommand command = invocation.getArgument(0);
            fetchedSkuParents.add(command.getSkuParent());
            return readySnapshot();
        });

        LocalDbNoonProductDetailBaselineSyncer syncer =
                new LocalDbNoonProductDetailBaselineSyncer(projectionService, productMasterService);

        NoonProductDetailBaselineSyncResult result = syncer.sync(request(2, null));

        assertEquals(List.of("SKU-2", "SKU-3"), fetchedSkuParents);
        assertEquals(5, result.getTotalProductCount());
        assertEquals(2, result.getAttemptedCount());
        assertEquals(2, result.getSucceededCount());
        assertEquals(1, result.getSkippedReadyCount());
        assertEquals(3, result.getCompletedCount());
        assertEquals(2, result.getRemainingCount());
        assertEquals("SKU-4", result.getNextResumePosition());
        assertTrue(result.isPartial());
    }

    private NoonProductDetailBaselineSyncRequest request(int maxDetailFetches, String resumePosition) {
        NoonProductDetailBaselineSyncRequest request = new NoonProductDetailBaselineSyncRequest();
        request.setOwnerUserId(307L);
        request.setStoreCode("STORE-AE");
        request.setSiteCode("AE");
        request.setMaxDetailFetches(maxDetailFetches);
        request.setResumePosition(resumePosition);
        return request;
    }

    private ProductListSummaryView summary(String skuParent, String detailBaselineStatus) {
        ProductListSummaryView summary = new ProductListSummaryView();
        summary.setSkuParent(skuParent);
        summary.setPartnerSku("PARTNER-" + skuParent);
        summary.setPskuCode("PSKU-" + skuParent);
        summary.setDetailBaselineStatus(detailBaselineStatus);
        return summary;
    }

    private ProductMasterSnapshotView readySnapshot() {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setReady(true);
        return snapshot;
    }
}
