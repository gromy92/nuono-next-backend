package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OfficialWarehouseAsnInboundMapperSqlTest {

    @Test
    void receiptLookupUsesOwnerStoreSiteAndExactAsnFallback() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/nuono/next/infrastructure/mapper/OfficialWarehouseMapper.java"
        ));

        assertThat(source)
                .contains("i.report_type = 'FBN_INBOUND_FBNRECEIVEDREPORT'")
                .contains("receipt.owner_user_id = a.owner_user_id")
                .contains("BINARY receipt.store_code = BINARY a.store_code")
                .contains("UPPER(receipt.site_code) = UPPER(a.site_code)")
                .contains("receipt.asn_id = a.id")
                .contains("receipt.asn_id IS NULL")
                .contains("BINARY receipt.noon_asn_nr = BINARY a.noon_asn_nr")
                .contains("ROW_NUMBER() OVER")
                .doesNotContain("receipt.noon_asn_nr LIKE");
    }
}
