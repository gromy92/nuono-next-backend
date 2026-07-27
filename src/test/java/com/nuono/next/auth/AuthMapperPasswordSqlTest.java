package com.nuono.next.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.AuthMapper;
import com.nuono.next.infrastructure.mapper.MasterDataMapper;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class AuthMapperPasswordSqlTest {

    @Test
    void passwordUpgradeShouldUseAnExactCompareAndSetGuard() throws Exception {
        Method method = AuthMapper.class.getMethod(
                "upgradePasswordIfUnchanged",
                Long.class,
                String.class,
                String.class
        );
        String sql = updateSql(method);

        assertThat(sql).contains("id = #{userId}");
        assertThat(sql).contains("BINARY password = BINARY #{expectedStoredCredential}");
        assertThat(sql).contains("password = #{newPasswordCredential}");
        assertThat(sql).doesNotContain("credential_version");
    }

    @Test
    void explicitPasswordChangesShouldAdvanceCredentialVersionInTheSameUpdate() throws Exception {
        String selfChangeSql = updateSql(AuthMapper.class.getMethod(
                "updateCurrentUserPassword",
                Long.class,
                Long.class,
                String.class,
                String.class
        ));
        String administratorChangeSql = updateSql(MasterDataMapper.class.getMethod(
                "updateUserPassword",
                Long.class,
                String.class,
                Long.class
        ));

        assertThat(selfChangeSql)
                .contains("password = #{passwordCredential}")
                .contains("credential_version = credential_version + 1")
                .contains("credential_version = #{expectedCredentialVersion}")
                .contains("BINARY password = BINARY #{expectedStoredCredential}");
        assertThat(administratorChangeSql)
                .contains("password = #{passwordCredential}")
                .contains("credential_version = credential_version + 1");
    }

    @Test
    void sessionValidationShouldReadCurrentAuthorizationForAnActiveUser() throws Exception {
        Method method = AuthMapper.class.getMethod("selectSessionState", Long.class);
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ").trim();

        assertThat(sql)
                .contains("u.credential_version")
                .contains("u.role_id")
                .contains("COALESCE(r.level, u.level) AS level")
                .contains("JOIN role r ON r.id = u.role_id AND r.is_deleted = 0")
                .contains("u.id = #{userId}")
                .contains("u.is_deleted = 0")
                .contains("u.status = 1")
                .contains("u.effective_time IS NULL OR u.effective_time <= NOW()")
                .contains("u.expired_time IS NULL OR u.expired_time >= NOW()");
    }

    @Test
    void passwordChangeCredentialLookupShouldOnlyReturnAnActiveAccount() throws Exception {
        Method method = AuthMapper.class.getMethod("selectCurrentPasswordCredential", Long.class);
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ").trim();

        assertThat(sql)
                .contains("SELECT u.password")
                .contains("u.id = #{userId}")
                .contains("u.is_deleted = 0")
                .contains("u.status = 1")
                .contains("u.effective_time IS NULL OR u.effective_time <= NOW()")
                .contains("u.expired_time IS NULL OR u.expired_time >= NOW()");
    }

    private static String updateSql(Method method) {
        Update update = method.getAnnotation(Update.class);
        return String.join(" ", update.value()).replaceAll("\\s+", " ").trim();
    }
}
