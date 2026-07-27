package com.nuono.next.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.permission.access.RequiredBusinessAccess;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@ExtendWith(MockitoExtension.class)
class ProcurementControllerAccessTest {

    @Mock
    private ObjectProvider<LocalDbProcurementService> procurementServiceProvider;

    @Mock
    private LocalDbProcurementService procurementService;

    @Mock
    private BusinessAccessResolver accessResolver;

    private ProcurementController controller;

    @BeforeEach
    void setUp() {
        controller = new ProcurementController(procurementServiceProvider, accessResolver);
    }

    @Test
    void shouldResolveRequestedOwnerBeforeReadingCandidatePool() {
        BusinessAccessContext context = BusinessAccessContext.builder()
                .sessionUserId(801L)
                .businessOwnerUserId(307L)
                .build();
        when(accessResolver.requireOwnerUserId(context, 999L)).thenReturn(307L);
        when(procurementServiceProvider.getIfAvailable()).thenReturn(procurementService);

        controller.candidatePool(999L, "PO-1001", context);

        verify(accessResolver).requireOwnerUserId(context, 999L);
        verify(procurementService).buildCandidatePool(307L, "PO-1001");
    }

    @Test
    void shouldBuildTrustedWriteContextForEveryProcurementWrite() {
        BusinessAccessContext context = BusinessAccessContext.builder()
                .sessionUserId(801L)
                .businessOwnerUserId(307L)
                .roleName("采购")
                .build();
        ProcurementDecisionCommand decision = owner(new ProcurementDecisionCommand());
        ProcurementCandidateReviewCommand review = owner(new ProcurementCandidateReviewCommand());
        ProcurementAutoSelectionCommand autoSelection = owner(new ProcurementAutoSelectionCommand());
        ProcurementImportSearchPageCommand searchImport = owner(new ProcurementImportSearchPageCommand());
        ProcurementManualCandidateBackfillCommand backfill = owner(new ProcurementManualCandidateBackfillCommand());
        when(accessResolver.requireOwnerUserId(context, 999L)).thenReturn(307L);
        when(procurementServiceProvider.getIfAvailable()).thenReturn(procurementService);

        controller.selectCandidate(decision, context);
        controller.reviewCandidate(review, context);
        controller.runAutoSelection(autoSelection, context);
        controller.importSearchPage(searchImport, context);
        controller.backfillCandidates(backfill, context);

        assertEquals(307L, decision.getOwnerUserId());
        assertEquals(307L, review.getOwnerUserId());
        assertEquals(307L, autoSelection.getOwnerUserId());
        assertEquals(307L, searchImport.getOwnerUserId());
        assertEquals(307L, backfill.getOwnerUserId());
        verify(accessResolver, times(5)).requireOwnerUserId(context, 999L);

        ArgumentCaptor<ProcurementCandidatePoolWriteContext> contextCaptor =
                ArgumentCaptor.forClass(ProcurementCandidatePoolWriteContext.class);
        verify(procurementService).selectCandidate(contextCaptor.capture(), same(decision));
        verify(procurementService).saveCandidateReview(contextCaptor.capture(), same(review));
        verify(procurementService).runAutoSelection(contextCaptor.capture(), same(autoSelection));
        verify(procurementService).importSearchPageCandidates(contextCaptor.capture(), same(searchImport));
        verify(procurementService).backfillManualCandidates(contextCaptor.capture(), same(backfill));
        contextCaptor.getAllValues().forEach(this::assertTrustedWriteContext);
    }

    @Test
    void shouldDeclareOneProcurementAccessContextOnEveryEndpoint() {
        List<Method> endpoints = Arrays.stream(ProcurementController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class)
                        || method.isAnnotationPresent(PostMapping.class))
                .collect(Collectors.toList());

        assertEquals(8, endpoints.size());
        endpoints.forEach(this::assertProcurementContext);
    }

    private void assertProcurementContext(Method method) {
        List<Parameter> contextParameters = Arrays.stream(method.getParameters())
                .filter(parameter -> parameter.getType() == BusinessAccessContext.class)
                .collect(Collectors.toList());
        assertEquals(1, contextParameters.size(), method.getName());
        RequiredBusinessAccess access = contextParameters.get(0).getAnnotation(RequiredBusinessAccess.class);
        assertNotNull(access, method.getName());
        assertEquals(BusinessCapability.PROCUREMENT, access.capability(), method.getName());
    }

    private ProcurementDecisionCommand owner(ProcurementDecisionCommand command) {
        command.setOwnerUserId(999L);
        return command;
    }

    private ProcurementCandidateReviewCommand owner(ProcurementCandidateReviewCommand command) {
        command.setOwnerUserId(999L);
        return command;
    }

    private ProcurementAutoSelectionCommand owner(ProcurementAutoSelectionCommand command) {
        command.setOwnerUserId(999L);
        return command;
    }

    private ProcurementImportSearchPageCommand owner(ProcurementImportSearchPageCommand command) {
        command.setOwnerUserId(999L);
        return command;
    }

    private ProcurementManualCandidateBackfillCommand owner(ProcurementManualCandidateBackfillCommand command) {
        command.setOwnerUserId(999L);
        return command;
    }

    private void assertTrustedWriteContext(ProcurementCandidatePoolWriteContext context) {
        assertEquals(307L, context.ownerUserId);
        assertEquals(801L, context.operatorUserId);
        assertEquals("采购", context.operatorRole);
    }
}
