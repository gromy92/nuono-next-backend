package com.nuono.next.versioning;

import java.util.List;
import java.util.Optional;

public interface VersionPublishRepository {

    Long nextRecordId();

    Long nextAuditId();

    void insertRecord(VersionPublishRecord record);

    void updateRecord(VersionPublishRecord record);

    VersionPublishRecord findRecord(Long id);

    Optional<VersionPublishRecord> findCurrent(String domainType, Long domainRefId);

    List<VersionPublishRecord> listRecords(String domainType, Long domainRefId);

    void insertAudit(VersionPublishAuditLog auditLog);

    List<VersionPublishAuditLog> listAudit(String domainType, Long domainRefId);
}
