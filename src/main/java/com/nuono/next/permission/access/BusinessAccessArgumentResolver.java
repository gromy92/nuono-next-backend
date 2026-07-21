package com.nuono.next.permission.access;

import javax.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class BusinessAccessArgumentResolver implements HandlerMethodArgumentResolver {

    private final BusinessAccessResolver businessAccessResolver;

    public BusinessAccessArgumentResolver(BusinessAccessResolver businessAccessResolver) {
        this.businessAccessResolver = businessAccessResolver;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return BusinessAccessContext.class.equals(parameter.getParameterType())
                || BusinessStoreAccess.class.equals(parameter.getParameterType())
                || parameter.hasParameterAnnotation(RequiredBusinessAccess.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) throws Exception {
        RequiredBusinessAccess requiredAccess = parameter.getParameterAnnotation(RequiredBusinessAccess.class);
        boolean businessContext = BusinessAccessContext.class.equals(parameter.getParameterType());
        boolean storeAccess = BusinessStoreAccess.class.equals(parameter.getParameterType());
        if (requiredAccess == null || (!businessContext && !storeAccess)) {
            throw new IllegalStateException(
                    "业务访问参数必须是带 @RequiredBusinessAccess 的 BusinessAccessContext 或 BusinessStoreAccess。"
            );
        }
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new IllegalStateException("缺少 HTTP 业务访问请求上下文。");
        }

        String storeQueryParameter = requiredAccess.storeQueryParameter();
        if (businessContext && storeQueryParameter.isEmpty()) {
            return businessAccessResolver.requireBusinessContext(request, requiredAccess.capability());
        }
        if (!StringUtils.hasText(storeQueryParameter)) {
            throw new IllegalStateException("业务店铺参数名不能为空白。");
        }
        String parameterName = storeQueryParameter.trim();
        String storeCode = webRequest.getParameter(parameterName);
        if (storeAccess) {
            if (storeCode == null) {
                throw new MissingServletRequestParameterException(parameterName, "String");
            }
            return businessAccessResolver.requireStoreAccessScope(
                    request,
                    requiredAccess.capability(),
                    storeCode
            );
        }
        return businessAccessResolver.requireStoreAccess(request, requiredAccess.capability(), storeCode);
    }
}
