package com.nuono.next.procurement.aliorder;

/** Locked DP-10 source identity, including a user-owned deletion tombstone. */
public final class Ali1688Dp10OrderHeaderIdentityRow {
    private Long id;
    private Long authorizationId;
    private String orderNaturalKey;
    private String providerCode;
    private String providerAccountId;
    private String providerOrderNo;
    private Boolean deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAuthorizationId() {
        return authorizationId;
    }

    public void setAuthorizationId(Long authorizationId) {
        this.authorizationId = authorizationId;
    }

    public String getOrderNaturalKey() {
        return orderNaturalKey;
    }

    public void setOrderNaturalKey(String orderNaturalKey) {
        this.orderNaturalKey = orderNaturalKey;
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

    public String getProviderOrderNo() {
        return providerOrderNo;
    }

    public void setProviderOrderNo(String providerOrderNo) {
        this.providerOrderNo = providerOrderNo;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }
}
