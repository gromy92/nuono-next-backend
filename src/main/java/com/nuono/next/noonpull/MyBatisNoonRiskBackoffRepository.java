package com.nuono.next.noonpull;

import com.nuono.next.infrastructure.mapper.NoonRiskBackoffMapper;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local-db")
public class MyBatisNoonRiskBackoffRepository implements NoonRiskBackoffRepository {
    private final NoonRiskBackoffMapper mapper;

    public MyBatisNoonRiskBackoffRepository(NoonRiskBackoffMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void upsert(NoonRiskBackoffHold hold) {
        mapper.upsert(hold);
    }

    @Override
    public NoonRiskBackoffHold selectActiveHold(String scopeKey, LocalDateTime now) {
        return mapper.selectActiveHold(scopeKey, now);
    }

    @Override
    public NoonRiskBackoffHold selectActiveAccountWideHold(Long ownerUserId, String storeCode, String siteCode, LocalDateTime now) {
        return mapper.selectActiveAccountWideHold(ownerUserId, storeCode, siteCode, now);
    }

    @Override
    public NoonRiskBackoffHold selectLatestHold(String scopeKey) {
        return mapper.selectLatestHold(scopeKey);
    }

    @Override
    public int resetAfterSuccess(String scopeKey, String sourceDomain, LocalDateTime resetAt) {
        return mapper.resetAfterSuccess(scopeKey, sourceDomain, resetAt);
    }
}
