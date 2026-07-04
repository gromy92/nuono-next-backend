package com.nuono.next.noonads;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.nuono.next.infrastructure.mapper.NoonAdvertisingMapper;
import com.nuono.next.noonpull.RealNoonAdvertisingReportProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

class NoonAdvertisingSpringWiringContractTest {

    @Test
    void noonAdvertisingComponentsAreSpringBeans() {
        assertNotNull(NoonAdvertisingAnalyticsService.class.getAnnotation(Service.class));
        assertNotNull(NoonAdvertisingImportService.class.getAnnotation(Service.class));
        assertNotNull(NoonAdvertisingReportAdapter.class.getAnnotation(Service.class));
        assertNotNull(MyBatisNoonAdvertisingRepository.class.getAnnotation(Repository.class));
    }

    @Test
    void realNoonAdvertisingReportProviderIsConditionalSpringComponent() {
        assertNotNull(RealNoonAdvertisingReportProvider.class.getAnnotation(Component.class));
    }

    @Test
    void noonAdvertisingServicesCanWireWithMyBatisRepository() {
        new ApplicationContextRunner()
                .withBean(NoonAdvertisingMapper.class, () -> mock(NoonAdvertisingMapper.class))
                .withUserConfiguration(NoonAdvertisingWiringConfig.class)
                .run(context -> {
                    assertTrue(
                            context.getStartupFailure() == null,
                            () -> String.valueOf(context.getStartupFailure())
                    );
                    assertNotNull(context.getBean(NoonAdvertisingAnalyticsService.class));
                    assertNotNull(context.getBean(NoonAdvertisingImportService.class));
                    assertNotNull(context.getBean(NoonAdvertisingReportAdapter.class));
                    assertNotNull(context.getBean(NoonAdvertisingRepository.class));
                    assertNotNull(context.getBean(NoonAdvertisingImportRepository.class));
                });
    }

    @Configuration
    @Import({
            NoonAdvertisingAnalyticsService.class,
            NoonAdvertisingImportService.class,
            NoonAdvertisingReportAdapter.class,
            MyBatisNoonAdvertisingRepository.class
    })
    static class NoonAdvertisingWiringConfig {
    }
}
