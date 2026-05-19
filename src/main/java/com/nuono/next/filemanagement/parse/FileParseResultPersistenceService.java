package com.nuono.next.filemanagement.parse;

import com.nuono.next.infrastructure.mapper.FileManagementParseMapper;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-db")
public class FileParseResultPersistenceService {

    private static final DateTimeFormatter RESULT_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final FileManagementParseMapper fileManagementParseMapper;

    public FileParseResultPersistenceService(FileManagementParseMapper fileManagementParseMapper) {
        this.fileManagementParseMapper = fileManagementParseMapper;
    }

    @Transactional
    public FileParsePersistedResult persist(
            FileParseTaskRow task,
            FileParseStructuredAiResult structuredResult,
            String lockOwner,
            Long operatorUserId
    ) {
        Long resultId = fileManagementParseMapper.nextResultId();
        String resultNo = "RESULT-" + LocalDate.now().format(RESULT_DATE_FORMATTER) + "-" + resultId;
        int inserted = fileManagementParseMapper.insertResult(
                resultId,
                resultNo,
                task.getId(),
                task.getTargetPlanId(),
                task.getStandardVersionId(),
                task.getBaseVersionId(),
                task.getDataScopeType(),
                task.getDataScopeKey(),
                structuredResult.getParserType(),
                structuredResult.getParserModel(),
                structuredResult.getSummaryJson(),
                structuredResult.getRawResultJson(),
                structuredResult.getValidationSummaryJson(),
                operatorUserId
        );
        if (inserted != 1) {
            throw new IllegalStateException("解析结果写入失败。");
        }

        for (FileParseStructuredItem item : structuredResult.getItems()) {
            Long itemId = fileManagementParseMapper.nextResultItemId();
            int itemInserted = fileManagementParseMapper.insertResultItem(
                    itemId,
                    resultId,
                    task.getId(),
                    task.getTargetPlanId(),
                    item.getItemType(),
                    item.getNaturalKey(),
                    item.getNaturalKeyHash(),
                    item.getChangeType(),
                    item.getReviewStatus(),
                    item.getConfidence(),
                    item.getValidationStatus(),
                    item.getNormalizedPayloadJson(),
                    item.getOldPayloadJson(),
                    item.getChangedFieldKeysJson(),
                    item.getEffectivePayloadJson(),
                    item.getEffectiveValidationStatus(),
                    item.getEffectivePayloadHash(),
                    item.getEvidenceJson(),
                    item.getValidationErrorJson(),
                    item.getSortNo(),
                    operatorUserId
            );
            if (itemInserted != 1) {
                throw new IllegalStateException("解析结果行写入失败。");
            }
        }

        fileManagementParseMapper.upsertCurrentResult(task.getId(), resultId);
        int taskUpdated = fileManagementParseMapper.markTaskReviewRequired(task.getId(), resultId, lockOwner, operatorUserId);
        if (taskUpdated != 1) {
            throw new IllegalStateException("解析任务状态更新失败。");
        }
        return new FileParsePersistedResult(resultId, resultNo, structuredResult.getItems().size());
    }

}
