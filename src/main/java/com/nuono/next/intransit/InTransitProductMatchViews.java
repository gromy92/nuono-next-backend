package com.nuono.next.intransit;

import java.util.Collections;
import java.util.List;

public final class InTransitProductMatchViews {
    private InTransitProductMatchViews() {
    }

    public static class CandidateListView {
        private List<InTransitProductMatchCandidate> items = Collections.emptyList();

        public List<InTransitProductMatchCandidate> getItems() { return items; }
        public void setItems(List<InTransitProductMatchCandidate> items) {
            this.items = items == null ? Collections.emptyList() : items;
        }
    }

    public static class RematchView {
        private int matchedCount;
        private int pendingCount;
        private List<InTransitProductMatchCandidate> pendingItems = Collections.emptyList();

        public int getMatchedCount() { return matchedCount; }
        public void setMatchedCount(int matchedCount) { this.matchedCount = matchedCount; }
        public int getPendingCount() { return pendingCount; }
        public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }
        public List<InTransitProductMatchCandidate> getPendingItems() { return pendingItems; }
        public void setPendingItems(List<InTransitProductMatchCandidate> pendingItems) {
            this.pendingItems = pendingItems == null ? Collections.emptyList() : pendingItems;
        }
    }

    public static class PreparationView {
        private int batchCount;
        private int matchedCount;
        private int pendingCount;

        public int getBatchCount() { return batchCount; }
        public void setBatchCount(int batchCount) { this.batchCount = batchCount; }
        public int getMatchedCount() { return matchedCount; }
        public void setMatchedCount(int matchedCount) { this.matchedCount = matchedCount; }
        public int getPendingCount() { return pendingCount; }
        public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }
    }
}
