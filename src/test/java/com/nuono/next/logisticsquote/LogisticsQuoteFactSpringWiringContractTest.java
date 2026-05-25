package com.nuono.next.logisticsquote;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

class LogisticsQuoteFactSpringWiringContractTest {

    @Test
    void logisticsFactLandingComponentsAreSpringBeans() {
        assertNotNull(LogisticsQuoteFactPublisher.class.getAnnotation(Service.class));
        assertNotNull(MyBatisLogisticsQuoteFactRepository.class.getAnnotation(Repository.class));
    }
}
