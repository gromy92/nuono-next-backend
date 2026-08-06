package com.nuono.next.datapull.advertising;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import com.nuono.next.datapull.snapshot.SnapshotPage;
import com.nuono.next.noonads.NoonAdvertisingCampaignFact;
import com.nuono.next.noonads.NoonAdvertisingQueryFact;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Executes exactly one DP-06 provider operation and stages its complete response. */
final class AdvertisingProviderSteps {

    private final AdvertisingProvider provider;
    private final AdvertisingStageCoordinator stageCoordinator;
    private final AdvertisingJobTransitions transitions;

    AdvertisingProviderSteps(
            AdvertisingProvider provider,
            AdvertisingStageCoordinator stageCoordinator,
            AdvertisingJobTransitions transitions
    ) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.stageCoordinator = Objects.requireNonNull(stageCoordinator, "stageCoordinator");
        this.transitions = Objects.requireNonNull(transitions, "transitions");
    }

    AdvanceResult resolveAdvertiser(
            DataPullTask task,
            AdvertisingPullRequest request,
            AdvertisingCheckpoint checkpoint
    ) {
        ProviderOutcome<AdvertisingAdvertiser> outcome = safeOutcome(
                () -> provider.resolveAdvertiser(request)
        );
        if (outcome.getType() != ProviderOutcomeType.SUCCESS) {
            return transitions.waitForProvider(task, checkpoint, outcome);
        }
        if (outcome.getValue() == null) {
            return transitions.waitForProvider(
                    task,
                    checkpoint,
                    ProviderOutcome.contractError("ADS_ADVERTISER_MISSING")
            );
        }
        return transitions.queued(checkpoint.dashboard(outcome.getValue()));
    }

    AdvanceResult fetchDashboard(
            DataPullTask task,
            AdvertisingPullRequest request,
            AdvertisingCheckpoint checkpoint
    ) {
        ProviderOutcome<AdvertisingDashboard> outcome = safeOutcome(
                () -> provider.fetchDashboard(request, checkpoint.getAdvertiser())
        );
        if (outcome.getType() != ProviderOutcomeType.SUCCESS) {
            return transitions.waitForProvider(task, checkpoint, outcome);
        }
        AdvertisingDashboard dashboard = outcome.getValue();
        if (dashboard == null) {
            return transitions.waitForProvider(
                    task,
                    checkpoint,
                    ProviderOutcome.contractError("ADS_DASHBOARD_MISSING")
            );
        }
        int totalPages;
        try {
            totalPages = Math.addExact(1, dashboard.getActiveCampaigns().size());
        } catch (ArithmeticException impossibleContainerSize) {
            return transitions.waitForProvider(
                    task,
                    checkpoint,
                    ProviderOutcome.contractError("ADS_CAMPAIGN_COUNT_INVALID")
            );
        }
        List<AdvertisingStagedFact> facts = new ArrayList<>();
        for (NoonAdvertisingCampaignFact fact : dashboard.getCampaignFacts()) {
            facts.add(AdvertisingStagedFact.campaign(fact));
        }
        boolean lastPage = dashboard.getActiveCampaigns().isEmpty();
        SnapshotPage<AdvertisingStagedFact> page = new SnapshotPage<>(
                1,
                lastPage ? null : 2,
                lastPage,
                totalPages,
                facts,
                dashboard.getAuthority().asSnapshotAuthority(),
                dashboard.getSourceCampaignCount(),
                dashboard.getBusinessSkippedCampaignCount()
        );
        AdvanceResult stageFailure = stageCoordinator.stage(task, checkpoint, page);
        return stageFailure == null
                ? transitions.queued(checkpoint.afterDashboard(
                        dashboard.getActiveCampaigns(),
                        dashboard.getAuthority()
                ))
                : stageFailure;
    }

    AdvanceResult fetchCampaign(
            DataPullTask task,
            AdvertisingPullRequest request,
            AdvertisingCheckpoint checkpoint
    ) {
        AdvertisingCampaignRef campaign = checkpoint.currentCampaign();
        ProviderOutcome<AdvertisingQueryReport> outcome = safeOutcome(
                () -> provider.fetchCampaignQueries(
                        request,
                        checkpoint.getAdvertiser(),
                        campaign
                )
        );
        if (outcome.getType() != ProviderOutcomeType.SUCCESS) {
            return transitions.waitForProvider(task, checkpoint, outcome);
        }
        AdvertisingQueryReport report = outcome.getValue();
        if (report == null) {
            return transitions.waitForProvider(
                    task,
                    checkpoint,
                    ProviderOutcome.contractError("ADS_CAMPAIGN_REPORT_MISSING")
            );
        }
        SnapshotPage<AdvertisingStagedFact> page;
        try {
            List<AdvertisingStagedFact> facts = queryFacts(campaign, report.getFacts());
            int businessSkipped = Math.addExact(
                    report.getBusinessSkippedItemCount(),
                    report.getFacts().size() - (facts.size() - 1)
            );
            int pageNo = Math.addExact(2, checkpoint.getNextCampaignIndex());
            int totalPages = Math.addExact(1, checkpoint.getActiveCampaigns().size());
            boolean lastPage = checkpoint.getNextCampaignIndex()
                    == checkpoint.getActiveCampaigns().size() - 1;
            page = new SnapshotPage<>(
                    pageNo,
                    lastPage ? null : Math.incrementExact(pageNo),
                    lastPage,
                    totalPages,
                    facts,
                    checkpoint.getAuthority().asSnapshotAuthority(),
                    Math.incrementExact(report.getSourceItemCount()),
                    businessSkipped
            );
        } catch (RuntimeException invalidCampaignContainer) {
            return transitions.waitForProvider(
                    task,
                    checkpoint,
                    ProviderOutcome.contractError("ADS_CAMPAIGN_CONTAINER_INVALID")
            );
        }
        AdvanceResult stageFailure = stageCoordinator.stage(task, checkpoint, page);
        return stageFailure == null
                ? transitions.queued(checkpoint.nextCampaign())
                : stageFailure;
    }

    private List<AdvertisingStagedFact> queryFacts(
            AdvertisingCampaignRef campaign,
            List<NoonAdvertisingQueryFact> rows
    ) {
        List<AdvertisingStagedFact> facts = new ArrayList<>();
        facts.add(AdvertisingStagedFact.queryPageProof(campaign));
        for (NoonAdvertisingQueryFact row : rows) {
            if (row != null && campaign.getCampaignCode().equals(row.getCampaignCode())) {
                facts.add(AdvertisingStagedFact.query(row));
            }
        }
        return facts;
    }

    private <T> ProviderOutcome<T> safeOutcome(ProviderCall<T> call) {
        try {
            ProviderOutcome<T> result = call.execute();
            return result == null
                    ? ProviderOutcome.transientFailure("ADS_PROVIDER_OUTCOME_MISSING")
                    : result;
        } catch (RuntimeException untypedFailure) {
            return ProviderOutcome.transientFailure("ADS_PROVIDER_UNTYPED_FAILURE");
        }
    }

    @FunctionalInterface
    private interface ProviderCall<T> {
        ProviderOutcome<T> execute();
    }
}
