package com.nuono.next.noonpull;

import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.store.StoreSyncOwnerContext;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NoonPullStoreBindingResolver {
    private final StoreSyncMapper storeSyncMapper;
    private final String configuredMerchantEmail;
    private final boolean configuredMerchantEmailLoginAvailable;

    @Autowired
    public NoonPullStoreBindingResolver(
            StoreSyncMapper storeSyncMapper,
            @Value("${nuono.noon.auth.email-otp.email:}") String configuredMerchantEmail,
            @Value("${nuono.noon.auth.email-otp.mail-auth-code:}") String configuredMerchantMailAuthCode
    ) {
        this.storeSyncMapper = storeSyncMapper;
        this.configuredMerchantEmail = normalize(configuredMerchantEmail);
        this.configuredMerchantEmailLoginAvailable = StringUtils.hasText(normalize(configuredMerchantEmail))
                && StringUtils.hasText(normalize(configuredMerchantMailAuthCode));
    }

    public NoonPullStoreBindingResolver(StoreSyncMapper storeSyncMapper) {
        this(storeSyncMapper, null, null);
    }

    public NoonPullStoreBinding resolve(NoonInterfacePullRequest request) {
        if (request == null) {
            throw providerNotConfigured("missing interface pull request");
        }
        return resolve(request.getOwnerUserId(), request.getStoreCode(), request.getSiteCode());
    }

    public NoonPullStoreBinding resolve(NoonReportPullRequest request) {
        if (request == null) {
            throw providerNotConfigured("missing report pull request");
        }
        return resolve(request.getOwnerUserId(), request.getStoreCode(), request.getSiteCode());
    }

    private NoonPullStoreBinding resolve(Long ownerUserId, String requestedStoreCode, String requestedSiteCode) {
        if (ownerUserId == null || !StringUtils.hasText(requestedStoreCode)) {
            throw providerNotConfigured("missing owner or store scope");
        }
        StoreSyncStoreRecord store = firstNonNull(
                storeSyncMapper.selectOwnerStore(ownerUserId, requestedStoreCode),
                storeSyncMapper.selectOwnerProjectionStore(ownerUserId, requestedStoreCode),
                storeSyncMapper.selectOwnerProject(ownerUserId, requestedStoreCode)
        );
        if (store == null) {
            throw providerNotConfigured("Noon store binding is missing for " + requestedStoreCode);
        }

        StoreSyncOwnerContext owner = storeSyncMapper.selectOwnerContext(ownerUserId);
        String projectCode = firstNonBlank(store.getProjectCode(), deriveProjectCode(store.getNoonPartnerId()));
        String storeCode = preferSiteStoreCode(store.getStoreCode(), requestedStoreCode);
        String siteCode = firstNonBlank(requestedSiteCode, store.getSite(), deriveSiteCode(storeCode));
        String partnerId = firstNonBlank(
                store.getNoonPartnerId(),
                owner == null ? null : owner.getNoonPartnerId(),
                derivePartnerId(projectCode)
        );
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

        requireText(projectCode, "missing Noon projectCode");
        requireText(storeCode, "missing Noon storeCode");
        requireText(siteCode, "missing Noon siteCode");
        requireText(partnerId, "missing Noon partnerId");
        requireText(noonUser, "missing Noon login account");
        requireNoonLoginCredential(noonEmailAuthCode, noonPassword, configuredMerchantEmailLoginAvailable);

        return new NoonPullStoreBinding(
                ownerUserId,
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
        if (values == null) {
            return null;
        }
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

    private static void requireText(String value, String reason) {
        if (!StringUtils.hasText(value)) {
            throw providerNotConfigured(reason);
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
            throw providerNotConfigured("missing Noon email auth code or legacy login password");
        }
    }

    private static NoonInterfacePullException providerNotConfigured(String reason) {
        return new NoonInterfacePullException("provider not configured: " + reason);
    }
}
