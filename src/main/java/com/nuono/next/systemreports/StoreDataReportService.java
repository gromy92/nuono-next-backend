package com.nuono.next.systemreports;

import com.nuono.next.infrastructure.mapper.SystemReportMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class StoreDataReportService {

    private final SystemReportMapper mapper;

    public StoreDataReportService(SystemReportMapper mapper) {
        this.mapper = mapper;
    }

    public StoreDataReportOverview overview(BusinessAccessContext context, String requestedStoreCode) {
        List<String> storeCodes = resolveStoreCodes(context, requestedStoreCode);
        List<StoreDataReportRow> rows = new ArrayList<>(mapper.selectStoreDataReportRows(storeCodes));
        rows.forEach(this::deriveStates);
        rows.sort(Comparator
                .comparing(StoreDataReportRow::getProjectCode, Comparator.nullsLast(String::compareTo))
                .thenComparing(StoreDataReportRow::getSiteCode, Comparator.nullsLast(String::compareTo))
                .thenComparing(StoreDataReportRow::getStoreCode, Comparator.nullsLast(String::compareTo)));
        return new StoreDataReportOverview(
                "店铺数据",
                LocalDateTime.now(),
                buildMetrics(rows),
                rows
        );
    }

    private List<String> resolveStoreCodes(BusinessAccessContext context, String requestedStoreCode) {
        if (StringUtils.hasText(requestedStoreCode)) {
            return List.of(normalizeStoreCode(requestedStoreCode));
        }
        return null;
    }

    private String normalizeStoreCode(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private List<StoreDataReportMetric> buildMetrics(List<StoreDataReportRow> rows) {
        long siteCount = rows.size();
        long offerCount = rows.stream().mapToLong(StoreDataReportRow::getSiteOfferCount).sum();
        long missingDetailBaseline = rows.stream().mapToLong(StoreDataReportRow::getMissingDetailBaselineCount).sum();
        long missingSalesFacts = rows.stream().mapToLong(StoreDataReportRow::getOffersWithoutSalesFacts).sum();
        long salesMappingAnomalies = rows.stream().mapToLong(StoreDataReportRow::getSalesKeysWithoutOfferCount).sum();
        long crossStoreOffers = rows.stream().mapToLong(StoreDataReportRow::getCrossStoreOfferCount).sum();
        long lifecycleMissing = rows.stream().mapToLong(StoreDataReportRow::getLifecycleMissingCount).sum();

        return List.of(
                metric("store_sites", "店铺站点", siteCount, "个", siteCount == 0 ? "empty" : "ready"),
                metric("site_offers", "商品经营面", offerCount, "条", offerCount == 0 ? "empty" : "ready"),
                metric("missing_detail_baseline", "详情基线缺失", missingDetailBaseline, "条", missingDetailBaseline > 0 ? "warning" : "ready"),
                metric("missing_sales_facts", "销量事实缺失", missingSalesFacts, "条", missingSalesFacts > 0 ? "warning" : "ready"),
                metric("sales_mapping_anomalies", "销量映射异常", salesMappingAnomalies, "个", salesMappingAnomalies > 0 ? "warning" : "ready"),
                metric("cross_store_offers", "跨店铺挂载", crossStoreOffers, "条", crossStoreOffers > 0 ? "warning" : "ready"),
                metric("lifecycle_missing", "生命周期未计算", lifecycleMissing, "条", lifecycleMissing > 0 ? "warning" : "ready")
        );
    }

    private StoreDataReportMetric metric(String key, String title, long value, String unit, String state) {
        return new StoreDataReportMetric(key, title, value, unit, state);
    }

    private void deriveStates(StoreDataReportRow row) {
        row.setDetailState(deriveDetailState(row));
        row.setSalesState(deriveSalesState(row));
        row.setLifecycleState(deriveLifecycleState(row));
        row.setOverallState(deriveOverallState(row));
    }

    private String deriveDetailState(StoreDataReportRow row) {
        if (row.getSiteOfferCount() == 0) {
            return "empty_store";
        }
        if (row.getCrossStoreOfferCount() > 0) {
            return "scope_anomaly";
        }
        if (row.getMissingDetailBaselineCount() > 0) {
            return "baseline_missing";
        }
        int fieldMissing = row.getMissingTitleEnCount()
                + row.getMissingDescriptionEnCount()
                + row.getMissingBrandCount()
                + row.getMissingProductFulltypeCount()
                + row.getMissingImageCount();
        return fieldMissing > 0 ? "field_missing" : "ready";
    }

    private String deriveSalesState(StoreDataReportRow row) {
        if (row.getSiteOfferCount() == 0 && row.getSalesProductKeyCount() == 0) {
            return "empty_store";
        }
        if (row.getSiteOfferCount() == 0 && row.getSalesProductKeyCount() > 0) {
            return "mapping_missing";
        }
        if (row.getSalesKeysWithoutOfferCount() > 0) {
            return "mapping_anomaly";
        }
        if (row.getOffersWithSalesFacts() == 0) {
            return "sales_missing";
        }
        return row.getOffersWithoutSalesFacts() > 0 ? "partial" : "ready";
    }

    private String deriveLifecycleState(StoreDataReportRow row) {
        if (row.getSiteOfferCount() == 0) {
            return "empty_store";
        }
        if (row.getLifecycleCurrentCount() == 0) {
            return "not_calculated";
        }
        if (row.getLifecycleMissingCount() > 0 || row.getLifecycleDataInsufficientCount() > 0) {
            return "partial";
        }
        return "ready";
    }

    private String deriveOverallState(StoreDataReportRow row) {
        if ("scope_anomaly".equals(row.getDetailState())
                || "mapping_missing".equals(row.getSalesState())
                || "mapping_anomaly".equals(row.getSalesState())) {
            return "anomaly";
        }
        if ("empty_store".equals(row.getDetailState()) && "empty_store".equals(row.getSalesState())) {
            return "empty";
        }
        if ("ready".equals(row.getDetailState())
                && "ready".equals(row.getSalesState())
                && "ready".equals(row.getLifecycleState())) {
            return "ready";
        }
        return "incomplete";
    }
}
