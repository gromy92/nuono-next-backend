package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class OfficialWarehouseFbnReceivedReportCsvParserTest {

    private final OfficialWarehouseFbnReceivedReportCsvParser parser =
            new OfficialWarehouseFbnReceivedReportCsvParser();

    @Test
    void parsesFbnReceivedReportColumnsAndQuotedValues() {
        String csv = "\ufeffpartner_sku,sku,po_nr,pbarcode_canonical,storage_type_code,volume,brand,product_title,asn,"
                + "partner_warehouse,noon_warehouse,country_code,qty_expected,received_qty,qc_failed_qty,"
                + "unidentified_qty,qc_failed_reason,asn_created_at,asn_schedule_date,asn_completed_at\n"
                + " PAPERSAYSB105N1,Z0B8C025C4C884FD10BE6Z-1,,6287053004607,standard,0.01,Papersay,"
                + "\"A4 file bag, black\",A05508658PN,-,RUH01S,sa,1,1,0,0,-,2026-06-11,2026-06-11,2026-06-13\n"
                + "PAPERSAYSB042,Z9DDECF61092EFCE742E9Z-1,,6287053004508,standard,0.02,Papersay,"
                + "Tape,A05508658PN,-,RUH01S,sa,3,2,0,1,missing,2026-06-11,2026-06-11,2026-06-13\n";

        OfficialWarehouseFbnReceivedReportCsvParser.ParsedFile result =
                parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(result.rows).hasSize(2);
        OfficialWarehouseFbnReceivedReportCsvParser.ReceivedRow first = result.rows.get(0);
        assertThat(first.rowNo).isEqualTo(2);
        assertThat(first.partnerSku).isEqualTo("PAPERSAYSB105N1");
        assertThat(first.noonSku).isEqualTo("Z0B8C025C4C884FD10BE6Z-1");
        assertThat(first.productTitle).isEqualTo("A4 file bag, black");
        assertThat(first.noonAsnNr).isEqualTo("A05508658PN");
        assertThat(first.qtyExpected).isEqualTo(1);
        assertThat(first.receivedQty).isEqualTo(1);
        assertThat(first.qcFailedReason).isNull();
        assertThat(first.rawFields).containsEntry("product_title", "A4 file bag, black");

        OfficialWarehouseFbnReceivedReportCsvParser.ReceivedRow second = result.rows.get(1);
        assertThat(second.unidentifiedQty).isEqualTo(1);
        assertThat(second.qcFailedReason).isEqualTo("missing");
    }
}
