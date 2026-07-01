package com.nuono.next.product.noon;

import java.util.Locale;
import javax.net.ssl.SSLHandshakeException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class NoonProductGateway {
    public static final String WHOAMI_URL =
            "https://toolbar.noon.partners/_svc/auth-v1/whoami";
    public static final String PROJECT_LIST_URL =
            "https://toolbar.noon.partners/_svc/mp-partner-platform/project/list";
    public static final String STORE_LIST_URL =
            "https://noon-store.noon.partners/_svc/mp-noon-store/noon/store/list";
    public static final String ZSKU_RETRIEVE_URL =
            "https://noon-catalog.noon.partners/_svc/mp-noon-catalog-api-content/catplat/zsku/retrieve";
    public static final String ZSKU_UPSERT_URL =
            "https://noon-catalog.noon.partners/_svc/mp-noon-catalog-api-content/catplat/zsku/upsert";
    public static final String PRODUCT_UPDATE_URL =
            "https://noon-catalog.noon.partners/_svc/mp-partner-catalog/catalog/product/update";
    public static final String PSKU_UNMAP_URL =
            "https://noon-catalog.noon.partners/_svc/mp-partner-catalog/psku/map";
    public static final String PSKU_DELETE_URL =
            "https://noon-catalog.noon.partners/_svc/mp-partner-catalog/psku/delete";
    public static final String CATPLAT_SKU_CACHE_URL =
            "https://noon-catalog.noon.partners/_svc/mp-noon-catalog-api-content/catplat/sku/cache";
    public static final String GROUP_CURRENT_URL_PREFIX =
            "https://noon-catalog.noon.partners/_svc/mp-noon-catalog-api-content/catalog/v2/group/";
    public static final String GROUP_DETAIL_URL =
            "https://noon-catalog.noon.partners/_svc/mp-noon-catalog-api-content/catplat/group/get";
    public static final String GROUP_LIST_URL =
            "https://noon-catalog.noon.partners/_svc/mp-noon-catalog-api-content/catalog/groups/list";
    public static final String GROUP_UPSERT_URL =
            "https://noon-catalog.noon.partners/_svc/mp-noon-catalog-api-content/catalog/group/upsert";
    public static final String VARIANT_INFO_URL =
            "https://noon-catalog.noon.partners/_svc/mp-noon-catalog-api-content/catplat/variants/information";
    public static final String PRICING_INFO_URL =
            "https://noon-catalog.noon.partners/_svc/mp-pricing-api/pricing/info";
    public static final String STOCK_INFO_URL =
            "https://noon-catalog.noon.partners/_svc/mp-noon-catalog-api-rocket/offer/stock/noon";
    public static final String OFFER_LIST_NOON_URL =
            "https://noon-catalog.noon.partners/_svc/mp-noon-catalog-api-rocket/offer/list/noon";
    public static final String OFFER_UPSERT_URL =
            "https://noon-catalog.noon.partners/_svc/mp-partner-catalog/offer/upsert";
    public static final String OFFER_MGMT_PRICE_UPSERT_URL =
            "https://noon-catalog.noon.partners/_vs/mp/mp-noon-catalog-api-offermgmt/offer/upsert/price";
    public static final String OFFER_MGMT_ID_WARRANTY_UPSERT_URL =
            "https://noon-catalog.noon.partners/_vs/mp/mp-noon-catalog-api-offermgmt/offer/upsert/id_warranty";
    public static final String OFFER_MGMT_OFFER_NOTE_UPSERT_URL =
            "https://noon-catalog.noon.partners/_vs/mp/mp-noon-catalog-api-offermgmt/offer/upsert/offer_note";
    public static final String OFFER_MGMT_IS_ACTIVE_UPSERT_URL =
            "https://noon-catalog.noon.partners/_vs/mp/mp-noon-catalog-api-offermgmt/offer/upsert/is_active";

    public NoonProductError classify(Throwable throwable) {
        String details = throwableDetails(throwable);
        String normalized = details.toLowerCase(Locale.ROOT);
        if (containsAny(
                normalized,
                "invalid username or password",
                "password validate",
                "invalid password",
                "bad credentials"
        )) {
            return new NoonProductError(
                    NoonProductErrorCode.NOON_CREDENTIAL_INVALID,
                    false,
                    "Noon 账号或密码错误，请检查店铺管理中的 Noon 登录账号。"
            );
        }
        if (containsAny(
                normalized,
                "pkix path building failed",
                "suncertpathbuilderexception",
                "unable to find valid certification path",
                "certificate_unknown",
                "sslhandshakeexception"
        ) || hasCause(throwable, SSLHandshakeException.class)) {
            return new NoonProductError(
                    NoonProductErrorCode.NOON_TLS_CERTIFICATE_FAILURE,
                    false,
                    "请求 Noon 失败：TLS 证书校验失败，请检查运行环境证书链或代理证书配置。"
            );
        }
        if (containsAny(
                normalized,
                "project.list 未返回目标项目",
                "project list missing target",
                "target project missing",
                "noon 项目列表未返回目标项目"
        )) {
            return new NoonProductError(
                    NoonProductErrorCode.NOON_PROJECT_SCOPE_MISSING,
                    false,
                    "Noon project.list 未返回目标项目，请检查当前账号是否有该项目权限。"
            );
        }
        if (containsAny(
                normalized,
                "http 429",
                "status 429",
                "too many requests",
                "rate limit",
                "rate_limit",
                "限流"
        )) {
            return new NoonProductError(
                    NoonProductErrorCode.NOON_RATE_LIMITED,
                    true,
                    "Noon 请求被限流，请稍后重试。"
            );
        }
        return new NoonProductError(
                NoonProductErrorCode.NOON_REQUEST_FAILED,
                false,
                "请求 Noon 失败：" + firstNonBlank(rootMessage(throwable), "未知错误")
        );
    }

    public NoonProductError classifyHttpFailure(int statusCode, String message) {
        if (statusCode == 429) {
            return new NoonProductError(
                    NoonProductErrorCode.NOON_RATE_LIMITED,
                    true,
                    "Noon 请求被限流，请稍后重试。"
            );
        }
        return classify(new IllegalStateException("HTTP " + statusCode + " " + firstNonBlank(message, "")));
    }

    public NoonProductException toException(Throwable throwable) {
        return new NoonProductException(classify(throwable), throwable);
    }

    private boolean containsAny(String value, String... candidates) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String throwableDetails(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())) {
                builder.append(' ').append(current.getMessage());
            }
            builder.append(' ').append(current.getClass().getSimpleName());
            current = current.getCause();
        }
        return builder.toString();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        String message = null;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())) {
                message = current.getMessage();
            }
            current = current.getCause();
        }
        return message;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }
}
