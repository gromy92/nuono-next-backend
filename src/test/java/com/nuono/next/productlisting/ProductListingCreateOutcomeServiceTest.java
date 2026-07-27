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

class ProductListingCreateOutcomeServiceTest {

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

    private ProductListingTaskView taskView() {
        ProductListingTaskView view = new ProductListingTaskView();
        view.setTaskId(20002L);
        view.setDraftId(10001L);
        view.setOwnerUserId(10002L);
        view.setStoreCode("STR245027-NAE");
        view.setMode("REAL_RUN");
        view.setStatus("written_verify_failed");
        view.setFailureCode("noon_create_outcome_unknown");
        return view;
    }

    private ProductListingTaskRecord uncertainTaskRecord(ObjectMapper objectMapper) throws Exception {
        ProductListingNoonWriteStepResult create = new ProductListingNoonWriteStepResult();
        create.setStepKey("create_product");
        create.setStatus("failed");
        create.setFailureCode("noon_create_outcome_unknown");
        ProductListingNoonWriteResult result = ProductListingNoonWriteResult.failed(
                "noon_uncertain_write",
                "noon_create_outcome_unknown",
                "unknown",
                List.of(create)
        );
        ProductListingTaskRecord record = new ProductListingTaskRecord();
        record.setId(20002L);
        record.setDraftId(10001L);
        record.setOwnerUserId(10002L);
        record.setStoreCode("STR245027-NAE");
        record.setMode("REAL_RUN");
        record.setStatus("written_verify_failed");
        record.setFailureCode("noon_create_outcome_unknown");
        record.setInputSnapshotJson("{\"psku\":\"NN-TEST-PSKU\"}");
        record.setValidationJson("[]");
        record.setConfirmationJson("{\"confirmRealNoonWrite\":true}");
        record.setNoonResultJson(objectMapper.writeValueAsString(result));
        record.setCompletedAt(LocalDateTime.now());
        return record;
    }

    private ProductListingNoonWriteStepResult foundReference() {
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey("resolve_create_reference");
        step.setStatus("succeeded");
        step.setExternalReference("skuParent=ZPARENT;pskuCode=PSKU_CODE_1");
        return step;
    }

    private ProductListingNoonWriteStepResult notFoundReference() {
        ProductListingNoonWriteStepResult step =
                new ProductListingNoonWriteStepResult();
        step.setStepKey("resolve_create_reference");
        step.setStatus("failed");
        step.setFailureCode("noon_create_reference_not_found");
        return step;
    }

    private ProductListingNoonWriteStepResult authenticationRequiredReference() {
        ProductListingNoonWriteStepResult step =
                new ProductListingNoonWriteStepResult();
        step.setStepKey("resolve_create_reference");
        step.setStatus("failed");
        step.setFailureCode("noon_auth_required");
        return step;
    }

    private ProductListingNoonWriteResult withReliableNotFoundSteps(
            ProductListingNoonWriteResult original,
            List<LocalDateTime> checkedAt
    ) {
        List<ProductListingNoonWriteStepResult> steps = new ArrayList<>(
                original.getSteps()
        );
        for (int index = 0; index < checkedAt.size(); index++) {
            ProductListingNoonWriteStepResult step = notFoundReference();
            step.setExternalReference(
                    "lookupAttempt=" + (index + 1)
                            + ";lookupCheckedAt=" + checkedAt.get(index)
            );
            steps.add(step);
        }
        return ProductListingNoonWriteResult.failed(
                original.getFailureCategory(),
                original.getFailureCode(),
                original.getFailureMessage(),
                steps
        );
    }
}
