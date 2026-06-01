package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.ai.AiStructuredTextCommand;
import com.nuono.next.ai.AiStructuredTextResult;
import com.nuono.next.infrastructure.mapper.Ali1688CollectionMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class Ali1688CandidateAiAssessmentServiceTest {

    @Mock
    private Ali1688CollectionMapper mapper;

    @Mock
    private ObjectProvider<AiCapabilityService> aiCapabilityServiceProvider;

    @Mock
    private AiCapabilityService aiCapabilityService;

    @Mock
    private ObjectProvider<Ali1688PluginExecutionAssignmentService> assignmentServiceProvider;

    @Mock
    private Ali1688PluginExecutionAssignmentService assignmentService;

    @Test
    void processPendingAssessmentCallsAiBaseAndWritesFinalScore() {
        Ali1688CandidateAiAssessmentService service = service();
        Ali1688CollectionRecords.AiAssessmentRecord assessment = assessment();
        Ali1688CollectionRecords.CandidateRecord candidate = candidate();
        Ali1688CollectionRecords.TaskRecord task = task();
        AiStructuredTextResult aiResult = AiStructuredTextResult.success();
        aiResult.setModel("gpt-test");
        aiResult.setParsedJson(Map.of(
                "matchScore", 31,
                "specScore", 17,
                "riskLevel", "low",
                "reasons", List.of("主图和标题表达同类商品"),
                "warnings", List.of()
        ));

        when(mapper.listClaimableAiAssessmentIds(3, 10, 3)).thenReturn(List.of(assessment.id));
        when(mapper.claimAiAssessment(eq(assessment.id), anyString(), eq(3), eq(10))).thenReturn(1);
        when(mapper.selectAiAssessmentById(assessment.id)).thenReturn(assessment);
        when(mapper.selectCandidateById(candidate.id)).thenReturn(candidate);
        when(mapper.selectTaskById(task.id)).thenReturn(task);
        when(mapper.listCandidatesByTask(task.id)).thenReturn(List.of(finalCandidate(candidate.id, 93, 1), ruleCandidate(88002L, 44, 2)));
        when(aiCapabilityServiceProvider.getIfAvailable()).thenReturn(aiCapabilityService);
        when(aiCapabilityService.createStructuredText(org.mockito.ArgumentMatchers.any(AiStructuredTextCommand.class))).thenReturn(aiResult);

        int processed = service.processPendingAssessmentsOnce();

        assertEquals(1, processed);
        ArgumentCaptor<AiStructuredTextCommand> commandCaptor = ArgumentCaptor.forClass(AiStructuredTextCommand.class);
        verify(aiCapabilityService).createStructuredText(commandCaptor.capture());
        AiStructuredTextCommand command = commandCaptor.getValue();
        assertEquals("ALI1688_CANDIDATE_SCORE", command.getFeatureCode());
        assertEquals("CANDIDATE_MATCH_SPEC_ASSESSMENT", command.getOperationCode());
        assertEquals("nuono_ali1688_candidate_score_v1", command.getSchemaName());
        JsonNode schema = new ObjectMapper().valueToTree(command.getSchema());
        assertEquals("string", schema.path("properties").path("reasons").path("items").path("type").asText());
        assertEquals("string", schema.path("properties").path("warnings").path("items").path("type").asText());
        assertTrue(command.getPrompt().contains("仿真花束"));

        verify(mapper).markAiAssessmentSuccess(
                eq(assessment.id),
                anyString(),
                eq("gpt-test"),
                contains("matchScore"),
                eq(31),
                eq(17),
                eq("low"),
                eq(99L)
        );
        verify(mapper).updateCandidateAiScore(
                eq(candidate.id),
                eq(31),
                eq(17),
                eq(93),
                contains("\"aiPending\":false"),
                eq(99L)
        );
        verify(mapper, never()).markCandidateAiAssessmentFailed(eq(candidate.id), eq(99L));
        verify(mapper).clearSelectedRanks(task.id, candidate.updatedBy);
        verify(mapper).updateSelectedRank(task.id, candidate.id, 1, candidate.updatedBy);
        verify(mapper).updateSelectedRank(task.id, 88002L, 2, candidate.updatedBy);
    }

    @Test
    void processPendingAssessmentIssuesPricePreviewAssignmentAfterAiGateChanges() {
        Ali1688CandidateAiAssessmentService service = service();
        Ali1688CollectionRecords.AiAssessmentRecord assessment = assessment();
        Ali1688CollectionRecords.CandidateRecord candidate = candidate();
        Ali1688CollectionRecords.TaskRecord task = task();
        AiStructuredTextResult aiResult = AiStructuredTextResult.success();
        aiResult.setModel("gpt-test");
        aiResult.setParsedJson(Map.of(
                "matchScore", 31,
                "specScore", 17,
                "riskLevel", "low",
                "reasons", List.of("同款"),
                "warnings", List.of()
        ));

        when(mapper.listClaimableAiAssessmentIds(3, 10, 3)).thenReturn(List.of(assessment.id));
        when(mapper.claimAiAssessment(eq(assessment.id), anyString(), eq(3), eq(10))).thenReturn(1);
        when(mapper.selectAiAssessmentById(assessment.id)).thenReturn(assessment);
        when(mapper.selectCandidateById(candidate.id)).thenReturn(candidate);
        when(mapper.selectTaskById(task.id)).thenReturn(task);
        when(mapper.listCandidatesByTask(task.id)).thenReturn(List.of(finalCandidate(candidate.id, 93, 1)));
        when(aiCapabilityServiceProvider.getIfAvailable()).thenReturn(aiCapabilityService);
        when(aiCapabilityService.createStructuredText(org.mockito.ArgumentMatchers.any(AiStructuredTextCommand.class))).thenReturn(aiResult);
        when(assignmentServiceProvider.getIfAvailable()).thenReturn(assignmentService);

        int processed = service.processPendingAssessmentsOnce();

        assertEquals(1, processed);
        verify(assignmentService).issuePricePreviewAssignmentsForEligibleTask(task.id, candidate.updatedBy);
    }

    @Test
    void createPendingAssessmentIncludesLatestDetailEvidenceWithoutDroppingSourceContext() throws Exception {
        Ali1688CandidateAiAssessmentService service = service();
        Ali1688CollectionRecords.TaskRecord task = task();
        Ali1688CollectionRecords.CandidateRecord candidate = candidate();
        candidate.offerId = "745612345001";
        candidate.candidateUrl = "https://detail.1688.com/offer/745612345001.html";
        candidate.title = "Razr Fold 手机保护膜";
        candidate.supplierName = "深圳屏幕配件工厂";
        candidate.mainImageUrl = "https://images.example.com/list-main.jpg";
        candidate.imageUrlsJson = "[\"https://images.example.com/list-main.jpg\"]";

        when(mapper.nextAiAssessmentId()).thenReturn(89002L);
        when(mapper.selectLatestDetailEnrichmentSnapshotByCandidateId(candidate.id)).thenReturn(detailSnapshot(candidate.id));

        service.createPendingAssessments(task, List.of(candidate));

        ArgumentCaptor<Ali1688CollectionRecords.AiAssessmentRecord> assessmentCaptor =
                ArgumentCaptor.forClass(Ali1688CollectionRecords.AiAssessmentRecord.class);
        verify(mapper).insertAiAssessment(assessmentCaptor.capture());
        JsonNode snapshot = new ObjectMapper().readTree(assessmentCaptor.getValue().inputSnapshotJson);
        assertEquals("仿真花束", snapshot.path("sourceTitleCn").asText());
        assertEquals("https://images.example.com/source.jpg", snapshot.path("sourceImageUrl").asText());
        assertEquals("Razr Fold 手机保护膜", snapshot.path("title").asText());
        JsonNode detail = snapshot.path("detailEvidence");
        assertFalse(detail.isMissingNode());
        assertEquals("Razr Fold 手机壳保护套详情", detail.path("title").asText());
        assertEquals("DETAIL_ENRICHMENT", detail.path("source").asText());
        assertEquals("透明", detail.path("skuOptions").get(0).path("values").get(0).asText());
        assertEquals("https://images.example.com/detail-1.jpg", detail.path("detailImageUrls").get(0).asText());
    }

    @Test
    void processPendingAssessmentSkipsStaleInputWhenLatestDetailEvidenceChanged() {
        Ali1688CandidateAiAssessmentService service = service();
        Ali1688CollectionRecords.AiAssessmentRecord assessment = assessment();
        assessment.inputSnapshotJson = "{\"candidateId\":88001,\"title\":\"旧详情输入\"}";
        Ali1688CollectionRecords.CandidateRecord candidate = candidate();
        Ali1688CollectionRecords.TaskRecord task = task();

        when(mapper.listClaimableAiAssessmentIds(3, 10, 3)).thenReturn(List.of(assessment.id));
        when(mapper.claimAiAssessment(eq(assessment.id), anyString(), eq(3), eq(10))).thenReturn(1);
        when(mapper.selectAiAssessmentById(assessment.id)).thenReturn(assessment);
        when(mapper.selectCandidateById(candidate.id)).thenReturn(candidate);
        when(mapper.selectTaskById(task.id)).thenReturn(task);
        when(mapper.selectLatestDetailEnrichmentSnapshotByCandidateId(candidate.id)).thenReturn(detailSnapshot(candidate.id));

        int processed = service.processPendingAssessmentsOnce();

        assertEquals(1, processed);
        verify(mapper).markAiAssessmentFailed(
                eq(assessment.id),
                eq("stale_ai_input"),
                contains("详情证据已更新"),
                eq(assessment.updatedBy)
        );
        verify(aiCapabilityServiceProvider, never()).getIfAvailable();
        verify(mapper, never()).updateCandidateAiScore(anyLong(), anyInt(), anyInt(), anyInt(), anyString(), anyLong());
        verify(mapper, never()).clearSelectedRanks(anyLong(), anyLong());
    }

    private Ali1688CandidateAiAssessmentService service() {
        Ali1688CandidateAiAssessmentService service = new Ali1688CandidateAiAssessmentService(
                mapper,
                aiCapabilityServiceProvider,
                assignmentServiceProvider,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(service, "schedulerMaxItems", 3);
        ReflectionTestUtils.setField(service, "lockTimeoutMinutes", 10);
        ReflectionTestUtils.setField(service, "maxAttempts", 3);
        return service;
    }

    private Ali1688CollectionRecords.AiAssessmentRecord assessment() {
        Ali1688CollectionRecords.AiAssessmentRecord assessment = new Ali1688CollectionRecords.AiAssessmentRecord();
        assessment.id = 89001L;
        assessment.taskId = 87001L;
        assessment.candidateId = 88001L;
        assessment.inputSnapshotJson = "{\"sourceTitleCn\":\"仿真花束\",\"title\":\"仿真罂粟花束\"}";
        assessment.updatedBy = 99L;
        return assessment;
    }

    private Ali1688CollectionRecords.CandidateRecord candidate() {
        Ali1688CollectionRecords.CandidateRecord candidate = new Ali1688CollectionRecords.CandidateRecord();
        candidate.id = 88001L;
        candidate.taskId = 87001L;
        candidate.ruleScore = 45;
        candidate.priceScore = 15;
        candidate.moqScore = 10;
        candidate.supplierScore = 12;
        candidate.deliveryScore = 8;
        candidate.updatedBy = 99L;
        return candidate;
    }

    private Ali1688CollectionRecords.CandidateRecord finalCandidate(Long id, Integer totalScore, Integer rankNo) {
        Ali1688CollectionRecords.CandidateRecord candidate = ruleCandidate(id, 0, rankNo);
        candidate.scoreStatus = "final";
        candidate.totalScore = totalScore;
        return candidate;
    }

    private Ali1688CollectionRecords.CandidateRecord ruleCandidate(Long id, Integer ruleScore, Integer rankNo) {
        Ali1688CollectionRecords.CandidateRecord candidate = new Ali1688CollectionRecords.CandidateRecord();
        candidate.id = id;
        candidate.taskId = 87001L;
        candidate.ruleScore = ruleScore;
        candidate.rankNo = rankNo;
        return candidate;
    }

    private Ali1688CollectionRecords.TaskRecord task() {
        Ali1688CollectionRecords.TaskRecord task = new Ali1688CollectionRecords.TaskRecord();
        task.id = 87001L;
        task.sourceCollectionId = 86001L;
        task.sourceTitleCn = "仿真花束";
        task.sourceImageUrl = "https://images.example.com/source.jpg";
        task.updatedBy = 99L;
        return task;
    }

    private Ali1688CollectionRecords.DetailEnrichmentSnapshotRecord detailSnapshot(Long candidateId) {
        Ali1688CollectionRecords.DetailEnrichmentSnapshotRecord snapshot = new Ali1688CollectionRecords.DetailEnrichmentSnapshotRecord();
        snapshot.id = 91001L;
        snapshot.assignmentId = 90001L;
        snapshot.taskId = 87001L;
        snapshot.candidateId = candidateId;
        snapshot.snapshotSource = "DETAIL_ENRICHMENT";
        snapshot.pageUrl = "https://detail.1688.com/offer/745612345001.html";
        snapshot.detailTitle = "Razr Fold 手机壳保护套详情";
        snapshot.mainImageUrlsJson = "[\"https://images.example.com/detail-main.jpg\"]";
        snapshot.detailImageUrlsJson = "[\"https://images.example.com/detail-1.jpg\"]";
        snapshot.imageUrlsJson = "[\"https://images.example.com/detail-main.jpg\",\"https://images.example.com/detail-1.jpg\"]";
        snapshot.skuOptionsJson = "[{\"name\":\"颜色\",\"values\":[\"透明\",\"蓝色\"]}]";
        snapshot.moqText = "2件起批";
        snapshot.supplierName = "深圳详情工厂";
        snapshot.locationText = "广东 深圳";
        snapshot.listPriceText = "¥15.20";
        snapshot.serviceLabelsJson = "[\"48小时发货\"]";
        snapshot.salesLabelsJson = "[\"近90天低价\"]";
        snapshot.rawEvidenceSnippetsJson = "[\"Razr Fold 手机壳保护套详情\",\"颜色: 透明 / 蓝色\"]";
        return snapshot;
    }
}
