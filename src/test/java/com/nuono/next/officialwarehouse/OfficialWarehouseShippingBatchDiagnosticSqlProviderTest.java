package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseShippingBatchDiagnosticSqlProvider;
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
}
