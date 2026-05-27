package com.nuono.next.versioning;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class VersionPublishService {

    private final VersionPublishRepository repository;
    private final Clock clock;

    @Autowired
    public VersionPublishService(VersionPublishRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    VersionPublishService(VersionPublishRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public VersionPublishRecord createDraft(VersionPublishDraftCommand command) {
        requireText(command.getDomainType(), "domainType");
        requirePositive(command.getDomainRefId(), "domainRefId");
        requireText(command.getVersionNo(), "versionNo");
        VersionPublishOperator operator = requireOperator(command.getOperator());
        LocalDateTime now = now();
        VersionPublishRecord record = new VersionPublishRecord(
                repository.nextRecordId(),
                command.getDomainType(),
                command.getDomainRefId(),
                command.getVersionNo(),
                VersionPublishStatus.DRAFT,
                command.getScopeSummary(),
                null,
                null,
                null,
                null,
                operator.getOperatorUserId(),
                operator.getOperatorUserId(),
                now,
                now
        );
        repository.insertRecord(record);
        audit(record, VersionPublishAction.CREATE_DRAFT, operator, null, null, "create draft", now);
        return record;
    }

    public VersionPublishRecord updateDraft(VersionPublishDraftUpdateCommand command) {
        VersionPublishRecord record = requireRecord(command.getRecordId());
        requireStatus(record, VersionPublishStatus.DRAFT, "Only draft versions can be edited");
        VersionPublishOperator operator = requireOperator(command.getOperator());
        LocalDateTime now = now();
        VersionPublishRecord updated = record.withDraftEdit(
                command.getScopeSummary(),
                operator.getOperatorUserId(),
                now
        );
        repository.updateRecord(updated);
        audit(
                updated,
                VersionPublishAction.EDIT_DRAFT,
                operator,
                command.getBeforeSnapshot(),
                command.getAfterSnapshot(),
                command.getMessage(),
                now
        );
        return updated;
    }

    public VersionPublishRecord publish(VersionPublishActionCommand command) {
        VersionPublishRecord record = requireRecord(command.getRecordId());
        requireStatus(record, VersionPublishStatus.DRAFT, "Only draft versions can be published");
        VersionPublishOperator operator = requireOperator(command.getOperator());
        LocalDateTime now = now();
        Optional<VersionPublishRecord> previousCurrent = repository.findCurrent(
                record.getDomainType(),
                record.getDomainRefId()
        );
        previousCurrent.ifPresent(previous -> repository.updateRecord(
                previous.withHistorical(operator.getOperatorUserId(), now)
        ));
        VersionPublishRecord published = record.withPublished(
                operator.getOperatorUserId(),
                now,
                command.getMessage(),
                previousCurrent.map(VersionPublishRecord::getId).orElse(null)
        );
        repository.updateRecord(published);
        audit(
                published,
                VersionPublishAction.PUBLISH,
                operator,
                null,
                command.getAfterSnapshot(),
                command.getMessage(),
                now
        );
        return published;
    }

    public VersionPublishRecord disable(VersionPublishActionCommand command) {
        VersionPublishRecord record = requireRecord(command.getRecordId());
        VersionPublishOperator operator = requireOperator(command.getOperator());
        LocalDateTime now = now();
        VersionPublishRecord disabled = record.withDisabled(
                operator.getOperatorUserId(),
                now,
                command.getMessage()
        );
        repository.updateRecord(disabled);
        audit(
                disabled,
                VersionPublishAction.DISABLE,
                operator,
                null,
                command.getAfterSnapshot(),
                command.getMessage(),
                now
        );
        return disabled;
    }

    public VersionPublishRecord copyFromVersion(VersionPublishCopyCommand command) {
        VersionPublishRecord source = requireRecord(command.getSourceRecordId());
        requireText(command.getVersionNo(), "versionNo");
        VersionPublishOperator operator = requireOperator(command.getOperator());
        LocalDateTime now = now();
        VersionPublishRecord copied = new VersionPublishRecord(
                repository.nextRecordId(),
                source.getDomainType(),
                command.getTargetDomainRefId() == null ? source.getDomainRefId() : command.getTargetDomainRefId(),
                command.getVersionNo(),
                VersionPublishStatus.DRAFT,
                command.getScopeSummary(),
                source.getId(),
                null,
                null,
                command.getMessage(),
                operator.getOperatorUserId(),
                operator.getOperatorUserId(),
                now,
                now
        );
        repository.insertRecord(copied);
        audit(
                copied,
                VersionPublishAction.COPY_FROM_VERSION,
                operator,
                "{\"sourceRecordId\":" + source.getId() + "}",
                null,
                command.getMessage(),
                now
        );
        return copied;
    }

    public VersionPublishRecord markHistorical(
            Long recordId,
            VersionPublishOperator operator,
            String message
    ) {
        VersionPublishRecord record = requireRecord(recordId);
        if (!VersionPublishStatus.PUBLISHED.equals(record.getStatus())) {
            return record;
        }
        VersionPublishOperator safeOperator = requireOperator(operator);
        LocalDateTime now = now();
        VersionPublishRecord historical = record.withHistorical(safeOperator.getOperatorUserId(), now);
        repository.updateRecord(historical);
        return historical;
    }

    public Optional<VersionPublishRecord> findCurrent(String domainType, Long domainRefId) {
        requireText(domainType, "domainType");
        requirePositive(domainRefId, "domainRefId");
        return repository.findCurrent(domainType, domainRefId);
    }

    public List<VersionPublishRecord> listHistory(String domainType, Long domainRefId) {
        requireText(domainType, "domainType");
        requirePositive(domainRefId, "domainRefId");
        return repository.listRecords(domainType, domainRefId);
    }

    public List<VersionPublishAuditLog> listAudit(String domainType, Long domainRefId) {
        requireText(domainType, "domainType");
        requirePositive(domainRefId, "domainRefId");
        return repository.listAudit(domainType, domainRefId);
    }

    private void audit(
            VersionPublishRecord record,
            VersionPublishAction action,
            VersionPublishOperator operator,
            String beforeSnapshot,
            String afterSnapshot,
            String message,
            LocalDateTime now
    ) {
        repository.insertAudit(new VersionPublishAuditLog(
                repository.nextAuditId(),
                record.getId(),
                record.getDomainType(),
                record.getDomainRefId(),
                action,
                operator.getOperatorUserId(),
                operator.getOperatorRole(),
                beforeSnapshot,
                afterSnapshot,
                message,
                now
        ));
    }

    private VersionPublishRecord requireRecord(Long recordId) {
        requirePositive(recordId, "recordId");
        VersionPublishRecord record = repository.findRecord(recordId);
        if (record == null) {
            throw new IllegalArgumentException("Version publish record not found: " + recordId);
        }
        return record;
    }

    private void requireStatus(VersionPublishRecord record, VersionPublishStatus expected, String message) {
        if (!expected.equals(record.getStatus())) {
            throw new IllegalStateException(message + ": " + record.getStatus());
        }
    }

    private VersionPublishOperator requireOperator(VersionPublishOperator operator) {
        if (operator == null) {
            throw new IllegalArgumentException("operator is required");
        }
        requirePositive(operator.getOperatorUserId(), "operatorUserId");
        requireText(operator.getOperatorRole(), "operatorRole");
        return operator;
    }

    private void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
