package com.nuono.next.noonsync;

public class NoonProductExplicitRefreshPolicy {

    private final boolean confirmationRequired;
    private final String mergePolicy;
    private final NoonProductRefreshDraftHandling draftHandling;
    private final boolean externalWriteAllowed;

    public NoonProductExplicitRefreshPolicy(
            boolean confirmationRequired,
            String mergePolicy,
            NoonProductRefreshDraftHandling draftHandling,
            boolean externalWriteAllowed
    ) {
        this.confirmationRequired = confirmationRequired;
        this.mergePolicy = mergePolicy;
        this.draftHandling = draftHandling;
        this.externalWriteAllowed = externalWriteAllowed;
    }

    public boolean isConfirmationRequired() {
        return confirmationRequired;
    }

    public String getMergePolicy() {
        return mergePolicy;
    }

    public NoonProductRefreshDraftHandling getDraftHandling() {
        return draftHandling;
    }

    public boolean isExternalWriteAllowed() {
        return externalWriteAllowed;
    }
}
