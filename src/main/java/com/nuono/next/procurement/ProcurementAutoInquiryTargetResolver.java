package com.nuono.next.procurement;

import com.nuono.next.procurement.ProcurementCandidatePoolView.CandidateView;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class ProcurementAutoInquiryTargetResolver {

    private static final Pattern OFFER_ID_PATH_PATTERN = Pattern.compile("/offer/(\\d+)\\.html", Pattern.CASE_INSENSITIVE);
    private static final Pattern OFFER_ID_QUERY_PATTERN = Pattern.compile("[?&](?:offerId|offer_id|id)=(\\d+)", Pattern.CASE_INSENSITIVE);

    public TargetResolution resolve(CandidateView candidate) {
        if (candidate == null) {
            return TargetResolution.failure("MISSING_CANDIDATE", "缺少候选上下文，暂时不能命中聊天目标。");
        }
        String platform = normalize(candidate.getCandidatePlatform());
        if (platform == null) {
            platform = ProcurementAutoInquiryLifecycle.PLATFORM_1688;
        }
        if (!ProcurementAutoInquiryLifecycle.PLATFORM_1688.equalsIgnoreCase(platform)) {
            return TargetResolution.failure("UNSUPPORTED_PLATFORM", "当前自动询价主链只支持 1688 候选。");
        }

        String candidateUrl = normalize(candidate.getCandidateUrl());
        String supplierIdentity = normalizeSupplier(candidate.getSupplierName());
        String offerId = extractOfferId(candidateUrl);
        if (candidateUrl == null) {
            return TargetResolution.failure("MISSING_TARGET_URL", "候选商品缺少详情链接，暂时不能命中聊天目标。");
        }
        if (supplierIdentity == null) {
            return TargetResolution.failure("MISSING_SUPPLIER", "候选商品缺少供应商主体，暂时不能校验聊天目标。");
        }
        if (offerId == null) {
            return TargetResolution.failure("MISSING_OFFER_ID", "当前详情链接里还没有稳定 offerId，暂时不能安全命中聊天线程。");
        }
        if (!looksLike1688Url(candidateUrl)) {
            return TargetResolution.failure("INVALID_1688_URL", "当前候选链接不是稳定的 1688 详情页地址，暂时不能继续自动询价。");
        }

        TargetResolution resolution = new TargetResolution();
        resolution.setResolved(true);
        resolution.setPlatform(ProcurementAutoInquiryLifecycle.PLATFORM_1688);
        resolution.setOfferId(offerId);
        resolution.setSupplierIdentity(supplierIdentity);
        resolution.setEntryUrl(candidateUrl);
        resolution.setLocatorText("offerId=" + offerId + "；supplier=" + supplierIdentity + "；entry=" + candidateUrl);
        return resolution;
    }

    private boolean looksLike1688Url(String candidateUrl) {
        try {
            URI uri = new URI(candidateUrl);
            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                return false;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            return normalizedHost.endsWith("1688.com");
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private String extractOfferId(String candidateUrl) {
        if (!StringUtils.hasText(candidateUrl)) {
            return null;
        }

        Matcher pathMatcher = OFFER_ID_PATH_PATTERN.matcher(candidateUrl);
        if (pathMatcher.find()) {
            return pathMatcher.group(1);
        }

        Matcher queryMatcher = OFFER_ID_QUERY_PATTERN.matcher(candidateUrl);
        if (queryMatcher.find()) {
            return queryMatcher.group(1);
        }
        return null;
    }

    private String normalizeSupplier(String supplierName) {
        String normalized = normalize(supplierName);
        if (normalized == null) {
            return null;
        }
        return normalized.replaceAll("\\s+", " ");
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    public static class TargetResolution {

        private boolean resolved;

        private String platform;

        private String offerId;

        private String supplierIdentity;

        private String entryUrl;

        private String locatorText;

        private String failureCode;

        private String failureMessage;

        public static TargetResolution failure(String failureCode, String failureMessage) {
            TargetResolution resolution = new TargetResolution();
            resolution.setResolved(false);
            resolution.setFailureCode(failureCode);
            resolution.setFailureMessage(failureMessage);
            return resolution;
        }

        public boolean isResolved() {
            return resolved;
        }

        public void setResolved(boolean resolved) {
            this.resolved = resolved;
        }

        public String getPlatform() {
            return platform;
        }

        public void setPlatform(String platform) {
            this.platform = platform;
        }

        public String getOfferId() {
            return offerId;
        }

        public void setOfferId(String offerId) {
            this.offerId = offerId;
        }

        public String getSupplierIdentity() {
            return supplierIdentity;
        }

        public void setSupplierIdentity(String supplierIdentity) {
            this.supplierIdentity = supplierIdentity;
        }

        public String getEntryUrl() {
            return entryUrl;
        }

        public void setEntryUrl(String entryUrl) {
            this.entryUrl = entryUrl;
        }

        public String getLocatorText() {
            return locatorText;
        }

        public void setLocatorText(String locatorText) {
            this.locatorText = locatorText;
        }

        public String getFailureCode() {
            return failureCode;
        }

        public void setFailureCode(String failureCode) {
            this.failureCode = failureCode;
        }

        public String getFailureMessage() {
            return failureMessage;
        }

        public void setFailureMessage(String failureMessage) {
            this.failureMessage = failureMessage;
        }
    }
}
