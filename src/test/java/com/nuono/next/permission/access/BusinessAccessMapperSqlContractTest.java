package com.nuono.next.permission.access;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class BusinessAccessMapperSqlContractTest {

    @Test
    void storeScopeIncludesLogicalStoreSiteRowsForNewSystemStores() throws Exception {
        Method method = BusinessAccessMapper.class.getMethod("selectStoreScope", Long.class);
        Select select = method.getAnnotation(Select.class);
        String sql = String.join("\n", select.value());

        assertTrue(sql.contains("logical_store ls"), "store scope must include logical_store owner rows");
        assertTrue(sql.contains("logical_store_site lss"), "store scope must include logical_store_site store codes");
        assertTrue(sql.contains("ls.owner_user_id = #{userId}"), "boss users must get their own logical store sites");
        assertTrue(sql.contains("upa.user_id = #{userId}"), "operators must get logical store sites through project access");
        assertTrue(
                sql.contains("BINARY ls.project_code = BINARY up.project_code"),
                "logical store project joins must avoid local MySQL collation mismatches"
        );
        assertTrue(
                sql.contains("CONVERT(lss.store_code USING utf8mb4) COLLATE utf8mb4_unicode_ci AS store_code"),
                "scope rows must expose site-level store codes"
        );
    }

    @Test
    void storeScopeNormalizesStoreCodeCollationAcrossUnionBranches() throws Exception {
        Method method = BusinessAccessMapper.class.getMethod("selectStoreScope", Long.class);
        Select select = method.getAnnotation(Select.class);
        String sql = String.join("\n", select.value());

        assertTrue(sql.contains("CONVERT(up.project_code USING utf8mb4) COLLATE utf8mb4_unicode_ci AS store_code"));
        assertTrue(sql.contains("CONVERT(us.store_code USING utf8mb4) COLLATE utf8mb4_unicode_ci AS store_code"));
        assertTrue(sql.contains("CONVERT(lss.store_code USING utf8mb4) COLLATE utf8mb4_unicode_ci AS store_code"));
        assertFalse(sql.contains("up.project_code AS store_code"));
        assertFalse(sql.contains("us.store_code AS store_code"));
        assertFalse(sql.contains("lss.store_code AS store_code"));
    }
}
