package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class ProductActiveStateReconciliationSchedulerContractTest {

    @Test
    void schedulerIsResumableAndRequiresExplicitProductionEnablement() throws Exception {
        Method method = ProductActiveStateReconciliationScheduler.class.getMethod(
                "reconcileUnknownActiveStates"
        );
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        String application = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(scheduled.fixedDelayString()).isEqualTo(
                "${nuono.product-management.active-state-reconciliation.scheduler.fixed-delay-ms:300000}"
        );
        assertThat(application).contains(
                "NUONO_PRODUCT_ACTIVE_STATE_RECONCILIATION_SCHEDULER_ENABLED:false"
        );
        assertThat(application).contains(
                "NUONO_PRODUCT_ACTIVE_STATE_RECONCILIATION_SCHEDULER_MAX_SCOPES_PER_TICK:4"
        );
        assertThat(application).contains(
                "NUONO_PRODUCT_ACTIVE_STATE_RECONCILIATION_MAX_ITEMS_PER_SCOPE:10"
        );
        assertThat(application).contains(
                "NUONO_PRODUCT_ACTIVE_STATE_RECONCILIATION_STALE_AFTER_MINUTES:60"
        );
    }
}
