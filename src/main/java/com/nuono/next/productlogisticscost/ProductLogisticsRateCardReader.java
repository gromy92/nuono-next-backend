package com.nuono.next.productlogisticscost;

import com.nuono.next.infrastructure.mapper.ProductLogisticsCostMapper;
import com.nuono.next.infrastructure.mapper.PublishedProductLogisticsRateCardMapper;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.RateCardRow;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.RateCardView;
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
        view.items.addAll(rowsBySlot.values());
        return view;
    }

    private void addIfAbsent(Map<String, RateCardRow> rowsBySlot, List<RateCardRow> rows) {
        if (rows == null) {
            return;
        }
        rows.forEach(row -> rowsBySlot.putIfAbsent(slot(row), row));
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
