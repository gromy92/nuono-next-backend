package com.nuono.next.competitoranalysis;

import java.util.Locale;
import org.springframework.util.StringUtils;

public class CompetitorWatchProductQuery {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private String storeCode;
    private String siteCode;
    private String productSearch;
    private String keywordSearch;
    private String competitorSearch;
    private String status;
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
        CompetitorWatchProductQuery query = new CompetitorWatchProductQuery();
        query.setStoreCode(upperBlankToNull(storeCode));
        query.setSiteCode(upperBlankToNull(siteCode));
        query.setProductSearch(blankToNull(productSearch));
        query.setKeywordSearch(blankToNull(keywordSearch));
        query.setCompetitorSearch(blankToNull(competitorSearch));
        query.setStatus(upperBlankToNull(status));
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
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
}
