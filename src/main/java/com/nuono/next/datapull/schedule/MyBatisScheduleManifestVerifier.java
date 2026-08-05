package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.DataPullScheduleAnchorMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleScanMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Resumably verifies the official cutover manifest in pages of at most 64 anchors. */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public final class MyBatisScheduleManifestVerifier {
    private final DataPullScheduleScanMapper mapper;
    private final DataPullScheduleAnchorMapper anchors;

    public MyBatisScheduleManifestVerifier(
            DataPullScheduleScanMapper mapper,
            DataPullScheduleAnchorMapper anchors
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.anchors = Objects.requireNonNull(anchors, "anchors");
    }

    public Advance advance(OperationCode operation) {
        DataPullScheduleCutover cutover = anchors.selectActiveCutover(operation);
        if (cutover == null) return Advance.rejected();
        cutover.validateActive();
        ScheduleManifestSealRow seal = mapper.lockManifestSeal(
                operation, cutover.getCutoverKey()
        );
        if (seal == null) seal = initialize(cutover);
        requireSealIdentity(cutover, seal);
        if ("SEALED".equals(seal.getSealState())) {
            return cutover.getAnchorManifestSha256().equals(seal.getVerifiedManifestSha256())
                    ? Advance.sealed(cutover.getCutoverKey(), false) : Advance.rejected();
        }
        if (!"VERIFYING".equals(seal.getSealState())) return Advance.rejected();
        return verifyPage(cutover, seal);
    }

    private ScheduleManifestSealRow initialize(DataPullScheduleCutover cutover) {
        DataPullScheduleAnchorManifestAccumulator initial =
                DataPullScheduleAnchorManifestAccumulator.initial(
                        cutover.getOperationCode(), cutover.getCutoverKey(),
                        cutover.getExpectedScopeCount()
                );
        mapper.insertManifestSeal(
                cutover.getOperationCode(), cutover.getCutoverKey(),
                cutover.getExpectedScopeCount(), cutover.getAnchorManifestSha256(),
                initial.snapshot()
        );
        return mapper.lockManifestSeal(cutover.getOperationCode(), cutover.getCutoverKey());
    }

    private Advance verifyPage(
            DataPullScheduleCutover cutover,
            ScheduleManifestSealRow seal
    ) {
        DataPullScheduleAnchorManifestAccumulator digest =
                DataPullScheduleAnchorManifestAccumulator.resume(
                        cutover.getOperationCode(), cutover.getCutoverKey(),
                        cutover.getExpectedScopeCount(), seal.getScannedScopeCount(),
                        seal.getNextScopeKey(), seal.getResumableSha256State()
                );
        List<DataPullScheduleAnchor> page = List.copyOf(Objects.requireNonNull(
                anchors.listCutoverAnchorsAfter(
                        cutover.getOperationCode(), cutover.getCutoverKey(),
                        seal.getNextScopeKey(), MyBatisScheduleReconciliationStore.SCOPES_PER_STEP + 1
                ), "bounded cutover anchors"
        ));
        boolean hasMore = page.size() > MyBatisScheduleReconciliationStore.SCOPES_PER_STEP;
        boolean overflow = appendPage(cutover, digest, page);
        boolean exactCount = digest.getScannedCount() == cutover.getExpectedScopeCount();
        boolean rejected = overflow || (hasMore && exactCount) || (!hasMore && !exactCount);
        String actual = !rejected && !hasMore ? digest.finishHex() : null;
        if (actual != null && !actual.equals(cutover.getAnchorManifestSha256())) rejected = true;
        String nextState = rejected ? "REJECTED" : (!hasMore ? "SEALED" : "VERIFYING");
        requireOne(mapper.advanceManifestSeal(
                cutover.getOperationCode(), cutover.getCutoverKey(), seal.getVersion(),
                seal.getScannedScopeCount(), seal.getNextScopeKey(), digest.getPreviousScope(),
                digest.getScannedCount(), digest.snapshot(), nextState,
                "SEALED".equals(nextState) ? actual : null
        ));
        if (rejected) return Advance.rejected();
        return "SEALED".equals(nextState)
                ? Advance.sealed(cutover.getCutoverKey(), true)
                : Advance.verifying(cutover.getCutoverKey());
    }

    private static boolean appendPage(
            DataPullScheduleCutover cutover,
            DataPullScheduleAnchorManifestAccumulator digest,
            List<DataPullScheduleAnchor> page
    ) {
        for (int index = 0;
                index < page.size() && index < MyBatisScheduleReconciliationStore.SCOPES_PER_STEP;
                index++) {
            if (digest.getScannedCount() == cutover.getExpectedScopeCount()) return true;
            digest.append(Objects.requireNonNull(page.get(index), "cutover anchor"));
        }
        return digest.getScannedCount() > cutover.getExpectedScopeCount();
    }

    private static void requireSealIdentity(
            DataPullScheduleCutover cutover,
            ScheduleManifestSealRow seal
    ) {
        if (seal == null || seal.getOperationCode() != cutover.getOperationCode()
                || !cutover.getCutoverKey().equals(seal.getCutoverKey())
                || !Objects.equals(cutover.getExpectedScopeCount(), seal.getExpectedScopeCount())
                || !cutover.getAnchorManifestSha256().equals(seal.getExpectedManifestSha256())) {
            throw new IllegalStateException("DP_SCHEDULE_MANIFEST_SEAL_CUTOVER_DRIFT");
        }
    }

    private static void requireOne(int changed) {
        if (changed != 1) throw new IllegalStateException("manifest seal CAS must affect one row");
    }

    public enum Progress { VERIFYING, SEALED, SEALED_NOW, REJECTED }

    public static final class Advance {
        private final Progress progress;
        private final String cutoverKey;

        private Advance(Progress progress, String cutoverKey) {
            this.progress = Objects.requireNonNull(progress, "progress");
            this.cutoverKey = cutoverKey;
        }

        private static Advance rejected() { return new Advance(Progress.REJECTED, null); }
        private static Advance verifying(String key) { return new Advance(Progress.VERIFYING, key); }
        private static Advance sealed(String key, boolean now) {
            return new Advance(
                    now ? Progress.SEALED_NOW : Progress.SEALED,
                    DataPullScheduleAnchor.requireIdentity(key, "cutoverKey", 96)
            );
        }

        public Progress getProgress() { return progress; }
        public String requireSealedCutoverKey() {
            if (progress != Progress.SEALED && progress != Progress.SEALED_NOW) {
                throw new IllegalStateException("schedule manifest is not sealed");
            }
            return cutoverKey;
        }
    }
}
