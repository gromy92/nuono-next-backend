package com.nuono.next.product;

import com.nuono.next.infrastructure.mapper.ProductImageProfileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("local-db")
class ProductImagePublishRecoveryScheduler {
    private static final Logger log =
            LoggerFactory.getLogger(ProductImagePublishRecoveryScheduler.class);
    private final ProductImageProfileMapper mapper;

    @Value("${nuono.product-management.image-publish.stale-minutes:30}")
    private int staleMinutes;

    ProductImagePublishRecoveryScheduler(ProductImageProfileMapper mapper) {
        this.mapper = mapper;
    }

    @Scheduled(
            initialDelayString = "${nuono.product-management.image-publish.recovery-initial-delay-ms:60000}",
            fixedDelayString = "${nuono.product-management.image-publish.recovery-fixed-delay-ms:60000}"
    )
    void recover() {
        int recovered = mapper.failStalePublishingSuites(Math.max(5, staleMinutes), 0L);
        if (recovered > 0) {
            log.warn("product-image recovered stale PUBLISHING suites count={}", recovered);
        }
    }
}
