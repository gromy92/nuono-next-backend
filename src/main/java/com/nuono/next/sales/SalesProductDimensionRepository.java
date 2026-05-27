package com.nuono.next.sales;

import java.util.List;

public interface SalesProductDimensionRepository {

    List<SalesProductDimensionSnapshot> list(SalesFactQuery query);
}
