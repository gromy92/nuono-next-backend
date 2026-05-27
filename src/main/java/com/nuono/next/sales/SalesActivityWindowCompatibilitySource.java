package com.nuono.next.sales;

import java.util.List;

public interface SalesActivityWindowCompatibilitySource {

    List<SalesActivityWindowRecord> listActive(SalesActivityWindowScope scope);
}
