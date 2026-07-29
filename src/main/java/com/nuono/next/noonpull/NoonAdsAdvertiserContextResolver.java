package com.nuono.next.noonpull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

final class NoonAdsAdvertiserContextResolver {
    private final ObjectMapper objectMapper;
    private final String advertiserAccountsUrl;

    NoonAdsAdvertiserContextResolver(ObjectMapper objectMapper, String advertiserAccountsUrl) {
        this.objectMapper = objectMapper;
        this.advertiserAccountsUrl = advertiserAccountsUrl;
    }

    NoonAdsAdvertiserContext resolve(
            NoonPullGatewaySession session,
            NoonPullStoreBinding binding
    ) {
        byte[] responseBytes = session.getBytes(
                advertiserAccountsUrl,
                false,
                headers(binding, null, "application/json, text/plain, */*")
        );
        final JsonNode accounts;
        try {
            accounts = objectMapper.readTree(responseBytes);
        } catch (Exception exception) {
            throw new NoonInterfacePullException(
                    "mapping failed: unable to parse Noon Ads advertiser accounts",
                    exception
            );
        }
        if (accounts == null || !accounts.isArray()) {
            throw mismatch(binding, "advertiser accounts response is not an array");
        }
        Map<String, NoonAdsAdvertiserContext> matches = new LinkedHashMap<>();
        for (JsonNode account : accounts) {
            if (!matchesPartner(account, binding.getPartnerId()) || isDisabled(account)) {
                continue;
            }
            String advertiserCode = text(account, "advertiserCode");
            if (StringUtils.hasText(advertiserCode)) {
                matches.putIfAbsent(
                        advertiserCode,
                        new NoonAdsAdvertiserContext(advertiserCode)
                );
            }
        }
        if (matches.size() != 1) {
            throw mismatch(
                    binding,
                    "expected exactly one active advertiser account but found " + matches.size()
            );
        }
        return matches.values().iterator().next();
    }

    Map<String, String> headers(
            NoonPullStoreBinding binding,
            NoonAdsAdvertiserContext advertiserContext,
            String accept
    ) {
        String site = binding.getSiteCode() == null
                ? "ae"
                : binding.getSiteCode().toLowerCase(Locale.ROOT);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", accept);
        headers.put("X-Project", binding.getProjectCode());
        headers.put("x-content", "desktop");
        headers.put("x-locale", "en-" + site);
        headers.put("x-cms", "v3");
        headers.put("x-platform", "web");
        headers.put("x-mp", "noon");
        headers.put("x-border-enabled", "true");
        headers.put("x-seller-view", "true");
        headers.put("x-id-advertiser", binding.getPartnerId());
        if (advertiserContext != null
                && StringUtils.hasText(advertiserContext.getAdvertiserCode())) {
            headers.put(
                    "x-advertiser-codes",
                    advertiserContext.getAdvertiserCode()
            );
        }
        return headers;
    }

    private boolean matchesPartner(JsonNode account, String partnerId) {
        if (account == null || !StringUtils.hasText(partnerId)) {
            return false;
        }
        String normalizedPartnerId = partnerId.trim();
        String accountPartnerId = text(account, "idPartner");
        String partnerCode = text(account, "partnerCode");
        return normalizedPartnerId.equals(accountPartnerId)
                || ("p_" + normalizedPartnerId).equalsIgnoreCase(partnerCode);
    }

    private boolean isDisabled(JsonNode account) {
        return falseFlag(account, "isActive") || falseFlag(account, "isEnabled");
    }

    private boolean falseFlag(JsonNode account, String field) {
        JsonNode value = account == null ? null : account.path(field);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return false;
        }
        if (value.isBoolean()) {
            return !value.asBoolean();
        }
        if (value.isNumber()) {
            return value.asInt() == 0;
        }
        String flag = value.asText("").trim();
        return "0".equals(flag) || "false".equalsIgnoreCase(flag);
    }

    private NoonInterfacePullException mismatch(
            NoonPullStoreBinding binding,
            String reason
    ) {
        return new NoonInterfacePullException(
                "ads advertiser context mismatch: projectCode="
                        + (binding == null ? null : binding.getProjectCode())
                        + " partnerId="
                        + (binding == null ? null : binding.getPartnerId())
                        + "; "
                        + reason
        );
    }

    private String text(JsonNode node, String field) {
        if (node == null || !StringUtils.hasText(field)) {
            return "";
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
    }
}
