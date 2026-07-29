package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.noon.NoonProductDetailAdapter;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.noon.NoonShanghaiBusinessTime;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CompetitorProductDetailRefreshService {
    private final CompetitorAnalysisMapper mapper;
    private final CompetitorProductDetailBatchRunner batchRunner;
    private final boolean onlyMissingFromCompleteTop200Scan;

    @Autowired
    public CompetitorProductDetailRefreshService(
            CompetitorAnalysisMapper mapper,
            ObjectProvider<NoonProductDetailAdapter> detailAdapterProvider,
            ObjectProvider<CompetitorProductSnapshotService> snapshotServiceProvider,
            ObjectProvider<CompetitorListingObservationService> observationProvider,
            CompetitorProductDetailWriteGuard writeGuard
    ) {
        this(
                mapper,
                detailAdapterProvider == null ? null : detailAdapterProvider.getIfAvailable(),
                snapshotServiceProvider == null ? null : snapshotServiceProvider.getIfAvailable(),
                observationProvider == null ? null : observationProvider.getIfAvailable(),
                writeGuard,
                Clock.systemUTC(),
                true
        );
    }

    CompetitorProductDetailRefreshService(
            CompetitorAnalysisMapper mapper,
            NoonProductDetailAdapter detailAdapter,
            CompetitorProductSnapshotService snapshotService,
            Clock clock
    ) {
        this(
                mapper,
                detailAdapter,
                snapshotService,
                null,
                new CompetitorProductDetailWriteGuard(
                        mapper,
                        snapshotService,
                        CompetitorRefreshLeaseGuard.disabled(mapper)
                ),
                clock,
                false
        );
    }

    CompetitorProductDetailRefreshService(
            CompetitorAnalysisMapper mapper,
            NoonProductDetailAdapter detailAdapter,
            CompetitorProductSnapshotService snapshotService,
            CompetitorProductDetailWriteGuard writeGuard,
            Clock clock
    ) {
        this(
                mapper,
                detailAdapter,
                snapshotService,
                null,
                writeGuard,
                clock,
                false
        );
    }

    private CompetitorProductDetailRefreshService(
            CompetitorAnalysisMapper mapper,
            NoonProductDetailAdapter detailAdapter,
            CompetitorProductSnapshotService snapshotService,
            CompetitorListingObservationService observationService,
            CompetitorProductDetailWriteGuard writeGuard,
            Clock clock,
            boolean onlyMissingFromCompleteTop200Scan
    ) {
        this.mapper = mapper;
        this.onlyMissingFromCompleteTop200Scan =
                onlyMissingFromCompleteTop200Scan;
        Clock sourceClock = clock == null ? Clock.systemUTC() : clock;
        this.batchRunner = new CompetitorProductDetailBatchRunner(
                detailAdapter,
                snapshotService,
                writeGuard,
                observationService,
                new CompetitorProductDetailSupport(
                        sourceClock.withZone(NoonShanghaiBusinessTime.ZONE)
                )
        );
    }

    public CompetitorProductDetailRefreshResult refreshConfirmedCompetitors(
            CompetitorWatchProductRow watchProduct,
            Long searchRunId,
            Long taskId,
            Long actorUserId
    ) {
        return refreshConfirmedCompetitors(
                watchProduct, searchRunId, taskId, actorUserId, null
        );
    }

    public CompetitorProductDetailRefreshResult refreshConfirmedCompetitors(
            CompetitorWatchProductRow watchProduct,
            Long searchRunId,
            Long taskId,
            Long actorUserId,
            Runnable beforeFirstRequest
    ) {
        if (watchProduct == null || watchProduct.getId() == null) {
            return unavailable();
        }
        List<CompetitorProductDetailPlanEntry> targets =
                CompetitorProductDetailTargetPlan.initial(
                        mapper,
                        watchProduct,
                        onlyMissingFromCompleteTop200Scan
                );
        return batchRunner.refresh(
                watchProduct,
                targets,
                searchRunId,
                taskId,
                actorUserId,
                null,
                beforeFirstRequest
        );
    }

    public CompetitorProductDetailRefreshResult refreshTargets(
            CompetitorWatchProductRow watchProduct,
            List<CompetitorProductDetailTarget> targets,
            Long searchRunId,
            Long taskId,
            Long actorUserId
    ) {
        return refreshTargets(
                watchProduct, targets, searchRunId, taskId, actorUserId,
                null, null
        );
    }

    CompetitorProductDetailRefreshResult refreshTargets(
            CompetitorWatchProductRow watchProduct,
            List<CompetitorProductDetailTarget> targets,
            Long searchRunId,
            Long taskId,
            Long actorUserId,
            CompetitorDetailRetrySession retrySession
    ) {
        return refreshTargets(
                watchProduct, targets, searchRunId, taskId, actorUserId,
                retrySession, null
        );
    }

    CompetitorProductDetailRefreshResult refreshTargets(
            CompetitorWatchProductRow watchProduct,
            List<CompetitorProductDetailTarget> targets,
            Long searchRunId,
            Long taskId,
            Long actorUserId,
            CompetitorDetailRetrySession retrySession,
            Runnable beforeFirstRequest
    ) {
        if (watchProduct == null || watchProduct.getId() == null) {
            return unavailable();
        }
        List<CompetitorProductDetailPlanEntry> retryTargets =
                CompetitorProductDetailTargetPlan.retry(
                        mapper,
                        watchProduct,
                        targets,
                        onlyMissingFromCompleteTop200Scan
                );
        return batchRunner.refresh(
                watchProduct,
                retryTargets,
                searchRunId,
                taskId,
                actorUserId,
                retrySession,
                beforeFirstRequest
        );
    }

    List<CompetitorProductDetailTarget> currentTargets(
            CompetitorWatchProductRow watchProduct
    ) {
        if (watchProduct == null || watchProduct.getId() == null) {
            return List.of();
        }
        List<CompetitorProductDetailTarget> targets = new ArrayList<>();
        for (CompetitorProductDetailPlanEntry entry :
                CompetitorProductDetailTargetPlan.initial(
                        mapper,
                        watchProduct,
                        onlyMissingFromCompleteTop200Scan
                )) {
            targets.add(entry.target);
        }
        return targets;
    }

    private CompetitorProductDetailRefreshResult unavailable() {
        return CompetitorProductDetailRefreshResult.unavailable(
                "DETAIL_ADAPTER_UNAVAILABLE",
                "竞品列表补拉适配器或快照服务不可用。"
        );
    }
}
