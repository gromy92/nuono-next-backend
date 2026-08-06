package com.nuono.next.noonpull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NoonInterfacePullPage {
    private List<Map<String, Object>> items = new ArrayList<>();
    private int pageNumber;
    private int pageSize;
    private int totalItems;
    private boolean hasNextPage;
    private int requestCount;
    private String providerGenerationToken;

    public static Builder builder() {
        return new Builder();
    }

    public List<Map<String, Object>> getItems() {
        return items;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public int getPageSize() {
        return pageSize;
    }

    public boolean isHasNextPage() {
        return hasNextPage;
    }

    public int getRequestCount() {
        return requestCount;
    }

    /** Opaque collection-generation token supplied by Noon, never synthesized locally. */
    public String getProviderGenerationToken() {
        return providerGenerationToken;
    }

    public static class Builder {
        private final NoonInterfacePullPage page = new NoonInterfacePullPage();

        public Builder items(List<? extends Map<String, ?>> items) {
            page.items = copyItems(items);
            return this;
        }

        public Builder pageNumber(int pageNumber) {
            page.pageNumber = pageNumber;
            return this;
        }

        public Builder totalItems(int totalItems) {
            page.totalItems = totalItems;
            return this;
        }

        public Builder pageSize(int pageSize) {
            page.pageSize = pageSize;
            return this;
        }

        public Builder hasNextPage(boolean hasNextPage) {
            page.hasNextPage = hasNextPage;
            return this;
        }

        public Builder requestCount(int requestCount) {
            page.requestCount = requestCount;
            return this;
        }

        public Builder providerGenerationToken(String providerGenerationToken) {
            page.providerGenerationToken = providerGenerationToken;
            return this;
        }

        public NoonInterfacePullPage build() {
            return page;
        }
    }

    static List<Map<String, Object>> copyItems(List<? extends Map<String, ?>> items) {
        List<Map<String, Object>> copied = new ArrayList<>();
        if (items == null) {
            return copied;
        }
        for (Map<String, ?> item : items) {
            copied.add(new LinkedHashMap<>(item));
        }
        return copied;
    }
}
