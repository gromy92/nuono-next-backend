package com.nuono.next.productlisting;

import com.nuono.next.noonpull.NoonPullStoreBinding;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

final class ProductListingNoonHeaders {
    private ProductListingNoonHeaders() {
    }

    static Map<String, String> writeHeaders(NoonPullStoreBinding binding) {
        String siteCode = upper(binding.getSiteCode());
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-project", binding.getProjectCode());
        headers.put("x-locale", "en-" + siteCode);
        headers.put("Country-Code", siteCode);
        headers.put("Id-Partner", binding.getPartnerId());
        return headers;
    }

    static String upper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }
}
