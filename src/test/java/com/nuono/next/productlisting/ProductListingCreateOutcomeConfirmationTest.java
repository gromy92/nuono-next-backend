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

class ProductListingCreateOutcomeConfirmationTest extends ProductListingCreateOutcomeTestSupport {

    @Test
    void lookupAuthenticationFailureTransitionsUnknownTaskToReauthentication()
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
        when(listingService.loadTask(context, 20002L)).thenReturn(taskView());
        when(mapper.selectTaskById(20002L, 10002L)).thenReturn(record);
        when(adapter.resolveCreateReference(any()))
                .thenReturn(authenticationRequiredReference());
        when(mapper.markCreateOutcomeLookupAuthenticationRequired(
                eq(20002L),
                eq(10002L),
                eq(record.getNoonResultJson()),
                any()
        )).thenReturn(1);

        ProductListingCreateOutcomeVerificationView view =
                service.verify(context, 20002L);

        assertEquals("reauthentication_required", view.getStatus());
        assertEquals("noon_auth_required", view.getFailureCode());
        verify(mapper).markCreateOutcomeLookupAuthenticationRequired(
                eq(20002L),
                eq(10002L),
                eq(record.getNoonResultJson()),
                any()
        );
        verify(mapper, never()).persistRecoveredCreateReference(
                any(), any(), any(), any());
    }

    @Test
    void rapidNotFoundChecksDoNotUnlockConfirmNotCreated()
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
        record.setCompletedAt(now.minusMinutes(5));
        record.setNoonResultJson(objectMapper.writeValueAsString(
                withReliableNotFoundSteps(
                        objectMapper.readValue(
                                record.getNoonResultJson(),
                                ProductListingNoonWriteResult.class
                        ),
                        List.of(
                                now.minusSeconds(20),
                                now.minusSeconds(10),
                                now.minusSeconds(5)
                        )
                )
        ));
        when(listingService.loadTask(context, 20002L)).thenReturn(taskView());
        when(mapper.selectTaskByIdForUpdate(20002L, 10002L))
                .thenReturn(record);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.confirmNotCreated(context, 20002L)
        );

        verify(mapper, never()).updateTaskResult(any());
        verify(mapper, never()).markValidatedDryRunSuperseded(any(), any());
    }

    @Test
    void confirmedNotCreatedClosesAttemptAndSupersedesSourceDryRun()
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
        record.setSourceTaskId(20001L);
        LocalDateTime now = LocalDateTime.now();
        record.setCompletedAt(now.minusMinutes(5));
        record.setNoonResultJson(objectMapper.writeValueAsString(
                withReliableNotFoundSteps(
                        objectMapper.readValue(
                                record.getNoonResultJson(),
                                ProductListingNoonWriteResult.class
                        ),
                        List.of(
                                now.minusMinutes(4),
                                now.minusMinutes(2),
                                now.minusSeconds(1)
                        )
                )
        ));
        when(listingService.loadTask(context, 20002L)).thenReturn(taskView());
        when(mapper.selectTaskByIdForUpdate(20002L, 10002L))
                .thenReturn(record);
        when(mapper.updateTaskResult(record)).thenReturn(1);
        when(mapper.markValidatedDryRunSuperseded(20001L, 10002L))
                .thenReturn(1);

        Long draftId = service.confirmNotCreated(context, 20002L);

        assertEquals(10001L, draftId);
        assertEquals("failed", record.getStatus());
        assertEquals(
                "noon_create_not_found_confirmed",
                record.getFailureCode()
        );
        verify(mapper).updateTaskResult(record);
        verify(mapper).markValidatedDryRunSuperseded(20001L, 10002L);
    }

}
