package com.nuono.next.operationsconfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryOperationConfigTypedVersionRepository implements OperationConfigTypedVersionRepository {
    private long nextId = 91000L;
    private final Map<String, OperationConfigTypedVersion> versions = new LinkedHashMap<>();

    @Override
    public Long nextVersionId() {
        nextId += 1;
        return nextId;
    }

    @Override
    public void insert(OperationConfigTypedVersion version) {
        versions.put(version.getVersionNo(), version);
    }

    @Override
    public List<OperationConfigTypedVersion> listVersions() {
        return new ArrayList<>(versions.values());
    }

    @Override
    public Optional<OperationConfigTypedVersion> findByVersionNo(String versionNo) {
        return Optional.ofNullable(versions.get(versionNo));
    }

    @Override
    public void update(OperationConfigTypedVersion version) {
        versions.put(version.getVersionNo(), version);
    }

    @Override
    public void deleteByVersionNo(String versionNo) {
        versions.remove(versionNo);
    }

    public void replaceWith(List<OperationConfigTypedVersion> nextVersions) {
        versions.clear();
        for (OperationConfigTypedVersion version : nextVersions) {
            insert(version);
        }
    }
}
