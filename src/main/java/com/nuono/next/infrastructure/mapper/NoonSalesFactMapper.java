package com.nuono.next.infrastructure.mapper;

import com.nuono.next.nooncompleteness.NoonSalesProductViewsCompletenessAudit;
import com.nuono.next.noonpull.NoonSalesDailyFact;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectKey;
import org.apache.ibatis.annotations.Update;

public interface NoonSalesFactMapper {

    @Update({
            "CREATE TABLE IF NOT EXISTS `sales_data_id_sequence` (",
            "  `sequence_name` VARCHAR(80) NOT NULL,",
            "  `next_id` BIGINT NOT NULL,",
            "  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,",
            "  `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,",
            "  PRIMARY KEY (`sequence_name`)",
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
    })
    void ensureSalesDataIdSequence();

    @Insert({
            "INSERT INTO `sales_data_id_sequence` (`sequence_name`, `next_id`, `gmt_create`, `gmt_updated`)",
            "VALUES ('daily_sales_fact', 100000, NOW(), NOW())",
            "ON DUPLICATE KEY UPDATE",
            "  `next_id` = `next_id`,",
            "  `gmt_updated` = VALUES(`gmt_updated`)"
    })
    void ensureDailySalesFactSequence();

    @Update({
            "CREATE TABLE IF NOT EXISTS `daily_sales_fact` (",
            "  `id` BIGINT NOT NULL,",
            "  `source_system` VARCHAR(80) NOT NULL,",
            "  `source_batch_id` BIGINT DEFAULT NULL,",
            "  `owner_user_id` BIGINT NOT NULL,",
            "  `logical_store_id` BIGINT DEFAULT NULL,",
            "  `store_code` VARCHAR(80) NOT NULL,",
            "  `site_code` VARCHAR(20) NOT NULL,",
            "  `fact_date` DATE NOT NULL,",
            "  `partner_sku` VARCHAR(160) NOT NULL,",
            "  `sku` VARCHAR(160) NOT NULL,",
            "  `sku_config` VARCHAR(160) DEFAULT NULL,",
            "  `country_code` VARCHAR(20) DEFAULT NULL,",
            "  `currency_code` VARCHAR(20) DEFAULT NULL,",
            "  `product_title` VARCHAR(1000) DEFAULT NULL,",
            "  `your_visitors` INT DEFAULT NULL,",
            "  `total_visitors` INT DEFAULT NULL,",
            "  `gross_units` INT DEFAULT NULL,",
            "  `shipped_units` INT DEFAULT NULL,",
            "  `cancelled_units` INT DEFAULT NULL,",
            "  `net_units` INT NOT NULL DEFAULT 0,",
            "  `revenue_shipped` DECIMAL(18,6) DEFAULT NULL,",
            "  `buy_box_visitor_percentage` DECIMAL(10,4) DEFAULT NULL,",
            "  `conversion_visitors_percentage` DECIMAL(10,4) DEFAULT NULL,",
            "  `asp_shipped_percentage` DECIMAL(18,6) DEFAULT NULL,",
            "  `source_row_hash` VARCHAR(128) DEFAULT NULL,",
            "  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,",
            "  `gmt_updated` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,",
            "  PRIMARY KEY (`id`),",
            "  UNIQUE KEY `uk_daily_sales_fact_source_scope` (",
            "    `source_system`, `owner_user_id`, `store_code`, `site_code`, `fact_date`, `partner_sku`, `sku`",
            "  ),",
            "  KEY `idx_daily_sales_fact_scope_date` (`owner_user_id`, `store_code`, `site_code`, `fact_date`),",
            "  KEY `idx_daily_sales_fact_product` (`owner_user_id`, `partner_sku`, `sku`),",
            "  KEY `idx_daily_sales_fact_batch` (`source_batch_id`)",
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
    })
    void ensureDailySalesFactTable();

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

    @Select({
            "SELECT",
            "  MAX(fact_date) AS latestFactDate,",
            "  MIN(fact_date) AS historyCoveredFrom,",
            "  MAX(fact_date) AS historyCoveredTo,",
            "  COUNT(1) AS factRowCount",
            "FROM daily_sales_fact",
            "WHERE owner_user_id = #{ownerUserId}",
            "  AND store_code = #{storeCode}",
            "  AND site_code = #{siteCode}"
    })
    NoonSalesProductViewsCompletenessAudit auditSalesProductViewsCompleteness(
            @Param("ownerUserId") Long ownerUserId,
            @Param("storeCode") String storeCode,
            @Param("siteCode") String siteCode
    );
}
