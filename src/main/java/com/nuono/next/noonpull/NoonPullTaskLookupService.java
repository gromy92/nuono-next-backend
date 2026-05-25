package com.nuono.next.noonpull;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NoonPullTaskLookupService {
    private final NoonPullFoundationService foundationService;

    public NoonPullTaskLookupService(NoonPullFoundationService foundationService) {
        this.foundationService = foundationService;
    }

    public Optional<NoonPullTaskRecord> latestTask(NoonPullTaskLookupQuery query) {
        if (query == null) {
            return Optional.empty();
        }
        return foundationService.listTasks().stream()
                .filter((task) -> matches(query, task))
                .max(Comparator
                        .comparing(this::taskTimestamp, Comparator.nullsLast(LocalDateTime::compareTo))
                        .thenComparing(NoonPullTaskRecord::getId, Comparator.nullsLast(Long::compareTo)))
                .map(NoonPullTaskRecord::copy);
    }

    public LocalDateTime taskTimestamp(NoonPullTaskRecord task) {
        if (task == null) {
            return null;
        }
        if (task.getFinishedAt() != null) {
            return task.getFinishedAt();
        }
        if (task.getStartedAt() != null) {
            return task.getStartedAt();
        }
        if (task.getQueuedAt() != null) {
            return task.getQueuedAt();
        }
        return task.getUpdatedAt();
    }

    private boolean matches(NoonPullTaskLookupQuery query, NoonPullTaskRecord task) {
        return matchesOwner(query, task)
                && matchesStore(query, task)
                && matchesSite(query, task)
                && matchesPullType(query, task)
                && matchesDataDomain(query, task)
                && matchesTargetPrefix(query, task);
    }

    private boolean matchesOwner(NoonPullTaskLookupQuery query, NoonPullTaskRecord task) {
        return query.getOwnerUserId() == null || Objects.equals(query.getOwnerUserId(), task.getOwnerUserId());
    }

    private boolean matchesStore(NoonPullTaskLookupQuery query, NoonPullTaskRecord task) {
        return !StringUtils.hasText(query.getStoreCode())
                || Objects.equals(normalize(query.getStoreCode()), normalize(task.getStoreCode()));
    }

    private boolean matchesSite(NoonPullTaskLookupQuery query, NoonPullTaskRecord task) {
        return !StringUtils.hasText(query.getSiteCode())
                || Objects.equals(normalize(query.getSiteCode()), normalize(task.getSiteCode()));
    }

    private boolean matchesPullType(NoonPullTaskLookupQuery query, NoonPullTaskRecord task) {
        return query.getPullType() == null || query.getPullType() == task.getPullType();
    }

    private boolean matchesDataDomain(NoonPullTaskLookupQuery query, NoonPullTaskRecord task) {
        return query.getDataDomain() == null || query.getDataDomain() == task.getDataDomain();
    }

    private boolean matchesTargetPrefix(NoonPullTaskLookupQuery query, NoonPullTaskRecord task) {
        if (!StringUtils.hasText(query.getTargetIdentityPrefix())) {
            return true;
        }
        String target = normalize(task.getTargetIdentity());
        return target != null && target.startsWith(normalize(query.getTargetIdentityPrefix()));
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }
}
