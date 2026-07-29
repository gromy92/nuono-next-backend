package com.nuono.next.noonpull;

final class NoonAdsAdvertiserContext {
    private final String advertiserCode;

    NoonAdsAdvertiserContext(String advertiserCode) {
        this.advertiserCode = advertiserCode;
    }

    String getAdvertiserCode() {
        return advertiserCode;
    }
}
