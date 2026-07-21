package com.nuono.next.permission.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class RequiredBusinessStoreAccessHandlerMethodValidationTest {

    @Test
    void rejectsUnannotatedTypedScopeAtStartup() {
        contextRunner(MissingAnnotationConfig.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootCause(context.getStartupFailure()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MissingAnnotationController#missingAnnotation")
                    .hasMessageContaining("BusinessStoreAccess 必须声明 @RequiredBusinessAccess");
        });
    }

    @Test
    void rejectsTypedScopeWithoutStoreParameterAtStartup() {
        contextRunner(MissingStoreParameterConfig.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootCause(context.getStartupFailure()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MissingStoreParameterController#missingStoreParameter")
                    .hasMessageContaining("必须声明非空 storeQueryParameter");
        });
    }

    @Test
    void rejectsTypedScopeClaimedByEarlierBuiltInResolver() {
        contextRunner(ShadowedScopeConfig.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootCause(context.getStartupFailure()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ShadowedScopeController#shadowedScope")
                    .hasMessageContaining("RequestResponseBodyMethodProcessor");
        });
    }

    @Test
    void acceptsPlainAnnotatedTypedScopeWithStoreParameter() {
        contextRunner(ValidScopeConfig.class).run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context).hasSingleBean(RequiredBusinessAccessHandlerMethodValidator.class);
        });
    }

    private WebApplicationContextRunner contextRunner(Class<?> configuration) {
        return new WebApplicationContextRunner()
                .withBean(BusinessAccessResolver.class, () -> mock(BusinessAccessResolver.class))
                .withUserConfiguration(configuration);
    }

    private Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @Configuration
    @EnableWebMvc
    @Import({
            BusinessAccessArgumentResolver.class,
            BusinessAccessWebMvcConfig.class,
            MissingAnnotationController.class
    })
    static class MissingAnnotationConfig {
    }

    @Configuration
    @EnableWebMvc
    @Import({
            BusinessAccessArgumentResolver.class,
            BusinessAccessWebMvcConfig.class,
            MissingStoreParameterController.class
    })
    static class MissingStoreParameterConfig {
    }

    @Configuration
    @EnableWebMvc
    @Import({
            BusinessAccessArgumentResolver.class,
            BusinessAccessWebMvcConfig.class,
            ShadowedScopeController.class
    })
    static class ShadowedScopeConfig {
    }

    @Configuration
    @EnableWebMvc
    @Import({
            BusinessAccessArgumentResolver.class,
            BusinessAccessWebMvcConfig.class,
            ValidScopeController.class
    })
    static class ValidScopeConfig {
    }

    @RestController
    static class MissingAnnotationController {

        @GetMapping("/missing-store-access-annotation")
        String missingAnnotation(BusinessStoreAccess access) {
            return access.getStoreCode();
        }
    }

    @RestController
    static class MissingStoreParameterController {

        @GetMapping("/missing-store-parameter")
        String missingStoreParameter(
                @RequiredBusinessAccess(capability = BusinessCapability.PRODUCT_MASTER)
                BusinessStoreAccess access
        ) {
            return access.getStoreCode();
        }
    }

    @RestController
    static class ShadowedScopeController {

        @GetMapping("/shadowed-store-scope")
        String shadowedScope(
                @RequestBody
                @RequiredBusinessAccess(
                        capability = BusinessCapability.PRODUCT_MASTER,
                        storeQueryParameter = "storeCode"
                )
                BusinessStoreAccess access
        ) {
            return access.getStoreCode();
        }
    }

    @RestController
    static class ValidScopeController {

        @GetMapping("/valid-store-scope")
        String validScope(
                @RequiredBusinessAccess(
                        capability = BusinessCapability.PRODUCT_MASTER,
                        storeQueryParameter = "storeCode"
                )
                BusinessStoreAccess access
        ) {
            return access.getStoreCode();
        }
    }
}
