package com.nuono.next.product;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class ProductVariantSpecService {

    private final ProductManagementMapper mapper;

    public ProductVariantSpecService(ProductManagementMapper mapper) {
        this.mapper = mapper;
    }

    public ProductVariantSpecListView list(ProductVariantSpecListCommand command) {
        requireScope(command.getOwnerUserId(), command.getStoreCode(), command.getSkuParent());
        ProductVariantSpecListView view = new ProductVariantSpecListView();
        view.setReady(true);
        view.setOwnerUserId(command.getOwnerUserId());
        view.setStoreCode(command.getStoreCode());
        view.setSkuParent(command.getSkuParent());
        view.setItems(mapper.selectProductVariantSpecs(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSkuParent()
        ).stream().map(this::toView).collect(Collectors.toList()));
        return view;
    }

    public ProductVariantSpecOverviewView overview(ProductVariantSpecOverviewCommand command) {
        requireOwnerStore(command.getOwnerUserId(), command.getStoreCode());
        ProductVariantSpecOverviewView view = new ProductVariantSpecOverviewView();
        view.setReady(true);
        view.setOwnerUserId(command.getOwnerUserId());
        view.setStoreCode(command.getStoreCode());
        String keyword = normalizeKeyword(command.getKeyword());
        List<ProductVariantSpecView> items = mapper.selectProductVariantSpecOverview(
                command.getOwnerUserId(),
                command.getStoreCode(),
                keyword
        ).stream().map(this::toView).collect(Collectors.toList());
        Map<Long, List<ProductVariantSpecSourceView>> sourcesByVariantId =
                mapper.selectProductVariantSpecSourcesForOverview(
                        command.getOwnerUserId(),
                        command.getStoreCode(),
                        keyword
                ).stream()
                        .map(this::toSourceView)
                        .collect(Collectors.groupingBy(ProductVariantSpecSourceView::getVariantId));
        items.forEach(item -> item.setSources(sourcesByVariantId.getOrDefault(item.getVariantId(), List.of())));
        view.setItems(items);
        return view;
    }

    public ProductVariantSpecDetailView detail(Long ownerUserId, String storeCode, Long variantId) {
        requireVariantScope(ownerUserId, storeCode, variantId);
        ProductVariantSpecRecord scopedVariant = mapper.selectProductVariantForSpecByVariantId(ownerUserId, storeCode, variantId);
        if (scopedVariant == null || scopedVariant.getVariantId() == null) {
            throw new IllegalArgumentException("SKU 不属于当前店铺范围");
        }
        List<ProductVariantSpecSourceRecord> sourceRecords = mapper.selectProductVariantSpecSources(ownerUserId, storeCode, variantId);
        ProductVariantSpecDetailView view = new ProductVariantSpecDetailView();
        view.setReady(true);
        view.setOwnerUserId(ownerUserId);
        view.setStoreCode(storeCode);
        view.setVariantId(scopedVariant.getVariantId());
        view.setPartnerSku(scopedVariant.getPartnerSku());
        view.setChildSku(scopedVariant.getChildSku());
        view.setSkuParent(scopedVariant.getSkuParent());
        view.setTitle(scopedVariant.getTitle());
        view.setImageUrl(scopedVariant.getImageUrl());
        view.setEffectiveSourceId(scopedVariant.getEffectiveSourceId());
        view.setEffectiveSourceType(scopedVariant.getEffectiveSourceType());
        view.setSources(sourceRecords.stream().map(this::toSourceView).collect(Collectors.toList()));
        sourceRecords.stream()
                .filter(source -> source.getSourceId() != null && source.getSourceId().equals(scopedVariant.getEffectiveSourceId()))
                .findFirst()
                .map(source -> toEffectiveView(scopedVariant, source))
                .ifPresent(view::setEffectiveSpec);
        return view;
    }

    public ProductVariantSpecView save(ProductVariantSpecCommand command) {
        requireScope(command.getOwnerUserId(), command.getStoreCode(), command.getSkuParent());
        if (!StringUtils.hasText(command.getPartnerSku())) {
            throw new IllegalArgumentException("Partner SKU 不能为空");
        }
        if (command.getOperatorUserId() == null || command.getOperatorUserId() <= 0) {
            throw new IllegalArgumentException("操作人不能为空");
        }
        validatePositive(command.getProductLengthCm(), "产品长必须大于 0");
        validatePositive(command.getProductWidthCm(), "产品宽必须大于 0");
        validatePositive(command.getProductHeightCm(), "产品高必须大于 0");
        validatePositive(command.getProductWeightG(), "产品重量必须大于 0");
        validatePositive(command.getCartonLengthCm(), "外箱长必须大于 0");
        validatePositive(command.getCartonWidthCm(), "外箱宽必须大于 0");
        validatePositive(command.getCartonHeightCm(), "外箱高必须大于 0");
        validatePositive(command.getCartonWeightKg(), "外箱重量必须大于 0");
        if (command.getCartonQuantity() != null && command.getCartonQuantity() <= 0) {
            throw new IllegalArgumentException("箱装数必须大于 0");
        }
        String batteryMagneticType = ProductVariantSpecLogisticsType.normalizeBatteryMagnetic(command.getBatteryMagneticType());
        String liquidPowderType = ProductVariantSpecLogisticsType.normalizeLiquidPowder(command.getLiquidPowderType());
        ProductVariantSpecRecord scopedVariant = mapper.selectProductVariantForSpec(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSkuParent(),
                command.getPartnerSku(),
                command.getChildSku()
        );
        if (scopedVariant == null || scopedVariant.getVariantId() == null) {
            throw new IllegalArgumentException("SKU 不属于当前商品或店铺范围");
        }
        ProductVariantSpecSourceCommand sourceCommand = new ProductVariantSpecSourceCommand();
        sourceCommand.setOwnerUserId(command.getOwnerUserId());
        sourceCommand.setStoreCode(command.getStoreCode());
        sourceCommand.setVariantId(scopedVariant.getVariantId());
        sourceCommand.setSourceType(ProductVariantSpecSourceType.WAREHOUSE);
        sourceCommand.setProductLengthCm(command.getProductLengthCm());
        sourceCommand.setProductWidthCm(command.getProductWidthCm());
        sourceCommand.setProductHeightCm(command.getProductHeightCm());
        sourceCommand.setProductWeightG(command.getProductWeightG());
        sourceCommand.setCartonLengthCm(command.getCartonLengthCm());
        sourceCommand.setCartonWidthCm(command.getCartonWidthCm());
        sourceCommand.setCartonHeightCm(command.getCartonHeightCm());
        sourceCommand.setCartonWeightKg(command.getCartonWeightKg());
        sourceCommand.setCartonQuantity(command.getCartonQuantity());
        sourceCommand.setCartonSourceType(resolveWarehouseCartonSource(command));
        sourceCommand.setBatteryMagneticType(batteryMagneticType);
        sourceCommand.setLiquidPowderType(liquidPowderType);
        sourceCommand.setSourceRecordedAt(LocalDateTime.now());
        sourceCommand.setOperatorUserId(command.getOperatorUserId());
        ProductVariantSpecSourceView source = saveSource(sourceCommand);
        ProductVariantSpecDetailView detail = selectEffectiveSource(
                command.getOwnerUserId(),
                command.getStoreCode(),
                scopedVariant.getVariantId(),
                source.getSourceId(),
                command.getOperatorUserId()
        );
        return detail.getEffectiveSpec();
    }

    public ProductVariantSpecSourceView saveSource(ProductVariantSpecSourceCommand command) {
        requireVariantScope(command.getOwnerUserId(), command.getStoreCode(), command.getVariantId());
        if (command.getOperatorUserId() == null || command.getOperatorUserId() <= 0) {
            throw new IllegalArgumentException("操作人不能为空");
        }
        ProductVariantSpecRecord scopedVariant = mapper.selectProductVariantForSpecByVariantId(
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getVariantId()
        );
        if (scopedVariant == null || scopedVariant.getVariantId() == null) {
            throw new IllegalArgumentException("SKU 不属于当前店铺范围");
        }
        normalizeAndValidateSourceCommand(command);
        command.setId(mapper.nextProductVariantSpecSourceId());
        command.setSourceRecordedAt(LocalDateTime.now());
        mapper.upsertProductVariantSpecSource(command);
        return mapper.selectProductVariantSpecSources(command.getOwnerUserId(), command.getStoreCode(), command.getVariantId())
                .stream()
                .filter(source -> command.getSourceType().equals(source.getSourceType()))
                .findFirst()
                .map(this::toSourceView)
                .orElseThrow(() -> new IllegalStateException("来源规格保存后读取失败"));
    }

    public ProductVariantSpecDetailView selectEffectiveSource(
            Long ownerUserId,
            String storeCode,
            Long variantId,
            Long sourceId,
            Long operatorUserId
    ) {
        requireVariantScope(ownerUserId, storeCode, variantId);
        if (sourceId == null || sourceId <= 0) {
            throw new IllegalArgumentException("生效来源不能为空");
        }
        if (operatorUserId == null || operatorUserId <= 0) {
            throw new IllegalArgumentException("操作人不能为空");
        }
        ProductVariantSpecSourceRecord source = mapper.selectProductVariantSpecSourceForScope(ownerUserId, storeCode, variantId, sourceId);
        if (source == null || source.getSourceId() == null) {
            throw new IllegalArgumentException("规格来源不属于当前 SKU");
        }
        String sourceType = ProductVariantSpecSourceType.normalizeEffective(source.getSourceType());
        mapper.upsertProductVariantSpecEffectiveSource(
                mapper.nextProductVariantSpecId(),
                variantId,
                source.getSourceId(),
                sourceType,
                operatorUserId
        );
        return detail(ownerUserId, storeCode, variantId);
    }

    private void requireScope(Long ownerUserId, String storeCode, String skuParent) {
        requireOwnerStore(ownerUserId, storeCode);
        if (!StringUtils.hasText(skuParent)) {
            throw new IllegalArgumentException("商品 SKU Parent 不能为空");
        }
    }

    private void requireOwnerStore(Long ownerUserId, String storeCode) {
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("老板上下文不能为空");
        }
        if (!StringUtils.hasText(storeCode)) {
            throw new IllegalArgumentException("店铺不能为空");
        }
    }

    private void requireVariantScope(Long ownerUserId, String storeCode, Long variantId) {
        requireOwnerStore(ownerUserId, storeCode);
        if (variantId == null || variantId <= 0) {
            throw new IllegalArgumentException("SKU 不能为空");
        }
    }

    private String normalizeKeyword(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }

    private void validatePositive(BigDecimal value, String message) {
        if (value != null && value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private String resolveWarehouseCartonSource(ProductVariantSpecCommand command) {
        boolean hasCarton = command.getCartonLengthCm() != null
                || command.getCartonWidthCm() != null
                || command.getCartonHeightCm() != null
                || command.getCartonWeightKg() != null
                || command.getCartonQuantity() != null;
        return hasCarton ? ProductVariantSpecCartonSourceType.WAREHOUSE_MEASURED : null;
    }

    private void normalizeAndValidateSourceCommand(ProductVariantSpecSourceCommand command) {
        String sourceType = ProductVariantSpecSourceType.normalize(command.getSourceType());
        command.setSourceType(sourceType);
        validatePositive(command.getProductLengthCm(), "产品长必须大于 0");
        validatePositive(command.getProductWidthCm(), "产品宽必须大于 0");
        validatePositive(command.getProductHeightCm(), "产品高必须大于 0");
        validatePositive(command.getProductWeightG(), "产品重量必须大于 0");
        validatePositive(command.getCartonLengthCm(), "外箱长必须大于 0");
        validatePositive(command.getCartonWidthCm(), "外箱宽必须大于 0");
        validatePositive(command.getCartonHeightCm(), "外箱高必须大于 0");
        validatePositive(command.getCartonWeightKg(), "外箱重量必须大于 0");
        if (command.getCartonQuantity() != null && command.getCartonQuantity() <= 0) {
            throw new IllegalArgumentException("箱装数必须大于 0");
        }
        if (ProductVariantSpecSourceType.isNoonOfficial(sourceType)) {
            command.setCartonLengthCm(null);
            command.setCartonWidthCm(null);
            command.setCartonHeightCm(null);
            command.setCartonWeightKg(null);
            command.setCartonQuantity(null);
            command.setCartonSourceType(ProductVariantSpecCartonSourceType.NONE);
            command.setBatteryMagneticType(ProductVariantSpecLogisticsType.UNKNOWN);
            command.setLiquidPowderType(ProductVariantSpecLogisticsType.UNKNOWN);
            return;
        }
        String cartonSourceType = ProductVariantSpecCartonSourceType.normalize(command.getCartonSourceType());
        cartonSourceType = resolveCartonSourceAndDerivedValues(sourceType, command, cartonSourceType);
        if (ProductVariantSpecCartonSourceType.FACTORY_CARTON.equals(cartonSourceType)
                && !ProductVariantSpecSourceType.ALI1688.equals(sourceType)) {
            throw new IllegalArgumentException("出厂箱规只能来自 1688 规格");
        }
        if (ProductVariantSpecCartonSourceType.DERIVED_FROM_WAREHOUSE.equals(cartonSourceType)
                && !ProductVariantSpecSourceType.WAREHOUSE.equals(sourceType)) {
            throw new IllegalArgumentException("推导箱规只能来自仓管测量");
        }
        command.setCartonSourceType(cartonSourceType);
        command.setBatteryMagneticType(ProductVariantSpecLogisticsType.normalizeBatteryMagnetic(command.getBatteryMagneticType()));
        command.setLiquidPowderType(ProductVariantSpecLogisticsType.normalizeLiquidPowder(command.getLiquidPowderType()));
    }

    private String resolveCartonSourceAndDerivedValues(
            String sourceType,
            ProductVariantSpecSourceCommand command,
            String requestedCartonSourceType
    ) {
        boolean hasAnyCartonValue = command.getCartonLengthCm() != null
                || command.getCartonWidthCm() != null
                || command.getCartonHeightCm() != null
                || command.getCartonWeightKg() != null
                || command.getCartonQuantity() != null;
        if (hasAnyCartonValue) {
            if (ProductVariantSpecCartonSourceType.NONE.equals(requestedCartonSourceType)) {
                return ProductVariantSpecSourceType.ALI1688.equals(sourceType)
                        ? ProductVariantSpecCartonSourceType.FACTORY_CARTON
                        : ProductVariantSpecCartonSourceType.WAREHOUSE_MEASURED;
            }
            return requestedCartonSourceType;
        }
        if (ProductVariantSpecSourceType.WAREHOUSE.equals(sourceType) && canDeriveSingleUnitCarton(command)) {
            command.setCartonLengthCm(command.getProductLengthCm());
            command.setCartonWidthCm(command.getProductWidthCm());
            command.setCartonHeightCm(command.getProductHeightCm());
            command.setCartonWeightKg(command.getProductWeightG().divide(new BigDecimal("1000"), 3, RoundingMode.HALF_UP));
            command.setCartonQuantity(1);
            return ProductVariantSpecCartonSourceType.DERIVED_FROM_WAREHOUSE;
        }
        return ProductVariantSpecCartonSourceType.NONE;
    }

    private boolean canDeriveSingleUnitCarton(ProductVariantSpecSourceCommand command) {
        return command.getProductLengthCm() != null
                && command.getProductWidthCm() != null
                && command.getProductHeightCm() != null
                && command.getProductWeightG() != null;
    }

    private ProductVariantSpecView toView(ProductVariantSpecRecord row) {
        ProductVariantSpecView view = new ProductVariantSpecView();
        view.setStoreCode(row.getStoreCode());
        view.setSkuParent(row.getSkuParent());
        view.setTitle(row.getTitle());
        view.setImageUrl(row.getImageUrl());
        view.setVariantId(row.getVariantId());
        view.setPartnerSku(row.getPartnerSku());
        view.setChildSku(row.getChildSku());
        view.setSizeEn(row.getSizeEn());
        view.setSizeAr(row.getSizeAr());
        view.setEffectiveSourceId(row.getEffectiveSourceId());
        view.setEffectiveSourceType(row.getEffectiveSourceType());
        view.setProductLengthCm(row.getProductLengthCm());
        view.setProductWidthCm(row.getProductWidthCm());
        view.setProductHeightCm(row.getProductHeightCm());
        view.setProductWeightG(row.getProductWeightG());
        view.setCartonLengthCm(row.getCartonLengthCm());
        view.setCartonWidthCm(row.getCartonWidthCm());
        view.setCartonHeightCm(row.getCartonHeightCm());
        view.setCartonWeightKg(row.getCartonWeightKg());
        view.setCartonQuantity(row.getCartonQuantity());
        view.setCartonSourceType(row.getCartonSourceType() == null
                ? ProductVariantSpecCartonSourceType.NONE : row.getCartonSourceType());
        view.setBatteryMagneticType(row.getBatteryMagneticType() == null
                ? ProductVariantSpecLogisticsType.UNKNOWN : row.getBatteryMagneticType());
        view.setLiquidPowderType(row.getLiquidPowderType() == null
                ? ProductVariantSpecLogisticsType.UNKNOWN : row.getLiquidPowderType());
        view.setConfirmedAt(row.getConfirmedAt());
        view.setConfirmedBy(row.getConfirmedBy());
        applyCompleteness(view, row.getSpecId() != null || row.getEffectiveSourceId() != null);
        return view;
    }

    private ProductVariantSpecView toEffectiveView(ProductVariantSpecRecord variant, ProductVariantSpecSourceRecord source) {
        ProductVariantSpecRecord record = new ProductVariantSpecRecord();
        record.setSpecId(variant.getSpecId());
        record.setEffectiveSourceId(source.getSourceId());
        record.setEffectiveSourceType(source.getSourceType());
        record.setStoreCode(variant.getStoreCode());
        record.setSkuParent(variant.getSkuParent());
        record.setTitle(variant.getTitle());
        record.setImageUrl(variant.getImageUrl());
        record.setVariantId(variant.getVariantId());
        record.setPartnerSku(variant.getPartnerSku());
        record.setChildSku(variant.getChildSku());
        record.setSizeEn(variant.getSizeEn());
        record.setSizeAr(variant.getSizeAr());
        record.setProductLengthCm(source.getProductLengthCm());
        record.setProductWidthCm(source.getProductWidthCm());
        record.setProductHeightCm(source.getProductHeightCm());
        record.setProductWeightG(source.getProductWeightG());
        record.setCartonLengthCm(source.getCartonLengthCm());
        record.setCartonWidthCm(source.getCartonWidthCm());
        record.setCartonHeightCm(source.getCartonHeightCm());
        record.setCartonWeightKg(source.getCartonWeightKg());
        record.setCartonQuantity(source.getCartonQuantity());
        record.setCartonSourceType(source.getCartonSourceType());
        record.setBatteryMagneticType(source.getBatteryMagneticType());
        record.setLiquidPowderType(source.getLiquidPowderType());
        record.setSourceType(source.getSourceType());
        record.setConfirmedAt(source.getConfirmedAt());
        record.setConfirmedBy(source.getConfirmedBy());
        return toView(record);
    }

    private ProductVariantSpecSourceView toSourceView(ProductVariantSpecSourceRecord row) {
        ProductVariantSpecSourceView view = new ProductVariantSpecSourceView();
        view.setSourceId(row.getSourceId());
        view.setVariantId(row.getVariantId());
        view.setSourceType(row.getSourceType());
        view.setProductLengthCm(row.getProductLengthCm());
        view.setProductWidthCm(row.getProductWidthCm());
        view.setProductHeightCm(row.getProductHeightCm());
        view.setProductWeightG(row.getProductWeightG());
        view.setCartonLengthCm(row.getCartonLengthCm());
        view.setCartonWidthCm(row.getCartonWidthCm());
        view.setCartonHeightCm(row.getCartonHeightCm());
        view.setCartonWeightKg(row.getCartonWeightKg());
        view.setCartonQuantity(row.getCartonQuantity());
        view.setCartonSourceType(row.getCartonSourceType() == null
                ? ProductVariantSpecCartonSourceType.NONE : row.getCartonSourceType());
        view.setBatteryMagneticType(row.getBatteryMagneticType() == null
                ? ProductVariantSpecLogisticsType.UNKNOWN : row.getBatteryMagneticType());
        view.setLiquidPowderType(row.getLiquidPowderType() == null
                ? ProductVariantSpecLogisticsType.UNKNOWN : row.getLiquidPowderType());
        view.setSourceRecordedAt(row.getSourceRecordedAt());
        view.setConfirmedAt(row.getConfirmedAt());
        view.setConfirmedBy(row.getConfirmedBy());
        view.setUpdatedBy(row.getUpdatedBy());
        view.setGmtUpdated(row.getGmtUpdated());
        return view;
    }

    private void applyCompleteness(ProductVariantSpecView view, boolean specExists) {
        List<String> missing = new ArrayList<>();
        if (!specExists) {
            missing.add("spec_not_found");
            view.setCompletenessStatus("not_found");
            view.setMissingFields(missing);
            return;
        }
        if (view.getProductLengthCm() == null || view.getProductWidthCm() == null || view.getProductHeightCm() == null) {
            missing.add("dimensions");
        }
        if (view.getProductWeightG() == null) {
            missing.add("weight");
        }
        if (ProductVariantSpecLogisticsType.UNKNOWN.equals(view.getBatteryMagneticType())
                || ProductVariantSpecLogisticsType.UNKNOWN.equals(view.getLiquidPowderType())) {
            missing.add("logistics_attribute");
        }
        view.setMissingFields(missing);
        if (missing.isEmpty()) {
            view.setCompletenessStatus("ready");
        } else if (missing.contains("dimensions")) {
            view.setCompletenessStatus("missing_dimensions");
        } else if (missing.contains("weight")) {
            view.setCompletenessStatus("missing_weight");
        } else {
            view.setCompletenessStatus("logistics_attribute_unknown");
        }
    }
}
