package com.nuono.next.noonpull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NoonInterfacePullPage {
    private List<Map<String, Object>> items = new ArrayList<>();
    private int pageNumber;
    private int totalItems;
    private boolean hasNextPage;
    private int requestCount;

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

    public boolean isHasNextPage() {
        return hasNextPage;
    }

    public int getRequestCount() {
        return requestCount;
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

        public Builder hasNextPage(boolean hasNextPage) {
            page.hasNextPage = hasNextPage;
            return this;
        }

        public Builder requestCount(int requestCount) {
            page.requestCount = requestCount;
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
