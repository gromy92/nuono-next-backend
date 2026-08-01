package com.nuono.next.logisticsquote;

import com.nuono.next.infrastructure.mapper.LogisticsQuoteMapper;
import com.nuono.next.system.LocalDbBootstrapStatusService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LogisticsQuoteOperationService {

    private static final int REQUIRED_OPERATION_TABLE_COUNT = 7;

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
            view.setMessage("当前列表只读正式报价版本及报价明细；价格变更通过新报价版本生效。");
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

    private LogisticsQuoteOperationPriceItemsSummaryView buildSummary(List<LogisticsQuoteOperationPriceItemView> items) {
        LogisticsQuoteOperationPriceItemsSummaryView summary = new LogisticsQuoteOperationPriceItemsSummaryView();
        summary.setTotalItems(items.size());
        summary.setAirItemCount((int) items.stream().filter(item -> "AIR".equals(item.getTransportMode())).count());
        summary.setSeaItemCount((int) items.stream().filter(item -> "SEA".equals(item.getTransportMode())).count());
        summary.setWarehouseItemCount((int) items.stream().filter(item -> "WAREHOUSE".equals(item.getTransportMode())).count());
        return summary;
    }

    private String normalizeFilter(String value) {
        if (!StringUtils.hasText(value) || "ALL".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
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
                "YT-SAU-20260728",
                "义特物流",
                "YT-SAU-SEA-FBN-RUH-20260728",
                "义特沙特海运双清包税 + FBN利雅得送仓 20260728",
                "SEA",
                "普货",
                "PER_CBM",
                1540d,
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
        item.setUpdatedAt("2026-05-07 00:00:00");
        return item;
    }
}
