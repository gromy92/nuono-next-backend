package com.nuono.next.productselection;

import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("local-db")
@Order(100)
public class PublicPageSourceCollector implements ProductSelectionMarketplaceSourceCollector {

    private final ProductSelectionSourceCollectionHtmlParser htmlParser;

    public PublicPageSourceCollector(ProductSelectionSourceCollectionHtmlParser htmlParser) {
        this.htmlParser = htmlParser;
    }

    @Override
    public boolean supports(String platform) {
        return "SHEIN".equalsIgnoreCase(platform);
    }

    @Override
    public ProductSelectionSourceCollectionResult collect(ProductSelectionSourceCollectionRow row) {
        return htmlParser.collectUrl(htmlParser.firstText(row.getPageUrl(), row.getSourceUrl()), row.getSourcePlatform());
    }
}
