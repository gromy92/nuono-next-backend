package com.nuono.next.permission.access;

import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration
public class BusinessAccessWebMvcConfig implements WebMvcConfigurer {

    private final BusinessAccessArgumentResolver argumentResolver;

    public BusinessAccessWebMvcConfig(BusinessAccessArgumentResolver argumentResolver) {
        this.argumentResolver = argumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(argumentResolver);
    }

    @Bean
    public RequiredBusinessAccessHandlerMethodValidator requiredBusinessAccessHandlerMethodValidator(
            ObjectProvider<RequestMappingHandlerMapping> handlerMappings,
            ObjectProvider<RequestMappingHandlerAdapter> handlerAdapters
    ) {
        return new RequiredBusinessAccessHandlerMethodValidator(
                handlerMappings,
                handlerAdapters,
                argumentResolver
        );
    }
}
