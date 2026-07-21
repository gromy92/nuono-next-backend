package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.FileManagementParseMapper;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileParseTaskCreationInputNormalizationTest {

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
    void normalizesTypeRoleDisplaySortAndTextBeforePersistingMixedInputs() {
        FileParseUserContext user = user();
        stubTask(900L);
        when(mapper.nextTaskInputId()).thenReturn(1001L, 1002L, 1003L, 1004L);
        when(mapper.insertTaskInput(
                anyLong(), anyLong(), anyString(), anyString(), nullable(Long.class),
                nullable(String.class), anyString(), anyInt(), anyLong()
        )).thenReturn(1);
        when(mapper.selectFileAsset(50L)).thenReturn(asset(50L, "source.xlsx"));
        when(mapper.selectFileAsset(51L)).thenReturn(asset(51L, "fixed.xlsx"));
        when(mapper.bindFileAssetToTask(anyLong(), eq(900L), eq(7L))).thenReturn(1);
        when(uploadArchiveService.downloadUrl(50L)).thenReturn("/files/50");
        when(uploadArchiveService.downloadUrl(51L)).thenReturn("/files/51");

        FileParseTaskDetailView view = service.create(
                user, plan(), null, command(List.of(
                        fileInput(50L, " FILE ", " Reference ", " file note ", " workbook ", -2),
                        textInput(" ", " ", " base text ", " ", null),
                        textInput(" OCR_TEXT ", " Supplement ", " OCR output ", " OCR page ", 9),
                        fileInput(51L, " PDF ", " Parsed_File ", null, null, null)
                )), null
        );

        InOrder order = inOrder(mapper, actionPolicy, uploadArchiveService);
        order.verify(mapper).nextTaskId();
        order.verify(mapper).insertTask(
                eq(900L), anyString(), eq("Normalize"), eq(400L), eq(501L), eq(601L),
                eq(900L), nullable(Long.class), eq(1), nullable(String.class), nullable(String.class),
                eq("381a7a2684fdb8a5eab5e12e9d251e2fd6fb1046b4bfc99eb3199bbace995efd"), eq(7L)
        );
        order.verify(mapper).selectFileAsset(50L);
        order.verify(actionPolicy).isSystemAdmin(same(user));
        order.verify(mapper).bindFileAssetToTask(50L, 900L, 7L);
        order.verify(mapper).nextTaskInputId();
        order.verify(mapper).insertTaskInput(
                1001L, 900L, "excel", "reference", 50L,
                "file note", "workbook", 0, 7L
        );
        order.verify(uploadArchiveService).downloadUrl(50L);
        order.verify(mapper).nextTaskInputId();
        order.verify(mapper).insertTaskInput(
                1002L, 900L, "manual_text", "primary_source", null,
                "base text", "文本输入", 2, 7L
        );
        order.verify(mapper).nextTaskInputId();
        order.verify(mapper).insertTaskInput(
                1003L, 900L, "ocr_text", "supplement", null,
                "OCR output", "OCR page", 9, 7L
        );
        order.verify(mapper).selectFileAsset(51L);
        order.verify(actionPolicy).isSystemAdmin(same(user));
        order.verify(mapper).bindFileAssetToTask(51L, 900L, 7L);
        order.verify(mapper).nextTaskInputId();
        order.verify(mapper).insertTaskInput(
                1004L, 900L, "pdf", "parsed_file", 51L,
                null, "fixed.xlsx", 4, 7L
        );
        order.verify(uploadArchiveService).downloadUrl(51L);
        order.verifyNoMoreInteractions();

        assertAll(
                () -> assertEquals(List.of("excel", "manual_text", "ocr_text", "pdf"),
                        view.getInputItems().stream()
                                .map(FileParseTaskInputView::getInputType).collect(Collectors.toList())),
                () -> assertEquals(List.of("reference", "primary_source", "supplement", "parsed_file"),
                        view.getInputItems().stream()
                                .map(FileParseTaskInputView::getInputRole).collect(Collectors.toList())),
                () -> assertEquals("/files/50", view.getInputItems().get(0).getDownloadUrl()),
                () -> assertNull(view.getInputItems().get(1).getDownloadUrl()),
                () -> assertEquals("/files/51", view.getInputItems().get(3).getDownloadUrl())
        );
    }

    @ParameterizedTest(name = "{0} maps to {1}")
    @CsvSource({
            "pdf, pdf", "xls, excel", "csv, excel", "png, image",
            "jpg, image", "jpeg, image", "webp, image", "txt, file"
    })
    void infersGenericFileTypeFromArchivedExtension(String extension, String expectedType) {
        stubTask(900L);
        when(mapper.selectFileAsset(50L)).thenReturn(asset(50L, "source." + extension));
        when(mapper.bindFileAssetToTask(50L, 900L, 7L)).thenReturn(1);
        when(mapper.nextTaskInputId()).thenReturn(1001L);
        when(mapper.insertTaskInput(
                1001L, 900L, expectedType, "primary_source", 50L,
                null, "source." + extension, 1, 7L
        )).thenReturn(1);

        service.create(
                user(), plan(), null,
                command(List.of(fileInput(50L, "file", null, null, null, null))), null
        );

        verify(mapper).insertTaskInput(
                1001L, 900L, expectedType, "primary_source", 50L,
                null, "source." + extension, 1, 7L
        );
    }

    @Test
    void fallsBackToGenericFileWhenArchivedExtensionIsMissing() {
        stubTask(900L);
        FileParseFileAssetRow asset = asset(50L, "source");
        asset.setFileExtension(null);
        when(mapper.selectFileAsset(50L)).thenReturn(asset);
        when(mapper.bindFileAssetToTask(50L, 900L, 7L)).thenReturn(1);
        when(mapper.nextTaskInputId()).thenReturn(1001L);
        when(mapper.insertTaskInput(
                1001L, 900L, "file", "primary_source", 50L,
                null, "source", 1, 7L
        )).thenReturn(1);

        service.create(
                user(), plan(), null,
                command(List.of(fileInput(50L, "file", null, null, null, null))), null
        );

        verify(mapper).insertTaskInput(
                1001L, 900L, "file", "primary_source", 50L,
                null, "source", 1, 7L
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidInputs")
    void rejectsInvalidInputBeforeAllocatingInputIdentity(
            String scenario,
            FileParseTaskInputCommand input,
            String expectedMessage
    ) {
        stubTask(900L);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(
                        user(), plan(), null,
                        command(input == null ? Collections.singletonList(null) : List.of(input)), null
                )
        );

        assertEquals(expectedMessage, error.getMessage());
        verify(mapper, never()).nextTaskInputId();
        verify(mapper, never()).bindFileAssetToTask(anyLong(), anyLong(), anyLong());
    }

    private static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of("null item", null, "输入项不能为空。"),
                Arguments.of("unsupported type", textInput("archive", null, "x", null, null),
                        "不支持的输入类型：archive"),
                Arguments.of("unsupported role", textInput("manual_text", "owner", "x", null, null),
                        "不支持的输入角色：owner"),
                Arguments.of("blank text", textInput("manual_text", null, " ", null, null),
                        "文本输入内容不能为空。")
        );
    }

    private void stubTask(Long taskId) {
        when(mapper.nextTaskId()).thenReturn(taskId);
        when(mapper.insertTask(
                anyLong(), anyString(), anyString(), anyLong(), anyLong(), anyLong(), anyLong(),
                nullable(Long.class), anyInt(), nullable(String.class), nullable(String.class),
                anyString(), anyLong()
        )).thenReturn(1);
    }

    private FileParseCreateTaskCommand command(List<FileParseTaskInputCommand> inputs) {
        FileParseCreateTaskCommand command = new FileParseCreateTaskCommand();
        command.setDocumentTitle("Normalize");
        command.setInputItems(inputs);
        return command;
    }

    private static FileParseTaskInputCommand textInput(
            String type, String role, String text, String display, Integer sort
    ) {
        FileParseTaskInputCommand input = new FileParseTaskInputCommand();
        input.setInputType(type);
        input.setInputRole(role);
        input.setTextContent(text);
        input.setDisplayName(display);
        input.setSortNo(sort);
        return input;
    }

    private static FileParseTaskInputCommand fileInput(
            Long assetId, String type, String role, String text, String display, Integer sort
    ) {
        FileParseTaskInputCommand input = textInput(type, role, text, display, sort);
        input.setFileAssetId(assetId);
        return input;
    }

    private FileParseFileAssetRow asset(Long id, String fileName) {
        FileParseFileAssetRow asset = new FileParseFileAssetRow();
        asset.setId(id);
        asset.setTargetPlanId(400L);
        asset.setStandardVersionId(999L);
        asset.setOriginalFileName(fileName);
        asset.setFileExtension(fileName.substring(fileName.lastIndexOf('.') + 1));
        asset.setUploadedBy(7L);
        return asset;
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
}
