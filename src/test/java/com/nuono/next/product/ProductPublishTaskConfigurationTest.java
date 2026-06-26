package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ProductPublishTaskConfigurationTest {

    @Test
    void shouldEnableAsyncPublishTasksByDefault() throws Exception {
        String applicationYaml = resourceText("application.yml");

        assertTrue(
                applicationYaml.contains("async-enabled: ${NUONO_PRODUCT_PUBLISH_TASK_ASYNC_ENABLED:true}"),
                "product publish should default to async task creation so publish-current returns quickly"
        );
        assertTrue(
                applicationYaml.contains("enabled: ${NUONO_PRODUCT_PUBLISH_TASK_SCHEDULER_ENABLED:true}"),
                "product publish task scheduler should default on so queued publish tasks are executed"
        );
    }

    private String resourceText(String resourceName) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            assertNotNull(input, "missing test resource " + resourceName);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
