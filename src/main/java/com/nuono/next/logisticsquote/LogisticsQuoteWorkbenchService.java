package com.nuono.next.logisticsquote;

import com.nuono.next.infrastructure.mapper.LogisticsQuoteMapper;
import com.nuono.next.logisticsquote.LogisticsQuoteNotePreviewView.RestrictionPreviewView;
import com.nuono.next.logisticsquote.LogisticsQuoteNotePreviewView.RulePreviewView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.BundleDetailView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.BundleListItemView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.EvidenceView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.ForwarderView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.QuoteVersionView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.ReputationSignalView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.ReputationSnapshotView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.RestrictionView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.RuleView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.ServiceView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.SourceFileView;
import com.nuono.next.logisticsquote.LogisticsQuoteWorkbenchView.SourceNoteView;
import com.nuono.next.system.LocalDbBootstrapStatusService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LogisticsQuoteWorkbenchService {

    private static final long SYSTEM_USER_ID = 1L;

    private static final int REQUIRED_SOURCE_TABLE_COUNT = 4;

    private static final int REQUIRED_QUOTE_DRAFT_TABLE_COUNT = 5;

    private static final long MAX_ARCHIVE_FILE_BYTES = 30L * 1024L * 1024L;

    private static final String ARCHIVE_URI_PREFIX = "archive://logistics-quotes/";

    private static final Set<String> ALLOWED_ARCHIVE_EXTENSIONS = Set.of(
            "pdf", "xls", "xlsx", "csv", "txt", "doc", "docx", "png", "jpg", "jpeg", "webp"
    );

    private final LogisticsQuoteNoteInterpreter logisticsQuoteNoteInterpreter;
    private final ObjectProvider<LogisticsQuoteMapper> logisticsQuoteMapperProvider;
    private final ObjectProvider<LocalDbBootstrapStatusService> localDbBootstrapStatusServiceProvider;

    public LogisticsQuoteWorkbenchService(
            LogisticsQuoteNoteInterpreter logisticsQuoteNoteInterpreter,
            ObjectProvider<LogisticsQuoteMapper> logisticsQuoteMapperProvider,
            ObjectProvider<LocalDbBootstrapStatusService> localDbBootstrapStatusServiceProvider
    ) {
        this.logisticsQuoteNoteInterpreter = logisticsQuoteNoteInterpreter;
        this.logisticsQuoteMapperProvider = logisticsQuoteMapperProvider;
        this.localDbBootstrapStatusServiceProvider = localDbBootstrapStatusServiceProvider;
    }

    public LogisticsQuoteWorkbenchView buildWorkbench(Long selectedBundleId, Long selectedNoteId, Long selectedFileId) {
        if (isLocalDbSourcePersistenceReady()) {
            return buildDbWorkbench(selectedBundleId, selectedNoteId, selectedFileId);
        }

        Map<Long, BundleDetailView> sampleBundles = buildSampleBundles();
        LogisticsQuoteWorkbenchView view = new LogisticsQuoteWorkbenchView();
        view.setMode("sample-only");
        view.setReady(true);
        view.setMessage("这版先用本地样本验证物流报价的来源归档、规则标准化和补充文案预览，不先做文件上传/OCR。");

        List<BundleListItemView> bundleItems = new ArrayList<>();
        int totalRules = 0;
        for (BundleDetailView bundle : sampleBundles.values()) {
            BundleListItemView item = new BundleListItemView();
            item.setId(bundle.getId());
            item.setBundleName(bundle.getBundleName());
            item.setForwarderName(bundle.getForwarder().getName());
            item.setAnalysisStatus(bundle.getAnalysisStatus());
            item.setLatestVersionNo(bundle.getQuoteVersion().getVersionNo());
            item.setLatestVersionStatus(bundle.getQuoteVersion().getStatus());
            item.setRecommendationLevel(bundle.getReputationSnapshot().getRecommendationLevel());
            item.setFileCount(bundle.getFiles().size());
            item.setNoteCount(bundle.getNotes().size());
            item.setUpdatedAt(bundle.getQuoteVersion().getEffectiveFrom());
            totalRules += bundle.getRules().size();
            bundleItems.add(item);
        }

        LogisticsQuoteWorkbenchView.SummaryView summary = new LogisticsQuoteWorkbenchView.SummaryView();
        summary.setTotalForwarders((int) sampleBundles.values().stream().map(bundle -> bundle.getForwarder().getName()).distinct().count());
        summary.setTotalBundles(sampleBundles.size());
        summary.setPublishedVersions((int) sampleBundles.values().stream().filter(bundle -> "PUBLISHED".equals(bundle.getQuoteVersion().getStatus())).count());
        summary.setTotalRules(totalRules);

        BundleDetailView selected = sampleBundles.get(selectedBundleId);
        if (selected == null && !sampleBundles.isEmpty()) {
            selected = sampleBundles.values().iterator().next();
        }
        if (selected != null) {
            selected.setSelectedNoteId(resolveSelectedNoteId(selectedNoteId, selected.getNotes()));
            selected.setSelectedFileId(resolveSelectedFileId(selectedFileId, selected.getFiles()));
            selected.setSourceReadbackHint("当前详情来自样本工作台，用于验证来源包形态；不代表这些文件、文案、规则或风评已经真实入库。");
        }

        view.setSummary(summary);
        view.setBundles(bundleItems);
        view.setSelectedBundle(selected);
        view.setSelectedBundleId(selected == null ? null : selected.getId());
        return view;
    }

    @Transactional
    public LogisticsQuoteWorkbenchView createSourceBundle(LogisticsQuoteSourceBundleCreateCommand command) {
        LogisticsQuoteMapper mapper = requireDbPersistence();
        if (command == null) {
            throw new IllegalArgumentException("请先填写货代和来源包信息。");
        }

        String forwarderName = requireText(command.getForwarderName(), "请先填写货代名称。");
        String bundleName = requireText(command.getBundleName(), "请先填写来源包名称。");
        List<LogisticsQuoteSourceBundleCreateCommand.SourceFileInput> files =
                Optional.ofNullable(command.getFiles()).orElse(Collections.emptyList());
        List<LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput> notes =
                Optional.ofNullable(command.getNotes()).orElse(Collections.emptyList());
        if (files.isEmpty()) {
            throw new IllegalArgumentException("至少录入一条来源文件元数据后再保存来源包。");
        }
        if (notes.isEmpty()) {
            throw new IllegalArgumentException("至少录入一条补充文案或分析备注后再保存来源包。");
        }

        Long existingForwarderId = mapper.selectForwarderIdByName(forwarderName);
        Long forwarderId = existingForwarderId == null ? mapper.nextForwarderId() : existingForwarderId;
        mapper.upsertForwarder(
                forwarderId,
                forwarderName,
                normalize(command.getForwarderAlias()),
                normalize(command.getCompanyName()),
                "ACTIVE",
                normalize(command.getForwarderNotes()),
                SYSTEM_USER_ID
        );

        Long bundleId = mapper.nextBundleId();
        mapper.insertBundle(
                bundleId,
                forwarderId,
                bundleName,
                defaultIfBlank(command.getAnalysisStatus(), "DRAFT"),
                normalize(command.getAnalysisSummary()),
                SYSTEM_USER_ID
        );

        for (LogisticsQuoteSourceBundleCreateCommand.SourceFileInput file : files) {
            if (file == null) {
                continue;
            }
            String fileName = normalize(file.getFileName());
            if (!StringUtils.hasText(fileName)) {
                continue;
            }
            mapper.insertSourceFile(
                    mapper.nextFileId(),
                    bundleId,
                    fileName,
                    defaultIfBlank(file.getFileType(), inferFileType(fileName)),
                    normalize(file.getFilePath()),
                    SYSTEM_USER_ID
            );
        }

        Long selectedNoteId = null;
        for (LogisticsQuoteSourceBundleCreateCommand.SourceNoteInput note : notes) {
            if (note == null) {
                continue;
            }
            String content = normalize(note.getContent());
            if (!StringUtils.hasText(content)) {
                continue;
            }
            Long noteId = mapper.nextNoteId();
            mapper.insertSourceNote(
                    noteId,
                    bundleId,
                    defaultIfBlank(note.getNoteType(), "manual_note"),
                    defaultIfBlank(note.getSourceChannel(), "manual"),
                    content,
                    normalize(note.getAuthorName()),
                    SYSTEM_USER_ID
            );
            if (selectedNoteId == null) {
                selectedNoteId = noteId;
            }
        }

        LogisticsQuoteWorkbenchView refreshedView = buildDbWorkbench(bundleId, selectedNoteId, null);
        refreshedView.setMessage("来源包已写入本地库，当前工作台优先回读真实存储的文件与补充文案元数据。");
        return refreshedView;
    }

    public LogisticsQuoteNotePreviewView previewNote(LogisticsQuoteNotePreviewCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("请先输入补充文案，再执行规则预览。");
        }
        return logisticsQuoteNoteInterpreter.interpret(command.getNoteText());
    }

    @Transactional
    public LogisticsQuoteWorkbenchView createQuoteDraftFromNote(
            Long bundleId,
            Long selectedFileId,
            LogisticsQuoteDraftFromNoteCommand command
    ) {
        LogisticsQuoteMapper mapper = requireQuoteDraftPersistence();
        if (bundleId == null) {
            throw new IllegalArgumentException("请先选择一个已保存的来源包。");
        }
        if (command == null || command.getNoteId() == null) {
            throw new IllegalArgumentException("请先选择要转成报价草稿的补充文案。");
        }
        BundleDetailView bundle = mapper.selectBundleDetail(bundleId);
        if (bundle == null || bundle.getForwarder() == null || bundle.getForwarder().getId() == null) {
            throw new IllegalArgumentException("未找到要生成报价草稿的来源包，请先刷新工作台后重试。");
        }
        SourceNoteView sourceNote = mapper.selectSourceNote(bundleId, command.getNoteId());
        if (sourceNote == null) {
            throw new IllegalArgumentException("未找到要转成报价草稿的补充文案，请先刷新工作台后重试。");
        }

        LogisticsQuoteNotePreviewView previewView = logisticsQuoteNoteInterpreter.interpret(sourceNote.getContent());
        if (previewView.getRulePreviews().isEmpty() && previewView.getRestrictionPreviews().isEmpty()) {
            throw new IllegalArgumentException("当前补充文案没有可保存的规则或限制预览，请先补充金额、单位或限制阈值。");
        }

        mapper.deleteDraftEvidencesForBundle(bundleId);
        mapper.deleteDraftRestrictionsForBundle(bundleId);
        mapper.deleteDraftRulesForBundle(bundleId);
        mapper.deleteDraftServicesForBundle(bundleId);
        mapper.deleteDraftVersionsForBundle(bundleId);

        Long quoteVersionId = mapper.nextQuoteVersionId();
        String versionNo = defaultIfBlank(command.getVersionNo(), "DRAFT-" + bundleId + "-" + command.getNoteId());
        String summary = defaultIfBlank(
                command.getSummary(),
                "由补充文案 #" + command.getNoteId() + " 生成的结构化报价草稿，待人工确认发布。"
        );
        mapper.insertQuoteVersion(
                quoteVersionId,
                bundle.getForwarder().getId(),
                bundleId,
                versionNo,
                normalize(command.getEffectiveFrom()),
                "DRAFT",
                summary,
                SYSTEM_USER_ID
        );

        Long serviceId = mapper.nextServiceId();
        String serviceName = requireText(command.getServiceName(), "请先填写这批规则所属的服务名称。");
        mapper.insertForwarderService(
                serviceId,
                quoteVersionId,
                serviceName,
                normalize(command.getCountryCode()),
                normalize(command.getRouteCode()),
                defaultIfBlank(command.getTransportMode(), "SEA"),
                defaultIfBlank(command.getBusinessType(), "B2B"),
                defaultIfBlank(command.getServiceScope(), "FIRST_LEG"),
                "由补充文案预览生成，需人工确认后发布。",
                SYSTEM_USER_ID
        );

        String currency = defaultIfBlank(command.getCurrency(), "CNY");
        for (RulePreviewView rulePreview : previewView.getRulePreviews()) {
            Long ruleId = mapper.nextQuoteRuleId();
            mapper.insertQuoteRule(
                    ruleId,
                    serviceId,
                    defaultIfBlank(rulePreview.getRuleName(), "补充文案规则"),
                    defaultIfBlank(rulePreview.getRuleType(), "MANUAL_NOTE_DERIVED"),
                    normalize(rulePreview.getTriggerCondition()),
                    normalize(rulePreview.getBillingUnit()),
                    currency,
                    rulePreview.getUnitPrice(),
                    "MANUAL_NOTE",
                    normalize(rulePreview.getSummary()),
                    SYSTEM_USER_ID
            );
            mapper.insertEvidenceRef(
                    mapper.nextEvidenceId(),
                    quoteVersionId,
                    "RULE",
                    ruleId,
                    "NOTE",
                    command.getNoteId(),
                    "quote_source_note#" + command.getNoteId(),
                    normalize(rulePreview.getSummary()),
                    0.90d,
                    SYSTEM_USER_ID
            );
        }

        for (RestrictionPreviewView restrictionPreview : previewView.getRestrictionPreviews()) {
            Long restrictionId = mapper.nextRestrictionId();
            mapper.insertRestrictionRule(
                    restrictionId,
                    serviceId,
                    defaultIfBlank(restrictionPreview.getRestrictionType(), "MANUAL_NOTE_RESTRICTION"),
                    normalize(restrictionPreview.getOperator()),
                    normalize(restrictionPreview.getValue()),
                    normalize(restrictionPreview.getUnit()),
                    normalize(restrictionPreview.getDescription()),
                    defaultIfBlank(restrictionPreview.getSeverity(), "SOFT"),
                    SYSTEM_USER_ID
            );
            mapper.insertEvidenceRef(
                    mapper.nextEvidenceId(),
                    quoteVersionId,
                    "RESTRICTION",
                    restrictionId,
                    "NOTE",
                    command.getNoteId(),
                    "quote_source_note#" + command.getNoteId(),
                    normalize(restrictionPreview.getDescription()),
                    0.90d,
                    SYSTEM_USER_ID
            );
        }

        mapper.updateBundleAnalysisStatus(bundleId, "READY_FOR_REVIEW", SYSTEM_USER_ID);

        LogisticsQuoteWorkbenchView refreshedView = buildDbWorkbench(bundleId, command.getNoteId(), selectedFileId);
        refreshedView.setMessage("已把补充文案预览保存为结构化报价草稿；当前服务、规则、限制和证据均从本地库回读，仍需人工确认后发布。");
        return refreshedView;
    }

    @Transactional
    public LogisticsQuoteWorkbenchView appendSourceBundleNote(
            Long bundleId,
            Long selectedFileId,
            LogisticsQuoteSourceBundleNoteCreateCommand command
    ) {
        LogisticsQuoteMapper mapper = requireDbPersistence();
        if (bundleId == null) {
            throw new IllegalArgumentException("请先选择一个已保存的来源包。");
        }
        if (mapper.selectBundleDetail(bundleId) == null) {
            throw new IllegalArgumentException("未找到要追加补充文案的来源包，请先刷新工作台后重试。");
        }
        if (command == null) {
            throw new IllegalArgumentException("请先填写要追加的补充文案。");
        }

        String content = requireText(command.getContent(), "追加补充文案内容不能为空。");
        Long noteId = mapper.nextNoteId();
        mapper.insertSourceNote(
                noteId,
                bundleId,
                defaultIfBlank(command.getNoteType(), "manual_note"),
                defaultIfBlank(command.getSourceChannel(), "manual"),
                content,
                normalize(command.getAuthorName()),
                SYSTEM_USER_ID
        );
        mapper.touchBundle(bundleId, SYSTEM_USER_ID);

        LogisticsQuoteWorkbenchView refreshedView = buildDbWorkbench(bundleId, noteId, selectedFileId);
        refreshedView.setMessage("补充文案已追加，当前工作台已从本地库重新回读新增 note 并自动切到这条保存记录。");
        return refreshedView;
    }

    @Transactional
    public LogisticsQuoteWorkbenchView appendSourceBundleFile(
            Long bundleId,
            Long selectedNoteId,
            LogisticsQuoteSourceBundleFileCreateCommand command
    ) {
        LogisticsQuoteMapper mapper = requireDbPersistence();
        if (bundleId == null) {
            throw new IllegalArgumentException("请先选择一个已保存的来源包。");
        }
        if (mapper.selectBundleDetail(bundleId) == null) {
            throw new IllegalArgumentException("未找到要追加文件元数据的来源包，请先刷新工作台后重试。");
        }
        if (command == null) {
            throw new IllegalArgumentException("请先填写要追加的来源文件元数据。");
        }

        String fileName = requireText(command.getFileName(), "来源文件名不能为空。");
        Long fileId = mapper.nextFileId();
        mapper.insertSourceFile(
                fileId,
                bundleId,
                fileName,
                defaultIfBlank(command.getFileType(), inferFileType(fileName)),
                normalize(command.getFilePath()),
                SYSTEM_USER_ID
        );
        mapper.touchBundle(bundleId, SYSTEM_USER_ID);

        LogisticsQuoteWorkbenchView refreshedView = buildDbWorkbench(bundleId, selectedNoteId, fileId);
        refreshedView.setMessage("来源文件元数据已追加，当前工作台已从本地库重新回读新增 file 记录。");
        return refreshedView;
    }

    @Transactional
    public LogisticsQuoteWorkbenchView archiveSourceBundleFile(
            Long bundleId,
            Long selectedNoteId,
            Long fileId,
            MultipartFile file
    ) {
        LogisticsQuoteMapper mapper = requireDbPersistence();
        if (bundleId == null) {
            throw new IllegalArgumentException("请先选择一个已保存的来源包。");
        }
        if (mapper.selectBundleDetail(bundleId) == null) {
            throw new IllegalArgumentException("未找到要归档文件的来源包，请先刷新工作台后重试。");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要归档的报价文件。");
        }
        if (file.getSize() > MAX_ARCHIVE_FILE_BYTES) {
            throw new IllegalArgumentException("归档文件不能超过 30MB。");
        }

        String originalFileName = safeOriginalFileName(file);
        String fileType = requireArchiveFileType(originalFileName);
        Long targetFileId = fileId;
        if (targetFileId != null && mapper.selectSourceFile(bundleId, targetFileId) == null) {
            throw new IllegalArgumentException("未找到要绑定归档原件的来源文件记录，请先刷新工作台后重试。");
        }
        if (targetFileId == null) {
            targetFileId = mapper.nextFileId();
        }

        String archiveUri = saveArchiveFile(bundleId, targetFileId, originalFileName, file);
        try {
            if (fileId == null) {
                mapper.insertSourceFile(
                        targetFileId,
                        bundleId,
                        originalFileName,
                        fileType,
                        archiveUri,
                        SYSTEM_USER_ID
                );
            } else {
                int updatedRows = mapper.updateSourceFileMetadata(
                        bundleId,
                        targetFileId,
                        originalFileName,
                        fileType,
                        archiveUri,
                        SYSTEM_USER_ID
                );
                if (updatedRows == 0) {
                    throw new IllegalArgumentException("未找到要绑定归档原件的来源文件记录，请先刷新工作台后重试。");
                }
            }
            mapper.touchBundle(bundleId, SYSTEM_USER_ID);
        } catch (RuntimeException exception) {
            deleteArchivedFileQuietly(archiveUri);
            throw exception;
        }

        LogisticsQuoteWorkbenchView refreshedView = buildDbWorkbench(bundleId, selectedNoteId, targetFileId);
        refreshedView.setMessage("报价文件原件已归档到本地文件库；当前不做解析或 OCR，只管理原始文件与来源记录。");
        return refreshedView;
    }

    public LogisticsQuoteArchivedFile resolveArchivedSourceFile(Long fileId) {
        LogisticsQuoteMapper mapper = requireDbPersistence();
        if (fileId == null) {
            throw new IllegalArgumentException("请选择要下载的归档文件。");
        }
        SourceFileView sourceFile = mapper.selectSourceFileById(fileId);
        if (sourceFile == null || !isArchiveUri(sourceFile.getFilePath())) {
            throw new IllegalArgumentException("当前来源文件没有已归档原件。");
        }
        Path archivedPath = archiveUriToPath(sourceFile.getFilePath());
        if (!Files.exists(archivedPath) || !Files.isRegularFile(archivedPath)) {
            throw new IllegalArgumentException("归档文件不存在，请重新上传。");
        }
        return new LogisticsQuoteArchivedFile(archivedPath, sourceFile.getFileName());
    }

    @Transactional
    public LogisticsQuoteWorkbenchView updateSourceBundleNote(
            Long bundleId,
            Long selectedFileId,
            LogisticsQuoteSourceBundleNoteUpdateCommand command
    ) {
        LogisticsQuoteMapper mapper = requireDbPersistence();
        if (bundleId == null) {
            throw new IllegalArgumentException("请先选择一个已保存的来源包。");
        }
        if (command == null || command.getNoteId() == null) {
            throw new IllegalArgumentException("请先指定要更新的补充文案。");
        }
        String content = requireText(command.getContent(), "补充文案内容不能为空。");
        int updatedRows = mapper.updateSourceNoteContent(bundleId, command.getNoteId(), content, SYSTEM_USER_ID);
        if (updatedRows <= 0) {
            throw new IllegalArgumentException("未找到可更新的补充文案，请先刷新工作台后重试。");
        }
        mapper.touchBundle(bundleId, SYSTEM_USER_ID);

        LogisticsQuoteWorkbenchView refreshedView = buildDbWorkbench(bundleId, command.getNoteId(), selectedFileId);
        refreshedView.setMessage("补充文案已更新，当前工作台已从本地库重新回读保存后的来源内容。");
        return refreshedView;
    }

    @Transactional
    public LogisticsQuoteWorkbenchView updateSourceBundleFile(
            Long bundleId,
            Long selectedNoteId,
            Long selectedFileId,
            LogisticsQuoteSourceBundleFileUpdateCommand command
    ) {
        LogisticsQuoteMapper mapper = requireDbPersistence();
        if (bundleId == null) {
            throw new IllegalArgumentException("请先选择一个已保存的来源包。");
        }
        if (command == null || command.getFileId() == null) {
            throw new IllegalArgumentException("请先指定要更新的来源文件。");
        }
        String fileName = requireText(command.getFileName(), "来源文件名不能为空。");
        int updatedRows = mapper.updateSourceFileMetadata(
                bundleId,
                command.getFileId(),
                fileName,
                defaultIfBlank(command.getFileType(), inferFileType(fileName)),
                normalize(command.getFilePath()),
                SYSTEM_USER_ID
        );
        if (updatedRows <= 0) {
            throw new IllegalArgumentException("未找到可更新的来源文件，请先刷新工作台后重试。");
        }
        mapper.touchBundle(bundleId, SYSTEM_USER_ID);

        LogisticsQuoteWorkbenchView refreshedView = buildDbWorkbench(bundleId, selectedNoteId, selectedFileId);
        refreshedView.setMessage("来源文件元数据已更新，当前工作台已从本地库重新回读保存后的 file 记录。");
        return refreshedView;
    }

    @Transactional
    public LogisticsQuoteWorkbenchView updateSourceBundleAnalysisSummary(
            Long bundleId,
            Long selectedNoteId,
            Long selectedFileId,
            LogisticsQuoteSourceBundleAnalysisSummaryUpdateCommand command
    ) {
        LogisticsQuoteMapper mapper = requireDbPersistence();
        if (bundleId == null) {
            throw new IllegalArgumentException("请先选择一个已保存的来源包。");
        }
        if (command == null) {
            throw new IllegalArgumentException("请先填写来源分析摘要。");
        }
        String analysisSummary = requireText(command.getAnalysisSummary(), "来源分析摘要不能为空。");
        int updatedRows = mapper.updateBundleAnalysisSummary(bundleId, analysisSummary, SYSTEM_USER_ID);
        if (updatedRows <= 0) {
            throw new IllegalArgumentException("未找到可更新的来源包，请先刷新工作台后重试。");
        }

        LogisticsQuoteWorkbenchView refreshedView = buildDbWorkbench(bundleId, selectedNoteId, selectedFileId);
        refreshedView.setMessage("来源分析摘要已更新，当前工作台已从本地库重新回读保存后的 bundle 摘要。");
        return refreshedView;
    }

    private LogisticsQuoteWorkbenchView buildDbWorkbench(Long selectedBundleId, Long selectedNoteId, Long selectedFileId) {
        LogisticsQuoteMapper mapper = requireDbPersistence();
        List<BundleListItemView> bundles = mapper.listBundles();

        LogisticsQuoteWorkbenchView view = new LogisticsQuoteWorkbenchView();
        view.setMode("local-db");
        view.setReady(true);

        LogisticsQuoteWorkbenchView.SummaryView summary = new LogisticsQuoteWorkbenchView.SummaryView();
        summary.setTotalBundles(bundles.size());
        summary.setTotalForwarders((int) bundles.stream()
                .map(BundleListItemView::getForwarderName)
                .filter(StringUtils::hasText)
                .distinct()
                .count());
        if (isLocalDbQuoteDraftPersistenceReady()) {
            summary.setPublishedVersions(Optional.ofNullable(mapper.countPublishedQuoteVersions()).orElse(0));
            summary.setTotalRules(Optional.ofNullable(mapper.countQuoteRules()).orElse(0));
        } else {
            summary.setPublishedVersions(0);
            summary.setTotalRules(0);
        }
        view.setSummary(summary);
        view.setBundles(bundles);

        if (bundles.isEmpty()) {
            view.setMessage("本地来源层表已可用，当前还没有真实来源包；可先录入文件元数据和补充文案完成第一条持久化链路。");
            view.setSelectedBundleId(null);
            view.setSelectedBundle(null);
            return view;
        }

        Long resolvedBundleId = resolveSelectedBundleId(selectedBundleId, bundles);
        BundleDetailView selected = mapper.selectBundleDetail(resolvedBundleId);
        if (selected != null) {
            selected.setFiles(mapper.listSourceFiles(resolvedBundleId));
            selected.setNotes(mapper.listSourceNotes(resolvedBundleId));
            selected.setSelectedNoteId(resolveSelectedNoteId(selectedNoteId, selected.getNotes()));
            selected.setSelectedFileId(resolveSelectedFileId(selectedFileId, selected.getFiles()));
            QuoteVersionView quoteVersion = isLocalDbQuoteDraftPersistenceReady()
                    ? mapper.selectLatestQuoteVersionForBundle(resolvedBundleId)
                    : null;
            if (quoteVersion == null) {
                selected.setQuoteVersion(buildSourceOnlyVersion(selected));
            } else {
                selected.setQuoteVersion(quoteVersion);
                selected.setServices(mapper.listServicesForQuoteVersion(quoteVersion.getId()));
                selected.setRules(mapper.listRulesForQuoteVersion(quoteVersion.getId()));
                selected.setRestrictions(mapper.listRestrictionsForQuoteVersion(quoteVersion.getId()));
                selected.setEvidences(mapper.listEvidencesForQuoteVersion(quoteVersion.getId()));
            }
            selected.setSourceReadbackHint(buildDbSourceReadbackHint(selected));
        }

        view.setMessage("当前工作台优先展示本地库里已保存的来源包元数据；结构化报价草稿会从本地库回读，风评仍保留在后续阶段。");
        view.setSelectedBundleId(resolvedBundleId);
        view.setSelectedBundle(selected);
        return view;
    }

    private boolean isLocalDbSourcePersistenceReady() {
        LogisticsQuoteMapper mapper = logisticsQuoteMapperProvider.getIfAvailable();
        LocalDbBootstrapStatusService bootstrapStatusService = localDbBootstrapStatusServiceProvider.getIfAvailable();
        if (mapper == null || bootstrapStatusService == null) {
            return false;
        }
        Integer existingTableCount = mapper.countExistingSourceTables(bootstrapStatusService.inspect().getSchema());
        return existingTableCount != null && existingTableCount >= REQUIRED_SOURCE_TABLE_COUNT;
    }

    private boolean isLocalDbQuoteDraftPersistenceReady() {
        LogisticsQuoteMapper mapper = logisticsQuoteMapperProvider.getIfAvailable();
        LocalDbBootstrapStatusService bootstrapStatusService = localDbBootstrapStatusServiceProvider.getIfAvailable();
        if (mapper == null || bootstrapStatusService == null || !isLocalDbSourcePersistenceReady()) {
            return false;
        }
        Integer existingTableCount = mapper.countExistingQuoteDraftTables(bootstrapStatusService.inspect().getSchema());
        return existingTableCount != null && existingTableCount >= REQUIRED_QUOTE_DRAFT_TABLE_COUNT;
    }

    private LogisticsQuoteMapper requireDbPersistence() {
        LogisticsQuoteMapper mapper = logisticsQuoteMapperProvider.getIfAvailable();
        if (mapper == null || !isLocalDbSourcePersistenceReady()) {
            throw new IllegalArgumentException("当前本地库还没有准备好物流报价来源层表，请先执行 000_local_dev_bootstrap.sql 并用 local-db profile 启动后端。");
        }
        return mapper;
    }

    private LogisticsQuoteMapper requireQuoteDraftPersistence() {
        LogisticsQuoteMapper mapper = requireDbPersistence();
        if (!isLocalDbQuoteDraftPersistenceReady()) {
            throw new IllegalArgumentException("当前本地库还没有准备好结构化报价草稿表，请先执行最新的 000_local_dev_bootstrap.sql。");
        }
        return mapper;
    }

    private Long resolveSelectedBundleId(Long selectedBundleId, List<BundleListItemView> bundles) {
        if (selectedBundleId != null && bundles.stream().map(BundleListItemView::getId).anyMatch(selectedBundleId::equals)) {
            return selectedBundleId;
        }
        return bundles.get(0).getId();
    }

    private Long resolveSelectedNoteId(Long selectedNoteId, List<SourceNoteView> notes) {
        List<SourceNoteView> safeNotes = Optional.ofNullable(notes).orElse(Collections.emptyList());
        if (safeNotes.isEmpty()) {
            return null;
        }
        if (selectedNoteId != null && safeNotes.stream().map(SourceNoteView::getId).anyMatch(selectedNoteId::equals)) {
            return selectedNoteId;
        }
        return safeNotes.get(0).getId();
    }

    private Long resolveSelectedFileId(Long selectedFileId, List<SourceFileView> files) {
        List<SourceFileView> safeFiles = Optional.ofNullable(files).orElse(Collections.emptyList());
        if (safeFiles.isEmpty()) {
            return null;
        }
        if (selectedFileId != null && safeFiles.stream().map(SourceFileView::getId).anyMatch(selectedFileId::equals)) {
            return selectedFileId;
        }
        return safeFiles.get(safeFiles.size() - 1).getId();
    }

    private QuoteVersionView buildSourceOnlyVersion(BundleDetailView detailView) {
        QuoteVersionView versionView = new QuoteVersionView();
        versionView.setVersionNo("未生成");
        versionView.setStatus("SOURCE_ONLY");
        versionView.setSummary(defaultIfBlank(
                detailView.getAnalysisSummary(),
                detailView.getAnalysisStatus(),
                "SOURCE_ONLY"
        ) + " 来源层已入库，结构化报价版本仍未生成。");
        return versionView;
    }

    private String buildDbSourceReadbackHint(BundleDetailView detailView) {
        int fileCount = Optional.ofNullable(detailView.getFiles()).orElse(Collections.emptyList()).size();
        int noteCount = Optional.ofNullable(detailView.getNotes()).orElse(Collections.emptyList()).size();
        int serviceCount = Optional.ofNullable(detailView.getServices()).orElse(Collections.emptyList()).size();
        int ruleCount = Optional.ofNullable(detailView.getRules()).orElse(Collections.emptyList()).size();
        int restrictionCount = Optional.ofNullable(detailView.getRestrictions()).orElse(Collections.emptyList()).size();
        return "当前详情已从本地库真实回读：来源文件元数据 "
                + fileCount
                + " 条、补充文案 "
                + noteCount
                + " 条、来源摘要 "
                + (StringUtils.hasText(detailView.getAnalysisSummary()) ? "已保存" : "未填写")
                + "；结构化报价草稿服务 "
                + serviceCount
                + " 条、规则 "
                + ruleCount
                + " 条、限制 "
                + restrictionCount
                + " 条。报价文件可归档原件但不做 OCR/解析，风评仍保留在后续阶段。";
    }

    private String requireText(String value, String message) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String defaultIfBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalize(value);
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String inferFileType(String fileName) {
        String normalized = Objects.toString(fileName, "").trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".pdf")) {
            return "pdf";
        }
        if (normalized.endsWith(".xlsx") || normalized.endsWith(".xls") || normalized.endsWith(".csv")) {
            return "excel";
        }
        if (normalized.endsWith(".png") || normalized.endsWith(".jpg") || normalized.endsWith(".jpeg") || normalized.endsWith(".webp")) {
            return "image";
        }
        return "unknown";
    }

    private String safeOriginalFileName(MultipartFile file) {
        String original = normalize(file.getOriginalFilename());
        if (!StringUtils.hasText(original)) {
            original = "logistics-quote-file";
        }
        String cleaned = StringUtils.cleanPath(original);
        int slashIndex = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        if (slashIndex >= 0) {
            cleaned = cleaned.substring(slashIndex + 1);
        }
        cleaned = cleaned.replace("..", "_").replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return StringUtils.hasText(cleaned) ? cleaned : "logistics-quote-file";
    }

    private String requireArchiveFileType(String fileName) {
        String extension = StringUtils.getFilenameExtension(fileName);
        String normalizedExtension = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        if (!ALLOWED_ARCHIVE_EXTENSIONS.contains(normalizedExtension)) {
            throw new IllegalArgumentException("归档文件仅支持 PDF、Excel、CSV、Word、文本和常见图片格式。");
        }
        return inferFileType(fileName);
    }

    private String saveArchiveFile(Long bundleId, Long fileId, String originalFileName, MultipartFile file) {
        Path root = archiveRoot().toAbsolutePath().normalize();
        String storedFileName = fileId + "-" + UUID.randomUUID() + "-" + originalFileName;
        String relativePath = bundleId + "/" + storedFileName;
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("归档文件路径不合法。");
        }
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            return ARCHIVE_URI_PREFIX + relativePath;
        } catch (IOException exception) {
            throw new IllegalStateException("保存归档文件失败，请稍后重试。", exception);
        }
    }

    private Path archiveUriToPath(String archiveUri) {
        if (!isArchiveUri(archiveUri)) {
            throw new IllegalArgumentException("归档文件路径不合法。");
        }
        String relativePath = archiveUri.substring(ARCHIVE_URI_PREFIX.length());
        Path root = archiveRoot().toAbsolutePath().normalize();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("归档文件路径不合法。");
        }
        return target;
    }

    private boolean isArchiveUri(String filePath) {
        return StringUtils.hasText(filePath) && filePath.startsWith(ARCHIVE_URI_PREFIX);
    }

    private void deleteArchivedFileQuietly(String archiveUri) {
        try {
            if (isArchiveUri(archiveUri)) {
                Files.deleteIfExists(archiveUriToPath(archiveUri));
            }
        } catch (IOException | IllegalArgumentException ignored) {
            // Best-effort cleanup only. The source row remains authoritative.
        }
    }

    private Path archiveRoot() {
        String configuredDir = System.getenv("NUONO_NEXT_LOGISTICS_QUOTE_ARCHIVE_DIR");
        if (StringUtils.hasText(configuredDir)) {
            return Paths.get(configuredDir);
        }
        return Paths.get(System.getProperty("java.io.tmpdir"), "nuono-next-logistics-quotes");
    }

    private Map<Long, BundleDetailView> buildSampleBundles() {
        Map<Long, BundleDetailView> bundles = new LinkedHashMap<>();
        BundleDetailView qike = new BundleDetailView();
        qike.setId(91001L);
        qike.setBundleName("起客-沙特阿联酋报价包-2026-04");
        qike.setAnalysisStatus("ANALYZED");
        qike.setForwarder(forwarder(501L, "起客", "Qike", "起客物流", "Excel 报价包里混合了 B2B、大货、小包、海外仓与禁运清单。"));
        qike.setQuoteVersion(version(601L, "2026-04-v1", "PUBLISHED", "沙特 / 阿联酋主报价包，当前先按服务拆分视图收口。", "2026-04-11"));
        qike.setFiles(List.of(file("副本20260411起-起客物流报价.xlsx", "excel", "用户提供"), file("报价包目录", "virtual", "sheet summary")));
        qike.setNotes(List.of(note("analysis_note", "system", "该文件不是一条报价，而是一个包含空运、海运、小包、海外仓的方案包。")));
        qike.setServices(List.of(
                service("沙特双清空运专线", "SA", "CN-SA", "AIR", "B2B", "FIRST_LEG", "15-20天", "空运规则独立维护"),
                service("沙特双清海运专线", "SA", "CN-SA", "SEA", "B2B", "FIRST_LEG", "45-60天", "海运规则按品类拆分"),
                service("阿联酋双清空运专线", "AE", "CN-AE", "AIR", "B2B", "FIRST_LEG", "15-20天", "按 sheet 维护")
        ));
        qike.setRules(List.of(
                rule("沙特双清空运专线", "主运价规则", "BASE_RATE", "按品类拆分", "KG", "CNY", null, "GREATER_OF", "空运计费规则存在体积重取大逻辑。"),
                rule("沙特双清海运专线", "主运价规则", "BASE_RATE", "按品类拆分", "CBM", "CNY", null, "FIXED", "海运主规则按方计费。"),
                rule("沙特双清海运专线", "混装高收提示", "MANUAL_NOTE_DERIVED", "混装货", "FIXED", "CNY", null, "MIXED_RULE", "不同产品类型需分装，否则按高收。")
        ));
        qike.setRestrictions(List.of(
                restriction("沙特双清海运专线", "MIXED_PACKING_RULE", "TEXT", "-", "-", "SOFT", "不同产品类型需分装，否则按高收。"),
                restriction("沙特双清空运专线", "CATEGORY_FORBIDDEN", "TEXT", "-", "-", "HARD", "禁运品需按禁运清单单独核验。")
        ));
        qike.setEvidences(List.of(
                evidence("SERVICE", "沙特双清海运专线", "FILE", "副本20260411起-起客物流报价.xlsx", "sheet=沙特双清海运专线", "目录页与专线页共同证明该 bundle 是多服务报价包。")
        ));
        qike.setReputationSnapshot(reputation(74, 78, 72, 70, 71, 77, "B", "公开口碑偏中性，规则完整度高于公开风评密度。", "可作为普通线稳定备选。"));
        qike.setReputationSignals(List.of(
                signal("SERVICE", "POSITIVE", "LOW", "INTERNAL", "规则整理度", "报价结构相对完整，适合先做标准化落地。"),
                signal("PRICE", "NEGATIVE", "LOW", "NOTE", "混装附加", "部分混装高收规则写在备注里，需要人工确认后发布。")
        ));
        bundles.put(qike.getId(), qike);

        BundleDetailView et = new BundleDetailView();
        et.setId(91002L);
        et.setBundleName("易通-仓到仓报价包-2026-04");
        et.setAnalysisStatus("ANALYZED");
        et.setForwarder(forwarder(502L, "易通", "ET", "易通物流", "当前主文件表现为多线路仓到仓报价表。"));
        et.setQuoteVersion(version(602L, "2026-04-v1", "PUBLISHED", "沙特 / 阿联酋仓到仓主报价版本。", "2026-04-14"));
        et.setFiles(List.of(file("ET物流报价-20260414入仓生效.pdf", "pdf", "用户提供")));
        et.setNotes(List.of(note("analysis_note", "system", "PDF 主体是规则表，不是单行价格，需要后续继续补字段级抽取。")));
        et.setServices(List.of(
                service("中国到沙特仓到仓", "SA", "CN-SA", "SEA", "B2B", "FIRST_LEG_WITH_DELIVERY", "约45天", "仓到仓"),
                service("中国到阿联酋仓到仓", "AE", "CN-AE", "AIR", "B2B", "FIRST_LEG_WITH_DELIVERY", "约15天", "仓到仓")
        ));
        et.setRules(List.of(
                rule("中国到沙特仓到仓", "海运主规则", "BASE_RATE", "按类别拆分", "CBM", "CNY", null, "FIXED", "海运仓到仓按类别和线路分组。"),
                rule("中国到阿联酋仓到仓", "空运主规则", "BASE_RATE", "按类别拆分", "KG", "CNY", null, "GREATER_OF", "空运规则需结合体积重。")
        ));
        et.setRestrictions(List.of(
                restriction("中国到沙特仓到仓", "PACKING_REQUIREMENT", "TEXT", "-", "-", "SOFT", "需进一步补齐包装与发货门槛说明。")
        ));
        et.setEvidences(List.of(
                evidence("SERVICE", "中国到沙特仓到仓", "FILE", "ET物流报价-20260414入仓生效.pdf", "page=1", "PDF 头部直接出现多条仓到仓线路。")
        ));
        et.setReputationSnapshot(reputation(72, 80, 69, 68, 70, 74, "B", "公开信息量一般，适合继续结合内部合作记录确认。", "可作为敏感线备选。"));
        et.setReputationSignals(List.of(
                signal("COMPLIANCE", "POSITIVE", "LOW", "OFFICIAL", "主体状态", "主体公开信息未见明显异常。"),
                signal("SERVICE", "NEUTRAL", "LOW", "SEARCH_RESULT", "公开口碑", "公开风评密度有限，需继续依赖内部反馈。")
        ));
        bundles.put(et.getId(), et);

        BundleDetailView zhongdong = new BundleDetailView();
        zhongdong.setId(91003L);
        zhongdong.setBundleName("众鸫-沙特迪拜专线包-2026-04");
        zhongdong.setAnalysisStatus("ANALYZED");
        zhongdong.setForwarder(forwarder(503L, "众鸫", "众鸫供应链", "深圳市众鸫供应链", "文件里同时包含专线、小包、海外仓、违禁品与软件服务。"));
        zhongdong.setQuoteVersion(version(603L, "2026-04-v1", "DRAFT", "当前先把空运/海运头程与海外仓服务拆开。", "2026-04-11"));
        zhongdong.setFiles(List.of(file("深圳市众鸫供应链报价单2026.4.11(1)(2)(1).xlsx", "excel", "用户提供")));
        zhongdong.setNotes(List.of(note("analysis_note", "system", "同一国家、同一运输方式下也存在 FBA/FBN 含送仓与海外仓不含送仓两套服务范围。")));
        zhongdong.setServices(List.of(
                service("沙特空运专线", "SA", "CN-SA", "AIR", "B2B", "FIRST_LEG_WITH_DELIVERY", "约15天", "含送仓/不含送仓需拆 scope"),
                service("沙特海运专线", "SA", "CN-SA", "SEA", "B2B", "FIRST_LEG", "约45天", "分类细粒度高"),
                service("迪拜海运专线", "AE", "CN-AE", "SEA", "B2B", "FIRST_LEG", "约33-40天", "专线")
        ));
        zhongdong.setRules(List.of(
                rule("沙特海运专线", "海运分类规则", "BASE_RATE", "A/B/B1/.../K", "CBM", "CNY", null, "FIXED", "同一条海运专线按大量细分类拆价。"),
                rule("沙特空运专线", "服务范围拆分", "SUPPLEMENT_RATE", "FBA/FBN / 海外仓", "FIXED", "CNY", null, "MIXED_RULE", "是否含送仓需要结构化字段 service_scope 承接。")
        ));
        zhongdong.setRestrictions(List.of(
                restriction("沙特海运专线", "PACKING_REQUIREMENT", "TEXT", "-", "-", "HARD", "单箱单品 / 单箱单类 / 混装限制需结构化。"),
                restriction("沙特空运专线", "CATEGORY_FORBIDDEN", "TEXT", "-", "-", "HARD", "违禁品清单需独立挂接。")
        ));
        zhongdong.setEvidences(List.of(
                evidence("SERVICE", "沙特海运专线", "FILE", "深圳市众鸫供应链报价单2026.4.11(1)(2)(1).xlsx", "sheet=沙特海运专线", "sheet 名直接体现线路拆分。")
        ));
        zhongdong.setReputationSnapshot(reputation(71, 79, 68, 67, 70, 73, "B", "文件结构复杂度高，适合先做标准化后再比较。", "适合作为多场景服务备选。"));
        zhongdong.setReputationSignals(List.of(
                signal("SERVICE", "POSITIVE", "LOW", "INTERNAL", "服务域丰富", "同一报价包覆盖头程、小包和海外仓，后续利于一体化比对。"),
                signal("PRICE", "NEGATIVE", "LOW", "NOTE", "范围复杂", "不先拆 service_scope 容易把含送仓和不含送仓混算。")
        ));
        bundles.put(zhongdong.getId(), zhongdong);

        BundleDetailView yite = new BundleDetailView();
        yite.setId(91004L);
        yite.setBundleName("义特-沙特海运双清-2026-04");
        yite.setAnalysisStatus("ANALYZED");
        yite.setForwarder(forwarder(504L, "义特", "义特物流", "义特物流", "当前样本里信息最完整，适合先验证规则标准化和文案衍生规则。"));
        yite.setQuoteVersion(version(604L, "2026-04-v1", "PUBLISHED", "义特沙特海运双清包税版本，含微信补充规则。", "2026-04-23"));
        yite.setFiles(List.of(file("沙特价格表.pdf", "pdf", "用户提供")));
        yite.setNotes(List.of(note("manual_note", "wechat", "单品需要分别加240/方")));
        yite.setServices(List.of(
                service("沙特海运双清包税", "SA", "CN-SA", "SEA", "B2B", "FIRST_LEG", "约45天", "China to Saudi")
        ));
        yite.setRules(List.of(
                rule("沙特海运双清包税", "普货基础价", "BASE_RATE", "普货", "CBM", "CNY", 950d, "FIXED", "普货 950元/方"),
                rule("沙特海运双清包税", "带电带插带磁类", "BASE_RATE", "带电带插带磁类", "CBM", "CNY", 1400d, "FIXED", "带电带插带磁类 1400元/方"),
                rule("沙特海运双清包税", "灯具、卫浴类", "BASE_RATE", "灯具、卫浴类", "CBM", "CNY", 1500d, "FIXED", "灯具、卫浴类 1500元/方"),
                rule("沙特海运双清包税", "一般敏感货", "BASE_RATE", "一般敏感货", "CBM", "CNY", 1900d, "FIXED", "一般敏感货 1900元/方"),
                rule("沙特海运双清包税", "机械设备类", "BASE_RATE", "机械设备类", "CBM", "CNY", 2500d, "FIXED", "机械设备类 2500元/方"),
                rule("沙特海运双清包税", "特殊类", "BASE_RATE", "特殊类", "CBM", "CNY", 3000d, "FIXED", "特殊类 3000元/方"),
                rule("沙特海运双清包税", "卡牌类", "BASE_RATE", "卡牌类", "KG", "CNY", 19d, "FIXED", "卡牌类 19元/公斤"),
                rule("沙特海运双清包税", "眼镜类", "BASE_RATE", "眼镜类", "KG", "CNY", 25d, "FIXED", "眼镜类 25元/公斤"),
                rule("沙特海运双清包税", "敏感货", "BASE_RATE", "敏感货", "KG", "CNY", 30d, "FIXED", "敏感货 30元/公斤"),
                rule("沙特海运双清包税", "FBA/FBN 送仓", "SURCHARGE", "送仓", "KG", "CNY", 3d, "FIXED", "FBA/FBN送仓 利亚德/吉达 3RMB/KG，最低300RMB/票"),
                rule("沙特海运双清包税", "单品分别发附加", "MANUAL_NOTE_DERIVED", "单品分别发", "CBM", "CNY", 240d, "FIXED", "单品需要分别加240/方")
        ));
        yite.setRestrictions(List.of(
                restriction("沙特海运双清包税", "MAX_BOX_WEIGHT", "<=", "40", "KG", "HARD", "单箱重量不超40公斤。"),
                restriction("沙特海运双清包税", "INQUIRY_REQUIRED", "TEXT", "100", "CM", "SOFT", "单边尺寸超100CM以上价格单询。"),
                restriction("沙特海运双清包税", "CUSTOMS_DECLARATION_LIMIT", ">=", "10", "CBM", "HARD", "低于10个方以下的货不接报关件。"),
                restriction("沙特海运双清包税", "PLUG_STANDARD_REQUIREMENT", "TEXT", "英规三插", "-", "HARD", "有插头产品必须是英规三插。")
        ));
        yite.setEvidences(List.of(
                evidence("RULE", "普货基础价", "FILE", "沙特价格表.pdf", "page=1", "普货 950元/方"),
                evidence("RULE", "单品分别发附加", "NOTE", "wechat_note", "note#1", "单品需要分别加240/方"),
                evidence("RESTRICTION", "单箱重量不超40公斤", "FILE", "沙特价格表.pdf", "page=1", "单箱重量不超40公斤")
        ));
        yite.setReputationSnapshot(reputation(73, 81, 68, 65, 70, 76, "B", "规则相对清楚，但仍有一部分价格依赖人工补充说明。", "适合先做规则标准化验证，不建议在缺规格时直接整批算方案。"));
        yite.setReputationSignals(List.of(
                signal("PRICE", "NEGATIVE", "MEDIUM", "NOTE", "补充规则", "部分生效规则依赖微信补充说明。"),
                signal("SERVICE", "POSITIVE", "LOW", "INTERNAL", "规则清晰度", "主表和出货说明信息较完整，适合做结构化试点。")
        ));
        bundles.put(yite.getId(), yite);

        return bundles;
    }

    private ForwarderView forwarder(Long id, String name, String alias, String companyName, String notes) {
        ForwarderView view = new ForwarderView();
        view.setId(id);
        view.setName(name);
        view.setAlias(alias);
        view.setCompanyName(companyName);
        view.setNotes(notes);
        return view;
    }

    private QuoteVersionView version(Long id, String versionNo, String status, String summary, String effectiveFrom) {
        QuoteVersionView view = new QuoteVersionView();
        view.setId(id);
        view.setVersionNo(versionNo);
        view.setStatus(status);
        view.setSummary(summary);
        view.setEffectiveFrom(effectiveFrom);
        return view;
    }

    private SourceFileView file(String fileName, String fileType, String sourceLabel) {
        SourceFileView view = new SourceFileView();
        view.setFileName(fileName);
        view.setFileType(fileType);
        view.setSourceLabel(sourceLabel);
        return view;
    }

    private SourceNoteView note(String noteType, String sourceChannel, String content) {
        SourceNoteView view = new SourceNoteView();
        view.setNoteType(noteType);
        view.setSourceChannel(sourceChannel);
        view.setContent(content);
        return view;
    }

    private ServiceView service(
            String serviceName,
            String countryCode,
            String routeCode,
            String transportMode,
            String businessType,
            String serviceScope,
            String transitTimeText,
            String remarks
    ) {
        ServiceView view = new ServiceView();
        view.setServiceName(serviceName);
        view.setCountryCode(countryCode);
        view.setRouteCode(routeCode);
        view.setTransportMode(transportMode);
        view.setBusinessType(businessType);
        view.setServiceScope(serviceScope);
        view.setTransitTimeText(transitTimeText);
        view.setRemarks(remarks);
        return view;
    }

    private RuleView rule(
            String serviceName,
            String ruleName,
            String ruleType,
            String cargoCategory,
            String billingUnit,
            String currency,
            Double unitPrice,
            String calcBasis,
            String summary
    ) {
        RuleView view = new RuleView();
        view.setServiceName(serviceName);
        view.setRuleName(ruleName);
        view.setRuleType(ruleType);
        view.setCargoCategory(cargoCategory);
        view.setBillingUnit(billingUnit);
        view.setCurrency(currency);
        view.setUnitPrice(unitPrice);
        view.setCalcBasis(calcBasis);
        view.setSummary(summary);
        return view;
    }

    private RestrictionView restriction(
            String serviceName,
            String restrictionType,
            String operator,
            String value,
            String unit,
            String severity,
            String description
    ) {
        RestrictionView view = new RestrictionView();
        view.setServiceName(serviceName);
        view.setRestrictionType(restrictionType);
        view.setOperator(operator);
        view.setValue(value);
        view.setUnit(unit);
        view.setSeverity(severity);
        view.setDescription(description);
        return view;
    }

    private EvidenceView evidence(
            String targetType,
            String targetName,
            String sourceType,
            String sourceName,
            String locator,
            String evidenceText
    ) {
        EvidenceView view = new EvidenceView();
        view.setTargetType(targetType);
        view.setTargetName(targetName);
        view.setSourceType(sourceType);
        view.setSourceName(sourceName);
        view.setLocator(locator);
        view.setEvidenceText(evidenceText);
        return view;
    }

    private ReputationSnapshotView reputation(
            Integer overallScore,
            Integer complianceScore,
            Integer timelinessScore,
            Integer priceTransparencyScore,
            Integer claimsScore,
            Integer serviceScore,
            String recommendationLevel,
            String recentRiskSummary,
            String analysisSummary
    ) {
        ReputationSnapshotView view = new ReputationSnapshotView();
        view.setOverallScore(overallScore);
        view.setComplianceScore(complianceScore);
        view.setTimelinessScore(timelinessScore);
        view.setPriceTransparencyScore(priceTransparencyScore);
        view.setClaimsScore(claimsScore);
        view.setServiceScore(serviceScore);
        view.setRecommendationLevel(recommendationLevel);
        view.setRecentRiskSummary(recentRiskSummary);
        view.setAnalysisSummary(analysisSummary);
        return view;
    }

    private ReputationSignalView signal(
            String signalType,
            String polarity,
            String severity,
            String sourceType,
            String topic,
            String evidenceText
    ) {
        ReputationSignalView view = new ReputationSignalView();
        view.setSignalType(signalType);
        view.setPolarity(polarity);
        view.setSeverity(severity);
        view.setSourceType(sourceType);
        view.setTopic(topic);
        view.setEvidenceText(evidenceText);
        return view;
    }
}
