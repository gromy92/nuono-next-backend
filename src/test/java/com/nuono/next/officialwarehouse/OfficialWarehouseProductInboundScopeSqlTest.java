package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseStatisticsMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class OfficialWarehouseProductInboundScopeSqlTest {

    @Test
    void stockSourceCandidatesRequireTheRequestedStoreWithinTheOwnerAndSite() throws Exception {
        Method method = OfficialWarehouseStatisticsMapper.class.getMethod(
                "listProductStockSourceCandidates",
                Long.class,
                String.class,
                String.class,
                Long.class,
                String.class,
                int.class
        );

        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("AND b.owner_user_id = s.owner_user_id")
                .contains("AND s.owner_user_id = #{ownerUserId}")
                .contains("AND s.source_store_code = #{storeCode}")
                .contains("AND UPPER(s.site_code) = UPPER(#{siteCode})")
                .doesNotContain("<if test='storeCode");
    }
}
