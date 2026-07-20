package com.nuono.next.filemanagement.parse;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
final class FileParseHttpSupport {

    static final String BASE_PATH = "/api/file-management/parse";

    private final ObjectProvider<LocalDbFileManagementParseService> serviceProvider;
    private final AuthSessionTokenService sessionTokenService;

    FileParseHttpSupport(
            ObjectProvider<LocalDbFileManagementParseService> serviceProvider,
            AuthSessionTokenService sessionTokenService
    ) {
        this.serviceProvider = serviceProvider;
        this.sessionTokenService = sessionTokenService;
    }

    <T> T invokeAccessOnly(HttpServletRequest request, Operation<T> operation) {
        LocalDbFileManagementParseService service = requireService();
        try {
            return operation.invoke(service, sessionTokenService.requireSession(request));
        } catch (FileParseAccessDeniedException error) {
            throw forbidden(error);
        }
    }

    <T> T invokeValidated(HttpServletRequest request, Operation<T> operation) {
        LocalDbFileManagementParseService service = requireService();
        try {
            return operation.invoke(service, sessionTokenService.requireSession(request));
        } catch (FileParseAccessDeniedException error) {
            throw forbidden(error);
        } catch (IllegalArgumentException error) {
            throw badRequest(error);
        }
    }

    <T> T invokeInternalFailure(HttpServletRequest request, Operation<T> operation) {
        LocalDbFileManagementParseService service = requireService();
        try {
            return operation.invoke(service, sessionTokenService.requireSession(request));
        } catch (FileParseAccessDeniedException error) {
            throw forbidden(error);
        } catch (IllegalArgumentException error) {
            throw badRequest(error);
        } catch (IllegalStateException error) {
            throw internalFailure(error);
        }
    }

    <T> T invokeConflictAware(HttpServletRequest request, Operation<T> operation) {
        LocalDbFileManagementParseService service = requireService();
        try {
            return operation.invoke(service, sessionTokenService.requireSession(request));
        } catch (FileParseAccessDeniedException error) {
            throw forbidden(error);
        } catch (IllegalArgumentException error) {
            throw badRequest(error);
        } catch (IllegalStateException error) {
            throw conflict(error);
        }
    }

    <T> T invokeIoInternalFailure(HttpServletRequest request, IoOperation<T> operation) {
        LocalDbFileManagementParseService service = requireService();
        try {
            return operation.invoke(service, sessionTokenService.requireSession(request));
        } catch (FileParseAccessDeniedException error) {
            throw forbidden(error);
        } catch (IllegalArgumentException error) {
            throw badRequest(error);
        } catch (IOException | IllegalStateException error) {
            throw internalFailure(error);
        }
    }

    ResponseEntity<Resource> exportResponse(FileParseExportFile exportFile) {
        String encodedFileName = encodedFileName(exportFile.getFileName());
        byte[] content = exportFile.getContent();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentLength(content.length)
                .contentType(MediaType.parseMediaType(exportFile.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(encodedFileName))
                .body(new ByteArrayResource(content));
    }

    ResponseEntity<Resource> downloadResponse(FileParseArchivedFile archivedFile) throws IOException {
        String contentType = archivedFile.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = Files.probeContentType(archivedFile.getPath());
        }
        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(contentType))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        attachment(encodedFileName(archivedFile.getFileName()))
                )
                .body(new FileSystemResource(archivedFile.getPath()));
    }

    private LocalDbFileManagementParseService requireService() {
        LocalDbFileManagementParseService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "文件管理解析数据库服务尚未启用。"
            );
        }
        return service;
    }

    private String encodedFileName(String fileName) {
        return URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String attachment(String encodedFileName) {
        return "attachment; filename*=UTF-8''" + encodedFileName;
    }

    private ResponseStatusException forbidden(FileParseAccessDeniedException error) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
    }

    private ResponseStatusException badRequest(IllegalArgumentException error) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
    }

    private ResponseStatusException conflict(IllegalStateException error) {
        return new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage(), error);
    }

    private ResponseStatusException internalFailure(Exception error) {
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, error.getMessage(), error);
    }

    @FunctionalInterface
    interface Operation<T> {
        T invoke(LocalDbFileManagementParseService service, AuthenticatedSession session);
    }

    @FunctionalInterface
    interface IoOperation<T> {
        T invoke(LocalDbFileManagementParseService service, AuthenticatedSession session) throws IOException;
    }
}
