package com.nuono.next.permission.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BusinessAccessArgumentResolverTest {

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    @Test
    void claimsEveryContextOrAnnotatedParameterSoMisconfigurationFailsClosed() throws Exception {
        BusinessAccessArgumentResolver resolver = resolver();

        assertThat(resolver.supportsParameter(parameter("businessContext", BusinessAccessContext.class))).isTrue();
        assertThat(resolver.supportsParameter(parameter("unannotated", BusinessAccessContext.class))).isTrue();
        assertThat(resolver.supportsParameter(parameter("wrongType", String.class))).isTrue();
        assertThatThrownBy(() -> resolver.resolveArgument(
                parameter("unannotated", BusinessAccessContext.class),
                null,
                new ServletWebRequest(new MockHttpServletRequest()),
                null
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@RequiredBusinessAccess");
        assertThatThrownBy(() -> resolver.resolveArgument(
                parameter("wrongType", String.class),
                null,
                new ServletWebRequest(new MockHttpServletRequest()),
                null
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BusinessAccessContext");
        verifyNoInteractions(businessAccessResolver);
    }

    @Test
    void delegatesBusinessCapabilityToTheExistingResolver() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext expected = context();
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS))
                .thenReturn(expected);

        Object resolved = resolver().resolveArgument(
                parameter("businessContext", BusinessAccessContext.class),
                null,
                new ServletWebRequest(request),
                null
        );

        assertThat(resolved).isSameAs(expected);
        verify(businessAccessResolver).requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS);
    }

    @Test
    void delegatesConfiguredStoreParameterToRequireStoreAccess() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("storeCode", " str-a ");
        BusinessAccessContext expected = context();
        when(businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                " str-a "
        )).thenReturn(expected);

        Object resolved = resolver().resolveArgument(
                parameter("storeContext", BusinessAccessContext.class),
                null,
                new ServletWebRequest(request),
                null
        );

        assertThat(resolved).isSameAs(expected);
        verify(businessAccessResolver).requireStoreAccess(
                request,
                BusinessCapability.SALES_DATA,
                " str-a "
        );
    }

    @Test
    void pathVariableCannotMasqueradeAsTheRequiredStoreQueryParameter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("storeCode", "STR-A")
        );
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, null))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不能操作该店铺。"));

        assertThatThrownBy(() -> resolver().resolveArgument(
                parameter("storeContext", BusinessAccessContext.class),
                null,
                new ServletWebRequest(request),
                null
        )).isInstanceOfSatisfying(ResponseStatusException.class, error ->
                assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(businessAccessResolver).requireStoreAccess(request, BusinessCapability.SALES_DATA, null);
    }

    @Test
    void blankConfiguredStoreParameterFailsClosed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> resolver().resolveArgument(
                parameter("blankStoreParameter", BusinessAccessContext.class),
                null,
                new ServletWebRequest(request),
                null
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("参数名不能为空白");
        verifyNoInteractions(businessAccessResolver);
    }

    private BusinessAccessArgumentResolver resolver() {
        return new BusinessAccessArgumentResolver(businessAccessResolver);
    }

    private MethodParameter parameter(String methodName, Class<?> parameterType) throws Exception {
        Method method = Fixture.class.getDeclaredMethod(methodName, parameterType);
        return new MethodParameter(method, 0);
    }

    private BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .sessionUserId(357L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.OPERATOR)
                .build();
    }

    private static final class Fixture {

        void businessContext(
                @RequiredBusinessAccess(capability = BusinessCapability.IN_TRANSIT_GOODS)
                BusinessAccessContext context
        ) {
        }

        void storeContext(
                @RequiredBusinessAccess(
                        capability = BusinessCapability.SALES_DATA,
                        storeQueryParameter = "storeCode"
                )
                BusinessAccessContext context
        ) {
        }

        void unannotated(BusinessAccessContext context) {
        }

        void wrongType(
                @RequiredBusinessAccess(capability = BusinessCapability.IN_TRANSIT_GOODS)
                String value
        ) {
        }

        void blankStoreParameter(
                @RequiredBusinessAccess(
                        capability = BusinessCapability.SALES_DATA,
                        storeQueryParameter = " "
                )
                BusinessAccessContext context
        ) {
        }
    }
}
