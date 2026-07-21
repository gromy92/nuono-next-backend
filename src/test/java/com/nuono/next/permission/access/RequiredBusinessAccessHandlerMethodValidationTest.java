package com.nuono.next.permission.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class RequiredBusinessAccessHandlerMethodValidationTest {

    private static final AtomicBoolean WRONG_TYPE_EXECUTED = new AtomicBoolean();
    private static final AtomicBoolean MISSING_ANNOTATION_EXECUTED = new AtomicBoolean();

    @BeforeEach
    void resetExecutionFlags() {
        WRONG_TYPE_EXECUTED.set(false);
        MISSING_ANNOTATION_EXECUTED.set(false);
    }

    @Test
    void mvcContextRejectsAnnotationOnARequestParamBeforeHandlerExecution() {
        contextRunner(WrongTypeMvcConfig.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootCause(context.getStartupFailure()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("WrongTypeController#wrongType")
                    .hasMessageContaining("只能用于 BusinessAccessContext");
            assertThat(WRONG_TYPE_EXECUTED).isFalse();
        });
    }

    @Test
    void mvcContextRejectsUnannotatedBusinessContextBeforeHandlerExecution() {
        contextRunner(MissingAnnotationMvcConfig.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootCause(context.getStartupFailure()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MissingAnnotationController#missingAnnotation")
                    .hasMessageContaining("必须声明 @RequiredBusinessAccess");
            assertThat(MISSING_ANNOTATION_EXECUTED).isFalse();
        });
    }

    @Test
    void mvcContextRejectsBusinessContextClaimedByAnEarlierBuiltInResolver() {
        contextRunner(ShadowedContextMvcConfig.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootCause(context.getStartupFailure()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ShadowedContextController#shadowedContext")
                    .hasMessageContaining("RequestResponseBodyMethodProcessor");
        });
    }

    @Test
    void mvcContextAcceptsAPlainAnnotatedBusinessContext() {
        contextRunner(ValidContextMvcConfig.class).run(context -> {
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
            WrongTypeController.class
    })
    static class WrongTypeMvcConfig {
    }

    @Configuration
    @EnableWebMvc
    @Import({
            BusinessAccessArgumentResolver.class,
            BusinessAccessWebMvcConfig.class,
            MissingAnnotationController.class
    })
    static class MissingAnnotationMvcConfig {
    }

    @Configuration
    @EnableWebMvc
    @Import({
            BusinessAccessArgumentResolver.class,
            BusinessAccessWebMvcConfig.class,
            ShadowedContextController.class
    })
    static class ShadowedContextMvcConfig {
    }

    @Configuration
    @EnableWebMvc
    @Import({
            BusinessAccessArgumentResolver.class,
            BusinessAccessWebMvcConfig.class,
            ValidContextController.class
    })
    static class ValidContextMvcConfig {
    }

    @RestController
    static class WrongTypeController {

        @GetMapping("/wrong-type")
        String wrongType(
                @RequestParam
                @RequiredBusinessAccess(capability = BusinessCapability.IN_TRANSIT_GOODS)
                String value
        ) {
            WRONG_TYPE_EXECUTED.set(true);
            return value;
        }
    }

    @RestController
    static class MissingAnnotationController {

        @GetMapping("/missing-annotation")
        String missingAnnotation(BusinessAccessContext context) {
            MISSING_ANNOTATION_EXECUTED.set(true);
            return String.valueOf(context.getSessionUserId());
        }
    }

    @RestController
    static class ShadowedContextController {

        @GetMapping("/shadowed-context")
        String shadowedContext(
                @RequestBody
                @RequiredBusinessAccess(capability = BusinessCapability.IN_TRANSIT_GOODS)
                BusinessAccessContext context
        ) {
            return String.valueOf(context.getSessionUserId());
        }
    }

    @RestController
    static class ValidContextController {

        @GetMapping("/valid-context")
        String validContext(
                @RequiredBusinessAccess(capability = BusinessCapability.IN_TRANSIT_GOODS)
                BusinessAccessContext context
        ) {
            return String.valueOf(context.getSessionUserId());
        }
    }
}
