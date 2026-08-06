package com.nuono.next.noonpull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.advertising.AdvertisingAdvertiser;
import com.nuono.next.datapull.advertising.AdvertisingCampaignRef;
import com.nuono.next.datapull.advertising.AdvertisingDashboard;
import com.nuono.next.datapull.advertising.AdvertisingProvider;
import com.nuono.next.datapull.advertising.AdvertisingPullRequest;
import com.nuono.next.datapull.advertising.AdvertisingQueryReport;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.noonads.NoonAdvertisingReportDescriptor;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/** One externally visible Ad Manager request per DP-06 runtime advance. */
@Component
@ConditionalOnBean(NoonPullGatewaySessionFactory.class)
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public class RealNoonAdvertisingRuntimeProvider implements AdvertisingProvider {

    private static final String DEFAULT_BASE_URL = "https://admanager.noon.partners";

    private final ObjectMapper objectMapper;
    private final NoonPullStoreBindingResolver bindingResolver;
    private final NoonPullGatewaySessionFactory sessionFactory;
    private final NoonAdsAdvertiserContextResolver advertiserResolver;
    private final NoonAdvertisingDashboardParser dashboardParser;
    private final NoonAdvertisingQueryWorkbookParser queryParser;
    private final NoonAdvertisingOutcomeClassifier outcomeClassifier;
    private final String dashboardUrl;
    private final String queryReportUrl;

    public RealNoonAdvertisingRuntimeProvider(
            ObjectMapper objectMapper,
            NoonPullStoreBindingResolver bindingResolver,
            NoonPullGatewaySessionFactory sessionFactory,
            @Value("${nuono.noon.pull.real-provider.advertising-report.base-url:"
                    + DEFAULT_BASE_URL + "}") String baseUrl
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.bindingResolver = Objects.requireNonNull(bindingResolver, "bindingResolver");
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
        String root = NoonAdvertisingEndpoints.root(baseUrl, DEFAULT_BASE_URL);
        this.advertiserResolver = new NoonAdsAdvertiserContextResolver(
                objectMapper,
                root + "/_svc/productads/onboarding/advertiser/accounts"
        );
        this.dashboardParser = new NoonAdvertisingDashboardParser(objectMapper);
        this.queryParser = new NoonAdvertisingQueryWorkbookParser();
        this.outcomeClassifier = new NoonAdvertisingOutcomeClassifier();
        this.dashboardUrl = root + "/_svc/productads/v2/noon/metrics";
        this.queryReportUrl = root + "/_svc/productads/v2/noon/product/reports/queries";
    }

    @Override
    public ProviderOutcome<AdvertisingAdvertiser> resolveAdvertiser(AdvertisingPullRequest request) {
        try {
            BoundSession bound = boundSession(request);
            NoonAdsAdvertiserContext context = advertiserResolver.resolve(
                    bound.session,
                    bound.binding
            );
            return ProviderOutcome.success(
                    new AdvertisingAdvertiser(context.getAdvertiserCode())
            );
        } catch (RuntimeException failure) {
            return outcomeClassifier.classify(failure, "ADS_ADVERTISER_READ_FAILED");
        }
    }

    @Override
    public ProviderOutcome<AdvertisingDashboard> fetchDashboard(
            AdvertisingPullRequest request,
            AdvertisingAdvertiser advertiser
    ) {
        try {
            BoundSession bound = boundSession(request);
            NoonAdsAdvertiserContext context = advertiserContext(advertiser);
            return ProviderOutcome.success(dashboardParser.parse(bound.session.postJsonOnce(
                    dashboardUrl,
                    dashboardBody(request),
                    false,
                    advertiserResolver.headers(
                            bound.binding,
                            context,
                            "application/json, text/plain, */*"
                    )
            )));
        } catch (RuntimeException failure) {
            return outcomeClassifier.classify(failure, "ADS_DASHBOARD_READ_FAILED");
        }
    }

    @Override
    public ProviderOutcome<AdvertisingQueryReport> fetchCampaignQueries(
            AdvertisingPullRequest request,
            AdvertisingAdvertiser advertiser,
            AdvertisingCampaignRef campaign
    ) {
        try {
            BoundSession bound = boundSession(request);
            NoonAdsAdvertiserContext context = advertiserContext(advertiser);
            AdvertisingCampaignRef target = Objects.requireNonNull(campaign, "campaign");
            byte[] content = bound.session.postBytesOnce(
                    queryReportUrl,
                    queryReportBody(request, target.getCampaignCode()),
                    false,
                    advertiserResolver.headers(
                            bound.binding,
                            context,
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,*/*"
                    )
            );
            return ProviderOutcome.success(queryParser.parse(content, target));
        } catch (RuntimeException failure) {
            return outcomeClassifier.classify(failure, "ADS_QUERY_REPORT_READ_FAILED");
        }
    }

    private BoundSession boundSession(AdvertisingPullRequest request) {
        AdvertisingPullRequest value = Objects.requireNonNull(request, "request");
        NoonPullStoreBinding binding = bindingResolver.resolve(reportRequest(value));
        if (binding == null
                || !Objects.equals(binding.getOwnerUserId(), value.getOwnerUserId())
                || !sameIdentity(binding.getProjectCode(), value.getProjectCode())
                || !sameIdentity(binding.getStoreCode(), value.getStoreCode())
                || !sameIdentity(binding.getSiteCode(), value.getSiteCode())) {
            throw new NoonAdvertisingContractException("ADS_SCOPE_BINDING_MISMATCH");
        }
        return new BoundSession(binding, sessionFactory.openOneShot(binding));
    }

    private NoonReportPullRequest reportRequest(AdvertisingPullRequest request) {
        return NoonReportPullRequest.builder()
                .ownerUserId(request.getOwnerUserId())
                .storeCode(request.getStoreCode())
                .siteCode(request.getSiteCode())
                .dataDomain(NoonPullDataDomain.NOON_ADVERTISING)
                .reportType(NoonAdvertisingReportDescriptor.DEFAULT_REPORT_TYPE)
                .dateFrom(request.getReportDate())
                .dateTo(request.getReportDate())
                .build();
    }

    private NoonAdsAdvertiserContext advertiserContext(AdvertisingAdvertiser advertiser) {
        AdvertisingAdvertiser value = Objects.requireNonNull(advertiser, "advertiser");
        return new NoonAdsAdvertiserContext(value.getAdvertiserCode());
    }

    private ObjectNode dashboardBody(AdvertisingPullRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("startDate", request.getReportDate().toString());
        body.put("endDate", request.getReportDate().toString());
        body.set("campaignFilters", objectMapper.createObjectNode());
        body.put("isNamshi", false);
        return body;
    }

    private ObjectNode queryReportBody(AdvertisingPullRequest request, String campaignCode) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("campaignCode", campaignCode);
        body.put("campaignType", "product");
        body.put("startDate", request.getReportDate().toString());
        body.put("endDate", request.getReportDate().toString());
        return body;
    }

    private boolean sameIdentity(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }

    private static final class BoundSession {
        private final NoonPullStoreBinding binding;
        private final NoonPullGatewaySession session;

        private BoundSession(NoonPullStoreBinding binding, NoonPullGatewaySession session) {
            this.binding = binding;
            this.session = Objects.requireNonNull(session, "session");
        }
    }
}
