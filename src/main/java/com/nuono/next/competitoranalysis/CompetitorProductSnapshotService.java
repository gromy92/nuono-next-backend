package com.nuono.next.competitoranalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.competitoranalysis.noon.NoonSearchResult;
import com.nuono.next.infrastructure.mapper.CompetitorProductSnapshotMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CompetitorProductSnapshotService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CompetitorProductSnapshotMapper mapper;

    public CompetitorProductSnapshotService(CompetitorProductSnapshotMapper mapper) {
        this.mapper = mapper;
    }

    public int recordSearchSnapshots(
            CompetitorKeywordRefreshContext context,
            NoonSearchPage page,
            Map<String, NoonSearchResult> resultsByCode,
            Map<String, Long> competitorProductIdsByCode
    ) {
        int changedCount = 0;
        for (NoonSearchResult result : resultsByCode.values()) {
            CompetitorProductSnapshotCommand snapshot = buildSnapshot(
                    context,
                    page,
                    result,
                    competitorProductIdsByCode.get(normalizeCode(result.getNoonProductCode()))
            );
            if (snapshot == null) {
                continue;
            }
            CompetitorProductSnapshotRow daily = mapper.selectDailySnapshot(
                    snapshot.getWatchProductId(),
                    snapshot.getSubjectType(),
                    snapshot.getNoonProductCode(),
                    snapshot.getFactDate()
            );
            if (daily == null) {
                snapshot.setId(mapper.nextProductSnapshotId());
                mapper.insertProductSnapshot(snapshot);
            } else {
                snapshot.setId(daily.getId());
                mapper.updateProductSnapshot(snapshot);
            }
            mapper.softDeleteChangeEventsBySnapshotId(snapshot.getId(), context.getActorUserId());
            CompetitorProductSnapshotRow previous = mapper.selectPreviousSnapshot(
                    snapshot.getWatchProductId(),
                    snapshot.getSubjectType(),
                    snapshot.getNoonProductCode(),
                    snapshot.getFactDate()
            );
            changedCount += writeChangeEvents(context.getActorUserId(), snapshot, previous);
        }
        return changedCount;
    }

    public int recordProductDetailSnapshot(
            CompetitorWatchProductRow watchProduct,
            CompetitorProductRow product,
            NoonProductDetail detail,
            Long sourceRunId,
            Long actorUserId
    ) {
        CompetitorProductSnapshotCommand snapshot = buildDetailSnapshot(
                watchProduct,
                product,
                detail,
                sourceRunId,
                actorUserId
        );
        if (snapshot == null) {
            return 0;
        }
        CompetitorProductSnapshotRow daily = mapper.selectDailySnapshot(
                snapshot.getWatchProductId(),
                snapshot.getSubjectType(),
                snapshot.getNoonProductCode(),
                snapshot.getFactDate()
        );
        if (daily == null) {
            snapshot.setId(mapper.nextProductSnapshotId());
            mapper.insertProductSnapshot(snapshot);
        } else {
            snapshot.setId(daily.getId());
            mapper.updateProductSnapshot(snapshot);
        }
        mapper.softDeleteChangeEventsBySnapshotId(snapshot.getId(), actorUserId);
        CompetitorProductSnapshotRow previous = mapper.selectPreviousSnapshot(
                snapshot.getWatchProductId(),
                snapshot.getSubjectType(),
                snapshot.getNoonProductCode(),
                snapshot.getFactDate()
        );
        return writeChangeEvents(actorUserId, snapshot, previous);
    }

    private CompetitorProductSnapshotCommand buildSnapshot(
            CompetitorKeywordRefreshContext context,
            NoonSearchPage page,
            NoonSearchResult result,
            Long competitorProductId
    ) {
        String noonCode = normalizeCode(result.getNoonProductCode());
        if (!StringUtils.hasText(noonCode)) {
            return null;
        }
        LocalDateTime capturedAt = page.getCapturedAt() == null ? LocalDateTime.now() : page.getCapturedAt();
        CompetitorWatchProductRow watchProduct = context.getWatchProduct();
        CompetitorProductSnapshotCommand command = new CompetitorProductSnapshotCommand();
        command.setOwnerUserId(watchProduct.getOwnerUserId());
        command.setWatchProductId(watchProduct.getId());
        command.setCompetitorProductId(competitorProductId);
        command.setSubjectType(noonCode.equals(normalizeCode(watchProduct.getSelfNoonProductCode())) ? "SELF" : "COMPETITOR");
        command.setSiteCode(normalizeText(watchProduct.getSiteCode()));
        command.setNoonProductCode(noonCode);
        command.setCodeType(NoonProductCodeSupport.codeType(noonCode).orElse(result.getCodeType()));
        command.setFactDate(capturedAt.toLocalDate());
        command.setCapturedAt(capturedAt);
        command.setSourceRunId(context.getSearchRunId());
        command.setDetailUrl(normalizeText(result.getCanonicalUrl()));
        command.setTitleEn(normalizeText(result.getTitle()));
        command.setBrand(normalizeText(result.getBrand()));
        command.setPriceAmount(result.getPriceAmount());
        command.setCurrencyCode(normalizeText(result.getCurrencyCode()));
        command.setRating(result.getRating());
        command.setReviewCount(result.getReviewCount());
        command.setMainImageUrlRaw(normalizeText(result.getImageUrl()));
        command.setMainImageUrlNormalized(normalizeImageUrl(result.getImageUrl()));
        command.setMainImageAssetKey(extractAssetKey(result.getImageUrl()));
        command.setRawDetailJson(normalizeText(result.getRawResultJson()));
        command.setSnapshotHash(snapshotHash(command));
        command.setActorUserId(context.getActorUserId());
        return command;
    }

    private CompetitorProductSnapshotCommand buildDetailSnapshot(
            CompetitorWatchProductRow watchProduct,
            CompetitorProductRow product,
            NoonProductDetail detail,
            Long sourceRunId,
            Long actorUserId
    ) {
        if (watchProduct == null || product == null || detail == null) {
            return null;
        }
        String noonCode = normalizeCode(firstNonBlank(detail.getNoonProductCode(), product.getNoonProductCode()));
        if (!StringUtils.hasText(noonCode)) {
            return null;
        }
        LocalDateTime capturedAt = detail.getCapturedAt() == null ? LocalDateTime.now() : detail.getCapturedAt();
        CompetitorProductSnapshotCommand command = new CompetitorProductSnapshotCommand();
        command.setOwnerUserId(watchProduct.getOwnerUserId());
        command.setWatchProductId(watchProduct.getId());
        command.setCompetitorProductId(product.getId());
        command.setSubjectType("COMPETITOR");
        command.setSiteCode(normalizeText(watchProduct.getSiteCode()));
        command.setNoonProductCode(noonCode);
        command.setCodeType(NoonProductCodeSupport.codeType(noonCode).orElse(firstNonBlank(detail.getCodeType(), product.getCodeType())));
        command.setFactDate(capturedAt.toLocalDate());
        command.setCapturedAt(capturedAt);
        command.setSourceRunId(sourceRunId);
        command.setDetailUrl(normalizeText(detail.getDetailUrl()));
        command.setTitleEn(normalizeText(firstNonBlank(detail.getTitleEn(), detail.getTitleAr())));
        command.setBrand(normalizeText(detail.getBrand()));
        command.setPriceAmount(detail.getPriceAmount());
        command.setCurrencyCode(normalizeText(detail.getCurrencyCode()));
        command.setRating(detail.getRating());
        command.setReviewCount(detail.getReviewCount());
        command.setMainImageUrlRaw(normalizeText(detail.getMainImageUrlRaw()));
        command.setMainImageUrlNormalized(normalizeImageUrl(firstNonBlank(
                detail.getMainImageUrlNormalized(),
                detail.getMainImageUrlRaw()
        )));
        command.setMainImageAssetKey(normalizeText(firstNonBlank(
                detail.getMainImageAssetKey(),
                extractAssetKey(command.getMainImageUrlNormalized())
        )));
        command.setRawDetailJson(normalizeText(detail.getRawDetailJson()));
        command.setSnapshotHash(snapshotHash(command));
        command.setActorUserId(actorUserId);
        return command;
    }

    private int writeChangeEvents(
            Long actorUserId,
            CompetitorProductSnapshotCommand snapshot,
            CompetitorProductSnapshotRow previous
    ) {
        if (previous == null) {
            return 0;
        }
        int count = 0;
        count += writeTextChange(actorUserId, snapshot, previous.getId(), "title", "标题", previous.getTitleEn(), snapshot.getTitleEn(), "INFO");
        count += writeTextChange(actorUserId, snapshot, previous.getId(), "brand", "品牌", previous.getBrand(), snapshot.getBrand(), "INFO");
        count += writeDecimalChange(actorUserId, snapshot, previous.getId(), "price", "价格", previous.getPriceAmount(), snapshot.getPriceAmount(), "WARNING");
        count += writeTextChange(actorUserId, snapshot, previous.getId(), "currency", "币种", previous.getCurrencyCode(), snapshot.getCurrencyCode(), "INFO");
        count += writeDecimalChange(actorUserId, snapshot, previous.getId(), "rating", "评分", previous.getRating(), snapshot.getRating(), "INFO");
        count += writeIntegerChange(actorUserId, snapshot, previous.getId(), "reviewCount", "评论数", previous.getReviewCount(), snapshot.getReviewCount(), "INFO");
        count += writeTextChange(actorUserId, snapshot, previous.getId(), "mainImage", "主图资产", previous.getMainImageAssetKey(), snapshot.getMainImageAssetKey(), "INFO");
        return count;
    }

    private int writeTextChange(Long actorUserId, CompetitorProductSnapshotCommand snapshot, Long previousId, String key, String label, String oldValue, String newValue, String severity) {
        if (Objects.equals(normalizeText(oldValue), normalizeText(newValue))) {
            return 0;
        }
        writeChange(actorUserId, snapshot, previousId, key, label, oldValue, newValue, severity);
        return 1;
    }

    private int writeDecimalChange(Long actorUserId, CompetitorProductSnapshotCommand snapshot, Long previousId, String key, String label, BigDecimal oldValue, BigDecimal newValue, String severity) {
        if (compareDecimal(oldValue, newValue)) {
            return 0;
        }
        writeChange(actorUserId, snapshot, previousId, key, label, oldValue, newValue, severity);
        return 1;
    }

    private int writeIntegerChange(Long actorUserId, CompetitorProductSnapshotCommand snapshot, Long previousId, String key, String label, Integer oldValue, Integer newValue, String severity) {
        if (Objects.equals(oldValue, newValue)) {
            return 0;
        }
        writeChange(actorUserId, snapshot, previousId, key, label, oldValue, newValue, severity);
        return 1;
    }

    private void writeChange(Long actorUserId, CompetitorProductSnapshotCommand snapshot, Long previousId, String key, String label, Object oldValue, Object newValue, String severity) {
        CompetitorProductChangeEventCommand command = new CompetitorProductChangeEventCommand();
        command.setId(mapper.nextProductChangeEventId());
        command.setSnapshotId(snapshot.getId());
        command.setPreviousSnapshotId(previousId);
        command.setOwnerUserId(snapshot.getOwnerUserId());
        command.setWatchProductId(snapshot.getWatchProductId());
        command.setCompetitorProductId(snapshot.getCompetitorProductId());
        command.setSubjectType(snapshot.getSubjectType());
        command.setSiteCode(snapshot.getSiteCode());
        command.setNoonProductCode(snapshot.getNoonProductCode());
        command.setFactDate(snapshot.getFactDate());
        command.setFieldKey(key);
        command.setFieldLabel(label);
        command.setChangeType("VALUE_CHANGED");
        command.setOldValueJson(toJson(oldValue));
        command.setNewValueJson(toJson(newValue));
        command.setSeverity(severity);
        command.setActorUserId(actorUserId);
        mapper.insertProductChangeEvent(command);
    }

    private boolean compareDecimal(BigDecimal oldValue, BigDecimal newValue) {
        if (oldValue == null || newValue == null) {
            return oldValue == null && newValue == null;
        }
        return oldValue.compareTo(newValue) == 0;
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ignored) {
            return "null";
        }
    }

    private String snapshotHash(CompetitorProductSnapshotCommand command) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("title", command.getTitleEn());
        values.put("brand", command.getBrand());
        values.put("price", command.getPriceAmount());
        values.put("currency", command.getCurrencyCode());
        values.put("rating", command.getRating());
        values.put("reviewCount", command.getReviewCount());
        values.put("image", command.getMainImageAssetKey());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(OBJECT_MAPPER.writeValueAsString(values).getBytes(StandardCharsets.UTF_8));
            return toHex(bytes);
        } catch (Exception error) {
            throw new IllegalStateException("竞品商品快照 hash 计算失败", error);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return builder.toString();
    }

    private String extractAssetKey(String imageUrl) {
        String normalized = normalizeImageUrl(imageUrl);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private String normalizeImageUrl(String imageUrl) {
        String normalized = normalizeText(imageUrl);
        if (normalized == null) {
            return null;
        }
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        int hashIndex = normalized.indexOf('#');
        return hashIndex >= 0 ? normalized.substring(0, hashIndex) : normalized;
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
