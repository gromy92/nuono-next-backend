package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductListingCreateOutcomeVerificationTest extends ProductListingCreateOutcomeTestSupport {

    @Test
    void foundCreateReferenceIsPersistedWithoutExecutingContinuation() throws Exception {
        ProductListingMapper mapper = mock(ProductListingMapper.class);
        ProductListingService listingService = mock(ProductListingService.class);
        ProductListingNoonWriteAdapter adapter = mock(ProductListingNoonWriteAdapter.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ProductListingCreateOutcomeService service = new ProductListingCreateOutcomeService(
                mapper, listingService, adapter, objectMapper
        );
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE"
        );
        ProductListingTaskRecord record = uncertainTaskRecord(objectMapper);
        when(listingService.loadTask(context, 20002L)).thenReturn(taskView());
        when(mapper.selectTaskById(20002L, 10002L)).thenReturn(record);
        when(adapter.resolveCreateReference(any())).thenReturn(foundReference());
        when(mapper.persistRecoveredCreateReference(
                eq(20002L),
                eq(10002L),
                eq(record.getNoonResultJson()),
                any()
        )).thenReturn(1);

        ProductListingCreateOutcomeVerificationView view = service.verify(context, 20002L);

        assertEquals(20002L, view.getTaskId());
        assertEquals("NN-TEST-PSKU", view.getPartnerSku());
        assertEquals("found", view.getStatus());
        assertEquals("ZPARENT", view.getSkuParent());
        assertEquals("PSKU_CODE_1", view.getPskuCode());
        verify(adapter).resolveCreateReference(any());
        verify(adapter, never()).continueAfterCreate(any(), any(), any());
        verify(mapper).persistRecoveredCreateReference(
                eq(20002L),
                eq(10002L),
                eq(record.getNoonResultJson()),
                any()
        );
    }

    @Test
    void latestTaskThatIsNoLongerVerifiableFailsClosedBeforeNoonLookup() throws Exception {
        ProductListingMapper mapper = mock(ProductListingMapper.class);
        ProductListingService listingService = mock(ProductListingService.class);
        ProductListingNoonWriteAdapter adapter = mock(ProductListingNoonWriteAdapter.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ProductListingCreateOutcomeService service = new ProductListingCreateOutcomeService(
                mapper, listingService, adapter, objectMapper
        );
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE"
        );
        ProductListingTaskRecord latest = uncertainTaskRecord(objectMapper);
        latest.setStatus("succeeded");
        latest.setFailureCode(null);
        when(listingService.loadTask(context, 20002L)).thenReturn(taskView());
        when(mapper.selectTaskById(20002L, 10002L)).thenReturn(latest);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.verify(context, 20002L)
        );

        verify(adapter, never()).resolveCreateReference(any());
        verify(mapper, never()).persistRecoveredCreateReference(any(), any(), any(), any());
    }

    @Test
    void latestTaskIdentityMismatchFailsClosedBeforeNoonLookup() throws Exception {
        ProductListingMapper mapper = mock(ProductListingMapper.class);
        ProductListingService listingService = mock(ProductListingService.class);
        ProductListingNoonWriteAdapter adapter = mock(ProductListingNoonWriteAdapter.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ProductListingCreateOutcomeService service = new ProductListingCreateOutcomeService(
                mapper, listingService, adapter, objectMapper
        );
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE"
        );
        ProductListingTaskRecord latest = uncertainTaskRecord(objectMapper);
        latest.setOwnerUserId(10003L);
        latest.setDraftId(10009L);
        latest.setStoreCode("STR-OTHER");
        when(listingService.loadTask(context, 20002L)).thenReturn(taskView());
        when(mapper.selectTaskById(20002L, 10002L)).thenReturn(latest);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.verify(context, 20002L)
        );

        verify(adapter, never()).resolveCreateReference(any());
        verify(mapper, never()).persistRecoveredCreateReference(any(), any(), any(), any());
    }

    @Test
    void repeatedReliableNotFoundChecksArePersistedBeforeSafeExitIsOffered()
            throws Exception {
        ProductListingMapper mapper = mock(ProductListingMapper.class);
        ProductListingService listingService = mock(ProductListingService.class);
        ProductListingNoonWriteAdapter adapter =
                mock(ProductListingNoonWriteAdapter.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ProductListingCreateOutcomeService service =
                new ProductListingCreateOutcomeService(
                        mapper,
                        listingService,
                        adapter,
                        objectMapper
                );
        BusinessAccessContext context =
                ProductListingTestFixtures.businessContext(
                        10002L,
                        90001L,
                        "STR245027-NAE"
                );
        ProductListingTaskRecord record = uncertainTaskRecord(objectMapper);
        LocalDateTime now = LocalDateTime.now();
        record.setCompletedAt(now.minusMinutes(4));
        record.setNoonResultJson(objectMapper.writeValueAsString(
                withReliableNotFoundSteps(
                        objectMapper.readValue(
                                record.getNoonResultJson(),
                                ProductListingNoonWriteResult.class
                        ),
                        List.of(
                                now.minusMinutes(3),
                                now.minusMinutes(1)
                        )
                )
        ));
        when(listingService.loadTask(context, 20002L)).thenReturn(taskView());
        when(mapper.selectTaskById(20002L, 10002L)).thenReturn(record);
        when(adapter.resolveCreateReference(any())).thenReturn(notFoundReference());
        when(mapper.persistRecoveredCreateReference(
                eq(20002L),
                eq(10002L),
                eq(record.getNoonResultJson()),
                any()
        )).thenReturn(1);

        ProductListingCreateOutcomeVerificationView view =
                service.verify(context, 20002L);

        assertEquals("not_found", view.getStatus());
        assertEquals(3, view.getLookupAttemptCount());
        assertEquals(Boolean.TRUE, view.getCanConfirmNotCreated());
        verify(mapper).persistRecoveredCreateReference(
                eq(20002L),
                eq(10002L),
                any(),
                any()
        );
    }

}
