package com.nuono.next.foundation;

import com.nuono.next.infrastructure.mapper.FoundationOverviewMapper;
import com.nuono.next.system.CoreTableInspection;
import com.nuono.next.system.LocalDbBootstrapStatusService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbFoundationOverviewService {

    private final FoundationOverviewMapper foundationOverviewMapper;
    private final LocalDbBootstrapStatusService localDbBootstrapStatusService;

    public LocalDbFoundationOverviewService(
            FoundationOverviewMapper foundationOverviewMapper,
            LocalDbBootstrapStatusService localDbBootstrapStatusService
    ) {
        this.foundationOverviewMapper = foundationOverviewMapper;
        this.localDbBootstrapStatusService = localDbBootstrapStatusService;
    }

    public FoundationOverview buildOverview() {
        CoreTableInspection inspection = localDbBootstrapStatusService.inspect();

        FoundationOverview overview = new FoundationOverview();
        overview.setMode("local-db");
        overview.setReady(inspection.isReady());
        overview.setMissingCoreTables(inspection.getMissingTables());

        if (!inspection.isReady()) {
            overview.setMessage("本地库已启用，但第一批核心表还没有补齐，先执行初始化 SQL。");
            return overview;
        }

        overview.setCounts(foundationOverviewMapper.selectStats());
        List<FoundationUserSnapshot> sampleUsers = foundationOverviewMapper.listSampleUsers(8);
        applyScopeSummaries(sampleUsers, listUserScopeSummaries());
        overview.setSampleUsers(sampleUsers);
        overview.setMessage("已接入本地样本库，可以开始推进登录、用户、角色和店铺主链路。");
        return overview;
    }

    public FoundationUserDetail buildUserDetail(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("缺少用户 ID，暂时不能读取迁移详情。");
        }

        CoreTableInspection inspection = localDbBootstrapStatusService.inspect();
        if (!inspection.isReady()) {
            throw new IllegalStateException("本地库第一批核心表还没有补齐，暂时不能读取用户迁移详情。");
        }

        FoundationUserDetail detail = foundationOverviewMapper.selectUserDetail(userId);
        if (detail == null) {
            throw new IllegalArgumentException("当前样本库里没有找到这条用户记录。");
        }
        List<FoundationUserStoreLink> directStoreLinks = foundationOverviewMapper.listUserStoreLinks(userId);
        for (FoundationUserStoreLink link : directStoreLinks) {
            link.setUserId(userId);
            link.setDirectlyOwned(true);
        }
        detail.setStoreLinks(directStoreLinks);
        applyScopeSummary(detail, listUserScopeSummaries().get(userId));
        return detail;
    }

    public Map<Long, FoundationUserScopeSummary> listUserScopeSummaries() {
        List<FoundationUserHierarchyRow> users = foundationOverviewMapper.listUserHierarchyRows();
        List<FoundationUserStoreLink> allStoreLinks = foundationOverviewMapper.listAllUserStoreLinks();

        Map<Long, List<Long>> childrenByCreator = new HashMap<>();
        for (FoundationUserHierarchyRow user : users) {
            if (user.getCreatedBy() == null || user.getId() == null) {
                continue;
            }
            childrenByCreator.computeIfAbsent(user.getCreatedBy(), key -> new ArrayList<>()).add(user.getId());
        }

        Map<Long, List<FoundationUserStoreLink>> directLinksByUser = new HashMap<>();
        for (FoundationUserStoreLink link : allStoreLinks) {
            if (link.getUserId() == null) {
                continue;
            }
            directLinksByUser.computeIfAbsent(link.getUserId(), key -> new ArrayList<>()).add(link);
        }

        Map<Long, FoundationUserScopeSummary> summaries = new HashMap<>();
        for (FoundationUserHierarchyRow user : users) {
            if (user.getId() == null) {
                continue;
            }
            summaries.put(user.getId(), summarizeUserScope(user.getId(), childrenByCreator, directLinksByUser));
        }
        return summaries;
    }

    private void applyScopeSummaries(List<FoundationUserSnapshot> sampleUsers, Map<Long, FoundationUserScopeSummary> summaries) {
        for (FoundationUserSnapshot user : sampleUsers) {
            if (user.getId() == null) {
                continue;
            }
            FoundationUserScopeSummary summary = summaries.get(user.getId());
            if (summary == null) {
                continue;
            }
            user.setManagedStoreCount(summary.getManagedStoreCount());
            user.setManagedCompanyCount(summary.getManagedCompanyCount());
            user.setDescendantUserCount(summary.getDescendantUserCount());
        }
    }

    private void applyScopeSummary(FoundationUserDetail detail, FoundationUserScopeSummary summary) {
        int directCompanyCount = countDistinctCompanies(detail.getStoreLinks()).size();
        detail.setDirectCompanyCount(directCompanyCount);
        detail.setDirectCompanies(joinCompanyLabels(detail.getStoreLinks()));
        if (summary == null) {
            detail.setManagedStoreCount(detail.getStoreCount());
            detail.setManagedAuthorizedStoreCount(detail.getAuthorizedStoreCount());
            detail.setManagedCompanyCount(directCompanyCount);
            detail.setManagedCompanies(detail.getDirectCompanies());
            detail.setDescendantUserCount(0);
            detail.setDescendantStoreCount(0);
            detail.setDescendantCompanyCount(0);
            detail.setDescendantStoreLinks(List.of());
            return;
        }
        detail.setManagedStoreCount(summary.getManagedStoreCount());
        detail.setManagedAuthorizedStoreCount(summary.getManagedAuthorizedStoreCount());
        detail.setManagedCompanyCount(summary.getManagedCompanyCount());
        detail.setManagedCompanies(summary.getManagedCompanies());
        detail.setDescendantUserCount(summary.getDescendantUserCount());
        detail.setDescendantStoreCount(summary.getDescendantStoreCount());
        detail.setDescendantCompanyCount(summary.getDescendantCompanyCount());
        detail.setDescendantStoreLinks(summary.getDescendantStoreLinks());
    }

    private FoundationUserScopeSummary summarizeUserScope(
            Long userId,
            Map<Long, List<Long>> childrenByCreator,
            Map<Long, List<FoundationUserStoreLink>> directLinksByUser
    ) {
        FoundationUserScopeSummary summary = new FoundationUserScopeSummary();
        List<FoundationUserStoreLink> directLinks = directLinksByUser.getOrDefault(userId, List.of());
        summary.setDirectCompanyCount(countDistinctCompanies(directLinks).size());
        summary.setDirectCompanies(joinCompanyLabels(directLinks));

        Set<Long> descendantIds = collectDescendantIds(childrenByCreator, userId);
        summary.setDescendantUserCount(descendantIds.size());

        Map<String, FoundationUserStoreLink> managedLinksByKey = new LinkedHashMap<>();
        for (FoundationUserStoreLink link : directLinks) {
            FoundationUserStoreLink normalized = copyLink(link, true);
            managedLinksByKey.put(storeScopeKey(normalized), normalized);
        }

        List<FoundationUserStoreLink> descendantLinks = new ArrayList<>();
        for (Long descendantId : descendantIds) {
            for (FoundationUserStoreLink link : directLinksByUser.getOrDefault(descendantId, List.of())) {
                FoundationUserStoreLink normalized = copyLink(link, false);
                String scopeKey = storeScopeKey(normalized);
                FoundationUserStoreLink existing = managedLinksByKey.get(scopeKey);
                if (existing == null) {
                    managedLinksByKey.put(scopeKey, normalized);
                    descendantLinks.add(normalized);
                    continue;
                }
                if (!Boolean.TRUE.equals(existing.getAuthorized()) && Boolean.TRUE.equals(normalized.getAuthorized())) {
                    existing.setAuthorized(true);
                }
            }
        }

        List<FoundationUserStoreLink> managedLinks = new ArrayList<>(managedLinksByKey.values());
        summary.setManagedStoreCount(managedLinks.size());
        summary.setManagedAuthorizedStoreCount(countAuthorizedStores(managedLinks));
        summary.setManagedCompanyCount(countDistinctCompanies(managedLinks).size());
        summary.setManagedCompanies(joinCompanyLabels(managedLinks));
        summary.setDescendantStoreCount(descendantLinks.size());
        summary.setDescendantCompanyCount(countDistinctCompanies(descendantLinks).size());
        summary.setDescendantStoreLinks(descendantLinks);
        return summary;
    }

    private Set<Long> collectDescendantIds(Map<Long, List<Long>> childrenByCreator, Long userId) {
        Set<Long> descendantIds = new java.util.LinkedHashSet<>();
        Set<Long> visited = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>(childrenByCreator.getOrDefault(userId, List.of()));
        while (!queue.isEmpty()) {
            Long nextUserId = queue.removeFirst();
            if (nextUserId == null || !visited.add(nextUserId)) {
                continue;
            }
            descendantIds.add(nextUserId);
            queue.addAll(childrenByCreator.getOrDefault(nextUserId, List.of()));
        }
        return descendantIds;
    }

    private int countAuthorizedStores(List<FoundationUserStoreLink> links) {
        int count = 0;
        for (FoundationUserStoreLink link : links) {
            if (Boolean.TRUE.equals(link.getAuthorized())) {
                count++;
            }
        }
        return count;
    }

    private Map<String, String> countDistinctCompanies(List<FoundationUserStoreLink> links) {
        Map<String, String> companies = new LinkedHashMap<>();
        for (FoundationUserStoreLink link : links) {
            String companyKey = companyScopeKey(link);
            String companyLabel = companyScopeLabel(link);
            if (!StringUtils.hasText(companyKey) || companies.containsKey(companyKey)) {
                continue;
            }
            companies.put(companyKey, companyLabel);
        }
        return companies;
    }

    private String joinCompanyLabels(List<FoundationUserStoreLink> links) {
        return String.join("、", countDistinctCompanies(links).values());
    }

    private FoundationUserStoreLink copyLink(FoundationUserStoreLink source, boolean directlyOwned) {
        FoundationUserStoreLink copy = new FoundationUserStoreLink();
        copy.setId(source.getId());
        copy.setUserId(source.getUserId());
        copy.setOrgCode(source.getOrgCode());
        copy.setOrgName(source.getOrgName());
        copy.setProjectCode(source.getProjectCode());
        copy.setProjectName(source.getProjectName());
        copy.setStoreCode(source.getStoreCode());
        copy.setSite(source.getSite());
        copy.setAuthorized(source.getAuthorized());
        copy.setBindStatus(source.getBindStatus());
        copy.setNoonPartnerUser(source.getNoonPartnerUser());
        copy.setNoonPartnerProjectUser(source.getNoonPartnerProjectUser());
        copy.setNoonPartnerId(source.getNoonPartnerId());
        copy.setListLimit(source.getListLimit());
        copy.setCollectLimit(source.getCollectLimit());
        copy.setWhApLimit(source.getWhApLimit());
        copy.setChatgptTranslateLimit(source.getChatgptTranslateLimit());
        copy.setOwnerAccountNo(source.getOwnerAccountNo());
        copy.setOwnerRealName(source.getOwnerRealName());
        copy.setDirectlyOwned(directlyOwned);
        return copy;
    }

    private String storeScopeKey(FoundationUserStoreLink link) {
        return normalizedScopeValue(link.getStoreCode())
                + "#"
                + normalizedScopeValue(link.getSite())
                + "#"
                + normalizedScopeValue(link.getProjectCode())
                + "#"
                + normalizedScopeValue(link.getOrgCode());
    }

    private String companyScopeKey(FoundationUserStoreLink link) {
        String orgCode = normalizedScopeValue(link.getOrgCode());
        if (StringUtils.hasText(orgCode)) {
            return orgCode;
        }
        return normalizedScopeValue(link.getOrgName());
    }

    private String companyScopeLabel(FoundationUserStoreLink link) {
        return StringUtils.hasText(link.getOrgName()) ? link.getOrgName().trim() : normalizedScopeValue(link.getOrgCode());
    }

    private String normalizedScopeValue(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
