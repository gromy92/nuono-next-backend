package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.competitoranalysis.noon.NoonSearchProviderException;
import com.nuono.next.competitoranalysis.noon.NoonSearchResult;
import com.nuono.next.infrastructure.mapper.CompetitorListingObservationMapper;
import com.nuono.next.noon.NoonShanghaiBusinessTime;
import java.time.LocalDate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CompetitorListingObservationService {
    private static final String FOUND = "FOUND";
    private static final String NOT_FOUND = "NOT_FOUND";

    private final CompetitorListingObservationMapper mapper;
    private final CompetitorListingObservationCompletion completion;

    public CompetitorListingObservationService(
            CompetitorListingObservationMapper mapper
    ) {
        this.mapper = mapper;
        this.completion =
                new CompetitorListingObservationCompletion(mapper);
    }

    public void recordRankFound(
            CompetitorKeywordRefreshContext context,
            NoonSearchPage page,
            NoonSearchResult result
    ) {
        if (context == null
                || context.getWatchProduct() == null
                || result == null) {
            return;
        }
        String code = CompetitorListingObservationSupport.normalizeCode(
                result.getNoonProductCode()
        );
        if (!StringUtils.hasText(code)) {
            return;
        }
        mapper.upsertRankFound(
                CompetitorListingObservationSupport.rankCommand(
                        context,
                        page,
                        result,
                        mapper.nextListingObservationId()
                )
        );
    }

    public Lease acquireExact(
            CompetitorWatchProductRow watch,
            String noonProductCode,
            Long taskId,
            Long actorUserId
    ) {
        String code = CompetitorListingObservationSupport.normalizeCode(
                noonProductCode
        );
        LocalDate factDate = NoonShanghaiBusinessTime.now().toLocalDate();
        CompetitorListingObservationCommand command =
                CompetitorListingObservationSupport.baseCommand(
                watch,
                code,
                factDate,
                actorUserId
        );
        command.setId(mapper.nextListingObservationId());
        command.setLeaseToken(
                CompetitorListingObservationSupport.leaseToken(
                        taskId,
                        code
                )
        );
        try {
            if (mapper.insertExactClaim(command) == 1) {
                return Lease.acquired(
                        command.getId(),
                        command.getLeaseToken()
                );
            }
        } catch (DuplicateKeyException ignored) {
            // The daily observation already owns this natural key.
        }
        CompetitorListingObservationRow current = selectDaily(
                watch,
                code,
                factDate
        );
        Lease terminal = terminalLease(current);
        if (terminal != null) {
            return terminal;
        }
        if (current != null) {
            command.setId(current.getId());
            if (mapper.claimRetryableOrStale(command) == 1) {
                return Lease.acquired(
                        current.getId(),
                        command.getLeaseToken()
                );
            }
        }
        current = selectDaily(watch, code, factDate);
        terminal = terminalLease(current);
        if (terminal != null) {
            return terminal;
        }
        throw new NoonSearchProviderException(
                "LIST_OBSERVATION_IN_PROGRESS",
                "同一商品码的列表补拉正在执行，请稍后重试。",
                current == null ? null : current.getProviderHttpStatus(),
                current == null ? null : current.getSourceUrl(),
                current == null ? null : current.getResponseHash()
        );
    }

    public void completeFound(
            Lease lease,
            NoonProductDetail detail,
            Long actorUserId
    ) {
        if (lease == null || !lease.acquired || detail == null) {
            return;
        }
        completion.found(
                lease.observationId(),
                lease.leaseToken(),
                detail,
                actorUserId
        );
    }

    public void completeNotFound(
            Lease lease,
            NoonSearchProviderException error,
            Long actorUserId
    ) {
        if (lease != null && lease.acquired) {
            completion.failure(
                    lease.observationId(),
                    lease.leaseToken(),
                    error,
                    actorUserId,
                    true
            );
        }
    }

    public void completeRetryableFailure(
            Lease lease,
            RuntimeException error,
            Long actorUserId
    ) {
        if (lease != null && lease.acquired) {
            completion.failure(
                    lease.observationId(),
                    lease.leaseToken(),
                    error,
                    actorUserId,
                    false
            );
        }
    }

    private Lease terminalLease(CompetitorListingObservationRow row) {
        if (row == null) {
            return null;
        }
        if (FOUND.equalsIgnoreCase(row.getStatus())) {
            if ("EXACT_SEARCH".equalsIgnoreCase(row.getAcquisitionMode())
                    || hasCompleteTitles(row)) {
                return Lease.cached(
                        CompetitorListingObservationSupport.toDetail(row)
                );
            }
            return null;
        }
        if (NOT_FOUND.equalsIgnoreCase(row.getStatus())) {
            return Lease.notFound(new NoonSearchProviderException(
                    CompetitorListingObservationSupport.firstNonBlank(
                            row.getLastErrorCode(),
                            "LIST_PRODUCT_NOT_FOUND"
                    ),
                    CompetitorListingObservationSupport.firstNonBlank(
                            row.getLastErrorMessage(),
                            "Noon 前台列表没有返回完全匹配的商品码。"
                    ),
                    row.getProviderHttpStatus(),
                    row.getSourceUrl(),
                    row.getResponseHash()
            ));
        }
        return null;
    }

    private boolean hasCompleteTitles(CompetitorListingObservationRow row) {
        return StringUtils.hasText(row.getTitleEn())
                && StringUtils.hasText(row.getTitleAr());
    }

    private NoonProductDetail toDetail(CompetitorListingObservationRow row) {
        NoonProductDetail detail = new NoonProductDetail();
        detail.setNoonProductCode(row.getNoonProductCode());
        detail.setCodeType(row.getCodeType());
        detail.setDetailUrl(row.getCanonicalUrl());
        detail.setTitleEn(row.getTitleEn());
        detail.setTitleAr(row.getTitleAr());
        detail.setMainImageUrlRaw(row.getImageUrl());
        detail.setMainImageUrlNormalized(row.getImageUrl());
        detail.setPriceAmount(row.getPriceAmount());
        detail.setCurrencyCode(row.getCurrencyCode());
        detail.setBadgesJson(row.getTagsJson());
        detail.setProviderSourceUrl(row.getSourceUrl());
        detail.setParserVersion(row.getParserVersion());
        detail.setProviderHttpStatus(row.getProviderHttpStatus());
        detail.setSnapshotHash(row.getResponseHash());
        detail.setCapturedAt(row.getCapturedAt());
        detail.setAcquisitionMode(row.getAcquisitionMode());
        return detail;
    }

    private CompetitorListingObservationRow selectDaily(
            CompetitorWatchProductRow watch,
            String code,
            LocalDate factDate
    ) {
        return mapper.selectDaily(
                watch.getOwnerUserId(),
                CompetitorListingObservationSupport.normalizeUpper(
                        watch.getStoreCode()
                ),
                CompetitorListingObservationSupport.normalizeUpper(
                        watch.getSiteCode()
                ),
                code,
                factDate
        );
    }

    public static final class Lease {
        private final Long observationId;
        private final String leaseToken;
        private final boolean acquired;
        private final NoonProductDetail cachedDetail;
        private final NoonSearchProviderException notFound;

        private Lease(
                Long observationId,
                String leaseToken,
                boolean acquired,
                NoonProductDetail cachedDetail,
                NoonSearchProviderException notFound
        ) {
            this.observationId = observationId;
            this.leaseToken = leaseToken;
            this.acquired = acquired;
            this.cachedDetail = cachedDetail;
            this.notFound = notFound;
        }

        static Lease acquired(Long id, String token) {
            return new Lease(id, token, true, null, null);
        }

        static Lease cached(NoonProductDetail detail) {
            return new Lease(null, null, false, detail, null);
        }

        static Lease notFound(NoonSearchProviderException error) {
            return new Lease(null, null, false, null, error);
        }

        Long observationId() { return observationId; }
        String leaseToken() { return leaseToken; }
        public boolean isAcquired() { return acquired; }
        public NoonProductDetail getCachedDetail() { return cachedDetail; }
        public NoonSearchProviderException getNotFound() { return notFound; }
    }
}
