package com.nuono.next.permission.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.ServletWebRequest;

@ExtendWith(MockitoExtension.class)
class BusinessStoreAccessArgumentResolverTest {

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    @Test
    void claimsAnnotatedAndUnannotatedTypedParametersSoTheyFailClosed() throws Exception {
        BusinessAccessArgumentResolver resolver = resolver();

        assertThat(resolver.supportsParameter(parameter("storeAccess", BusinessStoreAccess.class))).isTrue();
        assertThat(resolver.supportsParameter(parameter("unannotated", BusinessStoreAccess.class))).isTrue();
        assertThat(resolver.supportsParameter(parameter("wrongType", String.class))).isTrue();
        assertThatThrownBy(() -> resolver.resolveArgument(
                parameter("unannotated", BusinessStoreAccess.class),
                null,
                new ServletWebRequest(new MockHttpServletRequest()),
                null
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@RequiredBusinessAccess");
        verifyNoInteractions(businessAccessResolver);
    }

    @Test
    void delegatesRawStoreValueToTypedScopeResolver() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("storeCode", " str-a ");
        BusinessStoreAccess expected = new BusinessStoreAccess(307L, "STR-A");
        when(businessAccessResolver.requireStoreAccessScope(
                request,
                BusinessCapability.PRODUCT_MASTER,
                " str-a "
        )).thenReturn(expected);

        Object resolved = resolver().resolveArgument(
                parameter("storeAccess", BusinessStoreAccess.class),
                null,
                new ServletWebRequest(request),
                null
        );

        assertThat(resolved).isSameAs(expected);
        verify(businessAccessResolver).requireStoreAccessScope(
                request,
                BusinessCapability.PRODUCT_MASTER,
                " str-a "
        );
    }

    @Test
    void missingStoreUsesSpringMissingRequestParameterContract() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> resolver().resolveArgument(
                parameter("storeAccess", BusinessStoreAccess.class),
                null,
                new ServletWebRequest(request),
                null
        )).isInstanceOfSatisfying(MissingServletRequestParameterException.class, error -> {
            assertThat(error.getParameterName()).isEqualTo("storeCode");
            assertThat(error.getParameterType()).isEqualTo("String");
        });
        verifyNoInteractions(businessAccessResolver);
    }

    @Test
    void typedScopeWithoutConfiguredStoreParameterFailsClosed() throws Exception {
        assertThatThrownBy(() -> resolver().resolveArgument(
                parameter("missingStoreParameter", BusinessStoreAccess.class),
                null,
                new ServletWebRequest(new MockHttpServletRequest()),
                null
        )).isInstanceOf(IllegalStateException.class)
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

    private static final class Fixture {

        void storeAccess(
                @RequiredBusinessAccess(
                        capability = BusinessCapability.PRODUCT_MASTER,
                        storeQueryParameter = "storeCode"
                )
                BusinessStoreAccess access
        ) {
        }

        void unannotated(BusinessStoreAccess access) {
        }

        void wrongType(
                @RequiredBusinessAccess(capability = BusinessCapability.PRODUCT_MASTER)
                String value
        ) {
        }

        void missingStoreParameter(
                @RequiredBusinessAccess(capability = BusinessCapability.PRODUCT_MASTER)
                BusinessStoreAccess access
        ) {
        }
    }
}
