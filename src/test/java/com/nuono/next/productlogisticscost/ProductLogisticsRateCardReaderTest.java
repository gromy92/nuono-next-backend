package com.nuono.next.productlogisticscost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductLogisticsCostMapper;
import com.nuono.next.infrastructure.mapper.PublishedProductLogisticsRateCardMapper;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.RateCardRow;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.RateCardView;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductLogisticsRateCardReaderTest {

    @Mock
    private ProductLogisticsCostMapper productCostMapper;

    @Mock
    private PublishedProductLogisticsRateCardMapper publishedRateCardMapper;

    @Test
    void ownerManualRateCardWinsWhilePublishedZdCategoriesFillMissingSlots() {
        RateCardRow manual = rateCard(430001L, "ZD_SA_SEA_A", "1500.00", "MANUAL_RATE_CARD");
        RateCardRow publishedA = rateCard(912003L, "ZD_SA_SEA_A", "1550.00", "PUBLISHED_FORWARDER_QUOTE");
        RateCardRow publishedB = rateCard(912004L, "ZD_SA_SEA_B", "1550.00", "PUBLISHED_FORWARDER_QUOTE");
        when(productCostMapper.listRateCards(307L, "SA", "ZD", "SEA")).thenReturn(List.of(manual));
        when(publishedRateCardMapper.listPublishedRateCards(307L, "SA", "ZD", "SEA"))
                .thenReturn(List.of(publishedA, publishedB));

        RateCardView view = new ProductLogisticsRateCardReader(productCostMapper, publishedRateCardMapper)
                .read(307L, "SA", "ZD", "SEA");

        assertThat(view.items).containsExactly(manual, publishedB);
    }

    private RateCardRow rateCard(Long id, String category, String price, String sourceType) {
        RateCardRow row = new RateCardRow();
        row.id = id;
        row.siteCode = "SA";
        row.forwarderCode = "ZD";
        row.forwarderName = "众鸫供应链";
        row.transportMode = "SEA";
        row.feeType = "HEADHAUL";
        row.cargoCategoryCode = category;
        row.cargoCategoryName = category;
        row.chargeUnit = "CBM";
        row.unitCostCny = new BigDecimal(price);
        row.sourceType = sourceType;
        return row;
    }
}
