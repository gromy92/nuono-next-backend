package com.nuono.next.noonauth;

import com.nuono.next.infrastructure.mapper.NoonAuthTransientBackoffMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.noonauth.gateway.NoonTransientErrorType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("local-db")
public class MyBatisNoonAuthTransientBackoffRepository
        implements NoonAuthTransientBackoffRepository {

    private final NoonAuthTransientBackoffMapper mapper;
    private final ProductManagementMapper productManagementMapper;

    @Autowired
    public MyBatisNoonAuthTransientBackoffRepository(
            NoonAuthTransientBackoffMapper mapper,
            ProductManagementMapper productManagementMapper
    ) {
        this.mapper = mapper;
        this.productManagementMapper = productManagementMapper;
    }

    MyBatisNoonAuthTransientBackoffRepository(NoonAuthTransientBackoffMapper mapper) {
        this(mapper, null);
    }

    @Override
    @Transactional
    public Long resolveLogicalStoreId(Long ownerUserId, String projectCode) {
        Long logicalStoreId = mapper.resolveLogicalStoreId(ownerUserId, projectCode);
        if (logicalStoreId != null || productManagementMapper == null) {
            return logicalStoreId;
        }
        Long allocatedId = productManagementMapper.nextLogicalStoreId();
        mapper.insertLogicalStoreIfAbsent(
                allocatedId,
                ownerUserId,
                projectCode
        );
        return mapper.resolveLogicalStoreId(ownerUserId, projectCode);
    }

    @Override
    @Transactional
    public NoonAuthTransientBackoffState incrementFailure(
            NoonAuthTransientBackoffState failure,
            NoonAuthTransientBackoffWriteFence fence,
            LocalDateTime now
    ) {
        if (!lockAndValidateFence(fence)) {
            return null;
        }
        mapper.incrementFailure(failure);
        return mapper.selectState(failure.getLogicalStoreId(), failure.getErrorType());
    }

    @Override
    public NoonAuthTransientBackoffState selectState(
            Long logicalStoreId,
            NoonTransientErrorType errorType
    ) {
        return mapper.selectState(logicalStoreId, errorType);
    }

    @Override
    public List<NoonAuthTransientBackoffState> listActiveHolds(
            Long logicalStoreId,
            LocalDateTime now
    ) {
        return mapper.listActiveHolds(logicalStoreId, now);
    }

    @Override
    public boolean hasFailureForRecovery(Long logicalStoreId, Long recoveryId) {
        return mapper.countFailuresForRecovery(logicalStoreId, recoveryId) > 0;
    }

    @Override
    @Transactional
    public boolean resetForRecovery(
            Long logicalStoreId,
            Long recoveryId,
            NoonAuthTransientBackoffWriteFence fence,
            LocalDateTime resetAt
    ) {
        if (!lockAndValidateFence(fence)) {
            return false;
        }
        mapper.resetForRecovery(logicalStoreId, recoveryId, resetAt);
        return true;
    }

    private boolean lockAndValidateFence(NoonAuthTransientBackoffWriteFence fence) {
        return fence != null
                && mapper.lockRecoveryById(fence.getRecoveryId()) != null
                && mapper.countCurrentRecoveryFence(fence) == 1;
    }
}
