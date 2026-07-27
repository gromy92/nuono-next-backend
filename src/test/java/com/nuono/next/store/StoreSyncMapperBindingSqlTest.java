package com.nuono.next.store;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class StoreSyncMapperBindingSqlTest {

    @Test
    void projectReadsIncludeCanonicalSessionUserCode() {
        Select select = mapperMethod(
                "selectOwnerProject",
                Long.class,
                String.class
        ).getAnnotation(Select.class);

        assertTrue(sql(select.value()).contains("up.noon_partner_user_code"));
    }

    @Test
    void reauthenticationPersistsVerifiedUserCodeWithProjectCookie() {
        Update update = mapperMethod(
                "updateProjectReauthenticationSuccess",
                Long.class,
                Long.class,
                String.class,
                String.class,
                Long.class
        ).getAnnotation(Update.class);
        String sql = sql(update.value());

        assertTrue(sql.contains("noon_partner_user_code"));
        assertTrue(sql.contains("noon_partner_cookie"));
        assertTrue(sql.contains("cookie_generate_time = NOW()"));
        assertTrue(sql.contains("WHERE id = #{projectId}"));
        assertTrue(sql.contains("AND user_id = #{ownerUserId}"));
    }

    private Method mapperMethod(String name, Class<?>... parameterTypes) {
        try {
            return StoreSyncMapper.class.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }

    private String sql(String[] lines) {
        return String.join(" ", Arrays.asList(lines))
                .replaceAll("\\s+", " ")
                .trim();
    }
}
