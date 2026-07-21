package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.FileManagementParseMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileParseTaskCreationPersistenceFailureTest {

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
    void rejectsTaskInsertBeforeAnyInputSideEffect() {
        when(mapper.nextTaskId()).thenReturn(900L);
        stubTaskInsert(0);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.create(user(), plan(), null, command(50L), null)
        );

        assertEquals("解析文档创建失败。", error.getMessage());
        verify(mapper, never()).selectFileAsset(anyLong());
        verify(mapper, never()).nextTaskInputId();
        verifyNoInteractions(uploadArchiveService, actionPolicy);
    }

    @Test
    void rejectsInputInsertAfterBindingWithoutProjectingDownloadUrl() {
        FileParseUserContext user = user();
        FileParseFileAssetRow asset = asset();
        when(mapper.nextTaskId()).thenReturn(900L);
        stubTaskInsert(1);
        when(mapper.selectFileAsset(50L)).thenReturn(asset);
        when(actionPolicy.isSystemAdmin(user)).thenReturn(false);
        when(mapper.bindFileAssetToTask(50L, 900L, 7L)).thenReturn(1);
        when(mapper.nextTaskInputId()).thenReturn(1001L);
        when(mapper.insertTaskInput(
                1001L, 900L, "pdf", "primary_source", 50L,
                null, "quote.pdf", 1, 7L
        )).thenReturn(0);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.create(user, plan(), null, command(50L), null)
        );

        assertEquals("解析文档输入项创建失败。", error.getMessage());
        InOrder order = inOrder(mapper, actionPolicy);
        order.verify(mapper).nextTaskId();
        verifyTaskInsert(order);
        order.verify(mapper).selectFileAsset(50L);
        order.verify(actionPolicy).isSystemAdmin(user);
        order.verify(mapper).bindFileAssetToTask(50L, 900L, 7L);
        order.verify(mapper).nextTaskInputId();
        order.verify(mapper).insertTaskInput(
                1001L, 900L, "pdf", "primary_source", 50L,
                null, "quote.pdf", 1, 7L
        );
        verify(uploadArchiveService, never()).downloadUrl(anyLong());
    }

    private void stubTaskInsert(int result) {
        when(mapper.insertTask(
                anyLong(), anyString(), anyString(), anyLong(), anyLong(), anyLong(), anyLong(),
                nullable(Long.class), anyInt(), nullable(String.class), nullable(String.class),
                anyString(), anyLong()
        )).thenReturn(result);
    }

    private void verifyTaskInsert(InOrder order) {
        order.verify(mapper).insertTask(
                anyLong(), anyString(), anyString(), anyLong(), anyLong(), anyLong(), anyLong(),
                nullable(Long.class), anyInt(), nullable(String.class), nullable(String.class),
                anyString(), anyLong()
        );
    }

    private FileParseCreateTaskCommand command(Long fileAssetId) {
        FileParseTaskInputCommand input = new FileParseTaskInputCommand();
        input.setInputType("file");
        input.setFileAssetId(fileAssetId);
        FileParseCreateTaskCommand command = new FileParseCreateTaskCommand();
        command.setDocumentTitle("File Task");
        command.setInputItems(List.of(input));
        return command;
    }

    private FileParseTargetPlanRow plan() {
        FileParseTargetPlanRow plan = new FileParseTargetPlanRow();
        plan.setId(400L);
        plan.setStandardVersionId(501L);
        plan.setCurrentVersionId(601L);
        return plan;
    }

    private FileParseUserContext user() {
        FileParseUserContext user = new FileParseUserContext();
        user.setUserId(7L);
        return user;
    }

    private FileParseFileAssetRow asset() {
        FileParseFileAssetRow asset = new FileParseFileAssetRow();
        asset.setId(50L);
        asset.setTargetPlanId(400L);
        asset.setUploadedBy(7L);
        asset.setOriginalFileName("quote.pdf");
        asset.setFileExtension("pdf");
        return asset;
    }
}
