package com.nuono.next.noon;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.StringUtils;

/** Applies project/store cookies and builds request-scoped Noon URIs. */
final class NoonSessionRequestContext {

    private static final URI[] COOKIE_TARGETS = {
            URI.create("https://noon-catalog.noon.partners"),
            URI.create("https://noon-store.noon.partners"),
            URI.create("https://toolbar.noon.partners"),
            URI.create("https://login.noon.partners"),
            URI.create("https://login-alt.noon.partners")
    };

    private final CookieManager cookieManager;

    NoonSessionRequestContext(CookieManager cookieManager) {
        this.cookieManager = Objects.requireNonNull(cookieManager, "cookieManager");
    }

    URI uri(String url, boolean withProject, String projectCode) {
        if (!withProject || !StringUtils.hasText(projectCode)) {
            return URI.create(url);
        }
        String separator = url.contains("?") ? "&" : "?";
        return URI.create(url + separator + "project=" + projectCode);
    }

    void applyCookies(String projectCode, String storeCode) {
        addCookie("projectCode", projectCode);
        addCookie("noonStore", storeCode);
    }

    void addCookie(String name, String value) {
        if (!StringUtils.hasText(name) || !StringUtils.hasText(value)) {
            return;
        }
        HttpCookie cookie = cookie(name, value);
        cookie.setDomain(".noon.partners");
        for (URI target : COOKIE_TARGETS) {
            cookieManager.getCookieStore().add(target, cookie);
        }
    }

    void addTargetCookie(URI target, String name, String value) {
        HttpCookie cookie = cookie(name, value);
        cookie.setDomain(target.getHost());
        cookie.setSecure("https".equalsIgnoreCase(target.getScheme()));
        cookieManager.getCookieStore().add(target, cookie);
    }

    static Map<String, String> parseCookieHeader(String cookieHeader) {
        Map<String, String> cookies = new LinkedHashMap<>();
        if (!StringUtils.hasText(cookieHeader)) {
            return cookies;
        }
        for (String raw : cookieHeader.split(";")) {
            String segment = raw == null ? "" : raw.trim();
            int split = segment.indexOf('=');
            if (split <= 0) {
                continue;
            }
            String name = segment.substring(0, split).trim();
            String value = segment.substring(split + 1).trim();
            if (StringUtils.hasText(name) && StringUtils.hasText(value)) {
                cookies.put(name, value);
            }
        }
        return cookies;
    }

    private HttpCookie cookie(String name, String value) {
        HttpCookie cookie = new HttpCookie(name, value);
        cookie.setPath("/");
        cookie.setVersion(0);
        return cookie;
    }
}
