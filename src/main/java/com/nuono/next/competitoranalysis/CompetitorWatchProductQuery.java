package com.nuono.next.competitoranalysis;

import java.util.Locale;
import org.springframework.util.StringUtils;

public class CompetitorWatchProductQuery {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    public static final String SORT_BY_CANDIDATE_COUNT_DESC = "CANDIDATE_COUNT_DESC";
    public static final String SORT_BY_CANDIDATE_COUNT_ASC = "CANDIDATE_COUNT_ASC";
    public static final String SORT_BY_MONITORED_COUNT_DESC = "MONITORED_COUNT_DESC";
    public static final String SORT_BY_MONITORED_COUNT_ASC = "MONITORED_COUNT_ASC";
    public static final String SORT_BY_RECENT_7D_CHANGE_COUNT_DESC = "RECENT_7D_CHANGE_COUNT_DESC";
    public static final String SORT_BY_RECENT_7D_CHANGE_COUNT_ASC = "RECENT_7D_CHANGE_COUNT_ASC";

    private String storeCode;
    private String siteCode;
    private String productSearch;
    private String keywordSearch;
    private String competitorSearch;
    private String status;
    private String sortBy;
    private boolean confirmedCompetitorCountZero;
    private boolean pendingCandidateCountZero;
    private int page;
    private int pageSize;

    public static CompetitorWatchProductQuery fromRequest(
            String storeCode,
            String siteCode,
            String productSearch,
            String keywordSearch,
            String competitorSearch,
            String status,
            Integer page,
            Integer pageSize
    ) {
        return fromRequest(
                storeCode,
                siteCode,
                productSearch,
                keywordSearch,
                competitorSearch,
                status,
                null,
                null,
                null,
                page,
                pageSize
        );
    }

    public static CompetitorWatchProductQuery fromRequest(
            String storeCode,
            String siteCode,
            String productSearch,
            String keywordSearch,
            String competitorSearch,
            String status,
            Boolean confirmedCompetitorCountZero,
            Boolean pendingCandidateCountZero,
            Integer page,
            Integer pageSize
    ) {
        return fromRequest(
                storeCode,
                siteCode,
                productSearch,
                keywordSearch,
                competitorSearch,
                status,
                confirmedCompetitorCountZero,
                pendingCandidateCountZero,
                null,
                page,
                pageSize
        );
    }

    public static CompetitorWatchProductQuery fromRequest(
            String storeCode,
            String siteCode,
            String productSearch,
            String keywordSearch,
            String competitorSearch,
            String status,
            Boolean confirmedCompetitorCountZero,
            Boolean pendingCandidateCountZero,
            String sortBy,
            Integer page,
            Integer pageSize
    ) {
        CompetitorWatchProductQuery query = new CompetitorWatchProductQuery();
        query.setStoreCode(upperBlankToNull(storeCode));
        query.setSiteCode(upperBlankToNull(siteCode));
        query.setProductSearch(blankToNull(productSearch));
        query.setKeywordSearch(blankToNull(keywordSearch));
        query.setCompetitorSearch(blankToNull(competitorSearch));
        query.setStatus(upperBlankToNull(status));
        query.setSortBy(normalizeSortBy(sortBy));
        query.setConfirmedCompetitorCountZero(Boolean.TRUE.equals(confirmedCompetitorCountZero));
        query.setPendingCandidateCountZero(Boolean.TRUE.equals(pendingCandidateCountZero));
        query.setPage(page == null || page < 1 ? DEFAULT_PAGE : page);
        query.setPageSize(normalizePageSize(pageSize));
        return query;
    }

    public int getOffset() {
        return (page - 1) * pageSize;
    }

    private static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private static String upperBlankToNull(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static String normalizeSortBy(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return SORT_BY_CANDIDATE_COUNT_DESC;
        }
        String compact = normalized
                .trim()
                .replace("-", "")
                .replace("_", "")
                .toUpperCase(Locale.ROOT);
        switch (compact) {
            case "CANDIDATECOUNTASC":
            case "PENDINGCANDIDATECOUNTASC":
                return SORT_BY_CANDIDATE_COUNT_ASC;
            case "MONITOREDCOUNT":
            case "MONITOREDCOUNTDESC":
            case "CONFIRMEDCOMPETITORCOUNT":
            case "CONFIRMEDCOMPETITORCOUNTDESC":
                return SORT_BY_MONITORED_COUNT_DESC;
            case "MONITOREDCOUNTASC":
            case "CONFIRMEDCOMPETITORCOUNTASC":
                return SORT_BY_MONITORED_COUNT_ASC;
            case "RECENT7DCHANGECOUNT":
            case "RECENT7DCHANGECOUNTDESC":
            case "RECENT7DCOMPETITORCHANGECOUNT":
            case "RECENT7DCOMPETITORCHANGECOUNTDESC":
                return SORT_BY_RECENT_7D_CHANGE_COUNT_DESC;
            case "RECENT7DCHANGECOUNTASC":
            case "RECENT7DCOMPETITORCHANGECOUNTASC":
                return SORT_BY_RECENT_7D_CHANGE_COUNT_ASC;
            case "CANDIDATECOUNT":
            case "CANDIDATECOUNTDESC":
            case "PENDINGCANDIDATECOUNT":
            case "PENDINGCANDIDATECOUNTDESC":
            default:
                return SORT_BY_CANDIDATE_COUNT_DESC;
        }
    }

    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getProductSearch() { return productSearch; }
    public void setProductSearch(String productSearch) { this.productSearch = productSearch; }
    public String getKeywordSearch() { return keywordSearch; }
    public void setKeywordSearch(String keywordSearch) { this.keywordSearch = keywordSearch; }
    public String getCompetitorSearch() { return competitorSearch; }
    public void setCompetitorSearch(String competitorSearch) { this.competitorSearch = competitorSearch; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }
    public boolean isConfirmedCompetitorCountZero() { return confirmedCompetitorCountZero; }
    public void setConfirmedCompetitorCountZero(boolean confirmedCompetitorCountZero) {
        this.confirmedCompetitorCountZero = confirmedCompetitorCountZero;
    }
    public boolean isPendingCandidateCountZero() { return pendingCandidateCountZero; }
    public void setPendingCandidateCountZero(boolean pendingCandidateCountZero) {
        this.pendingCandidateCountZero = pendingCandidateCountZero;
    }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
}
