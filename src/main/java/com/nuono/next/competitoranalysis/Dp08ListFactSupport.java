package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.dp08.Dp08ListTarget;
import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.infrastructure.mapper.CompetitorListingObservationMapper;
import com.nuono.next.infrastructure.mapper.Dp08RuntimeMapper;

/** Maps a verified DP08-B target into immutable snapshots and listing observations. */
final class Dp08ListFactSupport {
    private final Dp08RuntimeMapper runtimeMapper;
    private final CompetitorListingObservationMapper observationMapper;
    private final CompetitorProductSnapshotService snapshotService;
    private final CompetitorProductDetailSupport detailSupport;

    Dp08ListFactSupport(
            Dp08RuntimeMapper runtimeMapper,
            CompetitorListingObservationMapper observationMapper,
            CompetitorProductSnapshotService snapshotService,
            CompetitorProductDetailSupport detailSupport
    ) {
        this.runtimeMapper = runtimeMapper;
        this.observationMapper = observationMapper;
        this.snapshotService = snapshotService;
        this.detailSupport = detailSupport;
    }

    void recordFound(
            Dp08ListTarget target,
            NoonProductDetail detail,
            boolean recordObservation
    ) {
        detailSupport.normalizeDetail(detail, target.getNoonProductCode(), null);
        for (Dp08ListTarget.Reference reference : target.getReferences()) {
            snapshotService.recordProductDetailSnapshot(
                    boundWatch(target, reference),
                    boundProduct(target, reference),
                    detail,
                    null,
                    null
            );
        }
        if (recordObservation) {
            requireUpsert(runtimeMapper.upsertListFound(foundCommand(target, detail)));
        }
    }

    void recordNotFound(Dp08ListTarget target, NoonSearchPage evidence) {
        requireUpsert(runtimeMapper.upsertListNotFound(notFoundCommand(target, evidence)));
    }

    private CompetitorWatchProductRow boundWatch(
            Dp08ListTarget target,
            Dp08ListTarget.Reference reference
    ) {
        CompetitorWatchProductRow watch = new CompetitorWatchProductRow();
        watch.setId(reference.getWatchProductId());
        watch.setOwnerUserId(target.getOwnerUserId());
        watch.setLogicalStoreId(target.getLogicalStoreId());
        watch.setStoreCode(target.getStoreCode());
        watch.setSiteCode(target.getSiteCode());
        watch.setStatus("ACTIVE");
        if (reference.getCompetitorProductId() == null) {
            watch.setSelfNoonProductCode(target.getNoonProductCode());
            watch.setSelfCodeType(codeType(target.getNoonProductCode()));
        }
        return watch;
    }

    private CompetitorProductRow boundProduct(
            Dp08ListTarget target,
            Dp08ListTarget.Reference reference
    ) {
        if (reference.getCompetitorProductId() == null) {
            return null;
        }
        CompetitorProductRow product = new CompetitorProductRow();
        product.setId(reference.getCompetitorProductId());
        product.setWatchProductId(reference.getWatchProductId());
        product.setNoonProductCode(target.getNoonProductCode());
        product.setCodeType(codeType(target.getNoonProductCode()));
        product.setReviewStatus("CONFIRMED");
        return product;
    }

    private CompetitorListingObservationCommand foundCommand(
            Dp08ListTarget target,
            NoonProductDetail detail
    ) {
        CompetitorListingObservationCommand command = baseCommand(target);
        command.setCanonicalUrl(detail.getDetailUrl());
        command.setTitleEn(detail.getTitleEn());
        command.setTitleAr(detail.getTitleAr());
        command.setImageUrl(first(
                detail.getMainImageUrlNormalized(),
                detail.getMainImageUrlRaw()
        ));
        command.setPriceAmount(detail.getPriceAmount());
        command.setCurrencyCode(detail.getCurrencyCode());
        command.setTagsJson(first(detail.getBadgesJson(), detail.getLogisticsTagsJson()));
        command.setSourceUrl(detail.getProviderSourceUrl());
        command.setParserVersion(detail.getParserVersion());
        command.setProviderHttpStatus(detail.getProviderHttpStatus());
        command.setResponseHash(detail.getSnapshotHash());
        command.setCapturedAt(detail.getCapturedAt());
        return command;
    }

    private CompetitorListingObservationCommand notFoundCommand(
            Dp08ListTarget target,
            NoonSearchPage evidence
    ) {
        CompetitorListingObservationCommand command = baseCommand(target);
        command.setSourceUrl(evidence == null ? null : evidence.getSourceUrl());
        command.setProviderHttpStatus(
                evidence == null ? null : evidence.getProviderHttpStatus()
        );
        command.setResponseHash(evidence == null ? null : evidence.getResponseHash());
        command.setCapturedAt(evidence == null ? null : evidence.getCapturedAt());
        return command;
    }

    private CompetitorListingObservationCommand baseCommand(Dp08ListTarget target) {
        CompetitorListingObservationCommand command = new CompetitorListingObservationCommand();
        command.setId(observationMapper.nextListingObservationId());
        command.setOwnerUserId(target.getOwnerUserId());
        command.setStoreCode(target.getStoreCode());
        command.setSiteCode(target.getSiteCode());
        command.setNoonProductCode(target.getNoonProductCode());
        command.setCodeType(codeType(target.getNoonProductCode()));
        command.setFactDate(target.getFactDate());
        return command;
    }

    private String codeType(String noonProductCode) {
        return NoonProductCodeSupport.codeType(noonProductCode)
                .orElseThrow(() -> new IllegalStateException(
                        "invalid DP08-B Noon product code"
                ));
    }

    private String first(String first, String second) {
        return first == null || first.trim().isEmpty() ? second : first;
    }

    private void requireUpsert(int changed) {
        if (changed < 0 || changed > 2) {
            throw new IllegalStateException(
                    "DP08-B observation upsert changed an invalid row count"
            );
        }
    }
}
