package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.product.noon.NoonProductGateway;
import com.nuono.next.product.noon.ProductNoonAdapter;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

class ProductProjectSiteResolver {

    private static final Logger log = LoggerFactory.getLogger(ProductProjectSiteResolver.class);

    private final ObjectMapper objectMapper;
    private final StoreSyncMapper storeSyncMapper;
    private final ProductNoonAdapter productNoonAdapter;
    private final ConcurrentMap<String, ResolvedProjectCodeCacheEntry> resolvedProjectCodeCache =
            new ConcurrentHashMap<>();

    ProductProjectSiteResolver(
            ObjectMapper objectMapper,
            StoreSyncMapper storeSyncMapper,
            ProductNoonAdapter productNoonAdapter
    ) {
        this.objectMapper = objectMapper;
        this.storeSyncMapper = storeSyncMapper;
        this.productNoonAdapter = productNoonAdapter;
    }

    List<ProductProjectSiteContext> loadProjectSiteContexts(
            NoonSession session,
            Long ownerUserId,
            StoreSyncStoreRecord store,
            List<String> warnings
    ) {
        Map<String, ProductProjectSiteContext> siteMap = new LinkedHashMap<>();
        List<StoreSyncStoreRecord> localProjectStores = findRelatedStores(ownerUserId, store);
        for (StoreSyncStoreRecord localStore : localProjectStores) {
            if (!StringUtils.hasText(localStore.getStoreCode())) {
                continue;
            }
            siteMap.put(
                    localStore.getStoreCode(),
                    new ProductProjectSiteContext(localStore.getStoreCode(), localStore.getSite(), null)
            );
        }

        ObjectNode storeListBody = objectMapper.createObjectNode();
        storeListBody.put("noonStoreCode", "");
        long storeListStartedAt = System.nanoTime();
        JsonNode storeListRoot = safePost(
                session,
                NoonProductGateway.STORE_LIST_URL,
                storeListBody,
                true,
                warnings,
                "读取项目站点列表失败"
        );
        log.info(
                "product-management fetchSnapshot detail stage=store.list store={} durationMs={}",
                normalize(store.getStoreCode()),
                nanosToMillis(storeListStartedAt)
        );
        JsonNode noonStoresNode = storeListRoot.path("noonStores");
        if (noonStoresNode.isArray()) {
            for (JsonNode siteNode : noonStoresNode) {
                String liveStoreCode = text(siteNode, "noonStoreCode");
                if (!StringUtils.hasText(liveStoreCode)) {
                    continue;
                }

                String liveSite = firstNonBlank(text(siteNode, "countryCode"), deriveSiteFromStoreCode(liveStoreCode));
                String statusCode = text(siteNode, "statusCode");
                ProductProjectSiteContext existing = siteMap.get(liveStoreCode);
                if (existing != null) {
                    existing.setSite(firstNonBlank(liveSite, existing.getSite()));
                    existing.setStatusCode(firstNonBlank(statusCode, existing.getStatusCode()));
                }
            }
        }

        if (!StringUtils.hasText(store.getStoreCode())) {
            return new ArrayList<>(siteMap.values());
        }

        ProductProjectSiteContext referenceStore = siteMap.get(store.getStoreCode());
        if (referenceStore == null) {
            siteMap.put(
                    store.getStoreCode(),
                    new ProductProjectSiteContext(
                            store.getStoreCode(),
                            firstNonBlank(store.getSite(), deriveSiteFromStoreCode(store.getStoreCode())),
                            null
                    )
            );
        } else {
            referenceStore.setSite(firstNonBlank(
                    referenceStore.getSite(),
                    store.getSite(),
                    deriveSiteFromStoreCode(store.getStoreCode())
            ));
        }

        return new ArrayList<>(siteMap.values());
    }

    String resolveReferenceSite(List<ProductProjectSiteContext> projectSites, String referenceStoreCode) {
        for (ProductProjectSiteContext projectSite : projectSites) {
            if (projectSite.getStoreCode().equalsIgnoreCase(referenceStoreCode)) {
                return firstNonBlank(projectSite.getSite(), deriveSiteFromStoreCode(referenceStoreCode));
            }
        }
        return deriveSiteFromStoreCode(referenceStoreCode);
    }

    String resolveProjectCode(
            NoonSession session,
            String localProjectCode,
            StoreSyncStoreRecord store,
            List<String> warnings
    ) {
        String cacheKey = buildResolvedProjectCodeCacheKey(store, localProjectCode);
        ResolvedProjectCodeCacheEntry cachedEntry = resolvedProjectCodeCache.get(cacheKey);
        if (cachedEntry != null) {
            if (StringUtils.hasText(cachedEntry.warning()) && !warnings.contains(cachedEntry.warning())) {
                warnings.add(cachedEntry.warning());
            }
            return cachedEntry.resolvedProjectCode();
        }

        ObjectNode emptyBody = objectMapper.createObjectNode();
        JsonNode projectListRoot = safePost(
                session,
                NoonProductGateway.PROJECT_LIST_URL,
                emptyBody,
                false,
                warnings,
                "读取 Noon 项目列表失败"
        );
        JsonNode projectsNode = projectListRoot.path("projects");
        if (!projectsNode.isArray() || projectsNode.size() == 0) {
            return localProjectCode;
        }

        String localDigits = extractDigits(localProjectCode);
        String storeProjectName = normalize(store.getProjectName());

        for (JsonNode projectNode : projectsNode) {
            String candidateCode = text(projectNode, "projectCode");
            if (localProjectCode != null && localProjectCode.equalsIgnoreCase(candidateCode)) {
                return cacheResolvedProjectCode(cacheKey, candidateCode, null);
            }
        }

        if (StringUtils.hasText(localDigits)) {
            for (JsonNode projectNode : projectsNode) {
                String candidateCode = text(projectNode, "projectCode");
                if (extractDigits(candidateCode).equals(localDigits)) {
                    return cacheResolvedProjectCode(cacheKey, candidateCode, null);
                }
            }
        }

        if (StringUtils.hasText(storeProjectName)) {
            for (JsonNode projectNode : projectsNode) {
                String candidateName = normalize(text(projectNode, "projectName"));
                if (storeProjectName.equalsIgnoreCase(candidateName)) {
                    return cacheResolvedProjectCode(cacheKey, text(projectNode, "projectCode"), null);
                }
            }
        }

        String fallbackProjectCode = text(projectsNode.get(0), "projectCode");
        if (StringUtils.hasText(fallbackProjectCode)
                && !fallbackProjectCode.equalsIgnoreCase(localProjectCode)) {
            String warning = "本地店铺 projectCode="
                    + localProjectCode
                    + " 与 Noon 实时 projectCode="
                    + fallbackProjectCode
                    + " 不一致，当前已按 Noon 实时项目码读取。";
            warnings.add(warning);
            return cacheResolvedProjectCode(cacheKey, fallbackProjectCode, warning);
        }

        return cacheResolvedProjectCode(
                cacheKey,
                StringUtils.hasText(fallbackProjectCode) ? fallbackProjectCode : localProjectCode,
                null
        );
    }

    String deriveSiteFromStoreCode(String storeCode) {
        if (!StringUtils.hasText(storeCode) || storeCode.length() < 2) {
            return null;
        }
        String suffix = storeCode.substring(storeCode.length() - 2).toUpperCase();
        if ("SA".equals(suffix) || "AE".equals(suffix)) {
            return suffix;
        }
        return null;
    }

    String describeSite(ProductProjectSiteContext projectSite) {
        String storeCode = firstNonBlank(projectSite.getStoreCode(), "未知站点");
        if (!StringUtils.hasText(projectSite.getSite())) {
            return storeCode;
        }
        return projectSite.getSite() + " / " + storeCode;
    }

    private List<StoreSyncStoreRecord> findRelatedStores(Long ownerUserId, StoreSyncStoreRecord referenceStore) {
        List<StoreSyncStoreRecord> ownerStores = storeSyncMapper.listOwnerStores(ownerUserId);
        String projectKey = projectKey(referenceStore);
        List<StoreSyncStoreRecord> relatedStores = new ArrayList<>();
        for (StoreSyncStoreRecord ownerStore : ownerStores) {
            if (projectKey.equals(projectKey(ownerStore))) {
                relatedStores.add(ownerStore);
            }
        }
        if (relatedStores.isEmpty()) {
            relatedStores.add(referenceStore);
        }
        return relatedStores;
    }

    private String projectKey(StoreSyncStoreRecord store) {
        String projectCode = normalize(store.getProjectCode());
        if (StringUtils.hasText(projectCode)) {
            return "project:" + projectCode.toLowerCase();
        }
        String projectName = normalize(store.getProjectName());
        if (StringUtils.hasText(projectName)) {
            return "project-name:" + projectName.toLowerCase();
        }
        return "store:" + normalize(store.getStoreCode());
    }

    private JsonNode safePost(
            NoonSession session,
            String url,
            JsonNode body,
            boolean withProject,
            List<String> warnings,
            String warningPrefix
    ) {
        try {
            return productNoonAdapter.postJson(session, url, body, withProject);
        } catch (IllegalStateException exception) {
            warnings.add(warningPrefix + "：" + noonFailureMessage(exception));
            return MissingNode.getInstance();
        }
    }

    private String noonFailureMessage(RuntimeException exception) {
        if (productNoonAdapter == null) {
            return shrink(exception.getMessage());
        }
        return shrink(productNoonAdapter.userMessage(exception));
    }

    private String buildResolvedProjectCodeCacheKey(StoreSyncStoreRecord store, String localProjectCode) {
        String storeCode = normalize(store.getStoreCode());
        if (StringUtils.hasText(storeCode)) {
            return "store:" + storeCode.toLowerCase();
        }
        String projectCode = normalize(localProjectCode);
        if (StringUtils.hasText(projectCode)) {
            return "project:" + projectCode.toLowerCase();
        }
        String projectName = normalize(store.getProjectName());
        if (StringUtils.hasText(projectName)) {
            return "project-name:" + projectName.toLowerCase();
        }
        return "store:unknown";
    }

    private String cacheResolvedProjectCode(String cacheKey, String resolvedProjectCode, String warning) {
        if (StringUtils.hasText(cacheKey) && StringUtils.hasText(resolvedProjectCode)) {
            resolvedProjectCodeCache.put(
                    cacheKey,
                    new ResolvedProjectCodeCacheEntry(resolvedProjectCode, warning)
            );
        }
        return resolvedProjectCode;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node != null ? node.path(field) : MissingNode.getInstance();
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isValueNode()) {
            return value.asText();
        }
        return value.toString();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String extractDigits(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("\\D+", "");
    }

    private String shrink(String value) {
        if (!StringUtils.hasText(value)) {
            return "未返回更多错误信息";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > 160 ? normalized.substring(0, 160) + "..." : normalized;
    }

    private long nanosToMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static final class ResolvedProjectCodeCacheEntry {

        private final String resolvedProjectCode;
        private final String warning;

        private ResolvedProjectCodeCacheEntry(String resolvedProjectCode, String warning) {
            this.resolvedProjectCode = resolvedProjectCode;
            this.warning = warning;
        }

        private String resolvedProjectCode() {
            return resolvedProjectCode;
        }

        private String warning() {
            return warning;
        }
    }
}
