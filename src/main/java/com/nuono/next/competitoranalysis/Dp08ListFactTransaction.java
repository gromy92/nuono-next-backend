package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.dp08.Dp08FactWriter;
import com.nuono.next.competitoranalysis.dp08.Dp08ListTarget;
import com.nuono.next.competitoranalysis.dp08.Dp08MemberSetHandle;
import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.CompetitorListingObservationMapper;
import com.nuono.next.infrastructure.mapper.Dp08MemberSetMapper;
import com.nuono.next.infrastructure.mapper.Dp08RuntimeMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Spring transaction boundary for one bounded DP08-B fact advance. */
@Service
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
final class Dp08ListFactTransaction {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final Dp08FactFence fence;
    private final Dp08ListFactSupport factSupport;
    private final Dp08ListMemberFactApplier memberApplier;

    @Autowired
    Dp08ListFactTransaction(
            Dp08RuntimeMapper runtimeMapper,
            CompetitorListingObservationMapper observationMapper,
            CompetitorProductSnapshotService snapshotService,
            Dp08MemberSetMapper memberSetMapper
    ) {
        this.fence = new Dp08FactFence(runtimeMapper);
        this.factSupport = new Dp08ListFactSupport(
                runtimeMapper,
                observationMapper,
                snapshotService,
                new CompetitorProductDetailSupport(Clock.system(SHANGHAI))
        );
        this.memberApplier = memberSetMapper == null
                ? null
                : new Dp08ListMemberFactApplier(memberSetMapper, fence, factSupport);
    }

    Dp08ListFactTransaction(
            Dp08RuntimeMapper runtimeMapper,
            CompetitorListingObservationMapper observationMapper,
            CompetitorProductSnapshotService snapshotService
    ) {
        this(runtimeMapper, observationMapper, snapshotService, null);
    }

    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    Dp08FactWriter.ApplyResult applyFound(
            DataPullTask task,
            Dp08ListTarget target,
            NoonProductDetail detail
    ) {
        fence.require(task, OperationCode.DP08B);
        factSupport.recordFound(target, detail, true);
        fence.requireStillLive(task);
        return Dp08FactWriter.ApplyResult.APPLIED;
    }

    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    Dp08FactWriter.ApplyResult applyNotFound(
            DataPullTask task,
            Dp08ListTarget target,
            NoonSearchPage evidence
    ) {
        fence.require(task, OperationCode.DP08B);
        factSupport.recordNotFound(target, evidence);
        fence.requireStillLive(task);
        return Dp08FactWriter.ApplyResult.APPLIED;
    }

    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    Dp08FactWriter.ApplyResult applyFound(
            DataPullTask task,
            Dp08MemberSetHandle handle,
            LocalDate factDate,
            NoonProductDetail detail
    ) {
        return requireMemberApplier().applyFound(task, handle, factDate, detail);
    }

    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    Dp08FactWriter.ApplyResult applyNotFound(
            DataPullTask task,
            Dp08MemberSetHandle handle,
            LocalDate factDate,
            NoonSearchPage evidence
    ) {
        return requireMemberApplier().applyNotFound(task, handle, factDate, evidence);
    }

    private Dp08ListMemberFactApplier requireMemberApplier() {
        if (memberApplier == null) {
            throw new IllegalStateException("DP08 member-set mapper is unavailable");
        }
        return memberApplier;
    }
}
