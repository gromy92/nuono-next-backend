package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.DataPullScheduleScanMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Persistent Adapter for fair rotation, resumable manifest proof and two-pass source scans. */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public class MyBatisScheduleReconciliationStore {
    public static final int SCOPES_PER_STEP = 64;
    private static final Duration COMPLETED_EPOCH_RESCAN_DELAY = Duration.ofMinutes(5);

    private final DataPullScheduleScanMapper mapper;
    private final ScheduleScopeSourceRegistry sources;
    private final ScheduleEpochRetention retention;

    public MyBatisScheduleReconciliationStore(
            DataPullScheduleScanMapper mapper,
            ScheduleScopeSourceRegistry sources,
            ScheduleEpochRetention retention
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.sources = Objects.requireNonNull(sources, "sources");
        this.retention = Objects.requireNonNull(retention, "retention");
        sources.requireComplete();
    }

    public SourceProgress advanceSource(
            OperationCode operation,
            String verifiedCutoverKey,
            Instant reconcileUntil
    ) {
        OperationCode code = Objects.requireNonNull(operation, "operation");
        String cutoverKey = DataPullScheduleAnchor.requireIdentity(
                verifiedCutoverKey, "verifiedCutoverKey", 96
        );
        Instant upperBound = Objects.requireNonNull(reconcileUntil, "reconcileUntil");
        ScheduleSourceEpochRow epoch = mapper.lockActiveEpoch(code);
        if (epoch != null && !cutoverKey.equals(epoch.getCutoverKey())) {
            if ("PASS_ONE".equals(epoch.getEpochState())
                    || "PASS_TWO".equals(epoch.getEpochState())) {
                return abort(epoch);
            }
            throw new IllegalStateException("DP_SCHEDULE_APPLY_CUTOVER_DRIFT");
        }
        if (epoch == null) {
            ScheduleSourceEpochRow latest = mapper.lockLatestEpoch(code);
            if (latest != null && "ABORTED".equals(latest.getEpochState())) {
                if (retention.drainAbortedBeforeRetry(latest)) {
                    return SourceProgress.CLEANING;
                }
            }
            if (latest != null && "COMPLETE".equals(latest.getEpochState())
                    && cutoverKey.equals(latest.getCutoverKey())) {
                LocalDateTime terminalAt = Objects.requireNonNull(
                        latest.getTerminalAtUtc(), "completed epoch timestamp"
                );
                if (upperBound.isBefore(terminalAt.toInstant(ZoneOffset.UTC)
                        .plus(COMPLETED_EPOCH_RESCAN_DELAY))) {
                    return SourceProgress.IDLE;
                }
            }
            long nextEpoch = allocateEpochNo(code);
            String initial = ScheduleSourceOrderedDigest.initial().snapshot();
            requireOne(mapper.insertEpoch(
                    code, nextEpoch, cutoverKey, utcMillis(upperBound), initial,
                    code == OperationCode.DP08A || code == OperationCode.DP08B
                            ? "PENDING" : "NOT_REQUIRED"
            ), "source epoch insert");
            epoch = mapper.lockActiveEpoch(code);
        }
        if (epoch == null) throw new IllegalStateException("source epoch disappeared");
        if ("PASS_ONE".equals(epoch.getEpochState())) return passOne(epoch);
        if ("PASS_TWO".equals(epoch.getEpochState())) return passTwo(epoch);
        if ("SEALED".equals(epoch.getEpochState())) return SourceProgress.SEALED;
        if ("ABORTED".equals(epoch.getEpochState())) return SourceProgress.DRIFTED;
        return SourceProgress.APPLYING;
    }

    private SourceProgress passOne(ScheduleSourceEpochRow epoch) {
        ScheduleSourcePage page = sourcePage(
                epoch, epoch.getPassOneCursor(), ScheduleSourceReadContext.Pass.ONE
        );
        ScheduleSourceOrderedDigest digest = append(
                epoch.getPassOneOrderedSha256(), page
        );
        long count = Math.addExact(epoch.getPassOneScopeCount(), page.getItems().size());
        String cursor = page.nextCursor();
        if (cursor != null && cursor.equals(epoch.getPassOneCursor())) return abort(epoch);
        List<ScheduleSourceStageRow> rows = stageRows(epoch, page);
        if (!rows.isEmpty()) {
            if (mapper.countStageConflicts(epoch.getOperationCode(), epoch.getEpochNo(), rows) > 0) {
                return abort(epoch);
            }
            if (mapper.insertStageRows(rows) != rows.size()) return abort(epoch);
        }
        requireOne(mapper.advancePassOne(
                epoch.getOperationCode(), epoch.getEpochNo(), epoch.getVersion(),
                epoch.getPassOneCursor(), epoch.getPassOneScopeCount(),
                epoch.getPassOneOrderedSha256(), cursor, count, digest.snapshot(),
                page.hasMore() ? "PASS_ONE" : "PASS_TWO"
        ), "source pass-one CAS");
        return SourceProgress.SCANNING;
    }

    private SourceProgress passTwo(ScheduleSourceEpochRow epoch) {
        ScheduleSourcePage page = sourcePage(
                epoch, epoch.getPassTwoCursor(), ScheduleSourceReadContext.Pass.TWO
        );
        ScheduleSourceOrderedDigest digest = append(
                epoch.getPassTwoOrderedSha256(), page
        );
        long count = Math.addExact(epoch.getPassTwoScopeCount(), page.getItems().size());
        String cursor = page.nextCursor();
        if (cursor != null && cursor.equals(epoch.getPassTwoCursor())) return abort(epoch);
        boolean finalPage = !page.hasMore();
        boolean matching = count == epoch.getPassOneScopeCount()
                && digest.snapshot().equals(epoch.getPassOneOrderedSha256());
        String nextState = finalPage ? (matching ? "SEALED" : "ABORTED") : "PASS_TWO";
        requireOne(mapper.advancePassTwo(
                epoch.getOperationCode(), epoch.getEpochNo(), epoch.getVersion(),
                epoch.getPassTwoCursor(), epoch.getPassTwoScopeCount(),
                epoch.getPassTwoOrderedSha256(), cursor, count, digest.snapshot(), nextState
        ), "source pass-two CAS");
        return "SEALED".equals(nextState)
                ? SourceProgress.SEALED
                : ("ABORTED".equals(nextState) ? SourceProgress.DRIFTED : SourceProgress.SCANNING);
    }

    private ScheduleSourcePage sourcePage(
            ScheduleSourceEpochRow epoch,
            String cursor,
            ScheduleSourceReadContext.Pass pass
    ) {
        return sources.require(epoch.getOperationCode()).readPage(
                new ScheduleSourceReadContext(
                        epoch.getOperationCode(), epoch.getEpochNo(), pass, cursor,
                        epoch.getReconcileUntilUtc().toInstant(ZoneOffset.UTC), SCOPES_PER_STEP
                )
        );
    }

    private static ScheduleSourceOrderedDigest append(
            String state, ScheduleSourcePage page
    ) {
        ScheduleSourceOrderedDigest digest = ScheduleSourceOrderedDigest.resume(state);
        for (ScheduleSourceScope item : page.getItems()) {
            digest = digest.append(
                    item.getSourceCursor(), item.getScope().getStableScopeKey(),
                    item.getImmutablePayloadSha256()
            );
        }
        return digest;
    }

    private static List<ScheduleSourceStageRow> stageRows(
            ScheduleSourceEpochRow epoch, ScheduleSourcePage page
    ) {
        List<ScheduleSourceStageRow> result = new ArrayList<>(page.getItems().size());
        for (ScheduleSourceScope item : page.getItems()) {
            result.add(ScheduleSourceStageRow.from(
                    epoch.getOperationCode(), epoch.getEpochNo(), item
            ));
        }
        return result;
    }

    private SourceProgress abort(ScheduleSourceEpochRow epoch) {
        requireOne(mapper.abortEpoch(
                epoch.getOperationCode(), epoch.getEpochNo(), epoch.getVersion()
        ), "source epoch abort CAS");
        return SourceProgress.DRIFTED;
    }

    private long allocateEpochNo(OperationCode operation) {
        ScheduleEpochSequenceRow sequence = Objects.requireNonNull(
                mapper.lockEpochSequence(operation), "schedule epoch sequence"
        );
        long current = Objects.requireNonNull(
                sequence.getLastEpochNo(), "schedule epoch sequence value"
        );
        long version = Objects.requireNonNull(
                sequence.getVersion(), "schedule epoch sequence version"
        );
        if (current < 0 || version < 0) {
            throw new IllegalStateException("schedule epoch sequence is invalid");
        }
        long next = Math.addExact(current, 1L);
        requireOne(mapper.advanceEpochSequence(operation, current, version, next),
                "schedule epoch sequence CAS");
        return next;
    }

    private static LocalDateTime utcMillis(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }

    private static void requireOne(int changed, String action) {
        if (changed != 1) throw new IllegalStateException(action + " must affect one row");
    }

    public enum SourceProgress { SCANNING, SEALED, DRIFTED, CLEANING, APPLYING, IDLE }
}
