package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class OfficialWarehouseFbnReceivedReportCsvParserTest {
    private static final String MINIMAL_HEADER =
            "partner_sku,sku,pbarcode_canonical,asn,qty_expected,received_qty,qc_failed_qty,"
                    + "unidentified_qty,asn_created_at,asn_schedule_date,asn_completed_at\n";

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
        assertThat(result.sourceDataRowCount).isEqualTo(2);
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

    @Test
    void skipsOnlyDeterministicBusinessDefects() {
        String csv = MINIMAL_HEADER
                + row("P-KEEP", "Z-KEEP", "", "A-KEEP", "1", "1", "0", "0", "", "2026-08-01", "")
                + row("P-BLANK", "Z-BLANK", "", "A-BLANK", "", "1", "0", "0", "", "2026-08-01", "")
                + row("P-DASH", "Z-DASH", "", "A-DASH", "1", "-", "0", "0", "", "2026-08-01", "")
                + row("P-NO-ASN", "Z-NO-ASN", "", "-", "1", "1", "0", "0", "", "2026-08-01", "")
                + row("", "", "", "A-NO-PRODUCT", "1", "1", "0", "0", "", "2026-08-01", "")
                + row("P-NEG", "Z-NEG", "", "A-NEG", "1", "1", "-1", "0", "", "2026-08-01", "")
                + row("", "", "6287053004607", "A-BARCODE", "1", "1", "0", "0", "", "2026-08-01", "");

        OfficialWarehouseFbnReceivedReportCsvParser.ParsedFile result =
                parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(result.rows).extracting(row -> row.rowNo).containsExactly(2, 8);
        assertThat(result.rows).extracting(row -> row.noonAsnNr).containsExactly("A-KEEP", "A-BARCODE");
        assertThat(result.sourceDataRowCount).isEqualTo(7);
    }

    @Test
    void headerOnlyFileHasNoLocalEvidenceOfAuthoritativeEmpty() {
        OfficialWarehouseFbnReceivedReportCsvParser.ParsedFile result =
                parser.parse(MINIMAL_HEADER.getBytes(StandardCharsets.UTF_8));

        assertThat(result.rows).isEmpty();
        assertThat(result.sourceDataRowCount).isZero();
    }

    @Test
    void rejectsContainerAndTypedParseDefectsEvenWhenTheSameRowHasABusinessDefect() {
        String invalidQuantity = MINIMAL_HEADER
                + row("P1", "Z1", "", "", "invalid", "1", "0", "0", "", "2026-08-01", "");
        String invalidDate = MINIMAL_HEADER
                + row("P1", "Z1", "", "", "1", "1", "0", "0", "", "2026-02-30", "");

        assertThatThrownBy(() -> parser.parse(
                invalidQuantity.getBytes(StandardCharsets.UTF_8)
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> parser.parse(
                invalidDate.getBytes(StandardCharsets.UTF_8)
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> parser.parse(
                (MINIMAL_HEADER + "P1,Z1,,A1,1,1,0,0,,\"2026-08-01,")
                        .getBytes(StandardCharsets.UTF_8)
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> parser.parse(
                new byte[]{(byte) 0xc3, (byte) 0x28}
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> parser.parse(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> parser.parse(
                "partner_sku,sku,SKU,asn,qty_expected,received_qty,qc_failed_qty,unidentified_qty,"
                        .concat("asn_schedule_date\n")
                        .getBytes(StandardCharsets.UTF_8)
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> parser.parse(
                "partner_sku,,sku,asn,qty_expected,received_qty,qc_failed_qty,unidentified_qty,"
                        .concat("asn_schedule_date\n")
                        .getBytes(StandardCharsets.UTF_8)
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> parser.parse(
                (MINIMAL_HEADER + "P1,Z1,,A1,1,1,0,0,,2026-08-01\n")
                        .getBytes(StandardCharsets.UTF_8)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildsTheSameBusinessKeyForCaseWhitespaceAndUnicodeCompatibilityVariants() {
        String csv = MINIMAL_HEADER
                + row("　ｐ－１　", "ｚ－１", "１２３", "ａ－１", "1", "1", "0", "0", "", "2026-08-01", "")
                + row("P-1", "Z-1", "123", "A-1", "1", "1", "0", "0", "", "2026-08-01", "");

        OfficialWarehouseFbnReceivedReportCsvParser.ParsedFile result =
                parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(result.rows).hasSize(2);
        assertThat(result.rows.get(0).businessKey()).isEqualTo(result.rows.get(1).businessKey());
        assertThat(result.rows.get(0).businessKey()).contains("A-1", "P-1", "Z-1", "123");
    }

    private static String row(
            String partnerSku,
            String noonSku,
            String barcode,
            String asn,
            String qtyExpected,
            String receivedQty,
            String qcFailedQty,
            String unidentifiedQty,
            String asnCreatedAt,
            String asnScheduleDate,
            String asnCompletedAt
    ) {
        return String.join(",",
                partnerSku,
                noonSku,
                barcode,
                asn,
                qtyExpected,
                receivedQty,
                qcFailedQty,
                unidentifiedQty,
                asnCreatedAt,
                asnScheduleDate,
                asnCompletedAt
        ) + "\n";
    }
}
