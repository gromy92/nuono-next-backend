package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.springframework.util.StringUtils;

final class PapersayPackageImageComposer {
    static final String CONTENT_LAYER_MARKER = "只生成透明商品内容层";
    static final String CONTENT_LAYER_EXCLUSIONS =
            "不要生成品牌皮肤、Logo、图标、文字、边框或背景";

    private static final int CANVAS_WIDTH = 1247;
    private static final int CANVAS_HEIGHT = 1706;
    private static final int PRODUCT_X = 112;
    private static final int PRODUCT_Y = 315;
    private static final int PRODUCT_WIDTH = 1023;
    private static final int PRODUCT_HEIGHT = 700;
    private static final Color GREEN = new Color(1, 63, 52);
    private static final Color WHITE = Color.WHITE;

    private final ObjectMapper objectMapper;

    PapersayPackageImageComposer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    GeneratedProductImage compose(
            ProductImageSuiteRecord suite,
            GeneratedProductImage contentLayer
    ) {
        if (!supports(suite)) {
            return contentLayer;
        }
        Contract contract = contract(suite);
        BufferedImage content = readImage(contentLayer.content(), "AI 包装商品内容层");
        validateTransparentContent(content);
        BufferedImage frame = readResource("package-frame.png", CANVAS_WIDTH, CANVAS_HEIGHT);
        BufferedImage brand = readResource("brand-lockup.png", 668, 151);
        BufferedImage specBackground = readResource("spec-bg.png", 510, 102);

        BufferedImage canvas =
                new BufferedImage(CANVAS_WIDTH, CANVAS_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        configure(graphics);
        graphics.setColor(WHITE);
        graphics.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
        drawContained(graphics, content, PRODUCT_X, PRODUCT_Y, PRODUCT_WIDTH, PRODUCT_HEIGHT);
        graphics.drawImage(brand, 0, 0, null);
        graphics.drawImage(specBackground, 0, 200, null);

        drawCentered(
                graphics,
                contract.spec,
                0,
                200,
                510,
                102,
                fitFont(graphics, contract.spec, 430, 48, 32),
                WHITE
        );
        graphics.setColor(GREEN);
        graphics.fillRoundRect(345, 1065, 557, 83, 83, 83);
        drawCentered(
                graphics,
                "Package Includes",
                345,
                1065,
                557,
                83,
                fitFont(graphics, "Package Includes", 487, 36, 28),
                WHITE
        );
        drawCentered(
                graphics,
                contract.packageCopy,
                90,
                1205,
                1067,
                115,
                fitFont(graphics, contract.packageCopy, 1067, 58, 32),
                GREEN
        );
        graphics.setColor(GREEN);
        graphics.fillRect(320, 1358, 608, 4);
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.drawImage(frame, 0, 0, null);
        graphics.dispose();
        return new GeneratedProductImage(writePng(canvas), "image/png");
    }

    boolean supports(ProductImageSuiteRecord suite) {
        String skinName = suite == null ? null : suite.getSkinName();
        return StringUtils.hasText(skinName)
                && skinName.toUpperCase(Locale.ROOT).contains("PAPERSAY");
    }

    private Contract contract(ProductImageSuiteRecord suite) {
        if (!StringUtils.hasText(suite.getDraftPackageJson())) {
            throw new IllegalStateException("PAPERSAY 包装图缺少套图草稿数据，已停止生成。");
        }
        try {
            JsonNode root = objectMapper.readTree(suite.getDraftPackageJson());
            String spec = root.path("profile").path("specSummary").asText("").trim();
            JsonNode copies = root.path("imageRequirements").path("packageList").path("copies");
            String packageCopy = copies.isArray() && !copies.isEmpty()
                    ? copies.path(0).asText("").trim()
                    : "";
            if (!StringUtils.hasText(spec)) {
                throw new IllegalStateException("PAPERSAY 包装图缺少已确认规格，已停止生成。");
            }
            if (!StringUtils.hasText(packageCopy)) {
                throw new IllegalStateException("PAPERSAY 包装图缺少精确包装清单，已停止生成。");
            }
            return new Contract(spec, packageCopy);
        } catch (IOException exception) {
            throw new IllegalStateException("PAPERSAY 包装图草稿格式无效，已停止生成。", exception);
        }
    }

    private BufferedImage readResource(String filename, int width, int height) {
        String resource = "/product-image/papersay-package/" + filename;
        try (InputStream stream = PapersayPackageImageComposer.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("PAPERSAY 包装皮肤组件缺失：" + filename);
            }
            BufferedImage image = ImageIO.read(stream);
            if (image == null || image.getWidth() != width || image.getHeight() != height) {
                throw new IllegalStateException("PAPERSAY 包装皮肤组件尺寸错误：" + filename);
            }
            return image;
        } catch (IOException exception) {
            throw new IllegalStateException("PAPERSAY 包装皮肤组件读取失败：" + filename, exception);
        }
    }

    private BufferedImage readImage(byte[] content, String label) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) {
                throw new IllegalStateException(label + "不是有效 PNG。");
            }
            return image;
        } catch (IOException exception) {
            throw new IllegalStateException(label + "读取失败。", exception);
        }
    }

    private void validateTransparentContent(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) {
            throw new IllegalStateException("AI 包装商品内容层必须使用透明背景。");
        }
        long transparent = 0;
        long opaque = 0;
        int cornerSize = Math.max(16, Math.min(image.getWidth(), image.getHeight()) / 30);
        long opaqueCorners = 0;
        long cornerPixels = 4L * cornerSize * cornerSize;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = image.getRGB(x, y) >>> 24;
                if (alpha < 16) {
                    transparent++;
                } else {
                    opaque++;
                }
                if (alpha >= 16 && inCorner(x, y, image, cornerSize)) {
                    opaqueCorners++;
                }
            }
        }
        double transparentFraction =
                transparent / (double) (image.getWidth() * image.getHeight());
        double opaqueFraction = opaque / (double) (image.getWidth() * image.getHeight());
        double opaqueCornerFraction = opaqueCorners / (double) cornerPixels;
        if (transparentFraction < 0.03
                || opaqueFraction < 0.01
                || opaqueCornerFraction > 0.25) {
            throw new IllegalStateException(
                    "AI 包装商品内容层包含背景或边角残留，已停止生成。"
            );
        }
    }

    private boolean inCorner(
            int x,
            int y,
            BufferedImage image,
            int cornerSize
    ) {
        boolean leftOrRight = x < cornerSize || x >= image.getWidth() - cornerSize;
        boolean topOrBottom = y < cornerSize || y >= image.getHeight() - cornerSize;
        return leftOrRight && topOrBottom;
    }

    private void drawContained(
            Graphics2D graphics,
            BufferedImage image,
            int x,
            int y,
            int width,
            int height
    ) {
        double scale = Math.min(width / (double) image.getWidth(), height / (double) image.getHeight());
        int renderedWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int renderedHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
        int renderedX = x + (width - renderedWidth) / 2;
        int renderedY = y + (height - renderedHeight) / 2;
        graphics.drawImage(
                image,
                renderedX,
                renderedY,
                renderedWidth,
                renderedHeight,
                null
        );
    }

    private Font fitFont(
            Graphics2D graphics,
            String text,
            int availableWidth,
            int maxSize,
            int minSize
    ) {
        for (int size = maxSize; size >= minSize; size -= 2) {
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, size);
            if (graphics.getFontMetrics(font).stringWidth(text) <= availableWidth) {
                return font;
            }
        }
        throw new IllegalStateException("PAPERSAY 包装图精确文案过长，无法安全排版。");
    }

    private void drawCentered(
            Graphics2D graphics,
            String text,
            int x,
            int y,
            int width,
            int height,
            Font font,
            Color color
    ) {
        graphics.setFont(font);
        graphics.setColor(color);
        FontMetrics metrics = graphics.getFontMetrics(font);
        int textX = x + (width - metrics.stringWidth(text)) / 2;
        int textY = y + (height - metrics.getHeight()) / 2 + metrics.getAscent();
        graphics.drawString(text, textX, textY);
    }

    private void configure(Graphics2D graphics) {
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );
        graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC
        );
        graphics.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );
    }

    private byte[] writePng(BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("PAPERSAY 包装图合成失败。", exception);
        }
    }

    private static final class Contract {
        private final String spec;
        private final String packageCopy;

        private Contract(String spec, String packageCopy) {
            this.spec = spec;
            this.packageCopy = packageCopy;
        }
    }
}
