package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonProductDetailAdapter;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CompetitorProductDetailRefreshService {
    private static final Logger log = LoggerFactory.getLogger(CompetitorProductDetailRefreshService.class);

    private final CompetitorAnalysisMapper mapper;
    private final NoonProductDetailAdapter detailAdapter;
    private final CompetitorProductSnapshotService snapshotService;
    private final CompetitorProductDetailWriteGuard writeGuard;
    private final CompetitorProductDetailSupport detailSupport;

    @Autowired
    public CompetitorProductDetailRefreshService(
            CompetitorAnalysisMapper mapper,
            ObjectProvider<NoonProductDetailAdapter> detailAdapterProvider,
            ObjectProvider<CompetitorProductSnapshotService> snapshotServiceProvider,
            CompetitorProductDetailWriteGuard writeGuard
    ) {
        this(
                mapper,
                detailAdapterProvider == null ? null : detailAdapterProvider.getIfAvailable(),
                snapshotServiceProvider == null ? null : snapshotServiceProvider.getIfAvailable(),
                writeGuard,
                Clock.systemUTC()
        );
    }

    CompetitorProductDetailRefreshService(
            CompetitorAnalysisMapper mapper,
            NoonProductDetailAdapter detailAdapter,
            CompetitorProductSnapshotService snapshotService,
            Clock clock
    ) {
        this(
                mapper,
                detailAdapter,
                snapshotService,
                new CompetitorProductDetailWriteGuard(
                        mapper,
                        snapshotService,
                        CompetitorRefreshLeaseGuard.disabled(mapper)
                ),
                clock
        );
    }

    CompetitorProductDetailRefreshService(
            CompetitorAnalysisMapper mapper,
            NoonProductDetailAdapter detailAdapter,
            CompetitorProductSnapshotService snapshotService,
            CompetitorProductDetailWriteGuard writeGuard,
            Clock clock
    ) {
        this.mapper = mapper;
        this.detailAdapter = detailAdapter;
        this.snapshotService = snapshotService;
        this.writeGuard = writeGuard;
        this.detailSupport = new CompetitorProductDetailSupport(clock);
    }

    public int refreshConfirmedCompetitors(
            CompetitorWatchProductRow watchProduct,
            Long searchRunId,
            Long taskId,
            Long actorUserId
    ) {
        if (watchProduct == null || watchProduct.getId() == null || detailAdapter == null || snapshotService == null) {
            return 0;
        }
        String selfCode = detailSupport.normalizeCode(watchProduct.getSelfNoonProductCode());
        int refreshed = refreshSelfDetail(watchProduct, selfCode, searchRunId, taskId, actorUserId);
        List<CompetitorProductRow> confirmedProducts =
                mapper.listConfirmedCompetitorProductsByWatchProductId(watchProduct.getId());
        Map<String, CompetitorProductRow> productsByCode = new LinkedHashMap<>();
        for (CompetitorProductRow product : confirmedProducts) {
            String code = detailSupport.normalizeCode(product == null ? null : product.getNoonProductCode());
            if (!StringUtils.hasText(code) || code.equals(selfCode) || NoonProductCodeSupport.codeType(code).isEmpty()) {
                continue;
            }
            productsByCode.putIfAbsent(code, product);
        }

        for (Map.Entry<String, CompetitorProductRow> entry : productsByCode.entrySet()) {
            String code = entry.getKey();
            CompetitorProductRow product = entry.getValue();
            NoonProductDetail detail;
            try {
                detail = detailAdapter.fetch(detailSupport.buildRequest(watchProduct, product, code));
                if (detail == null) {
                    continue;
                }
                detailSupport.normalizeDetail(detail, code, product);
            } catch (CompetitorRefreshLeaseLostException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                log.warn(
                        "competitor product detail fetch failed watchProductId={} competitorProductId={} noonProductCode={} taskId={} error={}",
                        watchProduct.getId(),
                        product == null ? null : product.getId(),
                        code,
                        taskId,
                        exception.getMessage(),
                        exception
                );
                if (recordFallbackSnapshot(
                        watchProduct, product, code, searchRunId, actorUserId, taskId
                )) {
                    refreshed++;
                }
                continue;
            }
            try {
                writeDetail(
                        watchProduct,
                        product,
                        detail,
                        detailSupport.buildProductUpdate(product, detail, actorUserId),
                        searchRunId,
                        taskId,
                        actorUserId
                );
                refreshed++;
            } catch (CompetitorRefreshLeaseLostException exception) {
                throw exception;
            } catch (CompetitorDetailTargetStaleException exception) {
                log.warn(
                        "competitor detail target stale watchProductId={} competitorProductId={} noonProductCode={} taskId={} errorCode={}",
                        watchProduct.getId(),
                        product == null ? null : product.getId(),
                        code,
                        taskId,
                        CompetitorDetailTargetStaleException.ERROR_CODE
                );
            } catch (RuntimeException exception) {
                log.warn(
                        "competitor product detail write failed watchProductId={} competitorProductId={} noonProductCode={} taskId={} error={}",
                        watchProduct.getId(),
                        product == null ? null : product.getId(),
                        code,
                        taskId,
                        exception.getMessage(),
                        exception
                );
            }
        }
        return refreshed;
    }

    private int refreshSelfDetail(
            CompetitorWatchProductRow watchProduct,
            String selfCode,
            Long searchRunId,
            Long taskId,
            Long actorUserId
    ) {
        if (!StringUtils.hasText(selfCode) || NoonProductCodeSupport.codeType(selfCode).isEmpty()) {
            return 0;
        }
        try {
            NoonProductDetail detail = detailAdapter.fetch(detailSupport.buildRequest(watchProduct, null, selfCode));
            if (detail == null) {
                return 0;
            }
            detailSupport.normalizeDetail(detail, selfCode, null);
            writeDetail(
                    watchProduct,
                    null,
                    detail,
                    null,
                    searchRunId,
                    taskId,
                    actorUserId
            );
            return 1;
        } catch (CompetitorRefreshLeaseLostException exception) {
            throw exception;
        } catch (CompetitorDetailTargetStaleException exception) {
            log.warn(
                    "competitor self detail target stale watchProductId={} noonProductCode={} taskId={} errorCode={}",
                    watchProduct == null ? null : watchProduct.getId(),
                    selfCode,
                    taskId,
                    CompetitorDetailTargetStaleException.ERROR_CODE
            );
            return 0;
        } catch (RuntimeException exception) {
            log.warn(
                    "competitor self product detail refresh failed watchProductId={} noonProductCode={} taskId={} error={}",
                    watchProduct == null ? null : watchProduct.getId(),
                    selfCode,
                    taskId,
                    exception.getMessage(),
                    exception
            );
            return 0;
        }
    }

    private boolean recordFallbackSnapshot(
            CompetitorWatchProductRow watchProduct,
            CompetitorProductRow product,
            String code,
            Long searchRunId,
            Long actorUserId,
            Long taskId
    ) {
        NoonProductDetail fallback = detailSupport.buildFallbackDetail(product, code);
        if (fallback == null) {
            return false;
        }
        try {
            detailSupport.normalizeDetail(fallback, code, product);
            writeDetail(
                    watchProduct,
                    product,
                    fallback,
                    null,
                    searchRunId,
                    taskId,
                    actorUserId
            );
            return true;
        } catch (CompetitorRefreshLeaseLostException exception) {
            throw exception;
        } catch (CompetitorDetailTargetStaleException exception) {
            log.warn(
                    "competitor fallback detail target stale watchProductId={} competitorProductId={} noonProductCode={} taskId={} errorCode={}",
                    watchProduct == null ? null : watchProduct.getId(),
                    product == null ? null : product.getId(),
                    code,
                    taskId,
                    CompetitorDetailTargetStaleException.ERROR_CODE
            );
            return false;
        } catch (RuntimeException exception) {
            log.warn(
                    "competitor product detail fallback snapshot failed watchProductId={} competitorProductId={} noonProductCode={} taskId={} error={}",
                    watchProduct == null ? null : watchProduct.getId(),
                    product == null ? null : product.getId(),
                    code,
                    taskId,
                    exception.getMessage(),
                    exception
            );
            return false;
        }
    }

    private void writeDetail(
            CompetitorWatchProductRow watchProduct,
            CompetitorProductRow product,
            NoonProductDetail detail,
            CompetitorProductInsertCommand productUpdate,
            Long searchRunId,
            Long taskId,
            Long actorUserId
    ) {
        writeGuard.write(
                taskId,
                searchRunId,
                watchProduct,
                product,
                productUpdate,
                detail,
                actorUserId
        );
    }

}
