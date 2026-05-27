package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import java.time.LocalDate;
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
class NoonPullSmokeControllerTest {

    @Mock
    private ObjectProvider<NoonPullSmokeRunner> runnerProvider;

    @Mock
    private NoonPullSmokeRunner runner;

    @Mock
    private AuthSessionTokenService sessionTokenService;

    private NoonPullSmokeController controller;

    @BeforeEach
    void setUp() {
        controller = new NoonPullSmokeController(runnerProvider, sessionTokenService);
    }

    @Test
    void shouldAllowOnlySystemAdminToRunSmoke() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        NoonPullSmokeRunCommand command = command();
        NoonPullSmokeRunResult expected = new NoonPullSmokeRunResult();
        expected.setTargetEnvironment("test");
        expected.setEvidence(List.of(new NoonPullSmokeEvidenceView()));
        when(runnerProvider.getIfAvailable()).thenReturn(runner);
        when(sessionTokenService.requireSession(request)).thenReturn(new AuthenticatedSession(10001L, 1L, 0));
        when(runner.run(command)).thenReturn(expected);

        NoonPullSmokeRunResult result = controller.runSmoke(command, request);

        assertEquals("test", result.getTargetEnvironment());
        assertEquals(1, result.getEvidence().size());
    }

    @Test
    void shouldRejectBusinessAccount() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(runnerProvider.getIfAvailable()).thenReturn(runner);
        when(sessionTokenService.requireSession(request)).thenReturn(new AuthenticatedSession(10002L, 2L, 1));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.runSmoke(command(), request)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
    }

    @Test
    void shouldReturnServiceUnavailableWhenRunnerIsMissing() {
        when(runnerProvider.getIfAvailable()).thenReturn(null);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.runSmoke(command(), new MockHttpServletRequest())
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
    }

    private NoonPullSmokeRunCommand command() {
        NoonPullSmokeRunCommand command = new NoonPullSmokeRunCommand();
        command.setTargetEnvironment("test");
        command.setOwnerUserId(10002L);
        command.setStoreCode("STR245027-NAE");
        command.setSiteCode("AE");
        command.setSalesDate(LocalDate.of(2026, 5, 21));
        command.setOrderDateFrom(LocalDate.of(2026, 5, 21));
        command.setOrderDateTo(LocalDate.of(2026, 5, 21));
        return command;
    }
}
