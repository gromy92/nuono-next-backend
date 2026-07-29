package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.noon.NoonShanghaiBusinessTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

final class CompetitorProductDetailTargetPlan {
    private CompetitorProductDetailTargetPlan() {
    }

    static List<CompetitorProductDetailPlanEntry> initial(
            CompetitorAnalysisMapper mapper,
            CompetitorWatchProductRow watchProduct
    ) {
        return initial(mapper, watchProduct, false);
    }

    static List<CompetitorProductDetailPlanEntry> initial(
            CompetitorAnalysisMapper mapper,
            CompetitorWatchProductRow watchProduct,
            boolean onlyMissingFromCompleteTop200Scan
    ) {
        List<CompetitorProductDetailPlanEntry> targets = new ArrayList<>();
        LocalDate factDate = NoonShanghaiBusinessTime.now().toLocalDate();
        if (onlyMissingFromCompleteTop200Scan
                && !hasCompleteCoverage(mapper, watchProduct, factDate)) {
            return deferUntilRankCoverage(
                    initial(mapper, watchProduct, false)
            );
        }
        String selfCode = normalizeCode(watchProduct.getSelfNoonProductCode());
        if (isRefreshableCode(selfCode)
                && (!onlyMissingFromCompleteTop200Scan
                || needsExactSearch(
                        mapper, watchProduct.getId(), selfCode, factDate
                ))) {
            targets.add(new CompetitorProductDetailPlanEntry(
                    CompetitorProductDetailTarget.self(selfCode),
                    null
            ));
        }
        List<CompetitorProductRow> confirmedProducts =
                mapper.listConfirmedCompetitorProductsByWatchProductId(watchProduct.getId());
        Map<String, CompetitorProductDetailPlanEntry> competitorsByCode =
                new LinkedHashMap<>();
        if (confirmedProducts == null) {
            confirmedProducts = Collections.emptyList();
        }
        for (CompetitorProductRow product : confirmedProducts) {
            String code = normalizeCode(product == null ? null : product.getNoonProductCode());
            if (!isRefreshableCode(code)
                    || code.equals(selfCode)
                    || (onlyMissingFromCompleteTop200Scan
                    && !needsExactSearch(
                            mapper, watchProduct.getId(), code, factDate
                    ))) {
                continue;
            }
            CompetitorProductDetailTarget target = CompetitorProductDetailTarget.competitor(
                    product.getId(),
                    code,
                    product.getCanonicalUrl()
            );
            competitorsByCode.putIfAbsent(
                    target.identityKey(),
                    new CompetitorProductDetailPlanEntry(target, product)
            );
        }
        targets.addAll(competitorsByCode.values());
        return targets;
    }

    static List<CompetitorProductDetailPlanEntry> retry(
            CompetitorAnalysisMapper mapper,
            CompetitorWatchProductRow watchProduct,
            List<CompetitorProductDetailTarget> requestedTargets
    ) {
        return retry(mapper, watchProduct, requestedTargets, false);
    }

    static List<CompetitorProductDetailPlanEntry> retry(
            CompetitorAnalysisMapper mapper,
            CompetitorWatchProductRow watchProduct,
            List<CompetitorProductDetailTarget> requestedTargets,
            boolean onlyMissingFromCompleteTop200Scan
    ) {
        if (requestedTargets == null) {
            return Collections.emptyList();
        }
        LocalDate factDate = NoonShanghaiBusinessTime.now().toLocalDate();
        if (onlyMissingFromCompleteTop200Scan
                && !hasCompleteCoverage(mapper, watchProduct, factDate)) {
            return deferUntilRankCoverage(
                    retry(mapper, watchProduct, requestedTargets, false)
            );
        }
        Map<String, CompetitorProductDetailPlanEntry> selfTargets =
                new LinkedHashMap<>();
        Map<String, CompetitorProductDetailPlanEntry> competitorTargets =
                new LinkedHashMap<>();
        String currentSelfCode = normalizeCode(watchProduct.getSelfNoonProductCode());
        Map<Long, CompetitorProductRow> currentCompetitors = confirmedProductsById(
                mapper.listConfirmedCompetitorProductsByWatchProductId(watchProduct.getId())
        );
        for (CompetitorProductDetailTarget requested : requestedTargets) {
            String code = normalizeCode(requested == null ? null : requested.getNoonProductCode());
            String subjectType = trim(requested == null ? null : requested.getSubjectType());
            if (CompetitorProductDetailTarget.SELF.equalsIgnoreCase(subjectType)) {
                if (isRefreshableCode(code)
                        && code.equals(currentSelfCode)) {
                    if (!onlyMissingFromCompleteTop200Scan
                            || needsExactSearch(
                            mapper, watchProduct.getId(), code, factDate
                    )) {
                        CompetitorProductDetailTarget target =
                                CompetitorProductDetailTarget.self(code);
                        selfTargets.putIfAbsent(
                                target.identityKey(),
                                new CompetitorProductDetailPlanEntry(
                                        target,
                                        null
                                )
                        );
                    } else {
                        CompetitorProductDetailTarget target =
                                CompetitorProductDetailTarget.self(code);
                        selfTargets.putIfAbsent(
                                target.identityKey(),
                                CompetitorProductDetailPlanEntry.covered(
                                        target,
                                        null
                                )
                        );
                    }
                } else {
                    putStale(selfTargets, requested);
                }
                continue;
            }
            if (!CompetitorProductDetailTarget.COMPETITOR.equalsIgnoreCase(subjectType)
                    || requested.getCompetitorProductId() == null
                    || !isRefreshableCode(code)
                    || code.equals(currentSelfCode)) {
                putStale(competitorTargets, requested);
                continue;
            }
            CompetitorProductRow current = currentCompetitors.get(requested.getCompetitorProductId());
            if (current == null || !code.equals(normalizeCode(current.getNoonProductCode()))) {
                putStale(competitorTargets, requested);
                continue;
            }
            if (onlyMissingFromCompleteTop200Scan
                    && !needsExactSearch(
                    mapper, watchProduct.getId(), code, factDate
            )) {
                CompetitorProductDetailTarget target = target(current);
                competitorTargets.putIfAbsent(
                        target.identityKey(),
                        CompetitorProductDetailPlanEntry.covered(
                                target,
                                current
                        )
                );
                continue;
            }
            CompetitorProductDetailTarget target = target(current);
            competitorTargets.putIfAbsent(
                    target.identityKey(),
                    new CompetitorProductDetailPlanEntry(target, current)
            );
        }
        List<CompetitorProductDetailPlanEntry> targets =
                new ArrayList<>(selfTargets.values());
        targets.addAll(competitorTargets.values());
        return targets;
    }

    private static Map<Long, CompetitorProductRow> confirmedProductsById(
            List<CompetitorProductRow> products
    ) {
        Map<Long, CompetitorProductRow> result = new LinkedHashMap<>();
        if (products != null) {
            for (CompetitorProductRow product : products) {
                if (product != null && product.getId() != null) {
                    result.putIfAbsent(product.getId(), product);
                }
            }
        }
        return result;
    }

    private static CompetitorProductDetailTarget target(CompetitorProductRow product) {
        return CompetitorProductDetailTarget.competitor(
                product.getId(),
                normalizeCode(product.getNoonProductCode()),
                product.getCanonicalUrl()
        );
    }

    private static void putStale(
            Map<String, CompetitorProductDetailPlanEntry> targets,
            CompetitorProductDetailTarget requested
    ) {
        CompetitorProductDetailTarget target = requested == null
                ? new CompetitorProductDetailTarget()
                : requested;
        targets.putIfAbsent(
                target.identityKey(),
                CompetitorProductDetailPlanEntry.stale(
                        target,
                        "重试目标已不属于当前监控范围。"
                )
        );
    }

    private static boolean isRefreshableCode(String code) {
        return StringUtils.hasText(code) && NoonProductCodeSupport.codeType(code).isPresent();
    }

    static boolean hasCompleteCoverage(
            CompetitorAnalysisMapper mapper,
            CompetitorWatchProductRow watchProduct,
            LocalDate factDate
    ) {
        return mapper != null
                && watchProduct != null
                && watchProduct.getId() != null
                && mapper.hasCompleteRankScanCoverage(
                        watchProduct.getId(), factDate
                );
    }

    private static List<CompetitorProductDetailPlanEntry>
            deferUntilRankCoverage(
            List<CompetitorProductDetailPlanEntry> targets
    ) {
        List<CompetitorProductDetailPlanEntry> deferred =
                new ArrayList<>();
        for (CompetitorProductDetailPlanEntry entry : targets) {
            if (entry == null || entry.isTerminalFailure()) {
                deferred.add(entry);
            } else {
                deferred.add(CompetitorProductDetailPlanEntry.deferred(
                        entry.target,
                        entry.product,
                        "RANK_COVERAGE_INCOMPLETE",
                        "当天前 200 排名覆盖尚未完整，列表补拉等待排名补偿。"
                ));
            }
        }
        return deferred;
    }

    private static boolean needsExactSearch(
            CompetitorAnalysisMapper mapper,
            Long watchProductId,
            String code,
            LocalDate factDate
    ) {
        return !mapper.hasRankedFactInTop200(
                watchProductId, code, factDate
        ) || !mapper.hasCompleteListTitlesToday(
                watchProductId, code, factDate
        );
    }

    private static String normalizeCode(String value) {
        return NoonProductCodeSupport.normalize(value);
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

}
