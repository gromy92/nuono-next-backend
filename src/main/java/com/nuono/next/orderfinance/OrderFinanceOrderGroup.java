package com.nuono.next.orderfinance;

import java.util.ArrayList;
import java.util.List;

public class OrderFinanceOrderGroup {
    private final String orderNr;
    private final List<OrderFinanceTransactionLine> lines = new ArrayList<>();

    public OrderFinanceOrderGroup(String orderNr) {
        this.orderNr = orderNr;
    }

    public void addLine(OrderFinanceTransactionLine line) {
        if (line != null) {
            lines.add(line);
        }
    }

    public String getOrderNr() { return orderNr; }
    public List<OrderFinanceTransactionLine> getLines() { return List.copyOf(lines); }
    public int getLineCount() { return lines.size(); }
    public long getOrderUpdateRowCount() {
        return lines.stream()
                .filter(line -> "order_update".equalsIgnoreCase(line.getTransactionType()))
                .count();
    }
}
