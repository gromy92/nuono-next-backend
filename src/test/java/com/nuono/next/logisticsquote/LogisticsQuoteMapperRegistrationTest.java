package com.nuono.next.logisticsquote;

import com.nuono.next.infrastructure.mapper.LogisticsQuoteMapper;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class LogisticsQuoteMapperRegistrationTest {

    @Test
    void mapperDynamicSqlRegistersSuccessfully() {
        Configuration configuration = new Configuration();
        configuration.addMapper(LogisticsQuoteMapper.class);
    }
}
