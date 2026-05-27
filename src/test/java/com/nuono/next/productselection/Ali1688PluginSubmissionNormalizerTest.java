package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Ali1688PluginSubmissionNormalizerTest {

    private final Ali1688PluginSubmissionNormalizer normalizer =
            new Ali1688PluginSubmissionNormalizer(new ObjectMapper());

    @Test
    void normalizesDedupesLimitsAndSanitizesPluginSubmission() {
        Ali1688PluginSubmissionCommand command = new Ali1688PluginSubmissionCommand();
        command.setIdempotencyKey("submit-001");
        command.setSourcePageUrl("https://s.1688.com/image/search");
        command.setRawSnapshot(Map.of(
                "visibleCount", 12,
                "cookie", "secret-cookie",
                "nested", Map.of("authorization", "Bearer secret-token", "safe", "kept")
        ));
        List<Ali1688PluginSubmissionCommand.Candidate> candidates = new ArrayList<>();
        candidates.add(candidate(
                "745612345001",
                "https://helper.1688.com/redirect?offerId=745612345001&spm=a261y",
                "仿真花束 1",
                "¥12.80",
                new BigDecimal("12.80"),
                "2件起批",
                2,
                "浙江 义乌"
        ));
        candidates.add(candidate(
                "745612345001",
                "https://detail.1688.com/offer/745612345001.html?spm=dup",
                "重复候选",
                "¥12.90",
                new BigDecimal("12.90"),
                "2件起批",
                2,
                "浙江 义乌"
        ));
        candidates.add(candidate(
                null,
                "https://detail.1688.com/offer/745612345002.html?spm=from-url",
                "URL 提取 offer",
                null,
                null,
                null,
                null,
                null
        ));
        for (int index = 3; index <= 11; index++) {
            String offerId = String.format("745612345%03d", index);
            candidates.add(candidate(
                    offerId,
                    "https://detail.1688.com/offer/" + offerId + ".html",
                    "仿真花束 " + index,
                    "¥" + index,
                    BigDecimal.valueOf(index),
                    index + "件起批",
                    index,
                    "浙江 义乌"
            ));
        }
        command.setCandidates(candidates);

        Ali1688PluginSubmissionNormalizer.Result result = normalizer.normalize(command);

        assertEquals(12, result.getSubmittedCandidateCount());
        assertEquals(10, result.getAcceptedCandidateCount());
        assertEquals(2, result.getRejectedCandidateCount());
        assertEquals("745612345001", result.getCandidates().get(0).offerId);
        assertEquals("https://detail.1688.com/offer/745612345001.html", result.getCandidates().get(0).candidateUrl);
        assertEquals("745612345002", result.getCandidates().get(1).offerId);
        assertEquals("https://detail.1688.com/offer/745612345002.html", result.getCandidates().get(1).candidateUrl);
        assertNull(result.getCandidates().get(1).priceText);
        assertNull(result.getCandidates().get(1).moqText);
        assertNull(result.getCandidates().get(1).locationText);
        assertFalse(result.getAuditSnapshotJson().contains("secret-cookie"));
        assertFalse(result.getAuditSnapshotJson().contains("secret-token"));
        assertTrue(result.getAuditSnapshotJson().contains("\"safe\":\"kept\""));
        assertTrue(result.getAuditSnapshotJson().contains("\"acceptedCandidateCount\":10"));
    }

    private Ali1688PluginSubmissionCommand.Candidate candidate(
            String offerId,
            String candidateUrl,
            String title,
            String priceText,
            BigDecimal priceMin,
            String moqText,
            Integer moqValue,
            String locationText
    ) {
        Ali1688PluginSubmissionCommand.Candidate candidate = new Ali1688PluginSubmissionCommand.Candidate();
        candidate.setOfferId(offerId);
        candidate.setCandidateUrl(candidateUrl);
        candidate.setTitle(title);
        candidate.setSupplierName("义乌诚信通源头工厂");
        candidate.setPriceText(priceText);
        candidate.setPriceMin(priceMin);
        candidate.setMoqText(moqText);
        candidate.setMoqValue(moqValue);
        candidate.setLocationText(locationText);
        candidate.setMainImageUrl("https://images.example.com/" + (offerId == null ? "from-url" : offerId) + ".jpg");
        candidate.setImageUrls(List.of(candidate.getMainImageUrl()));
        candidate.setBadges(Map.of("stock", "现货"));
        candidate.setSupplierSnapshot(Map.of("factory", true));
        candidate.setLogisticsSnapshot(Map.of("shipFrom", "浙江义乌"));
        return candidate;
    }
}
