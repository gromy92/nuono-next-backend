package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import com.nuono.next.auth.AuthSessionTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

class FileParseHttpSpringWiringTest {

    @Test
    void adaptersAndSharedSupportWireInSpringWithoutParseService() {
        assertNotNull(FileParseHttpSupport.class.getAnnotation(Component.class));
        new ApplicationContextRunner()
                .withBean(AuthSessionTokenService.class, () -> mock(AuthSessionTokenService.class))
                .withUserConfiguration(WiringConfig.class)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertNotNull(context.getBean(FileParseHttpSupport.class));
                    assertNotNull(context.getBean(FileParseTaskLifecycleController.class));
                    assertNotNull(context.getBean(FileParseInspectionController.class));
                    assertNotNull(context.getBean(FileParseReviewPublicationController.class));
                });
    }

    @Configuration
    @Import({
            FileParseHttpSupport.class,
            FileParseTaskLifecycleController.class,
            FileParseInspectionController.class,
            FileParseReviewPublicationController.class
    })
    static class WiringConfig {
    }
}
