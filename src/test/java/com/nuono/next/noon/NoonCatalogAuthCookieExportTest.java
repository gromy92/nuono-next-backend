package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import org.junit.jupiter.api.Test;

class NoonCatalogAuthCookieExportTest {

    private static final URI IDENTITY_URI = URI.create("https://login.noon.partners/session");
    private static final URI CATALOG_URI = URI.create("https://noon-catalog.noon.partners/catalog");

    @Test
    void exportsCatalogCookieOnceWhenIdentityAndCatalogReuseCookieName() {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        addCookie(cookieManager, IDENTITY_URI, "_npsid", "identity-session", "login.noon.partners");
        addCookie(cookieManager, CATALOG_URI, "_npsid", "catalog-ready", "noon-catalog.noon.partners");

        NoonCatalogAuthCookieExport cookieExport = new NoonCatalogAuthCookieExport(cookieManager);
        cookieExport.prefer(CATALOG_URI);
        cookieExport.captureRequestCookieHeader(CATALOG_URI);

        String exported = cookieExport.exportAuthCookieHeader();

        assertTrue(exported.contains("_npsid=catalog-ready"), exported);
        assertEquals(1, countOccurrences(exported, "_npsid="));
    }

    private void addCookie(
            CookieManager cookieManager,
            URI uri,
            String name,
            String value,
            String domain
    ) {
        HttpCookie cookie = new HttpCookie(name, value);
        cookie.setDomain(domain);
        cookie.setPath("/");
        cookie.setVersion(0);
        cookieManager.getCookieStore().add(uri, cookie);
    }

    private int countOccurrences(String value, String needle) {
        int count = 0;
        int offset = 0;
        while (value != null && (offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
