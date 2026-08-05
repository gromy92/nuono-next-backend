package com.nuono.next.productpublicdetail.noon;

import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.competitoranalysis.noon.NoonSearchProviderException;
import com.nuono.next.noon.NoonRetryAfterParser;
import com.nuono.next.noon.NoonShanghaiBusinessTime;
import com.nuono.next.productpublicdetail.ProductPublicDetailSyncStatus;
import java.time.Duration;
import org.springframework.util.StringUtils;

/** Maps frontend transport failures into DP-05 outcomes without changing provider channel. */
final class NoonPublicDetailFailureMapper {

    private NoonPublicDetailFailureMapper() {
    }

    static NoonPublicProductDetailResult fromProviderException(
            String productCode,
            NoonSearchProviderException exception
    ) {
        String failureCode = exception == null ? "PROVIDER_UNAVAILABLE" : exception.getErrorCode();
        Integer httpStatus = exception == null ? null : exception.getProviderHttpStatus();
        if (Integer.valueOf(429).equals(httpStatus)) {
            failureCode = "RATE_LIMITED";
        } else if (Integer.valueOf(403).equals(httpStatus)) {
            failureCode = "BLOCKED_BY_RISK_CONTROL";
        }
        NoonPublicProductDetailResult result = new NoonPublicProductDetailResult();
        result.setStatus(ProductPublicDetailSyncStatus.FAILED);
        result.setNoonProductCode(productCode);
        result.setCodeType(NoonProductCodeSupport.codeType(productCode).orElse(null));
        result.setFailureCode(failureCode);
        result.setFailureMessage(shrink(exception == null ? null : exception.getMessage(), 1000));
        result.setProviderHttpStatus(httpStatus);
        result.setProviderSourceUrl(exception == null ? null : exception.getSourceUrl());
        result.setProviderResponseHash(exception == null ? null : exception.getResponseHash());
        result.setProviderRetryAfter(exception == null ? null : exception.getRetryAfter());
        result.setFetchedAt(NoonShanghaiBusinessTime.now());
        return result;
    }

    static NoonSearchProviderException unsuccessfulStatus(
            int statusCode,
            String url,
            Duration retryAfter
    ) {
        if (statusCode == 429) {
            return failure(
                    "RATE_LIMITED",
                    "Noon 前台公开搜索返回 HTTP 429。",
                    statusCode,
                    url,
                    retryAfter
            );
        }
        if (statusCode == 403 || statusCode == 418) {
            return failure(
                    "BLOCKED_BY_RISK_CONTROL",
                    "Noon 前台公开搜索返回 HTTP " + statusCode + "。",
                    statusCode,
                    url,
                    retryAfter
            );
        }
        if (statusCode == 408 || statusCode >= 500) {
            return failure(
                    "PROVIDER_UNAVAILABLE",
                    "Noon 前台公开搜索返回 HTTP " + statusCode + "。",
                    statusCode,
                    url,
                    retryAfter
            );
        }
        return failure(
                "PARSE_FAILED",
                "Noon 前台公开搜索返回 HTTP " + statusCode + "。",
                statusCode,
                url,
                null
        );
    }

    static Duration retryAfter(String value) {
        return NoonRetryAfterParser.parse(value);
    }

    private static NoonSearchProviderException failure(
            String code,
            String message,
            int status,
            String url,
            Duration retryAfter
    ) {
        return new NoonSearchProviderException(
                code,
                message,
                status,
                url,
                null,
                retryAfter
        );
    }

    private static String shrink(String value, int maxLength) {
        String text = StringUtils.hasText(value) ? value.replaceAll("\\s+", " ").trim() : "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
