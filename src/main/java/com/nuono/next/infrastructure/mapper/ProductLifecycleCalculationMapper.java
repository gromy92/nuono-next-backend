package com.nuono.next.infrastructure.mapper;

import com.nuono.next.sales.ProductLifecycleCalculationScope;
import com.nuono.next.sales.ProductLifecycleListingSignalRow;
import com.nuono.next.sales.ProductLifecycleProductScopeRow;
import com.nuono.next.sales.ProductLifecycleScheduledScopeRow;
import com.nuono.next.sales.ProductLifecycleStateQuery;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProductLifecycleCalculationMapper {

    @ConstructorArgs({
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class)
    })
    @Select({
            "SELECT DISTINCT",
            "  ls.owner_user_id AS ownerUserId,",
            "  lss.store_code AS storeCode,",
            "  lss.site AS siteCode",
            "FROM logical_store_site lss",
            "JOIN logical_store ls ON ls.id = lss.logical_store_id AND ls.is_deleted = b'0'",
            "JOIN product_master pm ON pm.logical_store_id = ls.id AND pm.is_deleted = b'0'",
            "JOIN product_variant pv ON pv.product_master_id = pm.id AND pv.is_deleted = b'0'",
            "JOIN product_site_offer pso ON pso.variant_id = pv.id",
            "  AND pso.site_id = lss.id",
            "  AND pso.is_deleted = b'0'",
            "WHERE lss.is_deleted = b'0'",
            "  AND ls.owner_user_id IS NOT NULL",
            "  AND lss.store_code IS NOT NULL",
            "  AND lss.store_code <> ''",
            "  AND lss.site IS NOT NULL",
            "  AND lss.site <> ''",
            "ORDER BY ls.owner_user_id ASC, lss.store_code ASC, lss.site ASC"
    })
    List<ProductLifecycleScheduledScopeRow> selectScheduledScopes();

    @ConstructorArgs({
            @Arg(column = "ownerUserId", javaType = Long.class),
            @Arg(column = "storeCode", javaType = String.class),
            @Arg(column = "siteCode", javaType = String.class),
            @Arg(column = "partnerSku", javaType = String.class),
            @Arg(column = "sku", javaType = String.class)
    })
    @Select({
            "SELECT DISTINCT",
            "  ls.owner_user_id AS ownerUserId,",
            "  lss.store_code AS storeCode,",
            "  lss.site AS siteCode,",
            "  pv.partner_sku AS partnerSku,",
            "  COALESCE(",
            "    MAX(NULLIF(pv.child_sku, '')),",
            "    MAX(NULLIF(pso.offer_code, '')),",
            "    MAX(NULLIF(pso.psku_code, '')),",
            "    MAX(NULLIF(pm.sku_parent, ''))",
            "  ) AS sku",
            "FROM logical_store_site lss",
            "JOIN logical_store ls ON ls.id = lss.logical_store_id AND ls.is_deleted = b'0'",
            "JOIN product_master pm ON pm.logical_store_id = ls.id AND pm.is_deleted = b'0'",
            "JOIN product_variant pv ON pv.product_master_id = pm.id AND pv.is_deleted = b'0'",
            "JOIN product_site_offer pso ON pso.variant_id = pv.id",
            "  AND pso.site_id = lss.id",
            "  AND pso.is_deleted = b'0'",
            "WHERE ls.owner_user_id = #{scope.ownerUserId}",
            "  AND lss.store_code = #{scope.storeCode}",
            "  AND lss.site = #{scope.siteCode}",
            "  AND lss.is_deleted = b'0'",
            "  AND pv.partner_sku IS NOT NULL",
            "  AND pv.partner_sku <> ''",
            "GROUP BY ls.owner_user_id, lss.store_code, lss.site, pv.partner_sku",
            "HAVING sku IS NOT NULL",
            "ORDER BY pv.partner_sku ASC, sku ASC"
    })
    List<ProductLifecycleProductScopeRow> selectProductScopes(
            @Param("scope") ProductLifecycleCalculationScope scope
    );

    @ConstructorArgs({
            @Arg(column = "officialListingDate", javaType = LocalDate.class),
            @Arg(column = "earliestInventoryDate", javaType = LocalDate.class),
            @Arg(column = "earliestPvDate", javaType = LocalDate.class),
            @Arg(column = "earliestSalesDate", javaType = LocalDate.class),
            @Arg(column = "productPulledDate", javaType = LocalDate.class),
            @Arg(column = "historicalSignalDays", javaType = int.class),
            @Arg(column = "salesSignalDays", javaType = int.class),
            @Arg(column = "pvSignalDays", javaType = int.class),
            @Arg(column = "inventorySignalDays", javaType = int.class)
    })
    @Select({
            "SELECT",
            "  MIN(DATE(pso.official_listing_at)) AS officialListingDate,",
            "  NULL AS earliestInventoryDate,",
            "  (",
            "    SELECT MIN(dsf.fact_date)",
            "    FROM daily_sales_fact dsf",
            "    WHERE dsf.owner_user_id = #{query.ownerUserId}",
            "      AND dsf.store_code = #{query.storeCode}",
            "      AND dsf.site_code = #{query.siteCode}",
            "      AND dsf.partner_sku = #{query.partnerSku}",
            "      AND (dsf.your_visitors IS NOT NULL OR dsf.total_visitors IS NOT NULL)",
            "  ) AS earliestPvDate,",
            "  (",
            "    SELECT MIN(dsf.fact_date)",
            "    FROM daily_sales_fact dsf",
            "    WHERE dsf.owner_user_id = #{query.ownerUserId}",
            "      AND dsf.store_code = #{query.storeCode}",
            "      AND dsf.site_code = #{query.siteCode}",
            "      AND dsf.partner_sku = #{query.partnerSku}",
            "      AND COALESCE(dsf.net_units, 0) > 0",
            "  ) AS earliestSalesDate,",
            "  COALESCE(MIN(DATE(pm.last_synced_at)), MIN(DATE(pm.gmt_create))) AS productPulledDate,",
            "  (",
            "    SELECT COUNT(DISTINCT dsf.fact_date)",
            "    FROM daily_sales_fact dsf",
            "    WHERE dsf.owner_user_id = #{query.ownerUserId}",
            "      AND dsf.store_code = #{query.storeCode}",
            "      AND dsf.site_code = #{query.siteCode}",
            "      AND dsf.partner_sku = #{query.partnerSku}",
            "      AND (dsf.net_units IS NOT NULL OR dsf.your_visitors IS NOT NULL OR dsf.total_visitors IS NOT NULL)",
            "  ) AS historicalSignalDays,",
            "  (",
            "    SELECT COUNT(DISTINCT dsf.fact_date)",
            "    FROM daily_sales_fact dsf",
            "    WHERE dsf.owner_user_id = #{query.ownerUserId}",
            "      AND dsf.store_code = #{query.storeCode}",
            "      AND dsf.site_code = #{query.siteCode}",
            "      AND dsf.partner_sku = #{query.partnerSku}",
            "      AND dsf.net_units IS NOT NULL",
            "  ) AS salesSignalDays,",
            "  (",
            "    SELECT COUNT(DISTINCT dsf.fact_date)",
            "    FROM daily_sales_fact dsf",
            "    WHERE dsf.owner_user_id = #{query.ownerUserId}",
            "      AND dsf.store_code = #{query.storeCode}",
            "      AND dsf.site_code = #{query.siteCode}",
            "      AND dsf.partner_sku = #{query.partnerSku}",
            "      AND (dsf.your_visitors IS NOT NULL OR dsf.total_visitors IS NOT NULL)",
            "  ) AS pvSignalDays,",
            "  0 AS inventorySignalDays",
            "FROM product_site_offer pso",
            "JOIN product_variant pv ON pv.id = pso.variant_id AND pv.is_deleted = b'0'",
            "JOIN product_master pm ON pm.id = pv.product_master_id AND pm.is_deleted = b'0'",
            "JOIN logical_store ls ON ls.id = pm.logical_store_id AND ls.is_deleted = b'0'",
            "JOIN logical_store_site lss ON lss.id = pso.site_id",
            "  AND lss.logical_store_id = ls.id",
            "  AND lss.is_deleted = b'0'",
            "WHERE ls.owner_user_id = #{query.ownerUserId}",
            "  AND lss.store_code = #{query.storeCode}",
            "  AND lss.site = #{query.siteCode}",
            "  AND pso.is_deleted = b'0'",
            "  AND pv.partner_sku = #{query.partnerSku}"
    })
    ProductLifecycleListingSignalRow selectListingSignals(@Param("query") ProductLifecycleStateQuery query);

    @Select({
            "SELECT",
            "  CASE",
            "    WHEN COUNT(*) = 0 THEN NULL",
            "    WHEN SUM(CASE",
            "      WHEN pso.fbn_stock IS NOT NULL OR pso.supermall_stock IS NOT NULL OR pso.fbp_stock IS NOT NULL THEN 1",
            "      ELSE 0",
            "    END) = 0 THEN NULL",
            "    ELSE SUM(COALESCE(pso.fbn_stock, 0) + COALESCE(pso.supermall_stock, 0) + COALESCE(pso.fbp_stock, 0))",
            "  END",
            "FROM product_site_offer pso",
            "JOIN product_variant pv ON pv.id = pso.variant_id AND pv.is_deleted = b'0'",
            "JOIN product_master pm ON pm.id = pv.product_master_id AND pm.is_deleted = b'0'",
            "JOIN logical_store ls ON ls.id = pm.logical_store_id AND ls.is_deleted = b'0'",
            "JOIN logical_store_site lss ON lss.id = pso.site_id",
            "  AND lss.logical_store_id = ls.id",
            "  AND lss.is_deleted = b'0'",
            "WHERE ls.owner_user_id = #{query.ownerUserId}",
            "  AND lss.store_code = #{query.storeCode}",
            "  AND lss.site = #{query.siteCode}",
            "  AND pso.is_deleted = b'0'",
            "  AND pv.partner_sku = #{query.partnerSku}"
    })
    Integer selectCurrentStock(@Param("query") ProductLifecycleStateQuery query);
}
