package com.nuono.next.permission.access;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.MethodParameter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

public class RequiredBusinessAccessHandlerMethodValidator implements SmartInitializingSingleton {

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappings;
    private final ObjectProvider<RequestMappingHandlerAdapter> handlerAdapters;
    private final BusinessAccessArgumentResolver businessAccessArgumentResolver;

    public RequiredBusinessAccessHandlerMethodValidator(
            ObjectProvider<RequestMappingHandlerMapping> handlerMappings,
            ObjectProvider<RequestMappingHandlerAdapter> handlerAdapters,
            BusinessAccessArgumentResolver businessAccessArgumentResolver
    ) {
        this.handlerMappings = handlerMappings;
        this.handlerAdapters = handlerAdapters;
        this.businessAccessArgumentResolver = businessAccessArgumentResolver;
    }

    @Override
    public void afterSingletonsInstantiated() {
        handlerMappings.orderedStream()
                .flatMap(mapping -> mapping.getHandlerMethods().values().stream())
                .forEach(this::validate);
    }

    private void validate(HandlerMethod handlerMethod) {
        for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
            boolean required = parameter.hasParameterAnnotation(RequiredBusinessAccess.class);
            boolean businessContext = BusinessAccessContext.class.equals(parameter.getParameterType());
            if (required && !businessContext) {
                throw invalid(handlerMethod, parameter, "@RequiredBusinessAccess 只能用于 BusinessAccessContext");
            }
            if (businessContext && !required) {
                throw invalid(handlerMethod, parameter, "BusinessAccessContext 必须声明 @RequiredBusinessAccess");
            }
            if (businessContext) {
                requireBusinessAccessResolverFirst(handlerMethod, parameter);
            }
        }
    }

    private void requireBusinessAccessResolverFirst(
            HandlerMethod handlerMethod,
            MethodParameter parameter
    ) {
        RequestMappingHandlerAdapter adapter = handlerAdapters.orderedStream()
                .filter(candidate -> candidate.supports(handlerMethod))
                .findFirst()
                .orElseThrow(() -> invalid(handlerMethod, parameter, "没有可用的 RequestMappingHandlerAdapter"));
        if (adapter.getArgumentResolvers() == null) {
            throw invalid(handlerMethod, parameter, "RequestMappingHandlerAdapter 尚未初始化参数解析器");
        }
        HandlerMethodArgumentResolver first = adapter.getArgumentResolvers().stream()
                .filter(resolver -> resolver.supportsParameter(parameter))
                .findFirst()
                .orElseThrow(() -> invalid(handlerMethod, parameter, "没有可用的业务访问参数解析器"));
        if (first != businessAccessArgumentResolver) {
            throw invalid(
                    handlerMethod,
                    parameter,
                    "首个参数解析器不是 BusinessAccessArgumentResolver，而是 " + first.getClass().getName()
            );
        }
    }

    private IllegalStateException invalid(
            HandlerMethod handlerMethod,
            MethodParameter parameter,
            String reason
    ) {
        String method = handlerMethod.getBeanType().getName() + "#" + handlerMethod.getMethod().getName();
        return new IllegalStateException(
                "无效的业务访问 Handler Method: " + method
                        + " parameter[" + parameter.getParameterIndex() + "]: " + reason + "。"
        );
    }
}
