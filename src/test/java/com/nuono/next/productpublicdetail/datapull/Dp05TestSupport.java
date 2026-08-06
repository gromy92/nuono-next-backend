package com.nuono.next.productpublicdetail.datapull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.orchestration.ExecutionContext;
import com.nuono.next.datapull.orchestration.InMemoryBackoffHoldStore;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.BackoffPolicy;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.productpublicdetail.ProductPublicDetailCandidate;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailResult;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

final class Dp05TestSupport {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 2, 0, 0);
    static final DataPullScope SCOPE = new DataPullScope(
            307L,
            91L,
            "PRJ108065",
            "PRJ108065",
            "STR108065-NSA",
            "SA",
            "NOON:307:91:PRJ108065:STR108065-NSA:SA"
    );

    private Dp05TestSupport() {
    }

    static ProductPublicDetailCandidate candidate(long offerId, String code) {
        ProductPublicDetailCandidate value = new ProductPublicDetailCandidate();
        value.setOwnerUserId(307L);
        value.setLogicalStoreId(91L);
        value.setStoreCode("STR108065-NSA");
        value.setSiteCode("SA");
        value.setProductMasterId(1000L + offerId);
        value.setProductVariantId(2000L + offerId);
        value.setProductSiteOfferId(offerId);
        value.setPartnerSku("PSKU-" + offerId);
        value.setSkuParent(code);
        value.setNoonProductCode(code);
        return value;
    }

    static NoonPublicProductDetailResult partial(String code) {
        NoonPublicProductDetailResult result = new NoonPublicProductDetailResult();
        result.setStatus(com.nuono.next.productpublicdetail.ProductPublicDetailSyncStatus.PARTIAL);
        result.setNoonProductCode(code);
        result.setFailureCode("PARTIAL_DETAIL");
        result.setTitleEn("Product " + code);
        result.setFetchedAt(LocalDateTime.of(2026, 8, 2, 3, 31));
        return result;
    }

    static Dp05ProductDetailJob job(
            List<ProductPublicDetailCandidate> candidates,
            Function<Dp05FetchRequest, ProviderOutcome<Dp05ProviderValue>> frontend,
            Function<Dp05FetchRequest, ProviderOutcome<Dp05ProviderValue>> partner,
            RecordingWriter writer,
            InMemoryBackoffHoldStore holds
    ) {
        Dp05ProductCursor cursor = (scope, afterOfferId) -> candidates.stream()
                .filter(candidate -> candidate.getProductSiteOfferId() > afterOfferId)
                .findFirst()
                .orElse(null);
        return new Dp05ProductDetailJob(
                () -> List.of(SCOPE),
                cursor,
                frontend::apply,
                partner::apply,
                writer,
                new Dp05CheckpointCodec(mapper()),
                new Dp05StageBackoff(
                        holds,
                        new ProviderWaitTransition(new BackoffPolicy(
                                Duration.ofMinutes(1), Duration.ofHours(6), 0.0d
                        ))
                )
        );
    }

    static ObjectMapper mapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    static ExecutionContext context(String checkpoint, int attempt) {
        DataPullTask task = new DataPullTask();
        task.setId(500L);
        task.setOperationCode(OperationCode.DP05);
        task.setProviderChannel(Dp05ProductDetailJob.ROUTER_CHANNEL);
        task.setOwnerUserId(SCOPE.getOwnerUserId());
        task.setLogicalStoreId(SCOPE.getLogicalStoreId());
        task.setAccountKey(SCOPE.getAccountKey());
        task.setProjectCode(SCOPE.getProjectCode());
        task.setStoreCode(SCOPE.getStoreCode());
        task.setSiteCode(SCOPE.getSiteCode());
        task.setScopeKey(SCOPE.getStableScopeKey());
        task.setBusinessWindowKey("DP05:current-valid-items:2026-08-02");
        task.setState(TaskState.RUNNING);
        task.setStepCode(Dp05ProductDetailJob.INITIAL_STEP);
        task.setCheckpoint(checkpoint);
        task.setAttempt(attempt);
        task.setFenceEpoch(1L);
        task.setVersion(1L);
        task.setLeaseOwner("worker");
        task.setLeaseUntil(NOW.plusMinutes(10));
        return new ExecutionContext(task, NOW);
    }

    static final class RecordingWriter implements Dp05ProductDetailFactWriter {
        private final List<Write> writes = new ArrayList<>();

        @Override
        public ApplyResult apply(
                DataPullTask task,
                ProductPublicDetailCandidate candidate,
                NoonPublicProductDetailResult result,
                LocalDate factDate,
                long actorUserId
        ) {
            writes.add(new Write(candidate, result, factDate, actorUserId));
            return ApplyResult.APPLIED;
        }

        List<Write> writes() {
            return writes;
        }
    }

    static final class Write {
        final ProductPublicDetailCandidate candidate;
        final NoonPublicProductDetailResult result;
        final LocalDate factDate;
        final long actorUserId;

        private Write(
                ProductPublicDetailCandidate candidate,
                NoonPublicProductDetailResult result,
                LocalDate factDate,
                long actorUserId
        ) {
            this.candidate = candidate;
            this.result = result;
            this.factDate = factDate;
            this.actorUserId = actorUserId;
        }
    }
}
