package com.nuono.next.productselection;

import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class ProductSelectionSourceCollectionCollector {

    private static final Set<String> SUPPORTED_PLATFORMS = Set.of("Noon", "Amazon", "SHEIN");

    private final List<ProductSelectionMarketplaceSourceCollector> collectors;
    private final ProductSelectionSourceCollectionHtmlParser htmlParser;

    public ProductSelectionSourceCollectionCollector(
            List<ProductSelectionMarketplaceSourceCollector> collectors,
            ProductSelectionSourceCollectionHtmlParser htmlParser
    ) {
        this.collectors = collectors;
        this.htmlParser = htmlParser;
    }

    public ProductSelectionSourceCollectionResult collect(ProductSelectionSourceCollectionRow row) {
        String pageUrl = htmlParser.firstText(row.getPageUrl(), row.getSourceUrl());
        if (!StringUtils.hasText(pageUrl)) {
            throw new IllegalArgumentException("请先填写 Noon、Amazon 或 SHEIN 商品页链接。");
        }
        String normalizedUrl = htmlParser.normalizeUrl(pageUrl);
        String platform = htmlParser.inferPlatform(htmlParser.firstText(row.getSourcePlatform(), normalizedUrl));
        if (!isSupportedPlatform(platform)) {
            throw new IllegalArgumentException("当前自动采集只支持 Noon、Amazon 和 SHEIN 商品链接。");
        }

        ProductSelectionSourceCollectionRow normalizedRow = new ProductSelectionSourceCollectionRow();
        normalizedRow.setId(row.getId());
        normalizedRow.setSourcePlatform(platform);
        normalizedRow.setSourceUrl(normalizedUrl);
        normalizedRow.setPageUrl(normalizedUrl);
        for (ProductSelectionMarketplaceSourceCollector collector : collectors) {
            if (collector.supports(platform)) {
                return collector.collect(normalizedRow);
            }
        }
        throw new IllegalStateException("当前平台没有可用采集器：" + platform);
    }

    ProductSelectionSourceCollectionResult collectHtml(String html, String pageUrl, String platform) {
        return htmlParser.collectHtml(html, pageUrl, platform);
    }

    private boolean isSupportedPlatform(String platform) {
        return SUPPORTED_PLATFORMS.stream().anyMatch(item -> item.equalsIgnoreCase(platform));
    }
}
