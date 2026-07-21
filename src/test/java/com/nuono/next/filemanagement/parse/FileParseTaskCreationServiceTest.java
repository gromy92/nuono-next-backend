package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.FileManagementParseMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileParseTaskCreationServiceTest {

    @Mock
    private FileManagementParseMapper mapper;
    @Mock
    private FileParseUploadArchiveService uploadArchiveService;
    @Mock
    private FileParseActionPolicy actionPolicy;

    private FileParseTaskCreationService service;

    @BeforeEach
    void setUp() {
        service = new FileParseTaskCreationService(mapper, uploadArchiveService, actionPolicy);
    }

    @Test
    void createsFirstTaskWithStablePersistenceOrderAndResponse() {
        when(mapper.nextTaskId()).thenReturn(900L);
        when(mapper.insertTask(
                eq(900L), org.mockito.ArgumentMatchers.anyString(), eq("Quote Sheet"), eq(400L),
                eq(501L), eq(601L), eq(900L), isNull(), eq(1), eq("first"), eq("idem-1"),
                eq("b2c464c9e0c387ab2c15efb47342e0a9c6c25c91f2b3efda21f4df9a48533afc"), eq(7L)
        )).thenReturn(1);
        when(mapper.nextTaskInputId()).thenReturn(1001L);
        when(mapper.insertTaskInput(
                1001L, 900L, "manual_text", "primary_source", null,
                "Source text", "文本输入", 1, 7L
        )).thenReturn(1);

        FileParseTaskDetailView view = service.create(
                user(7L), plan(), null, command("  Quote Sheet  ", "  first  ", "  Source text  "),
                "  idem-1  "
        );

        ArgumentCaptor<String> taskNo = ArgumentCaptor.forClass(String.class);
        InOrder order = inOrder(mapper);
        order.verify(mapper).nextTaskId();
        order.verify(mapper).insertTask(
                eq(900L), taskNo.capture(), eq("Quote Sheet"), eq(400L), eq(501L), eq(601L),
                eq(900L), isNull(), eq(1), eq("first"), eq("idem-1"),
                eq("b2c464c9e0c387ab2c15efb47342e0a9c6c25c91f2b3efda21f4df9a48533afc"), eq(7L)
        );
        order.verify(mapper).nextTaskInputId();
        order.verify(mapper).insertTaskInput(
                1001L, 900L, "manual_text", "primary_source", null,
                "Source text", "文本输入", 1, 7L
        );
        order.verifyNoMoreInteractions();
        verifyNoInteractions(uploadArchiveService, actionPolicy);

        FileParseTaskInputView input = view.getInputItems().get(0);
        assertAll(
                () -> assertTrue(taskNo.getValue().matches("TASK-\\d{8}-900")),
                () -> assertEquals(900L, view.getId()),
                () -> assertEquals(taskNo.getValue(), view.getTaskNo()),
                () -> assertEquals("Quote Sheet", view.getDocumentTitle()),
                () -> assertEquals(400L, view.getTargetPlanId()),
                () -> assertEquals("QUOTE", view.getTargetPlanCode()),
                () -> assertEquals("报价单", view.getTargetPlanLabel()),
                () -> assertEquals("RATE_SHEET", view.getDocumentType()),
                () -> assertEquals("报价表", view.getDocumentName()),
                () -> assertEquals("STD-5", view.getStandardVersion()),
                () -> assertEquals("CUR-6", view.getCurrentVersion()),
                () -> assertEquals("reading", view.getStatus()),
                () -> assertNull(view.getResultId()),
                () -> assertEquals("global", view.getDataScopeType()),
                () -> assertEquals("global:*", view.getDataScopeKey()),
                () -> assertEquals(900L, view.getDocumentGroupId()),
                () -> assertNull(view.getParentTaskId()),
                () -> assertEquals(1, view.getIterationNo()),
                () -> assertEquals("first", view.getRemark()),
                () -> assertEquals("解析文档已创建，文件和文本输入已完成归档，等待后续 AI 解析执行。", view.getMessage()),
                () -> assertEquals(1, view.getInputItems().size()),
                () -> assertEquals(1001L, input.getId()),
                () -> assertEquals("manual_text", input.getInputType()),
                () -> assertEquals("primary_source", input.getInputRole()),
                () -> assertNull(input.getFileAssetId()),
                () -> assertEquals("文本输入", input.getDisplayName()),
                () -> assertNull(input.getDownloadUrl()),
                () -> assertEquals(1, input.getSortNo())
        );
    }

    @Test
    void createsChildInParentGroupUsingCurrentTargetVersionsAndNextIteration() {
        FileParseTaskRow parent = parent(800L, 700L);
        when(mapper.nextTaskId()).thenReturn(901L);
        when(mapper.selectMaxIterationNo(700L)).thenReturn(3);
        when(mapper.insertTask(
                eq(901L), org.mockito.ArgumentMatchers.anyString(), eq("Updated Quote"), eq(400L),
                eq(501L), eq(601L), eq(700L), eq(800L), eq(4), isNull(), isNull(),
                eq("4f92d9b1786a63eb139b02fd279b5314eaeeceb759bf0237bcbf0cb8dddf417a"), eq(7L)
        )).thenReturn(1);
        when(mapper.nextTaskInputId()).thenReturn(1002L);
        when(mapper.insertTaskInput(
                1002L, 901L, "manual_text", "primary_source", null,
                "changed", "文本输入", 1, 7L
        )).thenReturn(1);

        FileParseTaskDetailView view = service.create(
                user(7L), plan(), parent, command("Updated Quote", null, "changed"), null
        );

        InOrder order = inOrder(mapper);
        order.verify(mapper).nextTaskId();
        order.verify(mapper).selectMaxIterationNo(700L);
        order.verify(mapper).insertTask(
                eq(901L), org.mockito.ArgumentMatchers.argThat(value -> value.matches("TASK-\\d{8}-901")),
                eq("Updated Quote"), eq(400L), eq(501L), eq(601L), eq(700L), eq(800L), eq(4),
                isNull(), isNull(),
                eq("4f92d9b1786a63eb139b02fd279b5314eaeeceb759bf0237bcbf0cb8dddf417a"), eq(7L)
        );
        order.verify(mapper).nextTaskInputId();
        order.verify(mapper).insertTaskInput(
                1002L, 901L, "manual_text", "primary_source", null,
                "changed", "文本输入", 1, 7L
        );
        order.verifyNoMoreInteractions();
        verifyNoInteractions(uploadArchiveService, actionPolicy);
        assertAll(
                () -> assertEquals(700L, view.getDocumentGroupId()),
                () -> assertEquals(800L, view.getParentTaskId()),
                () -> assertEquals(4, view.getIterationNo()),
                () -> assertEquals("STD-5", view.getStandardVersion()),
                () -> assertEquals("CUR-6", view.getCurrentVersion()),
                () -> assertEquals("源文件更新任务已创建，本次解析会基于当前生效版本重新对比。", view.getMessage())
        );
    }

    private FileParseCreateTaskCommand command(String title, String remark, String text) {
        FileParseTaskInputCommand input = new FileParseTaskInputCommand();
        input.setTextContent(text);
        FileParseCreateTaskCommand command = new FileParseCreateTaskCommand();
        command.setDocumentTitle(title);
        command.setRemark(remark);
        command.setInputItems(List.of(input));
        return command;
    }

    private FileParseTargetPlanRow plan() {
        FileParseTargetPlanRow plan = new FileParseTargetPlanRow();
        plan.setId(400L);
        plan.setCode("QUOTE");
        plan.setLabel("报价单");
        plan.setDocumentType("RATE_SHEET");
        plan.setDocumentName("报价表");
        plan.setStandardVersionId(501L);
        plan.setCurrentVersionId(601L);
        plan.setStandardVersion("STD-5");
        plan.setCurrentVersion("CUR-6");
        return plan;
    }

    private FileParseUserContext user(Long userId) {
        FileParseUserContext user = new FileParseUserContext();
        user.setUserId(userId);
        return user;
    }

    private FileParseTaskRow parent(Long id, Long groupId) {
        FileParseTaskRow parent = new FileParseTaskRow();
        parent.setId(id);
        parent.setDocumentGroupId(groupId);
        return parent;
    }
}
