package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void scheduledProcessorLeavesPendingRowsUntouchedWhenSwitchIsDisabled() {
        Ali1688CandidateAiAssessmentService service = service();
        ReflectionTestUtils.setField(service, "schedulerEnabled", false);

        service.processPendingAssessments();

        verify(mapper, never()).listClaimableAiAssessmentIds(3, 10, 3);
        verify(mapper, never()).claimAiAssessment(any(), anyString(), any(), any());
        verify(aiCapabilityServiceProvider, never()).getIfAvailable();
    }

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
        assertStringArraySchema(command.getSchema(), "reasons");
        assertStringArraySchema(command.getSchema(), "warnings");
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
    void processPendingAssessmentDropsHighRuleButLowMatchCandidateFromTopFive() {
        Ali1688CandidateAiAssessmentService service = service();
        Ali1688CollectionRecords.AiAssessmentRecord assessment = assessment();
        Ali1688CollectionRecords.CandidateRecord candidate = candidate();
        Ali1688CollectionRecords.TaskRecord task = task();
        AiStructuredTextResult aiResult = AiStructuredTextResult.success();
        aiResult.setModel("gpt-test");
        aiResult.setParsedJson(Map.of(
                "matchScore", 3,
                "specScore", 3,
                "riskLevel", "high",
                "reasons", List.of("源商品是手机整机，候选是手机膜"),
                "warnings", List.of("明显错品")
        ));

        when(mapper.listClaimableAiAssessmentIds(3, 10, 3)).thenReturn(List.of(assessment.id));
        when(mapper.claimAiAssessment(eq(assessment.id), anyString(), eq(3), eq(10))).thenReturn(1);
        when(mapper.selectAiAssessmentById(assessment.id)).thenReturn(assessment);
        when(mapper.selectCandidateById(candidate.id)).thenReturn(candidate);
        when(mapper.selectTaskById(task.id)).thenReturn(task);
        when(mapper.listCandidatesByTask(task.id)).thenReturn(List.of(
                finalCandidate(candidate.id, 6, 1),
                ruleCandidate(88002L, 44, 2),
                ruleCandidate(88003L, 43, 3),
                ruleCandidate(88004L, 42, 4),
                ruleCandidate(88005L, 41, 5),
                ruleCandidate(88006L, 40, 6)
        ));
        when(aiCapabilityServiceProvider.getIfAvailable()).thenReturn(aiCapabilityService);
        when(aiCapabilityService.createStructuredText(any(AiStructuredTextCommand.class))).thenReturn(aiResult);

        int processed = service.processPendingAssessmentsOnce();

        assertEquals(1, processed);
        verify(mapper).updateCandidateAiScore(
                eq(candidate.id),
                eq(3),
                eq(3),
                eq(6),
                contains("\"finalScoreMode\":\"ai_mismatch_gate\""),
                eq(99L)
        );
        verify(mapper).clearSelectedRanks(task.id, candidate.updatedBy);
        verify(mapper, never()).updateSelectedRank(task.id, candidate.id, 1, candidate.updatedBy);
        verify(mapper).updateSelectedRank(task.id, 88002L, 1, candidate.updatedBy);
        verify(mapper).updateSelectedRank(task.id, 88003L, 2, candidate.updatedBy);
        verify(mapper).updateSelectedRank(task.id, 88004L, 3, candidate.updatedBy);
        verify(mapper).updateSelectedRank(task.id, 88005L, 4, candidate.updatedBy);
        verify(mapper).updateSelectedRank(task.id, 88006L, 5, candidate.updatedBy);
    }

    @Test
    void processPendingAssessmentDoesNotSilentlyRerankWhenSelectedCandidateIsAlreadyDownstream() {
        Ali1688CandidateAiAssessmentService service = service();
        Ali1688CollectionRecords.AiAssessmentRecord assessment = assessment();
        Ali1688CollectionRecords.CandidateRecord candidate = candidate();
        Ali1688CollectionRecords.TaskRecord task = task();
        Ali1688CollectionRecords.CandidateRecord frozen = finalCandidate(88009L, 96, 1);
        frozen.selectedRankNo = 1;
        frozen.scoreDetailJson = "{\"downstreamFrozen\":true,\"priceState\":\"price_probe_pending\"}";
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
        when(mapper.listCandidatesByTask(task.id)).thenReturn(List.of(
                frozen,
                finalCandidate(candidate.id, 93, 2),
                ruleCandidate(88002L, 44, 3)
        ));
        when(aiCapabilityServiceProvider.getIfAvailable()).thenReturn(aiCapabilityService);
        when(aiCapabilityService.createStructuredText(any(AiStructuredTextCommand.class))).thenReturn(aiResult);

        int processed = service.processPendingAssessmentsOnce();

        assertEquals(1, processed);
        verify(mapper).updateCandidateAiScore(eq(candidate.id), eq(31), eq(17), eq(93), contains("\"finalScoreMode\":\"rule_plus_ai\""), eq(99L));
        verify(mapper, never()).clearSelectedRanks(task.id, candidate.updatedBy);
        verify(mapper, never()).updateSelectedRank(eq(task.id), any(), any(), eq(candidate.updatedBy));
    }

    @Test
    void processPendingAssessmentUsesOriginalRankAsStableTieBreaker() {
        Ali1688CandidateAiAssessmentService service = service();
        Ali1688CollectionRecords.AiAssessmentRecord assessment = assessment();
        Ali1688CollectionRecords.CandidateRecord candidate = candidate();
        Ali1688CollectionRecords.TaskRecord task = task();
        AiStructuredTextResult aiResult = AiStructuredTextResult.success();
        aiResult.setModel("gpt-test");
        aiResult.setParsedJson(Map.of(
                "matchScore", 20,
                "specScore", 5,
                "riskLevel", "low",
                "reasons", List.of("近似款"),
                "warnings", List.of("规格不足")
        ));

        when(mapper.listClaimableAiAssessmentIds(3, 10, 3)).thenReturn(List.of(assessment.id));
        when(mapper.claimAiAssessment(eq(assessment.id), anyString(), eq(3), eq(10))).thenReturn(1);
        when(mapper.selectAiAssessmentById(assessment.id)).thenReturn(assessment);
        when(mapper.selectCandidateById(candidate.id)).thenReturn(candidate);
        when(mapper.selectTaskById(task.id)).thenReturn(task);
        when(mapper.listCandidatesByTask(task.id)).thenReturn(List.of(
                finalCandidate(candidate.id, 70, 2),
                finalCandidate(88002L, 70, 1),
                ruleCandidate(88003L, 42, 3)
        ));
        when(aiCapabilityServiceProvider.getIfAvailable()).thenReturn(aiCapabilityService);
        when(aiCapabilityService.createStructuredText(any(AiStructuredTextCommand.class))).thenReturn(aiResult);

        int processed = service.processPendingAssessmentsOnce();

        assertEquals(1, processed);
        verify(mapper).clearSelectedRanks(task.id, candidate.updatedBy);
        verify(mapper).updateSelectedRank(task.id, 88002L, 1, candidate.updatedBy);
        verify(mapper).updateSelectedRank(task.id, candidate.id, 2, candidate.updatedBy);
        verify(mapper).updateSelectedRank(task.id, 88003L, 3, candidate.updatedBy);
    }

    @Test
    void processPendingAssessmentRejectsMissingRequiredAiOutputFields() {
        Ali1688CandidateAiAssessmentService service = service();
        Ali1688CollectionRecords.AiAssessmentRecord assessment = assessment();
        Ali1688CollectionRecords.CandidateRecord candidate = candidate();
        AiStructuredTextResult aiResult = AiStructuredTextResult.success();
        aiResult.setModel("gpt-test");
        aiResult.setParsedJson(Map.of(
                "matchScore", 31,
                "riskLevel", "low"
        ));

        when(mapper.listClaimableAiAssessmentIds(3, 10, 3)).thenReturn(List.of(assessment.id));
        when(mapper.claimAiAssessment(eq(assessment.id), anyString(), eq(3), eq(10))).thenReturn(1);
        when(mapper.selectAiAssessmentById(assessment.id)).thenReturn(assessment);
        when(mapper.selectCandidateById(candidate.id)).thenReturn(candidate);
        when(mapper.selectTaskById(candidate.taskId)).thenReturn(task());
        when(aiCapabilityServiceProvider.getIfAvailable()).thenReturn(aiCapabilityService);
        when(aiCapabilityService.createStructuredText(any(AiStructuredTextCommand.class))).thenReturn(aiResult);

        int processed = service.processPendingAssessmentsOnce();

        assertEquals(1, processed);
        verify(mapper).markAiAssessmentFailed(
                eq(assessment.id),
                eq("invalid_ai_output"),
                contains("specScore"),
                eq(assessment.updatedBy)
        );
        verify(mapper).markCandidateAiAssessmentFailed(candidate.id, candidate.updatedBy);
        verify(mapper, never()).markAiAssessmentSuccess(eq(assessment.id), anyString(), anyString(), anyString(), any(), any(), anyString(), any());
        verify(mapper, never()).updateCandidateAiScore(eq(candidate.id), any(), any(), any(), anyString(), any());
    }

    @Test
    void processPendingAssessmentRejectsOutOfRangeScoresWithoutFinalScore() {
        Ali1688CandidateAiAssessmentService service = service();
        Ali1688CollectionRecords.AiAssessmentRecord assessment = assessment();
        Ali1688CollectionRecords.CandidateRecord candidate = candidate();
        AiStructuredTextResult aiResult = AiStructuredTextResult.success();
        aiResult.setModel("gpt-test");
        aiResult.setParsedJson(Map.of(
                "matchScore", 48,
                "specScore", 17,
                "riskLevel", "low",
                "reasons", List.of("分数越界"),
                "warnings", List.of()
        ));

        when(mapper.listClaimableAiAssessmentIds(3, 10, 3)).thenReturn(List.of(assessment.id));
        when(mapper.claimAiAssessment(eq(assessment.id), anyString(), eq(3), eq(10))).thenReturn(1);
        when(mapper.selectAiAssessmentById(assessment.id)).thenReturn(assessment);
        when(mapper.selectCandidateById(candidate.id)).thenReturn(candidate);
        when(mapper.selectTaskById(candidate.taskId)).thenReturn(task());
        when(aiCapabilityServiceProvider.getIfAvailable()).thenReturn(aiCapabilityService);
        when(aiCapabilityService.createStructuredText(any(AiStructuredTextCommand.class))).thenReturn(aiResult);

        int processed = service.processPendingAssessmentsOnce();

        assertEquals(1, processed);
        verify(mapper).markAiAssessmentFailed(
                eq(assessment.id),
                eq("invalid_ai_output"),
                contains("matchScore"),
                eq(assessment.updatedBy)
        );
        verify(mapper).markCandidateAiAssessmentFailed(candidate.id, candidate.updatedBy);
        verify(mapper, never()).updateCandidateAiScore(eq(candidate.id), any(), any(), any(), anyString(), any());
    }

    @Test
    void processPendingAssessmentRejectsInvalidRiskLevelWithoutFinalScore() {
        Ali1688CandidateAiAssessmentService service = service();
        Ali1688CollectionRecords.AiAssessmentRecord assessment = assessment();
        Ali1688CollectionRecords.CandidateRecord candidate = candidate();
        AiStructuredTextResult aiResult = AiStructuredTextResult.success();
        aiResult.setModel("gpt-test");
        aiResult.setParsedJson(Map.of(
                "matchScore", 31,
                "specScore", 17,
                "riskLevel", "critical",
                "reasons", List.of("模型输出非法风险等级"),
                "warnings", List.of()
        ));

        when(mapper.listClaimableAiAssessmentIds(3, 10, 3)).thenReturn(List.of(assessment.id));
        when(mapper.claimAiAssessment(eq(assessment.id), anyString(), eq(3), eq(10))).thenReturn(1);
        when(mapper.selectAiAssessmentById(assessment.id)).thenReturn(assessment);
        when(mapper.selectCandidateById(candidate.id)).thenReturn(candidate);
        when(mapper.selectTaskById(candidate.taskId)).thenReturn(task());
        when(aiCapabilityServiceProvider.getIfAvailable()).thenReturn(aiCapabilityService);
        when(aiCapabilityService.createStructuredText(any(AiStructuredTextCommand.class))).thenReturn(aiResult);

        int processed = service.processPendingAssessmentsOnce();

        assertEquals(1, processed);
        verify(mapper).markAiAssessmentFailed(
                eq(assessment.id),
                eq("invalid_ai_output"),
                contains("riskLevel"),
                eq(assessment.updatedBy)
        );
        verify(mapper).markCandidateAiAssessmentFailed(candidate.id, candidate.updatedBy);
        verify(mapper, never()).markAiAssessmentSuccess(eq(assessment.id), anyString(), anyString(), anyString(), any(), any(), anyString(), any());
        verify(mapper, never()).updateCandidateAiScore(eq(candidate.id), any(), any(), any(), anyString(), any());
    }

    @Test
    void processPendingAssessmentMarksCandidateFailedWhenAiServiceReturnsFailureOrNoJson() {
        Ali1688CandidateAiAssessmentService service = service();
        Ali1688CollectionRecords.AiAssessmentRecord assessment = assessment();
        Ali1688CollectionRecords.CandidateRecord candidate = candidate();
        AiStructuredTextResult aiResult = AiStructuredTextResult.failure("failed", "provider_unavailable", "AI provider unavailable");

        when(mapper.listClaimableAiAssessmentIds(3, 10, 3)).thenReturn(List.of(assessment.id));
        when(mapper.claimAiAssessment(eq(assessment.id), anyString(), eq(3), eq(10))).thenReturn(1);
        when(mapper.selectAiAssessmentById(assessment.id)).thenReturn(assessment);
        when(mapper.selectCandidateById(candidate.id)).thenReturn(candidate);
        when(mapper.selectTaskById(candidate.taskId)).thenReturn(task());
        when(aiCapabilityServiceProvider.getIfAvailable()).thenReturn(aiCapabilityService);
        when(aiCapabilityService.createStructuredText(any(AiStructuredTextCommand.class))).thenReturn(aiResult);

        int processed = service.processPendingAssessmentsOnce();

        assertEquals(1, processed);
        verify(mapper).markAiAssessmentFailed(
                eq(assessment.id),
                eq("provider_unavailable"),
                eq("AI provider unavailable"),
                eq(assessment.updatedBy)
        );
        verify(mapper).markCandidateAiAssessmentFailed(candidate.id, candidate.updatedBy);
        verify(mapper, never()).updateCandidateAiScore(eq(candidate.id), any(), any(), any(), anyString(), any());
    }

    @Test
    void createPendingAssessmentSnapshotIncludesSourceCandidateAndListHintContext() {
        Ali1688CandidateAiAssessmentService service = service();
        Ali1688CollectionRecords.TaskRecord task = task();
        Ali1688CollectionRecords.CandidateRecord candidate = candidate();
        candidate.title = "适用于 Razr Fold 的手机保护膜";
        candidate.candidateUrl = "https://detail.1688.com/offer/88201.html";
        candidate.supplierName = "深圳市手机配件工厂";
        candidate.mainImageUrl = "https://cbu01.alicdn.com/img/ibank/candidate.jpg";
        candidate.imageUrlsJson = "[\"https://cbu01.alicdn.com/img/ibank/candidate.jpg\"]";
        candidate.priceText = "¥ 6 .93 运费4元起 4400+件 50件起批";
        candidate.moqText = "50件起批";
        candidate.locationText = "广东 深圳";
        candidate.skuSnapshotJson = "{\"适用型号\":\"Razr Fold\"}";
        candidate.supplierSnapshotJson = "{\"factory\":true}";
        candidate.logisticsSnapshotJson = "{\"shipFrom\":\"深圳\"}";

        when(mapper.nextAiAssessmentId()).thenReturn(89009L);

        service.createPendingAssessmentsForCurrentTask(task, List.of(candidate));

        org.mockito.ArgumentCaptor<Ali1688CollectionRecords.AiAssessmentRecord> captor =
                org.mockito.ArgumentCaptor.forClass(Ali1688CollectionRecords.AiAssessmentRecord.class);
        verify(mapper).insertAiAssessment(captor.capture());
        String snapshot = captor.getValue().inputSnapshotJson;
        assertTrue(snapshot.contains("Razr Fold 手机整机"));
        assertTrue(snapshot.contains("https://images.example.com/razr-source.jpg"));
        assertTrue(snapshot.contains("https://www.noon.com/uae-en/razr-fold"));
        assertTrue(snapshot.contains("适用于 Razr Fold 的手机保护膜"));
        assertTrue(snapshot.contains("https://detail.1688.com/offer/88201.html"));
        assertTrue(snapshot.contains("深圳市手机配件工厂"));
        assertTrue(snapshot.contains("¥ 6 .93 运费4元起 4400+件 50件起批"));
        assertTrue(snapshot.contains("50件起批"));
        assertTrue(snapshot.contains("广东 深圳"));
        assertTrue(snapshot.contains("supplierSnapshotJson"));
        assertTrue(snapshot.contains("logisticsSnapshotJson"));
    }

    private Ali1688CandidateAiAssessmentService service() {
        Ali1688CandidateAiAssessmentService service = new Ali1688CandidateAiAssessmentService(
                mapper,
                aiCapabilityServiceProvider,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(service, "schedulerMaxItems", 3);
        ReflectionTestUtils.setField(service, "lockTimeoutMinutes", 10);
        ReflectionTestUtils.setField(service, "maxAttempts", 3);
        return service;
    }

    @SuppressWarnings("unchecked")
    private void assertStringArraySchema(Map<String, Object> schema, String fieldName) {
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertNotNull(properties);
        Map<String, Object> field = (Map<String, Object>) properties.get(fieldName);
        assertNotNull(field);
        assertEquals("array", field.get("type"));
        Map<String, Object> items = (Map<String, Object>) field.get("items");
        assertNotNull(items);
        assertEquals("string", items.get("type"));
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
        candidate.title = "仿真罂粟花束";
        candidate.candidateUrl = "https://detail.1688.com/offer/88001.html";
        candidate.supplierName = "义乌源头工厂";
        candidate.mainImageUrl = "https://images.example.com/candidate.jpg";
        candidate.imageUrlsJson = "[\"https://images.example.com/candidate.jpg\"]";
        candidate.priceText = "¥12.80-18.60";
        candidate.moqText = "2件起批";
        candidate.locationText = "浙江 义乌";
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
        task.sourceTitle = "Razr Fold phone handset";
        task.sourceTitleCn = "Razr Fold 手机整机";
        task.sourceImageUrl = "https://images.example.com/razr-source.jpg";
        task.sourceUrl = "https://www.noon.com/uae-en/razr-fold";
        task.pageUrl = "https://www.noon.com/uae-en/razr-fold";
        task.updatedBy = 99L;
        return task;
    }
}
