package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.nuono.next.infrastructure.mapper.SalesDataMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

class SalesDataSpringWiringContractTest {

    @Test
    void salesImportComponentsAreSpringBeans() {
        assertNotNull(NoonProductViewsSalesReportParser.class.getAnnotation(Component.class));
        assertNotNull(NoonSalesCsvImportService.class.getAnnotation(Service.class));
        assertNotNull(SalesImportQualityService.class.getAnnotation(Service.class));
        assertNotNull(MyBatisSalesFactRepository.class.getAnnotation(Repository.class));
        assertNotNull(NoonSalesReportBindingResolver.class.getAnnotation(Service.class));
    }

    @Test
    void salesAnalyticsServiceCanWireWithRepositoryWhenMultipleConstructorsExist() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        SalesAnalyticsService.class,
                        TestSalesFactRepositoryConfig.class
                )
                .run(context -> assertNotNull(context.getBean(SalesAnalyticsService.class)));
    }

    @Test
    void compositeSalesActivityWindowRepositoryCanWireWithMyBatisRepositoryAndCompatibilitySources() {
        new ApplicationContextRunner()
                .withBean(SalesDataMapper.class, () -> mock(SalesDataMapper.class))
                .withBean(MyBatisSalesActivityWindowRepository.class)
                .withBean(SalesActivityWindowCompatibilitySource.class, () -> scope -> List.of())
                .withBean(CompositeSalesActivityWindowRepository.class)
                .run(context -> assertTrue(
                        context.getStartupFailure() == null,
                        () -> String.valueOf(context.getStartupFailure())
                ));
    }

    @Configuration
    static class TestSalesFactRepositoryConfig {

        @Bean
        SalesFactRepository salesFactRepository() {
            return new SalesFactRepository() {
                @Override
                public long saveBatch(SalesImportBatch batch) {
                    return 1L;
                }

                @Override
                public void upsert(DailySalesFact fact) {
                }

                @Override
                public List<DailySalesFact> list(SalesFactQuery query) {
                    return List.of();
                }
            };
        }
    }
}
