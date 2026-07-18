package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.product.noon.NoonProductGateway;
import com.nuono.next.product.noon.ProductNoonAdapter;
import com.nuono.next.store.StoreSyncOwnerContext;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Constructor;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductImageNoonPublisherTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock private StoreSyncMapper storeSyncMapper;
    @Mock private ProductNoonAdapter noonAdapter;
    private NoonSession session;
    private Path imageFile;

    @BeforeEach
    void setUp() throws Exception {
        session = noonSession("STR108065-NAE");
        Path dir = ProductImageAssetFileSupport.productImageUploadDir().resolve("profiles/STR108065-NAE");
        Files.createDirectories(dir);
        imageFile = dir.resolve("approval-publish-test.png");
        Files.write(imageFile, new byte[] {1, 2, 3});
    }

    private NoonSession noonSession(String storeCode) {
        try {
            NoonSessionGateway gateway = new NoonSessionGateway(
                    objectMapper, storeSyncMapper, false, 0L, true,
                    "", "", "", "", false, false,
                    "", "", "", "", "", "", "", "",
                    false, "", "", 0, ""
            );
            for (Constructor<?> constructor : NoonSession.class.getDeclaredConstructors()) {
                if (constructor.getParameterCount() == 7) {
                    constructor.setAccessible(true);
                    return (NoonSession) constructor.newInstance(
                            gateway, 307L, "operator@example.com", "password", null, "PRJ-1", storeCode
                    );
                }
            }
            throw new IllegalStateException("未找到测试 NoonSession 构造器");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("无法创建测试 NoonSession", exception);
        }
    }

    @AfterEach
    void cleanUp() throws Exception {
        Files.deleteIfExists(imageFile);
    }

    @Test
    void shouldUploadWriteAndOnlyReturnAfterExactOrderedReadback() throws Exception {
        StoreSyncStoreRecord store = new StoreSyncStoreRecord();
        store.setProjectCode("PRJ-1");
        store.setNoonPartnerProjectUser("operator@example.com");
        store.setNoonPartnerCookie("cookie");
        StoreSyncOwnerContext owner = new StoreSyncOwnerContext();
        owner.setId(307L);
        when(storeSyncMapper.selectOwnerProject(307L, "STR108065-NAE")).thenReturn(store);
        when(storeSyncMapper.selectOwnerContext(307L)).thenReturn(owner);
        when(noonAdapter.loginWithPersistedCookie(307L, "operator@example.com", "cookie", "PRJ-1", "STR108065-NAE"))
                .thenReturn(session);
        when(noonAdapter.postMultipartFile(
                eq(session), any(String.class), eq("file"), eq("approval-publish-test.png"), eq("image/png"),
                any(byte[].class), eq(true), eq(null)
        )).thenReturn(objectMapper.readTree("{\"upload_path\":\"https://noon.example/image-1.png\"}"));
        when(noonAdapter.postWriteJson(eq(session), eq(NoonProductGateway.ZSKU_UPSERT_URL), any(JsonNode.class), eq(true)))
                .thenReturn(objectMapper.readTree("{}"));
        when(noonAdapter.postJson(eq(session), eq(NoonProductGateway.ZSKU_RETRIEVE_URL), any(JsonNode.class), eq(true)))
                .thenReturn(objectMapper.readTree(
                        "{\"PARENT-1\":{\"attributes\":{\"common\":{\"image_url_1\":\"https://noon.example/image-1.png\"}}}}"
                ));
        ProductImageNoonPublisher publisher = new ProductImageNoonPublisher(storeSyncMapper, noonAdapter, objectMapper);

        List<String> result = publisher.publish(
                307L,
                "STR108065-NAE",
                "PARENT-1",
                List.of("/api/product-images/assets/STR108065-NAE/approval-publish-test.png")
        );

        assertEquals(List.of("https://noon.example/image-1.png"), result);
        ArgumentCaptor<JsonNode> writeBody = ArgumentCaptor.forClass(JsonNode.class);
        verify(noonAdapter).postWriteJson(eq(session), eq(NoonProductGateway.ZSKU_UPSERT_URL), writeBody.capture(), eq(true));
        assertEquals("https://noon.example/image-1.png", writeBody.getValue().path("attributes").path("image_url_1").asText());
        assertTrue(writeBody.getValue().path("attributes").path("image_url_2").isNull());
    }
}
