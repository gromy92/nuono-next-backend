package com.nuono.next.versioning;

import com.nuono.next.infrastructure.mapper.VersionPublishMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisVersionPublishRepository implements VersionPublishRepository {

    private final VersionPublishMapper mapper;

    public MyBatisVersionPublishRepository(VersionPublishMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Long nextRecordId() {
        return mapper.nextVersionPublishRecordId();
    }

    @Override
    public Long nextAuditId() {
        return mapper.nextVersionPublishAuditLogId();
    }

    @Override
    public void insertRecord(VersionPublishRecord record) {
        mapper.insertRecord(record);
    }

    @Override
    public void updateRecord(VersionPublishRecord record) {
        mapper.updateRecord(record);
    }

    @Override
    public VersionPublishRecord findRecord(Long id) {
        return mapper.selectRecordById(id);
    }

    @Override
    public Optional<VersionPublishRecord> findCurrent(String domainType, Long domainRefId) {
        return Optional.ofNullable(mapper.selectCurrent(domainType, domainRefId));
    }

    @Override
    public List<VersionPublishRecord> listRecords(String domainType, Long domainRefId) {
        return mapper.selectRecords(domainType, domainRefId);
    }

    @Override
    public void insertAudit(VersionPublishAuditLog auditLog) {
        mapper.insertAudit(auditLog);
    }

    @Override
    public List<VersionPublishAuditLog> listAudit(String domainType, Long domainRefId) {
        return mapper.selectAudit(domainType, domainRefId);
    }
}
