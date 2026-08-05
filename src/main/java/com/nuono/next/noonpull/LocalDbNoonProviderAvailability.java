package com.nuono.next.noonpull;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.store.StoreSyncStoreRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Prevents scheduled and gap-planned tasks from being created while their Noon Project is
 * already known to require authorization recovery.
 */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.LEGACY)
public class LocalDbNoonProviderAvailability implements NoonProviderAvailability {
    private final StoreSyncMapper storeSyncMapper;
    private final NoonPullProjectAuthGate authGate;

    public LocalDbNoonProviderAvailability(
            StoreSyncMapper storeSyncMapper,
            NoonPullProjectAuthGate authGate
    ) {
        this.storeSyncMapper = storeSyncMapper;
        this.authGate = authGate;
    }

    @Override
    public boolean isAvailable(NoonPullPlanRecord plan) {
        if (plan == null || plan.getOwnerUserId() == null || !StringUtils.hasText(plan.getStoreCode())) {
            return true;
        }
        StoreSyncStoreRecord project =
                storeSyncMapper.selectOwnerProject(plan.getOwnerUserId(), plan.getStoreCode());
        if (project == null || !StringUtils.hasText(project.getProjectCode())) {
            return true;
        }
        return !authGate.isBlocked(plan.getOwnerUserId(), project.getProjectCode().trim());
    }
}
