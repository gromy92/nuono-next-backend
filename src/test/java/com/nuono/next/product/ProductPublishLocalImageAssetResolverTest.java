package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.product.noon.ProductNoonAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductPublishLocalImageAssetResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void secondUploadAuthShouldRecordFirstUploadAsPossibleWrite() throws Exception {
        Path uploadDir = ProductImageAssetFileSupport.productImageUploadDir();
        Files.createDirectories(uploadDir);
        Path firstImage = uploadDir.resolve(UUID.randomUUID() + ".jpg");
        Path secondImage = uploadDir.resolve(UUID.randomUUID() + ".jpg");
        Files.write(firstImage, new byte[] {1});
        Files.write(secondImage, new byte[] {2});
        try {
            ProductNoonAdapter adapter = mock(ProductNoonAdapter.class);
            ObjectNode uploadResponse = objectMapper.createObjectNode();
            uploadResponse.put("upload_path", "uploaded/first.jpg");
            when(adapter.postMultipartFile(
                    nullable(NoonSession.class),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString(),
                    any(byte[].class),
                    anyBoolean(),
                    nullable(Map.class)
            )).thenReturn(uploadResponse).thenThrow(authFailure());
            ProductPublishLocalImageAssetResolver resolver =
                    new ProductPublishLocalImageAssetResolver(objectMapper, adapter);
            ProductMasterSnapshotView baseline = snapshot(List.of("https://image.example/old.jpg"));
            ProductMasterSnapshotView draft = snapshot(List.of(
                    localImageUrl(firstImage),
                    localImageUrl(secondImage)
            ));

            ProductWriteAuthRequiredException failure = assertThrows(
                    ProductWriteAuthRequiredException.class,
                    () -> resolver.resolveChangedLocalImageAssets(null, draft, baseline, null)
            );

            assertTrue(failure.isWriteMayHaveOccurred());
        } finally {
            Files.deleteIfExists(firstImage);
            Files.deleteIfExists(secondImage);
        }
    }

    private ProductMasterSnapshotView snapshot(List<String> images) {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.getContent().put("images", images);
        return snapshot;
    }

    private String localImageUrl(Path image) {
        return "/api/product-master/image-assets/" + image.getFileName();
    }

    private ProductWriteAuthRequiredException authFailure() {
        return new ProductWriteAuthRequiredException(
                991L,
                false,
                "Noon Project 授权恢复中。",
                new IllegalStateException("auth_required")
        );
    }
}
