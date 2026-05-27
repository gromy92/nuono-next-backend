package com.nuono.next.operationsconfig;

import java.util.List;
import java.util.Optional;

public interface OperationConfigTypedVersionRepository {
    Long nextVersionId();

    void insert(OperationConfigTypedVersion version);

    List<OperationConfigTypedVersion> listVersions();

    Optional<OperationConfigTypedVersion> findByVersionNo(String versionNo);

    void update(OperationConfigTypedVersion version);

    void deleteByVersionNo(String versionNo);
}
