package com.nuono.next.system;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ReleaseHealthGroupConfigurationTest {

    @Test
    void releaseHealthUsesOnlyBaseRuntimeDependencies() throws IOException {
        String config = new String(
                getClass().getResourceAsStream("/application.yml").readAllBytes(),
                StandardCharsets.UTF_8
        );

        assertTrue(config.contains("release:\n          include: db,diskSpace,ping"));
        assertTrue(config.contains("dpRuntime:\n          include: dpRuntimeIndicator"));
    }
}
