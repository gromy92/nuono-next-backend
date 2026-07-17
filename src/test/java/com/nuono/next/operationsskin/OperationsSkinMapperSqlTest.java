package com.nuono.next.operationsskin;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.OperationsSkinMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class OperationsSkinMapperSqlTest {

    @Test
    void listDetailAndNameChecksUseLogicalStoreSiteScope() throws Exception {
        assertLogicalStoreScope(selectSql(
                "selectSkins",
                Long.class,
                String.class,
                String.class,
                String.class
        ));
        assertLogicalStoreScope(selectSql(
                "selectSkinById",
                Long.class,
                Long.class,
                String.class
        ));
        assertLogicalStoreScope(selectSql(
                "countByName",
                Long.class,
                String.class,
                String.class,
                Long.class
        ));
    }

    @Test
    void assetReadScopeUsesAuthorizedSiblingSiteOfSameLogicalStore() throws Exception {
        String sql = selectSql(
                "countLinkedStoreSites",
                Long.class,
                String.class,
                String.class
        );

        assertThat(sql)
                .contains("logical_scope.owner_user_id = #{ownerUserId}")
                .contains("source_site.logical_store_id = accessible_site.logical_store_id")
                .contains("accessible_site.store_code = #{accessibleStoreCode}")
                .contains("CONVERT(#{sourceStoreCode} USING utf8mb4) COLLATE utf8mb4_unicode_ci");
    }

    @Test
    void touchSkinRefreshesParentEffectiveUpdateTimeWithinPersistedScope() throws Exception {
        Method method = OperationsSkinMapper.class.getMethod(
                "touchSkin",
                Long.class,
                Long.class,
                String.class,
                Long.class
        );
        String sql = String.join("\n", method.getAnnotation(Update.class).value()).replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("UPDATE operations_image_skin")
                .contains("updated_by = #{updatedBy}")
                .contains("updated_at = NOW()")
                .contains("owner_user_id = #{ownerUserId}")
                .contains("store_code = #{storeCode}")
                .contains("deleted = b'0'");
    }

    private static String selectSql(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = OperationsSkinMapper.class.getMethod(methodName, parameterTypes);
        String rawSql = String.join("\n", method.getAnnotation(Select.class).value());
        new XMLLanguageDriver().createSqlSource(new Configuration(), rawSql, Object.class);
        return rawSql.replaceAll("\\s+", " ");
    }

    private static void assertLogicalStoreScope(String sql) {
        assertThat(sql)
                .contains("FROM logical_store_site requested_site")
                .contains("requested_store.owner_user_id = #{ownerUserId}")
                .contains("skin_site.logical_store_id = requested_site.logical_store_id")
                .contains("CONVERT(skin_site.store_code USING utf8mb4) COLLATE utf8mb4_unicode_ci")
                .contains("CONVERT(s.store_code USING utf8mb4) COLLATE utf8mb4_unicode_ci")
                .contains("requested_site.store_code = #{storeCode}")
                .contains("requested_site.is_deleted = b'0'")
                .contains("skin_site.is_deleted = b'0'");
    }
}
