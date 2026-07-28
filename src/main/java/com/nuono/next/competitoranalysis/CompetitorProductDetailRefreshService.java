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

    @Autowired
    public CompetitorProductDetailRefreshService(
            CompetitorAnalysisMapper mapper,
            ObjectProvider<NoonProductDetailAdapter> detailAdapterProvider,
            ObjectProvider<CompetitorProductSnapshotService> snapshotServiceProvider,
            CompetitorProductDetailWriteGuard writeGuard
    ) {
        this(
                mapper,
                detailAdapterProvider == null ? null : detailAdapterProvider.getIfAvailable(),
                snapshotServiceProvider == null ? null : snapshotServiceProvider.getIfAvailable(),
                writeGuard,
                Clock.systemUTC()
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
                new CompetitorProductDetailWriteGuard(
                        mapper,
                        snapshotService,
                        CompetitorRefreshLeaseGuard.disabled(mapper)
                ),
                clock
        );
    }

    CompetitorProductDetailRefreshService(
            CompetitorAnalysisMapper mapper,
            NoonProductDetailAdapter detailAdapter,
            CompetitorProductSnapshotService snapshotService,
            CompetitorProductDetailWriteGuard writeGuard,
            Clock clock
    ) {
        this.mapper = mapper;
        Clock sourceClock = clock == null ? Clock.systemUTC() : clock;
        this.batchRunner = new CompetitorProductDetailBatchRunner(
                detailAdapter,
                snapshotService,
                writeGuard,
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
        if (watchProduct == null || watchProduct.getId() == null) {
            return unavailable();
        }
        List<CompetitorProductDetailTargetPlan.Entry> targets =
                CompetitorProductDetailTargetPlan.initial(mapper, watchProduct);
        return batchRunner.refresh(
                watchProduct,
                targets,
                searchRunId,
                taskId,
                actorUserId,
                null
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
                watchProduct, targets, searchRunId, taskId, actorUserId, null
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
        if (watchProduct == null || watchProduct.getId() == null) {
            return CompetitorProductDetailRefreshResult.unavailable(
                    "DETAIL_ADAPTER_UNAVAILABLE",
                    "竞品详情适配器或快照服务不可用。"
            );
        }
        List<CompetitorProductDetailTargetPlan.Entry> retryTargets =
                CompetitorProductDetailTargetPlan.retry(mapper, watchProduct, targets);
        return batchRunner.refresh(
                watchProduct,
                retryTargets,
                searchRunId,
                taskId,
                actorUserId,
                retrySession
        );
    }

    List<CompetitorProductDetailTarget> currentTargets(
            CompetitorWatchProductRow watchProduct
    ) {
        if (watchProduct == null || watchProduct.getId() == null) {
            return List.of();
        }
        List<CompetitorProductDetailTarget> targets = new ArrayList<>();
        for (CompetitorProductDetailTargetPlan.Entry entry :
                CompetitorProductDetailTargetPlan.initial(mapper, watchProduct)) {
            targets.add(entry.target);
        }
        return targets;
    }

    private CompetitorProductDetailRefreshResult unavailable() {
        return CompetitorProductDetailRefreshResult.unavailable(
                "DETAIL_ADAPTER_UNAVAILABLE",
                "竞品详情适配器或快照服务不可用。"
        );
    }
}
