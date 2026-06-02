package com.nuono.next.store;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class StoreSyncMapperCookieSqlTest {

    @Test
    void ownerSessionCookieUpdateShouldBeScopedToProjectCode() throws Exception {
        Method method = StoreSyncMapper.class.getDeclaredMethod(
                "updateOwnerSessionCookie",
                Long.class,
                String.class,
                String.class,
                Long.class
        );
        Update update = method.getAnnotation(Update.class);
        String sql = String.join(" ", update.value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("WHERE user_id = #{ownerUserId}"));
        assertTrue(sql.contains("BINARY project_code = BINARY #{projectCode}"));
    }
}
