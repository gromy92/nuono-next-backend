package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.FileManagementParseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileParseTaskCatalogTaskTest {

    @Mock
    private FileManagementParseMapper mapper;

    @Mock
    private FileParseUploadArchiveService uploadArchiveService;

    private FileParseTaskCatalogService catalogService;

    @BeforeEach
    void setUp() {
        catalogService = new FileParseTaskCatalogService(
                mapper,
                uploadArchiveService,
                new FileParseActionPolicy()
        );
    }

    @Test
    void normalizesFiltersCapsPaginationAndBatchEnrichesInputs() {
        FileParseUserContext boss = user(10002L, 1, "BOSS", "老板");
        FileParseTaskListItemView item = taskListItem();
        FileParseTaskInputRow fileInput = input(30001L, 20001L, "excel", 90001L, "报价.xlsx", 1);
        FileParseTaskInputRow textInput = input(30002L, 20001L, "manual_text", null, "补充说明", 2);
        when(mapper.countTasks("quote", 4005L, "done", 1, false)).thenReturn(1);
        when(mapper.selectTasks("quote", 4005L, "done", 1, false, 100, 100))
                .thenReturn(List.of(item));
        when(mapper.selectTaskInputsByTaskIds(List.of(20001L)))
                .thenReturn(List.of(fileInput, textInput));
        when(uploadArchiveService.downloadUrl(90001L))
                .thenReturn("/api/file-management/parse/files/90001/download");

        FileParseTaskListView view = catalogService.listTasks(
                boss,
                " quote ",
                4005L,
                " done ",
                2,
                500
        );

        assertEquals(1, view.getTotal());
        assertEquals(2, view.getPage());
        assertEquals(100, view.getPageSize());
        assertSame(item, view.getItems().get(0));
        assertTrue(item.getAvailableActions().isCanCreateTask());
        assertTrue(item.getAvailableActions().isCanProcess());
        assertTrue(item.getAvailableActions().isCanActivateLogisticsChannels());
        assertFalse(item.getAvailableActions().isCanPublish());
        assertEquals(2, item.getInputItems().size());
        assertEquals("报价.xlsx", item.getInputItems().get(0).getDisplayName());
        assertEquals(
                "/api/file-management/parse/files/90001/download",
                item.getInputItems().get(0).getDownloadUrl()
        );
        assertNull(item.getInputItems().get(1).getDownloadUrl());
        InOrder order = inOrder(mapper);
        order.verify(mapper).countTasks("quote", 4005L, "done", 1, false);
        order.verify(mapper).selectTasks("quote", 4005L, "done", 1, false, 100, 100);
        order.verify(mapper).selectTaskInputsByTaskIds(List.of(20001L));
    }

    @Test
    void defaultsInvalidPaginationAndSkipsInputQueryForEmptyPage() {
        FileParseUserContext operator = user(10004L, 3, "OPS", "运营");
        when(mapper.countTasks(null, null, null, 3, false)).thenReturn(0);
        when(mapper.selectTasks(null, null, null, 3, false, 20, 0)).thenReturn(List.of());

        FileParseTaskListView view = catalogService.listTasks(
                operator,
                " ",
                null,
                "",
                0,
                0
        );

        assertEquals(0, view.getTotal());
        assertEquals(1, view.getPage());
        assertEquals(20, view.getPageSize());
        assertTrue(view.getItems().isEmpty());
        verify(mapper, never()).selectTaskInputsByTaskIds(anyList());
    }

    @Test
    void mapsTaskDetailInputsAndLineageDefaults() {
        FileParseTaskRow task = task();
        FileParseTargetPlanRow plan = targetPlan();
        FileParseTaskInputRow fileInput = input(30001L, 20001L, "excel", 90001L, "报价.xlsx", 1);
        FileParseTaskInputRow textInput = input(30002L, 20001L, "manual_text", null, "说明", 2);
        when(mapper.selectTaskInputs(20001L)).thenReturn(List.of(fileInput, textInput));
        when(uploadArchiveService.downloadUrl(90001L)).thenReturn("/download/90001");

        FileParseTaskDetailView view = catalogService.getTask(task, plan);

        assertEquals(20001L, view.getId());
        assertEquals("TASK-20001", view.getTaskNo());
        assertEquals("物流报价 2026-07", view.getDocumentTitle());
        assertEquals(4005L, view.getTargetPlanId());
        assertEquals("logistics_yite", view.getTargetPlanCode());
        assertEquals("物流-义特", view.getTargetPlanLabel());
        assertEquals("logistics_rule", view.getDocumentType());
        assertEquals("物流报价规则", view.getDocumentName());
        assertEquals("STD-2026-05", view.getStandardVersion());
        assertEquals("V2026.05", view.getCurrentVersion());
        assertEquals("failed", view.getStatus());
        assertEquals(70001L, view.getResultId());
        assertEquals("AI_TIMEOUT", view.getFailureCode());
        assertEquals("解析超时", view.getFailureMessage());
        assertEquals("解析超时", view.getMessage());
        assertEquals(LocalDateTime.of(2026, 7, 20, 10, 0), view.getNextRunAt());
        assertEquals("global", view.getDataScopeType());
        assertEquals("global:*", view.getDataScopeKey());
        assertEquals(20001L, view.getDocumentGroupId());
        assertEquals(19999L, view.getParentTaskId());
        assertEquals(1, view.getIterationNo());
        assertEquals(2, view.getInputItems().size());
        assertEquals(90001L, view.getInputItems().get(0).getFileAssetId());
        assertEquals("primary_source", view.getInputItems().get(0).getInputRole());
        assertEquals("/download/90001", view.getInputItems().get(0).getDownloadUrl());
        assertNull(view.getInputItems().get(1).getDownloadUrl());
        verify(mapper).selectTaskInputs(20001L);
    }

    private FileParseUserContext user(Long id, Integer level, String code, String name) {
        FileParseUserContext user = new FileParseUserContext();
        user.setUserId(id);
        user.setRoleLevel(level);
        user.setRoleCode(code);
        user.setRoleName(name);
        return user;
    }

    private FileParseTaskListItemView taskListItem() {
        FileParseTaskListItemView item = new FileParseTaskListItemView();
        item.setId(20001L);
        item.setTargetPlanId(4005L);
        item.setTargetPlanCode("logistics_yite");
        item.setTargetPlanLabel("物流-义特");
        item.setDocumentType("logistics_rule");
        item.setDocumentName("物流报价规则");
        item.setStandardVersion("STD-2026-05");
        item.setCurrentVersion("V2026.05");
        return item;
    }

    private FileParseTaskRow task() {
        FileParseTaskRow task = new FileParseTaskRow();
        task.setId(20001L);
        task.setTaskNo("TASK-20001");
        task.setDocumentTitle("物流报价 2026-07");
        task.setTargetPlanId(4005L);
        task.setStatus("failed");
        task.setCurrentResultId(70001L);
        task.setFailureCode("AI_TIMEOUT");
        task.setFailureMessage("解析超时");
        task.setNextRunAt(LocalDateTime.of(2026, 7, 20, 10, 0));
        task.setDataScopeType("global");
        task.setDataScopeKey("global:*");
        task.setParentTaskId(19999L);
        return task;
    }

    private FileParseTargetPlanRow targetPlan() {
        FileParseTargetPlanRow plan = new FileParseTargetPlanRow();
        plan.setId(4005L);
        plan.setCode("logistics_yite");
        plan.setLabel("物流-义特");
        plan.setDocumentType("logistics_rule");
        plan.setDocumentName("物流报价规则");
        plan.setStandardVersion("STD-2026-05");
        plan.setCurrentVersion("V2026.05");
        return plan;
    }

    private FileParseTaskInputRow input(
            Long id,
            Long taskId,
            String type,
            Long fileAssetId,
            String displayName,
            int sortNo
    ) {
        FileParseTaskInputRow input = new FileParseTaskInputRow();
        input.setId(id);
        input.setTaskId(taskId);
        input.setInputType(type);
        input.setInputRole("primary_source");
        input.setFileAssetId(fileAssetId);
        input.setDisplayName(displayName);
        input.setSortNo(sortNo);
        return input;
    }
}
