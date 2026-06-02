package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
        ProductVariantSpecSourceRecord source = sourceRecord(command);
        stubSuccessfulLegacySave(command, source);

        ProductVariantSpecView saved = service.save(command);

        assertEquals("logistics_attribute_unknown", saved.getCompletenessStatus());
        assertEquals(List.of("logistics_attribute"), saved.getMissingFields());
        verify(mapper).upsertProductVariantSpecSource(any(ProductVariantSpecSourceCommand.class));
    }

    @Test
    void saveShouldAllowPartialSpecsAndReturnMissingStatus() {
        ProductVariantSpecCommand command = validCommand();
        command.setProductLengthCm(null);
        command.setCartonQuantity(null);
        ProductVariantSpecSourceRecord source = sourceRecord(command);
        stubSuccessfulLegacySave(command, source);

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
        ProductVariantSpecSourceRecord source = sourceRecord(command);
        stubSuccessfulLegacySave(command, source);

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
        ProductVariantSpecSourceRecord source = sourceRecord(command);
        stubSuccessfulLegacySave(command, source);

        ProductVariantSpecView saved = service.save(command);

        assertEquals("ready", saved.getCompletenessStatus());
        assertEquals(List.of(), saved.getMissingFields());
    }

    @Test
    void saveShouldValidatePositiveNumbersAndUpsertWarehouseSourceThenSelectEffectiveSource() {
        ProductVariantSpecCommand command = validCommand();
        ProductVariantSpecSourceRecord source = sourceRecord(command);
        stubSuccessfulLegacySave(command, source);

        ProductVariantSpecView saved = service.save(command);

        assertEquals("ready", saved.getCompletenessStatus());
        ArgumentCaptor<ProductVariantSpecSourceCommand> captor = ArgumentCaptor.forClass(ProductVariantSpecSourceCommand.class);
        verify(mapper).upsertProductVariantSpecSource(captor.capture());
        assertEquals(53001L, captor.getValue().getVariantId());
        assertEquals(120001L, captor.getValue().getId());
        assertEquals(ProductVariantSpecSourceType.WAREHOUSE, captor.getValue().getSourceType());
        verify(mapper).upsertProductVariantSpecEffectiveSource(
                99001L,
                53001L,
                120001L,
                ProductVariantSpecSourceType.WAREHOUSE,
                10003L
        );
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
        ProductVariantSpecSourceRecord source = sourceRecord(command);
        stubSuccessfulLegacySave(command, source);

        ProductVariantSpecView saved = service.save(command);

        assertEquals(10003L, saved.getConfirmedBy());
        assertNotNull(saved.getConfirmedAt());
    }

    @Test
    void saveSourceShouldDeriveSingleUnitCartonForWarehouseWhenCartonIsMissing() {
        ProductVariantSpecSourceCommand command = validSourceCommand(ProductVariantSpecSourceType.WAREHOUSE);
        when(mapper.selectProductVariantForSpecByVariantId(10002L, "STR245027-NAE", 53001L))
                .thenReturn(scopeRecord());
        when(mapper.nextProductVariantSpecSourceId()).thenReturn(120001L);
        ProductVariantSpecSourceRecord savedSource = sourceRecord(command);
        savedSource.setCartonLengthCm(new BigDecimal("12.3"));
        savedSource.setCartonWidthCm(new BigDecimal("4.5"));
        savedSource.setCartonHeightCm(new BigDecimal("6.7"));
        savedSource.setCartonWeightKg(new BigDecimal("0.120"));
        savedSource.setCartonQuantity(1);
        savedSource.setCartonSourceType(ProductVariantSpecCartonSourceType.DERIVED_FROM_WAREHOUSE);
        when(mapper.selectProductVariantSpecSources(10002L, "STR245027-NAE", 53001L))
                .thenReturn(List.of(savedSource));

        ProductVariantSpecSourceView saved = service.saveSource(command);

        assertEquals(ProductVariantSpecCartonSourceType.DERIVED_FROM_WAREHOUSE, saved.getCartonSourceType());
        assertEquals(1, saved.getCartonQuantity());
        ArgumentCaptor<ProductVariantSpecSourceCommand> captor = ArgumentCaptor.forClass(ProductVariantSpecSourceCommand.class);
        verify(mapper).upsertProductVariantSpecSource(captor.capture());
        assertEquals(new BigDecimal("0.120"), captor.getValue().getCartonWeightKg());
    }

    @Test
    void selectEffectiveSourceShouldRejectNoonOfficialSource() {
        ProductVariantSpecSourceRecord noonSource = sourceRecord(validSourceCommand(ProductVariantSpecSourceType.NOON_OFFICIAL));
        noonSource.setSourceType(ProductVariantSpecSourceType.NOON_OFFICIAL);
        when(mapper.selectProductVariantSpecSourceForScope(10002L, "STR245027-NAE", 53001L, 120001L))
                .thenReturn(noonSource);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.selectEffectiveSource(10002L, "STR245027-NAE", 53001L, 120001L, 10003L)
        );

        assertEquals("Noon 官方测量不能设为经营生效规格", error.getMessage());
    }

    private void stubSuccessfulLegacySave(ProductVariantSpecCommand command, ProductVariantSpecSourceRecord source) {
        ProductVariantSpecRecord scopedVariant = scopeRecord();
        when(mapper.selectProductVariantForSpec(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSkuParent(),
                command.getPartnerSku(),
                command.getChildSku()
        )).thenReturn(scopedVariant);
        when(mapper.nextProductVariantSpecSourceId()).thenReturn(120001L);
        when(mapper.selectProductVariantSpecSources(command.getOwnerUserId(), command.getStoreCode(), scopedVariant.getVariantId()))
                .thenReturn(List.of(source));
        when(mapper.selectProductVariantSpecSourceForScope(
                command.getOwnerUserId(),
                command.getStoreCode(),
                scopedVariant.getVariantId(),
                source.getSourceId()
        )).thenReturn(source);
        when(mapper.nextProductVariantSpecId()).thenReturn(99001L);
        ProductVariantSpecRecord detailVariant = scopeRecord();
        detailVariant.setSpecId(99001L);
        detailVariant.setEffectiveSourceId(source.getSourceId());
        detailVariant.setEffectiveSourceType(source.getSourceType());
        when(mapper.selectProductVariantForSpecByVariantId(
                command.getOwnerUserId(),
                command.getStoreCode(),
                scopedVariant.getVariantId()
        )).thenReturn(detailVariant);
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

    private ProductVariantSpecSourceCommand validSourceCommand(String sourceType) {
        ProductVariantSpecSourceCommand command = new ProductVariantSpecSourceCommand();
        command.setOwnerUserId(10002L);
        command.setStoreCode("STR245027-NAE");
        command.setVariantId(53001L);
        command.setSourceType(sourceType);
        command.setProductLengthCm(new BigDecimal("12.3"));
        command.setProductWidthCm(new BigDecimal("4.5"));
        command.setProductHeightCm(new BigDecimal("6.7"));
        command.setProductWeightG(new BigDecimal("120"));
        command.setBatteryMagneticType("none");
        command.setLiquidPowderType("none");
        command.setOperatorUserId(10003L);
        return command;
    }

    private ProductVariantSpecRecord scopeRecord() {
        ProductVariantSpecRecord row = new ProductVariantSpecRecord();
        row.setStoreCode("STR245027-NAE");
        row.setSkuParent("MILKYWAYA05P");
        row.setTitle("Milkyway sample");
        row.setImageUrl("https://img.test/sample.jpg");
        row.setVariantId(53001L);
        row.setPartnerSku("MILKYWAYA05");
        row.setChildSku("N701");
        row.setSizeEn("One Size");
        return row;
    }

    private ProductVariantSpecSourceRecord sourceRecord(ProductVariantSpecCommand command) {
        ProductVariantSpecSourceRecord row = new ProductVariantSpecSourceRecord();
        row.setSourceId(120001L);
        row.setVariantId(53001L);
        row.setSourceType(ProductVariantSpecSourceType.WAREHOUSE);
        row.setProductLengthCm(command.getProductLengthCm());
        row.setProductWidthCm(command.getProductWidthCm());
        row.setProductHeightCm(command.getProductHeightCm());
        row.setProductWeightG(command.getProductWeightG());
        row.setCartonLengthCm(command.getCartonLengthCm());
        row.setCartonWidthCm(command.getCartonWidthCm());
        row.setCartonHeightCm(command.getCartonHeightCm());
        row.setCartonWeightKg(command.getCartonWeightKg());
        row.setCartonQuantity(command.getCartonQuantity());
        row.setCartonSourceType(command.getCartonLengthCm() == null
                ? ProductVariantSpecCartonSourceType.NONE : ProductVariantSpecCartonSourceType.WAREHOUSE_MEASURED);
        row.setBatteryMagneticType(command.getBatteryMagneticType());
        row.setLiquidPowderType(command.getLiquidPowderType());
        row.setSourceRecordedAt(LocalDateTime.of(2026, 6, 2, 10, 0));
        row.setConfirmedAt(LocalDateTime.of(2026, 6, 2, 10, 0));
        row.setConfirmedBy(command.getOperatorUserId());
        return row;
    }

    private ProductVariantSpecSourceRecord sourceRecord(ProductVariantSpecSourceCommand command) {
        ProductVariantSpecSourceRecord row = new ProductVariantSpecSourceRecord();
        row.setSourceId(120001L);
        row.setVariantId(command.getVariantId());
        row.setSourceType(command.getSourceType());
        row.setProductLengthCm(command.getProductLengthCm());
        row.setProductWidthCm(command.getProductWidthCm());
        row.setProductHeightCm(command.getProductHeightCm());
        row.setProductWeightG(command.getProductWeightG());
        row.setCartonLengthCm(command.getCartonLengthCm());
        row.setCartonWidthCm(command.getCartonWidthCm());
        row.setCartonHeightCm(command.getCartonHeightCm());
        row.setCartonWeightKg(command.getCartonWeightKg());
        row.setCartonQuantity(command.getCartonQuantity());
        row.setCartonSourceType(command.getCartonSourceType());
        row.setBatteryMagneticType(command.getBatteryMagneticType());
        row.setLiquidPowderType(command.getLiquidPowderType());
        row.setSourceRecordedAt(LocalDateTime.of(2026, 6, 2, 10, 0));
        row.setConfirmedAt(LocalDateTime.of(2026, 6, 2, 10, 0));
        row.setConfirmedBy(command.getOperatorUserId());
        return row;
    }
}
