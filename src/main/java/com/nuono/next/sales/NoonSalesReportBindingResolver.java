package com.nuono.next.sales;

import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.store.StoreSyncOwnerContext;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NoonSalesReportBindingResolver {

    private final StoreSyncMapper storeSyncMapper;
    private final String configuredMerchantEmail;
    private final boolean configuredMerchantEmailLoginAvailable;

    @Autowired
    public NoonSalesReportBindingResolver(
            StoreSyncMapper storeSyncMapper,
            @Value("${nuono.noon.auth.email-otp.email:}") String configuredMerchantEmail,
            @Value("${nuono.noon.auth.email-otp.mail-auth-code:}") String configuredMerchantMailAuthCode
    ) {
        this.storeSyncMapper = storeSyncMapper;
        this.configuredMerchantEmail = normalize(configuredMerchantEmail);
        this.configuredMerchantEmailLoginAvailable = StringUtils.hasText(normalize(configuredMerchantEmail))
                && StringUtils.hasText(normalize(configuredMerchantMailAuthCode));
    }

    public NoonSalesReportBindingResolver(StoreSyncMapper storeSyncMapper) {
        this(storeSyncMapper, null, null);
    }

    public NoonSalesReportBinding resolve(NoonSalesReportRequest request) {
        if (request == null || request.getOwnerUserId() == null || !StringUtils.hasText(request.getStoreCode())) {
            throw new IllegalArgumentException("缺少 Noon 销量报表同步的老板或店铺上下文。");
        }
        StoreSyncStoreRecord store = firstNonNull(
                storeSyncMapper.selectOwnerStore(request.getOwnerUserId(), request.getStoreCode()),
                storeSyncMapper.selectOwnerProjectionStore(request.getOwnerUserId(), request.getStoreCode()),
                storeSyncMapper.selectOwnerProject(request.getOwnerUserId(), request.getStoreCode())
        );
        if (store == null) {
            throw new IllegalStateException("未找到 Noon 销量报表同步店铺绑定：" + request.getStoreCode());
        }

        StoreSyncOwnerContext owner = storeSyncMapper.selectOwnerContext(request.getOwnerUserId());
        String projectCode = firstNonBlank(store.getProjectCode(), deriveProjectCode(store.getNoonPartnerId()));
        String storeCode = preferSiteStoreCode(store.getStoreCode(), request.getStoreCode());
        String siteCode = firstNonBlank(request.getSiteCode(), store.getSite(), deriveSiteCode(storeCode));
        String partnerId = firstNonBlank(store.getNoonPartnerId(), owner == null ? null : owner.getNoonPartnerId(), derivePartnerId(projectCode));
        String noonUser = firstNonBlank(
                store.getNoonPartnerUser(),
                store.getNoonPartnerProjectUser(),
                owner == null ? null : owner.getNoonPartnerUser(),
                owner == null ? null : owner.getNoonPartnerProjectUser(),
                configuredMerchantEmailLoginAvailable ? configuredMerchantEmail : null
        );
        String noonEmailAuthCode = firstNonBlank(
                store.getNoonPartnerMailAuthCode(),
                owner == null ? null : owner.getNoonPartnerMailAuthCode()
        );
        String noonPassword = firstNonBlank(
                store.getNoonPartnerPwd(),
                owner == null ? null : owner.getNoonPartnerPwd()
        );
        String persistedCookie = firstNonBlank(store.getNoonPartnerCookie());

        requireText(projectCode, "当前店铺缺少 Noon projectCode，不能同步销量报表。");
        requireText(storeCode, "当前店铺缺少 Noon storeCode，不能同步销量报表。");
        requireText(siteCode, "当前店铺缺少 Noon siteCode，不能同步销量报表。");
        requireText(partnerId, "当前店铺缺少 Noon partnerId，不能同步销量报表。");
        requireText(noonUser, "当前店铺缺少 Noon 登录账号，不能同步销量报表。");
        requireNoonLoginCredential(noonEmailAuthCode, noonPassword, configuredMerchantEmailLoginAvailable);

        return new NoonSalesReportBinding(
                request.getOwnerUserId(),
                request.getLogicalStoreId(),
                projectCode,
                storeCode,
                siteCode.toUpperCase(Locale.ROOT),
                partnerId,
                noonUser,
                noonPassword,
                noonEmailAuthCode,
                persistedCookie
        );
    }

    private static StoreSyncStoreRecord firstNonNull(StoreSyncStoreRecord... records) {
        for (StoreSyncStoreRecord record : records) {
            if (record != null) {
                return record;
            }
        }
        return null;
    }

    private static String preferSiteStoreCode(String boundStoreCode, String requestedStoreCode) {
        if (isProjectCode(boundStoreCode) && StringUtils.hasText(requestedStoreCode) && !isProjectCode(requestedStoreCode)) {
            return normalize(requestedStoreCode);
        }
        return firstNonBlank(boundStoreCode, requestedStoreCode);
    }

    private static boolean isProjectCode(String value) {
        return StringUtils.hasText(value) && normalize(value).toUpperCase(Locale.ROOT).startsWith("PRJ");
    }

    private static String deriveProjectCode(String partnerId) {
        return StringUtils.hasText(partnerId) ? "PRJ" + normalize(partnerId) : null;
    }

    private static String derivePartnerId(String projectCode) {
        if (!StringUtils.hasText(projectCode)) {
            return null;
        }
        String normalized = normalize(projectCode);
        return normalized.toUpperCase(Locale.ROOT).startsWith("PRJ") ? normalized.substring(3) : normalized;
    }

    private static String deriveSiteCode(String storeCode) {
        if (!StringUtils.hasText(storeCode)) {
            return null;
        }
        String normalized = normalize(storeCode).toUpperCase(Locale.ROOT);
        if (normalized.endsWith("-NAE")) {
            return "AE";
        }
        if (normalized.endsWith("-NSA") || normalized.endsWith("-SAU")) {
            return "SA";
        }
        int dashIndex = normalized.lastIndexOf('-');
        return dashIndex >= 0 && dashIndex + 1 < normalized.length()
                ? normalized.substring(dashIndex + 1)
                : null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (StringUtils.hasText(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private static void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(message);
        }
    }

    private static void requireNoonLoginCredential(
            String noonEmailAuthCode,
            String noonPassword,
            boolean configuredMerchantEmailLoginAvailable
    ) {
        if (!StringUtils.hasText(noonEmailAuthCode)
                && !configuredMerchantEmailLoginAvailable
                && !StringUtils.hasText(noonPassword)) {
            throw new IllegalStateException("当前店铺缺少 Noon 邮箱授权码或历史登录密码，不能同步销量报表。");
        }
    }
}
