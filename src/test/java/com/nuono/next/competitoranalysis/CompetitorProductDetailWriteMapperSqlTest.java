package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.lang.reflect.Method;
import java.util.Locale;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class CompetitorProductDetailWriteMapperSqlTest {
    @Test
    void detailUpdateUsesCurrentWatchCodeAndConfirmedStateAsCompareAndSetGuard()
            throws Exception {
        Method method = CompetitorAnalysisMapper.class.getMethod(
                "updateCompetitorProductFromDetail",
                CompetitorProductInsertCommand.class
        );
        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .toUpperCase(Locale.ROOT);

        assertTrue(sql.contains("WHERE ID = #{ID}"));
        assertTrue(sql.contains("WATCH_PRODUCT_ID = #{WATCHPRODUCTID}"));
        assertTrue(sql.contains("UPPER(NOON_PRODUCT_CODE) = UPPER(#{NOONPRODUCTCODE})"));
        assertTrue(sql.contains("REVIEW_STATUS = 'CONFIRMED'"));
    }
}
