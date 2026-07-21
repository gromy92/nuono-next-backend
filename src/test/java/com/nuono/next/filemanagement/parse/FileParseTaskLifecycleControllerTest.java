package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class FileParseTaskLifecycleControllerTest extends FileParseHttpTestFixture {

    @TempDir
    private Path tempDir;

    @Test
    void delegatesTargetPlanTaskAndDeleteOperations() {
        FileParseTargetPlanSummary plan = new FileParseTargetPlanSummary();
        plan.setLabel("佣金-KSA");
        FileParseTaskListView tasks = new FileParseTaskListView();
        FileParseTaskDetailView detail = new FileParseTaskDetailView();
        when(service.listTargetPlans(session)).thenReturn(List.of(plan));
        when(service.listTasks(session, "quote", 4005L, "done", 2, 20)).thenReturn(tasks);
        when(service.getTask(session, 20001L)).thenReturn(detail);
        FileParseTaskLifecycleController controller = controller();

        assertEquals("佣金-KSA", controller.targetPlans(request).get(0).getLabel());
        assertEquals(tasks, controller.tasks("quote", 4005L, "done", 2, 20, request));
        assertEquals(detail, controller.taskDetail(20001L, request));
        assertEquals(HttpStatus.NO_CONTENT, controller.deleteTask(20001L, request).getStatusCode());
        verify(service).deleteTask(session, 20001L);
    }

    @Test
    void delegatesUploadCreateAndRunOperations() {
        MockMultipartFile file = new MockMultipartFile("file", "quote.xlsx", "application/vnd.ms-excel", new byte[] {1});
        FileParseUploadView upload = new FileParseUploadView();
        FileParseCreateTaskCommand command = new FileParseCreateTaskCommand();
        FileParseTaskDetailView task = new FileParseTaskDetailView();
        FileParseTaskRunView run = new FileParseTaskRunView();
        when(service.uploadFile(session, 4005L, file)).thenReturn(upload);
        when(service.createTask(session, command, "create-key")).thenReturn(task);
        when(service.startParseTask(session, 20001L)).thenReturn(run);
        FileParseTaskLifecycleController controller = controller();

        assertEquals(upload, controller.upload(4005L, file, request));
        assertEquals(task, controller.createTask(command, "create-key", request));
        assertEquals(run, controller.runTask(20001L, request));
    }

    @Test
    void returnsDownloadWithOriginalTransportContract() throws Exception {
        Path filePath = tempDir.resolve("quote.xlsx");
        Files.writeString(filePath, "quote");
        when(service.resolveArchivedFile(session, 10001L)).thenReturn(
                new FileParseArchivedFile(filePath, "义特 FBN 报价.xlsx", "application/vnd.ms-excel")
        );

        ResponseEntity<Resource> response = controller().download(10001L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(CacheControl.noStore().getHeaderValue(), response.getHeaders().getCacheControl());
        assertEquals("application/vnd.ms-excel", response.getHeaders().getContentType().toString());
        assertEquals(-1L, response.getHeaders().getContentLength());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
                .contains("filename*=UTF-8''%E4%B9%89%E7%89%B9%20FBN%20%E6%8A%A5%E4%BB%B7.xlsx"));
    }

    @Test
    void preservesUnavailableAndForbiddenResponses() {
        when(service.listTargetPlans(session)).thenThrow(new FileParseAccessDeniedException("denied"));
        ResponseStatusException forbidden = assertThrows(
                ResponseStatusException.class,
                () -> controller().targetPlans(request)
        );
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatus());

        when(serviceProvider.getIfAvailable()).thenReturn(null);
        ResponseStatusException unavailable = assertThrows(
                ResponseStatusException.class,
                () -> controller().targetPlans(request)
        );
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, unavailable.getStatus());
        assertEquals("文件管理解析数据库服务尚未启用。", unavailable.getReason());
    }

    private FileParseTaskLifecycleController controller() {
        return new FileParseTaskLifecycleController(support);
    }
}
