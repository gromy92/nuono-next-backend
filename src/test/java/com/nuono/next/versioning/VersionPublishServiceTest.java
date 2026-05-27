package com.nuono.next.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class VersionPublishServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-22T04:00:00Z"),
            ZoneId.of("UTC")
    );
    private static final VersionPublishOperator BOSS_OPERATOR =
            new VersionPublishOperator(88L, "boss");

    @Test
    void springContextCanCreateServiceWithRepositoryConstructor() {
        new ApplicationContextRunner()
                .withBean(VersionPublishRepository.class, InMemoryVersionPublishRepository::new)
                .withBean(VersionPublishService.class)
                .run(context -> assertTrue(
                        context.getStartupFailure() == null,
                        () -> String.valueOf(context.getStartupFailure())
                ));
    }

    @Test
    void publishingDraftMakesItCurrentAndArchivesPreviousPublishedVersion() {
        InMemoryVersionPublishRepository repository = new InMemoryVersionPublishRepository();
        VersionPublishService service = new VersionPublishService(repository, FIXED_CLOCK);

        VersionPublishRecord firstDraft = service.createDraft(new VersionPublishDraftCommand(
                "advanced_operations_config",
                101L,
                "calendar-2026-v1",
                "boss=7 stores=all",
                BOSS_OPERATOR
        ));
        VersionPublishRecord firstPublished = service.publish(new VersionPublishActionCommand(
                firstDraft.getId(),
                BOSS_OPERATOR,
                "{\"ruleCount\":1}",
                "publish first calendar config"
        ));

        assertEquals(VersionPublishStatus.PUBLISHED, firstPublished.getStatus());
        assertEquals(firstPublished.getId(), service.findCurrent("advanced_operations_config", 101L)
                .orElseThrow()
                .getId());

        VersionPublishRecord secondDraft = service.createDraft(new VersionPublishDraftCommand(
                "advanced_operations_config",
                101L,
                "calendar-2026-v2",
                "boss=7 stores=all",
                BOSS_OPERATOR
        ));
        VersionPublishRecord secondPublished = service.publish(new VersionPublishActionCommand(
                secondDraft.getId(),
                BOSS_OPERATOR,
                "{\"ruleCount\":2}",
                "replace calendar config"
        ));

        assertEquals(VersionPublishStatus.HISTORICAL, repository.findRecord(firstPublished.getId()).getStatus());
        assertEquals(VersionPublishStatus.PUBLISHED, secondPublished.getStatus());
        assertEquals(firstPublished.getId(), secondPublished.getPreviousVersionId());
        assertEquals(secondPublished.getId(), service.findCurrent("advanced_operations_config", 101L)
                .orElseThrow()
                .getId());
    }

    @Test
    void recordsAuditEntriesForDraftEditPublishCopyAndDisableWithOperatorRole() {
        InMemoryVersionPublishRepository repository = new InMemoryVersionPublishRepository();
        VersionPublishService service = new VersionPublishService(repository, FIXED_CLOCK);

        VersionPublishRecord draft = service.createDraft(new VersionPublishDraftCommand(
                "advanced_operations_config",
                202L,
                "lifecycle-v1",
                "boss=7 stores=all",
                BOSS_OPERATOR
        ));
        service.updateDraft(new VersionPublishDraftUpdateCommand(
                draft.getId(),
                "boss=7 stores=A,B",
                BOSS_OPERATOR,
                "{\"scope\":\"all\"}",
                "{\"scope\":\"A,B\"}",
                "narrow store scope"
        ));
        VersionPublishRecord published = service.publish(new VersionPublishActionCommand(
                draft.getId(),
                BOSS_OPERATOR,
                "{\"thresholds\":\"default-v1\"}",
                "publish lifecycle rules"
        ));
        service.copyFromVersion(new VersionPublishCopyCommand(
                published.getId(),
                "lifecycle-v2",
                "boss=7 stores=A,B",
                BOSS_OPERATOR,
                "copy for next revision"
        ));
        service.disable(new VersionPublishActionCommand(
                published.getId(),
                BOSS_OPERATOR,
                "{\"disabled\":true}",
                "disable active lifecycle rules"
        ));

        List<VersionPublishAuditLog> auditLogs = repository.listAudit("advanced_operations_config", 202L);
        assertIterableEquals(
                List.of(
                        VersionPublishAction.CREATE_DRAFT,
                        VersionPublishAction.EDIT_DRAFT,
                        VersionPublishAction.PUBLISH,
                        VersionPublishAction.COPY_FROM_VERSION,
                        VersionPublishAction.DISABLE
                ),
                auditLogs.stream().map(VersionPublishAuditLog::getAction).collect(Collectors.toList())
        );
        assertTrue(auditLogs.stream().allMatch(log -> BOSS_OPERATOR.getOperatorUserId().equals(log.getOperatorUserId())));
        assertTrue(auditLogs.stream().allMatch(log -> BOSS_OPERATOR.getOperatorRole().equals(log.getOperatorRole())));
        assertEquals("{\"scope\":\"all\"}", auditLogs.get(1).getBeforeSnapshot());
        assertEquals("{\"scope\":\"A,B\"}", auditLogs.get(1).getAfterSnapshot());
        assertFalse(service.findCurrent("advanced_operations_config", 202L).isPresent());
    }

    @Test
    void currentVersionLookupIsIsolatedByDomainTypeAndReference() {
        InMemoryVersionPublishRepository repository = new InMemoryVersionPublishRepository();
        VersionPublishService service = new VersionPublishService(repository, FIXED_CLOCK);

        VersionPublishRecord operations = publish(service, "advanced_operations_config", 301L, "calendar-v1");
        VersionPublishRecord forecast = publish(service, "sales_forecast_rule", 301L, "forecast-v1");
        VersionPublishRecord otherOperations = publish(service, "advanced_operations_config", 302L, "lifecycle-v1");

        assertEquals(operations.getId(), service.findCurrent("advanced_operations_config", 301L)
                .orElseThrow()
                .getId());
        assertEquals(forecast.getId(), service.findCurrent("sales_forecast_rule", 301L)
                .orElseThrow()
                .getId());
        assertEquals(otherOperations.getId(), service.findCurrent("advanced_operations_config", 302L)
                .orElseThrow()
                .getId());
    }

    private static VersionPublishRecord publish(
            VersionPublishService service,
            String domainType,
            Long domainRefId,
            String versionNo
    ) {
        VersionPublishRecord draft = service.createDraft(new VersionPublishDraftCommand(
                domainType,
                domainRefId,
                versionNo,
                "scope-summary",
                BOSS_OPERATOR
        ));
        return service.publish(new VersionPublishActionCommand(
                draft.getId(),
                BOSS_OPERATOR,
                "{\"version\":\"" + versionNo + "\"}",
                "publish"
        ));
    }

    private static class InMemoryVersionPublishRepository implements VersionPublishRepository {
        private long nextRecordId = 1000L;
        private long nextAuditId = 9000L;
        private final Map<Long, VersionPublishRecord> records = new LinkedHashMap<>();
        private final List<VersionPublishAuditLog> auditLogs = new ArrayList<>();

        @Override
        public Long nextRecordId() {
            nextRecordId += 1;
            return nextRecordId;
        }

        @Override
        public Long nextAuditId() {
            nextAuditId += 1;
            return nextAuditId;
        }

        @Override
        public void insertRecord(VersionPublishRecord record) {
            records.put(record.getId(), record);
        }

        @Override
        public void updateRecord(VersionPublishRecord record) {
            records.put(record.getId(), record);
        }

        @Override
        public VersionPublishRecord findRecord(Long id) {
            return records.get(id);
        }

        @Override
        public Optional<VersionPublishRecord> findCurrent(String domainType, Long domainRefId) {
            return records.values().stream()
                    .filter(record -> domainType.equals(record.getDomainType()))
                    .filter(record -> domainRefId.equals(record.getDomainRefId()))
                    .filter(record -> VersionPublishStatus.PUBLISHED.equals(record.getStatus()))
                    .max(Comparator.comparing(VersionPublishRecord::getPublishedAt)
                            .thenComparing(VersionPublishRecord::getId));
        }

        @Override
        public List<VersionPublishRecord> listRecords(String domainType, Long domainRefId) {
            return records.values().stream()
                    .filter(record -> domainType.equals(record.getDomainType()))
                    .filter(record -> domainRefId.equals(record.getDomainRefId()))
                    .collect(Collectors.toList());
        }

        @Override
        public void insertAudit(VersionPublishAuditLog auditLog) {
            auditLogs.add(auditLog);
        }

        @Override
        public List<VersionPublishAuditLog> listAudit(String domainType, Long domainRefId) {
            return auditLogs.stream()
                    .filter(log -> domainType.equals(log.getDomainType()))
                    .filter(log -> domainRefId.equals(log.getDomainRefId()))
                    .collect(Collectors.toList());
        }
    }
}
