package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonProductDetailAdapter;
import com.nuono.next.competitoranalysis.noon.NoonSearchProviderException;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.noon.NoonShanghaiBusinessTime;
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
        Clock sourceClock = clock == null ? Clock.systemUTC() : clock;
        this.detailSupport = new CompetitorProductDetailSupport(
                sourceClock.withZone(NoonShanghaiBusinessTime.ZONE)
        );
    }

    public CompetitorProductDetailRefreshResult refreshConfirmedCompetitors(
            CompetitorWatchProductRow watchProduct,
            Long searchRunId,
            Long taskId,
            Long actorUserId
    ) {
        if (watchProduct == null || watchProduct.getId() == null || detailAdapter == null || snapshotService == null) {
            return CompetitorProductDetailRefreshResult.unavailable(
                    "DETAIL_ADAPTER_UNAVAILABLE",
                    "竞品详情适配器或快照服务不可用。"
            );
        }
        CompetitorProductDetailRefreshResult result = CompetitorProductDetailRefreshResult.empty();
        String selfCode = detailSupport.normalizeCode(watchProduct.getSelfNoonProductCode());
        refreshSelfDetail(watchProduct, selfCode, searchRunId, taskId, actorUserId, result);
        if (result.hasRiskBackoffFailure()) {
            return result;
        }
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
            result.recordAttempt();
            try {
                detail = detailAdapter.fetch(detailSupport.buildRequest(watchProduct, product, code));
                if (detail == null) {
                    throw new IllegalStateException("Noon 前台商品详情未返回结果。");
                }
                detailSupport.normalizeDetail(detail, code, product);
            } catch (CompetitorRefreshLeaseLostException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                recordFailure(result, exception);
                log.warn(
                        "competitor product detail fetch failed watchProductId={} competitorProductId={} noonProductCode={} taskId={} error={}",
                        watchProduct.getId(),
                        product == null ? null : product.getId(),
                        code,
                        taskId,
                        exception.getMessage(),
                        exception
                );
                if (result.hasRiskBackoffFailure()) {
                    break;
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
                result.recordSuccess();
            } catch (CompetitorRefreshLeaseLostException exception) {
                throw exception;
            } catch (CompetitorDetailTargetStaleException exception) {
                result.recordFailure(
                        CompetitorDetailTargetStaleException.ERROR_CODE,
                        exception.getMessage()
                );
                log.warn(
                        "competitor detail target stale watchProductId={} competitorProductId={} noonProductCode={} taskId={} errorCode={}",
                        watchProduct.getId(),
                        product == null ? null : product.getId(),
                        code,
                        taskId,
                        CompetitorDetailTargetStaleException.ERROR_CODE
                );
            } catch (RuntimeException exception) {
                recordFailure(result, exception);
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
        return result;
    }

    private void refreshSelfDetail(
            CompetitorWatchProductRow watchProduct,
            String selfCode,
            Long searchRunId,
            Long taskId,
            Long actorUserId,
            CompetitorProductDetailRefreshResult result
    ) {
        if (!StringUtils.hasText(selfCode) || NoonProductCodeSupport.codeType(selfCode).isEmpty()) {
            return;
        }
        result.recordAttempt();
        try {
            NoonProductDetail detail = detailAdapter.fetch(detailSupport.buildRequest(watchProduct, null, selfCode));
            if (detail == null) {
                throw new IllegalStateException("Noon 前台商品详情未返回结果。");
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
            result.recordSuccess();
        } catch (CompetitorRefreshLeaseLostException exception) {
            throw exception;
        } catch (CompetitorDetailTargetStaleException exception) {
            result.recordFailure(
                    CompetitorDetailTargetStaleException.ERROR_CODE,
                    exception.getMessage()
            );
            log.warn(
                    "competitor self detail target stale watchProductId={} noonProductCode={} taskId={} errorCode={}",
                    watchProduct == null ? null : watchProduct.getId(),
                    selfCode,
                    taskId,
                    CompetitorDetailTargetStaleException.ERROR_CODE
            );
        } catch (RuntimeException exception) {
            recordFailure(result, exception);
            log.warn(
                    "competitor self product detail refresh failed watchProductId={} noonProductCode={} taskId={} error={}",
                    watchProduct == null ? null : watchProduct.getId(),
                    selfCode,
                    taskId,
                    exception.getMessage(),
                    exception
            );
        }
    }

    private void recordFailure(CompetitorProductDetailRefreshResult result, RuntimeException exception) {
        String errorCode = exception instanceof NoonSearchProviderException
                ? ((NoonSearchProviderException) exception).getErrorCode()
                : "DETAIL_REFRESH_FAILED";
        String errorMessage = StringUtils.hasText(exception.getMessage())
                ? exception.getMessage().trim()
                : "竞品详情抓取失败。";
        result.recordFailure(errorCode, errorMessage);
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
