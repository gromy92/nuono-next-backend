package com.nuono.next.productlogisticscost;

import com.nuono.next.infrastructure.mapper.ProductLogisticsCostMapper;
import com.nuono.next.infrastructure.mapper.PublishedProductLogisticsRateCardMapper;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.RateCardRow;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.RateCardView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProductLogisticsRateCardReader {

    private final ProductLogisticsCostMapper productCostMapper;
    private final PublishedProductLogisticsRateCardMapper publishedRateCardMapper;

    public ProductLogisticsRateCardReader(
            ProductLogisticsCostMapper productCostMapper,
            PublishedProductLogisticsRateCardMapper publishedRateCardMapper
    ) {
        this.productCostMapper = productCostMapper;
        this.publishedRateCardMapper = publishedRateCardMapper;
    }

    public RateCardView read(Long ownerUserId, String siteCode, String forwarderCode, String transportMode) {
        Map<String, RateCardRow> rowsBySlot = new LinkedHashMap<>();
        addIfAbsent(rowsBySlot, productCostMapper.listRateCards(
                ownerUserId,
                siteCode,
                forwarderCode,
                transportMode
        ));
        addIfAbsent(rowsBySlot, publishedRateCardMapper.listPublishedRateCards(
                ownerUserId,
                siteCode,
                forwarderCode,
                transportMode
        ));
        RateCardView view = new RateCardView();
        List<RateCardRow> rows = new ArrayList<>(rowsBySlot.values());
        rows.sort(Comparator
                .comparing((RateCardRow row) -> text(row.siteCode))
                .thenComparing(row -> text(row.forwarderCode))
                .thenComparing(row -> text(row.transportMode))
                .thenComparing(row -> text(row.cargoCategoryCode))
                .thenComparing(row -> text(row.chargeUnit)));
        view.items.addAll(rows);
        return view;
    }

    private void addIfAbsent(Map<String, RateCardRow> rowsBySlot, List<RateCardRow> rows) {
        if (rows == null) {
            return;
        }
        rows.forEach(row -> {
            normalizePublishedEtCategory(row);
            RateCardRow existing = rowsBySlot.putIfAbsent(slot(row), row);
            if (existing != null) {
                inheritPublishedCategoryDescription(existing, row);
            }
        });
    }

    private void inheritPublishedCategoryDescription(RateCardRow preferred, RateCardRow fallback) {
        if (preferred == null
                || fallback == null
                || !text(preferred.cargoCategoryDescription).isEmpty()
                || !"PUBLISHED_FORWARDER_QUOTE".equals(text(fallback.sourceType))) {
            return;
        }
        preferred.cargoCategoryDescription = fallback.cargoCategoryDescription;
    }

    private void normalizePublishedEtCategory(RateCardRow row) {
        if (row == null
                || !"ET".equals(text(row.forwarderCode))
                || !"PUBLISHED_FORWARDER_QUOTE".equals(text(row.sourceType))) {
            return;
        }
        String categoryCode = text(row.cargoCategoryCode);
        int marker = categoryCode.lastIndexOf("-CAT-");
        String normalizedCode = marker < 0 ? "" : categoryCode.substring(marker + 5);
        if (normalizedCode.length() != 1 || !Character.isLetterOrDigit(normalizedCode.charAt(0))) {
            return;
        }
        row.cargoCategoryCode = normalizedCode;
        row.cargoCategoryName = normalizedCode + "类别运费";
    }

    private String slot(RateCardRow row) {
        return String.join("|",
                text(row.siteCode),
                text(row.forwarderCode),
                text(row.transportMode),
                text(row.feeType),
                text(row.cargoCategoryCode),
                text(row.chargeUnit)
        );
    }

    private String text(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
