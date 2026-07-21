package com.nuono.next.filemanagement.parse;

import com.nuono.next.infrastructure.mapper.FileManagementParseMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class FileParseTaskCatalogService {

    private final FileManagementParseMapper mapper;
    private final FileParseUploadArchiveService uploadArchiveService;
    private final FileParseActionPolicy actionPolicy;

    public FileParseTaskCatalogService(
            FileManagementParseMapper mapper,
            FileParseUploadArchiveService uploadArchiveService,
            FileParseActionPolicy actionPolicy
    ) {
        this.mapper = mapper;
        this.uploadArchiveService = uploadArchiveService;
        this.actionPolicy = actionPolicy;
    }

    public List<FileParseTargetPlanSummary> listTargetPlans(FileParseUserContext user) {
        boolean systemAdmin = actionPolicy.isSystemAdmin(user);
        return mapper.selectVisibleTargetPlans(user.getRoleLevel(), systemAdmin).stream()
                .map(row -> toSummary(row, user))
                .collect(Collectors.toList());
    }

    public FileParseTaskListView listTasks(
            FileParseUserContext user,
            String keyword,
            Long targetPlanId,
            String status,
            Integer requestedPage,
            Integer requestedPageSize
    ) {
        boolean systemAdmin = actionPolicy.isSystemAdmin(user);
        String normalizedKeyword = normalizeOptionalKeyword(keyword);
        String normalizedStatus = normalizeOptionalKeyword(status);
        int page = normalizePage(requestedPage);
        int pageSize = normalizePageSize(requestedPageSize, 20);
        int offset = (page - 1) * pageSize;

        int total = mapper.countTasks(
                normalizedKeyword,
                targetPlanId,
                normalizedStatus,
                user.getRoleLevel(),
                systemAdmin
        );
        List<FileParseTaskListItemView> items = mapper.selectTasks(
                        normalizedKeyword,
                        targetPlanId,
                        normalizedStatus,
                        user.getRoleLevel(),
                        systemAdmin,
                        pageSize,
                        offset
                )
                .stream()
                .peek(item -> item.setAvailableActions(
                        actionPolicy.availableActions(toTargetPlanRow(item), user)
                ))
                .collect(Collectors.toList());
        enrichTaskListInputs(items);

        FileParseTaskListView view = new FileParseTaskListView();
        view.setTotal(total);
        view.setPage(page);
        view.setPageSize(pageSize);
        view.setItems(items);
        return view;
    }

    public FileParseTaskDetailView getTask(
            FileParseTaskRow task,
            FileParseTargetPlanRow targetPlan
    ) {
        return toTaskDetailView(task, targetPlan, mapper.selectTaskInputs(task.getId()));
    }

    private void enrichTaskListInputs(List<FileParseTaskListItemView> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<Long> taskIds = items.stream()
                .map(FileParseTaskListItemView::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (taskIds.isEmpty()) {
            return;
        }
        Map<Long, List<FileParseTaskInputView>> inputItemsByTaskId = mapper
                .selectTaskInputsByTaskIds(taskIds)
                .stream()
                .filter(row -> row.getTaskId() != null)
                .collect(Collectors.groupingBy(
                        FileParseTaskInputRow::getTaskId,
                        LinkedHashMap::new,
                        Collectors.mapping(this::toTaskInputView, Collectors.toList())
                ));
        for (FileParseTaskListItemView item : items) {
            item.setInputItems(inputItemsByTaskId.getOrDefault(item.getId(), List.of()));
        }
    }

    private FileParseTargetPlanSummary toSummary(
            FileParseTargetPlanRow row,
            FileParseUserContext user
    ) {
        FileParseTargetPlanSummary summary = new FileParseTargetPlanSummary();
        summary.setId(row.getId());
        summary.setCode(row.getCode());
        summary.setLabel(row.getLabel());
        summary.setDocumentType(row.getDocumentType());
        summary.setDocumentName(row.getDocumentName());
        summary.setStandardVersion(row.getStandardVersion());
        summary.setCurrentVersion(row.getCurrentVersion());
        summary.setDescription(row.getDescription());
        summary.setAvailableActions(actionPolicy.availableActions(row, user));
        summary.setItemTypes(toTargetPlanItemTypes(row.getStandardVersionId()));
        return summary;
    }

    private List<FileParseTargetPlanItemTypeView> toTargetPlanItemTypes(Long standardVersionId) {
        if (standardVersionId == null) {
            return List.of();
        }
        List<FileParseItemStandardRow> itemStandards = mapper.selectItemStandards(standardVersionId);
        if (itemStandards == null || itemStandards.isEmpty()) {
            return List.of();
        }
        return itemStandards.stream()
                .map(this::toTargetPlanItemType)
                .collect(Collectors.toList());
    }

    private FileParseTargetPlanItemTypeView toTargetPlanItemType(FileParseItemStandardRow row) {
        FileParseTargetPlanItemTypeView view = new FileParseTargetPlanItemTypeView();
        view.setValue(row.getItemType());
        view.setLabel(StringUtils.hasText(row.getItemLabel()) ? row.getItemLabel() : row.getItemType());
        return view;
    }

    private FileParseTaskDetailView toTaskDetailView(
            FileParseTaskRow task,
            FileParseTargetPlanRow targetPlan,
            List<FileParseTaskInputRow> inputRows
    ) {
        FileParseTaskDetailView view = new FileParseTaskDetailView();
        view.setId(task.getId());
        view.setTaskNo(task.getTaskNo());
        view.setDocumentTitle(task.getDocumentTitle());
        view.setTargetPlanId(targetPlan.getId());
        view.setTargetPlanCode(targetPlan.getCode());
        view.setTargetPlanLabel(targetPlan.getLabel());
        view.setDocumentType(targetPlan.getDocumentType());
        view.setDocumentName(targetPlan.getDocumentName());
        view.setStandardVersion(targetPlan.getStandardVersion());
        view.setCurrentVersion(targetPlan.getCurrentVersion());
        view.setStatus(task.getStatus());
        view.setResultId(task.getCurrentResultId());
        view.setFailureCode(task.getFailureCode());
        view.setFailureMessage(task.getFailureMessage());
        view.setNextRunAt(task.getNextRunAt());
        view.setDataScopeType(task.getDataScopeType());
        view.setDataScopeKey(task.getDataScopeKey());
        view.setDocumentGroupId(task.getDocumentGroupId() == null ? task.getId() : task.getDocumentGroupId());
        view.setParentTaskId(task.getParentTaskId());
        view.setIterationNo(task.getIterationNo() == null ? 1 : task.getIterationNo());
        view.setMessage(task.getFailureMessage());
        view.setInputItems(inputRows == null ? List.of() : inputRows.stream()
                .map(this::toTaskInputView)
                .collect(Collectors.toList()));
        return view;
    }

    private FileParseTaskInputView toTaskInputView(FileParseTaskInputRow row) {
        FileParseTaskInputView view = new FileParseTaskInputView();
        view.setId(row.getId());
        view.setInputType(row.getInputType());
        view.setInputRole(row.getInputRole());
        view.setFileAssetId(row.getFileAssetId());
        view.setDisplayName(row.getDisplayName());
        view.setDownloadUrl(row.getFileAssetId() == null
                ? null
                : uploadArchiveService.downloadUrl(row.getFileAssetId()));
        view.setSortNo(row.getSortNo());
        return view;
    }

    private FileParseTargetPlanRow toTargetPlanRow(FileParseTaskListItemView item) {
        FileParseTargetPlanRow row = new FileParseTargetPlanRow();
        row.setId(item.getTargetPlanId());
        row.setCode(item.getTargetPlanCode());
        row.setLabel(item.getTargetPlanLabel());
        row.setDocumentType(item.getDocumentType());
        row.setDocumentName(item.getDocumentName());
        row.setStandardVersion(item.getStandardVersion());
        row.setCurrentVersion(item.getCurrentVersion());
        return row;
    }

    private String normalizeOptionalKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }

    private int normalizePageSize(Integer pageSize, int defaultPageSize) {
        if (pageSize == null || pageSize < 1) {
            return defaultPageSize;
        }
        return Math.min(pageSize, 100);
    }
}
