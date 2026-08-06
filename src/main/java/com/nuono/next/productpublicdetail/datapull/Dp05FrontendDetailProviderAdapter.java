package com.nuono.next.productpublicdetail.datapull;

import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.RiskShareLevel;
import com.nuono.next.noon.NoonAuthenticationFailureClassifier;
import com.nuono.next.noon.NoonHttpException;
import com.nuono.next.noon.NoonRequestPacingException;
import com.nuono.next.noon.NoonTransientTransportFailurePolicy;
import com.nuono.next.productpublicdetail.ProductPublicDetailCandidate;
import com.nuono.next.productpublicdetail.ProductPublicDetailSyncStatus;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailAdapter;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailRequest;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailResult;
import java.util.Locale;
import java.util.Objects;
import org.springframework.util.StringUtils;

/** Strict typed bridge for the Noon consumer-frontend detail channel. */
public final class Dp05FrontendDetailProviderAdapter implements Dp05ProductDetailProvider {

    private final NoonPublicProductDetailAdapter delegate;

    public Dp05FrontendDetailProviderAdapter(NoonPublicProductDetailAdapter delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public ProviderOutcome<Dp05ProviderValue> fetch(Dp05FetchRequest request) {
        Dp05FetchRequest nonNull = Objects.requireNonNull(request, "request");
        ProductPublicDetailCandidate candidate = nonNull.getCandidate();
        String productCode = NoonProductCodeSupport.normalize(candidate.getNoonProductCode());
        if (!StringUtils.hasText(productCode)
                || NoonProductCodeSupport.codeType(productCode).isEmpty()) {
            return ProviderOutcome.contractError("DP05_FRONTEND_INVALID_PRODUCT_CODE");
        }
        NoonPublicProductDetailResult result;
        try {
            result = delegate.fetch(NoonPublicProductDetailRequest.builder()
                    .siteCode(nonNull.getScope().getSiteCode())
                    .locale(locale(nonNull.getScope().getSiteCode()))
                    .noonProductCode(productCode)
                    .build());
        } catch (RuntimeException failure) {
            return classifyThrown(failure);
        }
        return classifyResult(productCode, result);
    }

    private ProviderOutcome<Dp05ProviderValue> classifyResult(
            String requestedCode,
            NoonPublicProductDetailResult result
    ) {
        if (result == null || result.getStatus() == null) {
            return ProviderOutcome.contractError("DP05_FRONTEND_EMPTY_RESPONSE");
        }
        String returnedCode = NoonProductCodeSupport.normalize(result.getNoonProductCode());
        if (!requestedCode.equals(returnedCode)) {
            return ProviderOutcome.contractError("DP05_FRONTEND_IDENTITY_MISMATCH");
        }
        if (result.getStatus() == ProductPublicDetailSyncStatus.SUCCEEDED
                || result.getStatus() == ProductPublicDetailSyncStatus.PARTIAL) {
            return ProviderOutcome.success(Dp05ProviderValue.fact(result));
        }
        String failureCode = normalize(result.getFailureCode());
        String failureSignal = failureCode + " " + normalize(result.getFailureMessage());
        Integer status = result.getProviderHttpStatus();
        if (result.getStatus() == ProductPublicDetailSyncStatus.NOT_FOUND
                && "PUBLIC_DETAIL_NOT_FOUND".equals(failureCode)) {
            return ProviderOutcome.notFound("DP05_FRONTEND_NOT_FOUND");
        }
        if (isRisk(status, failureSignal)) {
            return ProviderOutcome.riskControl(
                    "DP05_FRONTEND_RISK_CONTROL",
                    result.getProviderRetryAfter(),
                    RiskShareLevel.EXACT
            );
        }
        if (isAuth(status, failureSignal)) {
            return ProviderOutcome.authRequired("DP05_FRONTEND_AUTH_REQUIRED");
        }
        if (isTransient(status, failureSignal)) {
            return ProviderOutcome.transientFailure(
                    "DP05_FRONTEND_TRANSIENT",
                    result.getProviderRetryAfter()
            );
        }
        return ProviderOutcome.contractError("DP05_FRONTEND_CONTRACT_ERROR");
    }

    private ProviderOutcome<Dp05ProviderValue> classifyThrown(RuntimeException failure) {
        NoonRequestPacingException pacing = findPacing(failure);
        if (pacing != null) {
            return ProviderOutcome.transientFailure(
                    "DP05_FRONTEND_LOCAL_PACING",
                    pacing.getRetryAfter()
            );
        }
        NoonHttpException http = findHttp(failure);
        if (http != null && isRisk(http.getStatusCode(), normalize(http.getResponseBody()))) {
            return ProviderOutcome.riskControl(
                    "DP05_FRONTEND_RISK_CONTROL",
                    http.getRetryAfter(),
                    RiskShareLevel.EXACT
            );
        }
        if (http != null && http.getStatusCode() == 401) {
            return ProviderOutcome.authRequired("DP05_FRONTEND_AUTH_REQUIRED");
        }
        if (NoonAuthenticationFailureClassifier.isAuthenticationFailure(failure)) {
            return ProviderOutcome.authRequired("DP05_FRONTEND_AUTH_REQUIRED");
        }
        if (isRisk(null, normalize(failure.getMessage()))) {
            return ProviderOutcome.riskControl("DP05_FRONTEND_RISK_CONTROL");
        }
        if (isAuth(null, normalize(failure.getMessage()))) {
            return ProviderOutcome.authRequired("DP05_FRONTEND_AUTH_REQUIRED");
        }
        if (NoonTransientTransportFailurePolicy.isRetryable(failure)) {
            return ProviderOutcome.transientFailure(
                    "DP05_FRONTEND_TRANSIENT",
                    http == null ? null : http.getRetryAfter()
            );
        }
        return ProviderOutcome.contractError("DP05_FRONTEND_UNCLASSIFIED_FAILURE");
    }

    private boolean isRisk(Integer status, String code) {
        return (status != null && (status == 403 || status == 418 || status == 429))
                || containsAny(code, "RATE_LIMITED", "BLOCKED_BY_RISK_CONTROL", "CAPTCHA", "IP_CHANNEL");
    }

    private boolean isAuth(Integer status, String code) {
        return (status != null && status == 401)
                || containsAny(code, "AUTH_REQUIRED", "INVALID_SESSION", "LOGIN_REQUIRED");
    }

    private boolean isTransient(Integer status, String code) {
        return (status != null && (status == 407 || status == 408 || status >= 500))
                || containsAny(code, "PROVIDER_UNAVAILABLE", "TIMEOUT", "NETWORK", "RESET", "EOF");
    }

    private NoonHttpException findHttp(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof NoonHttpException) {
                return (NoonHttpException) current;
            }
            current = current.getCause();
        }
        return null;
    }

    private NoonRequestPacingException findPacing(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof NoonRequestPacingException) {
                return (NoonRequestPacingException) current;
            }
            current = current.getCause();
        }
        return null;
    }

    private String locale(String siteCode) {
        String site = normalize(siteCode);
        if ("AE".equals(site) || "UAE".equals(site)) {
            return "en-AE";
        }
        if ("EG".equals(site) || "EGY".equals(site)) {
            return "en-EG";
        }
        return "en-SA";
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private boolean containsAny(String value, String... markers) {
        for (String marker : markers) {
            if (value.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
