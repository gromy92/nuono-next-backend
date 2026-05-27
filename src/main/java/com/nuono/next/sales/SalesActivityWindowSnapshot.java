package com.nuono.next.sales;

import java.util.List;

public class SalesActivityWindowSnapshot {

    private final List<SalesActivityWindowRecord> windows;

    public SalesActivityWindowSnapshot(List<SalesActivityWindowRecord> windows) {
        this.windows = windows == null ? List.of() : List.copyOf(windows);
    }

    public List<SalesActivityWindowRecord> getWindows() {
        return windows;
    }
}
