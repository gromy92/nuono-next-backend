package com.nuono.next.procurement.aliorder;

import java.time.LocalDateTime;

public class Ali1688HistoricalOrderAuthorizationRow {
    private Long id;
    private Long ownerUserId;
    private String providerCode;
    private String providerAccountId;
    private String accountLabel;
    private String status;
    private String scopeSummary;
    private String accessTokenCipher;
    private String refreshTokenCipher;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private Long createdBy;
    private Long updatedBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public void setProviderCode(String providerCode) {
        this.providerCode = providerCode;
    }

    public String getProviderAccountId() {
        return providerAccountId;
    }

    public void setProviderAccountId(String providerAccountId) {
        this.providerAccountId = providerAccountId;
    }

    public String getAccountLabel() {
        return accountLabel;
    }

    public void setAccountLabel(String accountLabel) {
        this.accountLabel = accountLabel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getScopeSummary() {
        return scopeSummary;
    }

    public void setScopeSummary(String scopeSummary) {
        this.scopeSummary = scopeSummary;
    }

    public String getAccessTokenCipher() {
        return accessTokenCipher;
    }

    public void setAccessTokenCipher(String accessTokenCipher) {
        this.accessTokenCipher = accessTokenCipher;
    }

    public String getRefreshTokenCipher() {
        return refreshTokenCipher;
    }

    public void setRefreshTokenCipher(String refreshTokenCipher) {
        this.refreshTokenCipher = refreshTokenCipher;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }
}
