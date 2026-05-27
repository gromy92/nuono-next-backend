package com.nuono.next.sales;

import java.util.List;

public interface LegacySalesBackfillRowProvider {

    List<LegacySalesBackfillRow> fetch(LegacySalesBackfillCommand command);
}
