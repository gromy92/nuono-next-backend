package com.nuono.next.productselection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class Ali1688ControlledPriceProbeExecutor implements Ali1688PriceProbeExecutor {

    private static final String SAFETY_MODE = "preview_only";
    private static final String SIDE_EFFECT_POLICY = "no_payment_no_order_no_message";
    private static final int MIN_MATCH_SCORE = 18;
    private static final int MIN_SPEC_SCORE = 10;
    private static final Set<String> TYPED_FAILURE_CODES = Set.of(
            "login_required",
            "captcha_required",
            "rate_limited",
            "sku_selection_required",
            "stock_unavailable",
            "shipping_unavailable",
            "preview_failed"
    );
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ObjectProvider<Ali1688PricePreviewClient> previewClientProvider;

    public Ali1688ControlledPriceProbeExecutor(ObjectProvider<Ali1688PricePreviewClient> previewClientProvider) {
        this.previewClientProvider = previewClientProvider;
    }

    @Override
    public Ali1688PriceProbeResult probe(Ali1688PriceProbeRequest request) {
        if (!isSafePreviewRequest(request)) {
            return failed("preview_failed", "系统价格探针请求无效。");
        }

        Ali1688PricePreviewClient previewClient = previewClientProvider.getIfAvailable();
        if (previewClient == null) {
            return failed("preview_failed", "真实价格预览客户端未启用。");
        }

        return normalize(previewClient.previewOrder(request));
    }

    private boolean isSafePreviewRequest(Ali1688PriceProbeRequest request) {
        if (request == null || !SAFETY_MODE.equals(request.safetyMode)) {
            return false;
        }
        if (request.task == null || request.candidate == null || request.task.id == null || request.candidate.id == null) {
            return false;
        }
        if (request.candidate.taskId == null || !request.task.id.equals(request.candidate.taskId)) {
            return false;
        }
        if (!"final".equals(request.candidate.scoreStatus)
                || request.candidate.matchScore == null
                || request.candidate.specScore == null) {
            return false;
        }
        String riskLevel = readRiskLevel(request.candidate.scoreDetailJson);
        return request.candidate.matchScore >= MIN_MATCH_SCORE
                && request.candidate.specScore >= MIN_SPEC_SCORE
                && !"high".equals(riskLevel)
                && !"medium".equals(riskLevel);
    }

    private Ali1688PriceProbeResult normalize(Ali1688PriceProbeResult result) {
        if (result == null) {
            return failed("preview_failed", "真实价格预览未返回结果。");
        }
        result.safetyMode = SAFETY_MODE;
        result.sideEffectPolicy = SIDE_EFFECT_POLICY;
        if (!"confirmed".equals(result.status)) {
            result.status = "failed";
            if (!TYPED_FAILURE_CODES.contains(result.failureCode)) {
                result.failureCode = "preview_failed";
            }
            if (!StringUtils.hasText(result.failureMessage)) {
                result.failureMessage = "真实价格预览失败。";
            }
        }
        if (containsCredentialMaterial(result.rawSnapshotJson)) {
            result.rawSnapshotJson = null;
        }
        return result;
    }

    private Ali1688PriceProbeResult failed(String failureCode, String failureMessage) {
        Ali1688PriceProbeResult result = Ali1688PriceProbeResult.failed(failureCode, failureMessage);
        result.safetyMode = SAFETY_MODE;
        result.sideEffectPolicy = SIDE_EFFECT_POLICY;
        return result;
    }

    private String readRiskLevel(String scoreDetailJson) {
        if (!StringUtils.hasText(scoreDetailJson)) {
            return "unknown";
        }
        try {
            JsonNode riskLevel = OBJECT_MAPPER.readTree(scoreDetailJson).get("riskLevel");
            return riskLevel == null || riskLevel.isNull() ? "unknown" : riskLevel.asText("unknown");
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private boolean containsCredentialMaterial(String rawSnapshotJson) {
        if (!StringUtils.hasText(rawSnapshotJson)) {
            return false;
        }
        String normalized = rawSnapshotJson.toLowerCase(Locale.ROOT);
        return normalized.contains("cookie")
                || normalized.contains("token")
                || normalized.contains("password")
                || normalized.contains("authorization")
                || normalized.contains("session");
    }
}
