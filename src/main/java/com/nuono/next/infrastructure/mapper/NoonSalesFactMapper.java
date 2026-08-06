package com.nuono.next.infrastructure.mapper;

import com.nuono.next.noonpull.NoonSalesDailyFact;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectKey;

public interface NoonSalesFactMapper {

    @Insert({
            "INSERT INTO sales_data_id_sequence (sequence_name, next_id, gmt_create, gmt_updated)",
            "VALUES (#{sequenceName}, LAST_INSERT_ID(#{initialValue} + 1), NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE",
            "  next_id = LAST_INSERT_ID(next_id + 1),",
            "  gmt_updated = NOW()"
    })
    @SelectKey(
            statement = "SELECT LAST_INSERT_ID()",
            keyProperty = "allocatedId",
            before = false,
            resultType = Long.class
    )
    void nextId(IdSequenceCommand command);

    default Long nextDailySalesFactId() {
        IdSequenceCommand command = new IdSequenceCommand("daily_sales_fact", 100000L);
        nextId(command);
        Long id = command.getAllocatedId();
        if (id == null || id <= 0) {
            throw new IllegalStateException("Noon sales fact ID allocation failed.");
        }
        return id;
    }

    @Insert({
            "INSERT INTO daily_sales_fact (",
            "  id, source_system, source_batch_id, owner_user_id, logical_store_id, store_code, site_code,",
            "  fact_date, partner_sku, sku, currency_code, shipped_units, net_units, revenue_shipped,",
            "  gmt_create, gmt_updated",
            ") VALUES (",
            "  #{id}, 'noon_productviewsandsalesdata', NULL, #{fact.ownerUserId}, NULL,",
            "  #{fact.storeCode}, #{fact.siteCode}, #{fact.salesDate}, #{fact.skuParent}, #{fact.sku},",
            "  #{fact.currency}, #{fact.unitsSold}, #{fact.unitsSold}, #{fact.salesAmount},",
            "  NOW(), NOW()",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "  currency_code = VALUES(currency_code),",
            "  shipped_units = VALUES(shipped_units),",
            "  net_units = VALUES(net_units),",
            "  revenue_shipped = VALUES(revenue_shipped),",
            "  gmt_updated = NOW()"
    })
    int upsertDailySalesFact(@Param("id") Long id, @Param("fact") NoonSalesDailyFact fact);

}
