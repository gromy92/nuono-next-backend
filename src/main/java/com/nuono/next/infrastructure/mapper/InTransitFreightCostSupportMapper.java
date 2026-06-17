package com.nuono.next.infrastructure.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.SelectKey;

public interface InTransitFreightCostSupportMapper {

    String ACTUAL_BILL_SELECT = ""
            + "SELECT id, owner_user_id, batch_id, standard_forwarder_id, forwarder_code, forwarder_name, "
            + "transport_mode, destination_code, target_site_code, source_type, source_system, bill_no, bill_status, business_occurred_at, "
            + "bill_date, paid_at, currency_code, exchange_rate_to_cny, original_total_amount, cny_total_amount, "
            + "freight_amount_cny, customs_amount_cny, storage_amount_cny, handling_amount_cny, delivery_amount_cny, "
            + "interest_amount_cny, posted_amount_cny, balance_amount_cny, raw_payload_json, created_by, updated_by "
            + "FROM in_transit_freight_actual_bill ";

    String ACTUAL_COMPONENT_SELECT = ""
            + "SELECT id, owner_user_id, actual_bill_id, batch_id, package_id, box_no, external_box_no, psku, "
            + "transport_mode, destination_code, target_site_code, raw_fee_name, standard_fee_type, "
            + "charge_quantity, charge_unit, unit_price, currency_code, "
            + "exchange_rate_to_cny, original_amount, cny_amount, quantity, measured_weight_kg, measured_volume_cbm, "
            + "volume_weight_kg, chargeable_weight_kg, allocation_basis, raw_payload_json, created_by, updated_by "
            + "FROM in_transit_freight_actual_component ";

    @Insert({
            "INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue} + 1), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE next_id = LAST_INSERT_ID(next_id + 1), gmt_updated = NOW()"
    })
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "allocatedId", before = false, resultType = Long.class)
    int allocateId(IdSequenceCommand command);

    default Long nextActualBillId() {
        return nextId("in_transit_freight_actual_bill", 59000L);
    }

    default Long nextActualComponentId() {
        return nextId("in_transit_freight_actual_component", 60000L);
    }

    default Long nextEstimateSnapshotId() {
        return nextId("in_transit_freight_estimate_snapshot", 61000L);
    }

    default Long nextEstimateComponentId() {
        return nextId("in_transit_freight_estimate_component", 62000L);
    }

    default Long nextEstimateMatchId() {
        return nextId("in_transit_freight_estimate_match", 63000L);
    }

    default Long nextRateCardVersionId() {
        return nextId("in_transit_freight_rate_card_version", 64000L);
    }

    default Long nextRateCardRuleId() {
        return nextId("in_transit_freight_rate_card_rule", 65000L);
    }

    default Long nextId(String sequenceName, Long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        allocateId(command);
        if (command.getAllocatedId() == null || command.getAllocatedId() <= 0) {
            throw new IllegalStateException("在途运费 ID 序列分配失败：" + sequenceName);
        }
        return command.getAllocatedId();
    }
}
