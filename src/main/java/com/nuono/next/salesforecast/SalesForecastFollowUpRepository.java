package com.nuono.next.salesforecast;

import java.util.List;

public interface SalesForecastFollowUpRepository {

    SalesForecastFollowUpRecord setMarked(SalesForecastFollowUpCommand command);

    List<SalesForecastFollowUpRecord> listMarked(SalesForecastQuery query);
}
