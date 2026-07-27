package com.nuono.next.procurement;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
class Ali1688BrowserUrlPolicy {

    private static final String AIR_HOST = "air.1688.com";
    private static final String DETAIL_HOST = "detail.1688.com";
    private static final String RESULT_PATH = "/kapp/1688-pc-front/ai-avatar/inquiryResult";
    private static final String CREATE_PATH = "/kapp/1688-pc-front/ai-avatar/inquiryCreate";
    private static final String CHAT_PATH =
            "/app/ocms-fusion-components-1688/def_cbu_web_im/index.html";
    private static final Pattern OFFER_PATH_PATTERN =
            Pattern.compile("^/offer/(\\d+)\\.html$");
    private static final Pattern CHAT_OFFER_QUERY_PATTERN =
            Pattern.compile("(?:^|&)offerId=(\\d+)(?=&|$)", Pattern.CASE_INSENSITIVE);

    enum PageKind {
        INQUIRY_RESULT,
        INQUIRY_CREATE,
        CHAT,
        OFFER_DETAIL
    }

    String validateRequestedUrl(String rawUrl, PageKind pageKind) {
        if (!StringUtils.hasText(rawUrl)) {
            return null;
        }
        URI uri = parse(rawUrl).normalize();
        if (!accepts(uri, pageKind)) {
            throw new IllegalArgumentException("浏览器地址不在允许的 1688 HTTPS 页面范围内。");
        }
        return uri.toASCIIString();
    }

    boolean acceptsObservedUrl(String rawUrl, PageKind pageKind) {
        if (!StringUtils.hasText(rawUrl)) {
            return false;
        }
        try {
            return accepts(parse(rawUrl).normalize(), pageKind);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    boolean matchesOfferId(String rawUrl, PageKind pageKind, String offerId) {
        if (!StringUtils.hasText(offerId) || !acceptsObservedUrl(rawUrl, pageKind)) {
            return false;
        }
        URI uri = parse(rawUrl).normalize();
        if (pageKind == PageKind.OFFER_DETAIL) {
            Matcher matcher = OFFER_PATH_PATTERN.matcher(uri.getRawPath());
            return matcher.matches() && offerId.equals(matcher.group(1));
        }
        if (pageKind != PageKind.CHAT || !StringUtils.hasText(uri.getRawQuery())) {
            return false;
        }
        Matcher matcher = CHAT_OFFER_QUERY_PATTERN.matcher(uri.getRawQuery());
        String matchedOfferId = null;
        int matchCount = 0;
        while (matcher.find()) {
            matchedOfferId = matcher.group(1);
            matchCount++;
        }
        return matchCount == 1 && offerId.equals(matchedOfferId);
    }

    boolean matchesRequestedPage(String observedUrl, String requestedUrl, PageKind pageKind) {
        if (!acceptsObservedUrl(observedUrl, pageKind)
                || !acceptsObservedUrl(requestedUrl, pageKind)) {
            return false;
        }
        URI observed = parse(observedUrl).normalize();
        URI requested = parse(requestedUrl).normalize();
        return Objects.equals(normalizedHost(observed), normalizedHost(requested))
                && Objects.equals(observed.getRawPath(), requested.getRawPath())
                && Objects.equals(observed.getRawQuery(), requested.getRawQuery());
    }

    boolean acceptsLoginUrl(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return false;
        }
        try {
            URI uri = parse(rawUrl).normalize();
            String host = normalizedHost(uri);
            return hasTrustedAuthority(uri)
                    && ("login.1688.com".equals(host) || "login.taobao.com".equals(host));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    String guardJavascript(PageKind pageKind, String expectedUrl, String javascript) {
        if (!StringUtils.hasText(javascript)) {
            throw new IllegalArgumentException("缺少浏览器执行脚本。");
        }
        String normalizedExpectedUrl = validateRequestedUrl(expectedUrl, pageKind);
        if (!StringUtils.hasText(normalizedExpectedUrl)) {
            throw new IllegalArgumentException("缺少浏览器执行目标地址。");
        }
        URI expected = parse(normalizedExpectedUrl);
        String expectedSearch = expected.getRawQuery() == null ? "" : "?" + expected.getRawQuery();
        String expression = javascript.trim();
        while (expression.endsWith(";")) {
            expression = expression.substring(0, expression.length() - 1).trim();
        }
        return "(() => {"
                + "if (!(location.protocol==='https:'"
                + "&&location.hostname==='" + escapeJavascript(expected.getHost()) + "'"
                + "&&location.port===''"
                + "&&location.pathname==='" + escapeJavascript(expected.getRawPath()) + "'"
                + "&&location.search==='" + escapeJavascript(expectedSearch) + "')) {"
                + "throw 'UNTRUSTED_BROWSER_URL';"
                + "}"
                + "return (" + expression + ");"
                + "})()";
    }

    private URI parse(String rawUrl) {
        try {
            return new URI(rawUrl.trim());
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("浏览器地址格式不正确。", exception);
        }
    }

    private boolean accepts(URI uri, PageKind pageKind) {
        if (pageKind == null || !hasTrustedAuthority(uri)) {
            return false;
        }
        String host = normalizedHost(uri);
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        switch (pageKind) {
            case INQUIRY_RESULT:
                return AIR_HOST.equals(host) && RESULT_PATH.equals(path);
            case INQUIRY_CREATE:
                return AIR_HOST.equals(host) && CREATE_PATH.equals(path);
            case CHAT:
                return AIR_HOST.equals(host) && CHAT_PATH.equals(path);
            case OFFER_DETAIL:
                return DETAIL_HOST.equals(host) && OFFER_PATH_PATTERN.matcher(path).matches();
            default:
                return false;
        }
    }

    private boolean hasTrustedAuthority(URI uri) {
        return uri != null
                && uri.isAbsolute()
                && !uri.isOpaque()
                && "https".equalsIgnoreCase(uri.getScheme())
                && StringUtils.hasText(uri.getHost())
                && uri.getRawUserInfo() == null
                && uri.getPort() == -1;
    }

    private String normalizedHost(URI uri) {
        return uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
    }

    private String escapeJavascript(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029");
    }
}
