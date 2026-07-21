package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.FileManagementParseMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class FileParseUploadArchiveServiceTest {

    @Mock
    private FileManagementParseMapper mapper;

    @TempDir
    private Path tempDir;

    private FileParseStorageProperties storageProperties;
    private FileParseUploadArchiveService service;

    @BeforeEach
    void setUp() {
        storageProperties = new FileParseStorageProperties();
        storageProperties.setRootDir(tempDir);
        storageProperties.setUploadExpiresHours(2L);
        service = new FileParseUploadArchiveService(mapper, storageProperties);
    }

    @Test
    void archivesFileWithStableMetadataAndDownloadContract() throws Exception {
        when(mapper.nextFileAssetId()).thenReturn(10001L);
        when(mapper.insertFileAsset(any(FileParseFileAssetRow.class))).thenReturn(1);
        MockMultipartFile file = file("../nested/quote\t.XLSX", " application/vnd.ms-excel ", "quote data");
        LocalDateTime before = LocalDateTime.now();

        FileParseUploadView view = service.archive(targetPlan(), 10001L, file);

        LocalDateTime after = LocalDateTime.now();
        ArgumentCaptor<FileParseFileAssetRow> captor = ArgumentCaptor.forClass(FileParseFileAssetRow.class);
        verify(mapper).insertFileAsset(captor.capture());
        FileParseFileAssetRow row = captor.getValue();
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        assertEquals(10001L, row.getId());
        assertEquals("UP-" + date + "-10001", row.getUploadId());
        assertEquals(4005L, row.getTargetPlanId());
        assertEquals(5105L, row.getStandardVersionId());
        assertEquals("quote_.XLSX", row.getOriginalFileName());
        assertEquals("application/vnd.ms-excel", row.getContentType());
        assertEquals("xlsx", row.getFileExtension());
        assertEquals(10L, row.getFileSizeBytes());
        assertEquals("c3d42f55c0402860fb2d6ca78685ecb583188582e0053f60db198384437be806", row.getSha256Hash());
        assertEquals("local-file-management-parse", row.getStorageBucket());
        assertEquals(10001L, row.getUploadedBy());
        assertTrue(row.getStorageKey().startsWith(date + "/10001-"));
        assertTrue(row.getStorageKey().endsWith("-quote_.XLSX"));
        assertFalse(row.getExpiresAt().isBefore(before.plusHours(2L)));
        assertFalse(row.getExpiresAt().isAfter(after.plusHours(2L)));
        assertEquals("quote data", Files.readString(tempDir.resolve(row.getStorageKey())));
        assertEquals(row.getId(), view.getFileId());
        assertEquals(row.getUploadId(), view.getUploadId());
        assertEquals(row.getTargetPlanId(), view.getTargetPlanId());
        assertEquals(row.getStandardVersionId(), view.getStandardVersionId());
        assertEquals(row.getOriginalFileName(), view.getOriginalFileName());
        assertEquals(row.getContentType(), view.getContentType());
        assertEquals(row.getFileExtension(), view.getFileExtension());
        assertEquals(row.getFileSizeBytes(), view.getSizeBytes());
        assertEquals(row.getSha256Hash(), view.getSha256Hash());
        assertEquals("/api/file-management/parse/files/10001/download", view.getDownloadUrl());
    }

    @Test
    void deletesPhysicalArchiveWhenAssetInsertFails() throws Exception {
        when(mapper.nextFileAssetId()).thenReturn(10002L);
        when(mapper.insertFileAsset(any(FileParseFileAssetRow.class))).thenReturn(0);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.archive(targetPlan(), 10001L, file("quote.xlsx", "", "quote"))
        );

        ArgumentCaptor<FileParseFileAssetRow> captor = ArgumentCaptor.forClass(FileParseFileAssetRow.class);
        verify(mapper).insertFileAsset(captor.capture());
        assertEquals("文件归档记录写入失败。", error.getMessage());
        assertFalse(Files.exists(tempDir.resolve(captor.getValue().getStorageKey())));
    }

    @Test
    void deletesPartialArchiveWhenCopyFails() throws Exception {
        when(mapper.nextFileAssetId()).thenReturn(10003L);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.archive(targetPlan(), 10001L, failingFile())
        );

        assertEquals("文件归档失败。", error.getMessage());
        try (var paths = Files.walk(tempDir)) {
            assertTrue(paths.noneMatch(Files::isRegularFile));
        }
        verify(mapper, never()).insertFileAsset(any(FileParseFileAssetRow.class));
    }

    @Test
    void rejectsInvalidUploadsBeforeAllocatingStorageIdentity() {
        storageProperties.setMaxFileBytes(3L);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.archive(targetPlan(), 10001L, file("quote.xlsx", "", "four"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.archive(targetPlan(), 10001L, file("quote.exe", "", "x"))
        );
        verify(mapper, never()).nextFileAssetId();
    }

    @Test
    void resolvesArchiveForItsOwner() throws Exception {
        FileParseFileAssetRow asset = asset("archive/quote.xlsx", 10001L);
        Path archive = writeArchive(asset.getStorageKey());
        when(mapper.selectFileAsset(10001L)).thenReturn(asset);

        FileParseArchivedFile resolved = service.resolve(10001L, 10001L, false);

        assertEquals(archive.toAbsolutePath().normalize(), resolved.getPath());
        assertEquals("quote.xlsx", resolved.getFileName());
        assertEquals("application/vnd.ms-excel", resolved.getContentType());
    }

    @Test
    void allowsSystemAdminAcrossOwnersButRejectsAnotherOrdinaryUser() throws Exception {
        FileParseFileAssetRow asset = asset("archive/quote.xlsx", 10001L);
        writeArchive(asset.getStorageKey());
        when(mapper.selectFileAsset(10001L)).thenReturn(asset);

        assertThrows(
                FileParseAccessDeniedException.class,
                () -> service.resolve(10001L, 10002L, false)
        );
        assertEquals(
                "quote.xlsx",
                service.resolve(10001L, 10002L, true).getFileName()
        );
    }

    @Test
    void rejectsExpiredUnboundAssetButAllowsExpiredBoundAsset() throws Exception {
        FileParseFileAssetRow asset = asset("archive/quote.xlsx", 10001L);
        asset.setExpiresAt(LocalDateTime.now().minusMinutes(1L));
        when(mapper.selectFileAsset(10001L)).thenReturn(asset);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.resolve(10001L, 10001L, false)
        );

        asset.setBoundTaskId(20001L);
        writeArchive(asset.getStorageKey());
        assertEquals("quote.xlsx", service.resolve(10001L, 10001L, false).getFileName());
    }

    @Test
    void rejectsEscapingPathAndReportsMissingArchive() {
        FileParseFileAssetRow asset = asset("../escape.xlsx", 10001L);
        when(mapper.selectFileAsset(10001L)).thenReturn(asset);

        IllegalArgumentException unsafePath = assertThrows(
                IllegalArgumentException.class,
                () -> service.resolve(10001L, 10001L, false)
        );
        assertEquals("文件路径不合法。", unsafePath.getMessage());

        asset.setStorageKey("archive/missing.xlsx");
        IllegalStateException missing = assertThrows(
                IllegalStateException.class,
                () -> service.resolve(10001L, 10001L, false)
        );
        assertEquals("归档文件不存在。", missing.getMessage());
    }

    private MockMultipartFile file(String name, String contentType, String content) {
        return new MockMultipartFile("file", name, contentType, content.getBytes());
    }

    private MockMultipartFile failingFile() {
        return new MockMultipartFile("file", "quote.xlsx", "application/vnd.ms-excel", new byte[] {1}) {
            @Override
            public InputStream getInputStream() {
                return new InputStream() {
                    private boolean first = true;

                    @Override
                    public int read() throws IOException {
                        if (first) {
                            first = false;
                            return 'x';
                        }
                        throw new IOException("copy failed");
                    }
                };
            }
        };
    }

    private FileParseTargetPlanRow targetPlan() {
        FileParseTargetPlanRow row = new FileParseTargetPlanRow();
        row.setId(4005L);
        row.setStandardVersionId(5105L);
        return row;
    }

    private FileParseFileAssetRow asset(String storageKey, Long uploadedBy) {
        FileParseFileAssetRow row = new FileParseFileAssetRow();
        row.setId(10001L);
        row.setOriginalFileName("quote.xlsx");
        row.setContentType("application/vnd.ms-excel");
        row.setStorageKey(storageKey);
        row.setUploadedBy(uploadedBy);
        row.setExpiresAt(LocalDateTime.now().plusHours(1L));
        return row;
    }

    private Path writeArchive(String storageKey) throws Exception {
        Path path = tempDir.resolve(storageKey);
        Files.createDirectories(path.getParent());
        return Files.writeString(path, "quote");
    }
}
