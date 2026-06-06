package com.nuono.next.competitoranalysis.noon;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class HttpNoonFrontendSearchAdapterWiringTest {

    @Test
    void localDbProfileCanCreateHttpNoonFrontendSearchAdapterBean() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=local-db")
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withUserConfiguration(WiringConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(NoonFrontendSearchPageParser.class);
                    assertThat(context).hasSingleBean(HttpNoonFrontendSearchAdapter.class);
                    assertThat(context).hasSingleBean(NoonFrontendSearchAdapter.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            NoonFrontendSearchPageParser.class,
            HttpNoonFrontendSearchAdapter.class
    })
    static class WiringConfig {
    }
}
