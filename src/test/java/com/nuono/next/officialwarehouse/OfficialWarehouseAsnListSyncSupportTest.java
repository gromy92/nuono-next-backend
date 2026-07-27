package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnLineInsertRecord;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfficialWarehouseAsnListSyncSupportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildsNoonPartnerAsnListRequestPayload() {
        ObjectNode body = OfficialWarehouseAsnListSyncSupport.buildListRequest(
                objectMapper,
                "108065",
                2,
                50,
                36
        );

        assertThat(body.path("idPartnerSource").asLong()).isEqualTo(108065L);
        assertThat(body.path("asnNr").asText()).isEmpty();
        assertThat(body.path("pagination").path("page").asInt()).isEqualTo(2);
        assertThat(body.path("pagination").path("perPage").asInt()).isEqualTo(50);
        assertThat(body.path("pagination").path("totalPages").asInt()).isEqualTo(36);
        assertThat(body.path("filters").path("status_list").isArray()).isTrue();
        assertThat(body.path("filters").path("status_list")).isEmpty();
        assertThat(body.path("orderBy").asText()).isEqualTo("DESC");
    }

    @Test
    void buildsNoonPartnerAsnExactSearchPayload() {
        ObjectNode body = OfficialWarehouseAsnListSyncSupport.buildSearchRequest(
                objectMapper,
                "108065",
                "A04540991PN"
        );

        assertThat(body.path("idPartnerSource").asLong()).isEqualTo(108065L);
        assertThat(body.path("asnNr").asText()).isEqualTo("A04540991PN");
        assertThat(body.path("pagination").path("page").asInt()).isEqualTo(1);
        assertThat(body.path("pagination").path("perPage").asInt()).isEqualTo(10);
        assertThat(body.path("pagination").path("totalPages").asInt()).isEqualTo(10);
        assertThat(body.path("filters").isObject()).isTrue();
        assertThat(body.path("orderBy").asText()).isEqualTo("DESC");
    }

    @Test
    void parsesScheduledNoonListRow() throws Exception {
        JsonNode row = objectMapper.readTree("{"
                + "\"id_partner_asn\":5508658,"
                + "\"asn_nr\":\"A05508658PN\","
                + "\"total_qty\":308,"
                + "\"status\":\"grn_completed\","
                + "\"warehouse_to\":\"RUH01S\","
                + "\"warehouse_code_to\":\"W00105371A\","
                + "\"updated_at\":\"2026-06-13T13:58:39\","
                + "\"schedule_date\":\"2026-06-11\","
                + "\"schedule_slot\":\"6pm-8pm\""
                + "}");

        OfficialWarehouseAsnListSyncSupport.NoonAsnListRow parsed =
                OfficialWarehouseAsnListSyncSupport.parseRow(row);

        assertThat(parsed.asnNr).isEqualTo("A05508658PN");
        assertThat(parsed.partnerAsnId).isEqualTo(5508658L);
        assertThat(parsed.totalQty).isEqualTo(308);
        assertThat(parsed.remoteStatus).isEqualTo("grn_completed");
        assertThat(parsed.localAsnStatus).isEqualTo("LINES_CREATED");
        assertThat(parsed.hasConfirmedAppointment()).isTrue();
        assertThat(parsed.appointmentDate).isEqualTo(LocalDate.parse("2026-06-11"));
        assertThat(parsed.appointmentTime).isEqualTo("6pm-8pm");
        assertThat(parsed.noonUpdatedAt).isEqualTo(LocalDateTime.parse("2026-06-13T13:58:39"));
        assertThat(parsed.warehouseToPartnerCode).isEqualTo("RUH01S");
        assertThat(parsed.warehouseToCode).isEqualTo("W00105371A");
    }

    @Test
    void mapsExpiredNoonStatusToFailedLocalAsn() throws Exception {
        JsonNode row = objectMapper.readTree("{"
                + "\"id_partner_asn\":5417078,"
                + "\"asn_nr\":\"A05417078PN\","
                + "\"total_qty\":15,"
                + "\"status\":\"expired\","
                + "\"schedule_date\":\"2026-05-27\","
                + "\"schedule_slot\":\"9am-11am\""
                + "}");

        OfficialWarehouseAsnListSyncSupport.NoonAsnListRow parsed =
                OfficialWarehouseAsnListSyncSupport.parseRow(row);

        assertThat(parsed.localAsnStatus).isEqualTo("FAILED");
        assertThat(parsed.hasConfirmedAppointment()).isTrue();
    }

    @Test
    void parsesRoutingWarehouseLinesFromNoonAsnDetail() throws Exception {
        JsonNode detail = objectMapper.readTree("{"
                + "\"asn_nr\":\"A05718626PN\","
                + "\"lines\":["
                + "{\"sku\":\"Z9F56DA86B5AEDA164B5BZ-1\",\"qty\":2,\"storage_type_code\":\"standard\"},"
                + "{\"noon_sku\":\"Z75EEXAMPLE\",\"quantity\":1},"
                + "{\"sku\":\"\",\"qty\":9},"
                + "{\"sku\":\"ZIGNORED\",\"qty\":0}"
                + "]"
                + "}");

        List<AsnLineInsertRecord> lines =
                OfficialWarehouseNoonInboundClient.routingLineRowsFromAsnDetail(detail);

        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).noonSku).isEqualTo("Z9F56DA86B5AEDA164B5BZ-1");
        assertThat(lines.get(0).quantity).isEqualTo(2);
        assertThat(lines.get(0).storageTypeCode).isEqualTo("standard");
        assertThat(lines.get(1).noonSku).isEqualTo("Z75EEXAMPLE");
        assertThat(lines.get(1).quantity).isEqualTo(1);
        assertThat(lines.get(1).storageTypeCode).isEqualTo("standard");
    }
}
