package com.nuono.next.productpublicdetail;

import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.Dp05RuntimeMapper;
import com.nuono.next.productpublicdetail.datapull.Dp05ProductDetailFactWriter;
import com.nuono.next.productpublicdetail.datapull.Dp05TaskFenceRow;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailResult;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reuses the existing daily snapshot upsert behind one short runtime transaction. */
@Service
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public class ProductPublicDetailRuntimeFactWriter implements Dp05ProductDetailFactWriter {

    private final ProductPublicDetailSyncService syncSupport;
    private final Dp05RuntimeMapper mapper;

    public ProductPublicDetailRuntimeFactWriter(
            ProductPublicDetailSyncService syncSupport,
            Dp05RuntimeMapper mapper
    ) {
        this.syncSupport = Objects.requireNonNull(syncSupport, "syncSupport");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public ApplyResult apply(
            DataPullTask task,
            ProductPublicDetailCandidate candidate,
            NoonPublicProductDetailResult result,
            LocalDate factDate,
            long actorUserId
    ) {
        DataPullTask claimed = Objects.requireNonNull(task, "task");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(factDate, "factDate");
        if (!sameScope(claimed, candidate, actorUserId)
                || !hasExactProductIdentity(candidate, result)) {
            throw new IllegalArgumentException("DP05 fact identity is outside the claimed task");
        }
        Dp05TaskFenceRow fence = mapper.selectTaskFenceForUpdate(claimed.getId());
        if (!ownsFence(fence, claimed)) {
            return ApplyResult.STALE_FENCE;
        }
        ProductPublicDetailSnapshot snapshot = syncSupport.toSnapshot(candidate, result, actorUserId);
        snapshot.setFactDate(factDate);
        syncSupport.upsertSnapshot(snapshot);
        if (mapper.countLiveTaskFence(
                claimed.getId(),
                claimed.getFenceEpoch(),
                claimed.getLeaseOwner()
        ) != 1) {
            throw new IllegalStateException("DP05 task lease expired during fact apply");
        }
        return ApplyResult.APPLIED;
    }

    private boolean ownsFence(Dp05TaskFenceRow fence, DataPullTask task) {
        return fence != null
                && Objects.equals(fence.getTaskId(), task.getId())
                && OperationCode.DP05.name().equals(fence.getOperationCode())
                && "RUNNING".equals(fence.getState())
                && Objects.equals(fence.getFenceEpoch(), task.getFenceEpoch())
                && Objects.equals(fence.getLeaseOwner(), task.getLeaseOwner())
                && Boolean.TRUE.equals(fence.getLeaseValid());
    }

    private boolean sameScope(
            DataPullTask task,
            ProductPublicDetailCandidate candidate,
            long actorUserId
    ) {
        return Objects.equals(task.getOwnerUserId(), candidate.getOwnerUserId())
                && Objects.equals(task.getOwnerUserId(), actorUserId)
                && Objects.equals(task.getLogicalStoreId(), candidate.getLogicalStoreId())
                && normalize(task.getStoreCode()).equals(normalize(candidate.getStoreCode()))
                && normalize(task.getSiteCode()).equals(normalize(candidate.getSiteCode()));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean hasExactProductIdentity(
            ProductPublicDetailCandidate candidate,
            NoonPublicProductDetailResult result
    ) {
        String expected = NoonProductCodeSupport.normalize(candidate.getNoonProductCode());
        String actual = NoonProductCodeSupport.normalize(result.getNoonProductCode());
        return expected != null && expected.equals(actual);
    }
}
