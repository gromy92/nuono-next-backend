package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class OfficialWarehouseScheduledDeliveryAccuracyCsvParserTest {

    private final OfficialWarehouseScheduledDeliveryAccuracyCsvParser parser =
            new OfficialWarehouseScheduledDeliveryAccuracyCsvParser();

    @Test
    void parsesScheduledDeliveryAccuracyRows() {
        String csv = "\ufeffASN Number,Warehouse,country,ASN Creation Date,Scheduled Date,Delivery Date,"
                + "Scheduled Quantity,GRN Quantity,Inbound Quantity Variance,Status,Inbound Utilization Efficiency %\n"
                + "A04540991PN,RUH01S,SA,2026-01-07 02:34:06 UTC,2026-01-08,2026-01-08,110,109,1,putaway_completed,99.49\n"
                + "A04544806PN,RUH07,SA,2026-01-07 10:28:36 UTC,2026-01-08,,2,0,2,cancelled,99.63\n";

        OfficialWarehouseScheduledDeliveryAccuracyCsvParser.ParsedFile result =
                parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(result.rows).hasSize(2);
        OfficialWarehouseScheduledDeliveryAccuracyCsvParser.AccuracyRow first = result.rows.get(0);
        assertThat(first.rowNo).isEqualTo(2);
        assertThat(first.noonAsnNr).isEqualTo("A04540991PN");
        assertThat(first.warehouseCode).isEqualTo("RUH01S");
        assertThat(first.countryCode).isEqualTo("SA");
        assertThat(first.asnCreationDate).isEqualTo("2026-01-07 02:34:06");
        assertThat(first.scheduledDate).isEqualTo("2026-01-08");
        assertThat(first.deliveryDate).isEqualTo("2026-01-08");
        assertThat(first.scheduledQty).isEqualTo(110);
        assertThat(first.grnQty).isEqualTo(109);
        assertThat(first.inboundQtyVariance).isEqualTo(1);
        assertThat(first.status).isEqualTo("putaway_completed");
        assertThat(first.inboundUtilizationEfficiency).isEqualTo("99.49");
        assertThat(first.businessKey()).isEqualTo("A04540991PN");

        OfficialWarehouseScheduledDeliveryAccuracyCsvParser.AccuracyRow second = result.rows.get(1);
        assertThat(second.deliveryDate).isNull();
        assertThat(second.status).isEqualTo("cancelled");
    }
}
