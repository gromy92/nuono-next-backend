package com.nuono.next.productselection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.ai.AiStructuredTextCommand;
import com.nuono.next.ai.AiStructuredTextResult;
import com.nuono.next.infrastructure.mapper.Ali1688CollectionMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class Ali1688CandidateAiAssessmentService {

    static final String FEATURE_CODE = "ALI1688_CANDIDATE_SCORE";
    static final String OPERATION_CODE = "CANDIDATE_MATCH_SPEC_ASSESSMENT";
    private static final String PROMPT_VERSION = "ALI1688_CANDIDATE_SCORE_PROMPT_V1";
    private static final String SCHEMA_VERSION = "ALI1688_CANDIDATE_SCORE_SCHEMA_V1";
    private static final String SCHEMA_NAME = "nuono_ali1688_candidate_score_v1";
    private static final String WORKER_NAME = "ali1688-candidate-ai-assessment";

    private final Ali1688CollectionMapper ali1688CollectionMapper;
    private final ObjectProvider<AiCapabilityService> aiCapabilityServiceProvider;
    private final ObjectMapper objectMapper;

    @Value("${nuono.product-selection.ali1688.ai.scheduler.enabled:false}")
    private boolean schedulerEnabled;

    @Value("${nuono.product-selection.ali1688.ai.scheduler.max-items-per-tick:3}")
    private int schedulerMaxItems;

    @Value("${nuono.product-selection.ali1688.ai.scheduler.lock-timeout-minutes:10}")
    private int lockTimeoutMinutes;

    @Value("${nuono.product-selection.ali1688.ai.scheduler.max-attempts:3}")
    private int maxAttempts;

    public Ali1688CandidateAiAssessmentService(
            Ali1688CollectionMapper ali1688CollectionMapper,
            ObjectProvider<AiCapabilityService> aiCapabilityServiceProvider,
            ObjectMapper objectMapper
    ) {
        this.ali1688CollectionMapper = ali1688CollectionMapper;
        this.aiCapabilityServiceProvider = aiCapabilityServiceProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void createPendingAssessments(
            Ali1688CollectionRecords.TaskRecord task,
            List<Ali1688CollectionRecords.CandidateRecord> candidates
    ) {
        for (Ali1688CollectionRecords.CandidateRecord candidate : candidates) {
            Ali1688CollectionRecords.AiAssessmentRecord assessment = new Ali1688CollectionRecords.AiAssessmentRecord();
            assessment.id = ali1688CollectionMapper.nextAiAssessmentId();
            assessment.taskId = task.id;
            assessment.candidateId = candidate.id;
            assessment.status = "pending";
            assessment.featureCode = FEATURE_CODE;
            assessment.operationCode = OPERATION_CODE;
            assessment.promptVersion = PROMPT_VERSION;
            assessment.schemaVersion = SCHEMA_VERSION;
            assessment.inputSnapshotJson = inputSnapshotJson(task, candidate);
            assessment.inputHash = sha256(assessment.inputSnapshotJson);
            assessment.createdBy = task.updatedBy;
            assessment.updatedBy = task.updatedBy;
            ali1688CollectionMapper.insertAiAssessment(assessment);
        }
    }

    @Scheduled(
            fixedDelayString = "${nuono.product-selection.ali1688.ai.scheduler.fixed-delay-ms:7000}",
            initialDelayString = "${nuono.product-selection.ali1688.ai.scheduler.initial-delay-ms:5000}"
    )
    public void processPendingAssessments() {
        if (!schedulerEnabled) {
            return;
        }
        processPendingAssessmentsOnce();
    }

    public int processPendingAssessmentsOnce() {
        int limit = Math.max(1, Math.min(schedulerMaxItems, 10));
        int processed = 0;
        for (Long assessmentId : ali1688CollectionMapper.listClaimableAiAssessmentIds(maxAttempts, lockTimeoutMinutes, limit)) {
            String lockedBy = WORKER_NAME + "-" + UUID.randomUUID();
            if (ali1688CollectionMapper.claimAiAssessment(assessmentId, lockedBy, maxAttempts, lockTimeoutMinutes) <= 0) {
                continue;
            }
            runAssessment(assessmentId, lockedBy);
            processed++;
        }
        return processed;
    }

    private void runAssessment(Long assessmentId, String lockedBy) {
        Ali1688CollectionRecords.AiAssessmentRecord assessment = ali1688CollectionMapper.selectAiAssessmentById(assessmentId);
        if (assessment == null) {
            return;
        }
        Ali1688CollectionRecords.CandidateRecord candidate = ali1688CollectionMapper.selectCandidateById(assessment.candidateId);
        Ali1688CollectionRecords.TaskRecord task = candidate == null ? null : ali1688CollectionMapper.selectTaskById(candidate.taskId);
        if (candidate == null || task == null) {
            markFailed(assessment, "candidate_missing", "1688 候选或任务不存在，AI 判断终止。");
            return;
        }
        AiCapabilityService aiCapabilityService = aiCapabilityServiceProvider.getIfAvailable();
        if (aiCapabilityService == null) {
            markFailed(assessment, "ai_service_missing", "AI 基座服务不可用。");
            ali1688CollectionMapper.markCandidateAiAssessmentFailed(candidate.id, candidate.updatedBy);
            return;
        }

        AiStructuredTextResult result = aiCapabilityService.createStructuredText(buildCommand(task, candidate, assessment));
        if (result == null || !result.isSuccess() || result.getParsedJson() == null) {
            markFailed(
                    assessment,
                    result == null ? "ai_empty_result" : defaultText(result.getErrorCode(), "ai_assessment_failed"),
                    result == null ? "AI 未返回结果。" : defaultText(result.getErrorMessage(), "AI 判断未返回可用结构化结果。")
            );
            ali1688CollectionMapper.markCandidateAiAssessmentFailed(candidate.id, candidate.updatedBy);
            return;
        }

        Map<String, Object> parsedJson = result.getParsedJson();
        Integer matchScore = clamp(asInteger(parsedJson.get("matchScore")), 0, 35);
        Integer specScore = clamp(asInteger(parsedJson.get("specScore")), 0, 20);
        String riskLevel = shrink(defaultText(asString(parsedJson.get("riskLevel")), "unknown"), 30);
        Integer totalScore = clamp(defaultInt(candidate.ruleScore) + matchScore + specScore, 0, 100);
        String outputJson = writeJson(parsedJson);
        String scoreDetailJson = finalScoreDetailJson(candidate, parsedJson, matchScore, specScore, riskLevel, result.getModel());

        ali1688CollectionMapper.markAiAssessmentSuccess(
                assessment.id,
                lockedBy,
                result.getModel(),
                outputJson,
                matchScore,
                specScore,
                riskLevel,
                assessment.updatedBy
        );
        ali1688CollectionMapper.updateCandidateAiScore(
                candidate.id,
                matchScore,
                specScore,
                totalScore,
                scoreDetailJson,
                candidate.updatedBy
        );
        reselectTopFive(task.id, candidate.updatedBy);
    }

    private AiStructuredTextCommand buildCommand(
            Ali1688CollectionRecords.TaskRecord task,
            Ali1688CollectionRecords.CandidateRecord candidate,
            Ali1688CollectionRecords.AiAssessmentRecord assessment
    ) {
        AiStructuredTextCommand command = new AiStructuredTextCommand();
        command.setFeatureCode(FEATURE_CODE);
        command.setOperationCode(OPERATION_CODE);
        command.setOperatorUserId(task.updatedBy);
        command.setSchemaName(SCHEMA_NAME);
        command.setSchema(outputSchema());
        command.setMaxOutputTokens(700);
        command.setInstructions(String.join("\n",
                "你是跨境电商采购的 1688 候选商品判断助手。",
                "只基于输入的源头商品和 1688 候选信息判断是否同款或近似款。",
                "matchScore 范围 0-35，specScore 范围 0-20；不确定时降低分数并写入 warnings。",
                "riskLevel 只能输出 low、medium、high、unknown。",
                "只输出符合 JSON schema 的 JSON，不要输出解释性正文。"
        ));
        command.setPrompt(defaultText(assessment.inputSnapshotJson, inputSnapshotJson(task, candidate)));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("taskId", task.id);
        metadata.put("candidateId", candidate.id);
        metadata.put("sourceCollectionId", task.sourceCollectionId);
        metadata.put("feature", "product-selection-ali1688");
        command.setMetadata(metadata);
        return command;
    }

    private Map<String, Object> outputSchema() {
        Map<String, Object> score = object("type", "integer");
        Map<String, Object> text = object("type", "string");
        Map<String, Object> textArray = object(
                "type", "array",
                "items", object("type", "string")
        );
        Map<String, Object> properties = object(
                "matchScore", score,
                "specScore", score,
                "riskLevel", text,
                "reasons", textArray,
                "warnings", textArray
        );
        return object(
                "type", "object",
                "additionalProperties", false,
                "required", Arrays.asList("matchScore", "specScore", "riskLevel", "reasons", "warnings"),
                "properties", properties
        );
    }

    private String inputSnapshotJson(
            Ali1688CollectionRecords.TaskRecord task,
            Ali1688CollectionRecords.CandidateRecord candidate
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("sourceCollectionId", task.sourceCollectionId);
        snapshot.put("sourceTitle", task.sourceTitle);
        snapshot.put("sourceTitleCn", task.sourceTitleCn);
        snapshot.put("sourceImageUrl", task.sourceImageUrl);
        snapshot.put("sourceUrl", task.sourceUrl);
        snapshot.put("pageUrl", task.pageUrl);
        snapshot.put("candidateId", candidate.id);
        snapshot.put("offerId", candidate.offerId);
        snapshot.put("candidateUrl", candidate.candidateUrl);
        snapshot.put("title", candidate.title);
        snapshot.put("supplierName", candidate.supplierName);
        snapshot.put("mainImageUrl", candidate.mainImageUrl);
        snapshot.put("imageUrlsJson", candidate.imageUrlsJson);
        snapshot.put("priceText", candidate.priceText);
        snapshot.put("moqText", candidate.moqText);
        snapshot.put("skuSnapshotJson", candidate.skuSnapshotJson);
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private String finalScoreDetailJson(
            Ali1688CollectionRecords.CandidateRecord candidate,
            Map<String, Object> aiOutput,
            Integer matchScore,
            Integer specScore,
            String riskLevel,
            String model
    ) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("version", Ali1688CandidateScoringService.SCORE_VERSION);
        detail.put("ruleScoreMax", 45);
        detail.put("priceScore", candidate.priceScore);
        detail.put("moqScore", candidate.moqScore);
        detail.put("supplierScore", candidate.supplierScore);
        detail.put("deliveryScore", candidate.deliveryScore);
        detail.put("matchScore", matchScore);
        detail.put("specScore", specScore);
        detail.put("riskLevel", riskLevel);
        detail.put("model", model);
        detail.put("aiOutput", aiOutput);
        detail.put("aiPending", false);
        return writeJson(detail);
    }

    private void markFailed(Ali1688CollectionRecords.AiAssessmentRecord assessment, String failureCode, String failureMessage) {
        ali1688CollectionMapper.markAiAssessmentFailed(
                assessment.id,
                shrink(defaultText(failureCode, "ai_assessment_failed"), 100),
                shrink(defaultText(failureMessage, "AI 判断失败。"), 480),
                assessment.updatedBy
        );
    }

    private void reselectTopFive(Long taskId, Long updatedBy) {
        List<Ali1688CollectionRecords.CandidateRecord> candidates = ali1688CollectionMapper.listCandidatesByTask(taskId);
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        ali1688CollectionMapper.clearSelectedRanks(taskId, updatedBy);
        List<Ali1688CollectionRecords.CandidateRecord> selected = candidates.stream()
                .sorted(Comparator.comparing(this::rankingScore).reversed()
                        .thenComparing(item -> item.rankNo == null ? Integer.MAX_VALUE : item.rankNo))
                .limit(5)
                .collect(Collectors.toList());
        int selectedRank = 1;
        for (Ali1688CollectionRecords.CandidateRecord candidate : selected) {
            ali1688CollectionMapper.updateSelectedRank(taskId, candidate.id, selectedRank, updatedBy);
            selectedRank++;
        }
    }

    private Integer rankingScore(Ali1688CollectionRecords.CandidateRecord candidate) {
        if (candidate == null) {
            return 0;
        }
        if ("final".equals(candidate.scoreStatus) && candidate.totalScore != null) {
            return candidate.totalScore;
        }
        return candidate.ruleScore == null ? 0 : candidate.ruleScore;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && StringUtils.hasText((String) value)) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer clamp(Integer value, int min, int max) {
        return Math.max(min, Math.min(value == null ? 0 : value, max));
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private Map<String, Object> object(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String shrink(String value, int maxLength) {
        String text = defaultText(value, "");
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 1)) + "…";
    }
}
