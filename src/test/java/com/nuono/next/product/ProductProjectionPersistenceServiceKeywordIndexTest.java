package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductProjectionPersistenceServiceKeywordIndexTest {

    @Test
    void publishedKeyContentHistoryCallsProductKeywordTitleIndexerWithInsertedHistoryId() throws Exception {
        String source = Files.readString(Path.of(
                "src",
                "main",
                "java",
                "com",
                "nuono",
                "next",
                "product",
                "ProductProjectionPersistenceService.java"
        ));

        assertThat(source)
                .contains("productKeywordTitleIndexer")
                .contains("indexPublishedHistory")
                .contains("Long historyId = productManagementMapper.nextProductKeyContentHistoryId()")
                .contains("summary = candidate.getSummary()");
    }
}
