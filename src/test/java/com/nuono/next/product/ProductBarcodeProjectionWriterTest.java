package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import org.junit.jupiter.api.Test;

class ProductBarcodeProjectionWriterTest {

    @Test
    void shouldRejectBarcodeAlreadyAssignedToAnotherProduct() {
        ProductManagementMapper mapper = mock(ProductManagementMapper.class);
        ProductBarcodeProjectionWriter writer = new ProductBarcodeProjectionWriter(mapper);
        when(mapper.selectProductBarcodeIdByBarcode("PAPERSAYSB440")).thenReturn(55001L);
        when(mapper.selectProductBarcodeProductMasterIdByBarcode("PAPERSAYSB440")).thenReturn(52999L);

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
                isNull(),
                eq(true),
                eq(307L)
        );
    }
}
