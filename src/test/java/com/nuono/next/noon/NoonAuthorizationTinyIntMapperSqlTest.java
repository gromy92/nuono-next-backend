package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.NoonAccountSessionMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class NoonAuthorizationTinyIntMapperSqlTest {
    @Test
    void manualAccountSessionReadsAndPersistsTinyintAuthorizedBindings() {
        String selectSql = selectSql(NoonAccountSessionMapper.class, "listBoundProjects");
        String updateSql = updateSql(NoonAccountSessionMapper.class, "persistProjectSession");

        assertTrue(selectSql.contains("COALESCE(us.is_authorized, 0) = 1"));
        assertTrue(selectSql.contains("COALESCE(up.is_authorized, 0) = 1"));
        assertTrue(updateSql.contains("COALESCE(is_authorized, 0) = 1"));
        assertFalse(selectSql.contains("is_authorized, b'0'"));
        assertFalse(updateSql.contains("is_authorized, b'0'"));
    }

    @Test
    void storeAccessLookupUsesTheSameTinyintAuthorizedContract() {
        String sql = selectSql(StoreSyncMapper.class, "selectAccessibleOwnerUserIdForStore");

        assertTrue(sql.contains("COALESCE(us.is_authorized, 0) = 1"));
        assertTrue(sql.contains("COALESCE(up.is_authorized, 0) = 1"));
        assertFalse(sql.contains("is_authorized, b'0'"));
    }

    private static String selectSql(Class<?> mapperType, String methodName) {
        return String.join(" ", method(mapperType, methodName).getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ");
    }

    private static String updateSql(Class<?> mapperType, String methodName) {
        return String.join(" ", method(mapperType, methodName).getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");
    }

    private static Method method(Class<?> mapperType, String methodName) {
        return Arrays.stream(mapperType.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
    }
}
