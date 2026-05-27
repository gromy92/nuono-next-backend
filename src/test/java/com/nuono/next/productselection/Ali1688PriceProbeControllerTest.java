package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
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
class Ali1688PriceProbeControllerTest {

    @Mock
    private ObjectProvider<Ali1688PriceProbeService> serviceProvider;

    @Mock
    private Ali1688PriceProbeService service;

    @Mock
    private AuthSessionTokenService sessionTokenService;

    private Ali1688PriceProbeController controller;

    @BeforeEach
    void setUp() {
        controller = new Ali1688PriceProbeController(serviceProvider, sessionTokenService);
    }

    @Test
    void priceProbeUsesWebSessionAndDelegatesToService() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthenticatedSession session = new AuthenticatedSession(307L, 2L, 3);
        Ali1688PriceProbeCommand command = new Ali1688PriceProbeCommand();
        Ali1688RealPriceSnapshot expected = new Ali1688RealPriceSnapshot();
        expected.id = 91001L;

        when(sessionTokenService.requireSession(request)).thenReturn(session);
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(service.requestProbe("88001", command, 307L)).thenReturn(expected);

        Ali1688RealPriceSnapshot actual = controller.requestPriceProbe("88001", command, request);

        assertSame(expected, actual);
        verify(service).requestProbe("88001", command, 307L);
    }

    @Test
    void priceProbeReturnsServiceUnavailableWhenProbeServiceIsDisabled() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(sessionTokenService.requireSession(request)).thenReturn(new AuthenticatedSession(307L, 2L, 3));
        when(serviceProvider.getIfAvailable()).thenReturn(null);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.requestPriceProbe("88001", new Ali1688PriceProbeCommand(), request)
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        assertEquals("1688 真实价格探针服务未启用。", error.getReason());
    }

    @Test
    void priceProbeRejectsInvalidProbeRequestAsBadRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Ali1688PriceProbeCommand command = new Ali1688PriceProbeCommand();
        when(sessionTokenService.requireSession(request)).thenReturn(new AuthenticatedSession(307L, 2L, 3));
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(service.requestProbe("88001", command, 307L)).thenThrow(new IllegalArgumentException("候选未通过 AI 门禁，不能执行真实价格探针。"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.requestPriceProbe("88001", command, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("候选未通过 AI 门禁，不能执行真实价格探针。", error.getReason());
    }
}
