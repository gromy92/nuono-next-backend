package com.nuono.next.competitoranalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.competitoranalysis.noon.NoonSearchResult;
import com.nuono.next.infrastructure.mapper.CompetitorProductSnapshotMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
@Service
public class CompetitorProductSnapshotService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CompetitorProductSnapshotMapper mapper;
    private final CompetitorListingObservationService observationService;

    @Autowired
    public CompetitorProductSnapshotService(
            CompetitorProductSnapshotMapper mapper,
            ObjectProvider<CompetitorListingObservationService> observationProvider
    ) {
        this.mapper = mapper;
        this.observationService = observationProvider == null
                ? null
                : observationProvider.getIfAvailable();
    }

    CompetitorProductSnapshotService(
            CompetitorProductSnapshotMapper mapper
    ) {
        this.mapper = mapper;
        this.observationService = null;
    }

    public int recordSearchSnapshots(
            CompetitorKeywordRefreshContext context,
            NoonSearchPage page,
            Map<String, NoonSearchResult> resultsByCode,
            Map<String, Long> competitorProductIdsByCode
    ) {
        int changedCount = 0;
        for (NoonSearchResult result : resultsByCode.values()) {
            if (observationService != null) {
                observationService.recordRankFound(context, page, result);
            }
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
            CompetitorListSnapshotValueSupport.mergeDailyLocalizedTitles(
                    snapshot,
                    daily
            );
            snapshot.setSnapshotHash(
                    CompetitorListSnapshotValueSupport.snapshotHash(snapshot)
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
        CompetitorListSnapshotValueSupport.mergeDailyLocalizedTitles(
                snapshot,
                daily
        );
        snapshot.setSnapshotHash(
                CompetitorListSnapshotValueSupport.snapshotHash(snapshot)
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
        LocalDateTime capturedAt = page.getCapturedAt() == null ? com.nuono.next.noon.NoonShanghaiBusinessTime.now() : page.getCapturedAt();
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
        command.setTitleEn(normalizeText(firstNonBlank(
                result.getTitleEn(),
                result.getTitle()
        )));
        command.setTitleAr(normalizeText(result.getTitleAr()));
        command.setBadgesJson(normalizeText(result.getTagsJson()));
        command.setPriceAmount(result.getPriceAmount());
        command.setCurrencyCode(normalizeText(result.getCurrencyCode()));
        command.setMainImageUrlRaw(normalizeText(result.getImageUrl()));
        command.setMainImageUrlNormalized(
                CompetitorListSnapshotValueSupport.normalizeImageUrl(
                        result.getImageUrl()
                )
        );
        command.setMainImageAssetKey(
                CompetitorListSnapshotValueSupport.extractAssetKey(
                        result.getImageUrl()
                )
        );
        command.setSnapshotHash(
                CompetitorListSnapshotValueSupport.snapshotHash(command)
        );
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
        if (watchProduct == null || detail == null) {
            return null;
        }
        String selfCode = normalizeCode(watchProduct.getSelfNoonProductCode());
        String productCode = product == null ? null : product.getNoonProductCode();
        String noonCode = normalizeCode(firstNonBlank(detail.getNoonProductCode(), productCode, selfCode));
        if (!StringUtils.hasText(noonCode)) {
            return null;
        }
        if (product == null && (!StringUtils.hasText(selfCode) || !selfCode.equals(noonCode))) {
            return null;
        }
        boolean selfSnapshot = product == null || noonCode.equals(selfCode);
        LocalDateTime capturedAt = detail.getCapturedAt() == null ? com.nuono.next.noon.NoonShanghaiBusinessTime.now() : detail.getCapturedAt();
        CompetitorProductSnapshotCommand command = new CompetitorProductSnapshotCommand();
        command.setOwnerUserId(watchProduct.getOwnerUserId());
        command.setWatchProductId(watchProduct.getId());
        command.setCompetitorProductId(selfSnapshot ? null : product.getId());
        command.setSubjectType(selfSnapshot ? "SELF" : "COMPETITOR");
        command.setSiteCode(normalizeText(watchProduct.getSiteCode()));
        command.setNoonProductCode(noonCode);
        command.setCodeType(NoonProductCodeSupport.codeType(noonCode).orElse(firstNonBlank(
                detail.getCodeType(),
                product == null ? null : product.getCodeType()
        )));
        command.setFactDate(capturedAt.toLocalDate());
        command.setCapturedAt(capturedAt);
        command.setSourceRunId(sourceRunId);
        command.setDetailUrl(normalizeText(detail.getDetailUrl()));
        command.setTitleEn(normalizeText(detail.getTitleEn()));
        command.setTitleAr(normalizeText(detail.getTitleAr()));
        command.setBadgesJson(normalizeText(firstNonBlank(
                detail.getBadgesJson(),
                detail.getLogisticsTagsJson()
        )));
        command.setPriceAmount(detail.getPriceAmount());
        command.setCurrencyCode(normalizeText(detail.getCurrencyCode()));
        command.setMainImageUrlRaw(normalizeText(detail.getMainImageUrlRaw()));
        command.setMainImageUrlNormalized(
                CompetitorListSnapshotValueSupport.normalizeImageUrl(
                        firstNonBlank(
                                detail.getMainImageUrlNormalized(),
                                detail.getMainImageUrlRaw()
                        )
                )
        );
        command.setMainImageAssetKey(normalizeText(firstNonBlank(
                detail.getMainImageAssetKey(),
                CompetitorListSnapshotValueSupport.extractAssetKey(
                        command.getMainImageUrlNormalized()
                )
        )));
        command.setSnapshotHash(
                CompetitorListSnapshotValueSupport.snapshotHash(command)
        );
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
        count += writeTextChange(actorUserId, snapshot, previous.getId(), "titleAr", "阿语标题", previous.getTitleAr(), snapshot.getTitleAr(), "INFO");
        count += writeTextChange(actorUserId, snapshot, previous.getId(), "tags", "标签", previous.getBadgesJson(), snapshot.getBadgesJson(), "INFO");
        count += writeDecimalChange(actorUserId, snapshot, previous.getId(), "price", "价格", previous.getPriceAmount(), snapshot.getPriceAmount(), "WARNING");
        count += writeTextChange(actorUserId, snapshot, previous.getId(), "currency", "币种", previous.getCurrencyCode(), snapshot.getCurrencyCode(), "INFO");
        String oldImage = CompetitorListSnapshotValueSupport.imageIdentity(
                previous.getMainImageAssetKey(), previous.getMainImageUrlNormalized()
        );
        String newImage = CompetitorListSnapshotValueSupport.imageIdentity(
                snapshot.getMainImageAssetKey(), snapshot.getMainImageUrlNormalized()
        );
        count += writeTextChange(actorUserId, snapshot, previous.getId(), "mainImage", "主图资产", oldImage, newImage, "INFO");
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
