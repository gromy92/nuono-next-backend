package com.nuono.next.noonauth;

import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.Locale;

final class NoonAuthRecoveryStoreTargetResolver {

    private NoonAuthRecoveryStoreTargetResolver() {
    }

    static NoonAuthRecoveryProjectCandidate resolve(
            StoreSyncMapper mapper,
            Long ownerUserId,
            String projectCode,
            String storeCode
    ) {
        String project = NoonAuthRecoveryTargetPolicy.normalize(projectCode);
        String requestedStore = NoonAuthRecoveryTargetPolicy.normalize(storeCode);
        if (mapper == null
                || requestedStore == null
                || requestedStore.toUpperCase(Locale.ROOT).startsWith("PRJ")) {
            return null;
        }
        if (project == null) {
            StoreSyncStoreRecord mappedProject = mapper.selectOwnerProject(ownerUserId, requestedStore);
            project = mappedProject == null
                    ? null
                    : NoonAuthRecoveryTargetPolicy.normalize(mappedProject.getProjectCode());
        }
        if (project == null) {
            return null;
        }
        StoreSyncStoreRecord store = mapper.selectOwnerStore(ownerUserId, requestedStore);
        String resolvedStore = store == null
                ? null
                : NoonAuthRecoveryTargetPolicy.normalize(store.getStoreCode());
        String resolvedSite = store == null
                ? null
                : NoonAuthRecoveryTargetPolicy.normalizeSite(store.getSite());
        if (store == null
                || !project.equals(NoonAuthRecoveryTargetPolicy.normalize(store.getProjectCode()))
                || !requestedStore.equals(resolvedStore)
                || !NoonAuthRecoveryTargetPolicy.hasCompleteBusinessIdentity(
                        ownerUserId, project, resolvedStore, resolvedSite
                )) {
            return null;
        }
        return new NoonAuthRecoveryProjectCandidate(
                ownerUserId, project, resolvedStore, resolvedSite
        );
    }
}
