package com.nuono.next.competitoranalysis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnBean({
        CompetitorCorrectionWriterFenceGuard.class,
        PlatformTransactionManager.class
})
class CompetitorCorrectionMaintenanceWebConfig implements WebMvcConfigurer {
    static final String API_PATH = "/api/competitor-analysis/**";

    private final CompetitorCorrectionMaintenanceInterceptor interceptor;

    CompetitorCorrectionMaintenanceWebConfig(
            CompetitorCorrectionWriterFenceGuard fenceGuard,
            PlatformTransactionManager transactionManager
    ) {
        this.interceptor = new CompetitorCorrectionMaintenanceInterceptor(
                fenceGuard,
                transactionManager
        );
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns(API_PATH);
    }
}
