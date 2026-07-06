package com.nuono.next.productpublicdetail.noon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.competitoranalysis.noon.NoonFrontendSearchPageParser;
import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.competitoranalysis.noon.NoonSearchProviderException;
import com.nuono.next.competitoranalysis.noon.NoonSearchRequest;
import com.nuono.next.competitoranalysis.noon.NoonSearchResult;
import com.nuono.next.noon.ChromeNoonCookieSupport;
import com.nuono.next.productpublicdetail.ProductPublicDetailSyncStatus;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class HttpNoonPublicProductDetailAdapter implements NoonPublicProductDetailAdapter {
    private static final String FRONTEND_CATALOG_DETAIL_PARSER_VERSION = "noon-frontend-catalog-detail-v1";

    private final NoonFrontendSearchPageParser parser;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final String publicBaseUrl;
    private final String catalogBaseUrl;
    private final String customerCatalogV3BaseUrl;
    private final String configuredFrontendCookieHeader;
    private final boolean chromeFrontendCookieEnabled;
    private final Supplier<String> frontendCookieHeaderSupplier;
    private final boolean curlEnabled;

    @Autowired
    public HttpNoonPublicProductDetailAdapter(
            NoonFrontendSearchPageParser parser,
            @Value("${nuono.product-public-detail.noon.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${nuono.product-public-detail.noon.request-timeout-seconds:20}") int requestTimeoutSeconds,
            @Value("${nuono.product-public-detail.noon.public-base-url:https://www.noon.com}") String publicBaseUrl,
            @Value("${nuono.product-public-detail.noon.catalog-base-url:https://noon-catalog.noon.partners/_svc/catalog/api/u}") String catalogBaseUrl,
            @Value("${nuono.product-public-detail.noon.customer-catalog-v3-base-url:https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u}") String customerCatalogV3BaseUrl,
            @Value("${nuono.product-public-detail.noon.frontend-cookie-header:}") String configuredFrontendCookieHeader,
            @Value("${nuono.product-public-detail.noon.chrome-frontend-cookie-enabled:false}") boolean chromeFrontendCookieEnabled,
            @Value("${nuono.product-public-detail.noon.curl-enabled:true}") boolean curlEnabled
    ) {
        this(
                parser,
                Duration.ofSeconds(Math.max(1, connectTimeoutSeconds)),
                Duration.ofSeconds(Math.max(3, requestTimeoutSeconds)),
                publicBaseUrl,
                catalogBaseUrl,
                customerCatalogV3BaseUrl,
                configuredFrontendCookieHeader,
                chromeFrontendCookieEnabled,
                ChromeNoonCookieSupport::loadNoonFrontendCookieHeader,
                curlEnabled
        );
    }

    HttpNoonPublicProductDetailAdapter(
            NoonFrontendSearchPageParser parser,
            Duration connectTimeout,
            Duration requestTimeout,
            String publicBaseUrl,
            String customerCatalogV3BaseUrl,
            String configuredFrontendCookieHeader,
            boolean curlEnabled
    ) {
        this(
                parser,
                connectTimeout,
                requestTimeout,
                publicBaseUrl,
                customerCatalogV3BaseUrl,
                configuredFrontendCookieHeader,
                false,
                () -> null,
                curlEnabled
        );
    }

    HttpNoonPublicProductDetailAdapter(
            NoonFrontendSearchPageParser parser,
            Duration connectTimeout,
            Duration requestTimeout,
            String publicBaseUrl,
            String customerCatalogV3BaseUrl,
            String configuredFrontendCookieHeader,
            boolean chromeFrontendCookieEnabled,
            Supplier<String> frontendCookieHeaderSupplier,
            boolean curlEnabled
    ) {
        this(
                parser,
                connectTimeout,
                requestTimeout,
                publicBaseUrl,
                "https://noon-catalog.noon.partners/_svc/catalog/api/u",
                customerCatalogV3BaseUrl,
                configuredFrontendCookieHeader,
                chromeFrontendCookieEnabled,
                frontendCookieHeaderSupplier,
                curlEnabled
        );
    }

    HttpNoonPublicProductDetailAdapter(
            NoonFrontendSearchPageParser parser,
            Duration connectTimeout,
            Duration requestTimeout,
            String publicBaseUrl,
            String catalogBaseUrl,
            String customerCatalogV3BaseUrl,
            String configuredFrontendCookieHeader,
            boolean curlEnabled
    ) {
        this(
                parser,
                connectTimeout,
                requestTimeout,
                publicBaseUrl,
                catalogBaseUrl,
                customerCatalogV3BaseUrl,
                configuredFrontendCookieHeader,
                false,
                () -> null,
                curlEnabled
        );
    }

    HttpNoonPublicProductDetailAdapter(
            NoonFrontendSearchPageParser parser,
            Duration connectTimeout,
            Duration requestTimeout,
            String publicBaseUrl,
            String catalogBaseUrl,
            String customerCatalogV3BaseUrl,
            String configuredFrontendCookieHeader,
            boolean chromeFrontendCookieEnabled,
            Supplier<String> frontendCookieHeaderSupplier,
            boolean curlEnabled
    ) {
        this.parser = parser;
        this.objectMapper = new ObjectMapper();
        this.requestTimeout = requestTimeout == null ? Duration.ofSeconds(20) : requestTimeout;
        this.publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
        this.catalogBaseUrl = normalizeCatalogBaseUrl(catalogBaseUrl);
        this.customerCatalogV3BaseUrl = normalizeCustomerCatalogV3BaseUrl(customerCatalogV3BaseUrl);
        this.configuredFrontendCookieHeader = configuredFrontendCookieHeader == null ? "" : configuredFrontendCookieHeader.trim();
        this.chromeFrontendCookieEnabled = chromeFrontendCookieEnabled;
        this.frontendCookieHeaderSupplier = frontendCookieHeaderSupplier == null ? () -> null : frontendCookieHeaderSupplier;
        this.curlEnabled = curlEnabled;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public NoonPublicProductDetailResult fetch(NoonPublicProductDetailRequest request) {
        String code = NoonProductCodeSupport.normalize(request == null ? null : request.getNoonProductCode());
        if (!StringUtils.hasText(code) || NoonProductCodeSupport.codeType(code).isEmpty()) {
            return failed(code, "INVALID_NOON_PRODUCT_CODE", "商品码不是有效 Noon 前台商品码。", null, null, null, null);
        }
        NoonSearchRequest searchRequest = NoonSearchRequest.builder()
                .siteCode(request.getSiteCode())
                .locale(request.getLocale())
                .keyword(code)
                .limit(20)
                .build();
        try {
            NoonPublicProductDetailResult detail = fetchFrontendCatalogDetail(searchRequest, code);
            if (detail != null) {
                return detail;
            }
        } catch (NoonSearchProviderException exception) {
            if (!shouldFallbackFromFrontendCatalogDetail(exception)) {
                return mapProviderException(code, exception);
            }
        } catch (Exception ignored) {
            // Existing search fallbacks still cover transient public frontend transport failures.
        }
        String url = buildCustomerCatalogV3SearchUrl(searchRequest);
        try {
            String frontendCookieHeader = loadFrontendCookieHeader(url);
            NoonSearchPage page = curlEnabled
                    ? fetchSearchPageWithCurl(searchRequest, url, frontendCookieHeader)
                    : fetchSearchPageWithHttp(searchRequest, url, frontendCookieHeader);
            NoonSearchResult exact = findExact(page, code);
            if (exact == null) {
                NoonPublicProductDetailResult result = notFound(code, page, "PUBLIC_DETAIL_NOT_FOUND", "Noon 前台 exact search 未返回该商品码。");
                result.setProviderSourceUrl(url);
                return result;
            }
            return partial(code, exact, page);
        } catch (NoonSearchProviderException exception) {
            if (shouldFallbackToCatalogPartner(exception)) {
                try {
                    return fetchFrontendHtmlSearch(searchRequest, code);
                } catch (NoonSearchProviderException ignored) {
                    // Keep the existing catalog partner fallback when the public HTML page is unavailable.
                } catch (Exception ignored) {
                    // Keep the existing catalog partner fallback when the public HTML page is unavailable.
                }
                try {
                    return fetchCatalogPartner(searchRequest, code);
                } catch (NoonSearchProviderException fallbackException) {
                    return mapProviderException(code, fallbackException);
                } catch (Exception fallbackException) {
                    return failed(
                            code,
                            "PROVIDER_UNAVAILABLE",
                            "Noon catalog partner 公开搜索暂不可用：" + shrink(fallbackException.getMessage(), 180),
                            null,
                            buildCatalogSearchUrl(searchRequest),
                            null,
                            null
                    );
                }
            }
            return mapProviderException(code, exception);
        } catch (Exception exception) {
            try {
                return fetchFrontendHtmlSearch(searchRequest, code);
            } catch (NoonSearchProviderException ignored) {
                // Keep the existing catalog partner fallback when the public HTML page is unavailable.
            } catch (Exception ignored) {
                // Keep the existing catalog partner fallback when the public HTML page is unavailable.
            }
            try {
                return fetchCatalogPartner(searchRequest, code);
            } catch (NoonSearchProviderException fallbackException) {
                return mapProviderException(code, fallbackException);
            } catch (Exception fallbackException) {
                return failed(
                        code,
                        "PROVIDER_UNAVAILABLE",
                        "Noon catalog partner 公开搜索暂不可用：" + shrink(fallbackException.getMessage(), 180),
                        null,
                        buildCatalogSearchUrl(searchRequest),
                        null,
                        null
                );
            }
        }
    }

    private NoonPublicProductDetailResult mapProviderException(String code, NoonSearchProviderException exception) {
        if (exception != null && exception.getProviderHttpStatus() != null && exception.getProviderHttpStatus() == 429) {
            return failed(
                    code,
                    "RATE_LIMITED",
                    exception.getMessage(),
                    exception.getProviderHttpStatus(),
                    exception.getSourceUrl(),
                    exception.getResponseHash(),
                    null
            );
        }
        if (exception != null && exception.getProviderHttpStatus() != null && exception.getProviderHttpStatus() == 403) {
            return failed(
                    code,
                    "BLOCKED_BY_RISK_CONTROL",
                    exception.getMessage(),
                    exception.getProviderHttpStatus(),
                    exception.getSourceUrl(),
                    exception.getResponseHash(),
                    null
            );
        }
        if ("PARSE_FAILED".equals(exception.getErrorCode())) {
            return failedOrNotFound(
                    ProductPublicDetailSyncStatus.NOT_FOUND,
                    code,
                    "PUBLIC_DETAIL_NOT_FOUND",
                    exception.getMessage(),
                    exception.getProviderHttpStatus(),
                    exception.getSourceUrl(),
                    exception.getResponseHash(),
                    null
            );
        }
        return failed(
                code,
                exception.getErrorCode(),
                exception.getMessage(),
                exception.getProviderHttpStatus(),
                exception.getSourceUrl(),
                exception.getResponseHash(),
                null
        );
    }

    private boolean shouldFallbackToCatalogPartner(NoonSearchProviderException exception) {
        String code = exception == null ? null : exception.getErrorCode();
        return "PROVIDER_UNAVAILABLE".equals(code)
                || "PARSE_FAILED".equals(code);
    }

    private boolean shouldFallbackFromFrontendCatalogDetail(NoonSearchProviderException exception) {
        Integer httpStatus = exception == null ? null : exception.getProviderHttpStatus();
        if (httpStatus != null && httpStatus != 200) {
            return httpStatus >= 500;
        }
        String code = exception == null ? null : exception.getErrorCode();
        return "PROVIDER_UNAVAILABLE".equals(code)
                || "PARSE_FAILED".equals(code);
    }

    NoonPublicProductDetailResult fetchFrontendCatalogDetail(NoonSearchRequest searchRequest, String code)
            throws IOException, InterruptedException {
        String url = buildCustomerCatalogV3DetailUrl(searchRequest, code);
        String frontendCookieHeader = loadFrontendCookieHeader(url);
        if (!StringUtils.hasText(frontendCookieHeader)) {
            return null;
        }
        return curlEnabled
                ? fetchFrontendCatalogDetailWithCurl(searchRequest, code, url, frontendCookieHeader)
                : fetchFrontendCatalogDetailWithHttp(searchRequest, code, url, frontendCookieHeader);
    }

    NoonPublicProductDetailResult fetchFrontendCatalogDetailWithHttp(
            NoonSearchRequest searchRequest,
            String code,
            String url,
            String frontendCookieHeader
    ) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(
                buildCustomerCatalogV3SearchRequest(searchRequest, url, frontendCookieHeader),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        return mapFrontendCatalogDetailResponse(searchRequest, code, url, response.statusCode(), response.body());
    }

    NoonPublicProductDetailResult fetchFrontendCatalogDetailWithCurl(
            NoonSearchRequest searchRequest,
            String code,
            String url,
            String frontendCookieHeader
    ) throws IOException {
        CurlResponse response = executeCurl(buildCustomerCatalogV3CurlConfig(searchRequest, url, frontendCookieHeader));
        if (response.exitCode != 0) {
            throw new NoonSearchProviderException(
                    "PROVIDER_UNAVAILABLE",
                    "Noon 前台 catalog detail curl 请求失败：" + shrink(response.stderr, 180),
                    null,
                    url,
                    null
            );
        }
        return mapFrontendCatalogDetailResponse(searchRequest, code, url, response.statusCode, response.body);
    }

    private NoonPublicProductDetailResult mapFrontendCatalogDetailResponse(
            NoonSearchRequest searchRequest,
            String code,
            String url,
            int statusCode,
            String body
    ) {
        if (statusCode == 404) {
            return failedOrNotFound(
                    ProductPublicDetailSyncStatus.NOT_FOUND,
                    code,
                    "PUBLIC_DETAIL_NOT_FOUND",
                    "Noon 前台 catalog detail 未返回该商品码。",
                    statusCode,
                    url,
                    sha256(body),
                    FRONTEND_CATALOG_DETAIL_PARSER_VERSION
            );
        }
        if (statusCode < 200 || statusCode >= 300) {
            throw mapUnsuccessfulStatus(statusCode, url);
        }
        return parseFrontendCatalogDetail(searchRequest, code, body, url, statusCode);
    }

    private NoonPublicProductDetailResult parseFrontendCatalogDetail(
            NoonSearchRequest searchRequest,
            String code,
            String json,
            String url,
            int providerHttpStatus
    ) {
        String body = json == null ? "" : json;
        String hash = sha256(body);
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception exception) {
            throw new NoonSearchProviderException(
                    "PARSE_FAILED",
                    "Noon 前台 catalog detail 响应不是有效 JSON。",
                    providerHttpStatus,
                    url,
                    hash
            );
        }

        JsonNode product = frontendCatalogProduct(root);
        if (product == null || !product.isObject()) {
            throw new NoonSearchProviderException(
                    "PARSE_FAILED",
                    "Noon 前台 catalog detail 响应无法解析出商品详情。",
                    providerHttpStatus,
                    url,
                    hash
            );
        }
        String productCode = NoonProductCodeSupport.normalize(firstNonBlank(
                textAny(product, "sku", "sku_config", "catalog_sku", "noonProductCode", "productCode", "product_code"),
                textAny(root, "sku", "sku_config", "catalog_sku", "noonProductCode", "productCode", "product_code")
        ));
        if (!code.equals(productCode)) {
            return failedOrNotFound(
                    ProductPublicDetailSyncStatus.NOT_FOUND,
                    code,
                    "PUBLIC_DETAIL_NOT_FOUND",
                    "Noon 前台 catalog detail 返回的商品码不匹配。",
                    providerHttpStatus,
                    url,
                    hash,
                    FRONTEND_CATALOG_DETAIL_PARSER_VERSION
            );
        }

        NoonPublicProductDetailResult mapped = failedOrNotFound(
                ProductPublicDetailSyncStatus.PARTIAL,
                code,
                "PARTIAL_DETAIL",
                "Noon 前台 catalog detail 返回公开商品字段，商家后台详情字段仍需后续补齐。",
                providerHttpStatus,
                url,
                hash,
                FRONTEND_CATALOG_DETAIL_PARSER_VERSION
        );
        JsonNode catalog = root.path("catalog");
        mapped.setCodeType(NoonProductCodeSupport.codeType(code).orElse(null));
        mapped.setTitleEn(trim(textAny(product, "product_title", "title", "name", "title_en", "name_en")));
        mapped.setTitleAr(trim(textAny(product, "product_title_ar", "title_ar", "name_ar")));
        mapped.setBrand(resolveFrontendBrand(product.path("brand")));
        mapped.setCategoryPath(trim(firstNonBlank(
                textAny(product, "category_path", "categoryPath", "category", "category_name"),
                textAny(catalog, "category_path", "categoryPath", "category", "category_name")
        )));
        mapped.setPriceAmount(resolveFrontendPrice(product));
        mapped.setCurrencyCode(trim(firstNonBlank(
                textAny(product, "currency", "currency_code", "currencyCode", "priceCurrency"),
                textAny(product.path("price"), "currency", "currency_code", "currencyCode"),
                inferCurrencyCode(searchRequest)
        )));
        mapped.setRating(resolveFrontendRating(product));
        mapped.setReviewCount(resolveFrontendReviewCount(product));
        mapped.setAvailabilityText(trim(textAny(product, "availability", "availability_text", "stock_status", "stockStatus")));
        mapped.setMainImageUrl(resolveFrontendImage(product));
        mapped.setDetailUrl(resolveDetailUrl(firstNonBlank(
                textAny(product, "canonical_url", "canonicalUrl", "url", "product_url", "productUrl"),
                textAny(catalog, "canonical_url", "canonicalUrl", "url", "product_url", "productUrl"),
                textAny(root, "canonical_url", "canonicalUrl", "url", "product_url", "productUrl")
        )));
        mapped.setRawPayloadJson(product.toString());
        return mapped;
    }

    private NoonPublicProductDetailResult fetchFrontendHtmlSearch(NoonSearchRequest searchRequest, String code) throws Exception {
        String url = buildFrontendHtmlSearchUrl(searchRequest);
        String frontendCookieHeader = loadFrontendCookieHeader(url);
        NoonSearchPage page = curlEnabled
                ? fetchFrontendHtmlSearchPageWithCurl(searchRequest, url, frontendCookieHeader)
                : fetchSearchPageWithHttp(searchRequest, url, frontendCookieHeader);
        NoonSearchResult exact = findExact(page, code);
        if (exact == null) {
            NoonPublicProductDetailResult result = notFound(code, page, "PUBLIC_DETAIL_NOT_FOUND", "Noon 前台 HTML exact search 未返回该商品码。");
            result.setProviderSourceUrl(url);
            return result;
        }
        return partial(code, exact, page);
    }

    private NoonPublicProductDetailResult fetchCatalogPartner(NoonSearchRequest searchRequest, String code) throws Exception {
        String url = buildCatalogSearchUrl(searchRequest);
        String frontendCookieHeader = loadFrontendCookieHeader(url);
        NoonSearchPage page = curlEnabled
                ? fetchSearchPageWithCurl(searchRequest, url, frontendCookieHeader)
                : fetchSearchPageWithHttp(searchRequest, url, frontendCookieHeader);
        NoonSearchResult exact = findExact(page, code);
        if (exact == null) {
            NoonPublicProductDetailResult result = notFound(code, page, "PUBLIC_DETAIL_NOT_FOUND", "Noon catalog partner exact search 未返回该商品码。");
            result.setProviderSourceUrl(url);
            return result;
        }
        return partial(code, exact, page);
    }

    NoonSearchPage fetchSearchPageWithHttp(NoonSearchRequest request, String url, String frontendCookieHeader) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(
                buildCustomerCatalogV3SearchRequest(request, url, frontendCookieHeader),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw mapUnsuccessfulStatus(statusCode, url);
        }
        if (isFrontendHtmlSearchUrl(url)) {
            return parser.parse(response.body(), url, statusCode);
        }
        return parser.parseCatalogJson(response.body(), url, statusCode);
    }

    NoonSearchPage fetchSearchPageWithCurl(NoonSearchRequest request, String url, String frontendCookieHeader) throws IOException {
        CurlResponse response = executeCurl(buildCustomerCatalogV3CurlConfig(request, url, frontendCookieHeader));
        if (response.exitCode != 0) {
            throw new NoonSearchProviderException(
                    "PROVIDER_UNAVAILABLE",
                    "Noon 前台 catalog v3 curl 请求失败：" + shrink(response.stderr, 180),
                    null,
                    url,
                    null
            );
        }
        if (response.statusCode < 200 || response.statusCode >= 300) {
            throw mapUnsuccessfulStatus(response.statusCode, url);
        }
        return parser.parseCatalogJson(response.body, url, response.statusCode);
    }

    NoonSearchPage fetchFrontendHtmlSearchPageWithCurl(NoonSearchRequest request, String url, String frontendCookieHeader) throws IOException {
        CurlResponse response = executeCurl(buildFrontendHtmlSearchCurlConfig(request, url, frontendCookieHeader));
        if (response.exitCode != 0) {
            throw new NoonSearchProviderException(
                    "PROVIDER_UNAVAILABLE",
                    "Noon 前台 HTML curl 请求失败：" + shrink(response.stderr, 180),
                    null,
                    url,
                    null
            );
        }
        if (response.statusCode < 200 || response.statusCode >= 300) {
            throw mapUnsuccessfulStatus(response.statusCode, url);
        }
        return parser.parse(response.body, url, response.statusCode);
    }

    HttpRequest buildCustomerCatalogV3SearchRequest(NoonSearchRequest request, String url, String frontendCookieHeader) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout)
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36")
                .header("Accept-Language", acceptLanguage(request))
                .header("Accept", "application/json, text/plain, */*")
                .header("Cache-Control", "no-cache, max-age=0, must-revalidate, no-store")
                .header("Referer", buildPublicSearchReferer(request))
                .header("Sec-CH-UA", "\"Chromium\";v=\"148\", \"Google Chrome\";v=\"148\", \"Not/A)Brand\";v=\"99\"")
                .header("Sec-CH-UA-Mobile", "?0")
                .header("Sec-CH-UA-Platform", "\"macOS\"")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .header("X-Border-Enabled", "true")
                .header("X-Cms", "v2")
                .header("X-Content", "desktop")
                .header("X-Ecom-Zonecode", xEcomZoneCode(request))
                .header("X-Lang", xLang(request))
                .header("X-Lat", xLatitude(request))
                .header("X-Lng", xLongitude(request))
                .header("X-Locale", xLocale(request))
                .header("X-Mp-Country", xCountry(request))
                .header("X-Platform", "web")
                .header("X-Rocket-Enabled", "true")
                .header("X-Rocket-Zonecode", xRocketZoneCode(request));
        if (StringUtils.hasText(frontendCookieHeader)) {
            builder.header("Cookie", frontendCookieHeader.trim());
        }
        return builder.GET().build();
    }

    String loadFrontendCookieHeader(String url) {
        if (StringUtils.hasText(configuredFrontendCookieHeader)) {
            return configuredFrontendCookieHeader;
        }
        if (!chromeFrontendCookieEnabled) {
            return null;
        }
        try {
            return frontendCookieHeaderSupplier.get();
        } catch (RuntimeException exception) {
            throw new NoonSearchProviderException(
                    "PROVIDER_UNAVAILABLE",
                    "Noon 前台公开详情缺少浏览器会话：" + shrink(exception.getMessage(), 180),
                    null,
                    url,
                    null
            );
        }
    }

    String buildCustomerCatalogV3SearchUrl(NoonSearchRequest request) {
        String keyword = request == null ? "" : trim(request.getKeyword());
        int limit = request == null || request.getLimit() == null ? 20 : Math.max(1, Math.min(request.getLimit(), 30));
        return customerCatalogV3BaseUrl
                + "/search?q="
                + URLEncoder.encode(keyword == null ? "" : keyword, StandardCharsets.UTF_8)
                + "&limit="
                + limit;
    }

    String buildCustomerCatalogV3DetailUrl(NoonSearchRequest request, String code) {
        return customerCatalogV3BaseUrl
                + "/"
                + URLEncoder.encode(code == null ? "" : code, StandardCharsets.UTF_8)
                + "/p";
    }

    String buildCatalogSearchUrl(NoonSearchRequest request) {
        String path = marketPath(request == null ? null : request.getSiteCode(), request == null ? null : request.getLocale());
        String keyword = request == null ? "" : trim(request.getKeyword());
        int limit = request == null || request.getLimit() == null ? 20 : Math.max(1, Math.min(request.getLimit(), 30));
        return catalogBaseUrl
                + path
                + "/search?q="
                + URLEncoder.encode(keyword == null ? "" : keyword, StandardCharsets.UTF_8)
                + "&limit="
                + limit;
    }

    String buildFrontendHtmlSearchUrl(NoonSearchRequest request) {
        String path = marketPath(request == null ? null : request.getSiteCode(), request == null ? null : request.getLocale());
        String keyword = request == null ? "" : trim(request.getKeyword());
        String encoded = URLEncoder.encode(keyword == null ? "" : keyword, StandardCharsets.UTF_8);
        return publicBaseUrl + path + "/search/?originalQuery=" + encoded + "&q=" + encoded;
    }

    String buildCustomerCatalogV3CurlConfig(NoonSearchRequest request, String url, String frontendCookieHeader) {
        StringBuilder config = new StringBuilder();
        appendCurlOption(config, "url", url);
        appendCurlHeader(config, "accept", "application/json, text/plain, */*");
        appendCurlHeader(config, "accept-language", acceptLanguage(request));
        appendCurlHeader(config, "cache-control", "no-cache, max-age=0, must-revalidate, no-store");
        appendCurlHeader(config, "referer", buildPublicSearchReferer(request));
        appendCurlHeader(config, "sec-ch-ua", "\"Chromium\";v=\"148\", \"Google Chrome\";v=\"148\", \"Not/A)Brand\";v=\"99\"");
        appendCurlHeader(config, "sec-ch-ua-mobile", "?0");
        appendCurlHeader(config, "sec-ch-ua-platform", "\"macOS\"");
        appendCurlHeader(config, "sec-fetch-dest", "empty");
        appendCurlHeader(config, "sec-fetch-mode", "cors");
        appendCurlHeader(config, "sec-fetch-site", "same-origin");
        appendCurlHeader(config, "user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36");
        appendCurlHeader(config, "x-border-enabled", "true");
        appendCurlHeader(config, "x-cms", "v2");
        appendCurlHeader(config, "x-content", "desktop");
        appendCurlHeader(config, "x-ecom-zonecode", xEcomZoneCode(request));
        appendCurlHeader(config, "x-lang", xLang(request));
        appendCurlHeader(config, "x-lat", xLatitude(request));
        appendCurlHeader(config, "x-lng", xLongitude(request));
        appendCurlHeader(config, "x-locale", xLocale(request));
        appendCurlHeader(config, "x-mp-country", xCountry(request));
        appendCurlHeader(config, "x-platform", "web");
        appendCurlHeader(config, "x-rocket-enabled", "true");
        appendCurlHeader(config, "x-rocket-zonecode", xRocketZoneCode(request));
        if (StringUtils.hasText(frontendCookieHeader)) {
            appendCurlHeader(config, "cookie", frontendCookieHeader.trim());
        }
        return config.toString();
    }

    String buildFrontendHtmlSearchCurlConfig(NoonSearchRequest request, String url, String frontendCookieHeader) {
        StringBuilder config = new StringBuilder();
        appendCurlOption(config, "url", url);
        appendCurlHeader(config, "accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        appendCurlHeader(config, "accept-language", acceptLanguage(request));
        appendCurlHeader(config, "cache-control", "no-cache, max-age=0, must-revalidate, no-store");
        appendCurlHeader(config, "user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36");
        if (StringUtils.hasText(frontendCookieHeader)) {
            appendCurlHeader(config, "cookie", frontendCookieHeader.trim());
        }
        return config.toString();
    }

    private CurlResponse executeCurl(String config) throws IOException {
        Process process = new ProcessBuilder(buildCurlCommand()).start();
        byte[] stdout;
        byte[] stderr;
        try (OutputStream stdin = process.getOutputStream();
             InputStream inputStream = process.getInputStream();
             InputStream errorStream = process.getErrorStream()) {
            stdin.write(config.getBytes(StandardCharsets.UTF_8));
            stdin.flush();
            stdin.close();
            stdout = readAllBytes(inputStream);
            stderr = readAllBytes(errorStream);
        }
        try {
            int exitCode = process.waitFor();
            return parseCurlResponse(exitCode, stdout, stderr);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("curl 请求被中断", exception);
        }
    }

    List<String> buildCurlCommand() {
        return List.of(
                "curl",
                "-sS",
                "--compressed",
                "--max-time",
                String.valueOf(Math.max(3L, requestTimeout.getSeconds())),
                "-K",
                "-",
                "-w",
                "\n__NUONO_HTTP_STATUS__:%{http_code}\n"
        );
    }

    private CurlResponse parseCurlResponse(int exitCode, byte[] stdout, byte[] stderr) {
        String output = new String(stdout, StandardCharsets.UTF_8);
        String errorOutput = new String(stderr, StandardCharsets.UTF_8);
        String marker = "\n__NUONO_HTTP_STATUS__:";
        int markerIndex = output.lastIndexOf(marker);
        if (markerIndex < 0) {
            return new CurlResponse(exitCode, 0, output, errorOutput);
        }
        String body = output.substring(0, markerIndex);
        String statusText = output.substring(markerIndex + marker.length()).trim();
        return new CurlResponse(exitCode, parseStatus(statusText), body, errorOutput);
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }

    private NoonSearchProviderException mapUnsuccessfulStatus(int statusCode, String url) {
        if (statusCode == 429) {
            return new NoonSearchProviderException("RATE_LIMITED", "Noon 前台公开搜索返回 HTTP 429。", statusCode, url, null);
        }
        if (statusCode == 403) {
            return new NoonSearchProviderException("BLOCKED_BY_RISK_CONTROL", "Noon 前台公开搜索返回 HTTP 403。", statusCode, url, null);
        }
        if (statusCode >= 500) {
            return new NoonSearchProviderException("PROVIDER_UNAVAILABLE", "Noon 前台公开搜索返回 HTTP " + statusCode + "。", statusCode, url, null);
        }
        return new NoonSearchProviderException("PARSE_FAILED", "Noon 前台公开搜索返回 HTTP " + statusCode + "。", statusCode, url, null);
    }

    private NoonSearchResult findExact(NoonSearchPage page, String code) {
        if (page == null || page.getResults() == null) {
            return null;
        }
        for (NoonSearchResult result : page.getResults()) {
            if (result != null && code.equals(NoonProductCodeSupport.normalize(result.getNoonProductCode()))) {
                return result;
            }
        }
        return null;
    }

    private NoonPublicProductDetailResult partial(String code, NoonSearchResult result, NoonSearchPage page) {
        NoonPublicProductDetailResult mapped = failedOrNotFound(
                ProductPublicDetailSyncStatus.PARTIAL,
                code,
                "PARTIAL_DETAIL",
                "Noon 前台 exact search 只返回基础公开字段，详情字段待后续详情页 adapter 补齐。",
                page == null ? null : page.getProviderHttpStatus(),
                page == null ? null : page.getSourceUrl(),
                page == null ? null : page.getResponseHash(),
                page == null ? null : page.getParserVersion()
        );
        mapped.setNoonProductCode(code);
        mapped.setCodeType(NoonProductCodeSupport.codeType(code).orElse(result.getCodeType()));
        mapped.setTitleEn(trim(result.getTitle()));
        mapped.setBrand(trim(result.getBrand()));
        mapped.setPriceAmount(result.getPriceAmount());
        mapped.setCurrencyCode(trim(result.getCurrencyCode()));
        mapped.setRating(result.getRating());
        mapped.setReviewCount(result.getReviewCount());
        mapped.setMainImageUrl(trim(result.getImageUrl()));
        mapped.setDetailUrl(resolveDetailUrl(result.getCanonicalUrl()));
        mapped.setRawPayloadJson(trim(result.getRawResultJson()));
        return mapped;
    }

    private NoonPublicProductDetailResult notFound(String code, NoonSearchPage page, String failureCode, String message) {
        return failedOrNotFound(
                ProductPublicDetailSyncStatus.NOT_FOUND,
                code,
                failureCode,
                message,
                page == null ? null : page.getProviderHttpStatus(),
                page == null ? null : page.getSourceUrl(),
                page == null ? null : page.getResponseHash(),
                page == null ? null : page.getParserVersion()
        );
    }

    private NoonPublicProductDetailResult failed(
            String code,
            String failureCode,
            String message,
            Integer providerHttpStatus,
            String providerSourceUrl,
            String providerResponseHash,
            String providerParserVersion
    ) {
        return failedOrNotFound(
                ProductPublicDetailSyncStatus.FAILED,
                code,
                failureCode,
                message,
                providerHttpStatus,
                providerSourceUrl,
                providerResponseHash,
                providerParserVersion
        );
    }

    private NoonPublicProductDetailResult failedOrNotFound(
            ProductPublicDetailSyncStatus status,
            String code,
            String failureCode,
            String message,
            Integer providerHttpStatus,
            String providerSourceUrl,
            String providerResponseHash,
            String providerParserVersion
    ) {
        NoonPublicProductDetailResult result = new NoonPublicProductDetailResult();
        result.setStatus(status);
        result.setNoonProductCode(code);
        result.setCodeType(NoonProductCodeSupport.codeType(code).orElse(null));
        result.setFailureCode(failureCode);
        result.setFailureMessage(shrink(message, 1000));
        result.setProviderHttpStatus(providerHttpStatus);
        result.setProviderSourceUrl(providerSourceUrl);
        result.setProviderResponseHash(providerResponseHash);
        result.setProviderParserVersion(providerParserVersion);
        result.setFetchedAt(LocalDateTime.now());
        return result;
    }

    private String buildPublicSearchReferer(NoonSearchRequest request) {
        return buildFrontendHtmlSearchUrl(request);
    }

    private boolean isFrontendHtmlSearchUrl(String url) {
        return StringUtils.hasText(url)
                && url.startsWith(publicBaseUrl)
                && url.contains("/search/");
    }

    private String resolveDetailUrl(String canonicalUrl) {
        String value = trim(canonicalUrl);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        return publicBaseUrl + (value.startsWith("/") ? value : "/" + value);
    }

    private String marketPath(String siteCode, String locale) {
        String site = normalize(siteCode);
        String language = normalize(locale);
        boolean arabic = language != null && language.toLowerCase(Locale.ROOT).contains("ar");
        if ("AE".equals(site) || "UAE".equals(site)) {
            return arabic ? "/uae-ar" : "/uae-en";
        }
        if ("EG".equals(site) || "EGY".equals(site) || "EGYPT".equals(site)) {
            return arabic ? "/egypt-ar" : "/egypt-en";
        }
        return arabic ? "/saudi-ar" : "/saudi-en";
    }

    private String acceptLanguage(NoonSearchRequest request) {
        String locale = request == null ? null : normalize(request.getLocale());
        return locale != null && locale.toLowerCase(Locale.ROOT).contains("ar")
                ? "ar-SA,ar;q=0.9,en;q=0.7"
                : "en-SA,en;q=0.9,ar;q=0.5";
    }

    private String xLang(NoonSearchRequest request) {
        String locale = request == null ? null : normalize(request.getLocale());
        return locale != null && locale.toLowerCase(Locale.ROOT).contains("ar") ? "ar" : "en";
    }

    private String xLocale(NoonSearchRequest request) {
        String site = normalize(request == null ? null : request.getSiteCode());
        String country = "sa";
        if ("AE".equals(site) || "UAE".equals(site)) {
            country = "ae";
        } else if ("EG".equals(site) || "EGY".equals(site) || "EGYPT".equals(site)) {
            country = "eg";
        }
        return xLang(request) + "-" + country;
    }

    private String xCountry(NoonSearchRequest request) {
        String site = normalize(request == null ? null : request.getSiteCode());
        if ("AE".equals(site) || "UAE".equals(site)) {
            return "ae";
        }
        if ("EG".equals(site) || "EGY".equals(site) || "EGYPT".equals(site)) {
            return "eg";
        }
        return "sa";
    }

    private String xEcomZoneCode(NoonSearchRequest request) {
        String country = xCountry(request);
        if ("ae".equals(country)) {
            return "AE-DXB-DXB";
        }
        if ("eg".equals(country)) {
            return "EG-CAI-CAI";
        }
        return "SA-RUH-S17";
    }

    private String xLatitude(NoonSearchRequest request) {
        String country = xCountry(request);
        if ("ae".equals(country)) {
            return "25197872";
        }
        if ("eg".equals(country)) {
            return "30044596";
        }
        return "247311382";
    }

    private String xLongitude(NoonSearchRequest request) {
        String country = xCountry(request);
        if ("ae".equals(country)) {
            return "55174350";
        }
        if ("eg".equals(country)) {
            return "31235844";
        }
        return "466700814";
    }

    private String xRocketZoneCode(NoonSearchRequest request) {
        String country = xCountry(request);
        if ("ae".equals(country)) {
            return "W00000168A";
        }
        if ("eg".equals(country)) {
            return "W00000100A";
        }
        return "W00083496A";
    }

    private JsonNode frontendCatalogProduct(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        JsonNode catalogProduct = root.path("catalog").path("product");
        if (catalogProduct.isObject()) {
            return catalogProduct;
        }
        JsonNode product = root.path("product");
        if (product.isObject()) {
            return product;
        }
        JsonNode catalog = root.path("catalog");
        if (catalog.isObject() && hasFrontendProductCode(catalog)) {
            return catalog;
        }
        return hasFrontendProductCode(root) ? root : null;
    }

    private boolean hasFrontendProductCode(JsonNode node) {
        return StringUtils.hasText(textAny(
                node,
                "sku",
                "sku_config",
                "catalog_sku",
                "noonProductCode",
                "productCode",
                "product_code"
        ));
    }

    private String resolveFrontendBrand(JsonNode brand) {
        if (brand == null || brand.isNull() || brand.isMissingNode()) {
            return null;
        }
        if (brand.isTextual()) {
            return trim(brand.asText());
        }
        return trim(textAny(brand, "name", "name_en", "brand_name", "brandName", "title"));
    }

    private BigDecimal resolveFrontendPrice(JsonNode product) {
        BigDecimal direct = decimalAny(product, "price_amount", "priceAmount", "sale_price", "salePrice", "price");
        if (direct != null) {
            return direct;
        }
        BigDecimal nested = decimalAny(product.path("price"), "amount", "value", "sale_price", "salePrice");
        if (nested != null) {
            return nested;
        }
        JsonNode offer = firstObject(firstObject(firstArray(product, "variants")).path("offers"));
        BigDecimal offerPrice = decimalAny(offer, "price", "sale_price", "salePrice", "amount", "value");
        if (offerPrice != null) {
            return offerPrice;
        }
        return decimalAny(offer.path("price"), "amount", "value");
    }

    private BigDecimal resolveFrontendRating(JsonNode product) {
        BigDecimal direct = decimalAny(product, "rating", "average_rating", "averageRating", "rating_average");
        if (direct != null) {
            return direct;
        }
        return decimalAny(product.path("reviews"), "rating", "average_rating", "averageRating", "value");
    }

    private Integer resolveFrontendReviewCount(JsonNode product) {
        Integer direct = intAny(product, "review_count", "reviews_count", "reviewCount", "reviewsCount", "num_reviews");
        if (direct != null) {
            return direct;
        }
        return intAny(product.path("reviews"), "count", "total", "review_count", "reviews_count");
    }

    private String resolveFrontendImage(JsonNode product) {
        JsonNode imageUrls = firstArray(product, "image_urls", "imageUrls", "images", "image_keys", "imageKeys");
        String image = imageUrls == null || imageUrls.isEmpty() ? null : imageUrls.get(0).asText(null);
        if (!StringUtils.hasText(image)) {
            image = textAny(product, "image_url", "imageUrl", "image", "main_image", "mainImage");
        }
        if (!StringUtils.hasText(image)) {
            return null;
        }
        String trimmed = image.trim();
        if (trimmed.startsWith("//")) {
            return "https:" + trimmed;
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        if (trimmed.startsWith("/")) {
            return publicBaseUrl + trimmed;
        }
        return trimmed;
    }

    private JsonNode firstArray(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isArray()) {
                return value;
            }
        }
        return null;
    }

    private JsonNode firstObject(JsonNode node) {
        if (node != null && node.isArray() && !node.isEmpty() && node.get(0).isObject()) {
            return node.get(0);
        }
        return objectMapper.createObjectNode();
    }

    private String textAny(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isTextual() && StringUtils.hasText(value.asText())) {
                return value.asText();
            }
            if ((value.isNumber() || value.isBoolean()) && StringUtils.hasText(value.asText())) {
                return value.asText();
            }
        }
        return null;
    }

    private BigDecimal decimalAny(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isNumber()) {
                return value.decimalValue();
            }
            if (value.isTextual() && StringUtils.hasText(value.asText())) {
                try {
                    return new BigDecimal(value.asText().trim());
                } catch (NumberFormatException ignored) {
                    // Keep scanning alternate price fields.
                }
            }
        }
        return null;
    }

    private Integer intAny(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.canConvertToInt()) {
                return value.asInt();
            }
            if (value.isTextual() && StringUtils.hasText(value.asText())) {
                try {
                    return Integer.parseInt(value.asText().trim());
                } catch (NumberFormatException ignored) {
                    // Keep scanning alternate review fields.
                }
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String inferCurrencyCode(NoonSearchRequest request) {
        String country = xCountry(request);
        if ("ae".equals(country)) {
            return "AED";
        }
        if ("eg".equals(country)) {
            return "EGP";
        }
        return "SAR";
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception exception) {
            return null;
        }
    }

    private void appendCurlHeader(StringBuilder config, String name, String value) {
        if (!StringUtils.hasText(name) || value == null) {
            return;
        }
        appendCurlOption(config, "header", name + ": " + value);
    }

    private void appendCurlOption(StringBuilder config, String option, String value) {
        config.append(option).append(" = \"").append(escapeCurlConfig(value)).append("\"\n");
    }

    private String escapeCurlConfig(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private int parseStatus(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception exception) {
            return 0;
        }
    }

    private String normalizeBaseUrl(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "https://www.noon.com";
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private String normalizeCatalogBaseUrl(String value) {
        String normalized = StringUtils.hasText(value)
                ? value.trim()
                : "https://noon-catalog.noon.partners/_svc/catalog/api/u";
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private String normalizeCustomerCatalogV3BaseUrl(String value) {
        String normalized = StringUtils.hasText(value)
                ? value.trim()
                : "https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u";
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String shrink(String value, int maxLength) {
        String text = StringUtils.hasText(value) ? value.replaceAll("\\s+", " ").trim() : "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private static final class CurlResponse {
        private final int exitCode;
        private final int statusCode;
        private final String body;
        private final String stderr;

        private CurlResponse(int exitCode, int statusCode, String body, String stderr) {
            this.exitCode = exitCode;
            this.statusCode = statusCode;
            this.body = body == null ? "" : body;
            this.stderr = stderr == null ? "" : stderr;
        }
    }
}
