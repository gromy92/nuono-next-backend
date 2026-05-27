package com.nuono.next.infrastructure.mapper;

import com.nuono.next.procurement.ProcurementLogisticsRequirementRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProcurementLogisticsRequirementMapper {

    @Select("SELECT COALESCE(MAX(id), 96000) + 1 FROM procurement_logistics_requirement")
    Long nextRequirementId();

    @Select({
            "SELECT COUNT(1)",
            "FROM procurement_demand_item di",
            "JOIN procurement_order po ON po.id = di.order_id AND po.is_deleted = b'0'",
            "WHERE di.id = #{demandItemId}",
            "  AND po.owner_user_id = #{ownerUserId}",
            "  AND di.is_deleted = b'0'"
    })
    int countOwnedDemandItem(
            @Param("ownerUserId") Long ownerUserId,
            @Param("demandItemId") Long demandItemId
    );

    @Select({
            "SELECT",
            "  id,",
            "  owner_user_id AS ownerUserId,",
            "  demand_item_id AS demandItemId,",
            "  transport_mode AS transportMode,",
            "  destination_country AS destinationCountry,",
            "  destination_node AS destinationNode,",
            "  origin_node AS originNode,",
            "  package_length_cm AS packageLengthCm,",
            "  package_width_cm AS packageWidthCm,",
            "  package_height_cm AS packageHeightCm,",
            "  unit_weight_grams AS unitWeightGrams,",
            "  quantity,",
            "  cargo_attributes AS cargoAttributes,",
            "  status",
            "FROM procurement_logistics_requirement",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND demand_item_id = #{demandItemId}",
            "  AND is_deleted = b'0'",
            "LIMIT 1"
    })
    ProcurementLogisticsRequirementRow selectRequirement(
            @Param("ownerUserId") Long ownerUserId,
            @Param("demandItemId") Long demandItemId
    );

    @Insert({
            "INSERT INTO procurement_logistics_requirement (",
            "  id, owner_user_id, demand_item_id, transport_mode, destination_country, destination_node, origin_node,",
            "  package_length_cm, package_width_cm, package_height_cm, unit_weight_grams, quantity, cargo_attributes,",
            "  status, created_by, updated_by, gmt_create, gmt_updated",
            ") VALUES (",
            "  #{row.id}, #{row.ownerUserId}, #{row.demandItemId}, #{row.transportMode}, #{row.destinationCountry},",
            "  #{row.destinationNode}, #{row.originNode}, #{row.packageLengthCm}, #{row.packageWidthCm},",
            "  #{row.packageHeightCm}, #{row.unitWeightGrams}, #{row.quantity}, #{row.cargoAttributes},",
            "  #{row.status}, #{operatorUserId}, #{operatorUserId}, NOW(), NOW()",
            ") ON DUPLICATE KEY UPDATE",
            "  transport_mode = VALUES(transport_mode),",
            "  destination_country = VALUES(destination_country),",
            "  destination_node = VALUES(destination_node),",
            "  origin_node = VALUES(origin_node),",
            "  package_length_cm = VALUES(package_length_cm),",
            "  package_width_cm = VALUES(package_width_cm),",
            "  package_height_cm = VALUES(package_height_cm),",
            "  unit_weight_grams = VALUES(unit_weight_grams),",
            "  quantity = VALUES(quantity),",
            "  cargo_attributes = VALUES(cargo_attributes),",
            "  status = VALUES(status),",
            "  is_deleted = b'0',",
            "  updated_by = VALUES(updated_by),",
            "  gmt_updated = NOW()"
    })
    int upsertRequirement(
            @Param("row") ProcurementLogisticsRequirementRow row,
            @Param("operatorUserId") Long operatorUserId
    );
}
