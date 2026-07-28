package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PapersayPackageImageComposerTest {

    @BeforeAll
    static void useHeadlessGraphics() {
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    void shouldComposeCanonicalPackageArtworkDeterministically() throws Exception {
        PapersayPackageImageComposer composer =
                new PapersayPackageImageComposer(new ObjectMapper());
        ProductImageSuiteRecord suite = papersaySuite();
        GeneratedProductImage content = transparentContent();

        GeneratedProductImage first = composer.compose(suite, content);
        GeneratedProductImage second = composer.compose(suite, content);

        BufferedImage result = ImageIO.read(new ByteArrayInputStream(first.content()));
        assertEquals(1247, result.getWidth());
        assertEquals(1706, result.getHeight());
        assertEquals("image/png", first.contentType());
        assertArrayEquals(first.content(), second.content());
        Path preview = Path.of("target", "test-artifacts", "papersay-package-preview.png");
        Files.createDirectories(preview.getParent());
        Files.write(preview, first.content());
    }

    @Test
    void shouldRejectOpaqueAiArtworkInsteadOfSavingItAsPackageImage() throws Exception {
        PapersayPackageImageComposer composer =
                new PapersayPackageImageComposer(new ObjectMapper());
        BufferedImage opaque = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(opaque, "png", output);

        assertThrows(
                IllegalStateException.class,
                () -> composer.compose(
                        papersaySuite(),
                        new GeneratedProductImage(output.toByteArray(), "image/png")
                )
        );
    }

    private ProductImageSuiteRecord papersaySuite() {
        ProductImageSuiteRecord suite = new ProductImageSuiteRecord();
        suite.setSkinName("PAPERSAY 黄框主图皮肤");
        suite.setDraftPackageJson("{"
                + "\"profile\":{\"specSummary\":\"2 Pieces\"},"
                + "\"imageRequirements\":{\"packageList\":{"
                + "\"copies\":[\"2 computer monitor memo boards\"]"
                + "}}"
                + "}");
        return suite;
    }

    private GeneratedProductImage transparentContent() throws Exception {
        BufferedImage image = new BufferedImage(400, 500, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(245, 245, 245, 255));
        graphics.fillRoundRect(70, 50, 100, 400, 16, 16);
        graphics.fillRoundRect(230, 50, 100, 400, 16, 16);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new GeneratedProductImage(output.toByteArray(), "image/png");
    }
}
