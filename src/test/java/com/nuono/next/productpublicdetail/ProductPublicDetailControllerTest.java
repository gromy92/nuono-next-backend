package com.nuono.next.productpublicdetail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class ProductPublicDetailControllerTest {

    @Test
    void controllerExposesOnlyTheRetainedReadSurfaces() {
        Method[] methods = ProductPublicDetailController.class.getDeclaredMethods();
        Set<String> reads = Arrays.stream(methods)
                .filter((method) -> method.isAnnotationPresent(GetMapping.class))
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("syncStatus", "latest"), reads);
        assertFalse(Arrays.stream(methods).anyMatch(
                (method) -> method.isAnnotationPresent(PostMapping.class)
        ));
    }
}
