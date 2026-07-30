package com.nuono.next.noonauth;

import com.nuono.next.infrastructure.mapper.NoonAuthRecoveryProjectBatchMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@Profile("local-db")
public class MyBatisNoonAuthRecoveryProjectBatchCatalog
        implements NoonAuthRecoveryProjectBatchCatalog {
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
            if (!valid(candidate) || !allowed(candidate.getProjectCode(), allowlist)) {
                continue;
            }
            unique.putIfAbsent(key(candidate), normalized(candidate));
        }
        return List.copyOf(new ArrayList<>(unique.values()));
    }

    private boolean valid(NoonAuthRecoveryProjectCandidate candidate) {
        return candidate != null
                && candidate.getOwnerUserId() != null
                && StringUtils.hasText(candidate.getProjectCode());
    }

    private boolean allowed(String projectCode, Set<String> allowlist) {
        return allowlist.isEmpty()
                || allowlist.contains(projectCode.trim().toUpperCase(Locale.ROOT));
    }

    private String key(NoonAuthRecoveryProjectCandidate candidate) {
        return candidate.getOwnerUserId() + ":" + candidate.getProjectCode().trim();
    }

    private NoonAuthRecoveryProjectCandidate normalized(
            NoonAuthRecoveryProjectCandidate candidate
    ) {
        String projectCode = candidate.getProjectCode().trim();
        String storeCode = StringUtils.hasText(candidate.getStoreCode())
                ? candidate.getStoreCode().trim()
                : projectCode;
        return new NoonAuthRecoveryProjectCandidate(
                candidate.getOwnerUserId(),
                projectCode,
                storeCode
        );
    }
}
