package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseCommands.CorrectAppointmentCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseCommands.UpsertAppointmentCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AppointmentRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AsnRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AppointmentView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AsnView;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OfficialWarehouseIdObjectScopeOperationsTest {

    private OfficialWarehouseMapper mapper;
    private LocalDbOfficialWarehouseService service;

    @BeforeEach
    void setUp() {
        mapper = mock(OfficialWarehouseMapper.class);
        service = new LocalDbOfficialWarehouseService(
                mapper, null, null, null, null, new ObjectMapper(), null, null, null
        );
    }

    @Test
    void opensAsnOwnedByAuthorizedSecondaryOwner() {
        AsnRecord asn = asn(408L, "STORE-B");
        when(mapper.selectAuthorizedAsn(storeOwners(), 500408L)).thenReturn(asn);

        AsnView result = service.getAsn(access(), "500408");

        assertThat(result.id).isEqualTo("500408");
        assertThat(result.storeCode).isEqualTo("STORE-B");
        verify(mapper).listAsnInboundReceipts(408L, List.of(500408L));
    }

    @Test
    void rejectsAsnWhoseStoredOwnerDoesNotMatchAuthorizedStoreOwner() {
        AsnRecord mismatched = asn(307L, "STORE-B");
        when(mapper.selectAuthorizedAsn(storeOwners(), 500307L)).thenReturn(mismatched);

        assertThatThrownBy(() -> service.getAsn(access(), "500307"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无权访问");

        verify(mapper, never()).listAsnLines(500307L);
        verify(mapper, never()).listAsnInboundReceipts(307L, List.of(500307L));
    }

    @Test
    void allIdOnlyEntrypointsRejectEmptyScopeBeforeMapperOrNoonWork() {
        BusinessAccessContext empty = BusinessAccessContext.builder()
                .sessionUserId(900L)
                .businessOwnerUserId(307L)
                .build();
        UpsertAppointmentCommand appointmentCommand = new UpsertAppointmentCommand();
        CorrectAppointmentCommand correctionCommand = new CorrectAppointmentCommand();

        assertThatThrownBy(() -> service.getAsn(empty, "500001"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getAsnInboundDetail(empty, "500001"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.listNoonCalls(empty, "500001"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.upsertAppointment(empty, "500001", appointmentCommand))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.submitManualAppointment(empty, "500001", appointmentCommand))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.listAppointmentAvailability(empty, "500001", appointmentCommand))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.listAppointmentNoonCalls(empty, "610001"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.runAppointmentOnce(empty, "610001"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.cancelAppointment(empty, "610001"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.correctAppointment(empty, "610001", correctionCommand))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(mapper);
    }

    @Test
    void cancelsAppointmentOwnedByAuthorizedSecondaryOwner() {
        AppointmentRecord appointment = appointment(408L, "STORE-B");
        when(mapper.selectAuthorizedAppointment(storeOwners(), 610408L)).thenReturn(appointment);
        when(mapper.selectAppointment(408L, 610408L)).thenReturn(appointment);
        when(mapper.cancelAppointment(408L, 610408L, 0L, 900L)).thenReturn(1);

        AppointmentView result = service.cancelAppointment(access(), "610408");

        assertThat(result.id).isEqualTo("610408");
        verify(mapper).cancelAppointment(408L, 610408L, 0L, 900L);
    }

    @Test
    void rejectsMismatchedAppointmentBeforeAnyWrite() {
        AppointmentRecord mismatched = appointment(307L, "STORE-B");
        when(mapper.selectAuthorizedAppointment(storeOwners(), 610307L)).thenReturn(mismatched);

        assertThatThrownBy(() -> service.cancelAppointment(access(), "610307"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无权访问");

        verify(mapper, never()).cancelAppointment(307L, 610307L, 0L, 900L);
    }

    @Test
    void preservesCanonicalOwnerFallbackOnlyWhenTheEntireOwnerMapIsAbsent() {
        AsnRecord asn = asn(307L, "STORE-A");
        AppointmentRecord appointment = appointment(307L, "STORE-A");
        Map<String, Long> legacyScope = Map.of("STORE-A", 307L);
        when(mapper.selectAuthorizedAsn(legacyScope, 500307L)).thenReturn(asn);
        when(mapper.selectAuthorizedAppointment(legacyScope, 610307L)).thenReturn(appointment);
        when(mapper.selectAppointment(307L, 610307L)).thenReturn(appointment);
        when(mapper.cancelAppointment(307L, 610307L, 0L, 900L)).thenReturn(1);

        assertThat(service.getAsn(legacyAccess(), "500307").id).isEqualTo("500307");
        assertThat(service.cancelAppointment(legacyAccess(), "610307").id).isEqualTo("610307");
        verify(mapper).cancelAppointment(307L, 610307L, 0L, 900L);
    }

    @Test
    void rejectsRevokedStoreEvenWhenAnotherStoreKeepsTheSameOwnerAuthorized() {
        Map<String, Long> remainingScope = Map.of("STORE-C", 408L);
        AsnRecord revoked = asn(408L, "STORE-B");
        when(mapper.selectAuthorizedAsn(remainingScope, 500408L)).thenReturn(revoked);
        BusinessAccessContext partialMapping = BusinessAccessContext.builder()
                .sessionUserId(900L)
                .businessOwnerUserId(307L)
                .storeCodes(Set.of("STORE-B", "STORE-C"))
                .storeOwnerUserIds(remainingScope)
                .build();

        assertThatThrownBy(() -> service.getAsn(partialMapping, "500408"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无权访问");

        verify(mapper, never()).listAsnLines(500408L);
        verify(mapper, never()).listAsnInboundReceipts(408L, List.of(500408L));
    }

    private static BusinessAccessContext access() {
        return access(storeOwners());
    }

    private static BusinessAccessContext access(Map<String, Long> storeOwnerUserIds) {
        return BusinessAccessContext.builder()
                .sessionUserId(900L)
                .businessOwnerUserId(307L)
                .storeOwnerUserIds(storeOwnerUserIds)
                .build();
    }

    private static BusinessAccessContext legacyAccess() {
        return BusinessAccessContext.builder()
                .sessionUserId(900L)
                .businessOwnerUserId(307L)
                .storeCodes(Set.of("STORE-A"))
                .build();
    }

    private static Map<String, Long> storeOwners() {
        return Map.of("STORE-A", 307L, "STORE-B", 408L);
    }

    private static AsnRecord asn(Long ownerUserId, String storeCode) {
        AsnRecord record = new AsnRecord();
        record.id = ownerUserId.equals(408L) ? 500408L : 500307L;
        record.ownerUserId = ownerUserId;
        record.storeCode = storeCode;
        record.siteCode = "SA";
        record.localAsnNo = "OWA-" + record.id;
        record.status = "LINES_CREATED";
        record.totalQuantity = 10;
        return record;
    }

    private static AppointmentRecord appointment(Long ownerUserId, String storeCode) {
        AppointmentRecord record = new AppointmentRecord();
        record.id = ownerUserId.equals(408L) ? 610408L : 610307L;
        record.ownerUserId = ownerUserId;
        record.asnId = ownerUserId.equals(408L) ? 500408L : 500307L;
        record.storeCode = storeCode;
        record.siteCode = "SA";
        record.localAsnNo = "OWA-" + record.asnId;
        record.status = "PENDING";
        return record;
    }
}
