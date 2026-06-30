package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OfficialWarehouseAppointmentWarehouseResolutionTest {

    @Test
    void requestedWarehouseOverridesAsnCreationRouteWhenSchedulingShipment() {
        assertThat(LocalDbOfficialWarehouseService.resolveAppointmentWarehouseToPartnerCode("JED01", "RUH01S"))
                .isEqualTo("RUH01S");
        assertThat(LocalDbOfficialWarehouseService.resolveAppointmentWarehouseToCode("W00000004A", "W00105371A"))
                .isEqualTo("W00105371A");
    }

    @Test
    void asnCreationRouteIsFallbackOnlyWhenUserDidNotChooseShipToWarehouse() {
        assertThat(LocalDbOfficialWarehouseService.resolveAppointmentWarehouseToPartnerCode(null, "RUH01S"))
                .isEqualTo("RUH01S");
        assertThat(LocalDbOfficialWarehouseService.resolveAppointmentWarehouseToCode(null, "W00105371A"))
                .isEqualTo("W00105371A");
        assertThat(LocalDbOfficialWarehouseService.resolveAppointmentWarehouseToPartnerCode("JED01", null))
                .isEqualTo("JED01");
        assertThat(LocalDbOfficialWarehouseService.resolveAppointmentWarehouseToCode("W00000004A", null))
                .isEqualTo("W00000004A");
    }

    @Test
    void currentAsnWarehouseFromIsFallbackWhenPartnerWarehouseListIsEmpty() {
        List<String> warehouses = LocalDbOfficialWarehouseService.resolveAppointmentWarehouseFromOptions(
                List.of(),
                new OfficialWarehouseAppointmentRunner.AsnDetail("sealed", "69486-1", "W00055867A"),
                null
        );

        assertThat(warehouses).containsExactly("69486-1");
    }
}
