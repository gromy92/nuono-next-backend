package com.nuono.next.datapull.snapshot;

import com.nuono.next.infrastructure.mapper.CompleteSnapshotStageMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Transaction-free fake that preserves the mapper's row semantics across Adapter reconstruction. */
final class FakeCompleteSnapshotStageMapper implements CompleteSnapshotStageMapper {
    private SnapshotStageTaskRow task;
    private SnapshotStageAggregateRow aggregate;
    private final Map<Integer, SnapshotStagePageRow> pages = new TreeMap<>();
    private final List<SnapshotStageItemRow> items = new ArrayList<>();

    void task(long taskId, long fenceEpoch, String state) {
        task(taskId, fenceEpoch, state, true);
    }

    void task(long taskId, long fenceEpoch, String state, boolean leaseValid) {
        SnapshotStageTaskRow row = new SnapshotStageTaskRow();
        row.setTaskId(taskId);
        row.setFenceEpoch(fenceEpoch);
        row.setState(state);
        row.setLeaseValid(leaseValid);
        task = row;
    }

    SnapshotStageAggregateRow aggregate() {
        return aggregate;
    }

    void replacePayload(int pageNo, int ordinal, String payload) {
        items.stream()
                .filter(row -> row.getPageNo() == pageNo && row.getItemOrdinal() == ordinal)
                .findFirst()
                .orElseThrow()
                .setPayload(payload);
    }

    @Override
    public SnapshotStageTaskRow selectTaskForUpdate(long taskId) {
        return task != null && task.getTaskId() == taskId ? task : null;
    }

    @Override
    public int insertAggregateIfAbsent(long taskId, long fenceEpoch) {
        if (aggregate != null) {
            return 0;
        }
        aggregate = new SnapshotStageAggregateRow();
        aggregate.setTaskId(taskId);
        aggregate.setActiveFenceEpoch(fenceEpoch);
        return 1;
    }

    @Override
    public SnapshotStageAggregateRow selectAggregateForUpdate(long taskId) {
        return aggregate != null && aggregate.getTaskId() == taskId ? aggregate : null;
    }

    @Override
    public int adoptFence(long taskId, long fenceEpoch) {
        if (aggregate == null || aggregate.getTaskId() != taskId
                || aggregate.getActiveFenceEpoch() >= fenceEpoch) {
            return 0;
        }
        aggregate.setActiveFenceEpoch(fenceEpoch);
        return 1;
    }

    @Override
    public int poison(long taskId, long fenceEpoch, String poisonCode) {
        if (aggregate == null || aggregate.getTaskId() != taskId
                || aggregate.getActiveFenceEpoch() != fenceEpoch
                || aggregate.getPoisonCode() != null) {
            return 0;
        }
        aggregate.setPoisonCode(poisonCode);
        return 1;
    }

    @Override
    public SnapshotStagePageRow selectPage(long taskId, int pageNo) {
        SnapshotStagePageRow row = pages.get(pageNo);
        return row != null && row.getTaskId() == taskId ? row : null;
    }

    @Override
    public Integer selectMaxPageNo(long taskId) {
        return pages.isEmpty() ? null : pages.keySet().stream().max(Integer::compareTo).orElse(null);
    }

    @Override
    public int updateMetadata(
            long taskId,
            long fenceEpoch,
            Integer declaredTotalPages,
            Integer knownLastPage,
            String authorityKind,
            String authorityTokenSha256,
            LocalDateTime snapshotAsOfUtc,
            Long declaredCollectionCount
    ) {
        if (aggregate == null || aggregate.getTaskId() != taskId
                || aggregate.getActiveFenceEpoch() != fenceEpoch
                || aggregate.getPoisonCode() != null) {
            return 0;
        }
        aggregate.setDeclaredTotalPages(declaredTotalPages);
        aggregate.setKnownLastPage(knownLastPage);
        aggregate.setAuthorityKind(authorityKind);
        aggregate.setAuthorityTokenSha256(authorityTokenSha256);
        aggregate.setSnapshotAsOfUtc(snapshotAsOfUtc);
        aggregate.setDeclaredCollectionCount(declaredCollectionCount);
        return 1;
    }

    @Override
    public int insertPage(SnapshotStagePageRow row) {
        return pages.putIfAbsent(row.getPageNo(), row) == null ? 1 : 0;
    }

    @Override
    public int insertItems(List<SnapshotStageItemRow> rows) {
        int changed = 0;
        for (SnapshotStageItemRow row : rows) {
            boolean duplicate = items.stream().anyMatch(existing ->
                    existing.getTaskId().equals(row.getTaskId())
                            && existing.getPageNo().equals(row.getPageNo())
                            && existing.getItemOrdinal().equals(row.getItemOrdinal())
            );
            if (!duplicate) {
                items.add(row);
                changed += 1;
            }
        }
        return changed;
    }

    @Override
    public int extendVerifiedSourcePages(
            long taskId,
            int sourcePageCount,
            int totalPageCount
    ) {
        int changed = 0;
        for (SnapshotStagePageRow row : pages.values()) {
            if (row.getTaskId() == taskId
                    && row.getPageNo() <= sourcePageCount
                    && row.getTotalPages() == sourcePageCount) {
                row.setTotalPages(totalPageCount);
                if (row.getPageNo() == sourcePageCount) {
                    row.setNextPage(sourcePageCount + 1);
                    row.setLastPage(false);
                }
                changed++;
            }
        }
        return changed;
    }

    @Override
    public int promoteVerifiedTwoPass(
            long taskId,
            long fenceEpoch,
            int sourcePageCount,
            int totalPageCount
    ) {
        if (aggregate == null || aggregate.getTaskId() != taskId
                || aggregate.getActiveFenceEpoch() != fenceEpoch
                || !Integer.valueOf(sourcePageCount).equals(aggregate.getDeclaredTotalPages())
                || !Integer.valueOf(sourcePageCount).equals(aggregate.getKnownLastPage())
                || !"TWO_PASS_REQUIRED".equals(aggregate.getCollectionMode())
                || !"VERIFIED".equals(aggregate.getVerificationState())
                || !"TWO_PASS_OBSERVATION".equals(aggregate.getAuthorityKind())) {
            return 0;
        }
        aggregate.setDeclaredTotalPages(totalPageCount);
        aggregate.setKnownLastPage(totalPageCount);
        return 1;
    }

    @Override
    public List<SnapshotStageItemRow> selectPageItems(long taskId, int pageNo) {
        return items.stream()
                .filter(row -> row.getTaskId() == taskId && row.getPageNo() == pageNo)
                .sorted(Comparator.comparing(SnapshotStageItemRow::getItemOrdinal))
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List<SnapshotStagePageRow> selectPages(long taskId) {
        return pages.values().stream()
                .filter(row -> row.getTaskId() == taskId)
                .sorted(Comparator.comparing(SnapshotStagePageRow::getPageNo))
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List<SnapshotStageItemRow> selectItems(long taskId) {
        return items.stream()
                .filter(row -> row.getTaskId() == taskId)
                .sorted(Comparator.comparing(SnapshotStageItemRow::getPageNo)
                        .thenComparing(SnapshotStageItemRow::getItemOrdinal))
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public int deleteStageItemsBounded(long taskId, int batchSize) {
        List<SnapshotStageItemRow> selected = selectItems(taskId).stream()
                .limit(batchSize)
                .collect(java.util.stream.Collectors.toList());
        items.removeAll(selected);
        return selected.size();
    }

    @Override
    public int deleteEmptyStagePagesBounded(long taskId, int batchSize) {
        List<Integer> selected = pages.entrySet().stream()
                .filter(entry -> entry.getValue().getTaskId() == taskId)
                .filter(entry -> items.stream().noneMatch(item ->
                        item.getTaskId() == taskId
                                && item.getPageNo().equals(entry.getKey())))
                .map(Map.Entry::getKey)
                .limit(batchSize)
                .collect(java.util.stream.Collectors.toList());
        selected.forEach(pages::remove);
        return selected.size();
    }

    @Override
    public SnapshotStageManifestRow selectManifest(long taskId) {
        if (aggregate == null || aggregate.getTaskId() != taskId) {
            return null;
        }
        List<SnapshotStagePageRow> taskPages = selectPages(taskId);
        List<SnapshotStageItemRow> taskItems = selectItems(taskId);
        SnapshotStageManifestRow row = new SnapshotStageManifestRow();
        row.setTaskId(taskId);
        row.setActiveFenceEpoch(aggregate.getActiveFenceEpoch());
        row.setDeclaredTotalPages(aggregate.getDeclaredTotalPages());
        row.setKnownLastPage(aggregate.getKnownLastPage());
        row.setPoisonCode(aggregate.getPoisonCode());
        row.setAuthorityKind(aggregate.getAuthorityKind());
        row.setAuthorityTokenSha256(aggregate.getAuthorityTokenSha256());
        row.setSnapshotAsOfUtc(aggregate.getSnapshotAsOfUtc());
        row.setDeclaredCollectionCount(aggregate.getDeclaredCollectionCount());
        row.setPageCount((long) taskPages.size());
        row.setFirstPage(taskPages.isEmpty() ? null : taskPages.get(0).getPageNo());
        row.setLastPage(taskPages.isEmpty() ? null : taskPages.get(taskPages.size() - 1).getPageNo());
        row.setStagedItemCount((long) taskItems.size());
        row.setCanonicalItemCount(taskItems.stream()
                .map(SnapshotStageItemRow::getStableIdentity).distinct().count());
        row.setSourceItemCount(taskPages.stream()
                .mapToLong(SnapshotStagePageRow::getSourceItemCount).sum());
        row.setBusinessSkippedItemCount(taskPages.stream()
                .mapToLong(SnapshotStagePageRow::getBusinessSkippedItemCount).sum());
        return row;
    }

    @Override
    public long countInvalidPageShapes(long taskId) {
        return 0L;
    }

    @Override
    public long countInvalidItems(long taskId) {
        return selectItems(taskId).stream().filter(row ->
                row.getValidatedIdentityCandidate() == null
                        || row.getAbsenceReconciliationSafe() == null
        ).count();
    }

    @Override
    public int deleteAggregate(long taskId, long fenceEpoch) {
        if (aggregate == null || aggregate.getTaskId() != taskId
                || aggregate.getActiveFenceEpoch() > fenceEpoch
                || !pages.isEmpty() || !items.isEmpty()) {
            return 0;
        }
        aggregate = null;
        return 1;
    }
}
