package com.nuono.next.noonauth;

import com.nuono.next.noonauth.gateway.NoonAuthRecoveryProjectTarget;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

final class NoonAuthRecoveryWorkerValues {

    private NoonAuthRecoveryWorkerValues() {
    }

    static List<NoonAuthRecoveryProjectTarget> uniqueTargets(
            List<NoonAuthRecoveryItemRecord> items
    ) {
        List<NoonAuthRecoveryProjectTarget> targets = new ArrayList<>();
        for (NoonAuthRecoveryItemRecord item : uniqueProjectItems(items).values()) {
            targets.add(target(item));
        }
        return targets;
    }

    static NoonAuthRecoveryProjectTarget target(NoonAuthRecoveryItemRecord item) {
        return new NoonAuthRecoveryProjectTarget(
                item.getOwnerUserId(), item.getProjectCode(), item.getStoreCode(),
                item.getSiteCode(), safeLong(item.getExpectedAuthVersion())
        );
    }

    static Map<String, NoonAuthRecoveryItemRecord> uniqueProjectItems(
            List<NoonAuthRecoveryItemRecord> items
    ) {
        Map<String, NoonAuthRecoveryItemRecord> projects = new LinkedHashMap<>();
        if (items == null) {
            return projects;
        }
        for (NoonAuthRecoveryItemRecord item : items) {
            if (item != null
                    && item.getOwnerUserId() != null
                    && StringUtils.hasText(item.getProjectCode())) {
                projects.putIfAbsent(projectKey(item), item);
            }
        }
        return projects;
    }

    static Set<String> excludedMessageHashes(NoonAuthIdentityRecoveryRecord candidate) {
        Set<String> hashes = new LinkedHashSet<>();
        if (StringUtils.hasText(candidate.getLastMailUidHash())) {
            hashes.add(candidate.getLastMailUidHash());
        }
        if (StringUtils.hasText(candidate.getLastMessageIdHash())) {
            hashes.add(candidate.getLastMessageIdHash());
        }
        return hashes;
    }

    static boolean isInterruptedAttempt(NoonAuthRecoveryStatus status) {
        return status == NoonAuthRecoveryStatus.AUTHENTICATING
                || status == NoonAuthRecoveryStatus.WAITING_EMAIL
                || status == NoonAuthRecoveryStatus.VALIDATING
                || status == NoonAuthRecoveryStatus.APPLYING_PROJECTS
                || status == NoonAuthRecoveryStatus.RECOVERING_PULLS;
    }

    static String projectKey(NoonAuthRecoveryItemRecord item) {
        return item.getOwnerUserId() + ":" + item.getProjectCode();
    }

    static long safeLong(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }
}
