package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.advertising.AdvertisingAdvertiser;
import com.nuono.next.datapull.advertising.AdvertisingCampaignRef;
import com.nuono.next.datapull.advertising.AdvertisingDashboard;
import com.nuono.next.datapull.advertising.AdvertisingPullRequest;
import com.nuono.next.datapull.advertising.AdvertisingQueryReport;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RealNoonAdvertisingRuntimeProviderTest {

    @Test
    void eachMethodMakesOneRemoteActionAndDashboardIncludesAllActiveCampaignsWithoutActivityCap() {
        RecordingNoonAdvertisingGatewaySession session =
                new RecordingNoonAdvertisingGatewaySession();
        RealNoonAdvertisingRuntimeProvider provider = provider(session);
        AdvertisingPullRequest request = request();

        ProviderOutcome<AdvertisingAdvertiser> advertiser = provider.resolveAdvertiser(request);
        ProviderOutcome<AdvertisingDashboard> dashboard = provider.fetchDashboard(
                request,
                advertiser.getValue()
        );
        AdvertisingCampaignRef first = dashboard.getValue().getActiveCampaigns().get(0);
        ProviderOutcome<AdvertisingQueryReport> queries = provider.fetchCampaignQueries(
                request,
                advertiser.getValue(),
                first
        );

        assertEquals(ProviderOutcomeType.SUCCESS, advertiser.getType());
        assertEquals(ProviderOutcomeType.SUCCESS, dashboard.getType());
        assertEquals(ProviderOutcomeType.SUCCESS, queries.getType());
        assertEquals(1, session.advertiserCalls);
        assertEquals(1, session.dashboardCalls);
        assertEquals(1, session.queryCalls);
        assertEquals(
                List.of("C-LIVE-NO-ACTIVITY", "C-RUNNING"),
                campaignCodes(dashboard.getValue().getActiveCampaigns())
        );
        assertEquals(3, dashboard.getValue().getCampaignFacts().size());
        assertEquals(3, dashboard.getValue().getAuthority().getDeclaredCampaignCount());
        assertTrue(dashboard.getValue().getAuthority().isComplete());
        assertEquals(2, queries.getValue().getSourceItemCount());
        assertEquals(1, queries.getValue().getBusinessSkippedItemCount());
        assertEquals(1, queries.getValue().getFacts().size());
        assertEquals("paper towel", queries.getValue().getFacts().get(0).getQueryText());
        assertEquals("PRJ108065", session.lastHeaders.get("X-Project"));
        assertEquals("ADV_108065", session.lastHeaders.get("x-advertiser-codes"));
    }

    @Test
    void unknownCampaignStatusIsContractErrorAndRateLimitIsRiskControl() {
        RecordingNoonAdvertisingGatewaySession invalidStatus =
                new RecordingNoonAdvertisingGatewaySession();
        invalidStatus.unknownStatus = true;
        RealNoonAdvertisingRuntimeProvider invalidProvider = provider(invalidStatus);

        ProviderOutcome<AdvertisingDashboard> contract = invalidProvider.fetchDashboard(
                request(),
                new AdvertisingAdvertiser("ADV_108065")
        );

        assertEquals(ProviderOutcomeType.CONTRACT_ERROR, contract.getType());
        assertEquals("ADS_CAMPAIGN_STATUS_UNKNOWN", contract.getSanitizedCode());

        RecordingNoonAdvertisingGatewaySession rateLimited =
                new RecordingNoonAdvertisingGatewaySession();
        rateLimited.rateLimited = true;
        ProviderOutcome<AdvertisingDashboard> risk = provider(rateLimited).fetchDashboard(
                request(),
                new AdvertisingAdvertiser("ADV_108065")
        );
        assertEquals(ProviderOutcomeType.RISK_CONTROL, risk.getType());
        assertEquals("ADS_RISK_CONTROL", risk.getSanitizedCode());
    }

    @Test
    void dashboardWithoutProviderNativeCampaignAuthorityIsContractError() {
        RecordingNoonAdvertisingGatewaySession session =
                new RecordingNoonAdvertisingGatewaySession();
        session.omitAuthority = true;

        ProviderOutcome<AdvertisingDashboard> outcome = provider(session).fetchDashboard(
                request(),
                new AdvertisingAdvertiser("ADV_108065")
        );

        assertEquals(ProviderOutcomeType.CONTRACT_ERROR, outcome.getType());
        assertEquals("ADS_CAMPAIGN_AUTHORITY_MISSING", outcome.getSanitizedCode());
        assertEquals(1, session.dashboardCalls);
    }

    @Test
    void oversizedCampaignPayloadFailsTheWholeDashboardContainerWithoutFacts() {
        RecordingNoonAdvertisingGatewaySession session =
                new RecordingNoonAdvertisingGatewaySession();
        session.oversizedCampaignPayload = true;

        ProviderOutcome<AdvertisingDashboard> outcome = provider(session).fetchDashboard(
                request(),
                new AdvertisingAdvertiser("ADV_108065")
        );

        assertEquals(ProviderOutcomeType.CONTRACT_ERROR, outcome.getType());
        assertEquals("ADS_FIELD_TOO_LARGE", outcome.getSanitizedCode());
        assertNull(outcome.getValue());
        assertEquals(1, session.dashboardCalls);
    }

    private RealNoonAdvertisingRuntimeProvider provider(
            RecordingNoonAdvertisingGatewaySession session
    ) {
        NoonPullStoreBinding binding = new NoonPullStoreBinding(
                307L,
                "PRJ108065",
                "STR108065-NSA",
                "SA",
                "108065",
                "seller@example.com",
                "cookie=value"
        );
        return new RealNoonAdvertisingRuntimeProvider(
                new ObjectMapper(),
                new StaticBindingResolver(binding),
                ignored -> session,
                "https://admanager.noon.partners"
        );
    }

    private AdvertisingPullRequest request() {
        DataPullTask task = DataPullTask.queued(
                6001L,
                OperationCode.DP06,
                "noon-admanager",
                307L,
                108065L,
                "PRJ108065",
                null,
                "PRJ108065",
                "STR108065-NSA",
                "SA",
                "scope-sa",
                LocalDateTime.of(2026, 8, 1, 22, 30),
                "DP06:date-range:2026-08-01..2026-08-01",
                "ADS_ADVERTISER",
                LocalDateTime.of(2026, 8, 1, 22, 0)
        );
        return AdvertisingPullRequest.from(task);
    }

    private List<String> campaignCodes(List<AdvertisingCampaignRef> campaigns) {
        List<String> result = new ArrayList<>();
        for (AdvertisingCampaignRef campaign : campaigns) {
            result.add(campaign.getCampaignCode());
        }
        return result;
    }

    private static final class StaticBindingResolver extends NoonPullStoreBindingResolver {
        private final NoonPullStoreBinding binding;

        private StaticBindingResolver(NoonPullStoreBinding binding) {
            super(null);
            this.binding = binding;
        }

        @Override
        public NoonPullStoreBinding resolve(NoonReportPullRequest request) {
            return binding;
        }
    }

}
