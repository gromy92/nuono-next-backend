package com.nuono.next.product;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
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
        command.setVariantId(scopedVariant.getVariantId());
        command.setId(mapper.nextProductVariantSpecId());
        command.setBatteryMagneticType(batteryMagneticType);
        command.setLiquidPowderType(liquidPowderType);
        mapper.upsertProductVariantSpec(command);

        ProductVariantSpecRecord saved = new ProductVariantSpecRecord();
        saved.setSpecId(command.getId());
        saved.setVariantId(scopedVariant.getVariantId());
        saved.setPartnerSku(scopedVariant.getPartnerSku());
        saved.setChildSku(scopedVariant.getChildSku());
        saved.setSizeEn(scopedVariant.getSizeEn());
        saved.setSizeAr(scopedVariant.getSizeAr());
        saved.setConfirmedBy(command.getOperatorUserId());
        saved.setConfirmedAt(LocalDateTime.now());
        copyCommand(command, saved);
        return toView(saved);
    }

    private void requireScope(Long ownerUserId, String storeCode, String skuParent) {
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("老板上下文不能为空");
        }
        if (!StringUtils.hasText(storeCode)) {
            throw new IllegalArgumentException("店铺不能为空");
        }
        if (!StringUtils.hasText(skuParent)) {
            throw new IllegalArgumentException("商品 SKU Parent 不能为空");
        }
    }

    private void validatePositive(BigDecimal value, String message) {
        if (value != null && value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private void copyCommand(ProductVariantSpecCommand command, ProductVariantSpecRecord target) {
        target.setProductLengthCm(command.getProductLengthCm());
        target.setProductWidthCm(command.getProductWidthCm());
        target.setProductHeightCm(command.getProductHeightCm());
        target.setProductWeightG(command.getProductWeightG());
        target.setCartonLengthCm(command.getCartonLengthCm());
        target.setCartonWidthCm(command.getCartonWidthCm());
        target.setCartonHeightCm(command.getCartonHeightCm());
        target.setCartonWeightKg(command.getCartonWeightKg());
        target.setCartonQuantity(command.getCartonQuantity());
        target.setBatteryMagneticType(command.getBatteryMagneticType());
        target.setLiquidPowderType(command.getLiquidPowderType());
    }

    private ProductVariantSpecView toView(ProductVariantSpecRecord row) {
        ProductVariantSpecView view = new ProductVariantSpecView();
        view.setVariantId(row.getVariantId());
        view.setPartnerSku(row.getPartnerSku());
        view.setChildSku(row.getChildSku());
        view.setSizeEn(row.getSizeEn());
        view.setSizeAr(row.getSizeAr());
        view.setProductLengthCm(row.getProductLengthCm());
        view.setProductWidthCm(row.getProductWidthCm());
        view.setProductHeightCm(row.getProductHeightCm());
        view.setProductWeightG(row.getProductWeightG());
        view.setCartonLengthCm(row.getCartonLengthCm());
        view.setCartonWidthCm(row.getCartonWidthCm());
        view.setCartonHeightCm(row.getCartonHeightCm());
        view.setCartonWeightKg(row.getCartonWeightKg());
        view.setCartonQuantity(row.getCartonQuantity());
        view.setBatteryMagneticType(row.getBatteryMagneticType() == null
                ? ProductVariantSpecLogisticsType.UNKNOWN : row.getBatteryMagneticType());
        view.setLiquidPowderType(row.getLiquidPowderType() == null
                ? ProductVariantSpecLogisticsType.UNKNOWN : row.getLiquidPowderType());
        view.setConfirmedAt(row.getConfirmedAt());
        view.setConfirmedBy(row.getConfirmedBy());
        applyCompleteness(view, row.getSpecId() != null);
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
        if (view.getProductLengthCm() == null || view.getProductWidthCm() == null || view.getProductHeightCm() == null
                || view.getCartonLengthCm() == null || view.getCartonWidthCm() == null || view.getCartonHeightCm() == null) {
            missing.add("dimensions");
        }
        if (view.getProductWeightG() == null || view.getCartonWeightKg() == null) {
            missing.add("weight");
        }
        if (view.getCartonQuantity() == null) {
            missing.add("carton_quantity");
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
        } else if (missing.contains("carton_quantity")) {
            view.setCompletenessStatus("missing_carton_quantity");
        } else {
            view.setCompletenessStatus("logistics_attribute_unknown");
        }
    }
}
