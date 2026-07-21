package com.nuono.next.filemanagement.parse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;

abstract class FileParseHttpTestFixture {

    protected ObjectProvider<LocalDbFileManagementParseService> serviceProvider;
    protected LocalDbFileManagementParseService service;
    protected AuthSessionTokenService sessionTokenService;
    protected AuthenticatedSession session;
    protected MockHttpServletRequest request;
    protected FileParseHttpSupport support;

    @BeforeEach
    void setUpHttpFixture() {
        serviceProvider = mock(ObjectProvider.class);
        service = mock(LocalDbFileManagementParseService.class);
        sessionTokenService = mock(AuthSessionTokenService.class);
        session = new AuthenticatedSession(10001L, 1L, 0);
        request = new MockHttpServletRequest();
        support = new FileParseHttpSupport(serviceProvider, sessionTokenService);
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(sessionTokenService.requireSession(request)).thenReturn(session);
    }
}
