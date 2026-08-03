package com.nuono.next.intransit;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.InTransitGoodsLineMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class InTransitGoodsLineMapperSqlTest {

    @Test
    void barcodeLookupNeverTreatsPartnerSkuAliasAsProductBarcode() throws Exception {
        Method method = InTransitGoodsLineMapper.class.getMethod(
                "selectProductIdentityByBarcode",
                Long.class,
                String.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("MIN(pb.logical_store_id) AS logicalStoreId")
                .contains("MIN(pb.partner_sku) AS partnerSku")
                .contains("WHERE pb.barcode = #{barcode}")
                .contains("BINARY pb.barcode = BINARY #{barcode}")
                .contains("COALESCE(pb.barcode_type, '') <> 'PARTNER_SKU_ALIAS'")
                .contains("ls.owner_user_id = #{ownerUserId}")
                .contains("HAVING COUNT(DISTINCT pb.logical_store_id, BINARY pb.partner_sku) = 1")
                .doesNotContain("LIMIT 1")
                .doesNotContain("product_variant")
                .doesNotContain("variant_id");
    }

    @Test
    void lineSaveMarksStoreProductLogisticsHistoryFromInTransitBatchOnly() throws Exception {
        Method method = InTransitGoodsLineMapper.class.getMethod(
                "markProductSiteOfferLogisticsHistoryByLine",
                Long.class,
                Long.class,
                Long.class,
                Long.class
        );

        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("UPDATE product_site_offer pso")
                .contains("FROM in_transit_goods_line line")
                .contains("JOIN in_transit_batch batch")
                .contains("batch.batch_status")
                .contains("JOIN logical_store ls")
                .contains("JOIN product_barcode pb")
                .contains("pb.barcode = line.sku")
                .contains("BINARY pb.barcode = BINARY line.sku")
                .contains("JOIN product_master pm")
                .contains("BINARY pm.partner_sku = BINARY pb.partner_sku")
                .contains("history.product_site_offer_id = pso.id")
                .contains("pso_match.id AS product_site_offer_id")
                .contains("COUNT(DISTINCT identity_pb.logical_store_id, BINARY identity_pb.partner_sku)")
                .contains("COALESCE(pb.barcode_type, '') <> 'PARTNER_SKU_ALIAS'")
                .contains("pso.logistics_history_source = 'IN_TRANSIT_GOODS_LINE'")
                .doesNotContain("line.psku")
                .doesNotContain("UPPER(")
                .doesNotContain("REGEXP")
                .doesNotContain("procurement_fulfillment_balance")
                .doesNotContain("warehouse_shipping")
                .doesNotContain("warehouse_packing")
                .doesNotContain("official_warehouse_asn")
                .doesNotContain("WAREHOUSE_DISPATCH_HANDOFF");
    }
}
