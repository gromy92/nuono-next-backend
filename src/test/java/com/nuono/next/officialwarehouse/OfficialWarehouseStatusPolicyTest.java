package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OfficialWarehouseStatusPolicyTest {

    @Test
    void normalizesNoonAsnStatusVariants() {
        assertThat(OfficialWarehouseStatusPolicy.normalizeNoonAsnStatus("grn-completed")).isEqualTo("GRN_COMPLETED");
        assertThat(OfficialWarehouseStatusPolicy.normalizeNoonAsnStatus(" scheduled ")).isEqualTo("SCHEDULED");
        assertThat(OfficialWarehouseStatusPolicy.normalizeNoonAsnStatus(null)).isEqualTo("");
    }

    @Test
    void classifiesNoonAsnLifecycleStatuses() {
        assertThat(OfficialWarehouseStatusPolicy.isNoonAsnReadyForAppointmentStatus("sealed")).isTrue();
        assertThat(OfficialWarehouseStatusPolicy.isNoonAsnScheduledStatus("scheduled")).isTrue();
        assertThat(OfficialWarehouseStatusPolicy.isNoonAsnScheduledStatus("grn_completed")).isTrue();
        assertThat(OfficialWarehouseStatusPolicy.isNoonAsnFailureStatus("cancelled")).isTrue();
        assertThat(OfficialWarehouseStatusPolicy.isNoonAsnFailureStatus("expired")).isTrue();
    }

    @Test
    void mapsRemoteStatusToLocalAsnStatus() {
        assertThat(OfficialWarehouseStatusPolicy.localAsnStatusFromNoon("created")).isEqualTo("ASN_CREATED");
        assertThat(OfficialWarehouseStatusPolicy.localAsnStatusFromNoon("sealed")).isEqualTo("LINES_CREATED");
        assertThat(OfficialWarehouseStatusPolicy.localAsnStatusFromNoon("grn_completed")).isEqualTo("LINES_CREATED");
        assertThat(OfficialWarehouseStatusPolicy.localAsnStatusFromNoon("expired")).isEqualTo("FAILED");
    }

    @Test
    void normalizesAppointmentCorrectionStatuses() {
        assertThat(OfficialWarehouseStatusPolicy.normalizeAppointmentCorrectionStatus("pending")).isEqualTo("PENDING");
        assertThat(OfficialWarehouseStatusPolicy.normalizeAppointmentCorrectionStatus("scheduled")).isEqualTo("SCHEDULED");
        assertThatThrownBy(() -> OfficialWarehouseStatusPolicy.normalizeAppointmentCorrectionStatus("unexpected"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持");
    }
}
