package com.nuono.next.sales;

public interface NoonSalesReportProvider {

    NoonSalesReportPayload fetch(NoonSalesReportRequest request);
}
