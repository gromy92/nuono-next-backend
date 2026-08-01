package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.AsnDetail;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class OfficialWarehouseNoonInboundClientAppointmentDetailTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesNoonScheduledDateAndSlotFromAsnDetail() {
        JsonNode detail = objectMapper.createObjectNode()
                .put("status", "scheduled")
                .put("schedule_date", "2026-08-01")
                .put("schedule_slot", "11am-2pm");

        AsnDetail result = OfficialWarehouseNoonInboundClient.parseAsnDetail(detail);

        assertThat(result.status).isEqualTo("scheduled");
        assertThat(result.appointmentDate).isEqualTo(LocalDate.parse("2026-08-01"));
        assertThat(result.appointmentTime).isEqualTo("11am-2pm");
    }
}
