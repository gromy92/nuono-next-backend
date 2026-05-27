package com.nuono.next.sales;

import java.util.List;

public interface SalesActivityWindowRepository {

    SalesActivityWindowRecord save(SalesActivityWindowRecord record);

    SalesActivityWindowRecord find(Long id);

    void setEnabled(Long id, boolean enabled, Long updatedBy);

    List<SalesActivityWindowRecord> listHistory(SalesActivityWindowScope scope);

    List<SalesActivityWindowRecord> listActive(SalesActivityWindowScope scope);
}
