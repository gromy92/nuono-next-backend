package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnLineInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnShippingBatchLinkInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.ShippingBatchSourceAllocationRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.StoreSiteRecord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OfficialWarehouseAsnSourceAllocationModuleTest {

    private OfficialWarehouseMapper mapper;
    private OfficialWarehouseAsnSourceAllocationModule module;

    @BeforeEach
    void setUp() {
        mapper = mock(OfficialWarehouseMapper.class);
        module = new OfficialWarehouseAsnSourceAllocationModule(mapper);
    }

    @Test
    void sameSkuCanMixFiveBatchUnitsWithThreeManualUnits() {
        ShippingBatchSourceAllocationRecord allocation = allocation(5);
        when(mapper.listShippingBatchSourceAllocations(
                anyLong(), anyString(), anyString(), anyCollection(), anyCollection(), anyCollection()))
                .thenReturn(new ArrayList<>(List.of(allocation)));
        when(mapper.nextAsnShippingBatchLinkId()).thenReturn(520001L);
        AsnLineInsertRecord mixed = line(8, 5);

        List<AsnShippingBatchLinkInsertRecord> links = module.buildLinks(
                307L, site(), 500001L, List.of(mixed), List.of(53023L), 901L);

        assertThat(links).singleElement().satisfies(link -> {
            assertThat(link.quantity).isEqualTo(5);
            assertThat(link.partnerSku).isEqualTo("PSKU-001");
        });
        assertThat(mixed.quantity).isEqualTo(8);
        assertThat(mixed.sourceBarcodes).containsExactly("BARCODE-001");
    }

    @Test
    void selectedBatchCannotBeAttachedToManualOnlyLines() {
        assertThatThrownBy(() -> module.buildLinks(
                307L, site(), 500001L, List.of(line(4, 0)), List.of(53023L), 901L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少选择一个物流单内商品");
    }

    private static ShippingBatchSourceAllocationRecord allocation(int quantity) {
        ShippingBatchSourceAllocationRecord row = new ShippingBatchSourceAllocationRecord();
        row.inTransitBatchId = 53023L;
        row.inTransitGoodsLineId = 54282L;
        row.shippingBatchNo = "TRACKING-001";
        row.partnerSku = "PSKU-001";
        row.sourceBarcode = "BARCODE-001";
        row.quantity = quantity;
        return row;
    }

    private static AsnLineInsertRecord line(int totalQuantity, int shippingBatchQuantity) {
        AsnLineInsertRecord row = new AsnLineInsertRecord();
        row.id = 510001L;
        row.productMasterId = 1001L;
        row.productVariantId = 2001L;
        row.partnerSku = "PSKU-001";
        row.pskuCode = "Z-001";
        row.quantity = totalQuantity;
        row.shippingBatchQuantity = shippingBatchQuantity;
        return row;
    }

    private static StoreSiteRecord site() {
        StoreSiteRecord row = new StoreSiteRecord();
        row.storeCode = "STR108065-NSA";
        row.siteCode = "SA";
        return row;
    }
}
