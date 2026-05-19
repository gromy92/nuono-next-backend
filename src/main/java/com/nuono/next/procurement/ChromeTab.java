package com.nuono.next.procurement;

import java.util.Locale;

class ChromeTab {

    int windowIndex;
    int tabIndex;
    String title;
    String url;

    boolean isLoginPage() {
        String normalizedUrl = url == null ? "" : url.toLowerCase(Locale.ROOT);
        String normalizedTitle = title == null ? "" : title.toLowerCase(Locale.ROOT);
        return normalizedUrl.contains("login.taobao.com")
                || normalizedUrl.contains("login.1688.com")
                || normalizedTitle.contains("采购批发平台");
    }
}
