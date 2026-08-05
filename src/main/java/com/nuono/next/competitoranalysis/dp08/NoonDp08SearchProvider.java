package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.competitoranalysis.noon.Dp08SearchPageContract;
import com.nuono.next.competitoranalysis.noon.NoonFrontendSearchAdapter;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.competitoranalysis.noon.NoonSearchProviderException;
import com.nuono.next.competitoranalysis.noon.NoonSearchRequest;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.RiskShareLevel;
import com.nuono.next.noon.NoonRequestPacingException;
import java.util.Locale;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Typed Adapter over the existing Noon customer-catalog v3 frontend channel. */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public final class NoonDp08SearchProvider implements Dp08SearchProvider {
    private static final int EXACT_SEARCH_LIMIT = 20;
    private final NoonFrontendSearchAdapter adapter;

    public NoonDp08SearchProvider(NoonFrontendSearchAdapter adapter) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
    }

    @Override
    public ProviderOutcome<NoonSearchPage> fetchRankPage(Dp08KeywordScope scope, int pageNo) {
        try {
            NoonSearchPage page = adapter.searchPage(NoonSearchRequest.builder()
                    .siteCode(scope.getSiteCode())
                    .locale(scope.getLocale())
                    .keyword(scope.getKeyword())
                    .limit(Dp08SearchPageContract.RANK_PAGE_SIZE)
                    .page(pageNo)
                    .build());
            return ProviderOutcome.success(
                    Dp08SearchPageContract.requireRankPage(page, pageNo)
            );
        } catch (NoonSearchProviderException failure) {
            return classify(failure);
        } catch (NoonRequestPacingException pacing) {
            return ProviderOutcome.transientFailure(
                    "DP08_LOCAL_PACING",
                    pacing.getRetryAfter()
            );
        } catch (RuntimeException unknown) {
            return ProviderOutcome.transientFailure("DP08_PROVIDER_UNKNOWN");
        }
    }

    @Override
    public ProviderOutcome<NoonSearchPage> searchExact(Dp08ListTarget target, String locale) {
        try {
            NoonSearchPage page = adapter.searchPage(NoonSearchRequest.builder()
                    .siteCode(target.getSiteCode())
                    .locale(locale)
                    .keyword(target.getNoonProductCode())
                    .limit(EXACT_SEARCH_LIMIT)
                    .build());
            if (page == null || page.getResults() == null) {
                return ProviderOutcome.contractError("DP08_LIST_PAGE_INVALID");
            }
            if (Dp08ListResultSupport.exact(page, target.getNoonProductCode()) == null
                    && !provesExactAbsence(page)) {
                return ProviderOutcome.contractError("DP08_LIST_NOT_FOUND_UNPROVEN");
            }
            return ProviderOutcome.success(page);
        } catch (NoonSearchProviderException failure) {
            return classify(failure);
        } catch (NoonRequestPacingException pacing) {
            return ProviderOutcome.transientFailure(
                    "DP08_LOCAL_PACING",
                    pacing.getRetryAfter()
            );
        } catch (RuntimeException unknown) {
            return ProviderOutcome.transientFailure("DP08_PROVIDER_UNKNOWN");
        }
    }

    private boolean provesExactAbsence(NoonSearchPage page) {
        int returned = page.getResults().size();
        if (page.getTotalHits() != null) {
            return page.getTotalHits() >= 0 && page.getTotalHits() <= returned;
        }
        return page.getTotalPages() != null
                && page.getTotalPages() >= 0
                && page.getTotalPages() <= 1;
    }

    private ProviderOutcome<NoonSearchPage> classify(NoonSearchProviderException failure) {
        String code = normalizeCode(failure.getErrorCode());
        if ("BLOCKED_BY_RISK_CONTROL".equals(code)
                || "RATE_LIMITED".equals(code)
                || "CAPTCHA_REQUIRED".equals(code)) {
            return ProviderOutcome.riskControl(
                    code,
                    failure.getRetryAfter(),
                    RiskShareLevel.EXACT
            );
        }
        if ("PROVIDER_UNAVAILABLE".equals(code) || "PARSE_FAILED".equals(code)) {
            return ProviderOutcome.transientFailure(code, failure.getRetryAfter());
        }
        if ("AUTH_REQUIRED".equals(code)) {
            return ProviderOutcome.authRequired(code);
        }
        return ProviderOutcome.transientFailure(
                code == null ? "DP08_PROVIDER_UNKNOWN" : code,
                failure.getRetryAfter()
        );
    }

    private static String normalizeCode(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9._:-]", "_");
    }
}
