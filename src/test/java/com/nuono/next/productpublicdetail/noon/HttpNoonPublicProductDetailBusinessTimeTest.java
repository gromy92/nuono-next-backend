package com.nuono.next.productpublicdetail.noon;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.competitoranalysis.noon.NoonFrontendSearchPageParser;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("default-time-zone")
class HttpNoonPublicProductDetailBusinessTimeTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    void recordsFetchedAtInShanghaiWhenJvmDefaultIsUtc() {
        TimeZone previousZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            HttpNoonPublicProductDetailAdapter adapter = new HttpNoonPublicProductDetailAdapter(
                    new NoonFrontendSearchPageParser(new ObjectMapper()),
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(3),
                    "https://www.noon.com",
                    "https://www.noon.com/_vs/nc/mp-customer-catalog-api/api/v3/u",
                    "",
                    false
            );
            LocalDateTime before = LocalDateTime.now(BUSINESS_ZONE).minusSeconds(1);

            NoonPublicProductDetailResult result = adapter.fetch(
                    NoonPublicProductDetailRequest.builder()
                            .siteCode("SA")
                            .locale("en-SA")
                            .noonProductCode("invalid")
                            .build()
            );

            LocalDateTime after = LocalDateTime.now(BUSINESS_ZONE).plusSeconds(1);
            assertFalse(result.getFetchedAt().isBefore(before));
            assertFalse(result.getFetchedAt().isAfter(after));
        } finally {
            TimeZone.setDefault(previousZone);
        }
    }
}
