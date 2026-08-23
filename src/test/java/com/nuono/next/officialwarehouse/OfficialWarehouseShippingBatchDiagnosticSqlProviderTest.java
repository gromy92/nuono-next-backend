package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseShippingBatchDiagnosticMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseShippingBatchDiagnosticSqlProvider;
import java.util.Map;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class OfficialWarehouseShippingBatchDiagnosticSqlProviderTest {

    @Test
    void diagnosesOnlyAnExactBatchInsideTheResolvedOwnerScope() {
        String sql = OfficialWarehouseShippingBatchDiagnosticSqlProvider.selectExactBatchDiagnostic();

        assertThat(sql)
                .contains("b.owner_user_id = #{ownerUserId}")
                .contains("BINARY b.batch_reference_no = BINARY #{keyword}")
                .contains("BINARY b.tracking_no = BINARY #{keyword}")
                .contains("BINARY b.external_shipment_no = BINARY #{keyword}")
                .contains("LIMIT 1")
                .doesNotContain("LIKE #{keyword}")
                .doesNotContain("owner_user_id != #{ownerUserId}");
    }

    @Test
    void countsEveryReasonWithoutRelaxingStrictBarcodeIdentity() {
        String sql = OfficialWarehouseShippingBatchDiagnosticSqlProvider.selectExactBatchDiagnostic();

        assertThat(sql)
                .contains("AS packageCount")
                .contains("AS sourceCandidateCount")
                .contains("AS currentScopeCandidateCount")
                .contains("candidate.match_status = 'UNMATCHED'")
                .contains("candidate.match_status = 'EXCLUDED'")
                .contains("AS goodsLineCount")
                .contains("AS resolvedLineCount")
                .contains("AS shippedQuantity")
                .contains("AS remainingQuantity")
                .contains("JOIN barcodeProducts product ON product.barcode = line.sku")
                .contains("BINARY product.barcode = BINARY line.sku")
                .contains("BINARY line.psku = BINARY product.partnerSku")
                .contains("target_store_code = #{storeCode} AND target_site_code = #{siteCode}");
    }

    @Test
    void rendersBarcodeComparatorsAsSqlInsteadOfXmlEntities() {
        Configuration configuration = new Configuration();
        configuration.addMapper(OfficialWarehouseShippingBatchDiagnosticMapper.class);

        BoundSql boundSql = configuration.getMappedStatement(
                        OfficialWarehouseShippingBatchDiagnosticMapper.class.getName()
                                + ".selectExactBatchDiagnostic"
                )
                .getBoundSql(Map.of(
                        "ownerUserId", 307L,
                        "storeCode", "STR108065-NSA",
                        "siteCode", "SA",
                        "keyword", "BATCH-001"
                ));

        assertThat(boundSql.getSql())
                .contains("COALESCE(pb.barcode_type, '') <> 'PARTNER_SKU_ALIAS'")
                .doesNotContain("&lt;", "&gt;");
    }
}
