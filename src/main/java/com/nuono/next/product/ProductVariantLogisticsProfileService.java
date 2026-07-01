package com.nuono.next.product;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class ProductVariantLogisticsProfileService {

    private static final Set<String> PROFILE_STATUS = Set.of("needs_review", "confirmed");
    private static final Set<String> COMMON_STATUS = Set.of("unknown", "none");
    private static final Set<String> BATTERY_ELECTRIC_TYPE = Set.of("unknown", "none", "battery_or_electric");
    private static final Set<String> BATTERY_TYPE = Set.of("unknown", "none", "battery_equipment");
    private static final Set<String> MAGNETIC_TYPE = Set.of("unknown", "none", "magnetic");
    private static final Set<String> LIQUID_TYPE = Set.of("unknown", "none", "liquid");
    private static final Set<String> POWDER_TYPE = Set.of("unknown", "none", "powder");
    private static final Set<String> LIQUID_POWDER_TYPE = Set.of("unknown", "none", "liquid", "powder", "liquid_and_powder");
    private static final Set<String> ELECTRIC_TYPE = Set.of("unknown", "none", "battery_equipment", "electric_equipment_review");
    private static final Set<String> PLUG_TYPE = Set.of("unknown", "none", "none_or_usb_review", "plug_required_review");
    private static final Set<String> DOC_STATUS = Set.of("unknown", "not_applicable", "required_review", "provided", "confirmed");
    private static final Set<String> RISK_TYPE = Set.of(
            "unknown", "none",
            "brand_reference_review",
            "food_contact_review",
            "medical_review",
            "cosmetic_review",
            "wireless_camera_gps_review",
            "passive_nfc_rfid",
            "laser_review",
            "blade_tool_review",
            "cultural_restriction_review",
            "wooden_material_review"
    );

    private final ProductManagementMapper mapper;

    public ProductVariantLogisticsProfileService(ProductManagementMapper mapper) {
        this.mapper = mapper;
    }

    public ProductVariantLogisticsProfileListView list(Long ownerUserId, String storeCode, String skuParent) {
        requireScope(ownerUserId, storeCode, skuParent);
        ProductVariantLogisticsProfileListView view = new ProductVariantLogisticsProfileListView();
        view.ready = true;
        view.ownerUserId = ownerUserId;
        view.storeCode = storeCode;
        view.skuParent = skuParent;
        view.items = mapper.selectProductVariantLogisticsProfiles(ownerUserId, storeCode, skuParent)
                .stream()
                .map(this::normalizeView)
                .collect(Collectors.toList());
        return view;
    }

    public ProductVariantLogisticsProfileView detailByPsku(Long ownerUserId, String storeCode, String partnerSku) {
        Long variantId = resolveVariantId(ownerUserId, storeCode, partnerSku, null);
        requireVariantScope(ownerUserId, storeCode, variantId);
        ProductVariantLogisticsProfileView saved = mapper.selectProductVariantLogisticsProfile(
                ownerUserId,
                storeCode,
                variantId
        );
        if (saved != null && saved.variantId != null) {
            return normalizeView(saved);
        }
        ProductVariantSpecRecord scopedVariant = mapper.selectProductVariantForSpecByVariantId(
                ownerUserId,
                storeCode,
                variantId
        );
        if (scopedVariant == null || scopedVariant.getVariantId() == null) {
            throw new IllegalArgumentException("SKU 不属于当前店铺范围");
        }
        ProductVariantLogisticsProfileView view = new ProductVariantLogisticsProfileView();
        view.storeCode = scopedVariant.getStoreCode();
        view.skuParent = scopedVariant.getSkuParent();
        view.title = scopedVariant.getTitle();
        view.imageUrl = scopedVariant.getImageUrl();
        view.variantId = scopedVariant.getVariantId();
        view.partnerSku = scopedVariant.getPartnerSku();
        view.childSku = scopedVariant.getChildSku();
        view.sizeEn = scopedVariant.getSizeEn();
        view.sizeAr = scopedVariant.getSizeAr();
        return normalizeView(view);
    }

    public ProductVariantLogisticsProfileView save(ProductVariantLogisticsProfileCommand command) {
        requireVariantScope(command.ownerUserId, command.storeCode, command.variantId);
        if (command.operatorUserId == null || command.operatorUserId <= 0) {
            throw new IllegalArgumentException("操作人不能为空");
        }
        ProductVariantSpecRecord scopedVariant = mapper.selectProductVariantForSpecByVariantId(
                command.ownerUserId,
                command.storeCode,
                command.variantId
        );
        if (scopedVariant == null || scopedVariant.getVariantId() == null) {
            throw new IllegalArgumentException("SKU 不属于当前店铺范围");
        }
        normalizeCommand(command);
        command.id = mapper.nextProductVariantLogisticsProfileId();
        mapper.upsertProductVariantLogisticsProfile(command);
        ProductVariantLogisticsProfileView saved = mapper.selectProductVariantLogisticsProfile(
                command.ownerUserId,
                command.storeCode,
                command.variantId
        );
        if (saved == null || saved.variantId == null) {
            throw new IllegalStateException("物流属性保存后读取失败");
        }
        return normalizeView(saved);
    }

    public ProductVariantLogisticsProfileView saveByPsku(ProductVariantLogisticsProfileCommand command) {
        if (command == null) {
            command = new ProductVariantLogisticsProfileCommand();
        }
        command.variantId = resolveVariantId(
                command.ownerUserId,
                command.storeCode,
                command.partnerSku,
                command.variantId
        );
        return save(command);
    }

    Long resolveVariantId(Long ownerUserId, String storeCode, String partnerSku, Long variantId) {
        requireOwnerStore(ownerUserId, storeCode);
        if (StringUtils.hasText(partnerSku)) {
            Long logicalStoreId = mapper.selectLogicalStoreIdByOwnerStoreCode(ownerUserId, storeCode.trim());
            if (logicalStoreId != null) {
                Long resolved = mapper.selectProductVariantIdByStorePartnerSku(logicalStoreId, partnerSku.trim());
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        return variantId;
    }

    private void requireScope(Long ownerUserId, String storeCode, String skuParent) {
        requireOwnerStore(ownerUserId, storeCode);
        if (!StringUtils.hasText(skuParent)) {
            throw new IllegalArgumentException("商品 SKU Parent 不能为空");
        }
    }

    private void requireVariantScope(Long ownerUserId, String storeCode, Long variantId) {
        requireOwnerStore(ownerUserId, storeCode);
        if (variantId == null || variantId <= 0) {
            throw new IllegalArgumentException("SKU 不能为空");
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

    private void normalizeCommand(ProductVariantLogisticsProfileCommand command) {
        command.profileStatus = normalize(command.profileStatus, PROFILE_STATUS, "needs_review", "物流属性状态不合法");
        command.batteryType = normalize(
                command.batteryType,
                BATTERY_TYPE,
                deriveBatteryType(command.batteryElectricType),
                "带电属性不合法"
        );
        command.magneticType = normalize(command.magneticType, MAGNETIC_TYPE, "unknown", "磁性属性不合法");
        command.liquidType = normalize(
                command.liquidType,
                LIQUID_TYPE,
                deriveLiquidType(command.liquidPowderType),
                "液体属性不合法"
        );
        command.powderType = normalize(
                command.powderType,
                POWDER_TYPE,
                derivePowderType(command.liquidPowderType),
                "粉末属性不合法"
        );
        command.liquidPowderType = deriveLiquidPowderType(command.liquidType, command.powderType);
        command.electricType = normalize(
                command.electricType,
                ELECTRIC_TYPE,
                deriveElectricType(command.batteryElectricType),
                "电器属性不合法"
        );
        command.batteryElectricType = deriveBatteryElectricType(command.batteryType, command.electricType);
        command.plugType = normalize(command.plugType, PLUG_TYPE, "unknown", "插头属性不合法");
        command.voltageCompatibleType = normalize(command.voltageCompatibleType, COMMON_STATUS, "unknown", "电压属性不合法");
        command.madeInChinaLabelStatus = normalize(command.madeInChinaLabelStatus, DOC_STATUS, "unknown", "Made in China 标签状态不合法");
        command.msdsStatus = normalize(command.msdsStatus, DOC_STATUS, "unknown", "MSDS 状态不合法");
        command.seaTransportReportStatus = normalize(command.seaTransportReportStatus, DOC_STATUS, "unknown", "海运鉴定状态不合法");
        command.brandRiskType = normalize(command.brandRiskType, RISK_TYPE, "unknown", "品牌风险属性不合法");
        command.foodContactType = normalize(command.foodContactType, RISK_TYPE, "unknown", "食品接触属性不合法");
        command.medicalType = normalize(command.medicalType, RISK_TYPE, "unknown", "医疗属性不合法");
        command.cosmeticType = normalize(command.cosmeticType, RISK_TYPE, "unknown", "化妆品属性不合法");
        command.wirelessCameraGpsType = normalize(command.wirelessCameraGpsType, RISK_TYPE, "unknown", "无线/摄像/GPS 属性不合法");
        command.laserType = normalize(command.laserType, RISK_TYPE, "unknown", "激光属性不合法");
        command.bladeWeaponType = normalize(command.bladeWeaponType, RISK_TYPE, "unknown", "刀具属性不合法");
        command.culturalRestrictionType = normalize(command.culturalRestrictionType, RISK_TYPE, "unknown", "文化限制属性不合法");
        command.woodenMaterialType = normalize(command.woodenMaterialType, RISK_TYPE, "unknown", "木制属性不合法");
        if (command.manualConfirmRequired == null) {
            command.manualConfirmRequired = !"confirmed".equals(command.profileStatus);
        }
    }

    private String normalize(String value, Set<String> allowed, String fallback, String errorMessage) {
        String normalized = StringUtils.hasText(value) ? value.trim() : fallback;
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(errorMessage + "：" + normalized);
        }
        return normalized;
    }

    private String deriveBatteryElectricType(String batteryType, String electricType) {
        String battery = StringUtils.hasText(batteryType) ? batteryType.trim() : "unknown";
        String electric = StringUtils.hasText(electricType) ? electricType.trim() : "unknown";
        if ("battery_equipment".equals(battery)
                || "battery_equipment".equals(electric)
                || "electric_equipment_review".equals(electric)) {
            return "battery_or_electric";
        }
        if ("none".equals(battery) && "none".equals(electric)) {
            return "none";
        }
        return "unknown";
    }

    private String deriveBatteryType(String batteryElectricType) {
        if ("battery_or_electric".equals(batteryElectricType)) {
            return "battery_equipment";
        }
        if ("none".equals(batteryElectricType)) {
            return "none";
        }
        return "unknown";
    }

    private String deriveElectricType(String batteryElectricType) {
        if ("battery_or_electric".equals(batteryElectricType)) {
            return "electric_equipment_review";
        }
        if ("none".equals(batteryElectricType)) {
            return "none";
        }
        return "unknown";
    }

    private String deriveLiquidType(String liquidPowderType) {
        String value = StringUtils.hasText(liquidPowderType) ? liquidPowderType.trim() : "unknown";
        if ("liquid".equals(value) || "liquid_and_powder".equals(value)) {
            return "liquid";
        }
        if ("none".equals(value) || "powder".equals(value)) {
            return "none";
        }
        return "unknown";
    }

    private String derivePowderType(String liquidPowderType) {
        String value = StringUtils.hasText(liquidPowderType) ? liquidPowderType.trim() : "unknown";
        if ("powder".equals(value) || "liquid_and_powder".equals(value)) {
            return "powder";
        }
        if ("none".equals(value) || "liquid".equals(value)) {
            return "none";
        }
        return "unknown";
    }

    private String deriveLiquidPowderType(String liquidType, String powderType) {
        boolean hasLiquid = "liquid".equals(liquidType);
        boolean hasPowder = "powder".equals(powderType);
        if (hasLiquid && hasPowder) {
            return "liquid_and_powder";
        }
        if (hasLiquid) {
            return "liquid";
        }
        if (hasPowder) {
            return "powder";
        }
        if ("none".equals(liquidType) && "none".equals(powderType)) {
            return "none";
        }
        return "unknown";
    }

    private ProductVariantLogisticsProfileView normalizeView(ProductVariantLogisticsProfileView view) {
        if (view.profileStatus == null) {
            view.profileStatus = "needs_review";
        }
        if (!StringUtils.hasText(view.batteryElectricType) || "unknown".equals(view.batteryElectricType)) {
            view.batteryElectricType = deriveBatteryElectricType(view.batteryType, view.electricType);
        }
        if (!StringUtils.hasText(view.liquidType) || "unknown".equals(view.liquidType)) {
            view.liquidType = deriveLiquidType(view.liquidPowderType);
        }
        if (!StringUtils.hasText(view.powderType) || "unknown".equals(view.powderType)) {
            view.powderType = derivePowderType(view.liquidPowderType);
        }
        if (view.manualConfirmRequired == null) {
            view.manualConfirmRequired = true;
        }
        return view;
    }
}
