package com.nuono.next.operationsconfig;

import com.nuono.next.infrastructure.mapper.OperationConfigTypedVersionMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisOperationConfigTypedVersionRepository implements OperationConfigTypedVersionRepository {
    private final OperationConfigTypedVersionMapper mapper;

    public MyBatisOperationConfigTypedVersionRepository(OperationConfigTypedVersionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Long nextVersionId() {
        return mapper.nextOperationConfigTypedVersionId();
    }

    @Override
    public void insert(OperationConfigTypedVersion version) {
        mapper.insertVersion(version);
    }

    @Override
    public List<OperationConfigTypedVersion> listVersions() {
        return mapper.selectVersions();
    }

    @Override
    public Optional<OperationConfigTypedVersion> findByVersionNo(String versionNo) {
        return Optional.ofNullable(mapper.selectVersionByVersionNo(versionNo));
    }

    @Override
    public void update(OperationConfigTypedVersion version) {
        mapper.updateVersion(version);
    }

    @Override
    public void deleteByVersionNo(String versionNo) {
        mapper.deleteVersionByVersionNo(versionNo);
    }
}
