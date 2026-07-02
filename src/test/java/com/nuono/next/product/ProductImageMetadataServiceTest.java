package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductImageProfileMapper;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ProductImageMetadataServiceTest {

    @Mock
    private ProductImageProfileMapper mapper;

    private ProductImageMetadataService service;

    @BeforeEach
    void setUp() {
        service = new ProductImageMetadataService(mapper);
    }

    @Test
    void uploadedImageMetadataShouldReadFileTypeSizeAndDimensions() throws Exception {
        byte[] bytes = pngBytes(320, 240);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.png",
                "image/png; charset=binary",
                bytes
        );

        ProductImageAssetMetadataView view = service.uploadedImageMetadata(file);

        assertEquals("image/png", view.getContentType());
        assertEquals(bytes.length, view.getSizeBytes());
        assertEquals(320, view.getWidthPx());
        assertEquals(240, view.getHeightPx());
    }

    @Test
    void assetMetadataShouldReturnStoredCurrentProductImageMetadata() {
        ProductImageProfileAssetRecord asset = new ProductImageProfileAssetRecord();
        asset.setImageUrl("https://example.test/product.jpg");
        asset.setContentType("image/jpeg");
        asset.setSizeBytes(345678L);
        asset.setWidthPx(1247);
        asset.setHeightPx(1700);
        when(mapper.countAccessibleProductImage(307L, "STR108065-NAE", 9001L, "https://example.test/product.jpg"))
                .thenReturn(1);
        when(mapper.selectCurrentProductImageByUrl(9001L, "https://example.test/product.jpg")).thenReturn(asset);

        ProductImageAssetMetadataView view = service.assetMetadata(
                307L,
                "STR108065-NAE",
                9001L,
                "https://example.test/product.jpg"
        );

        assertEquals(345678L, view.getSizeBytes());
        assertEquals(1247, view.getWidthPx());
        assertEquals(1700, view.getHeightPx());
        assertEquals("image/jpeg", view.getContentType());
        verify(mapper, never()).updateCurrentProductImageMetadata(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private byte[] pngBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
