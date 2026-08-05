package com.nuono.next.noon;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Map;
import org.springframework.util.StringUtils;

/** Applies the shared Noon browser header profile and operation-specific overrides. */
final class NoonRequestHeaders {

    private final String userAgent;
    private final String acceptLanguage;
    private final String locale;
    private final String language;

    NoonRequestHeaders(
            String userAgent,
            String acceptLanguage,
            String locale,
            String language
    ) {
        this.userAgent = userAgent;
        this.acceptLanguage = acceptLanguage;
        this.locale = locale;
        this.language = language;
    }

    void applyDefaults(HttpRequest.Builder builder, URI uri) {
        setIfPresent(builder, "User-Agent", userAgent);
        setIfPresent(builder, "Accept-Language", acceptLanguage);
        setIfPresent(builder, "X-Locale", locale);
        setIfPresent(builder, "X-Lang", language);
        builder.setHeader("X-Platform", "web");
        String origin = origin(uri);
        if (StringUtils.hasText(origin)) {
            builder.setHeader("Origin", origin);
            builder.setHeader("Referer", origin + "/");
        }
    }

    void applyExtra(HttpRequest.Builder builder, Map<String, String> headers) {
        if (headers == null) {
            return;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (StringUtils.hasText(entry.getKey())
                    && StringUtils.hasText(entry.getValue())) {
                builder.setHeader(entry.getKey(), entry.getValue());
            }
        }
    }

    private void setIfPresent(HttpRequest.Builder builder, String name, String value) {
        if (StringUtils.hasText(value)) {
            builder.setHeader(name, value);
        }
    }

    private String origin(URI uri) {
        if (uri == null || !StringUtils.hasText(uri.getScheme())
                || !StringUtils.hasText(uri.getHost())) {
            return null;
        }
        return uri.getScheme() + "://" + uri.getHost();
    }
}
