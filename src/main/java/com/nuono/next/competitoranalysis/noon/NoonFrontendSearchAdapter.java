package com.nuono.next.competitoranalysis.noon;

public interface NoonFrontendSearchAdapter {
    NoonSearchPage search(NoonSearchRequest request);

    /** One provider request only; runtime callers use this bounded seam. */
    default NoonSearchPage searchPage(NoonSearchRequest request) {
        return search(request);
    }
}
