package com.nuono.next.noon;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import org.springframework.util.StringUtils;

final class NoonCatalogAuthCookieExport {

    private static final URI DEFAULT_CATALOG_URI = URI.create("https://noon-catalog.noon.partners");
    private final CookieManager cookieManager;
    private volatile String preferredHost;
    private volatile URI preferredUri;
    private volatile String latestRequestHeader;

    NoonCatalogAuthCookieExport(CookieManager cookieManager) {
        this.cookieManager = cookieManager;
    }

    void prefer(URI targetUri) {
        preferredHost = targetUri == null ? null : targetUri.getHost();
        preferredUri = targetUri;
    }

    void captureRequestCookieHeader(URI requestUri) {
        try {
            Map<String, List<String>> requestHeaders = cookieManager.get(requestUri, Map.of());
            List<String> cookieHeaders = requestHeaders.get("Cookie");
            if (cookieHeaders == null || cookieHeaders.isEmpty()) {
                cookieHeaders = requestHeaders.get("cookie");
            }
            if (cookieHeaders != null && !cookieHeaders.isEmpty()) {
                String joined = String.join("; ", cookieHeaders);
                if (StringUtils.hasText(joined)) {
                    latestRequestHeader = joined;
                }
            }
        } catch (IOException ignored) {
            // Best effort: requests still use CookieManager even without this export hint.
        }
    }

    String exportAuthCookieHeader() {
        List<CookiePart> requestCookies = parseCookieHeader(latestRequestHeader);
        Map<String, HttpCookie> selectedCookies = new LinkedHashMap<>();
        Set<String> preferredNames = new LinkedHashSet<>();
        for (CookiePart cookie : requestCookies) {
            String normalizedName = cookie.normalizedName();
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
        requestCookies.forEach(cookie -> joiner.add(cookie.name() + "=" + cookie.value()));
        selectedCookies.values().forEach(cookie -> joiner.add(cookie.getName() + "=" + cookie.getValue()));
        String exported = joiner.toString();
        return StringUtils.hasText(exported) ? exported : null;
    }

    void importAuthCookieHeader(String cookieHeader, URI catalogUri) {
        if (catalogUri == null || !StringUtils.hasText(catalogUri.getHost())) {
            throw new IllegalArgumentException("Noon Catalog Cookie 导入缺少目标主机。");
        }
        List<CookiePart> cookies = parseCookieHeader(cookieHeader);
        Map<String, Integer> totals = new LinkedHashMap<>();
        cookies.forEach(cookie -> totals.merge(cookie.normalizedName(), 1, Integer::sum));
        Map<String, Integer> occurrences = new LinkedHashMap<>();
        boolean catalogCookieFirst = catalogCookieComesFirst(catalogUri);
        for (CookiePart cookie : cookies) {
            int occurrence = occurrences.getOrDefault(cookie.normalizedName(), 0);
            occurrences.put(cookie.normalizedName(), occurrence + 1);
            HttpCookie imported = new HttpCookie(cookie.name(), cookie.value());
            boolean duplicate = totals.get(cookie.normalizedName()) > 1;
            boolean catalogScoped = duplicate && (catalogCookieFirst == (occurrence == 0));
            imported.setDomain(catalogScoped ? catalogUri.getHost() : ".noon.partners");
            imported.setPath("/");
            imported.setVersion(0);
            imported.setSecure("https".equalsIgnoreCase(catalogUri.getScheme()));
            cookieManager.getCookieStore().add(catalogUri, imported);
        }
    }

    void importAuthCookieHeader(String cookieHeader, String catalogUrl) {
        URI catalogUri = StringUtils.hasText(catalogUrl) ? URI.create(catalogUrl) : DEFAULT_CATALOG_URI;
        importAuthCookieHeader(cookieHeader, catalogUri);
    }

    private boolean catalogCookieComesFirst(URI catalogUri) {
        try {
            CookieManager probe = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            HttpCookie parent = new HttpCookie("__nuono_order_probe", "parent");
            parent.setDomain(".noon.partners");
            parent.setPath("/");
            parent.setVersion(0);
            probe.getCookieStore().add(catalogUri, parent);
            HttpCookie catalog = new HttpCookie("__nuono_order_probe", "catalog");
            catalog.setDomain(catalogUri.getHost());
            catalog.setPath("/");
            catalog.setVersion(0);
            probe.getCookieStore().add(catalogUri, catalog);
            List<String> headers = probe.get(catalogUri, Map.of()).get("Cookie");
            return headers != null && String.join("; ", headers).indexOf("=catalog") >= 0
                    && String.join("; ", headers).indexOf("=catalog") < String.join("; ", headers).indexOf("=parent");
        } catch (IOException ignored) {
            return false;
        }
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

    private List<CookiePart> parseCookieHeader(String cookieHeader) {
        List<CookiePart> cookies = new ArrayList<>();
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
            if (isExportableName(name) && StringUtils.hasText(value)) {
                cookies.add(new CookiePart(name, value));
            }
        }
        return cookies;
    }

    private boolean isExportableName(String name) {
        return StringUtils.hasText(name)
                && !name.startsWith("$")
                && !"projectCode".equalsIgnoreCase(name)
                && !"noonStore".equalsIgnoreCase(name)
                && !"projectUser".equalsIgnoreCase(name);
    }

    private static final class CookiePart {
        private final String name;
        private final String value;

        private CookiePart(String name, String value) {
            this.name = name;
            this.value = value;
        }

        private String name() {
            return name;
        }

        private String value() {
            return value;
        }

        private String normalizedName() {
            return name.toLowerCase(Locale.ROOT);
        }
    }
}
