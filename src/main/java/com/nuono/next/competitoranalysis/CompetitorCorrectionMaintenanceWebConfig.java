package com.nuono.next.competitoranalysis;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class CompetitorCorrectionMaintenanceWebConfig implements WebMvcConfigurer {
    static final String API_PATH = "/api/competitor-analysis/**";

    private final CompetitorCorrectionMaintenanceInterceptor interceptor;

    CompetitorCorrectionMaintenanceWebConfig(
            CompetitorCorrectionMaintenanceInterceptor interceptor
    ) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns(API_PATH);
    }
}
