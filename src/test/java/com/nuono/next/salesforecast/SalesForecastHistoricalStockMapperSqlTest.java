package com.nuono.next.salesforecast;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.SalesDataMapper;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class SalesForecastHistoricalStockMapperSqlTest {

    @Test
    void historicalStockReadsTheEffectiveInventoryProjection() throws Exception {
        Method method = SalesDataMapper.class.getMethod(
                "selectSalesForecastHistoricalStock",
                SalesForecastQuery.class,
                LocalDate.class,
                LocalDate.class,
                List.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("FROM official_warehouse_effective_inventory_snapshot_line inventory")
                .doesNotContain("FROM official_warehouse_inventory_snapshot_line");
    }
}
