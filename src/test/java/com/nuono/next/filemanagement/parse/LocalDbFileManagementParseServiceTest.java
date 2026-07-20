package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.ai.AiCapabilityService;
import com.nuono.next.ai.AiStructuredTextCommand;
import com.nuono.next.ai.AiStructuredTextResult;
import com.nuono.next.auth.AuthenticatedSession;
import com.nuono.next.infrastructure.mapper.FileManagementParseMapper;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LocalDbFileManagementParseServiceTest {

    @Mock
    private FileManagementParseMapper fileManagementParseMapper;

    @Mock
    private AiCapabilityService aiCapabilityService;

    @TempDir
    private Path tempDir;

    private LocalDbFileManagementParseService service;

    @BeforeEach
    void setUp() {
        FileParseStorageProperties storageProperties = new FileParseStorageProperties();
        storageProperties.setRootDir(tempDir);
        ObjectMapper objectMapper = new ObjectMapper();
        FileParseResultItemViewAssembler itemViewAssembler = new FileParseResultItemViewAssembler(objectMapper);
        service = new LocalDbFileManagementParseService(
                fileManagementParseMapper,
                storageProperties,
                new FileParseUploadArchiveService(fileManagementParseMapper, storageProperties),
                mock(FileParseTaskCatalogService.class),
                new FileParseActionPolicy(),
                FileParseInputExtractionService.withDefaultExtractors(),
                new FileParseStructuredAiService(aiCapabilityService, objectMapper),
                new FileParseResultDiffService(fileManagementParseMapper, objectMapper),
                new FileParseResultPersistenceService(fileManagementParseMapper),
                new FileParseItemReviewService(
                        fileManagementParseMapper,
                        itemViewAssembler
                ),
                new FileParseQueryViewService(fileManagementParseMapper, itemViewAssembler),
                new FileParsePublishService(fileManagementParseMapper, itemViewAssembler, null, null),
                new FileParseLogisticsChannelActivationService(fileManagementParseMapper, itemViewAssembler)
        );
    }

    @Test
    void shouldDeletePublishedParseTaskArtifactsAndActiveBusinessResults() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTaskRow publishedTask = task(20077L, 4001L, "published", 1);
        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20077L)).thenReturn(publishedTask);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4001L, 0, true))
                .thenReturn(targetPlan(4001L, "commission_ksa", "佣金-KSA", "official_fee"));
        when(fileManagementParseMapper.selectVersionIdsBySourceTask(20077L)).thenReturn(List.of(70077L));
        when(fileManagementParseMapper.softDeleteTask(20077L, 10001L)).thenReturn(1);

        service.deleteTask(new AuthenticatedSession(10001L, 1L, 0), 20077L);

        verify(fileManagementParseMapper).softDeleteActiveVersionsByVersionIds(List.of(70077L), 10001L);
        verify(fileManagementParseMapper).markLogisticsServiceLinesDeletedBySourceVersionIds(List.of(70077L), 10001L);
        verify(fileManagementParseMapper).markLogisticsCargoCategoriesDeletedBySourceVersionIds(List.of(70077L), 10001L);
        verify(fileManagementParseMapper).markLogisticsPriceRulesDeletedBySourceVersionIds(List.of(70077L), 10001L);
        verify(fileManagementParseMapper).markLogisticsSurchargeRulesDeletedBySourceVersionIds(List.of(70077L), 10001L);
        verify(fileManagementParseMapper).markLogisticsBillingRulesDeletedBySourceVersionIds(List.of(70077L), 10001L);
        verify(fileManagementParseMapper).markLogisticsWarehouseFeeRulesDeletedBySourceVersionIds(List.of(70077L), 10001L);
        verify(fileManagementParseMapper).markLogisticsRestrictionRulesDeletedBySourceVersionIds(List.of(70077L), 10001L);
        verify(fileManagementParseMapper).markOfficialOutboundSizeClassificationRulesDeletedBySourceVersionIds(List.of(70077L), 10001L);
        verify(fileManagementParseMapper).markOfficialOutboundFeeWeightSlabRulesDeletedBySourceVersionIds(List.of(70077L), 10001L);
        verify(fileManagementParseMapper).markOfficialOutboundFeeCalculationPoliciesDeletedBySourceVersionIds(List.of(70077L), 10001L);
        verify(fileManagementParseMapper).softDeleteLogisticsChannelActivationsByVersionIds(List.of(70077L), 10001L);
        verify(fileManagementParseMapper).softDeleteVersionItemsByVersionIds(List.of(70077L), 10001L);
        verify(fileManagementParseMapper).softDeleteVersionsByIds(List.of(70077L), 10001L);
        verify(fileManagementParseMapper).deleteCurrentResultByTask(20077L);
        verify(fileManagementParseMapper).softDeleteTaskInputsByTask(20077L, 10001L);
        verify(fileManagementParseMapper).softDeleteFileAssetsByTask(20077L, 10001L);
        verify(fileManagementParseMapper).softDeleteSourceRowsByTask(20077L, 10001L);
        verify(fileManagementParseMapper).deleteResultItemSourcesByTask(20077L);
        verify(fileManagementParseMapper).softDeleteValidationIssuesByTask(20077L, 10001L);
        verify(fileManagementParseMapper).softDeleteItemReviewsByTask(20077L, 10001L);
        verify(fileManagementParseMapper).softDeleteResultItemsByTask(20077L, 10001L);
        verify(fileManagementParseMapper).markResultsDeletedByTask(20077L, 10001L);
        verify(fileManagementParseMapper).deleteAiChunksByTask(20077L);
        verify(fileManagementParseMapper).softDeleteTask(20077L, 10001L);
    }

    @Test
    void shouldResetStaleParsingTaskAndRetryAutomatically() {
        LocalDbFileManagementParseService spyService = spy(service);
        FileParseTaskRow staleTask = task(20001L, 4005L, "parsing", 1);
        when(fileManagementParseMapper.selectStaleParsingTasks(900, 2)).thenReturn(List.of(staleTask));
        when(fileManagementParseMapper.resetStaleParsingTaskForRetry(
                eq(20001L),
                eq(900),
                eq("PARSE_STALE_RETRYING"),
                anyString(),
                eq(10003L)
        )).thenReturn(1);
        doReturn(new FileParseTaskRunView())
                .when(spyService)
                .startParseTask(any(AuthenticatedSession.class), eq(20001L));

        int recovered = spyService.recoverStaleParsingTasks(2, 900, 3, 10003L);

        assertEquals(1, recovered);
        verify(fileManagementParseMapper).markOpenAiChunksFailedByTask(20001L, 10003L);
        verify(fileManagementParseMapper).resetStaleParsingTaskForRetry(
                eq(20001L),
                eq(900),
                eq("PARSE_STALE_RETRYING"),
                anyString(),
                eq(10003L)
        );
        verify(spyService).startParseTask(any(AuthenticatedSession.class), eq(20001L));
    }

    @Test
    void shouldFailStaleParsingTaskAfterMaxAutomaticRetries() {
        LocalDbFileManagementParseService spyService = spy(service);
        FileParseTaskRow staleTask = task(20002L, 4005L, "parsing", 3);
        when(fileManagementParseMapper.selectStaleParsingTasks(900, 2)).thenReturn(List.of(staleTask));
        when(fileManagementParseMapper.markStaleParsingTaskFinalFailed(
                eq(20002L),
                eq(900),
                eq("PARSE_STALE_TIMEOUT"),
                anyString(),
                eq(10003L)
        )).thenReturn(1);

        int recovered = spyService.recoverStaleParsingTasks(2, 900, 3, 10003L);

        assertEquals(1, recovered);
        verify(fileManagementParseMapper).markOpenAiChunksFailedByTask(20002L, 10003L);
        verify(fileManagementParseMapper).markStaleParsingTaskFinalFailed(
                eq(20002L),
                eq(900),
                eq("PARSE_STALE_TIMEOUT"),
                anyString(),
                eq(10003L)
        );
        verify(fileManagementParseMapper, never()).resetStaleParsingTaskForRetry(
                anyLong(),
                anyInt(),
                anyString(),
                anyString(),
                anyLong()
        );
        verify(spyService, never()).startParseTask(any(AuthenticatedSession.class), anyLong());
    }

    @Test
    void shouldRetryScheduledTransientAiFailureAutomatically() {
        LocalDbFileManagementParseService spyService = spy(service);
        FileParseTaskRow retryableTask = task(20003L, 4005L, "failed", 3);
        retryableTask.setFailureCode("OPENAI_HTTP_503");
        retryableTask.setFailureMessage("system cpu overloaded");
        FileParseTaskRunView runView = new FileParseTaskRunView();
        runView.setStatus("review_required");
        when(fileManagementParseMapper.selectStaleParsingTasks(900, 2)).thenReturn(List.of());
        when(fileManagementParseMapper.selectRetryableFailedParseTasks(2)).thenReturn(List.of(retryableTask));
        doReturn(runView)
                .when(spyService)
                .startParseTask(any(AuthenticatedSession.class), eq(20003L));

        int recovered = spyService.recoverStaleParsingTasks(2, 900, 5, 10003L);

        assertEquals(1, recovered);
        verify(spyService).startParseTask(any(AuthenticatedSession.class), eq(20003L));
        verify(fileManagementParseMapper, never()).markRetryableParseTaskFinalFailed(
                anyLong(),
                anyString(),
                anyString(),
                anyLong()
        );
    }

    @Test
    void shouldStopRetryableFailureAfterMaxAutomaticRetries() {
        LocalDbFileManagementParseService spyService = spy(service);
        FileParseTaskRow retryableTask = task(20004L, 4005L, "failed", 5);
        retryableTask.setFailureCode("OPENAI_HTTP_503");
        retryableTask.setFailureMessage("system cpu overloaded");
        when(fileManagementParseMapper.selectStaleParsingTasks(900, 2)).thenReturn(List.of());
        when(fileManagementParseMapper.selectRetryableFailedParseTasks(2)).thenReturn(List.of(retryableTask));
        when(fileManagementParseMapper.markRetryableParseTaskFinalFailed(
                eq(20004L),
                eq("PARSE_AUTO_RETRY_EXHAUSTED"),
                anyString(),
                eq(10003L)
        )).thenReturn(1);

        int recovered = spyService.recoverStaleParsingTasks(2, 900, 5, 10003L);

        assertEquals(1, recovered);
        verify(fileManagementParseMapper).markRetryableParseTaskFinalFailed(
                eq(20004L),
                eq("PARSE_AUTO_RETRY_EXHAUSTED"),
                anyString(),
                eq(10003L)
        );
        verify(spyService, never()).startParseTask(any(AuthenticatedSession.class), anyLong());
    }

    @Test
    void shouldCreateReadingTaskWithFileAndTextInputs() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4005L, "logistics_yite", "物流-义特", "logistics_rule");
        FileParseFileAssetRow asset = uploadedAsset(10001L, 4005L, 5105L, 10001L, "义特FBN报价.xlsx", "xlsx");

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4005L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.nextTaskId()).thenReturn(20001L);
        when(fileManagementParseMapper.nextTaskInputId()).thenReturn(30001L, 30002L);
        when(fileManagementParseMapper.insertTask(
                anyLong(),
                anyString(),
                anyString(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                nullable(Long.class),
                anyInt(),
                nullable(String.class),
                nullable(String.class),
                anyString(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.selectFileAsset(10001L)).thenReturn(asset);
        when(fileManagementParseMapper.bindFileAssetToTask(10001L, 20001L, 10001L)).thenReturn(1);
        when(fileManagementParseMapper.insertTaskInput(
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                nullable(Long.class),
                nullable(String.class),
                anyString(),
                anyInt(),
                anyLong()
        )).thenReturn(1);

        FileParseCreateTaskCommand command = new FileParseCreateTaskCommand();
        command.setDocumentTitle("义特 FBN 报价 2026-05");
        command.setTargetPlanId(4005L);
        FileParseTaskInputCommand fileInput = new FileParseTaskInputCommand();
        fileInput.setFileAssetId(10001L);
        FileParseTaskInputCommand textInput = new FileParseTaskInputCommand();
        textInput.setInputType("manual_text");
        textInput.setTextContent("补充说明：阿联酋 FBN 迪拜。");
        command.setInputItems(List.of(fileInput, textInput));

        FileParseTaskDetailView task = service.createTask(new AuthenticatedSession(10001L, 1L, 0), command, "idem-1");

        assertEquals(20001L, task.getId());
        assertEquals("reading", task.getStatus());
        assertEquals("global", task.getDataScopeType());
        assertEquals("global:*", task.getDataScopeKey());
        assertEquals(20001L, task.getDocumentGroupId());
        assertEquals(1, task.getIterationNo());
        assertEquals(2, task.getInputItems().size());
        assertEquals("excel", task.getInputItems().get(0).getInputType());
        assertEquals("manual_text", task.getInputItems().get(1).getInputType());
        verify(fileManagementParseMapper).bindFileAssetToTask(10001L, 20001L, 10001L);
    }

    @Test
    void shouldCreateSourceUpdateTaskUnderSameDocumentGroup() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4001L, "commission_ksa", "佣金-KSA", "official_commission");
        FileParseTaskRow parentTask = task(20024L, 4001L, "published", 0);
        parentTask.setDocumentTitle("Noon 佣金 2026-05");
        parentTask.setStandardVersionId(2001L);
        parentTask.setDocumentGroupId(20024L);
        parentTask.setIterationNo(1);
        FileParseFileAssetRow asset = uploadedAsset(10002L, 4001L, 2001L, 10001L, "Noon佣金新版.xlsx", "xlsx");

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20024L)).thenReturn(parentTask);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4001L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.nextTaskId()).thenReturn(20025L);
        when(fileManagementParseMapper.selectMaxIterationNo(20024L)).thenReturn(1);
        when(fileManagementParseMapper.nextTaskInputId()).thenReturn(30003L);
        when(fileManagementParseMapper.insertTask(
                anyLong(),
                anyString(),
                anyString(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                nullable(Long.class),
                anyInt(),
                nullable(String.class),
                nullable(String.class),
                anyString(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.selectFileAsset(10002L)).thenReturn(asset);
        when(fileManagementParseMapper.bindFileAssetToTask(10002L, 20025L, 10001L)).thenReturn(1);
        when(fileManagementParseMapper.insertTaskInput(
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                nullable(Long.class),
                nullable(String.class),
                anyString(),
                anyInt(),
                anyLong()
        )).thenReturn(1);

        FileParseCreateTaskCommand command = new FileParseCreateTaskCommand();
        command.setParentTaskId(20024L);
        command.setDocumentTitle("Noon 佣金 2026-05");
        FileParseTaskInputCommand fileInput = new FileParseTaskInputCommand();
        fileInput.setFileAssetId(10002L);
        command.setInputItems(List.of(fileInput));

        FileParseTaskDetailView task = service.createTask(new AuthenticatedSession(10001L, 1L, 0), command, "idem-update-1");

        assertEquals(20025L, task.getId());
        assertEquals(20024L, task.getDocumentGroupId());
        assertEquals(20024L, task.getParentTaskId());
        assertEquals(2, task.getIterationNo());
        assertEquals(4001L, task.getTargetPlanId());
        assertEquals("reading", task.getStatus());
    }

    @Test
    void shouldStartParseTaskWithFileAttachmentAndGenerateResult() throws Exception {
        Files.createDirectories(tempDir.resolve("20260513"));
        Files.writeString(tempDir.resolve("20260513/10001-quote.pdf"), "%PDF-1.4\nquote-content");

        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4005L, "logistics_yite", "物流-义特", "logistics_rule");
        FileParseTaskRow task = task(20001L, 4005L, "reading", 0);
        FileParseTaskInputRow fileInput = taskInput(30001L, "pdf", "义特FBN报价.pdf", "pdf", "20260513/10001-quote.pdf", null);
        FileParseTaskInputRow textInput = taskInput(30002L, "manual_text", "补充说明", null, null, "阿联酋 FBN 迪拜");

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20001L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4005L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.markTaskParsing(anyLong(), anyString(), anyLong())).thenReturn(1);
        when(fileManagementParseMapper.selectTaskInputs(20001L)).thenReturn(List.of(fileInput, textInput));
        when(fileManagementParseMapper.selectStandardVersion(5105L)).thenReturn(standardVersion(5105L));
        when(fileManagementParseMapper.selectItemStandards(5105L)).thenReturn(List.of(logisticsItemStandard()));
        when(aiCapabilityService.createStructuredText(any(AiStructuredTextCommand.class))).thenReturn(aiSuccess());
        when(fileManagementParseMapper.nextResultId()).thenReturn(40001L);
        when(fileManagementParseMapper.insertResult(
                anyLong(),
                anyString(),
                anyLong(),
                anyLong(),
                anyLong(),
                nullable(Long.class),
                anyString(),
                anyString(),
                anyString(),
                nullable(String.class),
                anyString(),
                anyString(),
                anyString(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.nextResultItemId()).thenReturn(50001L);
        when(fileManagementParseMapper.insertResultItem(
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                nullable(String.class),
                anyString(),
                anyString(),
                nullable(String.class),
                nullable(String.class),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                nullable(String.class),
                anyInt(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.upsertCurrentResult(20001L, 40001L)).thenReturn(1);
        when(fileManagementParseMapper.markTaskReviewRequired(anyLong(), anyLong(), anyString(), anyLong())).thenReturn(1);
        stubStableProcessTables(40001L, List.of());

        FileParseTaskRunView run = service.startParseTask(new AuthenticatedSession(10001L, 1L, 0), 20001L);

        assertEquals(20001L, run.getTaskId());
        assertEquals("review_required", run.getStatus());
        assertEquals(1, run.getParseAttemptCount());
        assertEquals(2, run.getExtractions().size());
        assertEquals("attached", run.getExtractions().get(0).getStatus());
        assertEquals("extracted", run.getExtractions().get(1).getStatus());
        ArgumentCaptor<AiStructuredTextCommand> commandCaptor = ArgumentCaptor.forClass(AiStructuredTextCommand.class);
        verify(aiCapabilityService).createStructuredText(commandCaptor.capture());
        assertEquals(1, commandCaptor.getValue().getInputAttachments().size());
        assertEquals("义特FBN报价.pdf", commandCaptor.getValue().getInputAttachments().get(0).getFileName());
    }

    @Test
    void shouldCallAiAgainWhenInputFileSignatureIsUnchanged() throws Exception {
        Files.createDirectories(tempDir.resolve("20260515"));
        Files.writeString(tempDir.resolve("20260515/ksa.pdf"), "%PDF-1.4\ncommission-content");

        Long historicalResultId = 40016L;
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4001L, "commission_ksa", "佣金-KSA", "official_commission");
        FileParseTaskRow task = task(20031L, 4001L, "reading", 0);
        task.setStandardVersionId(2001L);
        task.setBaseVersionId(70009L);
        task.setDocumentGroupId(20024L);
        task.setParentTaskId(20024L);
        task.setIterationNo(2);
        FileParseTaskInputRow fileInput = taskInput(30031L, "pdf", "fulfilled-by-noon-fbn-fees-in-ksa.pdf", "pdf", "20260515/ksa.pdf", null);
        fileInput.setFileAssetId(10018L);
        fileInput.setSha256Hash("same-file-hash");

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20031L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4001L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.markTaskParsing(anyLong(), anyString(), anyLong())).thenReturn(1);
        when(fileManagementParseMapper.selectTaskInputs(20031L)).thenReturn(List.of(fileInput));
        when(fileManagementParseMapper.selectStandardVersion(2001L)).thenReturn(standardVersion(2001L));
        when(fileManagementParseMapper.selectItemStandards(2001L)).thenReturn(List.of(commissionItemStandard()));
        when(aiCapabilityService.createStructuredText(any(AiStructuredTextCommand.class))).thenReturn(aiSuccessWithDuplicateCommissionRows(false));
        when(fileManagementParseMapper.nextResultId()).thenReturn(40017L);
        when(fileManagementParseMapper.insertResult(
                anyLong(),
                anyString(),
                anyLong(),
                anyLong(),
                anyLong(),
                nullable(Long.class),
                anyString(),
                anyString(),
                anyString(),
                nullable(String.class),
                anyString(),
                anyString(),
                anyString(),
                anyLong()
        )).thenReturn(1);
        stubStableProcessTables(40017L, List.of());
        when(fileManagementParseMapper.nextResultItemId()).thenReturn(50017L);
        when(fileManagementParseMapper.insertResultItem(
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                nullable(String.class),
                anyString(),
                anyString(),
                nullable(String.class),
                nullable(String.class),
                anyString(),
                anyString(),
                nullable(String.class),
                anyString(),
                nullable(String.class),
                anyInt(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.upsertCurrentResult(20031L, 40017L)).thenReturn(1);
        when(fileManagementParseMapper.markTaskReviewRequired(anyLong(), anyLong(), anyString(), anyLong())).thenReturn(1);

        FileParseTaskRunView run = service.startParseTask(new AuthenticatedSession(10001L, 1L, 0), 20031L);

        assertEquals("review_required", run.getStatus());
        assertEquals(40017L, run.getResultId());
        assertNotEquals(historicalResultId, run.getResultId());
        assertEquals(1, run.getResultItemCount());
        assertTrue(run.getMessage().contains("AI 结构化解析"));
        assertFalse(run.getMessage().contains("历史"));
        assertFalse(run.getMessage().contains("复用"));
        verify(aiCapabilityService).createStructuredText(any(AiStructuredTextCommand.class));
    }

    @Test
    void shouldClipNearRealisticCommissionSampleBeforeCallingAi() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4001L, "commission_ksa", "佣金-KSA", "official_commission");
        FileParseTaskRow task = task(20033L, 4001L, "reading", 0);
        task.setDocumentTitle("Noon KSA Fulfilled by Noon Fees");
        task.setStandardVersionId(2001L);
        FileParseTaskInputRow textInput = taskInput(
                30033L,
                "manual_text",
                "fulfilled-by-noon-fbn-fees-in-ksa.txt",
                null,
                null,
                String.join("\n",
                        "Noon seller fees",
                        "1. Referral Fees",
                        "Referral Fees as a % of the sale price",
                        "Colour Cosmetics All 15% for Generic brand, 10% for all other brands",
                        "Other Categories",
                        "Sports & Outdoors All - 20% for item with sales price of 30 SAR or less",
                        "- 13% for item with a total sales price greater than 30 SAR",
                        "FAQ",
                        "Value Added Services 99%"
                )
        );
        textInput.setTaskId(20033L);

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20033L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4001L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.markTaskParsing(anyLong(), anyString(), anyLong())).thenReturn(1);
        when(fileManagementParseMapper.selectTaskInputs(20033L)).thenReturn(List.of(textInput));
        when(fileManagementParseMapper.selectStandardVersion(2001L)).thenReturn(standardVersion(2001L));
        when(fileManagementParseMapper.selectItemStandards(2001L)).thenReturn(List.of(commissionItemStandard()));
        when(aiCapabilityService.createStructuredText(any(AiStructuredTextCommand.class)))
                .thenReturn(AiStructuredTextResult.failure("AI_DISABLED", "AI_DISABLED", "AI capability is disabled"));
        when(fileManagementParseMapper.markTaskFailed(anyLong(), anyString(), anyString(), anyString(), anyLong())).thenReturn(1);
        stubStableProcessStart();
        when(fileManagementParseMapper.markAiChunkFailed(anyLong(), anyLong(), anyString(), anyString(), anyLong())).thenReturn(1);

        FileParseTaskRunView run = service.startParseTask(new AuthenticatedSession(10001L, 1L, 0), 20033L);

        assertEquals("failed", run.getStatus());
        ArgumentCaptor<AiStructuredTextCommand> commandCaptor = ArgumentCaptor.forClass(AiStructuredTextCommand.class);
        verify(aiCapabilityService).createStructuredText(commandCaptor.capture());
        String prompt = commandCaptor.getValue().getPrompt();
        assertTrue(prompt.contains("1. Referral Fees"));
        assertTrue(prompt.contains("Colour Cosmetics"));
        assertTrue(prompt.contains("Sports & Outdoors"));
        assertFalse(prompt.contains("FAQ"));
        assertFalse(prompt.contains("Value Added Services 99%"));
    }

    @Test
    void shouldRecordSourceLineageWithAiChunkWhenParserSuppliesSourceRowIds() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4005L, "logistics_yite", "物流-义特", "logistics_rule");
        FileParseTaskRow task = task(20032L, 4005L, "reading", 0);
        FileParseTaskInputRow textInput = taskInput(
                30032L,
                "manual_text",
                "物流规则文本",
                null,
                null,
                "义特 + UAE + Dubai + 海运 + 卡牌 / 普货 + 26 CNY/KG，最低 12KG\n"
                        + "义特 + UAE + Dubai + 空运 + 带电产品 + 40 CNY/KG，最低 20 AED"
        );
        FileParseResultItemRow persistedItem = resultItem("pending", "added");
        persistedItem.setId(50032L);
        persistedItem.setTaskId(20032L);
        persistedItem.setResultId(40032L);

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20032L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4005L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.markTaskParsing(anyLong(), anyString(), anyLong())).thenReturn(1);
        when(fileManagementParseMapper.selectTaskInputs(20032L)).thenReturn(List.of(textInput));
        when(fileManagementParseMapper.selectStandardVersion(5105L)).thenReturn(standardVersion(5105L));
        when(fileManagementParseMapper.selectItemStandards(5105L)).thenReturn(List.of(logisticsItemStandard()));
        when(aiCapabilityService.createStructuredText(any(AiStructuredTextCommand.class)))
                .thenReturn(aiSuccessWithLogisticsSourceRowId(35002L));
        when(fileManagementParseMapper.nextResultId()).thenReturn(40032L);
        when(fileManagementParseMapper.insertResult(
                anyLong(),
                anyString(),
                anyLong(),
                anyLong(),
                anyLong(),
                nullable(Long.class),
                anyString(),
                anyString(),
                anyString(),
                nullable(String.class),
                anyString(),
                anyString(),
                anyString(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.nextResultItemId()).thenReturn(50032L);
        when(fileManagementParseMapper.insertResultItem(
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                nullable(String.class),
                anyString(),
                anyString(),
                nullable(String.class),
                nullable(String.class),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                nullable(String.class),
                anyInt(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.upsertCurrentResult(20032L, 40032L)).thenReturn(1);
        when(fileManagementParseMapper.markTaskReviewRequired(anyLong(), anyLong(), anyString(), anyLong())).thenReturn(1);
        stubStableProcessTables(40032L, List.of(persistedItem));
        when(fileManagementParseMapper.nextResultItemSourceId()).thenReturn(55032L);
        when(fileManagementParseMapper.insertResultItemSource(
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyLong()
        )).thenReturn(1);

        FileParseTaskRunView run = service.startParseTask(new AuthenticatedSession(10001L, 1L, 0), 20032L);

        assertEquals("review_required", run.getStatus());
        verify(fileManagementParseMapper).insertResultItemSource(
                55032L,
                20032L,
                40032L,
                50032L,
                35002L,
                36001L,
                "primary",
                "high",
                persistedItem.getNaturalKey(),
                10001L
        );
    }

    @Test
    void shouldPersistLogisticsManualSupplementAndMissingSectionCoverageIssues() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4006L, "logistics_et", "物流-易通", "logistics_rule");
        FileParseTaskRow task = task(20088L, 4006L, "reading", 0);
        FileParseTaskInputRow textInput = taskInput(
                30088L,
                "manual_text",
                "物流人工补充",
                null,
                null,
                "UAE air FBN delivery 5-7 days\n"
                        + "人工补充：Saudi sea 基础价格整体上调 10%，需要新版报价确认"
        );
        FileParseResultItemRow persistedItem = resultItem("confirmed", "added");
        persistedItem.setId(50088L);
        persistedItem.setTaskId(20088L);
        persistedItem.setResultId(40088L);
        persistedItem.setItemType(FileParseLogisticsQuoteStandard.SERVICE_LINE);
        persistedItem.setSortNo(1);
        persistedItem.setNaturalKey("et|UAE|FBN|air|warehouse_to_fbn|Dubai FBN warehouse");

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20088L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4006L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.markTaskParsing(anyLong(), anyString(), anyLong())).thenReturn(1);
        when(fileManagementParseMapper.selectTaskInputs(20088L)).thenReturn(List.of(textInput));
        when(fileManagementParseMapper.selectStandardVersion(5105L)).thenReturn(standardVersion(5105L));
        when(fileManagementParseMapper.selectItemStandards(5105L)).thenReturn(logisticsQuotePackageItemStandards());
        when(aiCapabilityService.createStructuredText(any(AiStructuredTextCommand.class)))
                .thenReturn(aiSuccessWithStructuredServiceLine(35001L));
        when(fileManagementParseMapper.nextResultId()).thenReturn(40088L);
        when(fileManagementParseMapper.insertResult(
                anyLong(),
                anyString(),
                anyLong(),
                anyLong(),
                anyLong(),
                nullable(Long.class),
                anyString(),
                anyString(),
                anyString(),
                nullable(String.class),
                anyString(),
                anyString(),
                anyString(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.nextResultItemId()).thenReturn(50088L);
        when(fileManagementParseMapper.insertResultItem(
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                nullable(String.class),
                anyString(),
                anyString(),
                nullable(String.class),
                nullable(String.class),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                nullable(String.class),
                anyInt(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.upsertCurrentResult(20088L, 40088L)).thenReturn(1);
        when(fileManagementParseMapper.markTaskReviewRequired(anyLong(), anyLong(), anyString(), anyLong())).thenReturn(1);
        stubStableProcessTables(40088L, List.of(persistedItem));
        when(fileManagementParseMapper.nextValidationIssueId()).thenReturn(90088L, 90089L);
        when(fileManagementParseMapper.insertValidationIssue(
                anyLong(),
                anyLong(),
                anyLong(),
                nullable(Long.class),
                nullable(Long.class),
                nullable(Long.class),
                anyString(),
                anyString(),
                nullable(String.class),
                anyString(),
                anyString(),
                anyLong()
        )).thenReturn(1);

        FileParseTaskRunView run = service.startParseTask(new AuthenticatedSession(10001L, 1L, 0), 20088L);

        assertEquals("review_required", run.getStatus());
        verify(fileManagementParseMapper).insertValidationIssue(
                eq(90088L),
                eq(20088L),
                eq(40088L),
                nullable(Long.class),
                eq(35002L),
                eq(36001L),
                eq("manual_relative_change_unresolved"),
                eq("hard_error"),
                nullable(String.class),
                eq("人工补充文本包含相对变化，但没有链接到可安全计算的物流输出行。"),
                argThat(details -> details.contains("\"sourceRowId\":35002") && details.contains("\"sourceType\":\"manual_text_block\"")),
                eq(10001L)
        );
        verify(fileManagementParseMapper).insertValidationIssue(
                eq(90089L),
                eq(20088L),
                eq(40088L),
                nullable(Long.class),
                eq(35002L),
                eq(36001L),
                eq("logistics_section_missing"),
                eq("hard_error"),
                nullable(String.class),
                eq("源内容提到了物流服务段，但解析结果缺少对应服务线路。"),
                argThat(details -> details.contains("\"sourceRowId\":35002")
                        && details.contains("\"country\":\"KSA\"")
                        && details.contains("\"transportMode\":\"sea\"")),
                eq(10001L)
        );
    }

    @Test
    void shouldRecordAiChunkLineageBySourceRowAfterCommissionStabilization() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4001L, "commission_ksa", "佣金-KSA", "official_commission");
        FileParseTaskRow task = task(20041L, 4001L, "reading", 0);
        FileParseTaskInputRow textInput = taskInput(
                30041L,
                "manual_text",
                "佣金规则多分块文本",
                null,
                null,
                commissionRowsWithSecondChunkRateLine()
        );
        textInput.setTaskId(20041L);
        FileParseResultItemRow firstPersistedItem = resultItem("pending", "added");
        firstPersistedItem.setId(50041L);
        firstPersistedItem.setTaskId(20041L);
        firstPersistedItem.setResultId(40041L);
        firstPersistedItem.setTargetPlanId(4001L);
        firstPersistedItem.setItemType("commission_rule");
        firstPersistedItem.setSortNo(1);
        firstPersistedItem.setNaturalKey("Fashion > Watches");
        FileParseResultItemRow secondPersistedItem = resultItem("pending", "added");
        secondPersistedItem.setId(50042L);
        secondPersistedItem.setTaskId(20041L);
        secondPersistedItem.setResultId(40041L);
        secondPersistedItem.setTargetPlanId(4001L);
        secondPersistedItem.setItemType("commission_rule");
        secondPersistedItem.setSortNo(2);
        secondPersistedItem.setNaturalKey("Other Categories > Sports & Outdoors");

        List<Long> persistedSourceRowIds = new ArrayList<>();
        for (long id = 35001L; id <= 35031L; id++) {
            persistedSourceRowIds.add(id);
        }
        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20041L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4001L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.markTaskParsing(anyLong(), anyString(), anyLong())).thenReturn(1);
        when(fileManagementParseMapper.selectTaskInputs(20041L)).thenReturn(List.of(textInput));
        when(fileManagementParseMapper.selectStandardVersion(5105L)).thenReturn(standardVersion(5105L));
        when(fileManagementParseMapper.selectItemStandards(5105L)).thenReturn(List.of(commissionItemStandard()));
        when(fileManagementParseMapper.nextSourceRowId()).thenReturn(
                persistedSourceRowIds.get(0),
                persistedSourceRowIds.subList(1, persistedSourceRowIds.size()).toArray(new Long[0])
        );
        when(fileManagementParseMapper.insertSourceRow(
                anyLong(),
                anyLong(),
                any(FileParseSourceRowDraft.class),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.nextAiChunkId()).thenReturn(36001L, 36002L);
        when(fileManagementParseMapper.insertAiChunk(
                anyLong(),
                anyLong(),
                anyInt(),
                anyString(),
                anyString(),
                anyInt(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyLong()
        )).thenReturn(1);
        when(aiCapabilityService.createStructuredText(any(AiStructuredTextCommand.class)))
                .thenReturn(
                        aiSuccessWithCommissionRule("Fashion", "Watches", "Fashion > Watches", 35003L),
                        aiSuccessWithNoItems()
                );
        when(fileManagementParseMapper.nextResultId()).thenReturn(40041L);
        when(fileManagementParseMapper.insertResult(
                anyLong(),
                anyString(),
                anyLong(),
                anyLong(),
                anyLong(),
                nullable(Long.class),
                anyString(),
                anyString(),
                anyString(),
                nullable(String.class),
                anyString(),
                anyString(),
                anyString(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.nextResultItemId()).thenReturn(50041L, 50042L);
        when(fileManagementParseMapper.insertResultItem(
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                nullable(String.class),
                anyString(),
                anyString(),
                nullable(String.class),
                nullable(String.class),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                nullable(String.class),
                anyInt(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.upsertCurrentResult(20041L, 40041L)).thenReturn(1);
        when(fileManagementParseMapper.markTaskReviewRequired(anyLong(), anyLong(), anyString(), anyLong())).thenReturn(1);
        when(fileManagementParseMapper.markAiChunkSucceeded(
                anyLong(),
                anyLong(),
                anyLong(),
                anyInt(),
                anyString(),
                anyString(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.selectResultItems(
                40041L,
                null,
                null,
                10_000,
                0
        )).thenReturn(List.of(firstPersistedItem, secondPersistedItem));
        when(fileManagementParseMapper.nextResultItemSourceId()).thenReturn(55041L, 55042L);
        when(fileManagementParseMapper.insertResultItemSource(
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyLong()
        )).thenReturn(1);

        FileParseTaskRunView run = service.startParseTask(new AuthenticatedSession(10001L, 1L, 0), 20041L);

        assertEquals("review_required", run.getStatus());
        verify(fileManagementParseMapper).insertResultItemSource(
                55041L,
                20041L,
                40041L,
                50041L,
                35003L,
                36001L,
                "primary",
                "high",
                "Fashion > Watches",
                10001L
        );
        verify(fileManagementParseMapper).insertResultItemSource(
                55042L,
                20041L,
                40041L,
                50042L,
                35031L,
                36002L,
                "primary",
                "high",
                "Other Categories > Sports & Outdoors",
                10001L
        );
    }

    @Test
    void shouldMarkTaskFailedWhenInputFileIsMissing() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4005L, "logistics_yite", "物流-义特", "logistics_rule");
        FileParseTaskRow task = task(20001L, 4005L, "reading", 0);
        FileParseTaskInputRow fileInput = taskInput(30001L, "excel", "义特FBN报价.xlsx", "xlsx", "missing/quote.xlsx", null);

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20001L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4005L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.markTaskParsing(anyLong(), anyString(), anyLong())).thenReturn(1);
        when(fileManagementParseMapper.selectTaskInputs(20001L)).thenReturn(List.of(fileInput));
        when(fileManagementParseMapper.markTaskFailed(anyLong(), anyString(), anyString(), anyString(), anyLong())).thenReturn(1);

        FileParseTaskRunView run = service.startParseTask(new AuthenticatedSession(10001L, 1L, 0), 20001L);

        assertEquals("failed", run.getStatus());
        assertEquals("归档文件不存在。", run.getMessage());
        verify(fileManagementParseMapper).markTaskFailed(anyLong(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void shouldRejectOperatorRunBecauseOperatorIsReadOnly() {
        FileParseUserContext operator = user(10004L, 3, "OPS", "运营");
        FileParseTaskRow task = task(20001L, 4002L, "reading", 0);
        when(fileManagementParseMapper.selectUserContext(10004L)).thenReturn(operator);
        when(fileManagementParseMapper.countActiveUserMenu(10004L, LocalDbFileManagementParseService.FILE_MANAGEMENT_MENU_ID))
                .thenReturn(1);
        when(fileManagementParseMapper.selectTask(20001L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4002L, 3, false))
                .thenReturn(targetPlan(4002L, "commission_uae", "佣金-UAE", "official_commission"));

        assertThrows(
                FileParseAccessDeniedException.class,
                () -> service.startParseTask(new AuthenticatedSession(10004L, 4L, 3), 20001L)
        );
    }

    @Test
    void shouldGenerateImmutableResultRevisionForTextInput() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4005L, "logistics_yite", "物流-义特", "logistics_rule");
        FileParseTaskRow task = task(20001L, 4005L, "reading", 0);
        FileParseTaskInputRow textInput = taskInput(
                30001L,
                "manual_text",
                "人工方案",
                null,
                null,
                "义特 + UAE + Dubai + 海运 + 卡牌 / 普货 + 26 CNY/KG，最低 12KG"
        );

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20001L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4005L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.markTaskParsing(anyLong(), anyString(), anyLong())).thenReturn(1);
        when(fileManagementParseMapper.selectTaskInputs(20001L)).thenReturn(List.of(textInput));
        when(fileManagementParseMapper.selectStandardVersion(5105L)).thenReturn(standardVersion(5105L));
        when(fileManagementParseMapper.selectItemStandards(5105L)).thenReturn(List.of(logisticsItemStandard()));
        when(aiCapabilityService.createStructuredText(any(AiStructuredTextCommand.class))).thenReturn(aiSuccess());
        when(fileManagementParseMapper.nextResultId()).thenReturn(40001L);
        when(fileManagementParseMapper.insertResult(
                anyLong(),
                anyString(),
                anyLong(),
                anyLong(),
                anyLong(),
                nullable(Long.class),
                anyString(),
                anyString(),
                anyString(),
                nullable(String.class),
                anyString(),
                anyString(),
                anyString(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.nextResultItemId()).thenReturn(50001L);
        when(fileManagementParseMapper.insertResultItem(
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                nullable(String.class),
                anyString(),
                anyString(),
                nullable(String.class),
                nullable(String.class),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                nullable(String.class),
                anyInt(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.upsertCurrentResult(20001L, 40001L)).thenReturn(1);
        when(fileManagementParseMapper.markTaskReviewRequired(anyLong(), anyLong(), anyString(), anyLong())).thenReturn(1);
        stubStableProcessTables(40001L, List.of());

        FileParseTaskRunView run = service.startParseTask(new AuthenticatedSession(10001L, 1L, 0), 20001L);

        assertEquals("review_required", run.getStatus());
        assertEquals(40001L, run.getResultId());
        assertEquals("RESULT-" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + "-40001", run.getResultNo());
        assertEquals(1, run.getResultItemCount());
        verify(aiCapabilityService).createStructuredText(any(AiStructuredTextCommand.class));
        verify(fileManagementParseMapper).insertResultItem(
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                nullable(String.class),
                anyString(),
                anyString(),
                nullable(String.class),
                nullable(String.class),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                nullable(String.class),
                anyInt(),
                anyLong()
        );
    }

    @Test
    void shouldValidateCommissionTierAsStructuredFields() {
        FileParseStructuredAiService aiService = new FileParseStructuredAiService(aiCapabilityService, new ObjectMapper());
        FileParseTaskRow task = task(20001L, 4001L, "parsing", 0);
        FileParseTargetPlanRow plan = targetPlan(4001L, "commission_ksa", "佣金-KSA", "official_commission");
        FileParseStandardVersionRow standardVersion = standardVersion(2001L);
        when(aiCapabilityService.createStructuredText(any(AiStructuredTextCommand.class))).thenReturn(aiSuccessWithUnsplitCommissionTier());

        FileParseStructuredAiResult result = aiService.parse(
                task,
                plan,
                standardVersion,
                List.of(commissionItemStandard()),
                "Fashion > Watches: 15% up to 5000 SAR, then 5% above 5000 SAR",
                10001L
        );

        assertEquals(1, result.getItems().size());
        FileParseStructuredItem item = result.getItems().get(0);
        assertEquals("hard_error", item.getValidationStatus());
        assertEquals("needs_fix", item.getReviewStatus());
        assertTrue(item.getValidationErrorJson().contains("阶梯金额区间需要拆成多条"));
        ArgumentCaptor<AiStructuredTextCommand> commandCaptor = ArgumentCaptor.forClass(AiStructuredTextCommand.class);
        verify(aiCapabilityService).createStructuredText(commandCaptor.capture());
        assertTrue(commandCaptor.getValue().getInstructions().contains("阶梯费率，必须拆成多条"));
        assertTrue(commandCaptor.getValue().getInstructions().contains("只解析 1. Referral Fees"));
    }

    @Test
    void shouldDeduplicateIdenticalCommissionRowsBeforePersisting() {
        FileParseStructuredAiService aiService = new FileParseStructuredAiService(aiCapabilityService, new ObjectMapper());
        FileParseTaskRow task = task(20001L, 4001L, "parsing", 0);
        FileParseTargetPlanRow plan = targetPlan(4001L, "commission_ksa", "佣金-KSA", "official_commission");
        FileParseStandardVersionRow standardVersion = standardVersion(2001L);
        when(aiCapabilityService.createStructuredText(any(AiStructuredTextCommand.class))).thenReturn(aiSuccessWithDuplicateCommissionRows(false));

        FileParseStructuredAiResult result = aiService.parse(
                task,
                plan,
                standardVersion,
                List.of(commissionItemStandard()),
                "重复佣金规则",
                10001L
        );

        assertEquals(1, result.getItems().size());
        assertEquals("pass", result.getItems().get(0).getValidationStatus());
    }

    @Test
    void shouldMarkConflictingDuplicateCommissionRowsAsHardError() {
        FileParseStructuredAiService aiService = new FileParseStructuredAiService(aiCapabilityService, new ObjectMapper());
        FileParseTaskRow task = task(20001L, 4001L, "parsing", 0);
        FileParseTargetPlanRow plan = targetPlan(4001L, "commission_ksa", "佣金-KSA", "official_commission");
        FileParseStandardVersionRow standardVersion = standardVersion(2001L);
        when(aiCapabilityService.createStructuredText(any(AiStructuredTextCommand.class))).thenReturn(aiSuccessWithDuplicateCommissionRows(true));

        FileParseStructuredAiResult result = aiService.parse(
                task,
                plan,
                standardVersion,
                List.of(commissionItemStandard()),
                "冲突重复佣金规则",
                10001L
        );

        assertEquals(1, result.getItems().size());
        FileParseStructuredItem item = result.getItems().get(0);
        assertEquals("hard_error", item.getValidationStatus());
        assertEquals("needs_fix", item.getReviewStatus());
        assertTrue(item.getValidationErrorJson().contains("业务主键相同"));
    }

    @Test
    void shouldSplitBrandRestrictedCommissionRowsByBrandRestrictionKey() {
        FileParseStructuredAiService aiService = new FileParseStructuredAiService(aiCapabilityService, new ObjectMapper());
        FileParseTaskRow task = task(20001L, 4001L, "parsing", 0);
        FileParseTargetPlanRow plan = targetPlan(4001L, "commission_ksa", "佣金-KSA", "official_commission");
        FileParseStandardVersionRow standardVersion = standardVersion(2001L);
        when(aiCapabilityService.createStructuredText(any(AiStructuredTextCommand.class))).thenReturn(aiSuccessWithBrandRestrictedCommissionRows());

        FileParseStructuredAiResult result = aiService.parse(
                task,
                plan,
                standardVersion,
                List.of(commissionItemStandard()),
                "[[SOURCE_ROW_ID=92001;TYPE=pdf_ocr_line;LOC=page=1;line=8]]\n"
                        + "Colour Cosmetics | All | - 15% for Generic brand - 10% for all other brands",
                10001L
        );

        assertEquals(2, result.getItems().size());
        assertEquals("pass", result.getItems().get(0).getValidationStatus());
        assertEquals("pass", result.getItems().get(1).getValidationStatus());
        assertNotEquals(result.getItems().get(0).getNaturalKeyHash(), result.getItems().get(1).getNaturalKeyHash());
        assertTrue(result.getItems().get(0).getNormalizedPayloadJson().contains("\"brandRestriction\":\"Generic brand\""));
        assertTrue(result.getItems().get(1).getNormalizedPayloadJson().contains("\"brandRestriction\":\"All other brands\""));
    }

    @Test
    void shouldRejectUnsplitBrandRestrictedCommissionRate() {
        FileParseStructuredAiService aiService = new FileParseStructuredAiService(aiCapabilityService, new ObjectMapper());
        FileParseTaskRow task = task(20001L, 4001L, "parsing", 0);
        FileParseTargetPlanRow plan = targetPlan(4001L, "commission_ksa", "佣金-KSA", "official_commission");
        FileParseStandardVersionRow standardVersion = standardVersion(2001L);
        when(aiCapabilityService.createStructuredText(any(AiStructuredTextCommand.class))).thenReturn(aiSuccessWithUnsplitBrandRestrictedCommissionRate());

        FileParseStructuredAiResult result = aiService.parse(
                task,
                plan,
                standardVersion,
                List.of(commissionItemStandard()),
                "Colour Cosmetics | All | 15% for Generic brand, 10% for all other brands",
                10001L
        );

        assertEquals(1, result.getItems().size());
        FileParseStructuredItem item = result.getItems().get(0);
        assertEquals("hard_error", item.getValidationStatus());
        assertTrue(item.getValidationErrorJson().contains("品牌限制费率需要拆成多条"));
    }

    @Test
    void shouldSupplementMissingCommissionTierFromSourceContext() {
        FileParseStructuredAiService aiService = new FileParseStructuredAiService(aiCapabilityService, new ObjectMapper());
        FileParseTaskRow task = task(20001L, 4001L, "parsing", 0);
        FileParseTargetPlanRow plan = targetPlan(4001L, "commission_ksa", "佣金-KSA", "official_commission");
        FileParseStandardVersionRow standardVersion = standardVersion(2001L);
        when(aiCapabilityService.createStructuredText(any(AiStructuredTextCommand.class))).thenReturn(aiSuccessWithNoItems());

        FileParseStructuredAiResult result = aiService.parse(
                task,
                plan,
                standardVersion,
                List.of(commissionItemStandard()),
                "[[SOURCE_ROW_ID=94001;TYPE=pdf_text_line;LOC=page=5;line=40]]\n"
                        + "Other Categories\n"
                        + "[[SOURCE_ROW_ID=94002;TYPE=pdf_text_line;LOC=page=5;line=41]]\n"
                        + "Sports & Outdoors All\n"
                        + "[[SOURCE_ROW_ID=94003;TYPE=pdf_text_line;LOC=page=5;line=42]]\n"
                        + "- 20% for item with sales price\n"
                        + "[[SOURCE_ROW_ID=94004;TYPE=pdf_text_line;LOC=page=5;line=43]]\n"
                        + "of 30 SAR or less\n"
                        + "[[SOURCE_ROW_ID=94005;TYPE=pdf_text_line;LOC=page=5;line=44]]\n"
                        + "- 13% for item with a total sales\n"
                        + "[[SOURCE_ROW_ID=94006;TYPE=pdf_text_line;LOC=page=5;line=45]]\n"
                        + "price greater than 30 SAR",
                10001L
        );

        assertEquals(2, result.getItems().size());
        assertTrue(result.getItems().stream().allMatch(current -> "pass".equals(current.getValidationStatus())));
        assertTrue(result.getItems().stream().anyMatch(current -> current.getNaturalKey().contains("MAX:30:1")));
        assertTrue(result.getItems().stream().anyMatch(current -> current.getNaturalKey().contains("MIN:30:0")));
        assertTrue(result.getItems().get(0).getNormalizedPayloadJson().contains("Other Categories > Sports & Outdoors"));
    }

    @Test
    void shouldStabilizeCommissionItemsWithFullSourceContextAfterChunkMerge() {
        FileParseStructuredAiService aiService = new FileParseStructuredAiService(aiCapabilityService, new ObjectMapper());
        FileParseTargetPlanRow plan = targetPlan(4001L, "commission_ksa", "佣金-KSA", "official_commission");
        FileParseStructuredAiResult result = new FileParseStructuredAiResult();
        result.setItems(List.of(
                structuredCommissionItem(
                        "{\"country\":\"KSA\",\"parentCategoryName\":\"Watches\",\"categoryName\":\"All\",\"categoryPath\":\"Watches > All\",\"amountRangeLabel\":\"<= 5000 SAR\",\"amountMax\":5000,\"amountMaxInclusive\":true,\"amountCurrency\":\"SAR\",\"commissionRate\":\"15%\"}",
                        List.of(95003L, 95004L)
                ),
                structuredCommissionItem(
                        "{\"country\":\"KSA\",\"parentCategoryName\":\"Wearables\",\"categoryName\":\"Smartwatches\",\"categoryPath\":\"Wearables > Smartwatches\",\"amountRangeLabel\":\"全部\",\"amountCurrency\":\"SAR\",\"commissionRate\":\"15%\"}",
                        List.of(96002L, 96003L, 96004L, 96005L)
                ),
                structuredCommissionItem(
                        "{\"country\":\"KSA\",\"parentCategoryName\":\"Wearables\",\"categoryName\":\"Fitness Bands\",\"categoryPath\":\"Wearables > Fitness Bands\",\"amountRangeLabel\":\"全部\",\"amountCurrency\":\"SAR\",\"commissionRate\":\"15%\"}",
                        List.of(96002L, 96003L, 96004L, 96005L)
                ),
                structuredCommissionItem(
                        "{\"country\":\"KSA\",\"parentCategoryName\":\"Other Categories\",\"categoryName\":\"Sports & Outdoors\",\"categoryPath\":\"Other Categories > Sports & Outdoors\",\"amountRangeLabel\":\"全部\",\"amountCurrency\":\"SAR\",\"commissionRate\":null}",
                        List.of(97002L)
                ),
                structuredCommissionItem(
                        "{\"country\":\"KSA\",\"parentCategoryName\":\"Other Categories\",\"categoryName\":\"Sports & Outdoors\",\"categoryPath\":\"Other Categories > Sports & Outdoors\",\"amountRangeLabel\":\"<= 30 SAR\",\"amountMax\":30,\"amountMaxInclusive\":true,\"amountCurrency\":\"SAR\",\"commissionRate\":\"20%\"}",
                        List.of(97002L, 97003L, 97004L)
                )
        ));

        FileParseStructuredAiResult stabilized = aiService.stabilizeWithSourceContext(
                result,
                plan,
                List.of(commissionItemStandard()),
                "[[SOURCE_ROW_ID=95001;TYPE=pdf_text_line;LOC=page=1;line=15]]\n"
                        + "Fashion\n"
                        + "[[SOURCE_ROW_ID=95002;TYPE=pdf_text_line;LOC=page=1;line=24]]\n"
                        + "The below referral fees is applicable from 1st September 2025\n"
                        + "[[SOURCE_ROW_ID=95003;TYPE=pdf_text_line;LOC=page=1;line=29]]\n"
                        + "Watches All\n"
                        + "[[SOURCE_ROW_ID=95004;TYPE=pdf_text_line;LOC=page=1;line=30]]\n"
                        + "- 15% for the portion of the\n"
                        + "[[SOURCE_ROW_ID=96001;TYPE=pdf_text_line;LOC=page=4;line=160]]\n"
                        + "Wearables\n"
                        + "[[SOURCE_ROW_ID=96002;TYPE=pdf_text_line;LOC=page=4;line=168]]\n"
                        + "Smartwatches, Fitness\n"
                        + "[[SOURCE_ROW_ID=96003;TYPE=pdf_text_line;LOC=page=4;line=169]]\n"
                        + "Bands, Smart Glasses,\n"
                        + "[[SOURCE_ROW_ID=96004;TYPE=pdf_text_line;LOC=page=4;line=170]]\n"
                        + "Smart Rings\n"
                        + "[[SOURCE_ROW_ID=96005;TYPE=pdf_text_line;LOC=page=4;line=171]]\n"
                        + "15%\n"
                        + "[[SOURCE_ROW_ID=97001;TYPE=pdf_text_line;LOC=page=5;line=245]]\n"
                        + "Other Categories\n"
                        + "[[SOURCE_ROW_ID=97002;TYPE=pdf_text_line;LOC=page=5;line=247]]\n"
                        + "Sports & Outdoors All\n"
                        + "[[SOURCE_ROW_ID=97003;TYPE=pdf_text_line;LOC=page=5;line=248]]\n"
                        + "- 20% for item with sales price\n"
                        + "[[SOURCE_ROW_ID=97004;TYPE=pdf_text_line;LOC=page=5;line=249]]\n"
                        + "of 30 SAR or less"
        );

        assertEquals(3, stabilized.getItems().size());
        assertTrue(stabilized.getItems().stream().allMatch(item -> "pass".equals(item.getValidationStatus())));
        assertTrue(stabilized.getItems().stream().anyMatch(item -> item.getNaturalKey().contains("Fashion > Watches")));
        assertTrue(stabilized.getItems().stream().anyMatch(item -> item.getNaturalKey().contains("Wearables > Smartwatches, Fitness Bands, Smart Glasses, Smart Rings")));
        assertFalse(stabilized.getItems().stream().anyMatch(item -> item.getNormalizedPayloadJson().contains("\"commissionRate\":null")));
    }

    @Test
    void shouldUseEvidenceSourceRowsWhenStabilizingCommissionItems() {
        FileParseStructuredAiService aiService = new FileParseStructuredAiService(aiCapabilityService, new ObjectMapper());
        FileParseTargetPlanRow plan = targetPlan(4001L, "commission_ksa", "佣金-KSA", "official_commission");
        FileParseStructuredItem item = structuredCommissionItem(
                "{\"country\":\"KSA\",\"parentCategoryName\":\"Jewelry\",\"categoryName\":\"Eyewear\",\"categoryPath\":\"Jewelry > Eyewear\",\"amountRangeLabel\":\"全部\",\"amountCurrency\":\"SAR\",\"commissionRate\":\"15%\"}",
                List.of()
        );
        item.setEvidenceJson("{\"source\":\"ai_structured_text\",\"sourceRowIds\":[99002]}");

        FileParseStructuredAiResult result = new FileParseStructuredAiResult();
        result.setItems(List.of(item));

        FileParseStructuredAiResult stabilized = aiService.stabilizeWithSourceContext(
                result,
                plan,
                List.of(commissionItemStandard()),
                "[[SOURCE_ROW_ID=99001;TYPE=pdf_text_line;LOC=page=1;line=37]]\n"
                        + "Jewelry\n"
                        + "[[SOURCE_ROW_ID=99002;TYPE=pdf_text_line;LOC=page=1;line=38]]\n"
                        + "Gold Bars & Coins 5%"
        );

        assertEquals(1, stabilized.getItems().size());
        assertTrue(stabilized.getItems().get(0).getNaturalKey().contains("Jewelry > Gold Bars & Coins"));
        assertEquals(List.of(99002L), stabilized.getItems().get(0).getSourceRowIds());
    }

    @Test
    void shouldMarkTaskFailedWhenAiBaseFails() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4005L, "logistics_yite", "物流-义特", "logistics_rule");
        FileParseTaskRow task = task(20001L, 4005L, "reading", 0);
        FileParseTaskInputRow textInput = taskInput(30001L, "manual_text", "人工方案", null, null, "义特报价");

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20001L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4005L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.markTaskParsing(anyLong(), anyString(), anyLong())).thenReturn(1);
        when(fileManagementParseMapper.selectTaskInputs(20001L)).thenReturn(List.of(textInput));
        when(fileManagementParseMapper.selectStandardVersion(5105L)).thenReturn(standardVersion(5105L));
        when(fileManagementParseMapper.selectItemStandards(5105L)).thenReturn(List.of(logisticsItemStandard()));
        when(aiCapabilityService.createStructuredText(any(AiStructuredTextCommand.class)))
                .thenReturn(AiStructuredTextResult.failure("AI_DISABLED", "AI_DISABLED", "AI capability is disabled"));
        when(fileManagementParseMapper.markTaskFailed(anyLong(), anyString(), anyString(), anyString(), anyLong())).thenReturn(1);
        stubStableProcessStart();
        when(fileManagementParseMapper.markAiChunkFailed(anyLong(), anyLong(), anyString(), anyString(), anyLong())).thenReturn(1);

        FileParseTaskRunView run = service.startParseTask(new AuthenticatedSession(10001L, 1L, 0), 20001L);

        assertEquals("failed", run.getStatus());
        assertEquals("AI capability is disabled", run.getMessage());
        verify(fileManagementParseMapper).markTaskFailed(anyLong(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void shouldQueueRetryWhenAiProviderReturnsHttp550() {
        ReflectionTestUtils.setField(service, "retrySchedulerEnabled", true);
        ReflectionTestUtils.setField(service, "retrySchedulerMaxAttempts", 5);
        ReflectionTestUtils.setField(service, "retrySchedulerRetryDelaySeconds", 60);
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4005L, "logistics_yite", "物流-义特", "logistics_rule");
        FileParseTaskRow task = task(20001L, 4005L, "reading", 0);
        FileParseTaskInputRow textInput = taskInput(30001L, "manual_text", "人工方案", null, null, "义特报价");

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20001L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4005L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.markTaskParsing(anyLong(), anyString(), anyLong())).thenReturn(1);
        when(fileManagementParseMapper.selectTaskInputs(20001L)).thenReturn(List.of(textInput));
        when(fileManagementParseMapper.selectStandardVersion(5105L)).thenReturn(standardVersion(5105L));
        when(fileManagementParseMapper.selectItemStandards(5105L)).thenReturn(List.of(logisticsItemStandard()));
        when(aiCapabilityService.createStructuredText(any(AiStructuredTextCommand.class)))
                .thenReturn(AiStructuredTextResult.failure("AI_PROVIDER_ERROR", "OPENAI_HTTP_550", "provider temporary failure"));
        when(fileManagementParseMapper.markTaskFailedRetryable(anyLong(), anyString(), anyString(), anyString(), anyInt(), anyLong())).thenReturn(1);
        stubStableProcessStart();
        when(fileManagementParseMapper.markAiChunkFailed(anyLong(), anyLong(), anyString(), anyString(), anyLong())).thenReturn(1);

        FileParseTaskRunView run = service.startParseTask(new AuthenticatedSession(10001L, 1L, 0), 20001L);

        assertEquals("retry_waiting", run.getStatus());
        assertTrue(run.getMessage().contains("系统会自动重试"));
        verify(fileManagementParseMapper).markTaskFailedRetryable(
                eq(20001L),
                eq("OPENAI_HTTP_550"),
                eq("provider temporary failure"),
                anyString(),
                anyInt(),
                eq(10001L)
        );
        verify(fileManagementParseMapper, never()).markTaskFailed(eq(20001L), eq("OPENAI_HTTP_550"), anyString(), anyString(), eq(10001L));
    }

    @Test
    void shouldListProcessingItemsFromCurrentResult() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4005L, "logistics_yite", "物流-义特", "logistics_rule");
        FileParseTaskRow task = task(20001L, 4005L, "review_required", 0);
        task.setCurrentResultId(40001L);

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20001L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4005L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.selectStandardVersion(5105L)).thenReturn(standardVersion(5105L));
        when(fileManagementParseMapper.selectItemStandards(5105L)).thenReturn(List.of(logisticsItemStandard()));
        when(fileManagementParseMapper.countResultItems(40001L, null, null)).thenReturn(1);
        when(fileManagementParseMapper.selectResultItems(40001L, null, null, 100, 0))
                .thenReturn(List.of(resultItem("pending", "changed")));

        FileParseProcessingItemsView view = service.listProcessingItems(
                new AuthenticatedSession(10001L, 1L, 0),
                20001L,
                null,
                null,
                1,
                100
        );

        assertEquals(40001L, view.getResultId());
        assertEquals(1, view.getTotal());
        assertEquals(1, view.getItems().size());
        assertEquals("26 CNY/KG，最低 12KG", view.getItems().get(0).getFields().get("billingRule"));
        assertEquals("24 CNY/KG，最低 10KG", view.getItems().get(0).getOldFields().get("billingRule"));
    }

    @Test
    void shouldAcceptProcessingItemAndMarkTaskReadyWhenNoBlockingItemsRemain() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4005L, "logistics_yite", "物流-义特", "logistics_rule");
        FileParseTaskRow task = task(20001L, 4005L, "review_required", 0);
        task.setCurrentResultId(40001L);
        FileParseResultItemRow pendingRow = resultItem("pending", "changed");
        FileParseResultItemRow confirmedRow = resultItem("confirmed", "changed");

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20001L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4005L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.selectStandardVersion(5105L)).thenReturn(standardVersion(5105L));
        when(fileManagementParseMapper.selectItemStandards(5105L)).thenReturn(List.of(logisticsItemStandard()));
        when(fileManagementParseMapper.selectResultItem(20001L, 50001L)).thenReturn(pendingRow, confirmedRow);
        when(fileManagementParseMapper.nextReviewId()).thenReturn(60001L);
        when(fileManagementParseMapper.insertItemReview(
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                nullable(String.class),
                anyString(),
                anyString(),
                nullable(String.class),
                nullable(String.class),
                anyLong(),
                anyString(),
                anyString(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.updateResultItemReviewCache(
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.updateTaskStatusAfterReviewFromItems(20001L, 10001L)).thenReturn(1);

        FileParseReviewCommand command = new FileParseReviewCommand();
        command.setExpectedResultId(40001L);
        FileParseProcessingItemView item = service.reviewResultItem(
                new AuthenticatedSession(10001L, 1L, 0),
                20001L,
                50001L,
                "accept",
                command,
                "idem-accept-1"
        );

        assertEquals("confirmed", item.getReviewStatus());
        verify(fileManagementParseMapper).insertItemReview(
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                nullable(String.class),
                anyString(),
                anyString(),
                nullable(String.class),
                nullable(String.class),
                anyLong(),
                anyString(),
                anyString(),
                anyLong()
        );
        verify(fileManagementParseMapper).updateTaskStatusAfterReviewFromItems(20001L, 10001L);
    }

    @Test
    void shouldBatchAcceptProcessingItems() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4005L, "logistics_yite", "物流-义特", "logistics_rule");
        FileParseTaskRow task = task(20001L, 4005L, "review_required", 0);
        task.setCurrentResultId(40001L);
        FileParseResultItemRow pendingRow1 = resultItem("pending", "changed");
        pendingRow1.setId(50001L);
        FileParseResultItemRow confirmedRow1 = resultItem("confirmed", "changed");
        confirmedRow1.setId(50001L);
        FileParseResultItemRow pendingRow2 = resultItem("pending", "added");
        pendingRow2.setId(50002L);
        pendingRow2.setNaturalKey("义特-UAE-Dubai-AIR-battery");
        pendingRow2.setNaturalKeyHash("hash-2");
        FileParseResultItemRow confirmedRow2 = resultItem("confirmed", "added");
        confirmedRow2.setId(50002L);
        confirmedRow2.setNaturalKey("义特-UAE-Dubai-AIR-battery");
        confirmedRow2.setNaturalKeyHash("hash-2");

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20001L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4005L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.selectStandardVersion(5105L)).thenReturn(standardVersion(5105L));
        when(fileManagementParseMapper.selectItemStandards(5105L)).thenReturn(List.of(logisticsItemStandard()));
        when(fileManagementParseMapper.selectResultItem(20001L, 50001L)).thenReturn(pendingRow1, confirmedRow1);
        when(fileManagementParseMapper.selectResultItem(20001L, 50002L)).thenReturn(pendingRow2, confirmedRow2);
        when(fileManagementParseMapper.nextReviewId()).thenReturn(60001L, 60002L);
        when(fileManagementParseMapper.insertItemReview(
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                nullable(String.class),
                anyString(),
                anyString(),
                nullable(String.class),
                nullable(String.class),
                anyLong(),
                anyString(),
                anyString(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.updateResultItemReviewCache(
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.updateTaskStatusAfterReviewFromItems(20001L, 10001L)).thenReturn(1);

        FileParseBatchReviewCommand command = new FileParseBatchReviewCommand();
        command.setExpectedResultId(40001L);
        command.setItemIds(List.of(50001L, 50002L));

        FileParseBatchReviewView view = service.batchAcceptResultItems(
                new AuthenticatedSession(10001L, 1L, 0),
                20001L,
                command,
                "idem-batch-accept"
        );

        assertEquals(2, view.getTotalCount());
        assertEquals(2, view.getSuccessCount());
        assertEquals(2, view.getItems().size());
        assertEquals("confirmed", view.getItems().get(0).getReviewStatus());
        assertEquals("confirmed", view.getItems().get(1).getReviewStatus());
        verify(fileManagementParseMapper, times(2)).insertItemReview(
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                nullable(String.class),
                anyString(),
                anyString(),
                nullable(String.class),
                nullable(String.class),
                anyLong(),
                anyString(),
                anyString(),
                anyLong()
        );
        verify(fileManagementParseMapper, times(2)).updateTaskStatusAfterReviewFromItems(20001L, 10001L);
    }

    @Test
    void shouldRejectKeepOldForAddedCommissionItemWithoutOldPayload() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4001L, "commission_ksa", "佣金-KSA", "official_commission");
        FileParseTaskRow task = task(20099L, 4001L, "review_required", 0);
        task.setStandardVersionId(2001L);
        task.setCurrentResultId(40092L);
        FileParseResultItemRow addedRow = resultItem("pending", "added");
        addedRow.setId(53801L);
        addedRow.setTaskId(20099L);
        addedRow.setResultId(40092L);
        addedRow.setTargetPlanId(4001L);
        addedRow.setItemType("commission_rule");
        addedRow.setNaturalKey("KSA + Fashion > Watches + 全部 + 2025-09-01");
        addedRow.setNaturalKeyHash("issue-13-added-commission");
        addedRow.setNormalizedPayloadJson(commissionPayload("Fashion > Watches", "15%"));
        addedRow.setOldPayloadJson(null);
        addedRow.setChangedFieldKeysJson("[]");

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20099L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4001L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.selectStandardVersion(2001L)).thenReturn(standardVersion(2001L));
        when(fileManagementParseMapper.selectItemStandards(2001L)).thenReturn(List.of(commissionItemStandard()));
        when(fileManagementParseMapper.selectResultItem(20099L, 53801L)).thenReturn(addedRow);

        FileParseReviewCommand command = new FileParseReviewCommand();
        command.setExpectedResultId(40092L);
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.reviewResultItem(
                        new AuthenticatedSession(10001L, 1L, 0),
                        20099L,
                        53801L,
                        "keep_old",
                        command,
                        "issue-13-keep-old-added"
                )
        );

        assertEquals("新增项没有旧值，不能保留旧值。", error.getMessage());
        verify(fileManagementParseMapper, never()).clearCurrentReview(anyLong(), anyLong());
        verify(fileManagementParseMapper, never()).insertItemReview(
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                nullable(String.class),
                anyString(),
                anyString(),
                nullable(String.class),
                nullable(String.class),
                anyLong(),
                anyString(),
                anyString(),
                anyLong()
        );
        verify(fileManagementParseMapper, never()).updateResultItemReviewCache(
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyLong()
        );
    }

    @Test
    void shouldKeepOldForChangedCommissionItemWithOldPayload() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4001L, "commission_ksa", "佣金-KSA", "official_commission");
        FileParseTaskRow task = task(20099L, 4001L, "review_required", 0);
        task.setStandardVersionId(2001L);
        task.setCurrentResultId(40092L);
        FileParseResultItemRow changedRow = resultItem("pending", "changed");
        changedRow.setId(53795L);
        changedRow.setTaskId(20099L);
        changedRow.setResultId(40092L);
        changedRow.setTargetPlanId(4001L);
        changedRow.setItemType("commission_rule");
        changedRow.setNaturalKey("KSA + Fashion > Watches + 全部 + 2025-09-01");
        changedRow.setNaturalKeyHash("issue-13-changed-commission");
        changedRow.setNormalizedPayloadJson(commissionPayload("Fashion > Watches", "15%"));
        changedRow.setOldPayloadJson(commissionPayload("Fashion > Watches", "10%"));
        FileParseResultItemRow keepOldRow = resultItem("keep_old", "changed");
        keepOldRow.setId(53795L);
        keepOldRow.setTaskId(20099L);
        keepOldRow.setResultId(40092L);
        keepOldRow.setTargetPlanId(4001L);
        keepOldRow.setItemType("commission_rule");
        keepOldRow.setEffectivePayloadJson(changedRow.getOldPayloadJson());

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20099L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4001L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.selectStandardVersion(2001L)).thenReturn(standardVersion(2001L));
        when(fileManagementParseMapper.selectItemStandards(2001L)).thenReturn(List.of(commissionItemStandard()));
        when(fileManagementParseMapper.selectResultItem(20099L, 53795L)).thenReturn(changedRow, keepOldRow);
        when(fileManagementParseMapper.nextReviewId()).thenReturn(60792L);
        when(fileManagementParseMapper.insertItemReview(
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                nullable(String.class),
                anyString(),
                anyString(),
                nullable(String.class),
                nullable(String.class),
                anyLong(),
                anyString(),
                anyString(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.updateResultItemReviewCache(
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.updateTaskStatusAfterReviewFromItems(20099L, 10001L)).thenReturn(1);

        FileParseReviewCommand command = new FileParseReviewCommand();
        command.setExpectedResultId(40092L);
        FileParseProcessingItemView item = service.reviewResultItem(
                new AuthenticatedSession(10001L, 1L, 0),
                20099L,
                53795L,
                "keep_old",
                command,
                "issue-13-keep-old-changed"
        );

        assertEquals("keep_old", item.getReviewStatus());
        ArgumentCaptor<String> effectivePayloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileManagementParseMapper).insertItemReview(
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                eq("keep_old"),
                eq("keep_old"),
                nullable(String.class),
                effectivePayloadCaptor.capture(),
                eq("pass"),
                nullable(String.class),
                nullable(String.class),
                eq(40092L),
                eq("issue-13-keep-old-changed"),
                anyString(),
                eq(10001L)
        );
        assertTrue(effectivePayloadCaptor.getValue().contains("\"commissionRate\":\"10%\""));
    }

    @Test
    void shouldApplyChangedDiffAgainstBaseVersionItem() {
        FileParseTaskRow task = task(20001L, 4005L, "parsing", 0);
        task.setBaseVersionId(70005L);
        FileParseStructuredItem item = new FileParseStructuredItem();
        item.setItemType("logistics_channel_rule");
        item.setNaturalKey("义特-UAE-Dubai-SEA-card");
        item.setNaturalKeyHash("hash-1");
        item.setReviewStatus("pending");
        item.setValidationStatus("pass");
        item.setNormalizedPayloadJson("{\"channelKey\":\"义特-UAE-Dubai-SEA-card\",\"billingRule\":\"26 CNY/KG，最低 12KG\"}");
        item.setSortNo(1);
        FileParseStructuredAiResult result = new FileParseStructuredAiResult();
        result.setItems(List.of(item));
        FileParseVersionItemRow versionItem = new FileParseVersionItemRow();
        versionItem.setVersionId(70005L);
        versionItem.setItemType("logistics_channel_rule");
        versionItem.setNaturalKey("义特-UAE-Dubai-SEA-card");
        versionItem.setNaturalKeyHash("hash-1");
        versionItem.setVersionPayloadJson("{\"channelKey\":\"义特-UAE-Dubai-SEA-card\",\"billingRule\":\"24 CNY/KG，最低 10KG\"}");
        versionItem.setSortNo(1);

        when(fileManagementParseMapper.selectVersionItems(70005L)).thenReturn(List.of(versionItem));

        new FileParseResultDiffService(fileManagementParseMapper, new ObjectMapper())
                .applyDiff(task, List.of(logisticsItemStandard()), result);

        assertEquals("changed", item.getChangeType());
        assertEquals("[\"billingRule\"]", item.getChangedFieldKeysJson());
        assertTrue(item.getOldPayloadJson().contains("24 CNY/KG"));
        assertEquals("pending", item.getReviewStatus());
    }

    @Test
    void shouldMatchCommissionDiffByCanonicalPayloadWhenAiNaturalKeyChanges() {
        FileParseTaskRow task = task(20026L, 4001L, "parsing", 0);
        task.setBaseVersionId(70009L);
        FileParseStructuredItem item = new FileParseStructuredItem();
        item.setItemType("commission_rule");
        item.setNaturalKey("KSA|noon FBN|Fashion|Apparel, Footwear|27%|2025-09-01");
        item.setNaturalKeyHash("new-ai-hash");
        item.setReviewStatus("pending");
        item.setValidationStatus("pass");
        item.setNormalizedPayloadJson("{\"country\":\"ksa\",\"platform\":\"noon\",\"fulfillmentType\":\"fbn\",\"categoryName\":\"Fashion > Apparel, Footwear\",\"amountRangeLabel\":\"All\",\"amountCurrency\":\"sar\",\"commissionRate\":\"27\",\"effectiveDate\":\"2025-09-01\"}");
        item.setSortNo(1);
        FileParseStructuredAiResult result = new FileParseStructuredAiResult();
        result.setItems(List.of(item));
        FileParseVersionItemRow versionItem = new FileParseVersionItemRow();
        versionItem.setVersionId(70009L);
        versionItem.setTargetPlanId(4001L);
        versionItem.setItemType("commission_rule");
        versionItem.setNaturalKey("KSA|FBN|Fashion|Apparel, Footwear|27|2025-09-01");
        versionItem.setNaturalKeyHash("old-ai-hash");
        versionItem.setVersionPayloadJson("{\"country\":\"KSA\",\"platform\":\"Noon\",\"fulfillmentType\":\"FBN\",\"categoryName\":\"Fashion > Apparel, Footwear\",\"amountRangeLabel\":\"全部\",\"amountCurrency\":\"SAR\",\"commissionRate\":\"27%\",\"effectiveDate\":\"2025-09-01\"}");
        versionItem.setSortNo(1);

        when(fileManagementParseMapper.selectVersionItems(70009L)).thenReturn(List.of(versionItem));

        new FileParseResultDiffService(fileManagementParseMapper, new ObjectMapper())
                .applyDiff(task, List.of(commissionItemStandard()), result);

        assertEquals(1, result.getItems().size());
        assertEquals("unchanged", item.getChangeType());
        assertEquals("confirmed", item.getReviewStatus());
        assertEquals("[]", item.getChangedFieldKeysJson());
    }

    @Test
    void shouldIgnoreDuplicateWarningWhenCommissionPayloadMatchesBaseVersion() {
        FileParseTaskRow task = task(20028L, 4001L, "parsing", 0);
        task.setBaseVersionId(70009L);
        FileParseStructuredItem item = new FileParseStructuredItem();
        item.setItemType("commission_rule");
        item.setNaturalKey("KSA|NOON|FBN|Colour Cosmetics|ALL|SAR|2025-09-01");
        item.setNaturalKeyHash("duplicate-hash");
        item.setReviewStatus("needs_fix");
        item.setValidationStatus("hard_error");
        item.setValidationErrorJson("{\"message\":\"AI 解析到多条业务主键相同但字段值不同的结果，请编辑确认后再发布。\",\"duplicateItemCount\":2}");
        item.setNormalizedPayloadJson("{\"country\":\"KSA\",\"platform\":\"Noon\",\"fulfillmentType\":\"FBN\",\"categoryName\":\"Colour Cosmetics\",\"amountRangeLabel\":\"全部\",\"amountCurrency\":\"SAR\",\"commissionRate\":\"15%\",\"effectiveDate\":\"2025-09-01\"}");
        item.setSortNo(1);
        FileParseStructuredAiResult result = new FileParseStructuredAiResult();
        result.setItems(List.of(item));
        FileParseVersionItemRow versionItem = new FileParseVersionItemRow();
        versionItem.setVersionId(70009L);
        versionItem.setTargetPlanId(4001L);
        versionItem.setItemType("commission_rule");
        versionItem.setNaturalKey("KSA|NOON|FBN|Colour Cosmetics|ALL|SAR|2025-09-01");
        versionItem.setNaturalKeyHash("old-hash");
        versionItem.setVersionPayloadJson("{\"country\":\"KSA\",\"platform\":\"Noon\",\"fulfillmentType\":\"FBN\",\"categoryName\":\"Colour Cosmetics\",\"amountRangeLabel\":\"全部\",\"amountCurrency\":\"SAR\",\"commissionRate\":\"15%\",\"effectiveDate\":\"2025-09-01\"}");
        versionItem.setSortNo(1);

        when(fileManagementParseMapper.selectVersionItems(70009L)).thenReturn(List.of(versionItem));

        new FileParseResultDiffService(fileManagementParseMapper, new ObjectMapper())
                .applyDiff(task, List.of(commissionItemStandard()), result);

        assertEquals("unchanged", item.getChangeType());
        assertEquals("confirmed", item.getReviewStatus());
        assertEquals("pass", item.getValidationStatus());
        assertEquals("pass", item.getEffectiveValidationStatus());
        assertEquals("{}", item.getValidationErrorJson());
    }

    @Test
    void shouldSuppressDeleteSuspectedWhenCurrentParseCoverageIsTooLow() {
        FileParseTaskRow task = task(20029L, 4001L, "parsing", 0);
        task.setBaseVersionId(70009L);
        FileParseStructuredItem item = new FileParseStructuredItem();
        item.setItemType("commission_rule");
        item.setNaturalKey("KSA|NOON|FBN|Category 1|ALL|SAR|2025-09-01");
        item.setNaturalKeyHash("current-hash-1");
        item.setReviewStatus("pending");
        item.setValidationStatus("pass");
        item.setNormalizedPayloadJson(commissionPayload("Category 1", "15%"));
        item.setSortNo(1);
        FileParseStructuredAiResult result = new FileParseStructuredAiResult();
        result.setItems(List.of(item));

        List<FileParseVersionItemRow> versionItems = new ArrayList<>();
        for (int index = 1; index <= 20; index++) {
            FileParseVersionItemRow versionItem = new FileParseVersionItemRow();
            versionItem.setVersionId(70009L);
            versionItem.setTargetPlanId(4001L);
            versionItem.setItemType("commission_rule");
            versionItem.setNaturalKey("KSA|NOON|FBN|Category " + index + "|ALL|SAR|2025-09-01");
            versionItem.setNaturalKeyHash("old-hash-" + index);
            versionItem.setVersionPayloadJson(commissionPayload("Category " + index, "15%"));
            versionItem.setSortNo(index);
            versionItems.add(versionItem);
        }

        when(fileManagementParseMapper.selectVersionItems(70009L)).thenReturn(versionItems);

        new FileParseResultDiffService(fileManagementParseMapper, new ObjectMapper())
                .applyDiff(task, List.of(commissionItemStandard()), result);

        assertEquals(1, result.getItems().size());
        assertEquals("unchanged", item.getChangeType());
        assertTrue(result.getSummaryJson().contains("\"deleteInferenceSuppressed\":true"));
        assertTrue(result.getSummaryJson().contains("\"suppressedDeleteSuspected\":19"));
        assertFalse(result.getSummaryJson().contains("\"deleteSuspected\":19"));
    }

    @Test
    void shouldPublishCommissionSnapshotByCanonicalPayloadWhenStoredHashChanges() throws Exception {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4001L, "commission_ksa", "佣金-KSA", "official_commission");
        FileParseTaskRow task = task(20027L, 4001L, "ready_to_publish", 0);
        task.setStandardVersionId(2001L);
        task.setCurrentResultId(40027L);
        task.setBaseVersionId(70009L);
        FileParseActiveVersionRow activeVersion = activeVersion(4001L, 70009L, "COMMISSION-KSA-20260514-70009");
        FileParseVersionItemRow baseItem = new FileParseVersionItemRow();
        baseItem.setVersionId(70009L);
        baseItem.setTargetPlanId(4001L);
        baseItem.setItemType("commission_rule");
        baseItem.setNaturalKey("KSA|FBN|Fashion|Apparel, Footwear|27|2025-09-01");
        baseItem.setNaturalKeyHash("old-ai-hash");
        baseItem.setVersionPayloadJson("{\"country\":\"KSA\",\"platform\":\"Noon\",\"fulfillmentType\":\"FBN\",\"categoryName\":\"Fashion > Apparel, Footwear\",\"amountRangeLabel\":\"全部\",\"amountCurrency\":\"SAR\",\"commissionRate\":\"27%\",\"effectiveDate\":\"2025-09-01\"}");
        baseItem.setSortNo(1);
        FileParseResultItemRow resultItem = new FileParseResultItemRow();
        resultItem.setId(50090L);
        resultItem.setResultId(40027L);
        resultItem.setTaskId(20027L);
        resultItem.setTargetPlanId(4001L);
        resultItem.setItemType("commission_rule");
        resultItem.setNaturalKey("KSA|noon FBN|Fashion|Apparel, Footwear|27%|2025-09-01");
        resultItem.setNaturalKeyHash("new-ai-hash");
        resultItem.setChangeType("changed");
        resultItem.setReviewStatus("confirmed");
        resultItem.setValidationStatus("pass");
        resultItem.setEffectiveValidationStatus("pass");
        resultItem.setNormalizedPayloadJson("{\"country\":\"KSA\",\"platform\":\"noon\",\"fulfillmentType\":\"FBN\",\"categoryName\":\"Fashion > Apparel, Footwear\",\"amountRangeLabel\":\"all\",\"amountCurrency\":\"SAR\",\"commissionRate\":\"28\",\"effectiveDate\":\"2025-09-01\"}");
        resultItem.setEffectivePayloadJson(resultItem.getNormalizedPayloadJson());
        resultItem.setSortNo(1);

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20027L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4001L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.selectStandardVersion(2001L)).thenReturn(standardVersion(2001L));
        when(fileManagementParseMapper.selectItemStandards(2001L)).thenReturn(List.of(commissionItemStandard()));
        when(fileManagementParseMapper.selectPublishAuditByIdempotency(20027L, "idem-commission-replace")).thenReturn(null);
        when(fileManagementParseMapper.selectActiveVersionForUpdate(4001L, "global", "global:*")).thenReturn(activeVersion);
        when(fileManagementParseMapper.countBlockingResultItems(40027L)).thenReturn(0);
        when(fileManagementParseMapper.selectVersionItems(70009L)).thenReturn(List.of(baseItem));
        when(fileManagementParseMapper.selectResultItemsForPublish(40027L)).thenReturn(List.of(resultItem));
        when(fileManagementParseMapper.nextVersionId()).thenReturn(70028L);
        when(fileManagementParseMapper.insertVersion(
                anyLong(),
                anyString(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                nullable(Long.class),
                anyString(),
                anyString(),
                any(LocalDateTime.class),
                anyString(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.nextVersionItemId()).thenReturn(88028L);
        when(fileManagementParseMapper.insertVersionItem(
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                nullable(Long.class),
                anyString(),
                anyString(),
                anyInt(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.updateActiveVersion(anyLong(), anyString(), anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(1);
        when(fileManagementParseMapper.markTaskPublished(20027L, 40027L, 10001L)).thenReturn(1);
        when(fileManagementParseMapper.nextAuditLogId()).thenReturn(90028L);

        FileParsePublishCommand command = new FileParsePublishCommand();
        command.setExpectedResultId(40027L);
        command.setConfirmMessage("确认发布");

        service.publishTask(
                new AuthenticatedSession(10001L, 1L, 0),
                20027L,
                command,
                "idem-commission-replace"
        );

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> sourceResultItemIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(fileManagementParseMapper, times(1)).insertVersionItem(
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                payloadCaptor.capture(),
                sourceResultItemIdCaptor.capture(),
                anyString(),
                anyString(),
                anyInt(),
                anyLong()
        );
        Map<?, ?> payload = new ObjectMapper().readValue(payloadCaptor.getValue(), Map.class);
        assertEquals(50090L, sourceResultItemIdCaptor.getValue());
        assertEquals("28%", payload.get("commissionRate"));
        assertEquals("全部", payload.get("amountRangeLabel"));
    }

    @Test
    void shouldListOverviewItemsUsingEffectivePayload() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4005L, "logistics_yite", "物流-义特", "logistics_rule");
        FileParseTaskRow task = task(20001L, 4005L, "ready_to_publish", 0);
        task.setCurrentResultId(40001L);
        FileParseResultItemRow row = resultItem("confirmed", "changed");
        row.setEffectivePayloadJson("{\"channelKey\":\"义特-UAE-Dubai-SEA-card\",\"country\":\"UAE\",\"city\":\"Dubai\",\"shippingMethod\":\"海运\",\"feeItem\":\"卡牌 / 普货\",\"billingRule\":\"28 CNY/KG，最低 12KG\"}");

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20001L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4005L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.selectStandardVersion(5105L)).thenReturn(standardVersion(5105L));
        when(fileManagementParseMapper.selectItemStandards(5105L)).thenReturn(List.of(logisticsItemStandard()));
        when(fileManagementParseMapper.countOverviewResultItems(40001L)).thenReturn(1);
        when(fileManagementParseMapper.selectOverviewResultItems(40001L, 1000, 0)).thenReturn(List.of(row));

        FileParseOverviewItemsView view = service.listOverviewItems(
                new AuthenticatedSession(10001L, 1L, 0),
                20001L,
                1,
                1000
        );

        assertEquals(40001L, view.getResultId());
        assertEquals(1, view.getTotal());
        assertEquals(1, view.getItems().size());
        assertEquals("28 CNY/KG，最低 12KG", view.getItems().get(0).getFields().get("billingRule"));
        assertFalse(view.getColumns().isEmpty());
    }

    @Test
    void shouldExportOverviewItemsAsWorkbook() throws Exception {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4005L, "logistics_yite", "物流-义特", "logistics_rule");
        FileParseTaskRow task = task(20001L, 4005L, "ready_to_publish", 0);
        task.setCurrentResultId(40001L);
        FileParseResultItemRow row = resultItem("confirmed", "changed");
        row.setEffectivePayloadJson("{\"channelKey\":\"义特-UAE-Dubai-SEA-card\",\"country\":\"UAE\",\"city\":\"Dubai\",\"shippingMethod\":\"海运\",\"feeItem\":\"卡牌 / 普货\",\"billingRule\":\"28 CNY/KG，最低 12KG\",\"extraParsedField\":\"补充字段\"}");

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20001L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4005L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.selectStandardVersion(5105L)).thenReturn(standardVersion(5105L));
        when(fileManagementParseMapper.selectItemStandards(5105L)).thenReturn(List.of(logisticsItemStandard()));
        when(fileManagementParseMapper.countOverviewResultItems(40001L)).thenReturn(1);
        when(fileManagementParseMapper.selectOverviewResultItems(40001L, 1000, 0)).thenReturn(List.of(row));
        when(fileManagementParseMapper.nextAuditLogId()).thenReturn(90002L);
        when(fileManagementParseMapper.insertAuditLog(
                anyLong(),
                anyLong(),
                anyLong(),
                nullable(Long.class),
                anyString(),
                anyString(),
                nullable(String.class),
                nullable(String.class),
                anyLong()
        )).thenReturn(1);

        FileParseExportFile exportFile = service.exportOverviewItems(
                new AuthenticatedSession(10001L, 1L, 0),
                20001L
        );

        assertEquals("义特 FBN 报价 2026-05-解析总览.xlsx", exportFile.getFileName());
        assertEquals(FileParseQueryViewService.OVERVIEW_EXPORT_CONTENT_TYPE, exportFile.getContentType());
        assertTrue(exportFile.getContent().length > 0);
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(exportFile.getContent()))) {
            Row header = workbook.getSheet("解析总览").getRow(0);
            assertEquals("结果类型", header.getCell(0).getStringCellValue());
            assertEquals("自然键", header.getCell(1).getStringCellValue());
            assertEquals("计费内容", header.getCell(7).getStringCellValue());
            assertEquals("时效", header.getCell(8).getStringCellValue());
            assertEquals(9, header.getLastCellNum());
            Row dataRow = workbook.getSheet("解析总览").getRow(1);
            assertEquals("logistics_channel_rule", dataRow.getCell(0).getStringCellValue());
            assertEquals("28 CNY/KG，最低 12KG", dataRow.getCell(7).getStringCellValue());
        }
        verify(fileManagementParseMapper).insertAuditLog(
                90002L,
                20001L,
                4005L,
                null,
                "export_overview_items",
                "导出解析总览：义特 FBN 报价 2026-05-解析总览.xlsx",
                null,
                null,
                10001L
        );
    }

    @Test
    void shouldListGlobalVersionsForVisibleTargetPlan() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4005L, "logistics_yite", "物流-义特", "logistics_rule");
        FileParseVersionSummaryRow version = versionSummary(70005L, "YITE-AE-FBN-2026-04", "active");

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4005L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.countVersionsByTargetPlan(4005L)).thenReturn(1);
        when(fileManagementParseMapper.selectVersionsByTargetPlan(4005L, 20, 0)).thenReturn(List.of(version));

        FileParseVersionListView view = service.listVersions(
                new AuthenticatedSession(10001L, 1L, 0),
                4005L,
                1,
                20
        );

        assertEquals(4005L, view.getTargetPlanId());
        assertEquals(1, view.getTotal());
        assertEquals("YITE-AE-FBN-2026-04", view.getItems().get(0).getVersionNo());
        assertEquals("active", view.getItems().get(0).getStatus());
    }

    @Test
    void shouldListVersionSnapshotItems() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4005L, "logistics_yite", "物流-义特", "logistics_rule");
        FileParseVersionSummaryRow version = versionSummary(70005L, "YITE-AE-FBN-2026-04", "active");
        FileParseVersionItemRow versionItem = versionItem(80005L, 70005L, "义特-UAE-Dubai-SEA-card");
        versionItem.setSourceResultItemId(50005L);

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectVersion(70005L)).thenReturn(version);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4005L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.selectItemStandards(5105L)).thenReturn(List.of(logisticsItemStandard()));
        when(fileManagementParseMapper.countVersionSnapshotItems(70005L)).thenReturn(1);
        when(fileManagementParseMapper.selectVersionSnapshotItems(70005L, 1000, 0)).thenReturn(List.of(versionItem));

        FileParseVersionItemsView view = service.listVersionItems(
                new AuthenticatedSession(10001L, 1L, 0),
                70005L,
                1,
                1000
        );

        assertEquals(70005L, view.getVersionId());
        assertEquals("YITE-AE-FBN-2026-04", view.getVersionNo());
        assertEquals(1, view.getTotal());
        assertEquals("24 CNY/KG，最低 10KG", view.getItems().get(0).getFields().get("billingRule"));
        assertEquals(50005L, view.getItems().get(0).getSourceResultItemId());
    }

    @Test
    void shouldPublishCompleteGlobalVersionSnapshot() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4005L, "logistics_yite", "物流-义特", "logistics_rule");
        FileParseTaskRow task = task(20001L, 4005L, "ready_to_publish", 0);
        task.setBaseVersionId(70005L);
        task.setCurrentResultId(40001L);
        FileParseActiveVersionRow activeVersion = activeVersion(4005L, 70005L, "YITE-AE-FBN-2026-04");
        FileParseVersionItemRow carriedBaseItem = versionItem(80006L, 70005L, "义特-UAE-Dubai-AIR-battery");
        carriedBaseItem.setNaturalKeyHash("hash-2");
        carriedBaseItem.setVersionPayloadJson("{\"channelKey\":\"义特-UAE-Dubai-AIR-battery\",\"country\":\"UAE\",\"city\":\"Dubai\",\"shippingMethod\":\"空运\",\"feeItem\":\"带电产品\",\"billingRule\":\"40 CNY/KG，最低 20 AED\"}");
        carriedBaseItem.setSortNo(2);
        FileParseResultItemRow changedItem = resultItem("confirmed", "changed");
        changedItem.setEffectivePayloadJson("{\"channelKey\":\"义特-UAE-Dubai-SEA-card\",\"country\":\"UAE\",\"city\":\"Dubai\",\"shippingMethod\":\"海运\",\"feeItem\":\"卡牌 / 普货\",\"billingRule\":\"26 CNY/KG，最低 12KG\"}");
        FileParseResultItemRow addedItem = resultItem("confirmed", "added");
        addedItem.setId(50002L);
        addedItem.setNaturalKey("义特-UAE-Dubai-SEA-small");
        addedItem.setNaturalKeyHash("hash-3");
        addedItem.setNormalizedPayloadJson("{\"channelKey\":\"义特-UAE-Dubai-SEA-small\",\"country\":\"UAE\",\"city\":\"Dubai\",\"shippingMethod\":\"海运\",\"feeItem\":\"小件\",\"billingRule\":\"12 CNY/KG，最低 5KG\"}");
        addedItem.setEffectivePayloadJson(addedItem.getNormalizedPayloadJson());
        addedItem.setSortNo(3);

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20001L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4005L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.selectStandardVersion(5105L)).thenReturn(standardVersion(5105L));
        when(fileManagementParseMapper.selectItemStandards(5105L)).thenReturn(List.of(logisticsItemStandard()));
        when(fileManagementParseMapper.selectPublishAuditByIdempotency(20001L, "idem-publish-1")).thenReturn(null);
        when(fileManagementParseMapper.selectActiveVersionForUpdate(4005L, "global", "global:*")).thenReturn(activeVersion);
        when(fileManagementParseMapper.countBlockingResultItems(40001L)).thenReturn(0);
        when(fileManagementParseMapper.selectVersionItems(70005L))
                .thenReturn(List.of(versionItem(80005L, 70005L, "义特-UAE-Dubai-SEA-card"), carriedBaseItem));
        when(fileManagementParseMapper.selectResultItemsForPublish(40001L)).thenReturn(List.of(changedItem, addedItem));
        when(fileManagementParseMapper.nextVersionId()).thenReturn(70006L);
        when(fileManagementParseMapper.insertVersion(
                anyLong(),
                anyString(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                any(LocalDateTime.class),
                anyString(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.nextVersionItemId()).thenReturn(80007L, 80008L, 80009L);
        when(fileManagementParseMapper.insertVersionItem(
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                nullable(Long.class),
                anyString(),
                anyString(),
                anyInt(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.updateActiveVersion(anyLong(), anyString(), anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(1);
        when(fileManagementParseMapper.markTaskPublished(20001L, 40001L, 10001L)).thenReturn(1);
        when(fileManagementParseMapper.nextAuditLogId()).thenReturn(90001L);

        FileParsePublishCommand command = new FileParsePublishCommand();
        command.setExpectedResultId(40001L);
        command.setConfirmMessage("确认发布为新版本");
        command.setRemark("五月物流规则确认发布");

        FileParsePublishView view = service.publishTask(
                new AuthenticatedSession(10001L, 1L, 0),
                20001L,
                command,
                "idem-publish-1"
        );

        assertEquals(70006L, view.getVersionId());
        assertTrue(view.getVersionNo().startsWith("LOGISTICS-YITE-"));
        assertTrue(view.getVersionNo().endsWith("-70006"));
        assertEquals("active", view.getStatus());
        ArgumentCaptor<String> naturalKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileManagementParseMapper, times(3)).insertVersionItem(
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                naturalKeyCaptor.capture(),
                anyString(),
                anyString(),
                nullable(Long.class),
                anyString(),
                anyString(),
                anyInt(),
                anyLong()
        );
        assertTrue(naturalKeyCaptor.getAllValues().contains("义特-UAE-Dubai-AIR-battery"));
        verify(fileManagementParseMapper).markVersionsHistory(4005L, "global", "global:*", 70006L, 10001L);
        verify(fileManagementParseMapper).insertPublishAudit(
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyLong()
        );
    }

    @Test
    void shouldRemoveConfirmedDeleteSuspectedAndKeepOldPayloadWhenPublishing() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4005L, "logistics_yite", "物流-义特", "logistics_rule");
        FileParseTaskRow task = task(20033L, 4005L, "ready_to_publish", 0);
        task.setBaseVersionId(70005L);
        task.setCurrentResultId(40033L);
        FileParseActiveVersionRow activeVersion = activeVersion(4005L, 70005L, "YITE-AE-FBN-2026-04");
        FileParseVersionItemRow deleteBaseItem = versionItem(80010L, 70005L, "义特-UAE-Dubai-SEA-card");
        deleteBaseItem.setNaturalKeyHash("hash-delete");
        FileParseVersionItemRow keepBaseItem = versionItem(80011L, 70005L, "义特-UAE-Dubai-AIR-battery");
        keepBaseItem.setNaturalKeyHash("hash-keep");
        keepBaseItem.setVersionPayloadJson("{\"channelKey\":\"义特-UAE-Dubai-AIR-battery\",\"country\":\"UAE\",\"city\":\"Dubai\",\"shippingMethod\":\"空运\",\"feeItem\":\"带电产品\",\"billingRule\":\"40 CNY/KG，最低 20 AED\"}");
        keepBaseItem.setSortNo(2);

        FileParseResultItemRow deleteItem = resultItem("confirmed", "delete_suspected");
        deleteItem.setId(50033L);
        deleteItem.setTaskId(20033L);
        deleteItem.setResultId(40033L);
        deleteItem.setNaturalKey("义特-UAE-Dubai-SEA-card");
        deleteItem.setNaturalKeyHash("hash-delete");
        deleteItem.setOldPayloadJson(deleteBaseItem.getVersionPayloadJson());

        FileParseResultItemRow keepOldItem = resultItem("keep_old", "changed");
        keepOldItem.setId(50034L);
        keepOldItem.setTaskId(20033L);
        keepOldItem.setResultId(40033L);
        keepOldItem.setNaturalKey("义特-UAE-Dubai-AIR-battery");
        keepOldItem.setNaturalKeyHash("hash-keep");
        keepOldItem.setOldPayloadJson(keepBaseItem.getVersionPayloadJson());
        keepOldItem.setNormalizedPayloadJson("{\"channelKey\":\"义特-UAE-Dubai-AIR-battery\",\"country\":\"UAE\",\"city\":\"Dubai\",\"shippingMethod\":\"空运\",\"feeItem\":\"带电产品\",\"billingRule\":\"45 CNY/KG，最低 25 AED\"}");
        keepOldItem.setEffectivePayloadJson(keepBaseItem.getVersionPayloadJson());
        keepOldItem.setSortNo(2);

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20033L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4005L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.selectStandardVersion(5105L)).thenReturn(standardVersion(5105L));
        when(fileManagementParseMapper.selectItemStandards(5105L)).thenReturn(List.of(logisticsItemStandard()));
        when(fileManagementParseMapper.selectPublishAuditByIdempotency(20033L, "idem-delete-keep-old")).thenReturn(null);
        when(fileManagementParseMapper.selectActiveVersionForUpdate(4005L, "global", "global:*")).thenReturn(activeVersion);
        when(fileManagementParseMapper.countBlockingResultItems(40033L)).thenReturn(0);
        when(fileManagementParseMapper.selectVersionItems(70005L)).thenReturn(List.of(deleteBaseItem, keepBaseItem));
        when(fileManagementParseMapper.selectResultItemsForPublish(40033L)).thenReturn(List.of(deleteItem, keepOldItem));
        when(fileManagementParseMapper.nextVersionId()).thenReturn(70033L);
        when(fileManagementParseMapper.insertVersion(
                anyLong(),
                anyString(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                any(LocalDateTime.class),
                anyString(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.nextVersionItemId()).thenReturn(80033L);
        when(fileManagementParseMapper.insertVersionItem(
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                nullable(Long.class),
                anyString(),
                anyString(),
                anyInt(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.updateActiveVersion(anyLong(), anyString(), anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(1);
        when(fileManagementParseMapper.markTaskPublished(20033L, 40033L, 10001L)).thenReturn(1);
        when(fileManagementParseMapper.nextAuditLogId()).thenReturn(90033L);

        FileParsePublishCommand command = new FileParsePublishCommand();
        command.setExpectedResultId(40033L);
        command.setConfirmMessage("确认删除和保留旧值");

        service.publishTask(
                new AuthenticatedSession(10001L, 1L, 0),
                20033L,
                command,
                "idem-delete-keep-old"
        );

        ArgumentCaptor<String> naturalKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileManagementParseMapper, times(1)).insertVersionItem(
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                naturalKeyCaptor.capture(),
                anyString(),
                payloadCaptor.capture(),
                nullable(Long.class),
                anyString(),
                anyString(),
                anyInt(),
                anyLong()
        );
        assertEquals("义特-UAE-Dubai-AIR-battery", naturalKeyCaptor.getValue());
        assertTrue(payloadCaptor.getValue().contains("40 CNY/KG"));
        assertFalse(payloadCaptor.getValue().contains("45 CNY/KG"));
    }

    @Test
    void shouldPublishSingleCommissionRateWithDefaultRangeAndCurrency() throws Exception {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4001L, "commission_ksa", "佣金-KSA", "official_commission");
        FileParseTaskRow task = task(20024L, 4001L, "ready_to_publish", 0);
        task.setStandardVersionId(2001L);
        task.setCurrentResultId(40010L);
        FileParseActiveVersionRow activeVersion = activeVersion(4001L, null, null);

        FileParseResultItemRow item = new FileParseResultItemRow();
        item.setId(50075L);
        item.setResultId(40010L);
        item.setTaskId(20024L);
        item.setTargetPlanId(4001L);
        item.setItemType("commission_rule");
        item.setNaturalKey("KSA|noon|FBN|Fashion|Apparel, Footwear|27%|2025-09-01");
        item.setNaturalKeyHash("hash-commission-apparel");
        item.setChangeType("added");
        item.setReviewStatus("confirmed");
        item.setValidationStatus("pass");
        item.setEffectiveValidationStatus("pass");
        item.setNormalizedPayloadJson("{\"categoryName\":\"Fashion > Apparel, Footwear\",\"commissionRate\":\"27%\",\"country\":\"KSA\",\"effectiveDate\":\"2025-09-01\",\"fulfillmentType\":\"FBN\",\"platform\":\"noon\"}");
        item.setEffectivePayloadJson(item.getNormalizedPayloadJson());
        item.setSortNo(1);

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20024L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4001L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.selectStandardVersion(2001L)).thenReturn(standardVersion(2001L));
        when(fileManagementParseMapper.selectItemStandards(2001L)).thenReturn(List.of(commissionItemStandard()));
        when(fileManagementParseMapper.selectPublishAuditByIdempotency(20024L, "idem-commission-publish")).thenReturn(null);
        when(fileManagementParseMapper.selectActiveVersionForUpdate(4001L, "global", "global:*")).thenReturn(activeVersion);
        when(fileManagementParseMapper.countBlockingResultItems(40010L)).thenReturn(0);
        when(fileManagementParseMapper.selectResultItemsForPublish(40010L)).thenReturn(List.of(item));
        when(fileManagementParseMapper.nextVersionId()).thenReturn(70020L);
        when(fileManagementParseMapper.insertVersion(
                anyLong(),
                anyString(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                nullable(Long.class),
                anyString(),
                anyString(),
                any(LocalDateTime.class),
                anyString(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.nextVersionItemId()).thenReturn(88020L);
        when(fileManagementParseMapper.insertVersionItem(
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                nullable(Long.class),
                anyString(),
                anyString(),
                anyInt(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.updateActiveVersion(anyLong(), anyString(), anyString(), anyLong(), anyString(), anyLong()))
                .thenReturn(1);
        when(fileManagementParseMapper.markTaskPublished(20024L, 40010L, 10001L)).thenReturn(1);
        when(fileManagementParseMapper.nextAuditLogId()).thenReturn(90024L);

        FileParsePublishCommand command = new FileParsePublishCommand();
        command.setExpectedResultId(40010L);
        command.setConfirmMessage("确认发布");

        service.publishTask(
                new AuthenticatedSession(10001L, 1L, 0),
                20024L,
                command,
                "idem-commission-publish"
        );

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileManagementParseMapper).insertVersionItem(
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                payloadCaptor.capture(),
                nullable(Long.class),
                anyString(),
                anyString(),
                anyInt(),
                anyLong()
        );
        Map<?, ?> payload = new ObjectMapper().readValue(payloadCaptor.getValue(), Map.class);
        assertEquals("全部", payload.get("amountRangeLabel"));
        assertEquals("SAR", payload.get("amountCurrency"));
        assertEquals("KSA", payload.get("country"));
        assertEquals("Noon", payload.get("platform"));
        assertEquals("FBN", payload.get("fulfillmentType"));
        assertEquals("全部", payload.get("brandRestriction"));
    }

    @Test
    void shouldRejectPublishWhenCurrentResultHasBlockingRows() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4005L, "logistics_yite", "物流-义特", "logistics_rule");
        FileParseTaskRow task = task(20034L, 4005L, "ready_to_publish", 0);
        task.setBaseVersionId(70005L);
        task.setCurrentResultId(40034L);

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20034L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4005L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.selectStandardVersion(5105L)).thenReturn(standardVersion(5105L));
        when(fileManagementParseMapper.selectItemStandards(5105L)).thenReturn(List.of(logisticsItemStandard()));
        when(fileManagementParseMapper.selectPublishAuditByIdempotency(20034L, "idem-publish-blocked")).thenReturn(null);
        when(fileManagementParseMapper.selectActiveVersionForUpdate(4005L, "global", "global:*"))
                .thenReturn(activeVersion(4005L, 70005L, "YITE-AE-FBN-2026-04"));
        when(fileManagementParseMapper.countBlockingResultItems(40034L)).thenReturn(1);

        FileParsePublishCommand command = new FileParsePublishCommand();
        command.setExpectedResultId(40034L);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.publishTask(
                        new AuthenticatedSession(10001L, 1L, 0),
                        20034L,
                        command,
                        "idem-publish-blocked"
                )
        );

        assertTrue(error.getMessage().contains("待处理或硬错误"));
        verify(fileManagementParseMapper, never()).selectResultItemsForPublish(40034L);
    }

    @Test
    void shouldRejectPublishWhenOpenHardValidationIssuesRemain() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4005L, "logistics_yite", "物流-义特", "logistics_rule");
        FileParseTaskRow task = task(20035L, 4005L, "ready_to_publish", 0);
        task.setBaseVersionId(70005L);
        task.setCurrentResultId(40035L);

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20035L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4005L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.selectStandardVersion(5105L)).thenReturn(standardVersion(5105L));
        when(fileManagementParseMapper.selectItemStandards(5105L)).thenReturn(List.of(logisticsItemStandard()));
        when(fileManagementParseMapper.selectPublishAuditByIdempotency(20035L, "idem-publish-hard-issue")).thenReturn(null);
        when(fileManagementParseMapper.selectActiveVersionForUpdate(4005L, "global", "global:*"))
                .thenReturn(activeVersion(4005L, 70005L, "YITE-AE-FBN-2026-04"));
        when(fileManagementParseMapper.countBlockingResultItems(40035L)).thenReturn(0);
        when(fileManagementParseMapper.countOpenHardValidationIssues(20035L)).thenReturn(1);

        FileParsePublishCommand command = new FileParsePublishCommand();
        command.setExpectedResultId(40035L);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.publishTask(
                        new AuthenticatedSession(10001L, 1L, 0),
                        20035L,
                        command,
                        "idem-publish-hard-issue"
                )
        );

        assertTrue(error.getMessage().contains("硬错误级校验问题"));
        verify(fileManagementParseMapper, never()).selectResultItemsForPublish(40035L);
    }

    @Test
    void shouldRejectPublishWhenActiveVersionChanged() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4005L, "logistics_yite", "物流-义特", "logistics_rule");
        FileParseTaskRow task = task(20001L, 4005L, "ready_to_publish", 0);
        task.setBaseVersionId(70005L);
        task.setCurrentResultId(40001L);

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20001L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4005L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.selectStandardVersion(5105L)).thenReturn(standardVersion(5105L));
        when(fileManagementParseMapper.selectItemStandards(5105L)).thenReturn(List.of(logisticsItemStandard()));
        when(fileManagementParseMapper.selectPublishAuditByIdempotency(20001L, "idem-publish-stale")).thenReturn(null);
        when(fileManagementParseMapper.selectActiveVersionForUpdate(4005L, "global", "global:*"))
                .thenReturn(activeVersion(4005L, 70006L, "YITE-AE-FBN-2026-05"));

        FileParsePublishCommand command = new FileParsePublishCommand();
        command.setExpectedResultId(40001L);

        assertThrows(
                IllegalStateException.class,
                () -> service.publishTask(
                        new AuthenticatedSession(10001L, 1L, 0),
                        20001L,
                        command,
                        "idem-publish-stale"
                )
        );
    }

    @Test
    void shouldPublishFirstVersionWhenActivePointerIsMissing() {
        FileParseUserContext admin = user(10001L, 0, "SYSTEM_ADMIN", "系统管理员");
        FileParseTargetPlanRow plan = targetPlan(4006L, "logistics_et", "物流-易通", "logistics_rule");
        FileParseTaskRow task = task(20040L, 4006L, "ready_to_publish", 0);
        task.setStandardVersionId(5105L);
        task.setCurrentResultId(40040L);

        FileParseResultItemRow item = new FileParseResultItemRow();
        item.setId(50140L);
        item.setResultId(40040L);
        item.setTaskId(20040L);
        item.setTargetPlanId(4006L);
        item.setItemType("logistics_channel_rule");
        item.setNaturalKey("ET-KSA-air-A");
        item.setNaturalKeyHash("hash-et-first");
        item.setChangeType("added");
        item.setReviewStatus("confirmed");
        item.setValidationStatus("pass");
        item.setEffectiveValidationStatus("pass");
        item.setNormalizedPayloadJson("{\"channelKey\":\"ET-KSA-air\",\"country\":\"KSA\",\"shippingMethod\":\"空运\",\"feeItem\":\"A类\",\"billingRule\":\"44 CNY/KG\"}");
        item.setEffectivePayloadJson(item.getNormalizedPayloadJson());
        item.setSortNo(1);

        when(fileManagementParseMapper.selectUserContext(10001L)).thenReturn(admin);
        when(fileManagementParseMapper.selectTask(20040L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4006L, 0, true)).thenReturn(plan);
        when(fileManagementParseMapper.selectStandardVersion(5105L)).thenReturn(standardVersion(5105L));
        when(fileManagementParseMapper.selectItemStandards(5105L)).thenReturn(List.of(logisticsItemStandard()));
        when(fileManagementParseMapper.selectPublishAuditByIdempotency(20040L, "idem-first-et-publish")).thenReturn(null);
        when(fileManagementParseMapper.selectActiveVersionForUpdate(4006L, "global", "global:*")).thenReturn(null);
        when(fileManagementParseMapper.countBlockingResultItems(40040L)).thenReturn(0);
        when(fileManagementParseMapper.selectResultItemsForPublish(40040L)).thenReturn(List.of(item));
        when(fileManagementParseMapper.nextVersionId()).thenReturn(70040L);
        when(fileManagementParseMapper.insertVersion(
                anyLong(),
                anyString(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                nullable(Long.class),
                anyString(),
                anyString(),
                any(LocalDateTime.class),
                anyString(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.nextVersionItemId()).thenReturn(88040L);
        when(fileManagementParseMapper.insertVersionItem(
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                nullable(Long.class),
                anyString(),
                anyString(),
                anyInt(),
                anyLong()
        )).thenReturn(1);
        String expectedVersionNo = "LOGISTICS-ET-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-70040";
        when(fileManagementParseMapper.updateActiveVersion(4006L, "global", "global:*", 70040L, expectedVersionNo, 10001L))
                .thenReturn(0);
        when(fileManagementParseMapper.nextActiveVersionId()).thenReturn(71040L);
        when(fileManagementParseMapper.upsertActiveVersion(71040L, 4006L, "global", "global:*", 70040L, expectedVersionNo, 10001L))
                .thenReturn(1);
        when(fileManagementParseMapper.markTaskPublished(20040L, 40040L, 10001L)).thenReturn(1);
        when(fileManagementParseMapper.nextAuditLogId()).thenReturn(90040L);

        FileParsePublishCommand command = new FileParsePublishCommand();
        command.setExpectedResultId(40040L);
        command.setConfirmMessage("确认首次发布");

        FileParsePublishView view = service.publishTask(
                new AuthenticatedSession(10001L, 1L, 0),
                20040L,
                command,
                "idem-first-et-publish"
        );

        assertEquals(70040L, view.getVersionId());
        verify(fileManagementParseMapper).upsertActiveVersion(
                71040L,
                4006L,
                "global",
                "global:*",
                70040L,
                expectedVersionNo,
                10001L
        );
    }

    @Test
    void shouldRejectBossPublishBecauseOnlyAdminCanPublish() {
        FileParseUserContext boss = user(10002L, 1, "BOSS", "老板");
        FileParseTaskRow task = task(20001L, 4005L, "ready_to_publish", 0);
        task.setCurrentResultId(40001L);

        when(fileManagementParseMapper.selectUserContext(10002L)).thenReturn(boss);
        when(fileManagementParseMapper.countActiveUserMenu(10002L, LocalDbFileManagementParseService.FILE_MANAGEMENT_MENU_ID))
                .thenReturn(1);
        when(fileManagementParseMapper.selectTask(20001L)).thenReturn(task);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4005L, 1, false))
                .thenReturn(targetPlan(4005L, "logistics_yite", "物流-义特", "logistics_rule"));

        FileParsePublishCommand command = new FileParsePublishCommand();
        command.setExpectedResultId(40001L);

        assertThrows(
                FileParseAccessDeniedException.class,
                () -> service.publishTask(
                        new AuthenticatedSession(10002L, 2L, 1),
                        20001L,
                        command,
                        "idem-publish-boss"
                )
        );
    }

    @Test
    void shouldLetBossSaveLogisticsChannelActivationsWithoutPublishingVersion() {
        FileParseUserContext boss = user(10002L, 1, "BOSS", "老板");
        FileParseTargetPlanRow plan = targetPlan(4005L, "logistics_yite", "物流-义特", "logistics_rule");
        FileParseVersionSummaryRow version = versionSummary(70005L, "YITE-AE-FBN-2026-04", "active");
        FileParseVersionItemRow sea = logisticsVersionItem(80001L, 70005L, "yite_ae_fbn_sea", "海运");
        FileParseVersionItemRow air = logisticsVersionItem(80002L, 70005L, "yite_ae_fbn_air", "空运");

        when(fileManagementParseMapper.selectUserContext(10002L)).thenReturn(boss);
        when(fileManagementParseMapper.countActiveUserMenu(10002L, LocalDbFileManagementParseService.FILE_MANAGEMENT_MENU_ID))
                .thenReturn(1);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4005L, 1, false)).thenReturn(plan);
        when(fileManagementParseMapper.selectVersion(70005L)).thenReturn(version);
        when(fileManagementParseMapper.selectVersionItems(70005L)).thenReturn(List.of(sea, air));
        when(fileManagementParseMapper.softDeleteLogisticsChannelActivations(10002L, 4005L, 10002L)).thenReturn(1);
        when(fileManagementParseMapper.nextLogisticsChannelActivationId()).thenReturn(95001L, 95002L);
        when(fileManagementParseMapper.insertLogisticsChannelActivation(
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.nextAuditLogId()).thenReturn(90003L);
        when(fileManagementParseMapper.insertAuditLog(
                anyLong(),
                nullable(Long.class),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                nullable(String.class),
                nullable(String.class),
                anyLong()
        )).thenReturn(1);

        FileParseLogisticsChannelActivationCommand command = new FileParseLogisticsChannelActivationCommand();
        command.setTargetPlanId(4005L);
        command.setVersionId(70005L);
        command.setSelectedChannelKeys(List.of("yite_ae_fbn_sea", "yite_ae_fbn_air"));

        FileParseLogisticsChannelActivationView view = service.saveLogisticsChannelActivations(
                new AuthenticatedSession(10002L, 2L, 1),
                command
        );

        assertEquals(4005L, view.getTargetPlanId());
        assertEquals(70005L, view.getVersionId());
        assertEquals(10002L, view.getOwnerUserId());
        assertEquals(List.of("yite_ae_fbn_sea", "yite_ae_fbn_air"), view.getSelectedChannelKeys());
        assertEquals(2, view.getChannels().size());
        assertTrue(view.getChannels().stream().allMatch(FileParseLogisticsChannelView::isSelected));
        verify(fileManagementParseMapper).softDeleteLogisticsChannelActivations(10002L, 4005L, 10002L);
        verify(fileManagementParseMapper, times(2)).insertLogisticsChannelActivation(
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyLong()
        );
        verify(fileManagementParseMapper).insertAuditLog(
                90003L,
                null,
                4005L,
                70005L,
                "save_logistics_channel_activation",
                "保存物流渠道生效选择：2 个渠道",
                null,
                null,
                10002L
        );
        verify(fileManagementParseMapper, never()).nextVersionId();
        verify(fileManagementParseMapper, never()).updateActiveVersion(
                anyLong(),
                anyString(),
                anyString(),
                anyLong(),
                anyString(),
                anyLong()
        );
    }

    @Test
    void shouldRejectOpsManagerSavingLogisticsChannelActivations() {
        FileParseUserContext opsManager = user(10003L, 2, "OPS_MANAGER", "运营主管");
        FileParseTargetPlanRow plan = targetPlan(4005L, "logistics_yite", "物流-义特", "logistics_rule");
        when(fileManagementParseMapper.selectUserContext(10003L)).thenReturn(opsManager);
        when(fileManagementParseMapper.countActiveUserMenu(10003L, LocalDbFileManagementParseService.FILE_MANAGEMENT_MENU_ID))
                .thenReturn(1);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4005L, 2, false)).thenReturn(plan);

        FileParseLogisticsChannelActivationCommand command = new FileParseLogisticsChannelActivationCommand();
        command.setTargetPlanId(4005L);
        command.setVersionId(70005L);
        command.setSelectedChannelKeys(List.of("yite_ae_fbn_sea"));

        assertThrows(
                FileParseAccessDeniedException.class,
                () -> service.saveLogisticsChannelActivations(
                        new AuthenticatedSession(10003L, 3L, 2),
                        command
                )
        );
        verify(fileManagementParseMapper, never()).selectVersion(anyLong());
        verify(fileManagementParseMapper, never()).softDeleteLogisticsChannelActivations(anyLong(), anyLong(), anyLong());
    }

    @Test
    void shouldListBossLogisticsChannelActivations() {
        FileParseUserContext boss = user(10002L, 1, "BOSS", "老板");
        FileParseTargetPlanRow plan = targetPlan(4005L, "logistics_yite", "物流-义特", "logistics_rule");
        FileParseVersionSummaryRow version = versionSummary(70005L, "YITE-AE-FBN-2026-04", "active");
        FileParseVersionItemRow sea = logisticsVersionItem(80001L, 70005L, "yite_ae_fbn_sea", "海运");
        FileParseVersionItemRow air = logisticsVersionItem(80002L, 70005L, "yite_ae_fbn_air", "空运");

        when(fileManagementParseMapper.selectUserContext(10002L)).thenReturn(boss);
        when(fileManagementParseMapper.countActiveUserMenu(10002L, LocalDbFileManagementParseService.FILE_MANAGEMENT_MENU_ID))
                .thenReturn(1);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4005L, 1, false)).thenReturn(plan);
        when(fileManagementParseMapper.selectVersion(70005L)).thenReturn(version);
        when(fileManagementParseMapper.selectActiveLogisticsChannelKeys(10002L, 4005L, 70005L))
                .thenReturn(List.of("yite_ae_fbn_air"));
        when(fileManagementParseMapper.selectVersionItems(70005L)).thenReturn(List.of(sea, air));

        FileParseLogisticsChannelActivationView view = service.listLogisticsChannelActivations(
                new AuthenticatedSession(10002L, 2L, 1),
                4005L,
                70005L
        );

        assertEquals(4005L, view.getTargetPlanId());
        assertEquals(70005L, view.getVersionId());
        assertEquals(10002L, view.getOwnerUserId());
        assertEquals(List.of("yite_ae_fbn_air"), view.getSelectedChannelKeys());
        assertEquals(2, view.getChannels().size());
        assertFalse(view.getChannels().get(0).isSelected());
        assertTrue(view.getChannels().get(1).isSelected());
    }

    @Test
    void shouldRejectNonLogisticsTargetPlanActivation() {
        FileParseUserContext boss = user(10002L, 1, "BOSS", "老板");
        FileParseTargetPlanRow plan = targetPlan(4001L, "commission_ksa", "佣金-KSA", "official_commission");

        when(fileManagementParseMapper.selectUserContext(10002L)).thenReturn(boss);
        when(fileManagementParseMapper.countActiveUserMenu(10002L, LocalDbFileManagementParseService.FILE_MANAGEMENT_MENU_ID))
                .thenReturn(1);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4001L, 1, false)).thenReturn(plan);

        FileParseLogisticsChannelActivationCommand command = new FileParseLogisticsChannelActivationCommand();
        command.setTargetPlanId(4001L);
        command.setVersionId(70001L);
        command.setSelectedChannelKeys(List.of("commission_key"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.saveLogisticsChannelActivations(
                        new AuthenticatedSession(10002L, 2L, 1),
                        command
                )
        );
        verify(fileManagementParseMapper, never()).selectVersion(anyLong());
        verify(fileManagementParseMapper, never()).softDeleteLogisticsChannelActivations(anyLong(), anyLong(), anyLong());
    }

    @Test
    void shouldRejectUnknownLogisticsChannelKey() {
        FileParseUserContext boss = user(10002L, 1, "BOSS", "老板");
        FileParseTargetPlanRow plan = targetPlan(4005L, "logistics_yite", "物流-义特", "logistics_rule");
        FileParseVersionSummaryRow version = versionSummary(70005L, "YITE-AE-FBN-2026-04", "active");

        when(fileManagementParseMapper.selectUserContext(10002L)).thenReturn(boss);
        when(fileManagementParseMapper.countActiveUserMenu(10002L, LocalDbFileManagementParseService.FILE_MANAGEMENT_MENU_ID))
                .thenReturn(1);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4005L, 1, false)).thenReturn(plan);
        when(fileManagementParseMapper.selectVersion(70005L)).thenReturn(version);
        when(fileManagementParseMapper.selectVersionItems(70005L))
                .thenReturn(List.of(logisticsVersionItem(80001L, 70005L, "yite_ae_fbn_sea", "海运")));

        FileParseLogisticsChannelActivationCommand command = new FileParseLogisticsChannelActivationCommand();
        command.setTargetPlanId(4005L);
        command.setVersionId(70005L);
        command.setSelectedChannelKeys(List.of("missing_channel"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.saveLogisticsChannelActivations(
                        new AuthenticatedSession(10002L, 2L, 1),
                        command
                )
        );
        verify(fileManagementParseMapper, never()).softDeleteLogisticsChannelActivations(anyLong(), anyLong(), anyLong());
    }

    @Test
    void shouldRejectRevokedLogisticsVersionActivation() {
        FileParseUserContext boss = user(10002L, 1, "BOSS", "老板");
        FileParseTargetPlanRow plan = targetPlan(4005L, "logistics_yite", "物流-义特", "logistics_rule");
        FileParseVersionSummaryRow version = versionSummary(70005L, "YITE-AE-FBN-2026-04", "revoked");

        when(fileManagementParseMapper.selectUserContext(10002L)).thenReturn(boss);
        when(fileManagementParseMapper.countActiveUserMenu(10002L, LocalDbFileManagementParseService.FILE_MANAGEMENT_MENU_ID))
                .thenReturn(1);
        when(fileManagementParseMapper.selectVisibleTargetPlan(4005L, 1, false)).thenReturn(plan);
        when(fileManagementParseMapper.selectVersion(70005L)).thenReturn(version);
        lenient().when(fileManagementParseMapper.selectVersionItems(70005L))
                .thenReturn(List.of(logisticsVersionItem(80001L, 70005L, "yite_ae_fbn_sea", "海运")));
        lenient().when(fileManagementParseMapper.softDeleteLogisticsChannelActivations(10002L, 4005L, 10002L)).thenReturn(1);
        lenient().when(fileManagementParseMapper.nextLogisticsChannelActivationId()).thenReturn(95001L);
        lenient().when(fileManagementParseMapper.insertLogisticsChannelActivation(
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyLong()
        )).thenReturn(1);
        lenient().when(fileManagementParseMapper.nextAuditLogId()).thenReturn(90004L);
        lenient().when(fileManagementParseMapper.insertAuditLog(
                anyLong(),
                nullable(Long.class),
                anyLong(),
                anyLong(),
                anyString(),
                anyString(),
                nullable(String.class),
                nullable(String.class),
                anyLong()
        )).thenReturn(1);

        FileParseLogisticsChannelActivationCommand command = new FileParseLogisticsChannelActivationCommand();
        command.setTargetPlanId(4005L);
        command.setVersionId(70005L);
        command.setSelectedChannelKeys(List.of("yite_ae_fbn_sea"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.saveLogisticsChannelActivations(
                        new AuthenticatedSession(10002L, 2L, 1),
                        command
                )
        );
    }

    private FileParseUserContext user(Long userId, Integer roleLevel, String roleCode, String roleName) {
        FileParseUserContext user = new FileParseUserContext();
        user.setUserId(userId);
        user.setAccountNo("user-" + userId);
        user.setRealName(roleName);
        user.setRoleCode(roleCode);
        user.setRoleName(roleName);
        user.setRoleLevel(roleLevel);
        user.setStatus(1);
        return user;
    }

    private FileParseTargetPlanRow targetPlan(Long id, String code, String label, String documentType) {
        FileParseTargetPlanRow row = new FileParseTargetPlanRow();
        row.setId(id);
        row.setCode(code);
        row.setLabel(label);
        row.setDocumentType(documentType);
        row.setDocumentName(label + "标准");
        row.setStandardVersionId(5105L);
        row.setStandardVersion("STD-2026.05");
        row.setCurrentVersionId(6105L);
        row.setCurrentVersion(code + "-2026-04");
        return row;
    }

    private FileParseFileAssetRow uploadedAsset(
            Long id,
            Long targetPlanId,
            Long standardVersionId,
            Long uploadedBy,
            String originalFileName,
            String fileExtension
    ) {
        FileParseFileAssetRow row = new FileParseFileAssetRow();
        row.setId(id);
        row.setTargetPlanId(targetPlanId);
        row.setStandardVersionId(standardVersionId);
        row.setOriginalFileName(originalFileName);
        row.setFileExtension(fileExtension);
        row.setUploadedBy(uploadedBy);
        row.setExpiresAt(LocalDateTime.now().plusHours(1));
        return row;
    }

    private FileParseTaskRow task(Long id, Long targetPlanId, String status, int parseAttemptCount) {
        FileParseTaskRow row = new FileParseTaskRow();
        row.setId(id);
        row.setTaskNo("TASK-20260513-" + id);
        row.setDocumentTitle("义特 FBN 报价 2026-05");
        row.setTargetPlanId(targetPlanId);
        row.setStandardVersionId(5105L);
        row.setDataScopeType("global");
        row.setDataScopeKey("global:*");
        row.setStatus(status);
        row.setParseAttemptCount(parseAttemptCount);
        row.setCreatedBy(10001L);
        return row;
    }

    private FileParseTaskInputRow taskInput(
            Long id,
            String inputType,
            String displayName,
            String fileExtension,
            String storageKey,
            String textContent
    ) {
        FileParseTaskInputRow row = new FileParseTaskInputRow();
        row.setId(id);
        row.setTaskId(20001L);
        row.setInputType(inputType);
        row.setInputRole("primary_source");
        row.setDisplayName(displayName);
        row.setFileAssetId(storageKey == null ? null : 10001L);
        row.setOriginalFileName(displayName);
        row.setFileExtension(fileExtension);
        row.setStorageKey(storageKey);
        row.setTextContent(textContent);
        row.setSortNo(id == null ? 0 : id.intValue());
        return row;
    }

    private FileParseStandardVersionRow standardVersion(Long id) {
        FileParseStandardVersionRow row = new FileParseStandardVersionRow();
        row.setId(id);
        row.setStandardId(1003L);
        row.setStandardVersion("STD-2026.05");
        return row;
    }

    private FileParseItemStandardRow logisticsItemStandard() {
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setId(3003L);
        row.setStandardVersionId(5105L);
        row.setItemType("logistics_channel_rule");
        row.setItemLabel("物流渠道规则");
        row.setNaturalKeyJson("{\"fields\":[\"channelKey\",\"country\",\"city\",\"shippingMethod\",\"feeItem\"]}");
        row.setFieldSchemaJson("{\"channelKey\":\"string\",\"country\":\"string\",\"city\":\"string\",\"shippingMethod\":\"string\",\"feeItem\":\"string\",\"billingRule\":\"string\",\"leadTime\":\"string\"}");
        row.setDisplayConfigJson("{\"columns\":[\"channelKey\",\"country\",\"city\",\"shippingMethod\",\"feeItem\",\"billingRule\",\"leadTime\"]}");
        row.setValidationRuleJson("{\"required\":[\"channelKey\",\"shippingMethod\",\"feeItem\",\"billingRule\"]}");
        row.setDiffRuleJson("{\"compareFields\":[\"billingRule\",\"leadTime\"]}");
        row.setSortNo(30);
        return row;
    }

    private List<FileParseItemStandardRow> logisticsQuotePackageItemStandards() {
        List<FileParseItemStandardRow> rows = new ArrayList<>();
        rows.add(logisticsItemStandard());
        long id = 3010L;
        int sortNo = 31;
        for (FileParseLogisticsQuoteStandard.ItemTypeDefinition definition
                : FileParseLogisticsQuoteStandard.structuredItemTypes()) {
            FileParseItemStandardRow row = new FileParseItemStandardRow();
            row.setId(id++);
            row.setStandardVersionId(5105L);
            row.setItemType(definition.getItemType());
            row.setItemLabel(definition.getLabel());
            row.setNaturalKeyJson("{\"fields\":[]}");
            row.setFieldSchemaJson("{}");
            row.setDisplayConfigJson("{}");
            row.setValidationRuleJson("{}");
            row.setDiffRuleJson("{}");
            row.setSortNo(sortNo++);
            rows.add(row);
        }
        return rows;
    }

    private FileParseItemStandardRow commissionItemStandard() {
        FileParseItemStandardRow row = new FileParseItemStandardRow();
        row.setId(3001L);
        row.setStandardVersionId(2001L);
        row.setItemType("commission_rule");
        row.setItemLabel("佣金规则");
        row.setNaturalKeyJson("{\"fields\":[\"country\",\"categoryPath\",\"parentCategoryName\",\"categoryName\",\"brandRestriction\",\"amountMin\",\"amountMinInclusive\",\"amountMax\",\"amountMaxInclusive\",\"amountCurrency\",\"effectiveDate\"]}");
        row.setFieldSchemaJson("{\"country\":\"string\",\"platform\":\"string\",\"fulfillmentType\":\"string\",\"parentCategoryName\":\"string\",\"categoryName\":\"string\",\"categoryPath\":\"string\",\"brandRestriction\":\"string\",\"amountRangeLabel\":\"string\",\"amountMin\":\"decimal\",\"amountMinInclusive\":\"boolean\",\"amountMax\":\"decimal\",\"amountMaxInclusive\":\"boolean\",\"amountCurrency\":\"string\",\"commissionRate\":\"decimal\",\"effectiveDate\":\"date\"}");
        row.setDisplayConfigJson("{\"columns\":[\"country\",\"platform\",\"fulfillmentType\",\"parentCategoryName\",\"categoryName\",\"brandRestriction\",\"amountRangeLabel\",\"amountCurrency\",\"commissionRate\",\"effectiveDate\"]}");
        row.setValidationRuleJson("{\"required\":[\"country\",\"categoryName\",\"amountRangeLabel\",\"amountCurrency\",\"commissionRate\"]}");
        row.setDiffRuleJson("{\"compareFields\":[\"amountRangeLabel\",\"amountMin\",\"amountMinInclusive\",\"amountMax\",\"amountMaxInclusive\",\"amountCurrency\",\"commissionRate\",\"effectiveDate\"]}");
        row.setSortNo(10);
        return row;
    }

    private String commissionPayload(String categoryName, String commissionRate) {
        return "{\"country\":\"KSA\",\"platform\":\"Noon\",\"fulfillmentType\":\"FBN\",\"categoryName\":\""
                + categoryName
                + "\",\"amountRangeLabel\":\"全部\",\"amountCurrency\":\"SAR\",\"commissionRate\":\""
                + commissionRate
                + "\",\"effectiveDate\":\"2025-09-01\"}";
    }

    private FileParseStructuredItem structuredCommissionItem(String payloadJson, List<Long> sourceRowIds) {
        FileParseStructuredItem item = new FileParseStructuredItem();
        item.setItemType("commission_rule");
        item.setNaturalKey("ai-key-" + sourceRowIds);
        item.setNaturalKeyHash(FileParseNaturalKeySupport.naturalKeyHash("commission_rule", item.getNaturalKey()));
        item.setConfidence("medium");
        item.setNormalizedPayloadJson(payloadJson);
        item.setSourceRowIds(sourceRowIds);
        return item;
    }

    private FileParseResultItemRow resultItem(String reviewStatus, String changeType) {
        FileParseResultItemRow row = new FileParseResultItemRow();
        row.setId(50001L);
        row.setResultId(40001L);
        row.setTaskId(20001L);
        row.setTargetPlanId(4005L);
        row.setItemType("logistics_channel_rule");
        row.setNaturalKey("义特-UAE-Dubai-SEA-card + UAE + Dubai + 海运 + 卡牌 / 普货");
        row.setNaturalKeyHash("hash-1");
        row.setChangeType(changeType);
        row.setReviewStatus(reviewStatus);
        row.setConfidence("high");
        row.setValidationStatus("pass");
        row.setNormalizedPayloadJson("{\"channelKey\":\"义特-UAE-Dubai-SEA-card\",\"country\":\"UAE\",\"city\":\"Dubai\",\"shippingMethod\":\"海运\",\"feeItem\":\"卡牌 / 普货\",\"billingRule\":\"26 CNY/KG，最低 12KG\"}");
        row.setOldPayloadJson("{\"channelKey\":\"义特-UAE-Dubai-SEA-card\",\"country\":\"UAE\",\"city\":\"Dubai\",\"shippingMethod\":\"海运\",\"feeItem\":\"卡牌 / 普货\",\"billingRule\":\"24 CNY/KG，最低 10KG\"}");
        row.setChangedFieldKeysJson("[\"billingRule\"]");
        row.setEffectivePayloadJson(row.getNormalizedPayloadJson());
        row.setEffectiveValidationStatus("pass");
        row.setEvidenceJson("{\"source\":\"ai_structured_text\"}");
        row.setSortNo(1);
        return row;
    }

    private FileParseVersionSummaryRow versionSummary(Long id, String versionNo, String status) {
        FileParseVersionSummaryRow row = new FileParseVersionSummaryRow();
        row.setId(id);
        row.setVersionNo(versionNo);
        row.setTargetPlanId(4005L);
        row.setSourceTaskId(20001L);
        row.setSourceResultId(40001L);
        row.setStandardVersionId(5105L);
        row.setBaseVersionId(70004L);
        row.setDataScopeType("global");
        row.setDataScopeKey("global:*");
        row.setVersionStatus(status);
        row.setPublishedAt(LocalDateTime.of(2026, 5, 13, 10, 30));
        row.setPublishedBy(10001L);
        row.setSummaryJson("{\"itemCount\":1}");
        return row;
    }

    private FileParseActiveVersionRow activeVersion(Long targetPlanId, Long versionId, String versionNo) {
        FileParseActiveVersionRow row = new FileParseActiveVersionRow();
        row.setId(71005L);
        row.setTargetPlanId(targetPlanId);
        row.setDataScopeType("global");
        row.setDataScopeKey("global:*");
        row.setVersionId(versionId);
        row.setVersionNo(versionNo);
        return row;
    }

    private FileParseVersionItemRow versionItem(Long id, Long versionId, String naturalKey) {
        FileParseVersionItemRow row = new FileParseVersionItemRow();
        row.setId(id);
        row.setVersionId(versionId);
        row.setTargetPlanId(4005L);
        row.setItemType("logistics_channel_rule");
        row.setNaturalKey(naturalKey);
        row.setNaturalKeyHash("hash-1");
        row.setVersionPayloadJson("{\"channelKey\":\"义特-UAE-Dubai-SEA-card\",\"country\":\"UAE\",\"city\":\"Dubai\",\"shippingMethod\":\"海运\",\"feeItem\":\"卡牌 / 普货\",\"billingRule\":\"24 CNY/KG，最低 10KG\"}");
        row.setSortNo(1);
        return row;
    }

    private FileParseVersionItemRow logisticsVersionItem(Long id, Long versionId, String channelKey, String shippingMethod) {
        FileParseVersionItemRow row = new FileParseVersionItemRow();
        row.setId(id);
        row.setVersionId(versionId);
        row.setTargetPlanId(4005L);
        row.setItemType("logistics_channel_rule");
        row.setNaturalKey("义特 + UAE + Dubai + " + shippingMethod + " + FBN普货");
        row.setNaturalKeyHash("hash-" + channelKey);
        row.setVersionPayloadJson("{\"channelKey\":\"" + channelKey + "\",\"country\":\"UAE\",\"city\":\"Dubai\",\"shippingMethod\":\""
                + shippingMethod + "\",\"feeItem\":\"FBN普货\",\"billingRule\":\"26 CNY/KG，最低 12KG\",\"leadTime\":\"5-7 天\"}");
        row.setSortNo(id == null ? 0 : id.intValue());
        return row;
    }

    private void stubStableProcessStart() {
        when(fileManagementParseMapper.nextSourceRowId()).thenReturn(
                35001L, 35002L, 35003L, 35004L, 35005L,
                35006L, 35007L, 35008L, 35009L, 35010L
        );
        when(fileManagementParseMapper.insertSourceRow(
                anyLong(),
                anyLong(),
                any(FileParseSourceRowDraft.class),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.nextAiChunkId()).thenReturn(36001L);
        when(fileManagementParseMapper.insertAiChunk(
                anyLong(),
                anyLong(),
                anyInt(),
                anyString(),
                anyString(),
                anyInt(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyLong()
        )).thenReturn(1);
    }

    private void stubStableProcessTables(Long resultId, List<FileParseResultItemRow> resultItems) {
        stubStableProcessStart();
        when(fileManagementParseMapper.markAiChunkSucceeded(
                anyLong(),
                anyLong(),
                anyLong(),
                anyInt(),
                anyString(),
                anyString(),
                anyLong()
        )).thenReturn(1);
        when(fileManagementParseMapper.selectResultItems(
                resultId,
                null,
                null,
                10_000,
                0
        )).thenReturn(resultItems);
    }

    private AiStructuredTextResult aiSuccess() {
        AiStructuredTextResult result = AiStructuredTextResult.success();
        result.setProvider("openai");
        result.setModel("test-model");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channelKey", "义特-UAE-Dubai-SEA-card");
        payload.put("country", "UAE");
        payload.put("city", "Dubai");
        payload.put("shippingMethod", "海运");
        payload.put("feeItem", "卡牌 / 普货");
        payload.put("billingRule", "26 CNY/KG，最低 12KG");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemType", "logistics_channel_rule");
        item.put("payload", payload);
        item.put("confidence", "high");
        result.setParsedJson(Map.of("summary", Map.of("source", "test"), "items", List.of(item)));
        return result;
    }

    private AiStructuredTextResult aiSuccessWithLogisticsSourceRowId(Long sourceRowId) {
        AiStructuredTextResult result = AiStructuredTextResult.success();
        result.setProvider("openai");
        result.setModel("test-model");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channelKey", "义特-UAE-Dubai-SEA-card");
        payload.put("country", "UAE");
        payload.put("city", "Dubai");
        payload.put("shippingMethod", "海运");
        payload.put("feeItem", "卡牌 / 普货");
        payload.put("billingRule", "26 CNY/KG，最低 12KG");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemType", "logistics_channel_rule");
        item.put("payload", payload);
        item.put("confidence", "high");
        item.put("sourceRowIds", List.of(sourceRowId));
        result.setParsedJson(Map.of("summary", Map.of("source", "test"), "items", List.of(item)));
        return result;
    }

    private AiStructuredTextResult aiSuccessWithStructuredServiceLine(Long sourceRowId) {
        AiStructuredTextResult result = AiStructuredTextResult.success();
        result.setProvider("openai");
        result.setModel("test-model");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("forwarderName", "ET");
        payload.put("country", "UAE");
        payload.put("fulfillmentMode", "FBN");
        payload.put("destinationNode", "Dubai FBN warehouse");
        payload.put("transportMode", "air");
        payload.put("serviceScope", "warehouse to FBN");
        payload.put("leadTimeText", "5-7 days");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemType", FileParseLogisticsQuoteStandard.SERVICE_LINE);
        item.put("payload", payload);
        item.put("confidence", "high");
        item.put("sourceRowIds", List.of(sourceRowId));
        result.setParsedJson(Map.of("summary", Map.of("source", "test"), "items", List.of(item)));
        return result;
    }

    private AiStructuredTextResult aiSuccessWithLogisticsRule(String channelKey, String shippingMethod, Long sourceRowId) {
        AiStructuredTextResult result = AiStructuredTextResult.success();
        result.setProvider("openai");
        result.setModel("test-model");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channelKey", channelKey);
        payload.put("country", "UAE");
        payload.put("city", "Dubai");
        payload.put("shippingMethod", shippingMethod);
        payload.put("feeItem", "卡牌 / 普货");
        payload.put("billingRule", "26 CNY/KG，最低 12KG");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemType", "logistics_channel_rule");
        item.put("payload", payload);
        item.put("confidence", "high");
        item.put("sourceRowIds", List.of(sourceRowId));
        result.setParsedJson(Map.of("summary", Map.of("source", "test"), "items", List.of(item)));
        return result;
    }

    private AiStructuredTextResult aiSuccessWithCommissionRule(
            String parentCategoryName,
            String categoryName,
            String categoryPath,
            Long sourceRowId
    ) {
        AiStructuredTextResult result = AiStructuredTextResult.success();
        result.setProvider("openai");
        result.setModel("test-model");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("country", "KSA");
        payload.put("platform", "Noon");
        payload.put("fulfillmentType", "FBN");
        payload.put("parentCategoryName", parentCategoryName);
        payload.put("categoryName", categoryName);
        payload.put("categoryPath", categoryPath);
        payload.put("brandRestriction", "全部");
        payload.put("amountRangeLabel", "全部");
        payload.put("amountMin", null);
        payload.put("amountMinInclusive", null);
        payload.put("amountMax", null);
        payload.put("amountMaxInclusive", null);
        payload.put("amountCurrency", "SAR");
        payload.put("commissionRate", "15%");
        payload.put("effectiveDate", "2025-09-01");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemType", "commission_rule");
        item.put("payload", payload);
        item.put("confidence", "high");
        item.put("sourceRowIds", List.of(sourceRowId));
        result.setParsedJson(Map.of("summary", Map.of("source", "test"), "items", List.of(item)));
        return result;
    }

    private String commissionRowsWithSecondChunkRateLine() {
        List<String> rows = new ArrayList<>();
        rows.add("1. Referral Fees");
        rows.add("Referral Fees as a % of Sale price");
        for (int index = 3; index <= 29; index++) {
            rows.add("Referral reference row " + index);
        }
        rows.add("Other Categories");
        rows.add("Sports & Outdoors All 20%");
        return String.join("\n", rows);
    }

    private AiStructuredTextResult aiSuccessWithNoItems() {
        AiStructuredTextResult result = AiStructuredTextResult.success();
        result.setProvider("openai");
        result.setModel("test-model");
        result.setParsedJson(Map.of("items", List.of()));
        return result;
    }

    private AiStructuredTextResult aiSuccessWithUnsplitCommissionTier() {
        AiStructuredTextResult result = AiStructuredTextResult.success();
        result.setProvider("openai");
        result.setModel("test-model");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("country", "KSA");
        payload.put("platform", "Noon");
        payload.put("fulfillmentType", "FBN");
        payload.put("categoryName", "Fashion > Watches");
        payload.put("amountRangeLabel", "all");
        payload.put("amountMin", null);
        payload.put("amountMinInclusive", null);
        payload.put("amountMax", null);
        payload.put("amountMaxInclusive", null);
        payload.put("amountCurrency", "SAR");
        payload.put("commissionRate", "15% up to 5000 SAR, then 5% above 5000 SAR");
        payload.put("effectiveDate", "2025-09-01");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemType", "commission_rule");
        item.put("payload", payload);
        item.put("confidence", "high");
        result.setParsedJson(Map.of("items", List.of(item)));
        return result;
    }

    private AiStructuredTextResult aiSuccessWithBrandRestrictedCommissionRows() {
        AiStructuredTextResult result = AiStructuredTextResult.success();
        result.setProvider("openai");
        result.setModel("test-model");
        Map<String, Object> firstPayload = brandRestrictedCommissionPayload("15%");
        Map<String, Object> secondPayload = brandRestrictedCommissionPayload("10%");
        Map<String, Object> firstItem = new LinkedHashMap<>();
        firstItem.put("itemType", "commission_rule");
        firstItem.put("payload", firstPayload);
        firstItem.put("confidence", "high");
        firstItem.put("sourceRowIds", List.of(92001L));
        Map<String, Object> secondItem = new LinkedHashMap<>();
        secondItem.put("itemType", "commission_rule");
        secondItem.put("payload", secondPayload);
        secondItem.put("confidence", "high");
        secondItem.put("sourceRowIds", List.of(92001L));
        result.setParsedJson(Map.of("items", List.of(firstItem, secondItem)));
        return result;
    }

    private AiStructuredTextResult aiSuccessWithUnsplitBrandRestrictedCommissionRate() {
        AiStructuredTextResult result = AiStructuredTextResult.success();
        result.setProvider("openai");
        result.setModel("test-model");
        Map<String, Object> payload = brandRestrictedCommissionPayload("15% for Generic brand, 10% for all other brands");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemType", "commission_rule");
        item.put("payload", payload);
        item.put("confidence", "high");
        result.setParsedJson(Map.of("items", List.of(item)));
        return result;
    }

    private Map<String, Object> brandRestrictedCommissionPayload(String commissionRate) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("country", "KSA");
        payload.put("platform", "Noon");
        payload.put("fulfillmentType", "FBN");
        payload.put("categoryName", "Colour Cosmetics");
        payload.put("amountRangeLabel", "all");
        payload.put("amountMin", null);
        payload.put("amountMinInclusive", null);
        payload.put("amountMax", null);
        payload.put("amountMaxInclusive", null);
        payload.put("amountCurrency", "SAR");
        payload.put("commissionRate", commissionRate);
        payload.put("effectiveDate", "2025-09-01");
        return payload;
    }

    private AiStructuredTextResult aiSuccessWithDuplicateCommissionRows(boolean conflictingPayload) {
        AiStructuredTextResult result = AiStructuredTextResult.success();
        result.setProvider("openai");
        result.setModel("test-model");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("country", "KSA");
        payload.put("platform", "Noon");
        payload.put("fulfillmentType", "FBN");
        payload.put("categoryName", "Fashion > Apparel, Footwear");
        payload.put("amountRangeLabel", "all");
        payload.put("amountMin", null);
        payload.put("amountMinInclusive", null);
        payload.put("amountMax", null);
        payload.put("amountMaxInclusive", null);
        payload.put("amountCurrency", "SAR");
        payload.put("commissionRate", "27%");
        payload.put("effectiveDate", "2025-09-01");
        Map<String, Object> secondPayload = new LinkedHashMap<>(payload);
        if (conflictingPayload) {
            secondPayload.put("commissionRate", "28%");
        }
        Map<String, Object> firstItem = new LinkedHashMap<>();
        firstItem.put("itemType", "commission_rule");
        firstItem.put("payload", payload);
        firstItem.put("confidence", "high");
        Map<String, Object> secondItem = new LinkedHashMap<>();
        secondItem.put("itemType", "commission_rule");
        secondItem.put("payload", secondPayload);
        secondItem.put("confidence", "high");
        result.setParsedJson(Map.of("items", List.of(firstItem, secondItem)));
        return result;
    }
}
