package com.nuono.next.datapull.advertising;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.orchestration.ExecutionContext;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.BackoffPolicy;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.datapull.snapshot.InMemorySnapshotStageStore;
import com.nuono.next.datapull.snapshot.SnapshotPage;
import com.nuono.next.datapull.snapshot.SnapshotStageProof;
import com.nuono.next.datapull.snapshot.SnapshotStageResult;
import com.nuono.next.datapull.snapshot.SnapshotStageStore;
import com.nuono.next.noonads.NoonAdvertisingCampaignFact;
import com.nuono.next.noonads.NoonAdvertisingQueryFact;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Dp06AdvertisingTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 2, 12, 0);
    private static final String CHANNEL = "noon-admanager";

    private Dp06AdvertisingTestSupport() {
    }

    static Dp06AdvertisingJob job(
            AdvertisingProvider provider,
            SnapshotStageStore<AdvertisingStagedFact> stage,
            AdvertisingFactWriter writer
    ) {
        DataPullScope scope = new DataPullScope(
                307L,
                108065L,
                "PRJ108065",
                "egress-cn-1",
                "PRJ108065",
                "STR108065-NSA",
                "SA",
                "scope-sa"
        );
        return new Dp06AdvertisingJob(
                CHANNEL,
                () -> List.of(scope),
                provider,
                stage,
                writer,
                new ProviderWaitTransition(new BackoffPolicy(
                        Duration.ofMinutes(1), Duration.ofHours(1), 0.0d
                )),
                Duration.ofSeconds(30)
        );
    }

    static SnapshotStageStore<AdvertisingStagedFact> stageStore() {
        return new InMemorySnapshotStageStore<>(new AdvertisingStagedFactCodec());
    }

    static DataPullTask task() {
        DataPullTask task = DataPullTask.queued(
                6001L,
                OperationCode.DP06,
                CHANNEL,
                307L,
                108065L,
                "PRJ108065",
                "egress-cn-1",
                "PRJ108065",
                "STR108065-NSA",
                "SA",
                "scope-sa",
                LocalDateTime.of(2026, 8, 1, 22, 30),
                "DP06:date-range:2026-08-01..2026-08-01",
                Dp06AdvertisingJob.INITIAL_STEP,
                NOW.minusHours(1)
        );
        task.setState(TaskState.RUNNING);
        task.setLeaseOwner("worker-1");
        task.setLeaseUntil(NOW.plusHours(1));
        task.setFenceEpoch(1L);
        task.setVersion(1L);
        return task;
    }

    static ExecutionContext context(DataPullTask task) {
        task.setState(TaskState.RUNNING);
        return new ExecutionContext(task, NOW);
    }

    static void continueTask(DataPullTask task, AdvanceResult result) {
        task.setCheckpoint(result.getCheckpoint());
        task.setStepCode(result.getStepCode());
        task.setState(TaskState.RUNNING);
        task.setFenceEpoch(task.getFenceEpoch() + 1L);
        task.setVersion(task.getVersion() + 1L);
    }

    static AdvertisingCampaignPage campaignPage() {
        return campaignPage(
                1,
                1,
                3L,
                List.of(
                        campaign("C-LIVE-1", "live"),
                        campaign("C-LIVE-2", "active"),
                        campaign("C-PAUSED", "paused")
                )
        );
    }

    static AdvertisingCampaignEnumerationAuthority authority(long campaignCount) {
        return AdvertisingCampaignEnumerationAuthority.fromTwoPassObservation(
                "1".repeat(64),
                campaignCount,
                true
        );
    }

    static AdvertisingCampaignPage campaignPage(
            int pageNo,
            int totalPages,
            long declaredCount,
            List<NoonAdvertisingCampaignFact> facts
    ) {
        List<AdvertisingStagedFact> staged = new ArrayList<>();
        List<AdvertisingCampaignObservation> observations = new ArrayList<>();
        for (NoonAdvertisingCampaignFact fact : facts) {
            staged.add(AdvertisingStagedFact.campaign(fact));
            observations.add(new AdvertisingCampaignObservation(
                    new AdvertisingCampaignRef(
                            fact.getCampaignCode(), fact.getCampaignName()
                    ),
                    "live".equals(fact.getCampaignStatus())
                            || "active".equals(fact.getCampaignStatus())
            ));
        }
        return new AdvertisingCampaignPage(
                pageNo, totalPages, declaredCount, staged, observations, List.of()
        );
    }

    static NoonAdvertisingCampaignFact campaign(String code, String status) {
        NoonAdvertisingCampaignFact fact = new NoonAdvertisingCampaignFact();
        fact.setCampaignCode(code);
        fact.setCampaignName(code + " name");
        fact.setCampaignStatus(status);
        fact.setSpendAmount(new BigDecimal("10.00"));
        return fact;
    }

    static NoonAdvertisingQueryFact query(String campaignCode, String query, String spend) {
        NoonAdvertisingQueryFact fact = new NoonAdvertisingQueryFact();
        fact.setCampaignCode(campaignCode);
        fact.setCampaignName(campaignCode + " name");
        fact.setAdSkuCode("ZSKU-1");
        fact.setQueryText(query);
        fact.setQueryKind("search_term");
        fact.setSpendAmount(new BigDecimal(spend));
        return fact;
    }

    static AdvertisingQueryReport queryReport(List<NoonAdvertisingQueryFact> facts) {
        return AdvertisingQueryReport.complete(facts);
    }

    static final class RecordingWriter implements AdvertisingFactWriter {
        final List<AdvertisingApplyCommand> commands = new ArrayList<>();
        int resetCalls;

        @Override
        public ApplyResult applyComplete(AdvertisingApplyCommand command) {
            commands.add(command);
            return ApplyResult.APPLIED;
        }

        @Override
        public ResetResult reset(long taskId, long fenceEpoch, String leaseOwner) {
            resetCalls++;
            return ResetResult.CLEARED;
        }
    }

    static final class ScriptedProvider implements AdvertisingProvider {
        final Deque<ProviderOutcome<AdvertisingAdvertiser>> advertisers = new ArrayDeque<>();
        final Deque<ProviderOutcome<AdvertisingCampaignPage>> campaignPages =
                new ArrayDeque<>();
        final Map<String, Deque<ProviderOutcome<AdvertisingQueryReport>>> queryResults =
                new LinkedHashMap<>();
        final List<String> calls = new ArrayList<>();

        Deque<ProviderOutcome<AdvertisingQueryReport>> queries(String campaignCode) {
            return queryResults.computeIfAbsent(campaignCode, ignored -> new ArrayDeque<>());
        }

        @Override
        public ProviderOutcome<AdvertisingAdvertiser> resolveAdvertiser(AdvertisingPullRequest request) {
            calls.add("ADVERTISER");
            return advertisers.removeFirst();
        }

        @Override
        public ProviderOutcome<AdvertisingCampaignPage> fetchCampaignPage(
                AdvertisingPullRequest request,
                AdvertisingAdvertiser advertiser,
                int pageNo
        ) {
            calls.add("CAMPAIGNS:" + pageNo);
            assertNotNull(advertiser);
            return campaignPages.removeFirst();
        }

        @Override
        public ProviderOutcome<AdvertisingQueryReport> fetchCampaignQueries(
                AdvertisingPullRequest request,
                AdvertisingAdvertiser advertiser,
                AdvertisingCampaignRef campaign
        ) {
            calls.add("QUERY:" + campaign.getCampaignCode());
            return queries(campaign.getCampaignCode()).removeFirst();
        }
    }

    static final class RejectingStageStore implements SnapshotStageStore<AdvertisingStagedFact> {
        int clearCalls;

        @Override
        public SnapshotStageResult stagePage(
                long taskId,
                long fenceEpoch,
                SnapshotPage<AdvertisingStagedFact> page
        ) {
            return SnapshotStageResult.rejected("SNAPSHOT_PAGE_CONTENT_DRIFT");
        }

        @Override
        public SnapshotStageProof<AdvertisingStagedFact> proveComplete(
                long taskId,
                long fenceEpoch
        ) {
            return SnapshotStageProof.incomplete("SNAPSHOT_PAGE_CONTENT_DRIFT");
        }

        @Override
        public boolean clear(long taskId, long fenceEpoch) {
            clearCalls++;
            return true;
        }
    }
}
