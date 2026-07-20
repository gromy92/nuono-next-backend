package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FileParseHttpAdapterMappingTest extends FileParseHttpTestFixture {

    private static final Set<Class<?>> ADAPTERS = Set.of(
            FileParseTaskLifecycleController.class,
            FileParseInspectionController.class,
            FileParseReviewPublicationController.class
    );

    @Test
    void preservesAllHandlerMethodsAndRoutesWithoutAmbiguity() {
        Set<String> routes = new HashSet<>();
        int handlerCount = 0;
        for (Class<?> adapter : ADAPTERS) {
            for (Method method : adapter.getDeclaredMethods()) {
                handlerCount += collectRoutes(method, routes);
            }
        }

        assertEquals(26, handlerCount);
        assertEquals(expectedRoutes(), routes);
        assertNotNull(MockMvcBuilders.standaloneSetup(
                new FileParseTaskLifecycleController(support),
                new FileParseInspectionController(support),
                new FileParseReviewPublicationController(support)
        ).build());
    }

    private int collectRoutes(Method method, Set<String> routes) {
        GetMapping get = method.getAnnotation(GetMapping.class);
        if (get != null) {
            addRoutes(routes, "GET", get.value());
            return 1;
        }
        PostMapping post = method.getAnnotation(PostMapping.class);
        if (post != null) {
            addRoutes(routes, "POST", post.value());
            return 1;
        }
        DeleteMapping delete = method.getAnnotation(DeleteMapping.class);
        if (delete != null) {
            addRoutes(routes, "DELETE", delete.value());
            return 1;
        }
        return 0;
    }

    private void addRoutes(Set<String> routes, String method, String[] paths) {
        Arrays.stream(paths)
                .map(path -> method + " " + FileParseHttpSupport.BASE_PATH + path)
                .forEach(routes::add);
    }

    private Set<String> expectedRoutes() {
        String base = FileParseHttpSupport.BASE_PATH;
        return Set.of(
                "GET " + base + "/target-plans",
                "GET " + base + "/tasks",
                "GET " + base + "/tasks/{taskId}",
                "DELETE " + base + "/tasks/{taskId}",
                "POST " + base + "/uploads",
                "POST " + base + "/tasks",
                "POST " + base + "/tasks/{taskId}/run",
                "GET " + base + "/files/{fileId}/download",
                "GET " + base + "/tasks/{taskId}/source-rows",
                "GET " + base + "/tasks/{taskId}/workflow",
                "GET " + base + "/tasks/{taskId}/ai-chunks",
                "GET " + base + "/tasks/{taskId}/validation-issues",
                "GET " + base + "/tasks/{taskId}/processing-items",
                "GET " + base + "/tasks/{taskId}/overview-items",
                "GET " + base + "/tasks/{taskId}/overview-items/export",
                "GET " + base + "/tasks/{taskId}/overview/export",
                "GET " + base + "/tasks/{taskId}/items/{itemId}/compare",
                "GET " + base + "/target-plans/{targetPlanId}/versions",
                "GET " + base + "/versions/{versionId}/items",
                "GET " + base + "/logistics-channel-activations",
                "POST " + base + "/logistics-channel-activations",
                "POST " + base + "/tasks/{taskId}/items/{itemId}/edit",
                "POST " + base + "/tasks/{taskId}/items/{itemId}/accept",
                "POST " + base + "/tasks/{taskId}/items/batch-accept",
                "POST " + base + "/tasks/{taskId}/items/{itemId}/reject",
                "POST " + base + "/tasks/{taskId}/items/{itemId}/keep-old",
                "POST " + base + "/tasks/{taskId}/publish"
        );
    }
}
