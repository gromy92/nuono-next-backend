package com.nuono.next.intransit;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.InTransitProductMatchCandidateMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class InTransitProductMatchContractTest {

    @Test
    void shouldKeepRawCandidatesWithoutBlockingMatchedAsnLines() throws Exception {
        String migration = read("src/main/resources/db/init/199_in_transit_product_match_candidate.sql");
        String candidateMapper = read(
                "src/main/java/com/nuono/next/infrastructure/mapper/InTransitProductMatchCandidateMapper.java"
        );
        String pluginSync = read(
                "src/main/java/com/nuono/next/intransit/InTransitPluginSyncService.java"
        );
        String officialWarehouse = read(
                "src/main/java/com/nuono/next/officialwarehouse/LocalDbOfficialWarehouseService.java"
        );
        String productPreparation = read(
                "src/main/java/com/nuono/next/officialwarehouse/OfficialWarehouseProductMatchController.java"
        );

        assertThat(migration)
                .contains("source_barcode", "source_psku", "source_msku", "product_name")
                .contains("shipped_quantity", "received_quantity", "carton_weight_kg")
                .contains("match_status", "UNMATCHED");
        assertThat(candidateMapper)
                .contains("insertProductMatchCandidate", "updateProductMatchCandidate")
                .contains("resolveProductMatchCandidate", "listProductLandingBatchIds")
                .contains("excludeProductMatchCandidate", "match_status = 'EXCLUDED'")
                .contains("product_barcode landingBarcode", "logical_store_site landingSite")
                .contains("product_master landingMaster")
                .contains("COUNT(DISTINCT identityBarcode.logical_store_id, BINARY identityBarcode.partner_sku)")
                .contains("identityStore.owner_user_id = candidate.owner_user_id")
                .contains("BINARY source_barcode = BINARY #{sourceBarcode}")
                .contains("match_status = 'UNMATCHED'");
        assertThat(pluginSync)
                .contains("productMatchService.saveCandidate")
                .doesNotContain("selectProductIdentityByBarcode");
        assertThat(officialWarehouse)
                .contains("listShippingBatchSourceAllocations")
                .contains("选择的物流批次没有匹配当前 ASN 商品")
                .contains("选择的物流批次数量不足")
                .doesNotContain("countPendingProductMatchesForBatches")
                .doesNotContain("条商品待匹配，请先在在途物流中重新匹配");
        assertThat(productPreparation)
                .contains("@PostMapping(\"/prepare\")")
                .contains("prepareForStoreSite");
    }

    @Test
    void shouldResolveOfficialWarehouseScopeFromMatchedBarcodeIdentity() throws Exception {
        String mapper = read(
                "src/main/java/com/nuono/next/infrastructure/mapper/OfficialWarehouseMapper.java"
        ) + read(
                "src/main/java/com/nuono/next/infrastructure/mapper/OfficialWarehouseSqlFragments.java"
        );

        assertThat(mapper)
                .contains("product_barcode scopeBarcode")
                .contains("scopeBarcode.logical_store_id = ls.id")
                .contains("scopeBarcode.barcode = line.sku")
                .contains("BINARY scopeBarcode.barcode = BINARY line.sku")
                .doesNotContain("scopeBarcode.partner_sku = line.psku")
                .doesNotContain("scopeBarcode.barcode = line.psku");
    }

    @Test
    void shouldRenderNonScriptLandingSqlWithoutXmlEntities() throws Exception {
        Select select = InTransitProductMatchCandidateMapper.class
                .getMethod("listProductLandingBatchIds", Long.class, String.class, String.class)
                .getAnnotation(Select.class);
        String sql = String.join(" ", select.value());

        assertThat(sql)
                .doesNotContain("&lt;", "&gt;")
                .contains("!= 'PARTNER_SKU_ALIAS'")
                .contains("COUNT(DISTINCT identityBarcode.logical_store_id, BINARY identityBarcode.partner_sku)")
                .contains("BINARY identityBarcode.barcode = BINARY candidate.source_barcode");
    }

    private String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
