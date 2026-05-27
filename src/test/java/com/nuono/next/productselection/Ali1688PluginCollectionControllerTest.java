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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class Ali1688PluginCollectionControllerTest {

    @Mock
    private ObjectProvider<Ali1688PluginCollectionService> pluginCollectionServiceProvider;

    @Mock
    private Ali1688PluginCollectionService pluginCollectionService;

    @Mock
    private AuthSessionTokenService sessionTokenService;

    private Ali1688PluginCollectionController controller;

    @BeforeEach
    void setUp() {
        controller = new Ali1688PluginCollectionController(pluginCollectionServiceProvider, sessionTokenService);
    }

    @Test
    void createAssignmentUsesWebSessionAndRejectsPayloadIdentityFields() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Ali1688PluginAssignmentCreateCommand command = new Ali1688PluginAssignmentCreateCommand();
        command.setOwnerUserId(99999L);
        when(sessionTokenService.requireSession(request)).thenReturn(new AuthenticatedSession(307L, 2L, 3));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.createAssignment("87001", command, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("插件采集任务归属由后端会话和 1688 任务推导，不能由前端传入。", error.getReason());
        verifyNoInteractions(pluginCollectionServiceProvider);
    }

    @Test
    void pluginFetchRequiresExplicitBearerBeforeSessionResolution() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.getAssignment("ALI1688-PLUGIN-90001-ABCDEF", request)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatus());
        assertEquals("插件接口需要 Bearer 登录态。", error.getReason());
        verifyNoInteractions(sessionTokenService);
        verifyNoInteractions(pluginCollectionServiceProvider);
    }

    @Test
    void pluginStartUsesBearerSessionAndDelegatesToService() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer signed-token");
        AuthenticatedSession session = new AuthenticatedSession(307L, 2L, 3);
        Ali1688PluginAssignmentView expected = new Ali1688PluginAssignmentView();
        expected.assignmentId = "90001";

        when(sessionTokenService.requireSession(request)).thenReturn(session);
        when(pluginCollectionServiceProvider.getIfAvailable()).thenReturn(pluginCollectionService);
        when(pluginCollectionService.startAssignment("ALI1688-PLUGIN-90001-ABCDEF", session)).thenReturn(expected);

        Ali1688PluginAssignmentView view = controller.startAssignment("ALI1688-PLUGIN-90001-ABCDEF", request);

        assertSame(expected, view);
        verify(pluginCollectionService).startAssignment("ALI1688-PLUGIN-90001-ABCDEF", session);
    }

    @Test
    void pluginListReturnsDiagnosticQueueContract() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer signed-token");
        AuthenticatedSession session = new AuthenticatedSession(307L, 2L, 3);
        Ali1688PluginAssignmentView expected = new Ali1688PluginAssignmentView();
        expected.assignmentId = "90001";
        Ali1688PluginAssignmentListView expectedList = new Ali1688PluginAssignmentListView();
        expectedList.items = java.util.List.of(expected);
        expectedList.summary.total = 1;
        expectedList.summary.pending = 1;
        expectedList.diagnostics.visibleTaskCount = 1;

        when(sessionTokenService.requireSession(request)).thenReturn(session);
        when(pluginCollectionServiceProvider.getIfAvailable()).thenReturn(pluginCollectionService);
        when(pluginCollectionService.listCurrentAssignments(session)).thenReturn(expectedList);

        Ali1688PluginAssignmentListView view = controller.listAssignments(request);

        assertEquals(1, view.items.size());
        assertSame(expected, view.items.get(0));
        assertEquals(1, view.summary.total);
        assertEquals(1, view.summary.pending);
        assertEquals(1, view.diagnostics.visibleTaskCount);
        verify(pluginCollectionService).listCurrentAssignments(session);
    }

    @Test
    void pluginSubmitRequiresExplicitBearerBeforeSessionResolution() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.submitCandidates("ALI1688-PLUGIN-90001-ABCDEF", new Ali1688PluginSubmissionCommand(), request)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatus());
        assertEquals("插件接口需要 Bearer 登录态。", error.getReason());
        verifyNoInteractions(sessionTokenService);
        verifyNoInteractions(pluginCollectionServiceProvider);
    }

    @Test
    void pluginSubmitUsesBearerSessionAndDelegatesToService() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer signed-token");
        AuthenticatedSession session = new AuthenticatedSession(307L, 2L, 3);
        Ali1688PluginSubmissionCommand command = new Ali1688PluginSubmissionCommand();
        command.setIdempotencyKey("submit-001");
        Ali1688PluginSubmissionView expected = new Ali1688PluginSubmissionView();
        expected.assignmentId = "90001";

        when(sessionTokenService.requireSession(request)).thenReturn(session);
        when(pluginCollectionServiceProvider.getIfAvailable()).thenReturn(pluginCollectionService);
        when(pluginCollectionService.submitCandidates("ALI1688-PLUGIN-90001-ABCDEF", session, command)).thenReturn(expected);

        Ali1688PluginSubmissionView view = controller.submitCandidates("ALI1688-PLUGIN-90001-ABCDEF", command, request);

        assertSame(expected, view);
        verify(pluginCollectionService).submitCandidates("ALI1688-PLUGIN-90001-ABCDEF", session, command);
    }
}
