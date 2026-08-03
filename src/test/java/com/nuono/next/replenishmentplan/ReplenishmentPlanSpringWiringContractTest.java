package com.nuono.next.replenishmentplan;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.nuono.next.salesforecast.SalesForecastRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ReplenishmentPlanSpringWiringContractTest {

    @Test
    void defaultReplenishmentPlanServiceCanWireWhenMultipleConstructorsExist() {
        new ApplicationContextRunner()
                .withBean(SalesForecastRunRepository.class, () -> mock(SalesForecastRunRepository.class))
                .withBean(ReplenishmentPlanRepository.class, () -> mock(ReplenishmentPlanRepository.class))
                .withBean(ReplenishmentPlanConfigResolver.class, () -> mock(ReplenishmentPlanConfigResolver.class))
                .withBean(DefaultReplenishmentPlanService.class)
                .run(context -> {
                    assertTrue(
                            context.getStartupFailure() == null,
                            () -> String.valueOf(context.getStartupFailure())
                    );
                    assertNotNull(context.getBean(DefaultReplenishmentPlanService.class));
                });
    }
}
