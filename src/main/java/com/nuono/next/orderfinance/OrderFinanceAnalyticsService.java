package com.nuono.next.orderfinance;

import com.nuono.next.infrastructure.mapper.NoonFinanceTransactionMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OrderFinanceAnalyticsService {
    private final NoonFinanceTransactionMapper mapper;

    public OrderFinanceAnalyticsService(NoonFinanceTransactionMapper mapper) {
        this.mapper = mapper;
    }

    public OrderFinanceSkuSummaryView skuSummary(OrderFinanceQuery query) {
        OrderFinanceSummaryView summary = mapper.selectOverallSummary(query);
        if (summary == null) {
            summary = new OrderFinanceSummaryView(null, false);
        }
        List<OrderFinanceSummaryView> summaries = mapper.selectCurrencySummaryRows(query);
        List<OrderFinanceSkuSummaryRow> rows = mapper.selectSkuSummaryRows(query);
        OrderFinanceDataStatus dataStatus = mapper.selectDataStatus(query);
        if (dataStatus == null) {
            dataStatus = new OrderFinanceDataStatus();
        }
        return new OrderFinanceSkuSummaryView(summary, summaries, rows, dataStatus);
    }

    public List<OrderFinanceOrderGroup> skuOrders(OrderFinanceQuery query) {
        List<OrderFinanceTransactionLine> lines = mapper.selectSkuOrderTransactionLines(query);
        Map<String, OrderFinanceOrderGroup> groups = new LinkedHashMap<>();
        for (OrderFinanceTransactionLine line : lines) {
            String orderNr = !StringUtils.hasText(line.getOrderNr()) ? "(missing)" : line.getOrderNr();
            OrderFinanceOrderGroup group = groups.computeIfAbsent(orderNr, OrderFinanceOrderGroup::new);
            group.addLine(line);
        }
        return new ArrayList<>(groups.values());
    }

    public boolean hasActiveStoreSite(Long ownerUserId, String storeCode, String siteCode) {
        if (ownerUserId == null
                || !StringUtils.hasText(storeCode)
                || !StringUtils.hasText(siteCode)) {
            return false;
        }
        return mapper.countActiveStoreSite(ownerUserId, storeCode.trim(), siteCode.trim()) > 0;
    }

    private OrderFinanceSummaryView summaryFor(List<OrderFinanceSummaryView> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return new OrderFinanceSummaryView(null, false);
        }
        if (summaries.size() == 1) {
            return summaries.get(0);
        }
        OrderFinanceSummaryView mixed = new OrderFinanceSummaryView("MULTI", true);
        for (OrderFinanceSummaryView summary : summaries) {
            mixed.addCounts(summary);
        }
        return mixed;
    }
}
