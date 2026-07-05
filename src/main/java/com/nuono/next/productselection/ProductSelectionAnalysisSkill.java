package com.nuono.next.productselection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.ai.AiResultStatus;
import com.nuono.next.ai.AiStructuredTextCommand;
import com.nuono.next.ai.AiStructuredTextResult;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class ProductSelectionAnalysisSkill {

    static final String FEATURE_CODE = "PRODUCT_SELECTION_ANALYSIS";
    static final String OPERATION_CODE = "MANUAL_SELECTION_AI_ANALYSIS";
    private static final String SCHEMA_NAME = "nuono_product_selection_analysis_v1";

    private final ProductSelectionMapper productSelectionMapper;
    private final ProductSelectionPermissionGuard permissionGuard;
    private final ObjectProvider<AiCapabilityService> aiCapabilityServiceProvider;
    private final ObjectMapper objectMapper;

    public ProductSelectionAnalysisSkill(
            ProductSelectionMapper productSelectionMapper,
            ProductSelectionPermissionGuard permissionGuard,
            ObjectProvider<AiCapabilityService> aiCapabilityServiceProvider,
            ObjectMapper objectMapper
    ) {
        this.productSelectionMapper = productSelectionMapper;
        this.permissionGuard = permissionGuard;
        this.aiCapabilityServiceProvider = aiCapabilityServiceProvider;
        this.objectMapper = objectMapper;
    }

    public ProductSelectionAnalysisView analyze(
            String collectionId,
            ProductSelectionAnalysisCommand command,
            Long operatorUserId
    ) {
        Long sourceCollectionId = parseLongId(collectionId);
        ProductSelectionSourceCollectionRow sourceCollection =
                productSelectionMapper.selectSourceCollectionById(sourceCollectionId);
        requireVisibleSourceCollection(operatorUserId, sourceCollection);

        ProductSelectionAnalysisCommand safeCommand = command == null
                ? new ProductSelectionAnalysisCommand()
                : command;
        AiCapabilityService aiCapabilityService = aiCapabilityServiceProvider.getIfAvailable();
        if (aiCapabilityService == null) {
            return failure(sourceCollectionId, AiResultStatus.AI_DISABLED, "AI_SERVICE_MISSING", "AI 基座服务不可用。", null, null);
        }

        AiStructuredTextResult result = aiCapabilityService.createStructuredText(
                buildCommand(sourceCollection, safeCommand, operatorUserId)
        );
        if (result == null || !result.isSuccess() || result.getParsedJson() == null) {
            return failure(
                    sourceCollectionId,
                    result == null ? AiResultStatus.AI_PROVIDER_ERROR : result.getStatus(),
                    result == null ? "AI_EMPTY_RESULT" : defaultText(result.getErrorCode(), "AI_ANALYSIS_FAILED"),
                    result == null ? "AI 未返回选品分析结果。" : defaultText(result.getErrorMessage(), "AI 选品分析失败。"),
                    result == null ? null : result.getModel(),
                    result == null ? null : result.getDurationMillis()
            );
        }
        return success(sourceCollectionId, result);
    }

    private AiStructuredTextCommand buildCommand(
            ProductSelectionSourceCollectionRow sourceCollection,
            ProductSelectionAnalysisCommand command,
            Long operatorUserId
    ) {
        AiStructuredTextCommand aiCommand = new AiStructuredTextCommand();
        aiCommand.setFeatureCode(FEATURE_CODE);
        aiCommand.setOperationCode(OPERATION_CODE);
        aiCommand.setOperatorUserId(operatorUserId);
        aiCommand.setSchemaName(SCHEMA_NAME);
        aiCommand.setSchema(outputSchema());
        aiCommand.setMaxOutputTokens(1200);
        aiCommand.setInstructions(String.join("\n",
                "你是跨境电商选品分析助手，服务于运营选品决策。",
                "只基于输入的源头采集、1688 候选、竞品拉取结果和利润预估上下文做判断。",
                "不要编造未提供的采购价、物流费、销量或竞品价格；缺信息必须写入 missingInformation。",
                "recommendationLevel 只能输出 recommend、review、reject、unknown。",
                "recommendationScore 范围 0-100，不确定时降低分数。",
                "输出必须是符合 JSON schema 的中文 JSON，不要输出解释性正文。"
        ));
        aiCommand.setPrompt(inputSnapshotJson(sourceCollection, command));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceCollectionId", sourceCollection.getId());
        metadata.put("logicalStoreId", sourceCollection.getLogicalStoreId());
        metadata.put("feature", "manual-selection");
        aiCommand.setMetadata(metadata);
        return aiCommand;
    }

    private ProductSelectionAnalysisView success(Long sourceCollectionId, AiStructuredTextResult result) {
        Map<String, Object> output = result.getParsedJson();
        ProductSelectionAnalysisView view = new ProductSelectionAnalysisView();
        view.setStatus("success");
        view.setSourceCollectionId(String.valueOf(sourceCollectionId));
        view.setRecommendationLevel(defaultText(asString(output.get("recommendationLevel")), "unknown"));
        view.setRecommendationScore(clamp(asInteger(output.get("recommendationScore")), 0, 100));
        view.setConclusion(defaultText(asString(output.get("conclusion")), ""));
        view.setSummary(defaultText(asString(output.get("summary")), ""));
        view.setProfitRisks(asStringList(output.get("profitRisks")));
        view.setCompetitorRisks(asStringList(output.get("competitorRisks")));
        view.setProcurementRisks(asStringList(output.get("procurementRisks")));
        view.setLogisticsRisks(asStringList(output.get("logisticsRisks")));
        view.setMissingInformation(asStringList(output.get("missingInformation")));
        view.setNextActions(asStringList(output.get("nextActions")));
        view.setWarnings(asStringList(output.get("warnings")));
        view.setModel(result.getModel());
        view.setDurationMillis(result.getDurationMillis());
        return view;
    }

    private ProductSelectionAnalysisView failure(
            Long sourceCollectionId,
            String status,
            String errorCode,
            String errorMessage,
            String model,
            Long durationMillis
    ) {
        ProductSelectionAnalysisView view = new ProductSelectionAnalysisView();
        view.setStatus(defaultText(status, "failed"));
        view.setSourceCollectionId(sourceCollectionId == null ? null : String.valueOf(sourceCollectionId));
        view.setRecommendationLevel("unknown");
        view.setRecommendationScore(0);
        view.setErrorCode(defaultText(errorCode, "AI_ANALYSIS_FAILED"));
        view.setErrorMessage(defaultText(errorMessage, "AI 选品分析失败。"));
        view.setModel(model);
        view.setDurationMillis(durationMillis);
        return view;
    }

    private Map<String, Object> outputSchema() {
        Map<String, Object> text = object("type", "string");
        Map<String, Object> score = object("type", "integer");
        Map<String, Object> textArray = object(
                "type", "array",
                "items", object("type", "string")
        );
        Map<String, Object> properties = object(
                "recommendationLevel", text,
                "recommendationScore", score,
                "conclusion", text,
                "summary", text,
                "profitRisks", textArray,
                "competitorRisks", textArray,
                "procurementRisks", textArray,
                "logisticsRisks", textArray,
                "missingInformation", textArray,
                "nextActions", textArray,
                "warnings", textArray
        );
        return object(
                "type", "object",
                "additionalProperties", false,
                "required", Arrays.asList(
                        "recommendationLevel",
                        "recommendationScore",
                        "conclusion",
                        "summary",
                        "profitRisks",
                        "competitorRisks",
                        "procurementRisks",
                        "logisticsRisks",
                        "missingInformation",
                        "nextActions",
                        "warnings"
                ),
                "properties", properties
        );
    }

    private String inputSnapshotJson(
            ProductSelectionSourceCollectionRow sourceCollection,
            ProductSelectionAnalysisCommand command
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("sourceCollection", sourceCollectionSnapshot(sourceCollection));
        snapshot.put("competitors", command.getCompetitors());
        snapshot.put("ali1688Candidates", command.getAli1688Candidates());
        snapshot.put("profitEstimate", command.getProfitEstimate());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private Map<String, Object> sourceCollectionSnapshot(ProductSelectionSourceCollectionRow row) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", row.getId());
        snapshot.put("collectionNo", row.getCollectionNo());
        snapshot.put("storeName", row.getStoreName());
        snapshot.put("storeCode", row.getStoreCode());
        snapshot.put("sourcePlatform", row.getSourcePlatform());
        snapshot.put("sourceUrl", row.getSourceUrl());
        snapshot.put("pageUrl", row.getPageUrl());
        snapshot.put("sourceTitle", row.getSourceTitle());
        snapshot.put("sourceTitleCn", row.getSourceTitleCn());
        snapshot.put("sourceImageUrl", row.getSourceImageUrl());
        snapshot.put("priceSummary", row.getPriceSummary());
        snapshot.put("moqHint", row.getMoqHint());
        snapshot.put("shippingFrom", row.getShippingFrom());
        snapshot.put("brandName", row.getBrandName());
        snapshot.put("unitCount", row.getUnitCount());
        snapshot.put("colorName", row.getColorName());
        snapshot.put("specHints", readStringListJson(row.getSpecHintsJson()));
        snapshot.put("sourceDescriptionEn", row.getSourceDescriptionEn());
        snapshot.put("sourceSellingPointsEn", readStringListJson(row.getSourceSellingPointsEnJson()));
        snapshot.put("selectedText", row.getSelectedText());
        snapshot.put("notes", row.getNotes());
        snapshot.put("status", row.getStatus());
        return snapshot;
    }

    private void requireVisibleSourceCollection(Long operatorUserId, ProductSelectionSourceCollectionRow row) {
        if (row == null) {
            throw new IllegalArgumentException("采集记录不存在或已被删除。");
        }
        ProductSelectionUserContext user = permissionGuard.requireActiveUser(operatorUserId);
        if (isSuperAdmin(user)) {
            return;
        }
        int visibleSites = productSelectionMapper.countVisibleLogicalStoreSites(
                user.getUserId(),
                row.getLogicalStoreId()
        );
        if (visibleSites <= 0) {
            throw new ProductSelectionAccessDeniedException("当前账号不能访问该采集记录。");
        }
    }

    private boolean isSuperAdmin(ProductSelectionUserContext user) {
        return user != null
                && (Integer.valueOf(0).equals(user.getLevel()) || "admin".equalsIgnoreCase(user.getAccountNo()));
    }

    private Long parseLongId(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("采集记录不存在或已被删除。");
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("采集记录不存在或已被删除。");
        }
    }

    private List<String> readStringListJson(String value) {
        if (!StringUtils.hasText(value)) {
            return new ArrayList<>();
        }
        try {
            List<String> result = objectMapper.readValue(value, new TypeReference<List<String>>() {
            });
            return result == null ? new ArrayList<>() : result;
        } catch (Exception exception) {
            return new ArrayList<>();
        }
    }

    private List<String> asStringList(Object value) {
        if (!(value instanceof List<?>)) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item != null && StringUtils.hasText(String.valueOf(item))) {
                result.add(String.valueOf(item).trim());
            }
        }
        return result;
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

    private int clamp(Integer value, int min, int max) {
        return Math.max(min, Math.min(value == null ? 0 : value, max));
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Object> object(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
