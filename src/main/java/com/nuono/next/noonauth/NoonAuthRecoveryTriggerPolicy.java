package com.nuono.next.noonauth;

import java.util.Locale;
import org.springframework.util.StringUtils;

public final class NoonAuthRecoveryTriggerPolicy {
    private NoonAuthRecoveryTriggerPolicy() {
    }

    public static boolean isExplicitAuthExpiry(String rawFailure) {
        if (!StringUtils.hasText(rawFailure)) {
            return false;
        }
        String value = rawFailure.toLowerCase(Locale.ROOT);
        if (value.contains("account does not contain current project")
                || value.contains("account does not include current project")
                || value.contains("账号不包含当前项目")
                || value.contains("project_access_denied")) {
            return false;
        }
        return value.contains("auth_required")
                || value.contains("auth required")
                || value.contains("cookie invalid")
                || value.contains("cookie expired")
                || value.contains("cookie 无效")
                || value.contains("cookie 已过期")
                || value.contains("http 401")
                || value.contains("unauthorized")
                || (value.contains("whoami") && containsAuthRedirect(value));
    }

    private static boolean containsAuthRedirect(String value) {
        return value.contains("301")
                || value.contains("302")
                || value.contains("303")
                || value.contains("307")
                || value.contains("308")
                || value.contains("redirect");
    }
}
