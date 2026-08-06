package com.nuono.next.productpublicdetail.datapull;

import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.orchestration.ExecutionContext;
import com.nuono.next.productpublicdetail.ProductPublicDetailCandidate;
import com.nuono.next.productpublicdetail.ProductPublicDetailSyncStatus;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailResult;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/** Deterministic local validation and fact metadata for the DP-05 state machine. */
final class Dp05JobSupport {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern WINDOW_DATE = Pattern.compile(".*:(\\d{4}-\\d{2}-\\d{2})$");

    private Dp05JobSupport() {
    }

    static boolean isPersistable(ProductPublicDetailSyncStatus status) {
        return status == ProductPublicDetailSyncStatus.SUCCEEDED
                || status == ProductPublicDetailSyncStatus.PARTIAL
                || status == ProductPublicDetailSyncStatus.NOT_FOUND;
    }

    static boolean hasExactProductIdentity(
            ProductPublicDetailCandidate candidate,
            NoonPublicProductDetailResult result
    ) {
        String expected = NoonProductCodeSupport.normalize(
                Objects.requireNonNull(candidate, "candidate").getNoonProductCode()
        );
        String actual = NoonProductCodeSupport.normalize(
                Objects.requireNonNull(result, "result").getNoonProductCode()
        );
        return StringUtils.hasText(expected) && expected.equals(actual);
    }

    static String candidateMismatch(
            DataPullScope scope,
            Dp05Checkpoint checkpoint,
            ProductPublicDetailCandidate candidate
    ) {
        Long offerId = candidate.getProductSiteOfferId();
        if (offerId == null || offerId <= checkpoint.getAfterOfferId()) {
            return "DP05_NON_MONOTONIC_CURSOR";
        }
        if (!Objects.equals(candidate.getOwnerUserId(), scope.getOwnerUserId())
                || !Objects.equals(candidate.getLogicalStoreId(), scope.getLogicalStoreId())
                || !same(candidate.getStoreCode(), scope.getStoreCode())
                || !same(candidate.getSiteCode(), scope.getSiteCode())) {
            return "DP05_CANDIDATE_SCOPE_MISMATCH";
        }
        return null;
    }

    static LocalDate factDate(ExecutionContext context) {
        String key = context.getTask().getBusinessWindowKey();
        Matcher matcher = WINDOW_DATE.matcher(key == null ? "" : key);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("DP05 business window date is missing");
        }
        return LocalDate.parse(matcher.group(1));
    }

    static NoonPublicProductDetailResult bothNotFound(
            ExecutionContext context,
            ProductPublicDetailCandidate candidate
    ) {
        NoonPublicProductDetailResult result = new NoonPublicProductDetailResult();
        result.setStatus(ProductPublicDetailSyncStatus.NOT_FOUND);
        result.setFailureCode("PUBLIC_AND_PARTNER_DETAIL_NOT_FOUND");
        result.setFailureMessage("Noon frontend and Partner exact search both returned NOT_FOUND.");
        result.setNoonProductCode(candidate.getNoonProductCode());
        result.setProviderHttpStatus(200);
        result.setProviderSourceUrl(NoonPartnerDp05DetailProvider.PROVIDER_URL);
        result.setProviderParserVersion("dp05-dual-not-found-v1");
        result.setFetchedAt(LocalDateTime.ofInstant(
                context.getNowUtc().toInstant(ZoneOffset.UTC),
                BUSINESS_ZONE
        ));
        return result;
    }

    private static boolean same(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }
}
