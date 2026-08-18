package com.nuono.next.mobile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

class MobileAppCorsWebMvcConfigTest {

    private static final String PROCUREMENT_EXTENSION_ORIGIN =
            "chrome-extension://hpbbkgdmajhhjbkbiaegidiagggliame";

    @Test
    void allowsNativeAppAndFixedProcurementExtensionCorsContract() {
        InspectableCorsRegistry registry = new InspectableCorsRegistry();

        new MobileAppCorsWebMvcConfig().addCorsMappings(registry);

        CorsConfiguration configuration = registry.configurations().get("/api/**");
        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins())
                .containsExactly("https://localhost", "capacitor://localhost", PROCUREMENT_EXTENSION_ORIGIN);
        assertThat(configuration.getAllowedMethods())
                .containsExactly("GET", "POST", "PUT", "DELETE", "OPTIONS");
        assertThat(configuration.getAllowedHeaders())
                .containsExactly(
                        "Authorization",
                        "Content-Type",
                        "Accept",
                        "X-Nuono-Extension",
                        "X-Nuono-Extension-Build",
                        "X-Nuono-Extension-Version");
        assertThat(configuration.checkOrigin(PROCUREMENT_EXTENSION_ORIGIN))
                .isEqualTo(PROCUREMENT_EXTENSION_ORIGIN);
        assertThat(configuration.checkOrigin("chrome-extension://untrusted-extension"))
                .isNull();
        assertThat(configuration.checkHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Nuono-Extension",
                "X-Nuono-Extension-Build",
                "X-Nuono-Extension-Version")))
                .containsExactly(
                        "Authorization",
                        "Content-Type",
                        "X-Nuono-Extension",
                        "X-Nuono-Extension-Build",
                        "X-Nuono-Extension-Version");
        assertThat(configuration.getAllowCredentials()).isTrue();
        assertThat(configuration.getMaxAge()).isEqualTo(3600L);
    }

    private static final class InspectableCorsRegistry extends CorsRegistry {

        private Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }
}
