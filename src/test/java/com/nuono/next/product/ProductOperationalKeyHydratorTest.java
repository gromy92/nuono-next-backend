package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductOperationalKeyHydratorTest {

    @Mock
    private ProductProjectionPersistenceService productProjectionPersistenceService;

    private ProductOperationalKeyHydrator hydrator;

    @BeforeEach
    void setUp() {
        hydrator = new ProductOperationalKeyHydrator(productProjectionPersistenceService);
    }

    @Test
    void shouldNotLookupProjectionWhenBothKeysAlreadyExist() {
        ProductOperationalKeyHydrator.OperationalKeys keys = hydrator.resolveOperationalKeysFromProjection(
                10002L,
                "STR245027-NAE",
                "PAPERSAYSB132",
                "PARTNER-001",
                "PSKU-001"
        );

        assertEquals("PARTNER-001", keys.getPartnerSku());
        assertEquals("PSKU-001", keys.getPskuCode());
        verifyNoInteractions(productProjectionPersistenceService);
    }

    @Test
    void shouldFillOnlyMissingKeysFromReadyListSummary() {
        ProductListSummaryView summary = new ProductListSummaryView();
        summary.setReady(true);
        summary.setPartnerSku("PARTNER-FROM-LIST");
        summary.setPskuCode("PSKU-FROM-LIST");
        when(productProjectionPersistenceService.loadProductListSummary(
                eq(10002L),
                eq("STR245027-NAE"),
                eq("PAPERSAYSB132"),
                anyList()
        )).thenReturn(summary);

        ProductOperationalKeyHydrator.OperationalKeys keys = hydrator.resolveOperationalKeysFromProjection(
                10002L,
                "STR245027-NAE",
                "PAPERSAYSB132",
                "PARTNER-EXISTING",
                null
        );

        assertEquals("PARTNER-EXISTING", keys.getPartnerSku());
        assertEquals("PSKU-FROM-LIST", keys.getPskuCode());
    }

    @Test
    void shouldIgnoreProjectionSummaryUntilItIsReady() {
        ProductListSummaryView summary = new ProductListSummaryView();
        summary.setReady(false);
        summary.setPartnerSku("PARTNER-FROM-LIST");
        summary.setPskuCode("PSKU-FROM-LIST");
        when(productProjectionPersistenceService.loadProductListSummary(
                eq(10002L),
                eq("STR245027-NAE"),
                eq("PAPERSAYSB132"),
                anyList()
        )).thenReturn(summary);

        ProductOperationalKeyHydrator.OperationalKeys keys = hydrator.resolveOperationalKeysFromProjection(
                10002L,
                "STR245027-NAE",
                "PAPERSAYSB132",
                null,
                null
        );

        assertNull(keys.getPartnerSku());
        assertNull(keys.getPskuCode());
    }

    @Test
    void shouldHydrateSnapshotIdentityAndCollectMissingKeys() {
        ProductListSummaryView summary = new ProductListSummaryView();
        summary.setReady(true);
        summary.setPartnerSku("PARTNER-FROM-LIST");
        summary.setPskuCode("PSKU-FROM-LIST");
        when(productProjectionPersistenceService.loadProductListSummary(
                eq(10002L),
                eq("STR245027-NAE"),
                eq("PAPERSAYSB132"),
                anyList()
        )).thenReturn(summary);
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();

        hydrator.hydrateSnapshotOperationalKeys(10002L, "STR245027-NAE", "PAPERSAYSB132", snapshot);

        assertEquals("PARTNER-FROM-LIST", snapshot.getIdentity().get("partnerSku"));
        assertEquals("PSKU-FROM-LIST", snapshot.getIdentity().get("pskuCode"));
        assertEquals(List.of(), hydrator.collectMissingOperationalKeys(
                String.valueOf(snapshot.getIdentity().get("partnerSku")),
                String.valueOf(snapshot.getIdentity().get("pskuCode"))
        ));
        verify(productProjectionPersistenceService).loadProductListSummary(
                eq(10002L),
                eq("STR245027-NAE"),
                eq("PAPERSAYSB132"),
                anyList()
        );
    }
}
