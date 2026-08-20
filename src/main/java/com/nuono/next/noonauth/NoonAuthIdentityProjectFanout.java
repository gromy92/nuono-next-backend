package com.nuono.next.noonauth;

import com.nuono.next.infrastructure.mapper.NoonAccountSessionMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noon.NoonAccountSessionProjectTarget;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local-db")
class NoonAuthIdentityProjectFanout {
    private final NoonAccountSessionMapper accountSessionMapper;
    private final StoreSyncMapper storeSyncMapper;
    private final NoonAuthRecoveryProperties properties;

    NoonAuthIdentityProjectFanout(
            NoonAccountSessionMapper accountSessionMapper,
            StoreSyncMapper storeSyncMapper,
            NoonAuthRecoveryProperties properties
    ) {
        this.accountSessionMapper = accountSessionMapper;
        this.storeSyncMapper = storeSyncMapper;
        this.properties = properties;
    }

    void enqueueAll(NoonAuthWaitQueue queue, Long triggeringOwnerId, String triggeringProjectCode) {
        for (NoonAccountSessionProjectTarget binding : accountSessionMapper.listBoundProjects()) {
            if (binding == null || !properties.allowsProject(binding.getProjectCode())) {
                continue;
            }
            if (java.util.Objects.equals(triggeringOwnerId, binding.getOwnerUserId())
                    && java.util.Objects.equals(triggeringProjectCode, binding.getProjectCode())) {
                continue;
            }
            NoonAuthRecoveryProjectCandidate target = NoonAuthRecoveryStoreTargetResolver.resolve(
                    storeSyncMapper,
                    binding.getOwnerUserId(),
                    binding.getProjectCode(),
                    binding.getStoreCode()
            );
            if (target != null) {
                queue.enqueue(NoonAuthWaitRequest.identityBatch(
                        target.getOwnerUserId(), target.getProjectCode(), target.getStoreCode()
                ));
            }
        }
    }
}
