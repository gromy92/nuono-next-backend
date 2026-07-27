package com.nuono.next.productlisting;

import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ProductListingRealRunHeartbeat {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ProductListingRealRunHeartbeat.class);
    private static final long INTERVAL_SECONDS = 10L;
    private static final ScheduledExecutorService EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(
                        runnable, "product-listing-real-run-heartbeat");
                thread.setDaemon(true);
                return thread;
            });

    private final ProductListingMapper mapper;

    ProductListingRealRunHeartbeat(ProductListingMapper mapper) {
        this.mapper = mapper;
    }

    ScheduledFuture<?> start(Long taskId, LocalDateTime startedAt) {
        return EXECUTOR.scheduleAtFixedRate(
                () -> heartbeat(taskId, startedAt),
                INTERVAL_SECONDS,
                INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private void heartbeat(Long taskId, LocalDateTime startedAt) {
        try {
            if (mapper.heartbeatRunningRealRunTask(taskId, startedAt) != 1) {
                LOGGER.warn(
                        "Product-listing real-run heartbeat lost its claim: taskId={}",
                        taskId
                );
            }
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Product-listing real-run heartbeat failed: taskId={}",
                    taskId,
                    exception
            );
        }
    }
}
