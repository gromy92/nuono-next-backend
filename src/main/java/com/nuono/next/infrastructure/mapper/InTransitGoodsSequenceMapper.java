package com.nuono.next.infrastructure.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.SelectKey;

public interface InTransitGoodsSequenceMapper extends InTransitGoodsSql {

    @Insert({
            "INSERT INTO product_management_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue} + 1), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE next_id = LAST_INSERT_ID(next_id + 1), gmt_updated = NOW()"
    })
    @SelectKey(statement = "SELECT LAST_INSERT_ID()", keyProperty = "allocatedId", before = false, resultType = Long.class)
    int allocateId(IdSequenceCommand command);

    default Long nextForwarderId() {
        return nextId("in_transit_forwarder", 51000L);
    }

    default Long nextForwarderAliasId() {
        return nextId("in_transit_forwarder_alias", 52000L);
    }

    default Long nextBatchId() {
        return nextId("in_transit_batch", 53000L);
    }

    default Long nextLineId() {
        return nextId("in_transit_goods_line", 54000L);
    }

    default Long nextPackageId() {
        return nextId("in_transit_package", 58000L);
    }

    default Long nextNodeId() {
        return nextId("in_transit_logistics_node", 55000L);
    }

    default Long nextImportBatchId() {
        return nextId("in_transit_import_batch", 56000L);
    }

    default Long nextOperationAuditId() {
        return nextId("in_transit_operation_audit", 57000L);
    }

    default Long nextId(String sequenceName, Long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        allocateId(command);
        if (command.getAllocatedId() == null || command.getAllocatedId() <= 0) {
            throw new IllegalStateException("在途商品 ID 序列分配失败：" + sequenceName);
        }
        return command.getAllocatedId();
    }
}
