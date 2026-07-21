package com.nuono.next.filemanagement.parse;

import com.nuono.next.infrastructure.mapper.FileManagementParseMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class FileParseTaskCreationService {

    private static final DateTimeFormatter TASK_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Set<String> FILE_TYPES = Set.of("file", "excel", "pdf", "image");
    private static final Set<String> TEXT_TYPES = Set.of("manual_text", "ocr_text");
    private static final Set<String> INPUT_ROLES = Set.of("primary_source", "parsed_file", "supplement", "reference");
    private final FileManagementParseMapper mapper;
    private final FileParseUploadArchiveService uploadArchiveService;
    private final FileParseActionPolicy actionPolicy;

    public FileParseTaskCreationService(
            FileManagementParseMapper mapper,
            FileParseUploadArchiveService uploadArchiveService,
            FileParseActionPolicy actionPolicy
    ) {
        this.mapper = mapper;
        this.uploadArchiveService = uploadArchiveService;
        this.actionPolicy = actionPolicy;
    }

    @Transactional
    public FileParseTaskDetailView create(
            FileParseUserContext user,
            FileParseTargetPlanRow targetPlan,
            FileParseTaskRow parentTask,
            FileParseCreateTaskCommand command,
            String idempotencyKey
    ) {
        Long taskId = mapper.nextTaskId();
        String taskNo = "TASK-" + LocalDate.now().format(TASK_DATE_FORMATTER) + "-" + taskId;
        String title = trimRequired(command.getDocumentTitle(), 200, "文档名称");
        String remark = trimOptional(command.getRemark(), 1000, "备注");
        String normalizedKey = trimOptional(idempotencyKey, 180, "幂等键");
        Long groupId = resolveGroupId(parentTask, taskId);
        Integer iterationNo = parentTask == null ? 1 : mapper.selectMaxIterationNo(groupId) + 1;
        Long parentTaskId = parentTask == null ? null : parentTask.getId();
        String requestHash = sha256(title + "|" + targetPlan.getId() + "|" + parentTaskId
                + "|" + command.getInputItems().size());

        int inserted = mapper.insertTask(
                taskId, taskNo, title, targetPlan.getId(), targetPlan.getStandardVersionId(),
                targetPlan.getCurrentVersionId(), groupId, parentTaskId, iterationNo,
                remark, normalizedKey, requestHash, user.getUserId()
        );
        if (inserted != 1) {
            throw new IllegalStateException("解析文档创建失败。");
        }

        List<FileParseTaskInputView> inputs = new ArrayList<>();
        for (int index = 0; index < command.getInputItems().size(); index++) {
            inputs.add(createInput(taskId, targetPlan, user, command.getInputItems().get(index), index + 1));
        }
        return toView(taskId, taskNo, title, targetPlan, groupId, parentTaskId, iterationNo, remark, inputs);
    }

    private FileParseTaskInputView createInput(
            Long taskId,
            FileParseTargetPlanRow targetPlan,
            FileParseUserContext user,
            FileParseTaskInputCommand command,
            int defaultSortNo
    ) {
        if (command == null) {
            throw new IllegalArgumentException("输入项不能为空。");
        }
        FileParseFileAssetRow asset = null;
        String inputType = normalizeInputType(command);
        if (FILE_TYPES.contains(inputType)) {
            asset = requireUsableFile(user, targetPlan, taskId, command.getFileAssetId());
            inputType = normalizeFileType(inputType, asset.getFileExtension());
        } else if (TEXT_TYPES.contains(inputType)) {
            if (!StringUtils.hasText(command.getTextContent())) {
                throw new IllegalArgumentException("文本输入内容不能为空。");
            }
        } else {
            throw new IllegalArgumentException("不支持的输入类型：" + inputType);
        }

        String inputRole = normalizeInputRole(command.getInputRole());
        String textContent = TEXT_TYPES.contains(inputType)
                ? trimRequired(command.getTextContent(), 20000, "文本输入内容")
                : trimOptional(command.getTextContent(), 20000, "文本输入内容");
        String displayName = normalizeDisplayName(command, asset);
        Integer sortNo = command.getSortNo() == null ? defaultSortNo : Math.max(command.getSortNo(), 0);
        Long inputId = mapper.nextTaskInputId();
        int inserted = mapper.insertTaskInput(
                inputId, taskId, inputType, inputRole, asset == null ? null : asset.getId(),
                textContent, displayName, sortNo, user.getUserId()
        );
        if (inserted != 1) {
            throw new IllegalStateException("解析文档输入项创建失败。");
        }
        return toInputView(inputId, inputType, inputRole, asset, displayName, sortNo);
    }

    private FileParseFileAssetRow requireUsableFile(
            FileParseUserContext user,
            FileParseTargetPlanRow targetPlan,
            Long taskId,
            Long fileAssetId
    ) {
        if (fileAssetId == null) {
            throw new IllegalArgumentException("文件输入项必须先上传文件。");
        }
        FileParseFileAssetRow asset = mapper.selectFileAsset(fileAssetId);
        if (asset == null) {
            throw new IllegalArgumentException("上传文件不存在或已删除。");
        }
        if (!targetPlan.getId().equals(asset.getTargetPlanId())) {
            throw new IllegalArgumentException("上传文件与目标输出方案不匹配。");
        }
        if (!actionPolicy.isSystemAdmin(user) && !user.getUserId().equals(asset.getUploadedBy())) {
            throw new FileParseAccessDeniedException("当前账号不能使用该上传文件。");
        }
        if (asset.getExpiresAt() != null && asset.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("上传文件已过期，请重新上传。");
        }
        if (asset.getBoundTaskId() != null && !taskId.equals(asset.getBoundTaskId())) {
            throw new IllegalArgumentException("上传文件已被其它解析文档使用。");
        }
        if (mapper.bindFileAssetToTask(asset.getId(), taskId, user.getUserId()) != 1) {
            throw new IllegalArgumentException("上传文件已被其它解析文档使用。");
        }
        return asset;
    }

    private FileParseTaskDetailView toView(
            Long taskId,
            String taskNo,
            String title,
            FileParseTargetPlanRow targetPlan,
            Long groupId,
            Long parentTaskId,
            Integer iterationNo,
            String remark,
            List<FileParseTaskInputView> inputs
    ) {
        FileParseTaskDetailView view = new FileParseTaskDetailView();
        view.setId(taskId);
        view.setTaskNo(taskNo);
        view.setDocumentTitle(title);
        view.setTargetPlanId(targetPlan.getId());
        view.setTargetPlanCode(targetPlan.getCode());
        view.setTargetPlanLabel(targetPlan.getLabel());
        view.setDocumentType(targetPlan.getDocumentType());
        view.setDocumentName(targetPlan.getDocumentName());
        view.setStandardVersion(targetPlan.getStandardVersion());
        view.setCurrentVersion(targetPlan.getCurrentVersion());
        view.setStatus("reading");
        view.setResultId(null);
        view.setDataScopeType("global");
        view.setDataScopeKey("global:*");
        view.setDocumentGroupId(groupId);
        view.setParentTaskId(parentTaskId);
        view.setIterationNo(iterationNo);
        view.setRemark(remark);
        view.setMessage(parentTaskId == null
                ? "解析文档已创建，文件和文本输入已完成归档，等待后续 AI 解析执行。"
                : "源文件更新任务已创建，本次解析会基于当前生效版本重新对比。");
        view.setInputItems(inputs);
        return view;
    }

    private FileParseTaskInputView toInputView(
            Long inputId,
            String inputType,
            String inputRole,
            FileParseFileAssetRow asset,
            String displayName,
            Integer sortNo
    ) {
        FileParseTaskInputView view = new FileParseTaskInputView();
        view.setId(inputId);
        view.setInputType(inputType);
        view.setInputRole(inputRole);
        view.setFileAssetId(asset == null ? null : asset.getId());
        view.setDisplayName(displayName);
        view.setDownloadUrl(asset == null ? null : uploadArchiveService.downloadUrl(asset.getId()));
        view.setSortNo(sortNo);
        return view;
    }

    private Long resolveGroupId(FileParseTaskRow parentTask, Long taskId) {
        if (parentTask == null) {
            return taskId;
        }
        return parentTask.getDocumentGroupId() == null ? parentTask.getId() : parentTask.getDocumentGroupId();
    }

    private String normalizeInputType(FileParseTaskInputCommand command) {
        String inputType = trimLowercase(command.getInputType());
        return StringUtils.hasText(inputType) ? inputType : command.getFileAssetId() == null ? "manual_text" : "file";
    }

    private String normalizeFileType(String inputType, String extension) {
        if (!"file".equals(inputType)) {
            return inputType;
        }
        if ("pdf".equals(extension)) {
            return "pdf";
        }
        if ("xlsx".equals(extension) || "xls".equals(extension) || "csv".equals(extension)) {
            return "excel";
        }
        if ("png".equals(extension) || "jpg".equals(extension)
                || "jpeg".equals(extension) || "webp".equals(extension)) {
            return "image";
        }
        return "file";
    }

    private String normalizeInputRole(String inputRole) {
        String role = trimLowercase(inputRole);
        if (!StringUtils.hasText(role)) {
            return "primary_source";
        }
        if (!INPUT_ROLES.contains(role)) {
            throw new IllegalArgumentException("不支持的输入角色：" + role);
        }
        return role;
    }

    private String normalizeDisplayName(FileParseTaskInputCommand command, FileParseFileAssetRow asset) {
        String displayName = trimOptional(command.getDisplayName(), 300, "展示名称");
        if (StringUtils.hasText(displayName)) {
            return displayName;
        }
        return asset == null ? "文本输入" : asset.getOriginalFileName();
    }

    private String trimLowercase(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }

    private String trimRequired(String value, int maxLength, String label) {
        String trimmed = value == null ? "" : value.trim();
        if (!StringUtils.hasText(trimmed)) {
            throw new IllegalArgumentException(label + "不能为空。");
        }
        return requireMaxLength(trimmed, maxLength, label);
    }

    private String trimOptional(String value, int maxLength, String label) {
        return StringUtils.hasText(value) ? requireMaxLength(value.trim(), maxLength, label) : null;
    }

    private String requireMaxLength(String value, int maxLength, String label) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(label + "长度不能超过 " + maxLength + " 个字符。");
        }
        return value;
    }

    private String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256。", error);
        }
    }
}
