package com.nuono.next.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoonAssetUploadContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldOwnTheCanonicalRequestTargetAndMultipartField() {
        assertEquals(
                "https://catalog.noon.partners/_svc/mp-partner-catalog/catalog/asset/upload",
                NoonAssetUploadContract.URL
        );
        assertEquals("catalog.noon.partners", NoonAssetUploadContract.host());
        assertEquals(443, NoonAssetUploadContract.port());
        assertEquals("file", NoonAssetUploadContract.FILE_FIELD);
    }

    @Test
    void shouldAcceptAllKnownSuccessfulResponseFields() {
        for (String field : List.of("upload_path", "uploadPath", "path", "url")) {
            ObjectNode response = objectMapper.createObjectNode();
            response.put(field, "uploaded/image.jpg");

            assertEquals("uploaded/image.jpg", NoonAssetUploadContract.requireUploadPath(response));
        }
    }

    @Test
    void shouldRejectResponsesWithoutAnUploadPath() {
        assertThrows(
                IllegalStateException.class,
                () -> NoonAssetUploadContract.requireUploadPath(objectMapper.createObjectNode())
        );
    }
}
