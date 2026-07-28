package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class WarehousePackingConcurrencyMapperSqlTest {

    @Test
    void packingListWriteLookupUsesRowLock() throws Exception {
        Method method = WarehouseDispatchMapper.class.getMethod(
                "selectPackingListByIdForUpdate",
                Long.class
        );
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql).contains("FROM warehouse_packing_list");
        assertThat(sql).contains("id = #{packingListId}");
        assertThat(sql).contains("is_deleted = b'0'");
        assertThat(sql).endsWith("FOR UPDATE");
    }
}
