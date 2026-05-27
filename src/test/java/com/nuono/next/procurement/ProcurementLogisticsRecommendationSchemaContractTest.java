package com.nuono.next.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

class ProcurementLogisticsRecommendationSchemaContractTest {

    @Test
    void serviceIsAvailableAsLocalDbSpringBean() {
        assertNotNull(LocalDbProcurementLogisticsRecommendationService.class.getAnnotation(Service.class));
        Profile profile = LocalDbProcurementLogisticsRecommendationService.class.getAnnotation(Profile.class);
        assertNotNull(profile);
        assertEquals("local-db", profile.value()[0]);
    }

    @Test
    void procurementControllerExposesRecommendationEndpoint() throws NoSuchMethodException {
        Method method = ProcurementController.class.getMethod("logisticsRecommendation", Long.class, Long.class);
        GetMapping mapping = method.getAnnotation(GetMapping.class);

        assertNotNull(mapping);
        assertEquals("/logistics-recommendation", mapping.value()[0]);
    }
}
