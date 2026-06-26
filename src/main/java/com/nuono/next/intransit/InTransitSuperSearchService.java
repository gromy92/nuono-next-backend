package com.nuono.next.intransit;

import com.nuono.next.infrastructure.mapper.InTransitSuperSearchMapper;
import com.nuono.next.intransit.InTransitSuperSearchCommands.InTransitSuperSearchQuery;
import com.nuono.next.intransit.InTransitSuperSearchRecords.SuperSearchItemRow;
import com.nuono.next.intransit.InTransitSuperSearchRecords.SuperSearchView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class InTransitSuperSearchService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final InTransitSuperSearchMapper mapper;

    public InTransitSuperSearchService(InTransitSuperSearchMapper mapper) {
        this.mapper = mapper;
    }

    public SuperSearchView search(InTransitSuperSearchQuery query) {
        InTransitSuperSearchQuery resolved = query == null ? new InTransitSuperSearchQuery() : query;
        requireOwnerUserId(resolved.getOwnerUserId());
        String keyword = clean(resolved.getKeyword());
        resolved.setKeyword(keyword);
        resolved.setProjectCode(clean(resolved.getProjectCode()));
        normalizeLimit(resolved);
        if (!StringUtils.hasText(keyword)) {
            return SuperSearchView.empty("", resolved.isIncludeHistory());
        }
        List<SuperSearchItemRow> lineRows = mapper.searchLineMatchedInTransitProducts(resolved);
        List<SuperSearchItemRow> titleRows = mapper.searchTitleMatchedInTransitProducts(resolved);
        List<SuperSearchItemRow> mergedRows = mergeRows(lineRows, titleRows, resolved.getLimit());
        if (!mergedRows.isEmpty()) {
            return SuperSearchView.from(keyword, resolved.isIncludeHistory(), mergedRows);
        }
        return SuperSearchView.from(
                keyword,
                resolved.isIncludeHistory(),
                mapper.searchInTransitProducts(resolved)
        );
    }

    private List<SuperSearchItemRow> mergeRows(
            List<SuperSearchItemRow> lineRows,
            List<SuperSearchItemRow> titleRows,
            int limit
    ) {
        Map<String, SuperSearchItemRow> uniqueRows = new LinkedHashMap<>();
        addRows(uniqueRows, lineRows);
        addRows(uniqueRows, titleRows);
        List<SuperSearchItemRow> mergedRows = new ArrayList<>(uniqueRows.values());
        mergedRows.sort(Comparator
                .comparing(SuperSearchItemRow::getSourceCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SuperSearchItemRow::getBatchId, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(row -> clean(row.getPsku())));
        return mergedRows.stream().limit(limit).collect(Collectors.toList());
    }

    private void addRows(Map<String, SuperSearchItemRow> target, List<SuperSearchItemRow> rows) {
        if (rows == null) {
            return;
        }
        rows.forEach(row -> target.putIfAbsent(rowKey(row), row));
    }

    private String rowKey(SuperSearchItemRow row) {
        return String.valueOf(row.getBatchId()) + "\u0000" + clean(row.getPsku());
    }

    private void normalizeLimit(InTransitSuperSearchQuery query) {
        Integer limit = query.getLimit();
        if (limit == null || limit <= 0) {
            query.setLimit(DEFAULT_LIMIT);
            return;
        }
        query.setLimit(Math.min(limit, MAX_LIMIT));
    }

    private Long requireOwnerUserId(Long ownerUserId) {
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("缺少业务 owner。");
        }
        return ownerUserId;
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
