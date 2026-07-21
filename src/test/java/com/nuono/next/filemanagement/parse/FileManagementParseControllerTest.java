package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class FileManagementParseControllerTest {

    @Mock
    private ObjectProvider<LocalDbFileManagementParseService> fileManagementParseServiceProvider;

    @Mock
    private LocalDbFileManagementParseService fileManagementParseService;

    @Mock
    private AuthSessionTokenService sessionTokenService;

    @TempDir
    private Path tempDir;

    private FileManagementParseController controller;

    @BeforeEach
    void setUp() {
        controller = new FileManagementParseController(fileManagementParseServiceProvider, sessionTokenService);
    }

    @Test
    void shouldReturnTargetPlansWhenServiceAvailable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthenticatedSession session = new AuthenticatedSession(10001L, 1L, 0);
        FileParseTargetPlanSummary plan = new FileParseTargetPlanSummary();
        plan.setId(4001L);
        plan.setCode("commission_ksa");
        plan.setLabel("佣金-KSA");

        when(fileManagementParseServiceProvider.getIfAvailable()).thenReturn(fileManagementParseService);
        when(sessionTokenService.requireSession(request)).thenReturn(session);
        when(fileManagementParseService.listTargetPlans(session)).thenReturn(List.of(plan));

        List<FileParseTargetPlanSummary> plans = controller.targetPlans(request);

        assertEquals(1, plans.size());
        assertEquals("佣金-KSA", plans.get(0).getLabel());
    }

    @Test
    void shouldTranslateServiceUnavailable() {
        when(fileManagementParseServiceProvider.getIfAvailable()).thenReturn(null);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.targetPlans(new MockHttpServletRequest())
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
    }

    @Test
    void shouldTranslateAccessDenied() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthenticatedSession session = new AuthenticatedSession(10002L, 2L, 1);
        when(fileManagementParseServiceProvider.getIfAvailable()).thenReturn(fileManagementParseService);
        when(sessionTokenService.requireSession(request)).thenReturn(session);
        when(fileManagementParseService.listTargetPlans(session))
                .thenThrow(new FileParseAccessDeniedException("当前账号没有文件管理入口权限。"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.targetPlans(request)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
    }

    @Test
    void shouldUploadFileWhenServiceAvailable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthenticatedSession session = new AuthenticatedSession(10001L, 1L, 0);
        MockMultipartFile file = new MockMultipartFile("file", "quote.xlsx", "application/vnd.ms-excel", "quote".getBytes());
        FileParseUploadView upload = new FileParseUploadView();
        upload.setFileId(10001L);
        upload.setOriginalFileName("quote.xlsx");

        when(fileManagementParseServiceProvider.getIfAvailable()).thenReturn(fileManagementParseService);
        when(sessionTokenService.requireSession(request)).thenReturn(session);
        when(fileManagementParseService.uploadFile(session, 4005L, file)).thenReturn(upload);

        FileParseUploadView result = controller.upload(4005L, file, request);

        assertEquals(10001L, result.getFileId());
        verify(fileManagementParseService).uploadFile(session, 4005L, file);
    }

    @Test
    void shouldCreateTaskWhenServiceAvailable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthenticatedSession session = new AuthenticatedSession(10001L, 1L, 0);
        FileParseCreateTaskCommand command = new FileParseCreateTaskCommand();
        FileParseTaskDetailView task = new FileParseTaskDetailView();
        task.setId(20001L);
        task.setStatus("reading");

        when(fileManagementParseServiceProvider.getIfAvailable()).thenReturn(fileManagementParseService);
        when(sessionTokenService.requireSession(request)).thenReturn(session);
        when(fileManagementParseService.createTask(session, command, "idem-1")).thenReturn(task);

        FileParseTaskDetailView result = controller.createTask(command, "idem-1", request);

        assertEquals(20001L, result.getId());
        assertEquals("reading", result.getStatus());
    }

    @Test
    void shouldRunTaskWhenServiceAvailable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthenticatedSession session = new AuthenticatedSession(10001L, 1L, 0);
        FileParseTaskRunView run = new FileParseTaskRunView();
        run.setTaskId(20001L);
        run.setStatus("parsing");

        when(fileManagementParseServiceProvider.getIfAvailable()).thenReturn(fileManagementParseService);
        when(sessionTokenService.requireSession(request)).thenReturn(session);
        when(fileManagementParseService.startParseTask(session, 20001L)).thenReturn(run);

        FileParseTaskRunView result = controller.runTask(20001L, request);

        assertEquals(20001L, result.getTaskId());
        assertEquals("parsing", result.getStatus());
    }

    @Test
    void shouldReturnProcessingItemsWhenServiceAvailable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthenticatedSession session = new AuthenticatedSession(10001L, 1L, 0);
        FileParseProcessingItemsView view = new FileParseProcessingItemsView();
        view.setTaskId(20001L);
        view.setResultId(40001L);
        view.setTotal(1);

        when(fileManagementParseServiceProvider.getIfAvailable()).thenReturn(fileManagementParseService);
        when(sessionTokenService.requireSession(request)).thenReturn(session);
        when(fileManagementParseService.listProcessingItems(session, 20001L, "all", null, 1, 100)).thenReturn(view);

        FileParseProcessingItemsView result = controller.processingItems(20001L, "all", null, 1, 100, request);

        assertEquals(40001L, result.getResultId());
        assertEquals(1, result.getTotal());
    }

    @Test
    void shouldAcceptProcessingItemWhenServiceAvailable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthenticatedSession session = new AuthenticatedSession(10001L, 1L, 0);
        FileParseReviewCommand command = new FileParseReviewCommand();
        command.setExpectedResultId(40001L);
        FileParseProcessingItemView item = new FileParseProcessingItemView();
        item.setItemId(50001L);
        item.setReviewStatus("confirmed");

        when(fileManagementParseServiceProvider.getIfAvailable()).thenReturn(fileManagementParseService);
        when(sessionTokenService.requireSession(request)).thenReturn(session);
        when(fileManagementParseService.reviewResultItem(session, 20001L, 50001L, "accept", command, "idem-1"))
                .thenReturn(item);

        FileParseProcessingItemView result = controller.acceptItem(20001L, 50001L, command, "idem-1", request);

        assertEquals("confirmed", result.getReviewStatus());
        verify(fileManagementParseService).reviewResultItem(session, 20001L, 50001L, "accept", command, "idem-1");
    }

    @Test
    void shouldBatchAcceptProcessingItemsWhenServiceAvailable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthenticatedSession session = new AuthenticatedSession(10001L, 1L, 0);
        FileParseBatchReviewCommand command = new FileParseBatchReviewCommand();
        command.setExpectedResultId(40001L);
        command.setItemIds(List.of(50001L, 50002L));
        FileParseBatchReviewView view = new FileParseBatchReviewView();
        view.setTaskId(20001L);
        view.setResultId(40001L);
        view.setTotalCount(2);
        view.setSuccessCount(2);

        when(fileManagementParseServiceProvider.getIfAvailable()).thenReturn(fileManagementParseService);
        when(sessionTokenService.requireSession(request)).thenReturn(session);
        when(fileManagementParseService.batchAcceptResultItems(session, 20001L, command, "idem-batch-1"))
                .thenReturn(view);

        FileParseBatchReviewView result = controller.batchAcceptItems(20001L, command, "idem-batch-1", request);

        assertEquals(2, result.getSuccessCount());
        verify(fileManagementParseService).batchAcceptResultItems(session, 20001L, command, "idem-batch-1");
    }

    @Test
    void shouldReturnOverviewItemsWhenServiceAvailable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthenticatedSession session = new AuthenticatedSession(10001L, 1L, 0);
        FileParseOverviewItemsView view = new FileParseOverviewItemsView();
        view.setTaskId(20001L);
        view.setResultId(40001L);
        view.setTotal(1);

        when(fileManagementParseServiceProvider.getIfAvailable()).thenReturn(fileManagementParseService);
        when(sessionTokenService.requireSession(request)).thenReturn(session);
        when(fileManagementParseService.listOverviewItems(session, 20001L, 1, 1000)).thenReturn(view);

        FileParseOverviewItemsView result = controller.overviewItems(20001L, 1, 1000, request);

        assertEquals(40001L, result.getResultId());
        assertEquals(1, result.getTotal());
    }

    @Test
    void shouldExportOverviewItemsWhenServiceAvailable() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthenticatedSession session = new AuthenticatedSession(10001L, 1L, 0);
        FileParseExportFile exportFile = new FileParseExportFile(
                "义特 FBN 报价 2026-05-解析总览.xlsx",
                FileParseQueryViewService.OVERVIEW_EXPORT_CONTENT_TYPE,
                new byte[] {1, 2, 3}
        );

        when(fileManagementParseServiceProvider.getIfAvailable()).thenReturn(fileManagementParseService);
        when(sessionTokenService.requireSession(request)).thenReturn(session);
        when(fileManagementParseService.exportOverviewItems(session, 20001L)).thenReturn(exportFile);

        ResponseEntity<Resource> response = controller.exportOverviewItems(20001L, request);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(FileParseQueryViewService.OVERVIEW_EXPORT_CONTENT_TYPE, response.getHeaders().getContentType().toString());
        assertEquals(3, response.getHeaders().getContentLength());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("filename*=UTF-8''"));
        assertEquals(3, response.getBody().contentLength());
        verify(fileManagementParseService).exportOverviewItems(session, 20001L);
    }

    @Test
    void shouldReturnVersionsWhenServiceAvailable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthenticatedSession session = new AuthenticatedSession(10001L, 1L, 0);
        FileParseVersionListView view = new FileParseVersionListView();
        view.setTargetPlanId(4005L);
        view.setTotal(1);

        when(fileManagementParseServiceProvider.getIfAvailable()).thenReturn(fileManagementParseService);
        when(sessionTokenService.requireSession(request)).thenReturn(session);
        when(fileManagementParseService.listVersions(session, 4005L, 1, 20)).thenReturn(view);

        FileParseVersionListView result = controller.versions(4005L, 1, 20, request);

        assertEquals(4005L, result.getTargetPlanId());
        assertEquals(1, result.getTotal());
    }

    @Test
    void shouldReturnLogisticsChannelActivationsWhenServiceAvailable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthenticatedSession session = new AuthenticatedSession(10002L, 2L, 1);
        FileParseLogisticsChannelActivationView view = new FileParseLogisticsChannelActivationView();
        view.setTargetPlanId(4005L);
        view.setVersionId(70005L);
        view.setOwnerUserId(10002L);
        view.setSelectedChannelKeys(List.of("yite_ae_fbn_sea"));

        when(fileManagementParseServiceProvider.getIfAvailable()).thenReturn(fileManagementParseService);
        when(sessionTokenService.requireSession(request)).thenReturn(session);
        when(fileManagementParseService.listLogisticsChannelActivations(session, 4005L, 70005L)).thenReturn(view);

        FileParseLogisticsChannelActivationView result = controller.logisticsChannelActivations(4005L, 70005L, request);

        assertEquals(4005L, result.getTargetPlanId());
        assertEquals(List.of("yite_ae_fbn_sea"), result.getSelectedChannelKeys());
    }

    @Test
    void shouldSaveLogisticsChannelActivationsWhenServiceAvailable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthenticatedSession session = new AuthenticatedSession(10002L, 2L, 1);
        FileParseLogisticsChannelActivationCommand command = new FileParseLogisticsChannelActivationCommand();
        command.setTargetPlanId(4005L);
        command.setVersionId(70005L);
        command.setSelectedChannelKeys(List.of("yite_ae_fbn_sea", "yite_ae_fbn_air"));
        FileParseLogisticsChannelActivationView view = new FileParseLogisticsChannelActivationView();
        view.setTargetPlanId(4005L);
        view.setVersionId(70005L);
        view.setSelectedChannelKeys(command.getSelectedChannelKeys());

        when(fileManagementParseServiceProvider.getIfAvailable()).thenReturn(fileManagementParseService);
        when(sessionTokenService.requireSession(request)).thenReturn(session);
        when(fileManagementParseService.saveLogisticsChannelActivations(session, command)).thenReturn(view);

        FileParseLogisticsChannelActivationView result = controller.saveLogisticsChannelActivations(command, request);

        assertEquals(70005L, result.getVersionId());
        assertEquals(2, result.getSelectedChannelKeys().size());
        verify(fileManagementParseService).saveLogisticsChannelActivations(session, command);
    }

    @Test
    void shouldReturnVersionItemsWhenServiceAvailable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthenticatedSession session = new AuthenticatedSession(10001L, 1L, 0);
        FileParseVersionItemsView view = new FileParseVersionItemsView();
        view.setVersionId(70005L);
        view.setTotal(1);

        when(fileManagementParseServiceProvider.getIfAvailable()).thenReturn(fileManagementParseService);
        when(sessionTokenService.requireSession(request)).thenReturn(session);
        when(fileManagementParseService.listVersionItems(session, 70005L, 1, 1000)).thenReturn(view);

        FileParseVersionItemsView result = controller.versionItems(70005L, 1, 1000, request);

        assertEquals(70005L, result.getVersionId());
        assertEquals(1, result.getTotal());
    }

    @Test
    void shouldPublishTaskWhenServiceAvailable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthenticatedSession session = new AuthenticatedSession(10001L, 1L, 0);
        FileParsePublishCommand command = new FileParsePublishCommand();
        command.setExpectedResultId(40001L);
        FileParsePublishView view = new FileParsePublishView();
        view.setVersionId(70006L);
        view.setVersionNo("LOGISTICS-YITE-20260513-70006");
        view.setStatus("active");

        when(fileManagementParseServiceProvider.getIfAvailable()).thenReturn(fileManagementParseService);
        when(sessionTokenService.requireSession(request)).thenReturn(session);
        when(fileManagementParseService.publishTask(session, 20001L, command, "idem-publish-1")).thenReturn(view);

        FileParsePublishView result = controller.publishTask(20001L, command, "idem-publish-1", request);

        assertEquals(70006L, result.getVersionId());
        assertEquals("active", result.getStatus());
    }

    @Test
    void shouldDownloadArchivedFile() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthenticatedSession session = new AuthenticatedSession(10001L, 1L, 0);
        Path filePath = tempDir.resolve("quote.xlsx");
        Files.writeString(filePath, "quote");

        when(fileManagementParseServiceProvider.getIfAvailable()).thenReturn(fileManagementParseService);
        when(sessionTokenService.requireSession(request)).thenReturn(session);
        when(fileManagementParseService.resolveArchivedFile(session, 10001L))
                .thenReturn(new FileParseArchivedFile(filePath, "义特FBN报价.xlsx", "application/vnd.ms-excel"));

        ResponseEntity<Resource> response = controller.download(10001L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("filename*=UTF-8''"));
    }
}
