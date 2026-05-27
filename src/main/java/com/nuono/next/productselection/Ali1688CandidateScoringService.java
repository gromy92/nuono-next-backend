package com.nuono.next.productselection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class Ali1688CandidateScoringService {

    public static final String SCORE_VERSION = "ALI1688_SCORE_V1_RULE_AI_ASYNC";

    private final ObjectMapper objectMapper;

    public Ali1688CandidateScoringService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void score(Ali1688CollectionRecords.CandidateRecord candidate) {
        candidate.priceScore = scorePrice(candidate);
        candidate.moqScore = scoreMoq(candidate);
        candidate.supplierScore = scoreSupplier(candidate);
        candidate.deliveryScore = scoreDelivery(candidate);
        candidate.ruleScore = candidate.priceScore + candidate.moqScore + candidate.supplierScore + candidate.deliveryScore;
        candidate.totalScore = null;
        candidate.scoreStatus = "partial";
        candidate.scoreVersion = SCORE_VERSION;
        candidate.scoreDetailJson = scoreDetailJson(candidate);
    }

    int scorePrice(Ali1688CollectionRecords.CandidateRecord candidate) {
        BigDecimal price = candidate.priceMin != null ? candidate.priceMin : candidate.priceMax;
        if (price == null) {
            return 0;
        }
        if (price.compareTo(BigDecimal.ZERO) <= 0 || price.compareTo(BigDecimal.valueOf(100000)) > 0) {
            return 2;
        }
        if (candidate.priceMin != null && candidate.priceMax != null
                && candidate.priceMax.compareTo(candidate.priceMin.multiply(BigDecimal.valueOf(5))) > 0) {
            return 8;
        }
        if (price.compareTo(BigDecimal.valueOf(50)) <= 0) {
            return 15;
        }
        if (price.compareTo(BigDecimal.valueOf(150)) <= 0) {
            return 12;
        }
        return 9;
    }

    int scoreMoq(Ali1688CollectionRecords.CandidateRecord candidate) {
        Integer moq = candidate.moqValue;
        if (moq == null || moq <= 0) {
            return 0;
        }
        if (moq <= 5) {
            return 10;
        }
        if (moq <= 20) {
            return 8;
        }
        if (moq <= 100) {
            return 5;
        }
        return 2;
    }

    int scoreSupplier(Ali1688CollectionRecords.CandidateRecord candidate) {
        String haystack = defaultText(candidate.supplierName, "") + " "
                + defaultText(candidate.badgesJson, "") + " "
                + defaultText(candidate.supplierSnapshotJson, "");
        if (!StringUtils.hasText(haystack)) {
            return 0;
        }
        int score = 6;
        if (haystack.contains("诚信通") || haystack.toLowerCase().contains("verified")) {
            score += 2;
        }
        if (haystack.contains("工厂") || haystack.contains("源头") || haystack.toLowerCase().contains("factory")) {
            score += 2;
        }
        if (haystack.contains("响应") || haystack.toLowerCase().contains("response")) {
            score += 1;
        }
        if (haystack.contains("认证") || haystack.toLowerCase().contains("certified")) {
            score += 1;
        }
        return Math.min(score, 12);
    }

    int scoreDelivery(Ali1688CollectionRecords.CandidateRecord candidate) {
        String haystack = defaultText(candidate.locationText, "") + " "
                + defaultText(candidate.logisticsSnapshotJson, "") + " "
                + defaultText(candidate.badgesJson, "");
        if (!StringUtils.hasText(haystack)) {
            return 0;
        }
        int score = 4;
        if (haystack.contains("现货") || haystack.toLowerCase().contains("stock")) {
            score += 2;
        }
        if (haystack.contains("48") || haystack.contains("24") || haystack.contains("发货")) {
            score += 1;
        }
        if (haystack.contains("广东") || haystack.contains("浙江") || haystack.contains("义乌") || haystack.contains("深圳")) {
            score += 1;
        }
        return Math.min(score, 8);
    }

    private String scoreDetailJson(Ali1688CollectionRecords.CandidateRecord candidate) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("version", SCORE_VERSION);
        detail.put("ruleScoreMax", 45);
        detail.put("priceScore", candidate.priceScore);
        detail.put("priceBasis", "list_price_hint");
        detail.put("confirmedRealPrice", false);
        detail.put("moqScore", candidate.moqScore);
        detail.put("supplierScore", candidate.supplierScore);
        detail.put("deliveryScore", candidate.deliveryScore);
        detail.put("aiPending", true);
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
