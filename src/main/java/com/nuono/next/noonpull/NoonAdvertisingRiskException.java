package com.nuono.next.noonpull;

/** Verified Noon Ad Manager risk-control response marker. */
final class NoonAdvertisingRiskException extends RuntimeException {

    NoonAdvertisingRiskException() {
        super("Noon Ads provider returned a verified risk marker");
    }
}
