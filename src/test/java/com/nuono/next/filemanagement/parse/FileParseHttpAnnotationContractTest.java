package com.nuono.next.filemanagement.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

class FileParseHttpAnnotationContractTest {

    private static final Map<String, Class<?>> REVIEW_BODY_TYPES = Map.of(
            "editItem", FileParseReviewCommand.class,
            "acceptItem", FileParseReviewCommand.class,
            "batchAcceptItems", FileParseBatchReviewCommand.class,
            "rejectItem", FileParseReviewCommand.class,
            "keepOldItem", FileParseReviewCommand.class,
            "publishTask", FileParsePublishCommand.class
    );

    @Test
    void uploadRemainsMultipartWithRequiredTargetPlanAndFile() {
        Method upload = method(FileParseTaskLifecycleController.class, "upload");
        PostMapping mapping = upload.getAnnotation(PostMapping.class);

        assertNotNull(mapping);
        assertEquals(Set.of(MediaType.MULTIPART_FORM_DATA_VALUE), Set.of(mapping.consumes()));
        assertRequiredRequestParam(upload, "targetPlanId");
        assertRequiredRequestParam(upload, "file");
    }

    @Test
    void createTaskKeepsOptionalIdempotencyKeyAndRequestBody() {
        Method createTask = method(FileParseTaskLifecycleController.class, "createTask");

        assertRequestBody(createTask, FileParseCreateTaskCommand.class);
        RequestHeader header = requestHeader(createTask, "Idempotency-Key");
        assertTrue(!header.required());
    }

    @Test
    void reviewAndPublishKeepRequiredIdempotencyKeysAndRequestBodies() {
        for (Map.Entry<String, Class<?>> contract : REVIEW_BODY_TYPES.entrySet()) {
            String handler = contract.getKey();
            Method method = method(FileParseReviewPublicationController.class, handler);
            assertTrue(requestHeader(method, "Idempotency-Key").required(), handler);
            assertRequestBody(method, contract.getValue());
        }
    }

    @Test
    void logisticsEndpointsKeepRequiredTargetPlanAndCommandBody() {
        Method list = method(FileParseReviewPublicationController.class, "logisticsChannelActivations");
        assertRequiredRequestParam(list, "targetPlanId");

        Method save = method(FileParseReviewPublicationController.class, "saveLogisticsChannelActivations");
        assertRequestBody(save, FileParseLogisticsChannelActivationCommand.class);
    }

    private void assertRequiredRequestParam(Method method, String name) {
        RequestParam annotation = Arrays.stream(method.getParameters())
                .map(parameter -> parameter.getAnnotation(RequestParam.class))
                .filter(value -> value != null && name.equals(value.value()))
                .findFirst()
                .orElseThrow();
        assertTrue(annotation.required(), name);
    }

    private void assertRequestBody(Method method, Class<?> bodyType) {
        Parameter parameter = Arrays.stream(method.getParameters())
                .filter(value -> value.isAnnotationPresent(RequestBody.class))
                .findFirst()
                .orElseThrow();
        assertEquals(bodyType, parameter.getType());
        assertTrue(parameter.getAnnotation(RequestBody.class).required());
    }

    private RequestHeader requestHeader(Method method, String name) {
        return Arrays.stream(method.getParameters())
                .map(parameter -> parameter.getAnnotation(RequestHeader.class))
                .filter(value -> value != null && name.equals(value.value()))
                .findFirst()
                .orElseThrow();
    }

    private Method method(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
