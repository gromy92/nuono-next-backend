package com.nuono.next.procurement.aliorder;

import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import com.nuono.next.infrastructure.mapper.Ali1688OpenApiAuthorizationMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Stores permanent access tokens issued by the 1688 enterprise self-use console. */
@Service
public class Ali1688EnterpriseSelfUseTokenService {

    private static final String PROVIDER_CODE = Ali1688HistoricalOrderOAuthService.PROVIDER_CODE;
    private static final String SCOPE_SUMMARY =
            "1688 企业自用永久 Token，仅用于读取历史订单，不会付款、下单或发送供应商消息。";

    private final Ali1688HistoricalOrderMapper mapper;
    private final Ali1688OpenApiAuthorizationMapper authorizationMapper;
    private final Ali1688TokenCipher tokenCipher;

    public Ali1688EnterpriseSelfUseTokenService(
            Ali1688HistoricalOrderMapper mapper,
            Ali1688OpenApiAuthorizationMapper authorizationMapper,
            Ali1688TokenCipher tokenCipher
    ) {
        this.mapper = mapper;
        this.authorizationMapper = authorizationMapper;
        this.tokenCipher = tokenCipher;
    }

    public Ali1688HistoricalOrderAuthorizationView.CompleteView save(
            BusinessAccessContext context,
            Ali1688HistoricalOrderAuthorizationView.EnterpriseSelfUseTokenRequest request
    ) {
        Long ownerUserId = ownerUserId(context);
        Long operatorUserId = context == null ? null : context.getSessionUserId();
        if (ownerUserId == null || operatorUserId == null) {
            throw new IllegalArgumentException("1688 企业自用 Token 缺少账号上下文。");
        }
        String providerAccountId = trimToNull(request == null ? null : request.getProviderAccountId());
        String accessToken = trimToNull(request == null ? null : request.getAccessToken());
        if (providerAccountId == null) {
            throw new IllegalArgumentException("请填写 1688 授权用户名。");
        }
        if (accessToken == null) {
            throw new IllegalArgumentException("请填写 1688 企业自用 accessToken。");
        }

        Ali1688HistoricalOrderAuthorizationRow row = mapper.selectAuthorizationByProviderAccount(
                ownerUserId, PROVIDER_CODE, providerAccountId
        );
        boolean insert = row == null;
        if (insert) {
            row = new Ali1688HistoricalOrderAuthorizationRow();
            row.setId(mapper.nextAuthorizationId());
            row.setOwnerUserId(ownerUserId);
            row.setCreatedBy(operatorUserId);
        }
        row.setProviderCode(PROVIDER_CODE);
        row.setProviderAccountId(providerAccountId);
        row.setAccountLabel(defaultText(request.getAccountLabel(), providerAccountId));
        row.setStatus("authorized");
        row.setScopeSummary(SCOPE_SUMMARY);
        row.setAccessTokenCipher(tokenCipher.encrypt(accessToken));
        row.setRefreshTokenCipher(null);
        row.setExpiresAt(null);
        row.setUpdatedBy(operatorUserId);
        if (insert) {
            mapper.insertAuthorization(row);
        } else {
            authorizationMapper.updateAuthorizationTokens(row);
        }
        bindScope(request, ownerUserId, operatorUserId, row.getId());
        return completion(row);
    }

    private void bindScope(
            Ali1688HistoricalOrderAuthorizationView.EnterpriseSelfUseTokenRequest request,
            Long ownerUserId,
            Long operatorUserId,
            Long authorizationId
    ) {
        String storeCode = trimToNull(request.getStoreCode());
        String siteCode = trimToNull(request.getSiteCode());
        if (storeCode != null) {
            mapper.insertExplicitStoreBinding(
                    mapper.nextOrderStoreBindingId(), ownerUserId, authorizationId, storeCode, siteCode,
                    operatorUserId, "1688 企业自用 Token 绑定到当前店铺范围。"
            );
            return;
        }
        mapper.insertOwnerWideStoreBinding(
                mapper.nextOrderStoreBindingId(), ownerUserId, authorizationId, operatorUserId
        );
    }

    private Ali1688HistoricalOrderAuthorizationView.CompleteView completion(
            Ali1688HistoricalOrderAuthorizationRow row
    ) {
        Ali1688HistoricalOrderAuthorizationView.CompleteView view =
                new Ali1688HistoricalOrderAuthorizationView.CompleteView();
        view.setAuthorizationId(row.getId());
        view.setProviderCode(PROVIDER_CODE);
        view.setProviderAccountId(row.getProviderAccountId());
        view.setAccountLabel(row.getAccountLabel());
        view.setMessage("1688 企业自用 Token 已安全保存，可以返回系统刷新历史订单。");
        return view;
    }

    private Long ownerUserId(BusinessAccessContext context) {
        if (context == null) return null;
        return context.getBusinessOwnerUserId() == null ? context.getSessionUserId() : context.getBusinessOwnerUserId();
    }

    private String defaultText(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
