package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Restart-safe generation/pass fake used through the production staging Interface. */
final class Ali1688Dp10InMemoryStageStore
        implements Ali1688Dp10PageStageStore, Ali1688Dp10StageCleanup {
    private final Map<String, Ali1688Dp10StagedPage> pages = new HashMap<>();
    private final Map<String, String> pageFingerprints = new HashMap<>();
    private final Map<String, long[]> fingerprintCounts = new HashMap<>();
    int olderCleanupBatches;
    int currentCleanupBatches;
    int olderCleanupCalls;
    int currentCleanupCalls;
    int lastSealCountRowsRead;

    int stagedPageCountForTest() { return pages.size(); }

    @Override
    public Ali1688Dp10StageCleanupAdvance cleanupOlderGenerations(
            DataPullTask task,
            long currentGenerationNo,
            LocalDateTime nowUtc
    ) {
        olderCleanupCalls++;
        if (olderCleanupBatches <= 0) return Ali1688Dp10StageCleanupAdvance.COMPLETE;
        olderCleanupBatches--;
        return Ali1688Dp10StageCleanupAdvance.PROGRESSED;
    }

    @Override
    public Ali1688Dp10StageCleanupAdvance cleanupCurrentGeneration(
            DataPullTask task,
            long currentGenerationNo,
            LocalDateTime nowUtc
    ) {
        currentCleanupCalls++;
        if (currentCleanupBatches <= 0) return Ali1688Dp10StageCleanupAdvance.COMPLETE;
        currentCleanupBatches--;
        return Ali1688Dp10StageCleanupAdvance.PROGRESSED;
    }

    List<Ali1688HistoricalOrderProvider.OrderSnapshot> acceptedForTest(long generationNo) {
        Map<String, Boolean> identities = new HashMap<>();
        List<Ali1688HistoricalOrderProvider.OrderSnapshot> accepted = new ArrayList<>();
        for (Ali1688Dp10StagedPage page : orderedPages(generationNo, 2)) {
            for (Ali1688Dp10StagedOrder item : page.getOrders()) {
                if (item.getState() == Ali1688Dp10ItemState.COMPLETE
                        && identities.putIfAbsent(item.getProviderOrderNo(), Boolean.TRUE) == null) {
                    accepted.add(item.getOrder());
                }
            }
        }
        return List.copyOf(accepted);
    }

    @Override
    public Optional<Ali1688Dp10StagedPage> load(
            DataPullTask task,
            long generationNo,
            int scanPass,
            Ali1688HistoricalOrderProvider.Partition partition,
            int pageNo,
            LocalDateTime nowUtc
    ) {
        return Optional.ofNullable(pages.get(key(generationNo, scanPass, partition, pageNo)));
    }

    @Override
    public Ali1688Dp10StagedPage stageList(
            DataPullTask task,
            long generationNo,
            int scanPass,
            Ali1688Dp10ValidatedPage page,
            LocalDateTime nowUtc
    ) {
        String pageKey = key(generationNo, scanPass, page.getPartition(), page.getPageNo());
        String pageFingerprint = pageFingerprint(page);
        Ali1688Dp10StagedPage existing = pages.get(pageKey);
        if (existing != null) {
            if (!pageFingerprint.equals(pageFingerprints.get(pageKey))) {
                throw new Ali1688Dp10PageContractException("DP10_STAGED_PAGE_DRIFT");
            }
            return existing;
        }
        List<Ali1688Dp10StagedOrder> orders = new ArrayList<>();
        for (Ali1688Dp10ListEntry entry : page.getEntries()) orders.add(stage(entry));
        Ali1688Dp10StagedPage staged = new Ali1688Dp10StagedPage(
                generationNo, scanPass, page.getPartition(), page.getPageNo(), page.getPageSize(),
                page.getTotalRecord(), page.getExpectedPages(),
                scanPass == 2 && ready(orders)
                        ? Ali1688Dp10StagedPage.State.READY
                        : Ali1688Dp10StagedPage.State.LISTED,
                orders
        );
        pages.put(pageKey, staged);
        pageFingerprints.put(pageKey, pageFingerprint);
        countPage(generationNo, scanPass, page);
        return staged;
    }

    @Override
    public Ali1688Dp10SealBatch readSealBatch(
            DataPullTask task,
            long generationNo,
            Ali1688HistoricalOrderProvider.Partition partition,
            String afterFingerprint,
            LocalDateTime nowUtc
    ) {
        String prefix = countPrefix(generationNo, partition);
        List<Ali1688Dp10FingerprintCountRow> rows = fingerprintCounts.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .filter(entry -> afterFingerprint == null
                        || entry.getKey().substring(prefix.length()).compareTo(afterFingerprint) > 0)
                .sorted(Map.Entry.comparingByKey())
                .limit(Ali1688Dp10FingerprintStage.SEAL_FETCH_SIZE)
                .map(entry -> countRow(entry.getKey().substring(prefix.length()), entry.getValue()))
                .collect(Collectors.toList());
        lastSealCountRowsRead = rows.size();
        return Ali1688Dp10FingerprintStage.compareRows(rows, afterFingerprint);
    }

    @Override
    public Optional<Ali1688Dp10PendingItem> nextPendingDetail(
            DataPullTask task,
            long generationNo,
            LocalDateTime nowUtc
    ) {
        return orderedPages(generationNo, 2).stream()
                .flatMap(page -> page.getOrders().stream()
                        .filter(order -> order.getState() == Ali1688Dp10ItemState.PENDING_DETAIL)
                        .map(order -> new Ali1688Dp10PendingItem(
                                generationNo, 2, page.getPartition(),
                                page.getPageNo(), order.getOrdinal())))
                .findFirst();
    }

    @Override
    public Ali1688Dp10StagedPage recordDetail(
            DataPullTask task,
            Ali1688Dp10PendingItem locator,
            Ali1688Dp10DetailDecision decision,
            LocalDateTime nowUtc
    ) {
        String key = key(locator.getGenerationNo(), locator.getScanPass(),
                locator.getPartition(), locator.getPageNo());
        Ali1688Dp10StagedPage current = pages.get(key);
        List<Ali1688Dp10StagedOrder> orders = new ArrayList<>(current.getOrders());
        Ali1688Dp10StagedOrder old = current.orderAt(locator.getItemOrdinal());
        orders.set(locator.getItemOrdinal(), new Ali1688Dp10StagedOrder(
                locator.getItemOrdinal(), old.getProviderOrderNo(), decision.getState(),
                decision.getSanitizedCode(), decision.getOrder()));
        Ali1688Dp10StagedPage updated = copy(
                current,
                ready(orders) ? Ali1688Dp10StagedPage.State.READY
                        : Ali1688Dp10StagedPage.State.LISTED,
                orders);
        pages.put(key, updated);
        return updated;
    }

    private Ali1688Dp10StagedOrder stage(Ali1688Dp10ListEntry entry) {
        String identity = entry.getOrder() == null ? null : entry.getOrder().getProviderOrderNo();
        return new Ali1688Dp10StagedOrder(
                entry.getOrdinal(), identity, entry.getState(),
                entry.getSanitizedCode(), entry.getOrder());
    }

    private void countPage(long generationNo, int scanPass, Ali1688Dp10ValidatedPage page) {
        int countIndex = scanPass - 1;
        if (countIndex < 0 || countIndex > 1) {
            throw new Ali1688Dp10PageContractException("DP10_STAGE_SCAN_PASS_INVALID");
        }
        for (Ali1688Dp10ListEntry entry : page.getEntries()) {
            String fingerprint = entry.getRawFingerprint();
            long[] counts = fingerprintCounts.computeIfAbsent(
                    countPrefix(generationNo, page.getPartition()) + fingerprint,
                    ignored -> new long[2]);
            try {
                counts[countIndex] = Math.addExact(counts[countIndex], 1L);
            } catch (ArithmeticException overflow) {
                throw new Ali1688Dp10PageContractException(
                        "DP10_STAGE_FINGERPRINT_COUNT_OVERFLOW");
            }
        }
    }

    private String pageFingerprint(Ali1688Dp10ValidatedPage page) {
        StringBuilder proof = new StringBuilder()
                .append(page.getPartition()).append(':').append(page.getPageNo()).append(':')
                .append(page.getPageSize()).append(':').append(page.getTotalRecord()).append(':')
                .append(page.getExpectedPages());
        for (Ali1688Dp10ListEntry entry : page.getEntries()) {
            proof.append(':').append(entry.getOrdinal()).append(':').append(
                    entry.getRawFingerprint());
        }
        return Ali1688Dp10Digest.sha256(proof.toString());
    }

    private Ali1688Dp10FingerprintCountRow countRow(String fingerprint, long[] counts) {
        Ali1688Dp10FingerprintCountRow row = new Ali1688Dp10FingerprintCountRow();
        row.setFingerprint(fingerprint);
        row.setPassOneCount(counts[0]);
        row.setPassTwoCount(counts[1]);
        return row;
    }

    private String countPrefix(
            long generationNo,
            Ali1688HistoricalOrderProvider.Partition partition
    ) {
        return generationNo + ":" + partition.name() + ":";
    }

    private List<Ali1688Dp10StagedPage> orderedPages(long generationNo, int scanPass) {
        List<Ali1688Dp10StagedPage> values = new ArrayList<>();
        for (Ali1688Dp10StagedPage page : pages.values()) {
            if (page.getGenerationNo() == generationNo && page.getScanPass() == scanPass) {
                values.add(page);
            }
        }
        values.sort(Comparator
                .comparing((Ali1688Dp10StagedPage page) ->
                        page.getPartition() == Ali1688HistoricalOrderProvider.Partition.CURRENT ? 0 : 1)
                .thenComparingInt(Ali1688Dp10StagedPage::getPageNo));
        return values;
    }

    private boolean ready(List<Ali1688Dp10StagedOrder> orders) {
        return orders.stream().noneMatch(
                item -> item.getState() == Ali1688Dp10ItemState.PENDING_DETAIL);
    }

    private Ali1688Dp10StagedPage copy(
            Ali1688Dp10StagedPage page,
            Ali1688Dp10StagedPage.State state,
            List<Ali1688Dp10StagedOrder> orders
    ) {
        return new Ali1688Dp10StagedPage(
                page.getGenerationNo(), page.getScanPass(), page.getPartition(), page.getPageNo(),
                page.getPageSize(), page.getTotalRecord(), page.getExpectedPages(), state, orders);
    }

    private String key(
            long generationNo,
            int scanPass,
            Ali1688HistoricalOrderProvider.Partition partition,
            int pageNo
    ) {
        return generationNo + ":" + scanPass + ":" + partition.name() + ":" + pageNo;
    }
}
