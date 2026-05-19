package com.nuono.next.filemanagement.parse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class FileParseResultItemViewAssembler {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public FileParseResultItemViewAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FileParseProcessingItemView toProcessingItemView(FileParseResultItemRow row) {
        FileParseProcessingItemView view = new FileParseProcessingItemView();
        view.setItemId(row.getId());
        view.setTaskId(row.getTaskId());
        view.setResultId(row.getResultId());
        view.setItemType(row.getItemType());
        view.setNaturalKey(row.getNaturalKey());
        view.setChangeType(row.getChangeType());
        view.setReviewStatus(row.getReviewStatus());
        view.setConfidence(row.getConfidence());
        view.setValidationStatus(currentValidationStatus(row));
        view.setFields(currentPayload(row));
        view.setOldFields(readMap(row.getOldPayloadJson()));
        view.setChangedFieldKeys(readStringList(row.getChangedFieldKeysJson()));
        view.setEvidence(readMap(row.getEvidenceJson()));
        view.setValidationError(readMap(row.getValidationErrorJson()));
        view.setSortNo(row.getSortNo());
        return view;
    }

    public FileParseItemCompareView toCompareView(FileParseResultItemRow row) {
        FileParseItemCompareView view = new FileParseItemCompareView();
        view.setItemId(row.getId());
        view.setTaskId(row.getTaskId());
        view.setResultId(row.getResultId());
        view.setChangeType(row.getChangeType());
        view.setNaturalKey(row.getNaturalKey());
        view.setChangedFieldKeys(readStringList(row.getChangedFieldKeysJson()));
        view.setBaseFields(readMap(row.getOldPayloadJson()));
        view.setCurrentFields(currentPayload(row));
        view.setReviewStatus(row.getReviewStatus());
        return view;
    }

    public List<FileParseProcessingColumnView> buildColumns(List<FileParseItemStandardRow> itemStandards) {
        if (itemStandards == null || itemStandards.isEmpty()) {
            return List.of();
        }
        FileParseItemStandardRow standard = itemStandards.get(0);
        Map<String, Object> fieldSchema = readMap(standard.getFieldSchemaJson());
        Map<String, Object> displayConfig = readMap(standard.getDisplayConfigJson());
        List<String> keys = readColumns(displayConfig, fieldSchema);
        Map<String, Object> labels = readNestedMap(displayConfig, "labels");
        Map<String, Object> widths = readNestedMap(displayConfig, "widths");
        List<FileParseProcessingColumnView> columns = new ArrayList<>();
        for (String key : keys) {
            FileParseProcessingColumnView column = new FileParseProcessingColumnView();
            column.setKey(key);
            column.setLabel(StringUtils.hasText(text(labels.get(key))) ? text(labels.get(key)) : fallbackLabel(key));
            column.setType(text(fieldSchema.get(key)));
            column.setTableVisible(true);
            column.setWidth(width(widths.get(key)));
            columns.add(column);
        }
        return columns;
    }

    public Map<String, Object> currentPayload(FileParseResultItemRow row) {
        if (StringUtils.hasText(row.getEffectivePayloadJson())) {
            return readMap(row.getEffectivePayloadJson());
        }
        return readMap(row.getNormalizedPayloadJson());
    }

    public String currentPayloadJson(FileParseResultItemRow row) {
        if (StringUtils.hasText(row.getEffectivePayloadJson())) {
            return row.getEffectivePayloadJson();
        }
        return StringUtils.hasText(row.getNormalizedPayloadJson()) ? row.getNormalizedPayloadJson() : "{}";
    }

    public String currentValidationStatus(FileParseResultItemRow row) {
        if (StringUtils.hasText(row.getEffectiveValidationStatus())) {
            return row.getEffectiveValidationStatus();
        }
        return row.getValidationStatus();
    }

    public Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("结果行 JSON 解析失败。");
        }
    }

    public String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("结果行 JSON 序列化失败。");
        }
    }

    private List<String> readColumns(Map<String, Object> displayConfig, Map<String, Object> fieldSchema) {
        Object columnsValue = displayConfig.get("columns");
        List<String> columns = new ArrayList<>();
        if (columnsValue instanceof List) {
            for (Object value : (List<?>) columnsValue) {
                if (value instanceof String && StringUtils.hasText((String) value)) {
                    columns.add(((String) value).trim());
                }
            }
        }
        if (columns.isEmpty()) {
            columns.addAll(fieldSchema.keySet());
        }
        return columns;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readNestedMap(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) value);
        }
        return new LinkedHashMap<>();
    }

    private Integer width(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = text(value);
        if (!StringUtils.hasText(text)) {
            return 160;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return 160;
        }
    }

    private String fallbackLabel(String key) {
        switch (key) {
            case "country":
                return "国家";
            case "platform":
                return "平台";
            case "fulfillmentType":
                return "履约方式";
            case "categoryName":
                return "类目";
            case "amountRangeLabel":
                return "计佣金额区间";
            case "amountMin":
                return "金额下限";
            case "amountMinInclusive":
                return "下限含边界";
            case "amountMax":
                return "金额上限";
            case "amountMaxInclusive":
                return "上限含边界";
            case "amountCurrency":
            case "currency":
                return "币种";
            case "commissionRate":
                return "佣金率";
            case "effectiveDate":
                return "生效日期";
            case "feeItem":
                return "费用项";
            case "sizeTier":
                return "尺寸分层";
            case "feeAmount":
                return "费用金额";
            case "minFee":
                return "最低费用";
            case "channelKey":
                return "渠道标识";
            case "city":
                return "城市";
            case "shippingMethod":
                return "运输方式";
            case "billingRule":
                return "计费内容";
            case "leadTime":
                return "时效";
            default:
                return key;
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException error) {
            return new ArrayList<>();
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
