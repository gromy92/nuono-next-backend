package com.nuono.next.orderfinance;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.NoonFinanceTransactionMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class NoonFinanceTransactionMapperSqlTest {

    @Test
    void productDimensionCandidatesExposeOnlyCanonicalPartnerSku() throws Exception {
        for (String methodName : new String[] {
                "selectOverallSummary",
                "selectCurrencySummaryRows",
                "selectSkuSummaryRows"
        }) {
            Method method = NoonFinanceTransactionMapper.class.getMethod(methodName, OrderFinanceQuery.class);
            String sql = String.join(" ", method.getAnnotation(Select.class).value())
                    .replaceAll("\\s+", " ");

            assertThat(sql)
                    .contains("pv.partner_sku AS partnerSku")
                    .doesNotContain("pso.psku_code AS partnerSku")
                    .doesNotContain("pso.offer_code AS partnerSku");
        }
    }
}
