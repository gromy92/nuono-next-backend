package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

final class CompetitorProductDetailTargetPlan {
    private CompetitorProductDetailTargetPlan() {
    }

    static List<Entry> initial(
            CompetitorAnalysisMapper mapper,
            CompetitorWatchProductRow watchProduct
    ) {
        List<Entry> targets = new ArrayList<>();
        String selfCode = normalizeCode(watchProduct.getSelfNoonProductCode());
        if (isRefreshableCode(selfCode)) {
            targets.add(new Entry(CompetitorProductDetailTarget.self(selfCode), null));
        }
        List<CompetitorProductRow> confirmedProducts =
                mapper.listConfirmedCompetitorProductsByWatchProductId(watchProduct.getId());
        Map<String, Entry> competitorsByCode = new LinkedHashMap<>();
        if (confirmedProducts == null) {
            confirmedProducts = Collections.emptyList();
        }
        for (CompetitorProductRow product : confirmedProducts) {
            String code = normalizeCode(product == null ? null : product.getNoonProductCode());
            if (!isRefreshableCode(code) || code.equals(selfCode)) {
                continue;
            }
            CompetitorProductDetailTarget target = CompetitorProductDetailTarget.competitor(
                    product.getId(),
                    code,
                    product.getCanonicalUrl()
            );
            competitorsByCode.putIfAbsent(target.identityKey(), new Entry(target, product));
        }
        targets.addAll(competitorsByCode.values());
        return targets;
    }

    static List<Entry> retry(
            CompetitorAnalysisMapper mapper,
            CompetitorWatchProductRow watchProduct,
            List<CompetitorProductDetailTarget> requestedTargets
    ) {
        if (requestedTargets == null) {
            return Collections.emptyList();
        }
        Map<String, Entry> selfTargets = new LinkedHashMap<>();
        Map<String, Entry> competitorTargets = new LinkedHashMap<>();
        String currentSelfCode = normalizeCode(watchProduct.getSelfNoonProductCode());
        Map<Long, CompetitorProductRow> currentCompetitors = confirmedProductsById(
                mapper.listConfirmedCompetitorProductsByWatchProductId(watchProduct.getId())
        );
        for (CompetitorProductDetailTarget requested : requestedTargets) {
            String code = normalizeCode(requested == null ? null : requested.getNoonProductCode());
            String subjectType = trim(requested == null ? null : requested.getSubjectType());
            if (CompetitorProductDetailTarget.SELF.equalsIgnoreCase(subjectType)) {
                if (isRefreshableCode(code) && code.equals(currentSelfCode)) {
                    CompetitorProductDetailTarget target = CompetitorProductDetailTarget.self(code);
                    selfTargets.putIfAbsent(target.identityKey(), new Entry(target, null));
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
            CompetitorProductDetailTarget target = target(current);
            competitorTargets.putIfAbsent(
                    target.identityKey(),
                    new Entry(target, current)
            );
        }
        List<Entry> targets = new ArrayList<>(selfTargets.values());
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
            Map<String, Entry> targets,
            CompetitorProductDetailTarget requested
    ) {
        CompetitorProductDetailTarget target = requested == null
                ? new CompetitorProductDetailTarget()
                : requested;
        targets.putIfAbsent(
                target.identityKey(),
                Entry.stale(target, "重试目标已不属于当前监控范围。")
        );
    }

    private static boolean isRefreshableCode(String code) {
        return StringUtils.hasText(code) && NoonProductCodeSupport.codeType(code).isPresent();
    }

    private static String normalizeCode(String value) {
        return NoonProductCodeSupport.normalize(value);
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    static final class Entry {
        final CompetitorProductDetailTarget target;
        final CompetitorProductRow product;
        final String terminalErrorCode;
        final String terminalErrorMessage;

        private Entry(
                CompetitorProductDetailTarget target,
                CompetitorProductRow product
        ) {
            this(target, product, null, null);
        }

        private Entry(
                CompetitorProductDetailTarget target,
                CompetitorProductRow product,
                String terminalErrorCode,
                String terminalErrorMessage
        ) {
            this.target = target;
            this.product = product;
            this.terminalErrorCode = terminalErrorCode;
            this.terminalErrorMessage = terminalErrorMessage;
        }

        private static Entry stale(
                CompetitorProductDetailTarget target,
                String message
        ) {
            return new Entry(target, null, "DETAIL_TARGET_STALE", message);
        }

        boolean isTerminalFailure() {
            return StringUtils.hasText(terminalErrorCode);
        }

        boolean recordTerminalFailure(CompetitorProductDetailRefreshResult result) {
            if (!isTerminalFailure()) {
                return false;
            }
            result.recordFailure(target, terminalErrorCode, terminalErrorMessage);
            return true;
        }
    }
}
