package com.nuono.next.logisticsquote;

import com.nuono.next.infrastructure.mapper.LogisticsQuoteMapper;
import com.nuono.next.system.LocalDbBootstrapStatusService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class LogisticsQuoteOperationService {

    private static final long SYSTEM_USER_ID = 1L;

    private static final int REQUIRED_OPERATION_TABLE_COUNT = 9;

    private final ObjectProvider<LogisticsQuoteMapper> logisticsQuoteMapperProvider;
    private final ObjectProvider<LocalDbBootstrapStatusService> localDbBootstrapStatusServiceProvider;

    public LogisticsQuoteOperationService(
            ObjectProvider<LogisticsQuoteMapper> logisticsQuoteMapperProvider,
            ObjectProvider<LocalDbBootstrapStatusService> localDbBootstrapStatusServiceProvider
    ) {
        this.logisticsQuoteMapperProvider = logisticsQuoteMapperProvider;
        this.localDbBootstrapStatusServiceProvider = localDbBootstrapStatusServiceProvider;
    }

    public LogisticsQuoteOperationPriceItemsView listPriceItems(
            String transportMode,
            Long forwarderId,
            String priceStatus
    ) {
        String normalizedTransportMode = normalizeFilter(transportMode);
        String normalizedPriceStatus = normalizeFilter(priceStatus);
        List<LogisticsQuoteOperationPriceItemView> items;
        LogisticsQuoteOperationPriceItemsView view = new LogisticsQuoteOperationPriceItemsView();

        if (isLocalDbOperationPersistenceReady()) {
            LogisticsQuoteMapper mapper = logisticsQuoteMapperProvider.getObject();
            items = mapper.listOperationPriceItems(
                    normalizedTransportMode,
                    forwarderId,
                    normalizedPriceStatus
            );
            view.setMode("local-db");
            view.setReady(true);
            view.setMessage("当前报价维护列表已从本地标准报价明细表回读；运营调整值只覆盖数值字段，不改管理员标准报价。");
        } else {
            items = buildSampleItems();
            if (StringUtils.hasText(normalizedTransportMode)) {
                items.removeIf(item -> !normalizedTransportMode.equals(item.getTransportMode()));
            }
            if (StringUtils.hasText(normalizedPriceStatus)) {
                items.removeIf(item -> !normalizedPriceStatus.equals(item.getPriceStatus()));
            }
            view.setMode("sample-only");
            view.setReady(true);
            view.setMessage("本地库尚未执行 030 物流报价运营表，当前先展示样本结构用于验收页面。");
        }

        view.setItems(items);
        view.setSummary(buildSummary(items));
        return view;
    }

    @Transactional
    public LogisticsQuoteOperationPriceAdjustmentView savePriceAdjustment(
            LogisticsQuoteOperationPriceAdjustmentCommand command
    ) {
        LogisticsQuoteMapper mapper = requireOperationPersistence();
        if (command == null) {
            throw new IllegalArgumentException("请先选择要调整的报价明细。");
        }

        String targetType = requireText(command.getTargetType(), "调整对象类型不能为空。").toUpperCase(Locale.ROOT);
        Long targetId = command.getTargetId();
        if (targetId == null) {
            throw new IllegalArgumentException("调整对象 ID 不能为空。");
        }
        String fieldName = requireText(command.getNumericField(), "调整字段不能为空。").toLowerCase(Locale.ROOT);
        Double adjustedValue = command.getAdjustedValue();
        if (adjustedValue == null || adjustedValue.isNaN() || adjustedValue.isInfinite()) {
            throw new IllegalArgumentException("调整值必须是有效数字。");
        }
        if (adjustedValue < 0) {
            throw new IllegalArgumentException("调整值不能小于 0。");
        }
        String reason = requireText(command.getReason(), "请填写调整原因，便于后续审计。");
        if (reason.length() > 500) {
            throw new IllegalArgumentException("调整原因不能超过 500 个字符。");
        }

        LogisticsQuoteOperationPriceItemView targetItem = mapper.listOperationPriceItems(null, null, null)
                .stream()
                .filter(item -> targetType.equals(item.getTargetType()))
                .filter(item -> targetId.equals(item.getTargetId()))
                .filter(item -> fieldName.equals(item.getNumericField()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到可调整的报价数值，请刷新列表后重试。"));

        Long operatorUserId = command.getOperatorUserId() == null ? SYSTEM_USER_ID : command.getOperatorUserId();
        String actionType = Boolean.TRUE.equals(targetItem.getHasAdjustment()) ? "UPDATE" : "CREATE";
        Double beforeValue = targetItem.getEffectiveValue() == null
                ? targetItem.getStandardValue()
                : targetItem.getEffectiveValue();

        mapper.upsertNumericAdjustment(
                mapper.nextNumericAdjustmentId(),
                targetItem.getQuoteVersionId(),
                targetType,
                targetId,
                fieldName,
                targetItem.getStandardValue(),
                adjustedValue,
                targetItem.getCurrency(),
                reason,
                operatorUserId
        );
        Long adjustmentId = mapper.selectActiveNumericAdjustmentId(targetType, targetId, fieldName);
        if (adjustmentId == null) {
            throw new IllegalStateException("调整记录保存后未能回读，请刷新后重试。");
        }

        Long logId = mapper.nextNumericAdjustmentLogId();
        mapper.insertNumericAdjustmentLog(
                logId,
                adjustmentId,
                targetItem.getQuoteVersionId(),
                targetType,
                targetId,
                fieldName,
                beforeValue,
                adjustedValue,
                actionType,
                reason,
                operatorUserId
        );

        LogisticsQuoteOperationPriceAdjustmentView view = new LogisticsQuoteOperationPriceAdjustmentView();
        view.setReady(true);
        view.setAdjustmentId(adjustmentId);
        view.setLogId(logId);
        view.setMessage("运营调整已保存，并已写入审计日志。管理员标准报价底稿未被修改。");
        return view;
    }

    private boolean isLocalDbOperationPersistenceReady() {
        LogisticsQuoteMapper mapper = logisticsQuoteMapperProvider.getIfAvailable();
        LocalDbBootstrapStatusService bootstrapStatusService = localDbBootstrapStatusServiceProvider.getIfAvailable();
        if (mapper == null || bootstrapStatusService == null) {
            return false;
        }
        Integer existingTableCount = mapper.countExistingOperationQuoteTables(
                bootstrapStatusService.inspect().getSchema()
        );
        return existingTableCount != null && existingTableCount >= REQUIRED_OPERATION_TABLE_COUNT;
    }

    private LogisticsQuoteMapper requireOperationPersistence() {
        LogisticsQuoteMapper mapper = logisticsQuoteMapperProvider.getIfAvailable();
        if (mapper == null || !isLocalDbOperationPersistenceReady()) {
            throw new IllegalArgumentException("当前本地库还没有准备好物流报价运营维护表，请先执行 030_logistics_quote_operations_v1.sql。");
        }
        return mapper;
    }

    private LogisticsQuoteOperationPriceItemsSummaryView buildSummary(List<LogisticsQuoteOperationPriceItemView> items) {
        LogisticsQuoteOperationPriceItemsSummaryView summary = new LogisticsQuoteOperationPriceItemsSummaryView();
        summary.setTotalItems(items.size());
        summary.setAirItemCount((int) items.stream().filter(item -> "AIR".equals(item.getTransportMode())).count());
        summary.setSeaItemCount((int) items.stream().filter(item -> "SEA".equals(item.getTransportMode())).count());
        summary.setWarehouseItemCount((int) items.stream().filter(item -> "WAREHOUSE".equals(item.getTransportMode())).count());
        summary.setAdjustedItemCount((int) items.stream().filter(item -> Boolean.TRUE.equals(item.getHasAdjustment())).count());
        return summary;
    }

    private String normalizeFilter(String value) {
        if (!StringUtils.hasText(value) || "ALL".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String message) {
        String normalized = value == null ? null : value.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private List<LogisticsQuoteOperationPriceItemView> buildSampleItems() {
        List<LogisticsQuoteOperationPriceItemView> items = new ArrayList<>();
        items.add(sampleItem(
                912001L,
                "BASE_PRICE",
                "unit_price",
                "ZD-20260411",
                "众鸫供应链",
                "ZD-SAU-AIR-FBN-RUH",
                "沙特空运专线 FBN利雅得（含送仓报价）",
                "AIR",
                "沙特空运（普货）",
                "PER_KG",
                67d,
                "KG",
                "NORMAL"
        ));
        items.add(sampleItem(
                912003L,
                "BASE_PRICE",
                "unit_price",
                "ZD-20260411",
                "众鸫供应链",
                "ZD-SAU-SEA-WH-RUH",
                "沙特海运专线到众鸫海外仓 + FBN利雅得送仓",
                "SEA",
                "沙特海运（A类）",
                "PER_CBM",
                1250d,
                "CBM",
                "NORMAL"
        ));
        items.add(sampleItem(
                912020L,
                "BASE_PRICE",
                "unit_price",
                "YT-SAU-UNDATED-001",
                "义特物流",
                "YT-SAU-SEA-FBN-RUH",
                "义特沙特海运双清包税 + FBN利雅得送仓",
                "SEA",
                "普货",
                "PER_CBM",
                1190d,
                "CBM",
                "NORMAL"
        ));
        items.add(sampleItem(
                913001L,
                "TRANSPORT_FEE",
                "amount",
                "ZD-20260411",
                "众鸫供应链",
                "ZD-SAU-SEA-WH-RUH",
                "沙特海运专线到众鸫海外仓 + FBN利雅得送仓",
                "SEA",
                "利雅得FBN送仓费",
                "PER_CBM",
                200d,
                "CBM",
                "NORMAL"
        ));
        items.add(sampleItem(
                915003L,
                "WAREHOUSE_PROCESSING_FEE",
                "amount",
                "ZD-20260411",
                "众鸫供应链",
                "ZD-SAU-WH-PROCESS",
                "众鸫沙特海外仓商品处理服务",
                "WAREHOUSE",
                "商品贴标费",
                "FIXED_PER_UNIT",
                1d,
                "PCS",
                "NORMAL"
        ));
        return items;
    }

    private LogisticsQuoteOperationPriceItemView sampleItem(
            Long targetId,
            String targetType,
            String numericField,
            String quoteVersionNo,
            String forwarderName,
            String serviceCode,
            String serviceName,
            String transportMode,
            String cargoCategoryName,
            String pricingModel,
            Double standardValue,
            String billingUnit,
            String priceStatus
    ) {
        LogisticsQuoteOperationPriceItemView item = new LogisticsQuoteOperationPriceItemView();
        item.setTargetId(targetId);
        item.setTargetType(targetType);
        item.setNumericField(numericField);
        item.setQuoteVersionNo(quoteVersionNo);
        item.setForwarderName(forwarderName);
        item.setServiceCode(serviceCode);
        item.setServiceName(serviceName);
        item.setTransportMode(transportMode);
        item.setTargetPlatform("FBN");
        item.setDeliveryCity("利雅得/RUH");
        item.setCargoCategoryName(cargoCategoryName);
        item.setPricingModel(pricingModel);
        item.setCurrency("RMB");
        item.setStandardValue(standardValue);
        item.setEffectiveValue(standardValue);
        item.setBillingUnit(billingUnit);
        item.setBillingBasis("样本口径，正式值以 030 初始化数据为准。");
        item.setPriceStatus(priceStatus);
        item.setSourceFileName("forwarder-standardized-saudi-fbn-riyadh-v3-20260507.xlsx");
        item.setSourceLocator("sample");
        item.setHasAdjustment(false);
        item.setUpdatedAt("2026-05-07 00:00:00");
        return item;
    }
}
