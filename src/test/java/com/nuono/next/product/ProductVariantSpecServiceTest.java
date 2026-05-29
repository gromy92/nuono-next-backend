package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductVariantSpecServiceTest {

    @Mock
    private ProductManagementMapper mapper;

    private ProductVariantSpecService service;

    @BeforeEach
    void setUp() {
        service = new ProductVariantSpecService(mapper);
    }

    @Test
    void listShouldReturnMissingStatusForSkuWithoutSpec() {
        ProductVariantSpecRecord row = new ProductVariantSpecRecord();
        row.setVariantId(53001L);
        row.setPartnerSku("MILKYWAYA05");
        row.setChildSku("N701");
        row.setSizeEn("One Size");
        when(mapper.selectProductVariantSpecs(10002L, "STR245027-NAE", "MILKYWAYA05P"))
                .thenReturn(List.of(row));

        ProductVariantSpecListCommand command = new ProductVariantSpecListCommand();
        command.setOwnerUserId(10002L);
        command.setStoreCode("STR245027-NAE");
        command.setSkuParent("MILKYWAYA05P");

        ProductVariantSpecListView view = service.list(command);

        assertEquals(true, view.isReady());
        assertEquals("not_found", view.getItems().get(0).getCompletenessStatus());
        assertEquals(List.of("spec_not_found"), view.getItems().get(0).getMissingFields());
    }

    @Test
    void saveShouldAllowUnknownAndReturnLogisticsAttributeUnknownStatus() {
        ProductVariantSpecCommand command = validCommand();
        command.setBatteryMagneticType("unknown");
        when(mapper.selectProductVariantForSpec(10002L, "STR245027-NAE", "MILKYWAYA05P", "MILKYWAYA05", "N701"))
                .thenReturn(scopeRecord());
        when(mapper.nextProductVariantSpecId()).thenReturn(99001L);

        ProductVariantSpecView saved = service.save(command);

        assertEquals("logistics_attribute_unknown", saved.getCompletenessStatus());
        assertEquals(List.of("logistics_attribute"), saved.getMissingFields());
        verify(mapper).upsertProductVariantSpec(any(ProductVariantSpecCommand.class));
    }

    @Test
    void saveShouldAllowPartialSpecsAndReturnMissingStatus() {
        ProductVariantSpecCommand command = validCommand();
        command.setProductLengthCm(null);
        command.setCartonQuantity(null);
        when(mapper.selectProductVariantForSpec(10002L, "STR245027-NAE", "MILKYWAYA05P", "MILKYWAYA05", "N701"))
                .thenReturn(scopeRecord());
        when(mapper.nextProductVariantSpecId()).thenReturn(99001L);

        ProductVariantSpecView saved = service.save(command);

        assertEquals("missing_dimensions", saved.getCompletenessStatus());
        assertEquals(List.of("dimensions"), saved.getMissingFields());
    }

    @Test
    void saveShouldRejectNonPositiveNumbersWhenProvided() {
        ProductVariantSpecCommand command = validCommand();
        command.setProductWeightG(BigDecimal.ZERO);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(command));

        assertEquals("产品重量必须大于 0", error.getMessage());
    }

    @Test
    void saveShouldRejectNonPositiveCartonQuantityWhenProvided() {
        ProductVariantSpecCommand command = validCommand();
        command.setCartonQuantity(0);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(command));

        assertEquals("箱装数必须大于 0", error.getMessage());
    }

    @Test
    void saveShouldRejectInvalidBatteryMagneticType() {
        ProductVariantSpecCommand command = validCommand();
        command.setBatteryMagneticType("flammable");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(command));

        assertEquals("带电/磁属性不合法", error.getMessage());
    }

    @Test
    void saveShouldRejectInvalidLiquidPowderType() {
        ProductVariantSpecCommand command = validCommand();
        command.setLiquidPowderType("gel");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(command));

        assertEquals("液体/粉末属性不合法", error.getMessage());
    }

    @Test
    void saveShouldReturnMissingWeightWhenOnlyWeightsAreMissing() {
        ProductVariantSpecCommand command = validCommand();
        command.setProductWeightG(null);
        command.setCartonWeightKg(null);
        when(mapper.selectProductVariantForSpec(10002L, "STR245027-NAE", "MILKYWAYA05P", "MILKYWAYA05", "N701"))
                .thenReturn(scopeRecord());
        when(mapper.nextProductVariantSpecId()).thenReturn(99001L);

        ProductVariantSpecView saved = service.save(command);

        assertEquals("missing_weight", saved.getCompletenessStatus());
        assertEquals(List.of("weight"), saved.getMissingFields());
    }

    @Test
    void saveShouldReturnReadyWhenOnlyCartonSpecsAreMissing() {
        ProductVariantSpecCommand command = validCommand();
        command.setCartonLengthCm(null);
        command.setCartonWidthCm(null);
        command.setCartonHeightCm(null);
        command.setCartonWeightKg(null);
        command.setCartonQuantity(null);
        when(mapper.selectProductVariantForSpec(10002L, "STR245027-NAE", "MILKYWAYA05P", "MILKYWAYA05", "N701"))
                .thenReturn(scopeRecord());
        when(mapper.nextProductVariantSpecId()).thenReturn(99001L);

        ProductVariantSpecView saved = service.save(command);

        assertEquals("ready", saved.getCompletenessStatus());
        assertEquals(List.of(), saved.getMissingFields());
    }

    @Test
    void saveShouldValidatePositiveNumbersAndUpsertSpec() {
        ProductVariantSpecCommand command = validCommand();
        when(mapper.selectProductVariantForSpec(10002L, "STR245027-NAE", "MILKYWAYA05P", "MILKYWAYA05", "N701"))
                .thenReturn(scopeRecord());
        when(mapper.nextProductVariantSpecId()).thenReturn(99001L);

        ProductVariantSpecView saved = service.save(command);

        assertEquals("ready", saved.getCompletenessStatus());
        ArgumentCaptor<ProductVariantSpecCommand> captor = ArgumentCaptor.forClass(ProductVariantSpecCommand.class);
        verify(mapper).upsertProductVariantSpec(captor.capture());
        assertEquals(53001L, captor.getValue().getVariantId());
        assertEquals(99001L, captor.getValue().getId());
    }

    @Test
    void saveShouldRejectSkuOutsideScopedProduct() {
        ProductVariantSpecCommand command = validCommand();
        when(mapper.selectProductVariantForSpec(10002L, "STR245027-NAE", "MILKYWAYA05P", "MILKYWAYA05", "N701"))
                .thenReturn(null);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(command));

        assertEquals("SKU 不属于当前商品或店铺范围", error.getMessage());
    }

    @Test
    void saveShouldRejectMissingOperatorUserId() {
        ProductVariantSpecCommand command = validCommand();
        command.setOperatorUserId(null);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.save(command));

        assertEquals("操作人不能为空", error.getMessage());
    }

    @Test
    void saveShouldReturnConfirmationAuditFields() {
        ProductVariantSpecCommand command = validCommand();
        when(mapper.selectProductVariantForSpec(10002L, "STR245027-NAE", "MILKYWAYA05P", "MILKYWAYA05", "N701"))
                .thenReturn(scopeRecord());
        when(mapper.nextProductVariantSpecId()).thenReturn(99001L);

        ProductVariantSpecView saved = service.save(command);

        assertEquals(10003L, saved.getConfirmedBy());
        assertNotNull(saved.getConfirmedAt());
    }

    private ProductVariantSpecCommand validCommand() {
        ProductVariantSpecCommand command = new ProductVariantSpecCommand();
        command.setOwnerUserId(10002L);
        command.setStoreCode("STR245027-NAE");
        command.setSkuParent("MILKYWAYA05P");
        command.setPartnerSku("MILKYWAYA05");
        command.setChildSku("N701");
        command.setProductLengthCm(new BigDecimal("12.3"));
        command.setProductWidthCm(new BigDecimal("4.5"));
        command.setProductHeightCm(new BigDecimal("6.7"));
        command.setProductWeightG(new BigDecimal("120"));
        command.setCartonLengthCm(new BigDecimal("40"));
        command.setCartonWidthCm(new BigDecimal("30"));
        command.setCartonHeightCm(new BigDecimal("20"));
        command.setCartonWeightKg(new BigDecimal("8.5"));
        command.setCartonQuantity(48);
        command.setBatteryMagneticType("none");
        command.setLiquidPowderType("none");
        command.setOperatorUserId(10003L);
        return command;
    }

    private ProductVariantSpecRecord scopeRecord() {
        ProductVariantSpecRecord row = new ProductVariantSpecRecord();
        row.setVariantId(53001L);
        row.setPartnerSku("MILKYWAYA05");
        row.setChildSku("N701");
        row.setSizeEn("One Size");
        return row;
    }
}
