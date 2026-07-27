package com.nuono.next.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProcurementMapper;
import com.nuono.next.procurement.ProcurementAutoSelectionEngine.AutoSelectionResult;
import com.nuono.next.procurement.ProcurementAutoSelectionEngine.GeneratedCandidate;
import com.nuono.next.procurement.ProcurementCandidatePoolView.CandidateView;
import com.nuono.next.procurement.ProcurementCandidatePoolView.DemandItemView;
import com.nuono.next.procurement.ProcurementCandidatePoolView.OrderView;
import com.nuono.next.procurement.ProcurementManualCandidateBackfillCommand.ManualCandidateInput;
import com.nuono.next.system.CoreTableInspection;
import com.nuono.next.system.LocalDbBootstrapStatusService;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.Invocation;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalDbProcurementServiceAuditTest {

    private static final Long OWNER_USER_ID = 307L;
    private static final Long OPERATOR_USER_ID = 801L;
    private static final Long DEMAND_ITEM_ID = 41L;
    private static final Long CANDIDATE_ID = 51L;

    @Mock private ProcurementMapper mapper;
    @Mock private LocalDbBootstrapStatusService bootstrapStatusService;
    @Mock private ProcurementStructuredFieldParser structuredFieldParser;
    @Mock private ProcurementDecisionSupportAdvisor decisionSupportAdvisor;
    @Mock private ProcurementCandidateGroupingAdvisor groupingAdvisor;
    @Mock private ProcurementInquiryPreparationAdvisor inquiryPreparationAdvisor;
    @Mock private ProcurementAutoSelectionEngine autoSelectionEngine;
    @Mock private Procurement1688SearchPageExtractor searchPageExtractor;

    private LocalDbProcurementService service;

    @BeforeEach
    void setUp() {
        service = new LocalDbProcurementService(
                mapper,
                bootstrapStatusService,
                structuredFieldParser,
                decisionSupportAdvisor,
                groupingAdvisor,
                inquiryPreparationAdvisor,
                autoSelectionEngine,
                searchPageExtractor
        );
    }

    @Test
    void shouldUseOwnerForCandidateScopeAndOperatorForSelectionAudit() {
        ProcurementDecisionCommand command = new ProcurementDecisionCommand();
        command.setOwnerUserId(999L);
        command.setDemandItemId(DEMAND_ITEM_ID);
        command.setCandidateId(CANDIDATE_ID);
        when(mapper.countOwnedCandidate(OWNER_USER_ID, DEMAND_ITEM_ID, CANDIDATE_ID)).thenReturn(1);
        OrderView order = new OrderView();
        order.setId(61L);
        stubReady();
        when(mapper.selectLatestOrder(OWNER_USER_ID, null)).thenReturn(order);

        service.selectCandidate(writeContext(), command);

        verify(mapper).countOwnedCandidate(OWNER_USER_ID, DEMAND_ITEM_ID, CANDIDATE_ID);
        verify(mapper).clearSelectedCandidates(DEMAND_ITEM_ID, OPERATOR_USER_ID);
        verify(mapper).selectCandidate(DEMAND_ITEM_ID, CANDIDATE_ID, OPERATOR_USER_ID);
        verify(mapper).markDemandItemDecided(DEMAND_ITEM_ID, CANDIDATE_ID, OPERATOR_USER_ID);
        verify(mapper).syncOrderDecisionSummary(61L, OPERATOR_USER_ID);
    }

    @Test
    void shouldUseOwnerForCandidateScopeAndOperatorForReviewAudit() {
        ProcurementCandidateReviewCommand command = new ProcurementCandidateReviewCommand();
        command.setOwnerUserId(999L);
        command.setDemandItemId(DEMAND_ITEM_ID);
        command.setCandidateId(CANDIDATE_ID);
        command.setNextAction("hold");
        when(mapper.countOwnedCandidate(OWNER_USER_ID, DEMAND_ITEM_ID, CANDIDATE_ID)).thenReturn(1);
        when(mapper.updateCandidateReview(
                DEMAND_ITEM_ID, CANDIDATE_ID, null, null, "HOLD", OPERATOR_USER_ID
        )).thenReturn(1);
        stubReady();

        service.saveCandidateReview(writeContext(), command);

        verify(mapper).countOwnedCandidate(OWNER_USER_ID, DEMAND_ITEM_ID, CANDIDATE_ID);
        verify(mapper).updateCandidateReview(
                DEMAND_ITEM_ID, CANDIDATE_ID, null, null, "HOLD", OPERATOR_USER_ID
        );
    }

    @Test
    void shouldUseOperatorForImportedCandidateAudit() {
        ProcurementImportSearchPageCommand command = new ProcurementImportSearchPageCommand();
        command.setOwnerUserId(999L);
        command.setDemandItemId(DEMAND_ITEM_ID);
        command.setHtml("<html>candidate</html>");
        ProcurementSearchPagePreviewView preview = new ProcurementSearchPagePreviewView();
        preview.setCandidates(List.of(new CandidateView()));
        when(searchPageExtractor.preview(command.getHtml(), null)).thenReturn(preview);
        when(autoSelectionEngine.evaluateExtractedCandidate(
                org.mockito.ArgumentMatchers.any(DemandItemView.class),
                org.mockito.ArgumentMatchers.any(CandidateView.class),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(generatedCandidate());
        stubGeneratedWrite();

        service.importSearchPageCandidates(writeContext(), command);

        assertGeneratedWriteAudit(true);
    }

    @Test
    void shouldUseOperatorForManualCandidateBackfillAudit() {
        ProcurementManualCandidateBackfillCommand command = new ProcurementManualCandidateBackfillCommand();
        command.setOwnerUserId(999L);
        command.setDemandItemId(DEMAND_ITEM_ID);
        ManualCandidateInput input = new ManualCandidateInput();
        input.setCandidateUrl("https://detail.1688.com/offer/1.html");
        input.setTitle("candidate");
        command.setCandidates(List.of(input));
        when(mapper.selectMaxRankNoByDemandItem(DEMAND_ITEM_ID)).thenReturn(0);
        when(autoSelectionEngine.evaluateExtractedCandidate(
                org.mockito.ArgumentMatchers.any(DemandItemView.class),
                org.mockito.ArgumentMatchers.any(CandidateView.class),
                org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(generatedCandidate());
        stubGeneratedWrite();

        service.backfillManualCandidates(writeContext(), command);

        assertGeneratedWriteAudit(false);
    }

    @Test
    void shouldUseOperatorForAutomaticSelectionAudit() {
        ProcurementAutoSelectionCommand command = new ProcurementAutoSelectionCommand();
        command.setOwnerUserId(999L);
        command.setDemandItemId(DEMAND_ITEM_ID);
        GeneratedCandidate generatedCandidate = generatedCandidate();
        when(autoSelectionEngine.generate(org.mockito.ArgumentMatchers.any(DemandItemView.class)))
                .thenReturn(new AutoSelectionResult(
                        "SUCCESS", 100, "KEYWORD", 0, "TITLE_KEYWORD_FALLBACK",
                        1, 1, "done", List.of(generatedCandidate)
                ));
        stubGeneratedWrite();

        service.runAutoSelection(writeContext(), command);

        assertGeneratedWriteAudit(true);
    }

    private void stubGeneratedWrite() {
        when(mapper.selectOwnedDemandItem(OWNER_USER_ID, DEMAND_ITEM_ID)).thenReturn(new DemandItemView());
        when(mapper.nextTaskId()).thenReturn(71L);
        when(mapper.nextCandidateId()).thenReturn(81L);
        stubReady();
    }

    private void stubReady() {
        when(bootstrapStatusService.inspect()).thenReturn(
                new CoreTableInspection("nuono_test", List.of(), List.of(), List.of())
        );
    }

    private ProcurementCandidatePoolWriteContext writeContext() {
        return new ProcurementCandidatePoolWriteContext(OWNER_USER_ID, OPERATOR_USER_ID, "PURCHASE");
    }

    private GeneratedCandidate generatedCandidate() {
        GeneratedCandidate candidate = new GeneratedCandidate();
        candidate.setRankNo(1);
        candidate.setLevel("recommended");
        return candidate;
    }

    private void assertGeneratedWriteAudit(boolean replacesExistingCandidates) {
        verify(mapper).selectOwnedDemandItem(OWNER_USER_ID, DEMAND_ITEM_ID);
        if (replacesExistingCandidates) {
            verify(mapper).archiveCandidatesByDemandItem(DEMAND_ITEM_ID, OPERATOR_USER_ID);
            verify(mapper).markDemandItemScreening(DEMAND_ITEM_ID, OPERATOR_USER_ID);
        }
        verify(mapper).updateDemandItemStatus(
                DEMAND_ITEM_ID,
                "REVIEWING",
                OPERATOR_USER_ID
        );
        assertAuditPair("insertMatchTask");
        assertAuditPair("insertCandidate");
    }

    private void assertAuditPair(String methodName) {
        List<Invocation> invocations = mockingDetails(mapper).getInvocations().stream()
                .filter(invocation -> methodName.equals(invocation.getMethod().getName()))
                .collect(Collectors.toList());
        assertFalse(invocations.isEmpty(), methodName);
        for (Invocation invocation : invocations) {
            Object[] arguments = invocation.getArguments();
            assertEquals(OPERATOR_USER_ID, arguments[arguments.length - 2], methodName + " createdBy");
            assertEquals(OPERATOR_USER_ID, arguments[arguments.length - 1], methodName + " updatedBy");
        }
    }
}
