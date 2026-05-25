package com.nuono.next.filemanagement.parse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        List<FileParseProcessingColumnView> columns = new ArrayList<>();
        Set<String> columnKeys = new LinkedHashSet<>();
        for (FileParseItemStandardRow standard : displayColumnStandards(itemStandards)) {
            Map<String, Object> fieldSchema = readMap(standard.getFieldSchemaJson());
            Map<String, Object> displayConfig = readMap(standard.getDisplayConfigJson());
            List<String> keys = readColumns(displayConfig, fieldSchema);
            Map<String, Object> labels = readNestedMap(displayConfig, "labels");
            Map<String, Object> widths = readNestedMap(displayConfig, "widths");
            for (String key : keys) {
                if (!StringUtils.hasText(key) || !columnKeys.add(key)) {
                    continue;
                }
                FileParseProcessingColumnView column = new FileParseProcessingColumnView();
                column.setKey(key);
                column.setLabel(StringUtils.hasText(text(labels.get(key))) ? text(labels.get(key)) : fallbackLabel(key));
                column.setType(text(fieldSchema.get(key)));
                column.setTableVisible(true);
                column.setWidth(width(widths.get(key)));
                columns.add(column);
            }
        }
        return columns;
    }

    private List<FileParseItemStandardRow> displayColumnStandards(List<FileParseItemStandardRow> itemStandards) {
        boolean hasStructuredLogistics = itemStandards.stream()
                .map(FileParseItemStandardRow::getItemType)
                .anyMatch(FileParseLogisticsQuoteStandard.structuredItemTypeNames()::contains);
        boolean hasStructuredOfficialOutbound = itemStandards.stream()
                .map(FileParseItemStandardRow::getItemType)
                .anyMatch(FileParseOfficialOutboundFeeStandard.structuredItemTypeNames()::contains);
        if (!hasStructuredLogistics && !hasStructuredOfficialOutbound) {
            return itemStandards;
        }
        List<FileParseItemStandardRow> standards = new ArrayList<>();
        for (FileParseItemStandardRow standard : itemStandards) {
            if (hasStructuredLogistics
                    && FileParseLogisticsQuoteStandard.LEGACY_CHANNEL_RULE.equals(standard.getItemType())) {
                continue;
            }
            if (hasStructuredOfficialOutbound
                    && FileParseOfficialOutboundFeeStandard.LEGACY_OUTBOUND_FEE_RULE.equals(standard.getItemType())) {
                continue;
            }
            standards.add(standard);
        }
        return standards;
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
            case "classificationName":
                return "规格分类";
            case "longestSideMaxCm":
                return "最长边上限";
            case "medianSideMaxCm":
                return "中间边上限";
            case "shortestSideMaxCm":
                return "最短边上限";
            case "maxShippingWeightGrams":
                return "最大发货重量";
            case "packagingWeightGrams":
                return "包装重量";
            case "priority":
                return "优先级";
            case "dimensionUnit":
                return "尺寸单位";
            case "weightUnit":
                return "重量单位";
            case "weightMinGrams":
                return "重量下限";
            case "weightMinInclusive":
                return "下限含边界";
            case "weightMaxGrams":
                return "重量上限";
            case "weightMaxInclusive":
                return "上限含边界";
            case "standardFeeAmount":
                return "标准费用";
            case "highAspFeeAmount":
                return "高客单价费用";
            case "salesPriceThresholdAmount":
                return "售价阈值";
            case "thresholdCurrency":
                return "阈值币种";
            case "extraWeightStepGrams":
                return "额外重量步长";
            case "extraFeeAmount":
                return "额外费用";
            case "policyName":
                return "策略名称";
            case "shippingWeightFormula":
                return "发货重量公式";
            case "dimensionSortRule":
                return "尺寸排序规则";
            case "weightBoundaryRule":
                return "重量边界规则";
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
            case "forwarderCode":
                return "货代编码";
            case "forwarderName":
                return "货代";
            case "fulfillmentMode":
                return "履约模式";
            case "destinationNode":
                return "目的节点";
            case "transportMode":
                return "运输方式";
            case "serviceScope":
                return "服务范围";
            case "originWarehouse":
                return "始发仓";
            case "destinationWarehouse":
                return "目的仓";
            case "departureFrequency":
                return "发车/航班频次";
            case "leadTimeText":
                return "时效";
            case "serviceLineKey":
                return "服务线标识";
            case "cargoCategoryKey":
                return "货物分类标识";
            case "categoryCode":
                return "分类编码";
            case "productExamples":
                return "商品示例";
            case "electricType":
                return "带电属性";
            case "sensitiveTags":
                return "敏感标签";
            case "packingPolicy":
                return "包装规则";
            case "manualConfirmRequired":
                return "需人工确认";
            case "unitPrice":
                return "单价";
            case "billingUnit":
                return "计费单位";
            case "pricingModel":
                return "计价模型";
            case "minimumBillableUnit":
                return "最低计费";
            case "minimumBillableUnitType":
                return "最低计费单位";
            case "volumeDivisor":
                return "体积重系数";
            case "seaWeightRatio":
                return "海运体积比";
            case "roundingRule":
                return "进位规则";
            case "priceStatus":
                return "价格状态";
            case "surchargeName":
                return "附加费名称";
            case "surchargeType":
                return "附加费类型";
            case "triggerCondition":
                return "触发条件";
            case "rate":
                return "费率";
            case "includedInBasePrice":
                return "是否含在基础价";
            case "ruleName":
                return "规则名称";
            case "conditionText":
                return "条件";
            case "operator":
                return "运算符";
            case "thresholdValue":
                return "阈值";
            case "thresholdUnit":
                return "阈值单位";
            case "actionText":
                return "处理方式";
            case "severity":
                return "严重级别";
            case "warehouseNode":
                return "仓节点";
            case "serviceName":
                return "服务名称";
            case "serviceType":
                return "服务类型";
            case "processingScope":
                return "处理范围";
            case "feeType":
                return "费用类型";
            case "amount":
                return "金额";
            case "freeCondition":
                return "免费条件";
            case "restrictionType":
                return "限制类型";
            case "itemText":
                return "限制项";
            case "requirementText":
                return "要求";
            case "applicabilityScope":
                return "适用范围";
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
