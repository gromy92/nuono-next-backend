package com.nuono.next.noonauth;

import com.nuono.next.infrastructure.mapper.NoonAuthRecoveryProjectBatchMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local-db")
public class MyBatisNoonAuthRecoveryProjectBatchCatalog
        implements NoonAuthRecoveryProjectBatchCatalog {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(MyBatisNoonAuthRecoveryProjectBatchCatalog.class);

    private final NoonAuthRecoveryProjectBatchMapper mapper;

    public MyBatisNoonAuthRecoveryProjectBatchCatalog(
            NoonAuthRecoveryProjectBatchMapper mapper
    ) {
        this.mapper = mapper;
    }

    @Override
    public List<NoonAuthRecoveryProjectCandidate> listEligibleProjects(
            Set<String> projectAllowlist
    ) {
        List<NoonAuthRecoveryProjectCandidate> candidates =
                mapper.listEligibleIdentityProjects();
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Set<String> allowlist = projectAllowlist == null ? Set.of() : projectAllowlist;
        Map<String, NoonAuthRecoveryProjectCandidate> unique = new LinkedHashMap<>();
        for (NoonAuthRecoveryProjectCandidate candidate : candidates) {
            if (candidate != null
                    && candidate.getProjectCode() != null
                    && !candidate.getProjectCode().trim().isEmpty()
                    && !allowed(candidate.getProjectCode(), allowlist)) {
                continue;
            }
            if (!valid(candidate)) {
                LOGGER.warn(
                        "Skipping Noon auth identity-batch project with incomplete store/site mapping. "
                                + "ownerUserId={} projectCode={}",
                        candidate == null ? null : candidate.getOwnerUserId(),
                        candidate == null ? null : candidate.getProjectCode()
                );
                continue;
            }
            unique.putIfAbsent(key(candidate), normalized(candidate));
        }
        return List.copyOf(new ArrayList<>(unique.values()));
    }

    private boolean valid(NoonAuthRecoveryProjectCandidate candidate) {
        return candidate != null && NoonAuthRecoveryTargetPolicy.hasCompleteBusinessIdentity(
                candidate.getOwnerUserId(),
                candidate.getProjectCode(),
                candidate.getStoreCode(),
                candidate.getSiteCode()
        );
    }

    private boolean allowed(String projectCode, Set<String> allowlist) {
        return allowlist.isEmpty()
                || allowlist.contains(projectCode.trim().toUpperCase(Locale.ROOT));
    }

    private String key(NoonAuthRecoveryProjectCandidate candidate) {
        return candidate.getOwnerUserId()
                + ":"
                + candidate.getProjectCode().trim().toUpperCase(Locale.ROOT);
    }

    private NoonAuthRecoveryProjectCandidate normalized(
            NoonAuthRecoveryProjectCandidate candidate
    ) {
        String projectCode = candidate.getProjectCode().trim();
        return new NoonAuthRecoveryProjectCandidate(
                candidate.getOwnerUserId(),
                projectCode,
                candidate.getStoreCode().trim(),
                NoonAuthRecoveryTargetPolicy.normalizeSite(candidate.getSiteCode())
        );
    }
}
