package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class FileParseHttpSupportTest extends FileParseHttpTestFixture {

    @Test
    void checksOptionalServiceBeforeResolvingSession() {
        when(serviceProvider.getIfAvailable()).thenReturn(null);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> support.invokeAccessOnly(request, (currentService, currentSession) -> "unused")
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        assertEquals("文件管理解析数据库服务尚未启用。", error.getReason());
        verifyNoInteractions(sessionTokenService);
    }

    @Test
    void passesAuthenticationResponseThroughUnchanged() {
        ResponseStatusException authentication = new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "请先登录后再继续操作。"
        );
        when(sessionTokenService.requireSession(request)).thenThrow(authentication);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> support.invokeValidated(request, (currentService, currentSession) -> "unused")
        );

        assertSame(authentication, error);
    }

    @Test
    void mapsAccessAndValidationFailures() {
        ResponseStatusException forbidden = assertThrows(
                ResponseStatusException.class,
                () -> support.invokeValidated(request, (currentService, currentSession) -> {
                    throw new FileParseAccessDeniedException("denied");
                })
        );
        ResponseStatusException badRequest = assertThrows(
                ResponseStatusException.class,
                () -> support.invokeValidated(request, (currentService, currentSession) -> {
                    throw new IllegalArgumentException("invalid");
                })
        );

        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatus());
        assertEquals("denied", forbidden.getReason());
        assertEquals(HttpStatus.BAD_REQUEST, badRequest.getStatus());
        assertEquals("invalid", badRequest.getReason());
    }

    @Test
    void mapsIllegalStateAccordingToOperationProfile() {
        ResponseStatusException internalFailure = assertThrows(
                ResponseStatusException.class,
                () -> support.invokeInternalFailure(request, (currentService, currentSession) -> {
                    throw new IllegalStateException("storage");
                })
        );
        ResponseStatusException conflict = assertThrows(
                ResponseStatusException.class,
                () -> support.invokeConflictAware(request, (currentService, currentSession) -> {
                    throw new IllegalStateException("stale");
                })
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, internalFailure.getStatus());
        assertEquals("storage", internalFailure.getReason());
        assertEquals(HttpStatus.CONFLICT, conflict.getStatus());
        assertEquals("stale", conflict.getReason());
    }

    @Test
    void mapsIoFailureToInternalServerError() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> support.invokeIoInternalFailure(request, (currentService, currentSession) -> {
                    throw new IOException("archive missing");
                })
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, error.getStatus());
        assertEquals("archive missing", error.getReason());
    }
}
