package com.nuono.next.noon;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import org.springframework.util.StringUtils;

final class NoonCatalogAuthCookieExport {

    private final CookieManager cookieManager;
    private volatile String preferredHost;
    private volatile URI preferredUri;
    private volatile String preferredHeader;

    NoonCatalogAuthCookieExport(CookieManager cookieManager) {
        this.cookieManager = cookieManager;
    }

    void prefer(URI targetUri) {
        preferredHost = targetUri == null ? null : targetUri.getHost();
        preferredUri = targetUri;
    }

    void capturePreferredRequestCookieHeader(URI requestUri) {
        String expectedHost = normalizeDomain(preferredHost);
        String requestHost = requestUri == null ? "" : normalizeDomain(requestUri.getHost());
        if (!StringUtils.hasText(expectedHost) || !expectedHost.equals(requestHost)) {
            return;
        }
        try {
            Map<String, List<String>> requestHeaders = cookieManager.get(requestUri, Map.of());
            List<String> cookieHeaders = requestHeaders.get("Cookie");
            if (cookieHeaders == null || cookieHeaders.isEmpty()) {
                cookieHeaders = requestHeaders.get("cookie");
            }
            if (cookieHeaders != null && !cookieHeaders.isEmpty()) {
                String joined = String.join("; ", cookieHeaders);
                if (StringUtils.hasText(joined)) {
                    preferredHeader = joined;
                }
            }
        } catch (IOException ignored) {
            // Best effort: requests still use CookieManager even without this export hint.
        }
    }

    String exportAuthCookieHeader() {
        Map<String, HttpCookie> selectedCookies = new LinkedHashMap<>();
        Set<String> preferredNames = new LinkedHashSet<>();
        for (Map.Entry<String, String> cookie : parseCookieHeader(preferredHeader).entrySet()) {
            String normalizedName = cookie.getKey().toLowerCase(Locale.ROOT);
            selectedCookies.put(normalizedName, new HttpCookie(cookie.getKey(), cookie.getValue()));
            preferredNames.add(normalizedName);
        }
        for (HttpCookie cookie : cookieManager.getCookieStore().getCookies()) {
            String name = cookie.getName();
            if (!isExportable(cookie, name)) {
                continue;
            }
            String normalizedName = name.toLowerCase(Locale.ROOT);
            if (preferredNames.contains(normalizedName)) {
                continue;
            }
            HttpCookie current = selectedCookies.get(normalizedName);
            if (current == null || prefers(cookie, current)) {
                selectedCookies.put(normalizedName, cookie);
            }
        }
        StringJoiner joiner = new StringJoiner("; ");
        selectedCookies.values().forEach(cookie -> joiner.add(cookie.getName() + "=" + cookie.getValue()));
        String exported = joiner.toString();
        return StringUtils.hasText(exported) ? exported : null;
    }

    private boolean isExportable(HttpCookie cookie, String name) {
        return cookie != null
                && StringUtils.hasText(name)
                && !"projectCode".equalsIgnoreCase(name)
                && !"noonStore".equalsIgnoreCase(name)
                && !"projectUser".equalsIgnoreCase(name)
                && StringUtils.hasText(cookie.getValue());
    }

    private boolean prefers(HttpCookie candidate, HttpCookie current) {
        int candidatePriority = priority(candidate);
        int currentPriority = priority(current);
        if (candidatePriority != currentPriority) {
            return candidatePriority > currentPriority;
        }
        return normalizedDomain(candidate).length() >= normalizedDomain(current).length();
    }

    private int priority(HttpCookie cookie) {
        String expectedHost = normalizeDomain(preferredHost);
        String cookieDomain = normalizedDomain(cookie);
        if (!StringUtils.hasText(expectedHost)) {
            return 0;
        }
        if (StringUtils.hasText(cookieDomain) && expectedHost.equals(cookieDomain)) {
            return 3;
        }
        if (preferredUri != null && cookieManager.getCookieStore().get(preferredUri).contains(cookie)) {
            return 2;
        }
        return StringUtils.hasText(cookieDomain) && expectedHost.endsWith("." + cookieDomain) ? 1 : 0;
    }

    private String normalizedDomain(HttpCookie cookie) {
        return cookie == null ? "" : normalizeDomain(cookie.getDomain());
    }

    private String normalizeDomain(String domain) {
        if (!StringUtils.hasText(domain)) {
            return "";
        }
        String normalized = domain.trim().toLowerCase(Locale.ROOT);
        while (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private Map<String, String> parseCookieHeader(String cookieHeader) {
        Map<String, String> cookies = new LinkedHashMap<>();
        if (!StringUtils.hasText(cookieHeader)) {
            return cookies;
        }
        for (String rawSegment : cookieHeader.split(";")) {
            String segment = rawSegment == null ? "" : rawSegment.trim();
            if (!StringUtils.hasText(segment) || !segment.contains("=")) {
                continue;
            }
            int splitIndex = segment.indexOf('=');
            String name = segment.substring(0, splitIndex).trim();
            String value = segment.substring(splitIndex + 1).trim();
            if (StringUtils.hasText(name) && !name.startsWith("$") && StringUtils.hasText(value)) {
                cookies.put(name, value);
            }
        }
        return cookies;
    }
}
