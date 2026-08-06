package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.advertising.AdvertisingAdvertiser;
import com.nuono.next.datapull.advertising.AdvertisingCampaignPage;
import com.nuono.next.datapull.advertising.AdvertisingCampaignRef;
import com.nuono.next.datapull.advertising.AdvertisingPullRequest;
import com.nuono.next.datapull.advertising.AdvertisingQueryReport;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RealNoonAdvertisingRuntimeProviderTest {

    @Test
    void eachMethodMakesOneRemoteActionAndCampaignRequestCarriesRealPagination() {
        RecordingNoonAdvertisingGatewaySession session =
                new RecordingNoonAdvertisingGatewaySession();
        RealNoonAdvertisingRuntimeProvider provider = provider(session);
        AdvertisingPullRequest request = request();

        ProviderOutcome<AdvertisingAdvertiser> advertiser = provider.resolveAdvertiser(request);
        ProviderOutcome<AdvertisingCampaignPage> campaigns = provider.fetchCampaignPage(
                request, advertiser.getValue(), 1
        );
        AdvertisingCampaignRef first = campaigns.getValue()
                .getObservations().get(0).getCampaign();
        ProviderOutcome<AdvertisingQueryReport> queries = provider.fetchCampaignQueries(
                request, advertiser.getValue(), first
        );

        assertEquals(ProviderOutcomeType.SUCCESS, advertiser.getType());
        assertEquals(ProviderOutcomeType.SUCCESS, campaigns.getType());
        assertEquals(ProviderOutcomeType.SUCCESS, queries.getType());
        assertEquals(1, session.advertiserCalls);
        assertEquals(1, session.campaignPageCalls);
        assertEquals(1, session.queryCalls);
        assertTrue(session.lastJsonUrl.endsWith("/metrics/campaigns"));
        assertEquals(1, session.lastJsonBody.path("pageNo").asInt());
        assertEquals(200, session.lastJsonBody.path("pageSize").asInt());
        assertEquals(1, session.lastJsonBody.path("marketplace").get(0).asInt());
        assertEquals(3, campaigns.getValue().getFacts().size());
        assertEquals(3, campaigns.getValue().getObservations().size());
        assertEquals(3L, campaigns.getValue().getDeclaredCampaignCount());
        assertEquals(1, campaigns.getValue().getTotalPages());
        assertEquals(2, queries.getValue().getSourceItemCount());
        assertEquals(1, queries.getValue().getBusinessSkippedItemCount());
        assertEquals(1, queries.getValue().getFacts().size());
        assertEquals("paper towel", queries.getValue().getFacts().get(0).getQueryText());
        assertEquals("PRJ108065", session.lastHeaders.get("X-Project"));
        assertEquals("ADV_108065", session.lastHeaders.get("x-advertiser-codes"));
    }

    @Test
    void unknownCampaignStatusSkipsOnlyThatRowWhileRateLimitStillBacksOffWholeCall() {
        RecordingNoonAdvertisingGatewaySession invalidStatus =
                new RecordingNoonAdvertisingGatewaySession();
        invalidStatus.unknownStatus = true;

        ProviderOutcome<AdvertisingCampaignPage> partial = provider(invalidStatus)
                .fetchCampaignPage(request(), new AdvertisingAdvertiser("ADV_108065"), 1);

        assertEquals(ProviderOutcomeType.SUCCESS, partial.getType());
        assertEquals(2, partial.getValue().getFacts().size());
        assertEquals(1, partial.getValue().getBusinessSkippedItemCount());
        assertEquals(3, partial.getValue().getSourceItemCount());

        RecordingNoonAdvertisingGatewaySession rateLimited =
                new RecordingNoonAdvertisingGatewaySession();
        rateLimited.rateLimited = true;
        ProviderOutcome<AdvertisingCampaignPage> risk = provider(rateLimited)
                .fetchCampaignPage(request(), new AdvertisingAdvertiser("ADV_108065"), 1);
        assertEquals(ProviderOutcomeType.RISK_CONTROL, risk.getType());
        assertEquals("ADS_RISK_CONTROL", risk.getSanitizedCode());
    }

    @Test
    void missingPaginationRejectsWholePageBecauseContainerExtentIsUnknown() {
        RecordingNoonAdvertisingGatewaySession session =
                new RecordingNoonAdvertisingGatewaySession();
        session.omitPagination = true;

        ProviderOutcome<AdvertisingCampaignPage> outcome = provider(session)
                .fetchCampaignPage(request(), new AdvertisingAdvertiser("ADV_108065"), 1);

        assertEquals(ProviderOutcomeType.CONTRACT_ERROR, outcome.getType());
        assertEquals("ADS_CAMPAIGN_PAGE_CONTAINER_INVALID", outcome.getSanitizedCode());
        assertEquals(1, session.campaignPageCalls);
    }

    @Test
    void oversizedCampaignPayloadSkipsOnlyThatBusinessRow() {
        RecordingNoonAdvertisingGatewaySession session =
                new RecordingNoonAdvertisingGatewaySession();
        session.oversizedCampaignPayload = true;

        ProviderOutcome<AdvertisingCampaignPage> outcome = provider(session)
                .fetchCampaignPage(request(), new AdvertisingAdvertiser("ADV_108065"), 1);

        assertEquals(ProviderOutcomeType.SUCCESS, outcome.getType());
        assertEquals(2, outcome.getValue().getFacts().size());
        assertEquals(1, outcome.getValue().getBusinessSkippedItemCount());
        assertEquals(3, outcome.getValue().getSourceItemCount());
    }

    private RealNoonAdvertisingRuntimeProvider provider(
            RecordingNoonAdvertisingGatewaySession session
    ) {
        NoonPullStoreBinding binding = new NoonPullStoreBinding(
                307L, "PRJ108065", "STR108065-NSA", "SA", "108065",
                "seller@example.com", "cookie=value"
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
                6001L, OperationCode.DP06, "noon-admanager", 307L, 108065L,
                "PRJ108065", null, "PRJ108065", "STR108065-NSA", "SA",
                "scope-sa", LocalDateTime.of(2026, 8, 1, 22, 30),
                "DP06:date-range:2026-08-01..2026-08-01", "ADS_ADVERTISER",
                LocalDateTime.of(2026, 8, 1, 22, 0)
        );
        return AdvertisingPullRequest.from(task);
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
