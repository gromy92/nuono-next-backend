package com.nuono.next.datapull.advertising;

import java.util.Objects;

/** Stable, non-secret advertiser identity returned by the first DP-06 provider call. */
public final class AdvertisingAdvertiser {
    private final String advertiserCode;

    public AdvertisingAdvertiser(String advertiserCode) {
        this.advertiserCode = requireIdentity(advertiserCode, "advertiserCode");
    }

    public String getAdvertiserCode() {
        return advertiserCode;
    }

    static String requireIdentity(String value, String name) {
        String nonNull = Objects.requireNonNull(value, name);
        if (nonNull.isEmpty() || !nonNull.equals(nonNull.trim()) || nonNull.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(name + " must be a stable non-blank identity");
        }
        return nonNull;
    }
}
