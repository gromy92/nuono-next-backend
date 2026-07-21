package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.FileManagementParseMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileParseTaskCreationFileBindingTest {

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedAssets")
    void rejectsFileAssetsInStableValidationOrder(
            String scenario,
            Long fileId,
            FileParseFileAssetRow asset,
            boolean policyCalled,
            boolean systemAdmin,
            boolean bindCalled,
            int bindResult,
            Class<? extends RuntimeException> errorType,
            String message
    ) {
        FileParseUserContext user = user(7L, null);
        stubTask();
        if (fileId != null) {
            when(mapper.selectFileAsset(fileId)).thenReturn(asset);
        }
        if (policyCalled) {
            when(actionPolicy.isSystemAdmin(user)).thenReturn(systemAdmin);
        }
        if (bindCalled) {
            when(mapper.bindFileAssetToTask(fileId, 900L, 7L)).thenReturn(bindResult);
        }

        RuntimeException error = assertThrows(
                errorType,
                () -> service.create(user, plan(), null, command(fileId), null)
        );

        assertEquals(message, error.getMessage());
        InOrder order = inOrder(mapper, actionPolicy);
        order.verify(mapper).nextTaskId();
        verifyTaskInsert(order);
        if (fileId != null) {
            order.verify(mapper).selectFileAsset(fileId);
        }
        if (policyCalled) {
            order.verify(actionPolicy).isSystemAdmin(same(user));
        }
        if (bindCalled) {
            order.verify(mapper).bindFileAssetToTask(fileId, 900L, 7L);
        }
        order.verifyNoMoreInteractions();
        verify(mapper, never()).nextTaskInputId();
        verify(uploadArchiveService, never()).downloadUrl(anyLong());
    }

    @Test
    void allowsOwnerToReuseSameTaskAssetWithoutCheckingStandardVersionId() {
        FileParseUserContext user = user(7L, null);
        FileParseFileAssetRow asset = asset(
                50L, 400L, 9999L, 7L, LocalDateTime.now().plusHours(1L), 900L
        );
        stubSuccessfulFile(asset, user, false);

        FileParseTaskDetailView view = service.create(user, plan(), null, command(50L), null);

        verifySuccessfulOrder(asset, user);
        FileParseTaskInputView input = view.getInputItems().get(0);
        assertAll(
                () -> assertEquals(50L, input.getFileAssetId()),
                () -> assertEquals("pdf", input.getInputType()),
                () -> assertEquals("quote.pdf", input.getDisplayName()),
                () -> assertEquals("/files/50", input.getDownloadUrl())
        );
    }

    @Test
    void allowsSystemAdminToBindAnotherUsersAsset() {
        FileParseUserContext admin = user(7L, 0);
        FileParseFileAssetRow asset = asset(
                50L, 400L, 501L, 88L, LocalDateTime.now().plusHours(1L), null
        );
        stubSuccessfulFile(asset, admin, true);

        FileParseTaskDetailView view = service.create(admin, plan(), null, command(50L), null);

        verifySuccessfulOrder(asset, admin);
        assertEquals(50L, view.getInputItems().get(0).getFileAssetId());
    }

    private static Stream<Arguments> rejectedAssets() {
        LocalDateTime future = LocalDateTime.now().plusHours(1L);
        return Stream.of(
                failure("missing file id", null, null, false, false, false, 0,
                        IllegalArgumentException.class, "文件输入项必须先上传文件。"),
                failure("missing archive", 50L, null, false, false, false, 0,
                        IllegalArgumentException.class, "上传文件不存在或已删除。"),
                failure("wrong target plan", 50L, asset(50L, 401L, 501L, 7L, future, null),
                        false, false, false, 0,
                        IllegalArgumentException.class, "上传文件与目标输出方案不匹配。"),
                failure("another ordinary owner", 50L, asset(50L, 400L, 501L, 88L, future, null),
                        true, false, false, 0,
                        FileParseAccessDeniedException.class, "当前账号不能使用该上传文件。"),
                failure("expired before bound check", 50L,
                        asset(50L, 400L, 501L, 7L, LocalDateTime.now().minusHours(1L), 999L),
                        true, false, false, 0,
                        IllegalArgumentException.class, "上传文件已过期，请重新上传。"),
                failure("already bound elsewhere", 50L, asset(50L, 400L, 501L, 7L, future, 999L),
                        true, false, false, 0,
                        IllegalArgumentException.class, "上传文件已被其它解析文档使用。"),
                failure("compare-and-set lost", 50L, asset(50L, 400L, 501L, 7L, future, null),
                        true, false, true, 0,
                        IllegalArgumentException.class, "上传文件已被其它解析文档使用。")
        );
    }

    private void stubTask() {
        when(mapper.nextTaskId()).thenReturn(900L);
        when(mapper.insertTask(
                anyLong(), anyString(), anyString(), anyLong(), anyLong(), anyLong(), anyLong(),
                nullable(Long.class), anyInt(), nullable(String.class), nullable(String.class),
                anyString(), anyLong()
        )).thenReturn(1);
    }

    private void stubSuccessfulFile(
            FileParseFileAssetRow asset,
            FileParseUserContext user,
            boolean systemAdmin
    ) {
        stubTask();
        when(mapper.selectFileAsset(asset.getId())).thenReturn(asset);
        when(actionPolicy.isSystemAdmin(user)).thenReturn(systemAdmin);
        when(mapper.bindFileAssetToTask(asset.getId(), 900L, 7L)).thenReturn(1);
        when(mapper.nextTaskInputId()).thenReturn(1001L);
        when(mapper.insertTaskInput(
                1001L, 900L, "pdf", "primary_source", 50L,
                null, "quote.pdf", 1, 7L
        )).thenReturn(1);
        when(uploadArchiveService.downloadUrl(50L)).thenReturn("/files/50");
    }

    private void verifySuccessfulOrder(FileParseFileAssetRow asset, FileParseUserContext user) {
        InOrder order = inOrder(mapper, actionPolicy, uploadArchiveService);
        order.verify(mapper).nextTaskId();
        verifyTaskInsert(order);
        order.verify(mapper).selectFileAsset(asset.getId());
        order.verify(actionPolicy).isSystemAdmin(same(user));
        order.verify(mapper).bindFileAssetToTask(asset.getId(), 900L, 7L);
        order.verify(mapper).nextTaskInputId();
        order.verify(mapper).insertTaskInput(
                1001L, 900L, "pdf", "primary_source", 50L,
                null, "quote.pdf", 1, 7L
        );
        order.verify(uploadArchiveService).downloadUrl(50L);
        order.verifyNoMoreInteractions();
    }

    private void verifyTaskInsert(InOrder order) {
        order.verify(mapper).insertTask(
                anyLong(), anyString(), anyString(), anyLong(), anyLong(), anyLong(), anyLong(),
                nullable(Long.class), anyInt(), nullable(String.class), nullable(String.class),
                anyString(), anyLong()
        );
    }

    private FileParseCreateTaskCommand command(Long assetId) {
        FileParseTaskInputCommand input = new FileParseTaskInputCommand();
        input.setInputType("file");
        input.setFileAssetId(assetId);
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

    private FileParseUserContext user(Long userId, Integer roleLevel) {
        FileParseUserContext user = new FileParseUserContext();
        user.setUserId(userId);
        user.setRoleLevel(roleLevel);
        return user;
    }

    private static FileParseFileAssetRow asset(
            Long id,
            Long targetPlanId,
            Long standardVersionId,
            Long uploadedBy,
            LocalDateTime expiresAt,
            Long boundTaskId
    ) {
        FileParseFileAssetRow asset = new FileParseFileAssetRow();
        asset.setId(id);
        asset.setTargetPlanId(targetPlanId);
        asset.setStandardVersionId(standardVersionId);
        asset.setUploadedBy(uploadedBy);
        asset.setExpiresAt(expiresAt);
        asset.setBoundTaskId(boundTaskId);
        asset.setOriginalFileName("quote.pdf");
        asset.setFileExtension("pdf");
        return asset;
    }

    private static Arguments failure(
            String scenario,
            Long fileId,
            FileParseFileAssetRow asset,
            boolean policyCalled,
            boolean systemAdmin,
            boolean bindCalled,
            int bindResult,
            Class<? extends RuntimeException> errorType,
            String message
    ) {
        return Arguments.of(
                scenario, fileId, asset, policyCalled, systemAdmin,
                bindCalled, bindResult, errorType, message
        );
    }
}
