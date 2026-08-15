package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductBarcodeProjectionWriterTest {

    @Test
    void shouldRejectBarcodeAlreadyAssignedToAnotherProduct() {
        ProductManagementMapper mapper = mock(ProductManagementMapper.class);
        ProductBarcodeProjectionWriter writer = new ProductBarcodeProjectionWriter(mapper);
        when(mapper.selectProductBarcodeIdByBarcode(50003L, "PAPERSAYSB440")).thenReturn(55001L);
        when(mapper.selectProductBarcodeProductMasterIdByBarcode(50003L, "PAPERSAYSB440")).thenReturn(52999L);

        assertThrows(
                IllegalStateException.class,
                () -> writer.persist(
                        53001L,
                        52001L,
                        50003L,
                        "PAPERSAYS440",
                        "PAPERSAYSB440",
                        307L
                )
        );

        verify(mapper).upsertProductBarcode(
                eq(55001L),
                eq(53001L),
                eq(52001L),
                eq(50003L),
                eq("PAPERSAYS440"),
                eq("PAPERSAYSB440"),
                eq("NOON_PBARCODE"),
                eq(true),
                eq(307L)
        );
    }

    @Test
    void shouldClassifyPartnerSkuAliasAndKeepOnlyOnePrimaryBarcode() {
        ProductManagementMapper mapper = mock(ProductManagementMapper.class);
        ProductBarcodeProjectionWriter writer = new ProductBarcodeProjectionWriter(mapper);
        when(mapper.selectProductBarcodeIdByBarcode(50003L, "PAPERSAYS440")).thenReturn(55001L);
        when(mapper.selectProductBarcodeIdByBarcode(50003L, "PAPERSAYSB440")).thenReturn(55002L);
        when(mapper.selectProductBarcodeIdByBarcode(50003L, "PAPERSAYSB440-ALT")).thenReturn(55003L);
        when(mapper.selectProductBarcodeProductMasterIdByBarcode(50003L, "PAPERSAYS440"))
                .thenReturn(52001L);
        when(mapper.selectProductBarcodeProductMasterIdByBarcode(50003L, "PAPERSAYSB440"))
                .thenReturn(52001L);
        when(mapper.selectProductBarcodeProductMasterIdByBarcode(50003L, "PAPERSAYSB440-ALT"))
                .thenReturn(52001L);

        writer.persistAll(
                53001L,
                52001L,
                50003L,
                "PAPERSAYS440",
                List.of(" PAPERSAYS440 ", "PAPERSAYSB440", "PAPERSAYSB440-ALT", "PAPERSAYSB440"),
                307L
        );

        verify(mapper).upsertProductBarcode(
                55001L, 53001L, 52001L, 50003L, "PAPERSAYS440", "PAPERSAYS440",
                "PARTNER_SKU_ALIAS", false, 307L
        );
        verify(mapper).upsertProductBarcode(
                55002L, 53001L, 52001L, 50003L, "PAPERSAYS440", "PAPERSAYSB440",
                "NOON_PBARCODE", true, 307L
        );
        verify(mapper).upsertProductBarcode(
                55003L, 53001L, 52001L, 50003L, "PAPERSAYS440", "PAPERSAYSB440-ALT",
                "NOON_PBARCODE", false, 307L
        );
    }

    @Test
    void shouldTreatSinglePartnerSkuValueAsAmbiguousBarcode() {
        ProductManagementMapper mapper = mock(ProductManagementMapper.class);
        ProductBarcodeProjectionWriter writer = new ProductBarcodeProjectionWriter(mapper);
        when(mapper.selectProductBarcodeIdByBarcode(50003L, "SGGRB260")).thenReturn(55001L);
        when(mapper.selectProductBarcodeProductMasterIdByBarcode(50003L, "SGGRB260")).thenReturn(52001L);

        writer.persistAll(53001L, 52001L, 50003L, "SGGRB260", List.of("SGGRB260"), 307L);

        verify(mapper).upsertProductBarcode(
                55001L, 53001L, 52001L, 50003L, "SGGRB260", "SGGRB260",
                "NOON_PBARCODE", true, 307L
        );
    }
}
