package com.nuono.next.competitoranalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

final class CompetitorListSnapshotValueSupport {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CompetitorListSnapshotValueSupport() {
    }

    static String snapshotHash(
            CompetitorProductSnapshotCommand command
    ) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("titleEn", command.getTitleEn());
        values.put("titleAr", command.getTitleAr());
        values.put("tags", command.getBadgesJson());
        values.put("price", command.getPriceAmount());
        values.put("currency", command.getCurrencyCode());
        values.put("image", imageIdentity(
                command.getMainImageAssetKey(),
                command.getMainImageUrlNormalized()
        ));
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                    OBJECT_MAPPER.writeValueAsString(values)
                            .getBytes(StandardCharsets.UTF_8)
            );
            StringBuilder output = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                output.append(String.format(
                        Locale.ROOT,
                        "%02x",
                        value & 0xff
                ));
            }
            return output.toString();
        } catch (Exception error) {
            throw new IllegalStateException(
                    "竞品商品快照 hash 计算失败",
                    error
            );
        }
    }

    static String extractAssetKey(String imageUrl) {
        String normalized = normalizeImageUrl(imageUrl);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    static String normalizeImageUrl(String imageUrl) {
        String normalized = normalizeText(imageUrl);
        if (normalized == null) {
            return null;
        }
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        int hashIndex = normalized.indexOf('#');
        return hashIndex >= 0
                ? normalized.substring(0, hashIndex)
                : normalized;
    }

    static String imageIdentity(String assetKey, String imageUrl) {
        return StringUtils.hasText(assetKey)
                ? assetKey.trim()
                : normalizeText(imageUrl);
    }

    static void mergeDailyLocalizedTitles(
            CompetitorProductSnapshotCommand snapshot,
            CompetitorProductSnapshotRow daily
    ) {
        if (snapshot == null || daily == null) {
            return;
        }
        if (!StringUtils.hasText(snapshot.getTitleEn())) {
            snapshot.setTitleEn(normalizeText(daily.getTitleEn()));
        }
        if (!StringUtils.hasText(snapshot.getTitleAr())) {
            snapshot.setTitleAr(normalizeText(daily.getTitleAr()));
        }
    }

    private static String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
