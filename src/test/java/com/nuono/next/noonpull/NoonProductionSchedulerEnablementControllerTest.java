package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import java.util.List;
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
class NoonProductionSchedulerEnablementControllerTest {
    @Mock
    private ObjectProvider<NoonProductionSchedulerEnablementGate> gateProvider;

    @Mock
    private NoonProductionSchedulerEnablementGate gate;

    @Mock
    private AuthSessionTokenService sessionTokenService;

    private NoonProductionSchedulerEnablementController controller;

    @BeforeEach
    void setUp() {
        controller = new NoonProductionSchedulerEnablementController(gateProvider, sessionTokenService);
    }

    @Test
    void shouldPassSystemAdminIdentityIntoEnablementGate() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        NoonProductionSchedulerEnablementCommand command = new NoonProductionSchedulerEnablementCommand();
        NoonProductionSchedulerEnablementResult expected = new NoonProductionSchedulerEnablementResult();
        expected.setEnabled(true);
        expected.setPlanIds(List.of(120000L, 120001L));
        when(gateProvider.getIfAvailable()).thenReturn(gate);
        when(sessionTokenService.requireSession(request)).thenReturn(new AuthenticatedSession(10001L, 1L, 0));
        when(gate.enable(command, 10001L)).thenReturn(expected);

        NoonProductionSchedulerEnablementResult result = controller.enable(command, request);

        assertEquals(true, result.isEnabled());
        assertEquals(List.of(120000L, 120001L), result.getPlanIds());
    }

    @Test
    void shouldRejectBusinessAccount() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(gateProvider.getIfAvailable()).thenReturn(gate);
        when(sessionTokenService.requireSession(request)).thenReturn(new AuthenticatedSession(10002L, 2L, 1));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.enable(new NoonProductionSchedulerEnablementCommand(), request)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
    }

    @Test
    void shouldReturnServiceUnavailableWhenGateIsMissing() {
        when(gateProvider.getIfAvailable()).thenReturn(null);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.enable(new NoonProductionSchedulerEnablementCommand(), new MockHttpServletRequest())
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
    }
}
