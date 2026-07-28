package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
    private Path secondImageFile;

    @BeforeEach
    void setUp() throws Exception {
        session = noonSession("STR108065-NAE");
        Path dir = ProductImageAssetFileSupport.productImageUploadDir().resolve("profiles/STR108065-NAE");
        Files.createDirectories(dir);
        imageFile = dir.resolve("approval-publish-test.png");
        Files.write(imageFile, new byte[] {1, 2, 3});
        secondImageFile = dir.resolve("approval-publish-test-2.png");
        Files.write(secondImageFile, new byte[] {4, 5, 6});
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
        Files.deleteIfExists(secondImageFile);
    }

    @Test
    void shouldRejectMoreThanTwentyImagesBeforeLoadingStoreOrCallingProvider() {
        ProductImageNoonPublisher publisher =
                new ProductImageNoonPublisher(storeSyncMapper, noonAdapter, objectMapper);
        List<String> imageUrls = IntStream.rangeClosed(
                        1,
                        ProductImagePublishCheckpoint.MAX_IMAGES + 1
                )
                .mapToObj(index -> "/api/product-images/assets/STR108065-NAE/image-" + index + ".png")
                .collect(Collectors.toList());

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> publisher.publish(
                        307L,
                        "STR108065-NAE",
                        "PARENT-1",
                        imageUrls
                )
        );

        assertTrue(failure.getMessage().contains("最多支持发布 20 张"));
        verifyNoInteractions(storeSyncMapper, noonAdapter);
    }

    @Test
    void shouldUploadWriteAndOnlyReturnAfterExactOrderedReadback() throws Exception {
        stubStoreSession();
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

    @Test
    void shouldReuseUploadedCheckpointAndOnlyUploadMissingImage() throws Exception {
        stubStoreSession();
        when(noonAdapter.postMultipartFile(
                eq(session), any(String.class), eq("file"), eq("approval-publish-test-2.png"), eq("image/png"),
                any(byte[].class), eq(true), eq(null)
        )).thenReturn(objectMapper.readTree("{\"upload_path\":\"https://noon.example/image-2.png\"}"));
        when(noonAdapter.postWriteJson(
                eq(session), eq(NoonProductGateway.ZSKU_UPSERT_URL), any(JsonNode.class), eq(true)
        )).thenReturn(objectMapper.readTree("{}"));
        when(noonAdapter.postJson(
                eq(session), eq(NoonProductGateway.ZSKU_RETRIEVE_URL), any(JsonNode.class), eq(true)
        )).thenReturn(objectMapper.readTree(
                "{\"PARENT-1\":{\"attributes\":{\"common\":{"
                        + "\"image_url_1\":\"https://noon.example/image-1.png\","
                        + "\"image_url_2\":\"https://noon.example/image-2.png\"}}}}"
        ));
        String firstUrl = "/api/product-images/assets/STR108065-NAE/approval-publish-test.png";
        String secondUrl = "/api/product-images/assets/STR108065-NAE/approval-publish-test-2.png";
        String checkpoint = "{\"version\":1,\"writeAttempted\":false,\"uploads\":[{"
                + "\"localImageUrl\":\"" + firstUrl + "\","
                + "\"sha256\":\"" + sha256(new byte[] {1, 2, 3}) + "\","
                + "\"noonUrl\":\"https://noon.example/image-1.png\"}]}";
        List<String> savedCheckpoints = new ArrayList<>();
        ProductImageNoonPublisher publisher =
                new ProductImageNoonPublisher(storeSyncMapper, noonAdapter, objectMapper);

        List<String> result = publisher.publish(
                307L,
                "STR108065-NAE",
                "PARENT-1",
                List.of(firstUrl, secondUrl),
                checkpoint,
                savedCheckpoints::add
        );

        assertEquals(
                List.of("https://noon.example/image-1.png", "https://noon.example/image-2.png"),
                result
        );
        verify(noonAdapter, never()).postMultipartFile(
                eq(session), any(String.class), eq("file"), eq("approval-publish-test.png"), eq("image/png"),
                any(byte[].class), eq(true), eq(null)
        );
        verify(noonAdapter).postMultipartFile(
                eq(session), any(String.class), eq("file"), eq("approval-publish-test-2.png"), eq("image/png"),
                any(byte[].class), eq(true), eq(null)
        );
        assertTrue(savedCheckpoints.stream().anyMatch(value -> value.contains("\"writeAttempted\":true")));
    }

    @Test
    void shouldVerifyWriteCheckpointBeforeAnyRepeatedUploadOrUpsert() throws Exception {
        stubStoreSession();
        when(noonAdapter.postJson(
                eq(session), eq(NoonProductGateway.ZSKU_RETRIEVE_URL), any(JsonNode.class), eq(true)
        )).thenReturn(objectMapper.readTree(
                "{\"PARENT-1\":{\"attributes\":{\"common\":{"
                        + "\"image_url_1\":\"https://noon.example/image-1.png\"}}}}"
        ));
        String firstUrl = "/api/product-images/assets/STR108065-NAE/approval-publish-test.png";
        String checkpoint = "{\"version\":1,\"writeAttempted\":true,\"uploads\":[{"
                + "\"localImageUrl\":\"" + firstUrl + "\","
                + "\"sha256\":\"" + sha256(new byte[] {1, 2, 3}) + "\","
                + "\"noonUrl\":\"https://noon.example/image-1.png\"}]}";
        ProductImageNoonPublisher publisher =
                new ProductImageNoonPublisher(storeSyncMapper, noonAdapter, objectMapper);

        List<String> result = publisher.publish(
                307L,
                "STR108065-NAE",
                "PARENT-1",
                List.of(firstUrl),
                checkpoint,
                ignored -> {
                }
        );

        assertEquals(List.of("https://noon.example/image-1.png"), result);
        verify(noonAdapter, never()).postMultipartFile(
                any(), any(String.class), any(String.class), any(String.class), any(String.class),
                any(byte[].class), eq(true), eq(null)
        );
        verify(noonAdapter, never()).postWriteJson(
                eq(session), eq(NoonProductGateway.ZSKU_UPSERT_URL), any(JsonNode.class), eq(true)
        );
    }

    @Test
    void shouldRejectChangedApprovedContentBeforeAnyNoonWrite() throws Exception {
        stubStoreSession();
        String imageUrl =
                "/api/product-images/assets/STR108065-NAE/approval-publish-test.png";
        ProductImageSuiteAssetRecord approved = new ProductImageSuiteAssetRecord();
        approved.setId(5001L);
        approved.setImageUrl(imageUrl);
        approved.setSha256(sha256(new byte[] {1, 2, 3}));
        String checkpoint = ProductImagePublishCheckpoint.start(List.of(approved))
                .toJson(objectMapper);
        Files.write(imageFile, new byte[] {9, 9, 9});
        ProductImageNoonPublisher publisher =
                new ProductImageNoonPublisher(storeSyncMapper, noonAdapter, objectMapper);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> publisher.publish(
                        307L, "STR108065-NAE", "PARENT-1",
                        List.of(imageUrl), checkpoint, ignored -> { }
                )
        );

        assertTrue(failure.getMessage().contains("审核通过后的套图文件已发生变化"));
        verify(noonAdapter, never()).postMultipartFile(
                any(), any(String.class), any(String.class), any(String.class), any(String.class),
                any(byte[].class), eq(true), eq(null)
        );
        verify(noonAdapter, never()).postWriteJson(
                any(), any(String.class), any(JsonNode.class), eq(true)
        );
    }

    private void stubStoreSession() {
        when(storeSyncMapper.selectOwnerProject(307L, "STR108065-NAE"))
                .thenReturn(storeRecord());
        when(storeSyncMapper.selectOwnerContext(307L)).thenReturn(ownerContext());
        when(noonAdapter.loginWithPersistedCookie(
                307L, "operator@example.com", "cookie", "PRJ-1", "STR108065-NAE"
        )).thenReturn(session);
    }

    private StoreSyncStoreRecord storeRecord() {
        StoreSyncStoreRecord store = new StoreSyncStoreRecord();
        store.setProjectCode("PRJ-1");
        store.setNoonPartnerProjectUser("operator@example.com");
        store.setNoonPartnerCookie("cookie");
        return store;
    }

    private StoreSyncOwnerContext ownerContext() {
        StoreSyncOwnerContext owner = new StoreSyncOwnerContext();
        owner.setId(307L);
        return owner;
    }

    private String sha256(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder builder = new StringBuilder();
        for (byte item : digest) {
            builder.append(String.format("%02x", item));
        }
        return builder.toString();
    }
}
