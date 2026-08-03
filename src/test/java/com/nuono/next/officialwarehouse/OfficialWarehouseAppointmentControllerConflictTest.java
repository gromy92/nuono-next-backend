package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nuono.next.officialwarehouse.OfficialWarehouseCommands.CorrectAppointmentCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseCommands.UpsertAppointmentCommand;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OfficialWarehouseAppointmentControllerConflictTest {

    @Mock
    private ObjectProvider<LocalDbOfficialWarehouseService> serviceProvider;
    @Mock
    private LocalDbOfficialWarehouseService service;
    @Mock
    private BusinessAccessResolver accessResolver;

    private OfficialWarehouseController controller;
    private MockHttpServletRequest request;
    private BusinessAccessContext access;

    @BeforeEach
    void setUp() {
        controller = new OfficialWarehouseController(serviceProvider, accessResolver);
        request = new MockHttpServletRequest();
        access = BusinessAccessContext.builder()
                .sessionUserId(900L)
                .businessOwnerUserId(307L)
                .storeOwnerUserIds(Map.of("STORE-A", 307L))
                .build();
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(
                request, BusinessCapability.OFFICIAL_WAREHOUSE
        )).thenReturn(access);
    }

    @Test
    void upsertStateConflictIsHttp409() {
        UpsertAppointmentCommand command = new UpsertAppointmentCommand();
        when(service.upsertAppointment(access, "500001", command)).thenThrow(conflict());

        assertConflict(() -> controller.upsertAppointment("500001", command, request));
    }

    @Test
    void selectedSlotStateConflictIsHttp409() {
        UpsertAppointmentCommand command = new UpsertAppointmentCommand();
        when(service.submitManualAppointment(access, "500001", command)).thenThrow(conflict());

        assertConflict(() -> controller.submitManualAppointment("500001", command, request));
    }

    @Test
    void runCancelAndCorrectionStateConflictsAreHttp409() {
        CorrectAppointmentCommand correction = new CorrectAppointmentCommand();
        when(service.runAppointmentOnce(access, "610001")).thenThrow(conflict());
        when(service.cancelAppointment(access, "610001")).thenThrow(conflict());
        when(service.correctAppointment(access, "610001", correction)).thenThrow(conflict());

        assertConflict(() -> controller.runAppointmentOnce("610001", request));
        assertConflict(() -> controller.cancelAppointment("610001", request));
        assertConflict(() -> controller.correctAppointment("610001", correction, request));
    }

    private void assertConflict(Runnable operation) {
        ResponseStatusException failure =
                catchThrowableOfType(operation::run, ResponseStatusException.class);

        assertThat(failure).isNotNull();
        assertThat(failure.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    private OfficialWarehouseAppointmentStateConflictException conflict() {
        return new OfficialWarehouseAppointmentStateConflictException(
                "约仓状态已变化，请刷新后重试。"
        );
    }
}
