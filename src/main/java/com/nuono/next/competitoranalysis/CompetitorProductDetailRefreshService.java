package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonProductDetailAdapter;
import com.nuono.next.competitoranalysis.noon.NoonSearchProviderException;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.noon.NoonShanghaiBusinessTime;
import java.time.Clock;
import java.util.List;
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
        if (watchProduct == null || watchProduct.getId() == null) {
            return unavailable();
        }
        List<CompetitorProductDetailTargetPlan.Entry> targets =
                CompetitorProductDetailTargetPlan.initial(mapper, watchProduct);
        if (detailAdapter == null || snapshotService == null) {
            return unavailableTargets(targets);
        }
        return refreshTargetContexts(
                watchProduct,
                targets,
                searchRunId,
                taskId,
                actorUserId
        );
    }

    public CompetitorProductDetailRefreshResult refreshTargets(
            CompetitorWatchProductRow watchProduct,
            List<CompetitorProductDetailTarget> targets,
            Long searchRunId,
            Long taskId,
            Long actorUserId
    ) {
        if (watchProduct == null || watchProduct.getId() == null) {
            return unavailable();
        }
        List<CompetitorProductDetailTargetPlan.Entry> retryTargets =
                CompetitorProductDetailTargetPlan.retry(mapper, watchProduct, targets);
        if (detailAdapter == null || snapshotService == null) {
            return unavailableTargets(retryTargets);
        }
        return refreshTargetContexts(
                watchProduct,
                retryTargets,
                searchRunId,
                taskId,
                actorUserId
        );
    }

    private CompetitorProductDetailRefreshResult unavailable() {
        return CompetitorProductDetailRefreshResult.unavailable(
                "DETAIL_ADAPTER_UNAVAILABLE",
                "竞品详情适配器或快照服务不可用。"
        );
    }

    private CompetitorProductDetailRefreshResult unavailableTargets(
            List<CompetitorProductDetailTargetPlan.Entry> targets
    ) {
        if (targets == null || targets.isEmpty()) {
            return unavailable();
        }
        CompetitorProductDetailRefreshResult result =
                CompetitorProductDetailRefreshResult.empty();
        for (CompetitorProductDetailTargetPlan.Entry target : targets) {
            if (target.recordTerminalFailure(result)) {
                continue;
            }
            result.recordFailure(
                    target.target,
                    "DETAIL_ADAPTER_UNAVAILABLE",
                    "竞品详情适配器或快照服务不可用。"
            );
        }
        return result;
    }

    private CompetitorProductDetailRefreshResult refreshTargetContexts(
            CompetitorWatchProductRow watchProduct,
            List<CompetitorProductDetailTargetPlan.Entry> targets,
            Long searchRunId,
            Long taskId,
            Long actorUserId
    ) {
        CompetitorProductDetailRefreshResult result =
                CompetitorProductDetailRefreshResult.empty();
        for (int index = 0; index < targets.size(); index++) {
            CompetitorProductDetailTargetPlan.Entry context = targets.get(index);
            CompetitorProductDetailTarget target = context.target;
            CompetitorProductRow product = context.product;
            String code = target.getNoonProductCode();
            result.recordAttempt(target);
            if (context.recordTerminalFailure(result)) {
                continue;
            }

            NoonProductDetail detail;
            try {
                detail = detailAdapter.fetch(
                        detailSupport.buildRequest(watchProduct, product, code)
                );
                if (detail == null) {
                    throw new IllegalStateException(
                            "Noon 前台商品详情未返回结果。"
                    );
                }
                detailSupport.normalizeDetail(detail, code, product);
            } catch (CompetitorRefreshLeaseLostException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                recordFailure(result, target, exception);
                log.warn(
                        "competitor product detail fetch failed watchProductId={} subjectType={} competitorProductId={} noonProductCode={} taskId={} error={}",
                        watchProduct.getId(),
                        target.getSubjectType(),
                        product == null ? null : product.getId(),
                        code,
                        taskId,
                        exception.getMessage(),
                        exception
                );
                if (result.hasRiskBackoffFailure()) {
                    deferRemainingTargets(targets, index, result);
                    break;
                }
                continue;
            }

            try {
                writeGuard.write(
                        taskId,
                        searchRunId,
                        watchProduct,
                        product,
                        product == null
                                ? null
                                : detailSupport.buildProductUpdate(
                                        product,
                                        detail,
                                        actorUserId
                                ),
                        detail,
                        actorUserId
                );
                result.recordSuccess(target);
            } catch (CompetitorRefreshLeaseLostException exception) {
                throw exception;
            } catch (CompetitorDetailTargetStaleException exception) {
                result.recordFailure(
                        target,
                        CompetitorDetailTargetStaleException.ERROR_CODE,
                        exception.getMessage()
                );
                log.warn(
                        "competitor detail target stale watchProductId={} subjectType={} competitorProductId={} noonProductCode={} taskId={} errorCode={}",
                        watchProduct.getId(),
                        target.getSubjectType(),
                        product == null ? null : product.getId(),
                        code,
                        taskId,
                        CompetitorDetailTargetStaleException.ERROR_CODE
                );
            } catch (RuntimeException exception) {
                recordFailure(result, target, exception);
                log.warn(
                        "competitor product detail write failed watchProductId={} subjectType={} competitorProductId={} noonProductCode={} taskId={} error={}",
                        watchProduct.getId(),
                        target.getSubjectType(),
                        product == null ? null : product.getId(),
                        code,
                        taskId,
                        exception.getMessage(),
                        exception
                );
                if (result.hasRiskBackoffFailure()) {
                    deferRemainingTargets(targets, index, result);
                    break;
                }
            }
        }
        return result;
    }

    private void deferRemainingTargets(
            List<CompetitorProductDetailTargetPlan.Entry> targets,
            int failedIndex,
            CompetitorProductDetailRefreshResult result
    ) {
        for (int index = failedIndex + 1; index < targets.size(); index++) {
            CompetitorProductDetailTargetPlan.Entry target = targets.get(index);
            if (target.recordTerminalFailure(result)) {
                continue;
            }
            result.recordDeferred(
                    target.target,
                    result.getRiskErrorCode(),
                    result.getRiskErrorMessage()
            );
        }
    }

    private void recordFailure(
            CompetitorProductDetailRefreshResult result,
            CompetitorProductDetailTarget target,
            RuntimeException exception
    ) {
        String errorCode = exception instanceof NoonSearchProviderException
                ? ((NoonSearchProviderException) exception).getErrorCode()
                : "DETAIL_REFRESH_FAILED";
        String errorMessage = StringUtils.hasText(exception.getMessage())
                ? exception.getMessage().trim()
                : "竞品详情抓取失败。";
        result.recordFailure(target, errorCode, errorMessage);
    }

}
