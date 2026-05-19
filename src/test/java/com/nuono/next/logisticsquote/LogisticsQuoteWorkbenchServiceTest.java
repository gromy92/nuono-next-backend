package com.nuono.next.logisticsquote;

import com.nuono.next.infrastructure.mapper.LogisticsQuoteMapper;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.BundleDetailView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.BundleListItemView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.EvidenceView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.ForwarderView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.QuoteVersionView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.RestrictionView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.RuleView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.ServiceView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.SourceFileView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.SourceNoteView;
import com.nuono.next.system.CoreTableInspection;
import com.nuono.next.system.LocalDbBootstrapStatusService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockMultipartFile;

class LogisticsQuoteWorkbenchServiceTest {

    @Test
    void shouldPreferStoredSourceBundleWorkbenchOverSampleDataWhenLocalDbIsReady() {
        InMemoryLogisticsQuoteMapper mapper = new InMemoryLogisticsQuoteMapper();
        LogisticsQuoteWorkbenchService service = new LogisticsQuoteWorkbenchService(
                new LogisticsQuoteNoteInterpreter(),
                provider(mapper, LogisticsQuoteMapper.class),
                provider(localDbReadyService(), LocalDbBootstrapStatusService.class)
        );

        LogisticsQuoteSourceBundleCreateCommand createCommand = new LogisticsQuoteSourceBundleCreateCommand();
        createCommand.setForwarderName("起客");
        createCommand.setBundleName("起客-沙特空派-本地入库包");
        createCommand.setAnalysisSummary("首条来源包已落到本地库。");

        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput file = new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        file.setFileName("起客报价表.xlsx");
        file.setFilePath("/quotes/qike.xlsx");
        createCommand.setFiles(List.of(file));

        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput note = new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        note.setNoteType("manual_note");
        note.setSourceChannel("wechat");
        note.setContent("低于10方不接报关");
        createCommand.setNotes(List.of(note));

        LogisticsQuoteWorkbenchView created = service.createSourceBundle(createCommand);
        LogisticsQuoteWorkbenchView workbench = service.buildWorkbench(created.getSelectedBundleId(), null, null);

        Assertions.assertEquals("local-db", workbench.getMode());
        Assertions.assertEquals(1, workbench.getBundles().size());
        Assertions.assertEquals(created.getSelectedBundleId(), workbench.getSelectedBundleId());
        Assertions.assertEquals("起客-沙特空派-本地入库包", workbench.getSelectedBundle().getBundleName());
        Assertions.assertEquals("起客", workbench.getSelectedBundle().getForwarder().getName());
        Assertions.assertEquals("未生成", workbench.getSelectedBundle().getQuoteVersion().getVersionNo());
        Assertions.assertEquals("SOURCE_ONLY", workbench.getSelectedBundle().getQuoteVersion().getStatus());
        Assertions.assertTrue(workbench.getSelectedBundle().getSourceReadbackHint().contains("当前详情已从本地库真实回读"));
        Assertions.assertTrue(workbench.getMessage().contains("优先展示本地库里已保存的来源包元数据"));
    }

    @Test
    void shouldAppendStoredSourceBundleFileAndReadBackLatestMetadata() {
        InMemoryLogisticsQuoteMapper mapper = new InMemoryLogisticsQuoteMapper();
        LogisticsQuoteWorkbenchService service = new LogisticsQuoteWorkbenchService(
                new LogisticsQuoteNoteInterpreter(),
                provider(mapper, LogisticsQuoteMapper.class),
                provider(localDbReadyService(), LocalDbBootstrapStatusService.class)
        );

        LogisticsQuoteSourceBundleCreateCommand createCommand = new LogisticsQuoteSourceBundleCreateCommand();
        createCommand.setForwarderName("义特");
        createCommand.setBundleName("义特-沙特海运双清-2026-04");

        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput file = new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        file.setFileName("义特报价表.xlsx");
        createCommand.setFiles(List.of(file));

        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput note = new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        note.setContent("单品需要分别加240/方");
        createCommand.setNotes(List.of(note));

        LogisticsQuoteWorkbenchView created = service.createSourceBundle(createCommand);
        Long bundleId = created.getSelectedBundleId();

        LogisticsQuoteSourceBundleFileCreateCommand appendCommand = new LogisticsQuoteSourceBundleFileCreateCommand();
        appendCommand.setFileName("义特补充说明.pdf");
        appendCommand.setFilePath("/quotes/yi-te-appendix.pdf");

        LogisticsQuoteWorkbenchView updated = service.appendSourceBundleFile(bundleId, null, appendCommand);

        Assertions.assertEquals("local-db", updated.getMode());
        Assertions.assertTrue(updated.getMessage().contains("来源文件元数据已追加"));
        Assertions.assertEquals(bundleId, updated.getSelectedBundleId());
        Assertions.assertEquals(2, updated.getSelectedBundle().getFiles().size());
        Assertions.assertEquals("义特补充说明.pdf", updated.getSelectedBundle().getFiles().get(1).getFileName());
        Assertions.assertEquals("pdf", updated.getSelectedBundle().getFiles().get(1).getFileType());
        Assertions.assertEquals("/quotes/yi-te-appendix.pdf", updated.getSelectedBundle().getFiles().get(1).getSourceLabel());
        Assertions.assertTrue(updated.getSelectedBundle().getSourceReadbackHint().contains("来源文件元数据 2 条"));
    }

    @Test
    void shouldArchiveSourceBundleFileAndExposeDownloadMetadata() {
        InMemoryLogisticsQuoteMapper mapper = new InMemoryLogisticsQuoteMapper();
        LogisticsQuoteWorkbenchService service = new LogisticsQuoteWorkbenchService(
                new LogisticsQuoteNoteInterpreter(),
                provider(mapper, LogisticsQuoteMapper.class),
                provider(localDbReadyService(), LocalDbBootstrapStatusService.class)
        );

        LogisticsQuoteSourceBundleCreateCommand createCommand = new LogisticsQuoteSourceBundleCreateCommand();
        createCommand.setForwarderName("义特");
        createCommand.setBundleName("义特-沙特海运双清-2026-04");

        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput file = new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        file.setFileName("待归档报价表.xlsx");
        createCommand.setFiles(List.of(file));

        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput note = new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        note.setContent("单品需要分别加240/方");
        createCommand.setNotes(List.of(note));

        LogisticsQuoteWorkbenchView created = service.createSourceBundle(createCommand);
        Long bundleId = created.getSelectedBundleId();
        Long fileId = created.getSelectedBundle().getFiles().get(0).getId();

        MockMultipartFile archiveFile = new MockMultipartFile(
                "file",
                "义特报价表.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "quote".getBytes()
        );
        LogisticsQuoteWorkbenchView updated = service.archiveSourceBundleFile(bundleId, null, fileId, archiveFile);

        SourceFileView archived = updated.getSelectedBundle().getFiles().get(0);
        Assertions.assertEquals("义特报价表.xlsx", archived.getFileName());
        Assertions.assertEquals("excel", archived.getFileType());
        Assertions.assertEquals(Boolean.TRUE, archived.getArchived());
        Assertions.assertEquals("/api/logistics-quote/source-files/" + fileId + "/archive", archived.getArchiveUrl());
        Assertions.assertTrue(updated.getMessage().contains("报价文件原件已归档"));
        Assertions.assertTrue(service.resolveArchivedSourceFile(fileId).getFileName().contains("义特报价表.xlsx"));
    }

    @Test
    void shouldPreserveExplicitSelectedNoteWhenFileIsAppendedAndReadBack() {
        InMemoryLogisticsQuoteMapper mapper = new InMemoryLogisticsQuoteMapper();
        LogisticsQuoteWorkbenchService service = new LogisticsQuoteWorkbenchService(
                new LogisticsQuoteNoteInterpreter(),
                provider(mapper, LogisticsQuoteMapper.class),
                provider(localDbReadyService(), LocalDbBootstrapStatusService.class)
        );

        LogisticsQuoteSourceBundleCreateCommand createCommand = new LogisticsQuoteSourceBundleCreateCommand();
        createCommand.setForwarderName("义特");
        createCommand.setBundleName("义特-沙特海运双清-2026-04");

        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput firstFile = new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        firstFile.setFileName("义特报价表.xlsx");
        createCommand.setFiles(List.of(firstFile));

        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput firstNote = new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        firstNote.setNoteType("manual_note");
        firstNote.setSourceChannel("wechat");
        firstNote.setContent("单品需要分别加240/方");
        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput secondNote = new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        secondNote.setNoteType("analysis_note");
        secondNote.setSourceChannel("manual");
        secondNote.setContent("低于10方不接报关");
        createCommand.setNotes(List.of(firstNote, secondNote));

        LogisticsQuoteWorkbenchView created = service.createSourceBundle(createCommand);
        Long bundleId = created.getSelectedBundleId();
        Long selectedNoteId = created.getSelectedBundle().getNotes().get(1).getId();

        LogisticsQuoteSourceBundleFileCreateCommand appendCommand = new LogisticsQuoteSourceBundleFileCreateCommand();
        appendCommand.setFileName("义特补充说明.pdf");
        appendCommand.setFilePath("/quotes/yi-te-appendix.pdf");

        LogisticsQuoteWorkbenchView updated = service.appendSourceBundleFile(bundleId, selectedNoteId, appendCommand);

        Assertions.assertEquals(selectedNoteId, updated.getSelectedBundle().getSelectedNoteId());
        Assertions.assertEquals(2, updated.getSelectedBundle().getFiles().size());
        Assertions.assertEquals("义特补充说明.pdf", updated.getSelectedBundle().getFiles().get(1).getFileName());
        Assertions.assertEquals(updated.getSelectedBundle().getFiles().get(1).getId(), updated.getSelectedBundle().getSelectedFileId());
    }

    @Test
    void shouldAppendStoredSourceBundleNoteAndSelectItOnReadback() {
        InMemoryLogisticsQuoteMapper mapper = new InMemoryLogisticsQuoteMapper();
        LogisticsQuoteWorkbenchService service = new LogisticsQuoteWorkbenchService(
                new LogisticsQuoteNoteInterpreter(),
                provider(mapper, LogisticsQuoteMapper.class),
                provider(localDbReadyService(), LocalDbBootstrapStatusService.class)
        );

        LogisticsQuoteSourceBundleCreateCommand createCommand = new LogisticsQuoteSourceBundleCreateCommand();
        createCommand.setForwarderName("义特");
        createCommand.setBundleName("义特-沙特海运双清-2026-04");

        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput file = new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        file.setFileName("义特报价表.xlsx");
        createCommand.setFiles(List.of(file));

        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput note = new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        note.setNoteType("manual_note");
        note.setSourceChannel("wechat");
        note.setContent("单品需要分别加240/方");
        createCommand.setNotes(List.of(note));

        LogisticsQuoteWorkbenchView created = service.createSourceBundle(createCommand);
        Long bundleId = created.getSelectedBundleId();

        LogisticsQuoteSourceBundleNoteCreateCommand appendCommand = new LogisticsQuoteSourceBundleNoteCreateCommand();
        appendCommand.setNoteType("analysis_note");
        appendCommand.setSourceChannel("manual");
        appendCommand.setContent("新增补充：FBA/FBN 送仓最低 300 RMB/票");

        LogisticsQuoteWorkbenchView updated = service.appendSourceBundleNote(bundleId, null, appendCommand);

        Assertions.assertEquals("local-db", updated.getMode());
        Assertions.assertTrue(updated.getMessage().contains("补充文案已追加"));
        Assertions.assertEquals(bundleId, updated.getSelectedBundleId());
        Assertions.assertEquals(2, updated.getSelectedBundle().getNotes().size());
        Assertions.assertEquals(
                "新增补充：FBA/FBN 送仓最低 300 RMB/票",
                updated.getSelectedBundle().getNotes().get(1).getContent()
        );
        Assertions.assertEquals(
                updated.getSelectedBundle().getNotes().get(1).getId(),
                updated.getSelectedBundle().getSelectedNoteId()
        );
        Assertions.assertTrue(updated.getSelectedBundle().getSourceReadbackHint().contains("补充文案 2 条"));
    }

    @Test
    void shouldUpdateStoredSourceBundleNoteAndReadBackLatestContent() {
        InMemoryLogisticsQuoteMapper mapper = new InMemoryLogisticsQuoteMapper();
        LogisticsQuoteWorkbenchService service = new LogisticsQuoteWorkbenchService(
                new LogisticsQuoteNoteInterpreter(),
                provider(mapper, LogisticsQuoteMapper.class),
                provider(localDbReadyService(), LocalDbBootstrapStatusService.class)
        );

        LogisticsQuoteSourceBundleCreateCommand createCommand = new LogisticsQuoteSourceBundleCreateCommand();
        createCommand.setForwarderName("义特");
        createCommand.setBundleName("义特-沙特海运双清-2026-04");

        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput file = new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        file.setFileName("义特报价表.xlsx");
        createCommand.setFiles(List.of(file));

        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput note = new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        note.setNoteType("manual_note");
        note.setSourceChannel("wechat");
        note.setContent("单品需要分别加240/方");
        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput secondNote = new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        secondNote.setNoteType("analysis_note");
        secondNote.setSourceChannel("manual");
        secondNote.setContent("低于10方不接报关");
        createCommand.setNotes(List.of(note, secondNote));

        LogisticsQuoteWorkbenchView created = service.createSourceBundle(createCommand);
        Long bundleId = created.getSelectedBundleId();
        Long noteId = created.getSelectedBundle().getNotes().get(0).getId();
        Assertions.assertEquals(noteId, created.getSelectedBundle().getSelectedNoteId());

        LogisticsQuoteSourceBundleNoteUpdateCommand updateCommand = new LogisticsQuoteSourceBundleNoteUpdateCommand();
        updateCommand.setNoteId(noteId);
        updateCommand.setContent("单品需要分别加240/方；单箱重量不超40公斤");

        LogisticsQuoteWorkbenchView updated = service.updateSourceBundleNote(bundleId, null, updateCommand);

        Assertions.assertEquals("local-db", updated.getMode());
        Assertions.assertTrue(updated.getMessage().contains("已从本地库重新回读"));
        Assertions.assertEquals(bundleId, updated.getSelectedBundleId());
        Assertions.assertEquals(noteId, updated.getSelectedBundle().getSelectedNoteId());
        Assertions.assertEquals(
                "单品需要分别加240/方；单箱重量不超40公斤",
                updated.getSelectedBundle().getNotes().get(0).getContent()
        );
        Assertions.assertTrue(updated.getSelectedBundle().getSourceReadbackHint().contains("当前详情已从本地库真实回读"));
    }

    @Test
    void shouldUpdateStoredSourceBundleFileAndReadBackLatestMetadata() {
        InMemoryLogisticsQuoteMapper mapper = new InMemoryLogisticsQuoteMapper();
        LogisticsQuoteWorkbenchService service = new LogisticsQuoteWorkbenchService(
                new LogisticsQuoteNoteInterpreter(),
                provider(mapper, LogisticsQuoteMapper.class),
                provider(localDbReadyService(), LocalDbBootstrapStatusService.class)
        );

        LogisticsQuoteSourceBundleCreateCommand createCommand = new LogisticsQuoteSourceBundleCreateCommand();
        createCommand.setForwarderName("义特");
        createCommand.setBundleName("义特-沙特海运双清-2026-04");

        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput file = new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        file.setFileName("义特报价表.xlsx");
        file.setFilePath("/quotes/yi-te.xlsx");
        createCommand.setFiles(List.of(file));

        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput note = new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        note.setContent("单品需要分别加240/方");
        createCommand.setNotes(List.of(note));

        LogisticsQuoteWorkbenchView created = service.createSourceBundle(createCommand);
        Long bundleId = created.getSelectedBundleId();
        Long fileId = created.getSelectedBundle().getFiles().get(0).getId();

        LogisticsQuoteSourceBundleFileUpdateCommand updateCommand = new LogisticsQuoteSourceBundleFileUpdateCommand();
        updateCommand.setFileId(fileId);
        updateCommand.setFileName("义特报价表-已核对.pdf");
        updateCommand.setFilePath("/quotes/yi-te-reviewed.pdf");

        LogisticsQuoteWorkbenchView updated = service.updateSourceBundleFile(bundleId, null, fileId, updateCommand);

        Assertions.assertEquals("local-db", updated.getMode());
        Assertions.assertTrue(updated.getMessage().contains("来源文件元数据已更新"));
        Assertions.assertEquals(bundleId, updated.getSelectedBundleId());
        Assertions.assertEquals(1, updated.getSelectedBundle().getFiles().size());
        Assertions.assertEquals("义特报价表-已核对.pdf", updated.getSelectedBundle().getFiles().get(0).getFileName());
        Assertions.assertEquals("pdf", updated.getSelectedBundle().getFiles().get(0).getFileType());
        Assertions.assertEquals("/quotes/yi-te-reviewed.pdf", updated.getSelectedBundle().getFiles().get(0).getSourceLabel());
        Assertions.assertTrue(updated.getSelectedBundle().getSourceReadbackHint().contains("来源文件元数据 1 条"));
        Assertions.assertTrue(updated.getSelectedBundle().getSourceReadbackHint().contains("报价文件可归档原件但不做 OCR/解析"));
    }

    @Test
    void shouldUpdateStoredAnalysisSummaryAndReadBackLatestContent() {
        InMemoryLogisticsQuoteMapper mapper = new InMemoryLogisticsQuoteMapper();
        LogisticsQuoteWorkbenchService service = new LogisticsQuoteWorkbenchService(
                new LogisticsQuoteNoteInterpreter(),
                provider(mapper, LogisticsQuoteMapper.class),
                provider(localDbReadyService(), LocalDbBootstrapStatusService.class)
        );

        LogisticsQuoteSourceBundleCreateCommand createCommand = new LogisticsQuoteSourceBundleCreateCommand();
        createCommand.setForwarderName("起客");
        createCommand.setBundleName("起客-沙特空派-2026-04");
        createCommand.setAnalysisSummary("首版来源包已录入，待补充细分规则。");

        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput file = new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        file.setFileName("起客报价表.xlsx");
        createCommand.setFiles(List.of(file));

        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput note = new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        note.setContent("低于10方不接报关");
        createCommand.setNotes(List.of(note));

        LogisticsQuoteWorkbenchView created = service.createSourceBundle(createCommand);
        Long bundleId = created.getSelectedBundleId();

        LogisticsQuoteSourceBundleAnalysisSummaryUpdateCommand updateCommand =
                new LogisticsQuoteSourceBundleAnalysisSummaryUpdateCommand();
        updateCommand.setAnalysisSummary("已补充回读验证：来源摘要可编辑并从本地库真实回读。");

        LogisticsQuoteWorkbenchView updated = service.updateSourceBundleAnalysisSummary(bundleId, null, null, updateCommand);

        Assertions.assertEquals("local-db", updated.getMode());
        Assertions.assertTrue(updated.getMessage().contains("bundle 摘要"));
        Assertions.assertEquals(bundleId, updated.getSelectedBundleId());
        Assertions.assertEquals(
                "已补充回读验证：来源摘要可编辑并从本地库真实回读。",
                updated.getSelectedBundle().getAnalysisSummary()
        );
        Assertions.assertTrue(updated.getSelectedBundle().getSourceReadbackHint().contains("来源摘要 已保存"));
        Assertions.assertTrue(updated.getSelectedBundle().getQuoteVersion().getSummary().contains("已补充回读验证"));
    }

    @Test
    void shouldPreserveSelectedNoteWhenSiblingRowsAreSavedAndReadBack() {
        InMemoryLogisticsQuoteMapper mapper = new InMemoryLogisticsQuoteMapper();
        LogisticsQuoteWorkbenchService service = new LogisticsQuoteWorkbenchService(
                new LogisticsQuoteNoteInterpreter(),
                provider(mapper, LogisticsQuoteMapper.class),
                provider(localDbReadyService(), LocalDbBootstrapStatusService.class)
        );

        LogisticsQuoteSourceBundleCreateCommand createCommand = new LogisticsQuoteSourceBundleCreateCommand();
        createCommand.setForwarderName("义特");
        createCommand.setBundleName("义特-沙特海运双清-2026-04");

        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput file = new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        file.setFileName("义特报价表.xlsx");
        createCommand.setFiles(List.of(file));

        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput firstNote = new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        firstNote.setNoteType("manual_note");
        firstNote.setSourceChannel("wechat");
        firstNote.setContent("单品需要分别加240/方");
        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput secondNote = new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        secondNote.setNoteType("analysis_note");
        secondNote.setSourceChannel("manual");
        secondNote.setContent("低于10方不接报关");
        createCommand.setNotes(List.of(firstNote, secondNote));

        LogisticsQuoteWorkbenchView created = service.createSourceBundle(createCommand);
        Long bundleId = created.getSelectedBundleId();
        Long selectedNoteId = created.getSelectedBundle().getNotes().get(1).getId();
        Long fileId = created.getSelectedBundle().getFiles().get(0).getId();

        LogisticsQuoteSourceBundleFileUpdateCommand fileUpdateCommand = new LogisticsQuoteSourceBundleFileUpdateCommand();
        fileUpdateCommand.setFileId(fileId);
        fileUpdateCommand.setFileName("义特报价表-已核对.pdf");

        LogisticsQuoteWorkbenchView afterFileUpdate =
                service.updateSourceBundleFile(bundleId, selectedNoteId, fileId, fileUpdateCommand);

        Assertions.assertEquals(selectedNoteId, afterFileUpdate.getSelectedBundle().getSelectedNoteId());

        LogisticsQuoteSourceBundleAnalysisSummaryUpdateCommand summaryUpdateCommand =
                new LogisticsQuoteSourceBundleAnalysisSummaryUpdateCommand();
        summaryUpdateCommand.setAnalysisSummary("保持当前 note 选中态，再保存来源摘要。");

        LogisticsQuoteWorkbenchView afterSummaryUpdate =
                service.updateSourceBundleAnalysisSummary(bundleId, selectedNoteId, fileId, summaryUpdateCommand);

        Assertions.assertEquals(selectedNoteId, afterSummaryUpdate.getSelectedBundle().getSelectedNoteId());
    }

    @Test
    void shouldPreserveExplicitSelectedFileWhenAnalysisSummaryIsUpdatedAndReadBack() {
        InMemoryLogisticsQuoteMapper mapper = new InMemoryLogisticsQuoteMapper();
        LogisticsQuoteWorkbenchService service = new LogisticsQuoteWorkbenchService(
                new LogisticsQuoteNoteInterpreter(),
                provider(mapper, LogisticsQuoteMapper.class),
                provider(localDbReadyService(), LocalDbBootstrapStatusService.class)
        );

        LogisticsQuoteSourceBundleCreateCommand createCommand = new LogisticsQuoteSourceBundleCreateCommand();
        createCommand.setForwarderName("义特");
        createCommand.setBundleName("义特-沙特海运双清-2026-04");

        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput firstFile = new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        firstFile.setFileName("义特报价表.xlsx");
        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput secondFile = new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        secondFile.setFileName("义特补充说明.pdf");
        createCommand.setFiles(List.of(firstFile, secondFile));

        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput firstNote = new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        firstNote.setNoteType("manual_note");
        firstNote.setSourceChannel("wechat");
        firstNote.setContent("单品需要分别加240/方");
        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput secondNote = new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        secondNote.setNoteType("analysis_note");
        secondNote.setSourceChannel("manual");
        secondNote.setContent("低于10方不接报关");
        createCommand.setNotes(List.of(firstNote, secondNote));

        LogisticsQuoteWorkbenchView created = service.createSourceBundle(createCommand);
        Long bundleId = created.getSelectedBundleId();
        Long selectedNoteId = created.getSelectedBundle().getNotes().get(1).getId();
        Long selectedFileId = created.getSelectedBundle().getFiles().get(1).getId();

        LogisticsQuoteSourceBundleAnalysisSummaryUpdateCommand updateCommand =
                new LogisticsQuoteSourceBundleAnalysisSummaryUpdateCommand();
        updateCommand.setAnalysisSummary("保持当前 file 选中态，再保存来源摘要。");

        LogisticsQuoteWorkbenchView updated =
                service.updateSourceBundleAnalysisSummary(bundleId, selectedNoteId, selectedFileId, updateCommand);

        Assertions.assertEquals(selectedNoteId, updated.getSelectedBundle().getSelectedNoteId());
        Assertions.assertEquals(selectedFileId, updated.getSelectedBundle().getSelectedFileId());
    }

    @Test
    void shouldPreserveExplicitSelectedFileWhenNoteIsUpdatedAndReadBack() {
        InMemoryLogisticsQuoteMapper mapper = new InMemoryLogisticsQuoteMapper();
        LogisticsQuoteWorkbenchService service = new LogisticsQuoteWorkbenchService(
                new LogisticsQuoteNoteInterpreter(),
                provider(mapper, LogisticsQuoteMapper.class),
                provider(localDbReadyService(), LocalDbBootstrapStatusService.class)
        );

        LogisticsQuoteSourceBundleCreateCommand createCommand = new LogisticsQuoteSourceBundleCreateCommand();
        createCommand.setForwarderName("义特");
        createCommand.setBundleName("义特-沙特海运双清-2026-04");

        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput firstFile = new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        firstFile.setFileName("义特报价表.xlsx");
        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput secondFile = new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        secondFile.setFileName("义特补充说明.pdf");
        createCommand.setFiles(List.of(firstFile, secondFile));

        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput firstNote = new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        firstNote.setNoteType("manual_note");
        firstNote.setSourceChannel("wechat");
        firstNote.setContent("单品需要分别加240/方");
        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput secondNote = new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        secondNote.setNoteType("analysis_note");
        secondNote.setSourceChannel("manual");
        secondNote.setContent("低于10方不接报关");
        createCommand.setNotes(List.of(firstNote, secondNote));

        LogisticsQuoteWorkbenchView created = service.createSourceBundle(createCommand);
        Long bundleId = created.getSelectedBundleId();
        Long selectedFileId = created.getSelectedBundle().getFiles().get(0).getId();
        Long noteId = created.getSelectedBundle().getNotes().get(1).getId();

        LogisticsQuoteSourceBundleNoteUpdateCommand updateCommand = new LogisticsQuoteSourceBundleNoteUpdateCommand();
        updateCommand.setNoteId(noteId);
        updateCommand.setContent("低于10方不接报关；送仓最低300RMB/票");

        LogisticsQuoteWorkbenchView updated = service.updateSourceBundleNote(bundleId, selectedFileId, updateCommand);

        Assertions.assertEquals(noteId, updated.getSelectedBundle().getSelectedNoteId());
        Assertions.assertEquals(selectedFileId, updated.getSelectedBundle().getSelectedFileId());
    }

    @Test
    void shouldPreserveExplicitSelectedFileWhenNoteIsAppendedAndReadBack() {
        InMemoryLogisticsQuoteMapper mapper = new InMemoryLogisticsQuoteMapper();
        LogisticsQuoteWorkbenchService service = new LogisticsQuoteWorkbenchService(
                new LogisticsQuoteNoteInterpreter(),
                provider(mapper, LogisticsQuoteMapper.class),
                provider(localDbReadyService(), LocalDbBootstrapStatusService.class)
        );

        LogisticsQuoteSourceBundleCreateCommand createCommand = new LogisticsQuoteSourceBundleCreateCommand();
        createCommand.setForwarderName("义特");
        createCommand.setBundleName("义特-沙特海运双清-2026-04");

        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput firstFile = new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        firstFile.setFileName("义特报价表.xlsx");
        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput secondFile = new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        secondFile.setFileName("义特补充说明.pdf");
        createCommand.setFiles(List.of(firstFile, secondFile));

        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput firstNote = new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        firstNote.setNoteType("manual_note");
        firstNote.setSourceChannel("wechat");
        firstNote.setContent("单品需要分别加240/方");
        createCommand.setNotes(List.of(firstNote));

        LogisticsQuoteWorkbenchView created = service.createSourceBundle(createCommand);
        Long bundleId = created.getSelectedBundleId();
        Long selectedFileId = created.getSelectedBundle().getFiles().get(0).getId();

        LogisticsQuoteSourceBundleNoteCreateCommand appendCommand = new LogisticsQuoteSourceBundleNoteCreateCommand();
        appendCommand.setNoteType("analysis_note");
        appendCommand.setSourceChannel("manual");
        appendCommand.setContent("新增补充：送仓最低300RMB/票");

        LogisticsQuoteWorkbenchView updated = service.appendSourceBundleNote(bundleId, selectedFileId, appendCommand);

        Assertions.assertEquals(selectedFileId, updated.getSelectedBundle().getSelectedFileId());
        Assertions.assertEquals(2, updated.getSelectedBundle().getNotes().size());
        Assertions.assertEquals(
                "新增补充：送仓最低300RMB/票",
                updated.getSelectedBundle().getNotes().get(1).getContent()
        );
        Assertions.assertEquals(
                updated.getSelectedBundle().getNotes().get(1).getId(),
                updated.getSelectedBundle().getSelectedNoteId()
        );
    }

    @Test
    void shouldPreserveExplicitSelectedFileWhenSiblingRowsAreSavedAndReadBack() {
        InMemoryLogisticsQuoteMapper mapper = new InMemoryLogisticsQuoteMapper();
        LogisticsQuoteWorkbenchService service = new LogisticsQuoteWorkbenchService(
                new LogisticsQuoteNoteInterpreter(),
                provider(mapper, LogisticsQuoteMapper.class),
                provider(localDbReadyService(), LocalDbBootstrapStatusService.class)
        );

        LogisticsQuoteSourceBundleCreateCommand createCommand = new LogisticsQuoteSourceBundleCreateCommand();
        createCommand.setForwarderName("义特");
        createCommand.setBundleName("义特-沙特海运双清-2026-04");

        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput firstFile = new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        firstFile.setFileName("义特报价表.xlsx");
        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput secondFile = new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        secondFile.setFileName("义特补充说明.pdf");
        createCommand.setFiles(List.of(firstFile, secondFile));

        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput note = new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        note.setNoteType("manual_note");
        note.setSourceChannel("wechat");
        note.setContent("单品需要分别加240/方");
        createCommand.setNotes(List.of(note));

        LogisticsQuoteWorkbenchView created = service.createSourceBundle(createCommand);
        Long bundleId = created.getSelectedBundleId();
        Long selectedFileId = created.getSelectedBundle().getFiles().get(0).getId();
        Long siblingFileId = created.getSelectedBundle().getFiles().get(1).getId();

        LogisticsQuoteSourceBundleAnalysisSummaryUpdateCommand summaryUpdateCommand =
                new LogisticsQuoteSourceBundleAnalysisSummaryUpdateCommand();
        summaryUpdateCommand.setAnalysisSummary("保持当前 file 选中态，再保存来源摘要。");

        LogisticsQuoteWorkbenchView afterSummaryUpdate =
                service.updateSourceBundleAnalysisSummary(bundleId, created.getSelectedBundle().getSelectedNoteId(), selectedFileId, summaryUpdateCommand);

        Assertions.assertEquals(selectedFileId, afterSummaryUpdate.getSelectedBundle().getSelectedFileId());

        LogisticsQuoteSourceBundleFileUpdateCommand fileUpdateCommand = new LogisticsQuoteSourceBundleFileUpdateCommand();
        fileUpdateCommand.setFileId(siblingFileId);
        fileUpdateCommand.setFileName("义特补充说明-已核对.pdf");

        LogisticsQuoteWorkbenchView afterSiblingFileUpdate =
                service.updateSourceBundleFile(bundleId, created.getSelectedBundle().getSelectedNoteId(), selectedFileId, fileUpdateCommand);

        Assertions.assertEquals(selectedFileId, afterSiblingFileUpdate.getSelectedBundle().getSelectedFileId());
        Assertions.assertEquals("义特补充说明-已核对.pdf", afterSiblingFileUpdate.getSelectedBundle().getFiles().get(1).getFileName());
    }

    @Test
    void shouldPreserveExplicitNoteAndFileSelectionAcrossBundleSwitchReadback() {
        InMemoryLogisticsQuoteMapper mapper = new InMemoryLogisticsQuoteMapper();
        LogisticsQuoteWorkbenchService service = new LogisticsQuoteWorkbenchService(
                new LogisticsQuoteNoteInterpreter(),
                provider(mapper, LogisticsQuoteMapper.class),
                provider(localDbReadyService(), LocalDbBootstrapStatusService.class)
        );

        LogisticsQuoteSourceBundleCreateCommand firstBundleCommand = new LogisticsQuoteSourceBundleCreateCommand();
        firstBundleCommand.setForwarderName("义特");
        firstBundleCommand.setBundleName("义特-沙特海运双清-2026-04");
        firstBundleCommand.setAnalysisStatus("ANALYZED");
        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput firstBundleFileA =
                new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        firstBundleFileA.setFileName("义特报价表.xlsx");
        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput firstBundleFileB =
                new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        firstBundleFileB.setFileName("义特补充说明.pdf");
        firstBundleFileB.setFilePath("/quotes/yi-te-appendix.pdf");
        firstBundleCommand.setFiles(List.of(firstBundleFileA, firstBundleFileB));
        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput firstBundleNoteA =
                new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        firstBundleNoteA.setNoteType("manual_note");
        firstBundleNoteA.setSourceChannel("wechat");
        firstBundleNoteA.setContent("单品需要分别加240/方");
        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput firstBundleNoteB =
                new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        firstBundleNoteB.setNoteType("analysis_note");
        firstBundleNoteB.setSourceChannel("manual");
        firstBundleNoteB.setContent("低于10方不接报关");
        firstBundleCommand.setNotes(List.of(firstBundleNoteA, firstBundleNoteB));
        LogisticsQuoteWorkbenchView firstBundleCreated = service.createSourceBundle(firstBundleCommand);

        LogisticsQuoteSourceBundleCreateCommand secondBundleCommand = new LogisticsQuoteSourceBundleCreateCommand();
        secondBundleCommand.setForwarderName("起客");
        secondBundleCommand.setBundleName("起客-沙特空派-2026-04");
        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput secondBundleFileA =
                new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        secondBundleFileA.setFileName("起客报价表.xlsx");
        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput secondBundleFileB =
                new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        secondBundleFileB.setFileName("起客补充说明.pdf");
        secondBundleCommand.setFiles(List.of(secondBundleFileA, secondBundleFileB));
        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput secondBundleNoteA =
                new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        secondBundleNoteA.setNoteType("manual_note");
        secondBundleNoteA.setSourceChannel("wechat");
        secondBundleNoteA.setContent("FBA/FBN 送仓 3 RMB/KG");
        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput secondBundleNoteB =
                new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        secondBundleNoteB.setNoteType("analysis_note");
        secondBundleNoteB.setSourceChannel("manual");
        secondBundleNoteB.setContent("单箱重量不超40kg");
        secondBundleCommand.setNotes(List.of(secondBundleNoteA, secondBundleNoteB));
        LogisticsQuoteWorkbenchView secondBundleCreated = service.createSourceBundle(secondBundleCommand);

        Long firstBundleId = firstBundleCreated.getSelectedBundleId();
        LogisticsQuoteSourceBundleAnalysisSummaryUpdateCommand summaryUpdateCommand =
                new LogisticsQuoteSourceBundleAnalysisSummaryUpdateCommand();
        summaryUpdateCommand.setAnalysisSummary("首个来源包的 persisted 分析摘要");
        LogisticsQuoteWorkbenchView firstBundleAfterSummaryUpdate = service.updateSourceBundleAnalysisSummary(
                firstBundleId,
                firstBundleCreated.getSelectedBundle().getNotes().get(1).getId(),
                firstBundleCreated.getSelectedBundle().getFiles().get(1).getId(),
                summaryUpdateCommand
        );
        Long firstSelectedFileId = firstBundleCreated.getSelectedBundle().getFiles().get(1).getId();
        Long firstSelectedNoteId = firstBundleCreated.getSelectedBundle().getNotes().get(1).getId();
        Long secondBundleId = secondBundleCreated.getSelectedBundleId();
        Long secondSelectedFileId = secondBundleCreated.getSelectedBundle().getFiles().get(0).getId();
        Long secondSelectedNoteId = secondBundleCreated.getSelectedBundle().getNotes().get(0).getId();

        LogisticsQuoteWorkbenchView firstSelectedView =
                service.buildWorkbench(firstBundleId, firstSelectedNoteId, firstSelectedFileId);
        Assertions.assertEquals(firstBundleId, firstSelectedView.getSelectedBundleId());
        Assertions.assertEquals(firstSelectedNoteId, firstSelectedView.getSelectedBundle().getSelectedNoteId());
        Assertions.assertEquals(firstSelectedFileId, firstSelectedView.getSelectedBundle().getSelectedFileId());
        Assertions.assertEquals(
                firstBundleAfterSummaryUpdate.getSelectedBundle().getAnalysisSummary(),
                firstSelectedView.getSelectedBundle().getAnalysisSummary()
        );
        Assertions.assertTrue(
                firstSelectedView.getSelectedBundle().getQuoteVersion().getSummary().contains("首个来源包的 persisted 分析摘要")
        );
        Assertions.assertEquals("/quotes/yi-te-appendix.pdf", firstSelectedView.getSelectedBundle().getFiles().get(1).getFilePath());

        LogisticsQuoteWorkbenchView secondSelectedView =
                service.buildWorkbench(secondBundleId, secondSelectedNoteId, secondSelectedFileId);
        Assertions.assertEquals(secondBundleId, secondSelectedView.getSelectedBundleId());
        Assertions.assertEquals(secondSelectedNoteId, secondSelectedView.getSelectedBundle().getSelectedNoteId());
        Assertions.assertEquals(secondSelectedFileId, secondSelectedView.getSelectedBundle().getSelectedFileId());

        LogisticsQuoteWorkbenchView switchedBackView =
                service.buildWorkbench(firstBundleId, firstSelectedNoteId, firstSelectedFileId);
        Assertions.assertEquals(firstBundleId, switchedBackView.getSelectedBundleId());
        Assertions.assertEquals("义特-沙特海运双清-2026-04", switchedBackView.getSelectedBundle().getBundleName());
        Assertions.assertEquals("ANALYZED", switchedBackView.getSelectedBundle().getAnalysisStatus());
        Assertions.assertEquals("义特", switchedBackView.getSelectedBundle().getForwarder().getName());
        Assertions.assertEquals(firstSelectedNoteId, switchedBackView.getSelectedBundle().getSelectedNoteId());
        Assertions.assertEquals(firstSelectedFileId, switchedBackView.getSelectedBundle().getSelectedFileId());
        Assertions.assertEquals("首个来源包的 persisted 分析摘要", switchedBackView.getSelectedBundle().getAnalysisSummary());
        Assertions.assertTrue(
                switchedBackView.getSelectedBundle().getQuoteVersion().getSummary().contains("首个来源包的 persisted 分析摘要")
        );
        Assertions.assertTrue(
                switchedBackView.getSelectedBundle().getSourceReadbackHint().contains("来源文件元数据 2 条")
        );
        Assertions.assertTrue(
                switchedBackView.getSelectedBundle().getSourceReadbackHint().contains("补充文案 2 条")
        );
        Assertions.assertTrue(
                switchedBackView.getSelectedBundle().getSourceReadbackHint().contains("来源摘要 已保存")
        );
        Assertions.assertEquals("低于10方不接报关", switchedBackView.getSelectedBundle().getNotes().get(1).getContent());
        Assertions.assertEquals("义特补充说明.pdf", switchedBackView.getSelectedBundle().getFiles().get(1).getFileName());
        Assertions.assertEquals(
                "/quotes/yi-te-appendix.pdf",
                switchedBackView.getSelectedBundle().getFiles().get(1).getFilePath()
        );
    }

    @Test
    void shouldIgnoreForeignSelectionIdsWhenSwitchingToAnotherStoredBundle() {
        InMemoryLogisticsQuoteMapper mapper = new InMemoryLogisticsQuoteMapper();
        LogisticsQuoteWorkbenchService service = new LogisticsQuoteWorkbenchService(
                new LogisticsQuoteNoteInterpreter(),
                provider(mapper, LogisticsQuoteMapper.class),
                provider(localDbReadyService(), LocalDbBootstrapStatusService.class)
        );

        LogisticsQuoteSourceBundleCreateCommand firstBundleCommand = new LogisticsQuoteSourceBundleCreateCommand();
        firstBundleCommand.setForwarderName("义特");
        firstBundleCommand.setBundleName("义特-沙特海运双清-2026-04");
        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput firstBundleFileA =
                new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        firstBundleFileA.setFileName("义特报价表.xlsx");
        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput firstBundleFileB =
                new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        firstBundleFileB.setFileName("义特补充说明.pdf");
        firstBundleCommand.setFiles(List.of(firstBundleFileA, firstBundleFileB));
        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput firstBundleNoteA =
                new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        firstBundleNoteA.setNoteType("manual_note");
        firstBundleNoteA.setSourceChannel("wechat");
        firstBundleNoteA.setContent("单品需要分别加240/方");
        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput firstBundleNoteB =
                new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        firstBundleNoteB.setNoteType("analysis_note");
        firstBundleNoteB.setSourceChannel("manual");
        firstBundleNoteB.setContent("低于10方不接报关");
        firstBundleCommand.setNotes(List.of(firstBundleNoteA, firstBundleNoteB));
        LogisticsQuoteWorkbenchView firstBundleCreated = service.createSourceBundle(firstBundleCommand);

        LogisticsQuoteSourceBundleCreateCommand secondBundleCommand = new LogisticsQuoteSourceBundleCreateCommand();
        secondBundleCommand.setForwarderName("起客");
        secondBundleCommand.setBundleName("起客-沙特空派-2026-04");
        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput secondBundleFileA =
                new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        secondBundleFileA.setFileName("起客报价表.xlsx");
        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput secondBundleFileB =
                new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        secondBundleFileB.setFileName("起客补充说明.pdf");
        secondBundleCommand.setFiles(List.of(secondBundleFileA, secondBundleFileB));
        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput secondBundleNoteA =
                new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        secondBundleNoteA.setNoteType("manual_note");
        secondBundleNoteA.setSourceChannel("wechat");
        secondBundleNoteA.setContent("FBA/FBN 送仓 3 RMB/KG");
        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput secondBundleNoteB =
                new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        secondBundleNoteB.setNoteType("analysis_note");
        secondBundleNoteB.setSourceChannel("manual");
        secondBundleNoteB.setContent("单箱重量不超40kg");
        secondBundleCommand.setNotes(List.of(secondBundleNoteA, secondBundleNoteB));
        LogisticsQuoteWorkbenchView secondBundleCreated = service.createSourceBundle(secondBundleCommand);

        Long firstBundleForeignNoteId = firstBundleCreated.getSelectedBundle().getNotes().get(1).getId();
        Long firstBundleForeignFileId = firstBundleCreated.getSelectedBundle().getFiles().get(1).getId();
        Long secondBundleId = secondBundleCreated.getSelectedBundleId();
        Long expectedSecondBundleNoteId = secondBundleCreated.getSelectedBundle().getNotes().get(0).getId();
        Long expectedSecondBundleFileId = secondBundleCreated.getSelectedBundle().getFiles().get(1).getId();

        LogisticsQuoteWorkbenchView switchedView =
                service.buildWorkbench(secondBundleId, firstBundleForeignNoteId, firstBundleForeignFileId);

        Assertions.assertEquals(secondBundleId, switchedView.getSelectedBundleId());
        Assertions.assertEquals("起客-沙特空派-2026-04", switchedView.getSelectedBundle().getBundleName());
        Assertions.assertEquals("起客", switchedView.getSelectedBundle().getForwarder().getName());
        Assertions.assertEquals(expectedSecondBundleNoteId, switchedView.getSelectedBundle().getSelectedNoteId());
        Assertions.assertEquals(expectedSecondBundleFileId, switchedView.getSelectedBundle().getSelectedFileId());
        Assertions.assertEquals("FBA/FBN 送仓 3 RMB/KG", switchedView.getSelectedBundle().getNotes().get(0).getContent());
        Assertions.assertEquals("起客补充说明.pdf", switchedView.getSelectedBundle().getFiles().get(1).getFileName());
    }

    @Test
    void shouldIgnoreForeignSelectionIdsWhenBundleIdFallsBackToFirstStoredBundle() {
        InMemoryLogisticsQuoteMapper mapper = new InMemoryLogisticsQuoteMapper();
        LogisticsQuoteWorkbenchService service = new LogisticsQuoteWorkbenchService(
                new LogisticsQuoteNoteInterpreter(),
                provider(mapper, LogisticsQuoteMapper.class),
                provider(localDbReadyService(), LocalDbBootstrapStatusService.class)
        );

        LogisticsQuoteSourceBundleCreateCommand firstBundleCommand = new LogisticsQuoteSourceBundleCreateCommand();
        firstBundleCommand.setForwarderName("义特");
        firstBundleCommand.setBundleName("义特-沙特海运双清-2026-04");
        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput firstBundleFileA =
                new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        firstBundleFileA.setFileName("义特报价表.xlsx");
        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput firstBundleFileB =
                new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        firstBundleFileB.setFileName("义特补充说明.pdf");
        firstBundleCommand.setFiles(List.of(firstBundleFileA, firstBundleFileB));
        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput firstBundleNoteA =
                new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        firstBundleNoteA.setNoteType("manual_note");
        firstBundleNoteA.setSourceChannel("wechat");
        firstBundleNoteA.setContent("单品需要分别加240/方");
        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput firstBundleNoteB =
                new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        firstBundleNoteB.setNoteType("analysis_note");
        firstBundleNoteB.setSourceChannel("manual");
        firstBundleNoteB.setContent("低于10方不接报关");
        firstBundleCommand.setNotes(List.of(firstBundleNoteA, firstBundleNoteB));
        LogisticsQuoteWorkbenchView firstBundleCreated = service.createSourceBundle(firstBundleCommand);

        LogisticsQuoteSourceBundleCreateCommand secondBundleCommand = new LogisticsQuoteSourceBundleCreateCommand();
        secondBundleCommand.setForwarderName("起客");
        secondBundleCommand.setBundleName("起客-沙特空派-2026-04");
        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput secondBundleFileA =
                new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        secondBundleFileA.setFileName("起客报价表.xlsx");
        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput secondBundleFileB =
                new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        secondBundleFileB.setFileName("起客补充说明.pdf");
        secondBundleCommand.setFiles(List.of(secondBundleFileA, secondBundleFileB));
        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput secondBundleNoteA =
                new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        secondBundleNoteA.setNoteType("manual_note");
        secondBundleNoteA.setSourceChannel("wechat");
        secondBundleNoteA.setContent("FBA/FBN 送仓 3 RMB/KG");
        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput secondBundleNoteB =
                new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        secondBundleNoteB.setNoteType("analysis_note");
        secondBundleNoteB.setSourceChannel("manual");
        secondBundleNoteB.setContent("单箱重量不超40kg");
        secondBundleCommand.setNotes(List.of(secondBundleNoteA, secondBundleNoteB));
        LogisticsQuoteWorkbenchView secondBundleCreated = service.createSourceBundle(secondBundleCommand);

        LogisticsQuoteWorkbenchView defaultFallbackView = service.buildWorkbench(null, null, null);
        Long invalidBundleId = defaultFallbackView.getSelectedBundleId() + 999L;
        Long foreignNoteId = firstBundleCreated.getSelectedBundle().getNotes().get(1).getId();
        Long foreignFileId = firstBundleCreated.getSelectedBundle().getFiles().get(1).getId();
        Long expectedFallbackBundleId = defaultFallbackView.getSelectedBundleId();
        Long expectedFallbackNoteId = defaultFallbackView.getSelectedBundle().getNotes().get(0).getId();
        Long expectedFallbackFileId = defaultFallbackView.getSelectedBundle().getFiles().get(1).getId();

        LogisticsQuoteWorkbenchView fallbackView =
                service.buildWorkbench(invalidBundleId, foreignNoteId, foreignFileId);

        Assertions.assertEquals(expectedFallbackBundleId, fallbackView.getSelectedBundleId());
        Assertions.assertEquals(defaultFallbackView.getSelectedBundle().getBundleName(), fallbackView.getSelectedBundle().getBundleName());
        Assertions.assertEquals(defaultFallbackView.getSelectedBundle().getForwarder().getName(), fallbackView.getSelectedBundle().getForwarder().getName());
        Assertions.assertEquals(expectedFallbackNoteId, fallbackView.getSelectedBundle().getSelectedNoteId());
        Assertions.assertEquals(expectedFallbackFileId, fallbackView.getSelectedBundle().getSelectedFileId());
        Assertions.assertEquals(
                defaultFallbackView.getSelectedBundle().getNotes().get(0).getContent(),
                fallbackView.getSelectedBundle().getNotes().get(0).getContent()
        );
        Assertions.assertEquals(
                defaultFallbackView.getSelectedBundle().getFiles().get(1).getFileName(),
                fallbackView.getSelectedBundle().getFiles().get(1).getFileName()
        );
    }

    @Test
    void shouldKeepResolvedFallbackBundlePersistedHeaderTruthWhenBundleIdFallsBack() {
        InMemoryLogisticsQuoteMapper mapper = new InMemoryLogisticsQuoteMapper();
        LogisticsQuoteWorkbenchService service = new LogisticsQuoteWorkbenchService(
                new LogisticsQuoteNoteInterpreter(),
                provider(mapper, LogisticsQuoteMapper.class),
                provider(localDbReadyService(), LocalDbBootstrapStatusService.class)
        );

        LogisticsQuoteSourceBundleCreateCommand firstBundleCommand = new LogisticsQuoteSourceBundleCreateCommand();
        firstBundleCommand.setForwarderName("义特");
        firstBundleCommand.setBundleName("义特-沙特海运双清-2026-04");
        firstBundleCommand.setAnalysisStatus("ANALYZED");
        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput firstBundleFileA =
                new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        firstBundleFileA.setFileName("义特报价表.xlsx");
        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput firstBundleFileB =
                new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        firstBundleFileB.setFileName("义特补充说明.pdf");
        firstBundleFileB.setFilePath("/quotes/yi-te-appendix.pdf");
        firstBundleCommand.setFiles(List.of(firstBundleFileA, firstBundleFileB));
        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput firstBundleNoteA =
                new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        firstBundleNoteA.setNoteType("manual_note");
        firstBundleNoteA.setSourceChannel("wechat");
        firstBundleNoteA.setContent("单品需要分别加240/方");
        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput firstBundleNoteB =
                new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        firstBundleNoteB.setNoteType("analysis_note");
        firstBundleNoteB.setSourceChannel("manual");
        firstBundleNoteB.setContent("低于10方不接报关");
        firstBundleCommand.setNotes(List.of(firstBundleNoteA, firstBundleNoteB));
        LogisticsQuoteWorkbenchView firstBundleCreated = service.createSourceBundle(firstBundleCommand);

        LogisticsQuoteSourceBundleAnalysisSummaryUpdateCommand summaryUpdateCommand =
                new LogisticsQuoteSourceBundleAnalysisSummaryUpdateCommand();
        summaryUpdateCommand.setAnalysisSummary("invalid bundle fallback should still read persisted bundle header truth");
        LogisticsQuoteSourceBundleCreateCommand secondBundleCommand = new LogisticsQuoteSourceBundleCreateCommand();
        secondBundleCommand.setForwarderName("起客");
        secondBundleCommand.setBundleName("起客-沙特空派-2026-04");
        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput secondBundleFileA =
                new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        secondBundleFileA.setFileName("起客报价表.xlsx");
        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput secondBundleFileB =
                new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        secondBundleFileB.setFileName("起客补充说明.pdf");
        secondBundleFileB.setFilePath("/quotes/qike-appendix.pdf");
        secondBundleCommand.setFiles(List.of(secondBundleFileA, secondBundleFileB));
        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput secondBundleNoteA =
                new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        secondBundleNoteA.setNoteType("manual_note");
        secondBundleNoteA.setSourceChannel("wechat");
        secondBundleNoteA.setContent("FBA/FBN 送仓 3 RMB/KG");
        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput secondBundleNoteB =
                new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        secondBundleNoteB.setNoteType("analysis_note");
        secondBundleNoteB.setSourceChannel("manual");
        secondBundleNoteB.setContent("单箱重量不超40kg");
        secondBundleCommand.setNotes(List.of(secondBundleNoteA, secondBundleNoteB));
        LogisticsQuoteWorkbenchView secondBundleCreated = service.createSourceBundle(secondBundleCommand);

        LogisticsQuoteWorkbenchView secondBundleAfterSummaryUpdate = service.updateSourceBundleAnalysisSummary(
                secondBundleCreated.getSelectedBundleId(),
                secondBundleCreated.getSelectedBundle().getNotes().get(1).getId(),
                secondBundleCreated.getSelectedBundle().getFiles().get(1).getId(),
                summaryUpdateCommand
        );

        LogisticsQuoteWorkbenchView defaultFallbackView = service.buildWorkbench(null, null, null);
        Long invalidBundleId = defaultFallbackView.getSelectedBundleId() + 999L;

        LogisticsQuoteWorkbenchView fallbackView = service.buildWorkbench(
                invalidBundleId,
                firstBundleCreated.getSelectedBundle().getNotes().get(1).getId(),
                firstBundleCreated.getSelectedBundle().getFiles().get(1).getId()
        );

        Assertions.assertEquals(secondBundleAfterSummaryUpdate.getSelectedBundleId(), fallbackView.getSelectedBundleId());
        Assertions.assertEquals("起客-沙特空派-2026-04", fallbackView.getSelectedBundle().getBundleName());
        Assertions.assertEquals("DRAFT", fallbackView.getSelectedBundle().getAnalysisStatus());
        Assertions.assertEquals("起客", fallbackView.getSelectedBundle().getForwarder().getName());
        Assertions.assertEquals(
                "invalid bundle fallback should still read persisted bundle header truth",
                fallbackView.getSelectedBundle().getAnalysisSummary()
        );
        Assertions.assertTrue(
                fallbackView.getSelectedBundle().getQuoteVersion().getSummary().contains(
                        "invalid bundle fallback should still read persisted bundle header truth"
                )
        );
        Assertions.assertTrue(
                fallbackView.getSelectedBundle().getSourceReadbackHint().contains("来源文件元数据 2 条")
        );
        Assertions.assertTrue(
                fallbackView.getSelectedBundle().getSourceReadbackHint().contains("补充文案 2 条")
        );
        Assertions.assertTrue(
                fallbackView.getSelectedBundle().getSourceReadbackHint().contains("来源摘要 已保存")
        );
        Assertions.assertEquals(
                "/quotes/qike-appendix.pdf",
                fallbackView.getSelectedBundle().getFiles().get(1).getFilePath()
        );
    }

    @Test
    void shouldSaveNotePreviewAsStructuredQuoteDraftAndReadBackRules() {
        InMemoryLogisticsQuoteMapper mapper = new InMemoryLogisticsQuoteMapper();
        LogisticsQuoteWorkbenchService service = new LogisticsQuoteWorkbenchService(
                new LogisticsQuoteNoteInterpreter(),
                provider(mapper, LogisticsQuoteMapper.class),
                provider(localDbReadyService(), LocalDbBootstrapStatusService.class)
        );

        LogisticsQuoteSourceBundleCreateCommand createCommand = new LogisticsQuoteSourceBundleCreateCommand();
        createCommand.setForwarderName("义特");
        createCommand.setBundleName("义特-沙特海运双清-2026-04");
        LogisticsQuoteSourceBundleCreateCommand.SourceFileInput file = new LogisticsQuoteSourceBundleCreateCommand.SourceFileInput();
        file.setFileName("沙特价格表.pdf");
        createCommand.setFiles(List.of(file));
        LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput note = new LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput();
        note.setNoteType("manual_note");
        note.setSourceChannel("wechat");
        note.setContent("单品需要分别加240/方；单箱重量不超40公斤");
        createCommand.setNotes(List.of(note));

        LogisticsQuoteWorkbenchView created = service.createSourceBundle(createCommand);
        Long bundleId = created.getSelectedBundleId();
        Long noteId = created.getSelectedBundle().getNotes().get(0).getId();

        LogisticsQuoteDraftFromNoteCommand draftCommand = new LogisticsQuoteDraftFromNoteCommand();
        draftCommand.setNoteId(noteId);
        draftCommand.setServiceName("沙特海运双清包税");
        draftCommand.setCountryCode("SA");
        draftCommand.setRouteCode("CN-SA");
        draftCommand.setTransportMode("SEA");
        draftCommand.setServiceScope("FIRST_LEG");
        draftCommand.setCurrency("CNY");
        draftCommand.setVersionNo("YITE-SA-DRAFT-202604");

        LogisticsQuoteWorkbenchView updated = service.createQuoteDraftFromNote(bundleId, null, draftCommand);

        Assertions.assertEquals("local-db", updated.getMode());
        Assertions.assertTrue(updated.getMessage().contains("结构化报价草稿"));
        Assertions.assertEquals("READY_FOR_REVIEW", updated.getSelectedBundle().getAnalysisStatus());
        Assertions.assertEquals("YITE-SA-DRAFT-202604", updated.getSelectedBundle().getQuoteVersion().getVersionNo());
        Assertions.assertEquals("DRAFT", updated.getSelectedBundle().getQuoteVersion().getStatus());
        Assertions.assertEquals(1, updated.getSelectedBundle().getServices().size());
        Assertions.assertEquals("沙特海运双清包税", updated.getSelectedBundle().getServices().get(0).getServiceName());
        Assertions.assertEquals(1, updated.getSelectedBundle().getRules().size());
        Assertions.assertEquals("CBM", updated.getSelectedBundle().getRules().get(0).getBillingUnit());
        Assertions.assertEquals(240d, updated.getSelectedBundle().getRules().get(0).getUnitPrice());
        Assertions.assertEquals(1, updated.getSelectedBundle().getRestrictions().size());
        Assertions.assertEquals("MAX_BOX_WEIGHT", updated.getSelectedBundle().getRestrictions().get(0).getRestrictionType());
        Assertions.assertEquals(2, updated.getSelectedBundle().getEvidences().size());
        Assertions.assertEquals(1, updated.getSummary().getTotalRules());
        Assertions.assertTrue(updated.getSelectedBundle().getSourceReadbackHint().contains("结构化报价草稿服务 1 条"));
    }

    private static LocalDbBootstrapStatusService localDbReadyService() {
        return new LocalDbBootstrapStatusService(null, null) {
            @Override
            public CoreTableInspection inspect() {
                return new CoreTableInspection(
                        "nuono_new_dev",
                        List.of("forwarder", "quote_source_bundle", "quote_source_file", "quote_source_note"),
                        List.of("forwarder", "quote_source_bundle", "quote_source_file", "quote_source_note"),
                        List.of()
                );
            }
        };
    }

    private static <T> ObjectProvider<T> provider(T instance, Class<T> type) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }

    private static final class InMemoryLogisticsQuoteMapper implements LogisticsQuoteMapper {

        private final Map<Long, ForwarderRecord> forwarders = new LinkedHashMap<>();
        private final Map<Long, BundleRecord> bundles = new LinkedHashMap<>();
        private final Map<Long, FileRecord> files = new LinkedHashMap<>();
        private final Map<Long, NoteRecord> notes = new LinkedHashMap<>();
        private final Map<Long, QuoteVersionRecord> quoteVersions = new LinkedHashMap<>();
        private final Map<Long, ServiceRecord> services = new LinkedHashMap<>();
        private final Map<Long, RuleRecord> rules = new LinkedHashMap<>();
        private final Map<Long, RestrictionRecord> restrictions = new LinkedHashMap<>();
        private final Map<Long, EvidenceRecord> evidences = new LinkedHashMap<>();
        private long nextForwarderId = 70001L;
        private long nextBundleId = 71001L;
        private long nextFileId = 72001L;
        private long nextNoteId = 73001L;
        private long nextQuoteVersionId = 74001L;
        private long nextServiceId = 75001L;
        private long nextRuleId = 76001L;
        private long nextRestrictionId = 77001L;
        private long nextEvidenceId = 78001L;
        private long nextNumericAdjustmentId = 930001L;
        private long nextNumericAdjustmentLogId = 940001L;
        private long tick = 1L;

        @Override
        public Integer countExistingSourceTables(String schema) {
            return 4;
        }

        @Override
        public Integer countExistingQuoteDraftTables(String schema) {
            return 5;
        }

        @Override
        public Integer countExistingOperationQuoteTables(String schema) {
            return 0;
        }

        @Override
        public Long nextForwarderId() {
            return nextForwarderId++;
        }

        @Override
        public Long nextBundleId() {
            return nextBundleId++;
        }

        @Override
        public Long nextFileId() {
            return nextFileId++;
        }

        @Override
        public Long nextNoteId() {
            return nextNoteId++;
        }

        @Override
        public Long nextQuoteVersionId() {
            return nextQuoteVersionId++;
        }

        @Override
        public Long nextServiceId() {
            return nextServiceId++;
        }

        @Override
        public Long nextQuoteRuleId() {
            return nextRuleId++;
        }

        @Override
        public Long nextRestrictionId() {
            return nextRestrictionId++;
        }

        @Override
        public Long nextEvidenceId() {
            return nextEvidenceId++;
        }

        @Override
        public Long nextNumericAdjustmentId() {
            return nextNumericAdjustmentId++;
        }

        @Override
        public Long nextNumericAdjustmentLogId() {
            return nextNumericAdjustmentLogId++;
        }

        @Override
        public Long selectForwarderIdByName(String name) {
            return forwarders.values().stream()
                    .filter(item -> item.name.equals(name))
                    .map(item -> item.id)
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public int upsertForwarder(Long id, String name, String alias, String companyName, String status, String notesText, Long updatedBy) {
            ForwarderRecord record = forwarders.getOrDefault(id, new ForwarderRecord());
            record.id = id;
            record.name = name;
            record.alias = alias;
            record.companyName = companyName;
            record.notes = notesText;
            forwarders.put(id, record);
            return 1;
        }

        @Override
        public int insertBundle(Long id, Long forwarderId, String bundleName, String analysisStatus, String analysisSummary, Long updatedBy) {
            BundleRecord record = new BundleRecord();
            record.id = id;
            record.forwarderId = forwarderId;
            record.bundleName = bundleName;
            record.analysisStatus = analysisStatus;
            record.analysisSummary = analysisSummary;
            record.updatedAt = "tick-" + tick++;
            bundles.put(id, record);
            return 1;
        }

        @Override
        public int insertSourceFile(Long id, Long bundleId, String fileName, String fileType, String filePath, Long updatedBy) {
            FileRecord record = new FileRecord();
            record.id = id;
            record.bundleId = bundleId;
            record.fileName = fileName;
            record.fileType = fileType;
            record.filePath = filePath;
            files.put(id, record);
            return 1;
        }

        @Override
        public int insertSourceNote(Long id, Long bundleId, String noteType, String sourceChannel, String content, String authorName, Long updatedBy) {
            NoteRecord record = new NoteRecord();
            record.id = id;
            record.bundleId = bundleId;
            record.noteType = noteType;
            record.sourceChannel = sourceChannel;
            record.content = content;
            notes.put(id, record);
            return 1;
        }

        @Override
        public int updateSourceNoteContent(Long bundleId, Long noteId, String content, Long updatedBy) {
            NoteRecord record = notes.get(noteId);
            if (record == null || !bundleId.equals(record.bundleId)) {
                return 0;
            }
            record.content = content;
            return 1;
        }

        @Override
        public int updateSourceFileMetadata(Long bundleId, Long fileId, String fileName, String fileType, String filePath, Long updatedBy) {
            FileRecord record = files.get(fileId);
            if (record == null || !bundleId.equals(record.bundleId)) {
                return 0;
            }
            record.fileName = fileName;
            record.fileType = fileType;
            record.filePath = filePath;
            return 1;
        }

        @Override
        public int updateBundleAnalysisSummary(Long bundleId, String analysisSummary, Long updatedBy) {
            BundleRecord record = bundles.get(bundleId);
            if (record == null) {
                return 0;
            }
            record.analysisSummary = analysisSummary;
            record.updatedAt = "tick-" + tick++;
            return 1;
        }

        @Override
        public int updateBundleAnalysisStatus(Long bundleId, String analysisStatus, Long updatedBy) {
            BundleRecord record = bundles.get(bundleId);
            if (record == null) {
                return 0;
            }
            record.analysisStatus = analysisStatus;
            record.updatedAt = "tick-" + tick++;
            return 1;
        }

        @Override
        public int touchBundle(Long bundleId, Long updatedBy) {
            BundleRecord record = bundles.get(bundleId);
            if (record == null) {
                return 0;
            }
            record.updatedAt = "tick-" + tick++;
            return 1;
        }

        @Override
        public int deleteDraftEvidencesForBundle(Long bundleId) {
            List<Long> draftVersionIds = draftVersionIds(bundleId);
            evidences.entrySet().removeIf(entry -> draftVersionIds.contains(entry.getValue().quoteVersionId));
            return 1;
        }

        @Override
        public int deleteDraftRestrictionsForBundle(Long bundleId) {
            List<Long> draftVersionIds = draftVersionIds(bundleId);
            List<Long> draftServiceIds = serviceIdsForQuoteVersions(draftVersionIds);
            restrictions.entrySet().removeIf(entry -> draftServiceIds.contains(entry.getValue().serviceId));
            return 1;
        }

        @Override
        public int deleteDraftRulesForBundle(Long bundleId) {
            List<Long> draftVersionIds = draftVersionIds(bundleId);
            List<Long> draftServiceIds = serviceIdsForQuoteVersions(draftVersionIds);
            rules.entrySet().removeIf(entry -> draftServiceIds.contains(entry.getValue().serviceId));
            return 1;
        }

        @Override
        public int deleteDraftServicesForBundle(Long bundleId) {
            List<Long> draftVersionIds = draftVersionIds(bundleId);
            services.entrySet().removeIf(entry -> draftVersionIds.contains(entry.getValue().quoteVersionId));
            return 1;
        }

        @Override
        public int deleteDraftVersionsForBundle(Long bundleId) {
            quoteVersions.entrySet().removeIf(entry -> bundleId.equals(entry.getValue().bundleId) && "DRAFT".equals(entry.getValue().status));
            return 1;
        }

        @Override
        public int insertQuoteVersion(Long id, Long forwarderId, Long bundleId, String versionNo, String effectiveFrom, String status, String summary, Long updatedBy) {
            QuoteVersionRecord record = new QuoteVersionRecord();
            record.id = id;
            record.forwarderId = forwarderId;
            record.bundleId = bundleId;
            record.versionNo = versionNo;
            record.effectiveFrom = effectiveFrom;
            record.status = status;
            record.summary = summary;
            quoteVersions.put(id, record);
            return 1;
        }

        @Override
        public int insertForwarderService(Long id, Long quoteVersionId, String serviceName, String countryCode, String routeCode, String transportMode, String businessType, String serviceScope, String remarks, Long updatedBy) {
            ServiceRecord record = new ServiceRecord();
            record.id = id;
            record.quoteVersionId = quoteVersionId;
            record.serviceName = serviceName;
            record.countryCode = countryCode;
            record.routeCode = routeCode;
            record.transportMode = transportMode;
            record.businessType = businessType;
            record.serviceScope = serviceScope;
            record.remarks = remarks;
            services.put(id, record);
            return 1;
        }

        @Override
        public int insertQuoteRule(Long id, Long serviceId, String ruleName, String ruleType, String cargoCategory, String billingUnit, String currency, Double unitPrice, String calcBasis, String remarks, Long updatedBy) {
            RuleRecord record = new RuleRecord();
            record.id = id;
            record.serviceId = serviceId;
            record.ruleName = ruleName;
            record.ruleType = ruleType;
            record.cargoCategory = cargoCategory;
            record.billingUnit = billingUnit;
            record.currency = currency;
            record.unitPrice = unitPrice;
            record.calcBasis = calcBasis;
            record.remarks = remarks;
            rules.put(id, record);
            return 1;
        }

        @Override
        public int insertRestrictionRule(Long id, Long serviceId, String restrictionType, String operator, String value, String unit, String description, String severity, Long updatedBy) {
            RestrictionRecord record = new RestrictionRecord();
            record.id = id;
            record.serviceId = serviceId;
            record.restrictionType = restrictionType;
            record.operator = operator;
            record.value = value;
            record.unit = unit;
            record.description = description;
            record.severity = severity;
            restrictions.put(id, record);
            return 1;
        }

        @Override
        public int insertEvidenceRef(Long id, Long quoteVersionId, String targetType, Long targetId, String sourceType, Long sourceId, String locator, String evidenceText, Double confidenceScore, Long updatedBy) {
            EvidenceRecord record = new EvidenceRecord();
            record.id = id;
            record.quoteVersionId = quoteVersionId;
            record.targetType = targetType;
            record.targetId = targetId;
            record.sourceType = sourceType;
            record.sourceId = sourceId;
            record.locator = locator;
            record.evidenceText = evidenceText;
            evidences.put(id, record);
            return 1;
        }

        @Override
        public List<BundleListItemView> listBundles() {
            List<BundleListItemView> result = new ArrayList<>();
            for (BundleRecord record : bundles.values()) {
                BundleListItemView item = new BundleListItemView();
                item.setId(record.id);
                item.setBundleName(record.bundleName);
                item.setForwarderName(forwarders.get(record.forwarderId).name);
                item.setAnalysisStatus(record.analysisStatus);
                QuoteVersionRecord quoteVersion = latestQuoteVersionForBundle(record.id);
                item.setLatestVersionNo(quoteVersion == null ? "未生成" : quoteVersion.versionNo);
                item.setLatestVersionStatus(quoteVersion == null ? "SOURCE_ONLY" : quoteVersion.status);
                item.setFileCount((int) files.values().stream().filter(file -> record.id.equals(file.bundleId)).count());
                item.setNoteCount((int) notes.values().stream().filter(note -> record.id.equals(note.bundleId)).count());
                item.setUpdatedAt(record.updatedAt);
                result.add(0, item);
            }
            return result;
        }

        @Override
        public Integer countPublishedQuoteVersions() {
            return (int) quoteVersions.values().stream().filter(item -> "PUBLISHED".equals(item.status)).count();
        }

        @Override
        public Integer countQuoteRules() {
            return rules.size();
        }

        @Override
        public int upsertNumericAdjustment(
                Long id,
                Long quoteVersionId,
                String targetType,
                Long targetId,
                String fieldName,
                Double originalValue,
                Double adjustedValue,
                String currency,
                String reason,
                Long updatedBy
        ) {
            return 1;
        }

        @Override
        public Long selectActiveNumericAdjustmentId(String targetType, Long targetId, String fieldName) {
            return 930001L;
        }

        @Override
        public int insertNumericAdjustmentLog(
                Long id,
                Long adjustmentId,
                Long quoteVersionId,
                String targetType,
                Long targetId,
                String fieldName,
                Double beforeValue,
                Double afterValue,
                String actionType,
                String reason,
                Long operatedBy
        ) {
            return 1;
        }

        @Override
        public List<LogisticsQuoteOperationPriceItemView> listOperationPriceItems(
                String transportMode,
                Long forwarderId,
                String priceStatus
        ) {
            return List.of();
        }

        @Override
        public BundleDetailView selectBundleDetail(Long bundleId) {
            BundleRecord record = bundles.get(bundleId);
            if (record == null) {
                return null;
            }
            BundleDetailView detailView = new BundleDetailView();
            detailView.setId(record.id);
            detailView.setBundleName(record.bundleName);
            detailView.setAnalysisStatus(record.analysisStatus);
            detailView.setAnalysisSummary(record.analysisSummary);

            ForwarderRecord forwarderRecord = forwarders.get(record.forwarderId);
            ForwarderView forwarderView = new ForwarderView();
            forwarderView.setId(forwarderRecord.id);
            forwarderView.setName(forwarderRecord.name);
            forwarderView.setAlias(forwarderRecord.alias);
            forwarderView.setCompanyName(forwarderRecord.companyName);
            forwarderView.setNotes(forwarderRecord.notes);
            detailView.setForwarder(forwarderView);
            return detailView;
        }

        @Override
        public QuoteVersionView selectLatestQuoteVersionForBundle(Long bundleId) {
            QuoteVersionRecord record = latestQuoteVersionForBundle(bundleId);
            if (record == null) {
                return null;
            }
            QuoteVersionView view = new QuoteVersionView();
            view.setId(record.id);
            view.setVersionNo(record.versionNo);
            view.setStatus(record.status);
            view.setSummary(record.summary);
            view.setEffectiveFrom(record.effectiveFrom);
            return view;
        }

        @Override
        public List<SourceFileView> listSourceFiles(Long bundleId) {
            List<SourceFileView> result = new ArrayList<>();
            for (FileRecord record : files.values()) {
                if (!bundleId.equals(record.bundleId)) {
                    continue;
                }
                result.add(toSourceFileView(record));
            }
            return result;
        }

        @Override
        public SourceFileView selectSourceFileById(Long fileId) {
            FileRecord record = files.get(fileId);
            return record == null ? null : toSourceFileView(record);
        }

        @Override
        public SourceFileView selectSourceFile(Long bundleId, Long fileId) {
            FileRecord record = files.get(fileId);
            if (record == null || !bundleId.equals(record.bundleId)) {
                return null;
            }
            return toSourceFileView(record);
        }

        private SourceFileView toSourceFileView(FileRecord record) {
            SourceFileView item = new SourceFileView();
            item.setId(record.id);
            item.setFileName(record.fileName);
            item.setFileType(record.fileType);
            item.setFilePath(record.filePath);
            boolean archived = record.filePath != null && record.filePath.startsWith("archive://logistics-quotes/");
            item.setSourceLabel(archived ? "已归档原件" : record.filePath == null ? "录入元数据" : record.filePath);
            item.setArchived(archived);
            item.setArchiveUrl(Boolean.TRUE.equals(item.getArchived())
                    ? "/api/logistics-quote/source-files/" + record.id + "/archive"
                    : null);
            return item;
        }

        @Override
        public SourceNoteView selectSourceNote(Long bundleId, Long noteId) {
            NoteRecord record = notes.get(noteId);
            if (record == null || !bundleId.equals(record.bundleId)) {
                return null;
            }
            SourceNoteView view = new SourceNoteView();
            view.setId(record.id);
            view.setNoteType(record.noteType);
            view.setSourceChannel(record.sourceChannel);
            view.setContent(record.content);
            return view;
        }

        @Override
        public List<ServiceView> listServicesForQuoteVersion(Long quoteVersionId) {
            List<ServiceView> result = new ArrayList<>();
            for (ServiceRecord record : services.values()) {
                if (!quoteVersionId.equals(record.quoteVersionId)) {
                    continue;
                }
                ServiceView view = new ServiceView();
                view.setServiceName(record.serviceName);
                view.setCountryCode(record.countryCode);
                view.setRouteCode(record.routeCode);
                view.setTransportMode(record.transportMode);
                view.setBusinessType(record.businessType);
                view.setServiceScope(record.serviceScope);
                view.setRemarks(record.remarks);
                result.add(view);
            }
            return result;
        }

        @Override
        public List<RuleView> listRulesForQuoteVersion(Long quoteVersionId) {
            List<RuleView> result = new ArrayList<>();
            for (RuleRecord record : rules.values()) {
                ServiceRecord service = services.get(record.serviceId);
                if (service == null || !quoteVersionId.equals(service.quoteVersionId)) {
                    continue;
                }
                RuleView view = new RuleView();
                view.setServiceName(service.serviceName);
                view.setRuleName(record.ruleName);
                view.setRuleType(record.ruleType);
                view.setCargoCategory(record.cargoCategory);
                view.setBillingUnit(record.billingUnit);
                view.setCurrency(record.currency);
                view.setUnitPrice(record.unitPrice);
                view.setCalcBasis(record.calcBasis);
                view.setSummary(record.remarks);
                result.add(view);
            }
            return result;
        }

        @Override
        public List<RestrictionView> listRestrictionsForQuoteVersion(Long quoteVersionId) {
            List<RestrictionView> result = new ArrayList<>();
            for (RestrictionRecord record : restrictions.values()) {
                ServiceRecord service = services.get(record.serviceId);
                if (service == null || !quoteVersionId.equals(service.quoteVersionId)) {
                    continue;
                }
                RestrictionView view = new RestrictionView();
                view.setServiceName(service.serviceName);
                view.setRestrictionType(record.restrictionType);
                view.setOperator(record.operator);
                view.setValue(record.value);
                view.setUnit(record.unit);
                view.setSeverity(record.severity);
                view.setDescription(record.description);
                result.add(view);
            }
            return result;
        }

        @Override
        public List<EvidenceView> listEvidencesForQuoteVersion(Long quoteVersionId) {
            List<EvidenceView> result = new ArrayList<>();
            for (EvidenceRecord record : evidences.values()) {
                if (!quoteVersionId.equals(record.quoteVersionId)) {
                    continue;
                }
                EvidenceView view = new EvidenceView();
                view.setTargetType(record.targetType);
                view.setTargetName(resolveEvidenceTargetName(record));
                view.setSourceType(record.sourceType);
                NoteRecord sourceNote = notes.get(record.sourceId);
                view.setSourceName(sourceNote == null ? String.valueOf(record.sourceId) : sourceNote.sourceChannel);
                view.setLocator(record.locator);
                view.setEvidenceText(record.evidenceText);
                result.add(view);
            }
            return result;
        }

        @Override
        public List<SourceNoteView> listSourceNotes(Long bundleId) {
            List<SourceNoteView> result = new ArrayList<>();
            for (NoteRecord record : notes.values()) {
                if (!bundleId.equals(record.bundleId)) {
                    continue;
                }
                SourceNoteView item = new SourceNoteView();
                item.setId(record.id);
                item.setNoteType(record.noteType);
                item.setSourceChannel(record.sourceChannel);
                item.setContent(record.content);
                result.add(item);
            }
            return result;
        }

        private List<Long> draftVersionIds(Long bundleId) {
            return quoteVersions.values().stream()
                    .filter(version -> bundleId.equals(version.bundleId) && "DRAFT".equals(version.status))
                    .map(version -> version.id)
                    .collect(Collectors.toList());
        }

        private List<Long> serviceIdsForQuoteVersions(List<Long> quoteVersionIds) {
            return services.values().stream()
                    .filter(service -> quoteVersionIds.contains(service.quoteVersionId))
                    .map(service -> service.id)
                    .collect(Collectors.toList());
        }

        private QuoteVersionRecord latestQuoteVersionForBundle(Long bundleId) {
            return quoteVersions.values().stream()
                    .filter(version -> bundleId.equals(version.bundleId))
                    .reduce((previous, current) -> previous.id > current.id ? previous : current)
                    .orElse(null);
        }

        private String resolveEvidenceTargetName(EvidenceRecord record) {
            if ("RULE".equals(record.targetType)) {
                RuleRecord rule = rules.get(record.targetId);
                return rule == null ? String.valueOf(record.targetId) : rule.ruleName;
            }
            if ("RESTRICTION".equals(record.targetType)) {
                RestrictionRecord restriction = restrictions.get(record.targetId);
                return restriction == null ? String.valueOf(record.targetId) : restriction.restrictionType;
            }
            return String.valueOf(record.targetId);
        }

        private static final class ForwarderRecord {
            private Long id;
            private String name;
            private String alias;
            private String companyName;
            private String notes;
        }

        private static final class BundleRecord {
            private Long id;
            private Long forwarderId;
            private String bundleName;
            private String analysisStatus;
            private String analysisSummary;
            private String updatedAt;
        }

        private static final class FileRecord {
            private Long id;
            private Long bundleId;
            private String fileName;
            private String fileType;
            private String filePath;
        }

        private static final class NoteRecord {
            private Long id;
            private Long bundleId;
            private String noteType;
            private String sourceChannel;
            private String content;
        }

        private static final class QuoteVersionRecord {
            private Long id;
            private Long forwarderId;
            private Long bundleId;
            private String versionNo;
            private String effectiveFrom;
            private String status;
            private String summary;
        }

        private static final class ServiceRecord {
            private Long id;
            private Long quoteVersionId;
            private String serviceName;
            private String countryCode;
            private String routeCode;
            private String transportMode;
            private String businessType;
            private String serviceScope;
            private String remarks;
        }

        private static final class RuleRecord {
            private Long id;
            private Long serviceId;
            private String ruleName;
            private String ruleType;
            private String cargoCategory;
            private String billingUnit;
            private String currency;
            private Double unitPrice;
            private String calcBasis;
            private String remarks;
        }

        private static final class RestrictionRecord {
            private Long id;
            private Long serviceId;
            private String restrictionType;
            private String operator;
            private String value;
            private String unit;
            private String description;
            private String severity;
        }

        private static final class EvidenceRecord {
            private Long id;
            private Long quoteVersionId;
            private String targetType;
            private Long targetId;
            private String sourceType;
            private Long sourceId;
            private String locator;
            private String evidenceText;
        }
    }
}
