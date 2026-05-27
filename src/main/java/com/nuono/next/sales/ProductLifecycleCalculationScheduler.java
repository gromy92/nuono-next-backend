package com.nuono.next.sales;

import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProductLifecycleCalculationScheduler {

    private final ProductLifecycleSchedulePolicy schedulePolicy;
    private final ProductLifecycleCalculationSource source;
    private final ProductLifecycleCalculationJobService jobService;

    @Value("${nuono.sales.lifecycle.scheduler.enabled:false}")
    private boolean enabled;

    public ProductLifecycleCalculationScheduler(
            ProductLifecycleSchedulePolicy schedulePolicy,
            ProductLifecycleCalculationSource source,
            ProductLifecycleCalculationJobService jobService
    ) {
        this.schedulePolicy = schedulePolicy;
        this.source = source;
        this.jobService = jobService;
    }

    @Scheduled(
            initialDelayString = "${nuono.sales.lifecycle.scheduler.initial-delay-ms:60000}",
            fixedDelayString = "${nuono.sales.lifecycle.scheduler.fixed-delay-ms:259200000}"
    )
    public void runScheduledLifecycleCalculation() {
        if (!enabled) {
            return;
        }
        LocalDate anchorDate = schedulePolicy.defaultAnchorDate();
        for (ProductLifecycleCalculationScope scope : source.listScheduledScopes(anchorDate)) {
            jobService.run(scope);
        }
    }
}
