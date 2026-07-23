package com.nuono.next.intransit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InTransitProductMatchContractTest {

    @Test
    void shouldKeepRawCandidateSchemaAndDeferredBusinessGate() throws Exception {
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
                .contains("resolveProductMatchCandidate");
        assertThat(pluginSync)
                .contains("productMatchService.saveCandidate")
                .doesNotContain("selectProductIdentityByBarcode");
        assertThat(officialWarehouse)
                .contains("countPendingProductMatchesForBatches")
                .contains("条商品待匹配，请先在在途物流中重新匹配");
        assertThat(productPreparation).contains("prepareForStoreSite");
    }

    private String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
