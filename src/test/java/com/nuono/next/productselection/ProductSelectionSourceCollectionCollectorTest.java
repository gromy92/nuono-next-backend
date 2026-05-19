package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ProductSelectionSourceCollectionCollectorTest {

    private final ProductSelectionSourceCollectionHtmlParser htmlParser = new ProductSelectionSourceCollectionHtmlParser();
    private final ProductSelectionSourceCollectionCollector collector = new ProductSelectionSourceCollectionCollector(List.of(), htmlParser);

    @Test
    void extractsNoonProductMetadataFromHtml() {
        String html = String.join("\n",
                "<html>",
                "  <head>",
                "    <meta property=\"og:title\" content=\"USB Rechargeable Bakhoor Burner | noon\">",
                "    <meta property=\"og:image\" content=\"https://f.nooncdn.com/p/v1/main.jpg\">",
                "    <meta property=\"product:price:amount\" content=\"59.00\">",
                "    <meta property=\"product:price:currency\" content=\"SAR\">",
                "  </head>",
                "  <body>",
                "    <h1>USB Rechargeable Bakhoor Burner</h1>",
                "    <ul>",
                "      <li>Rechargeable battery with USB power cable</li>",
                "      <li>Ceramic chamber, gift packaging included</li>",
                "    </ul>",
                "  </body>",
                "</html>"
        );

        ProductSelectionSourceCollectionResult result = collector.collectHtml(
                html,
                "https://www.noon.com/saudi-en/item/Z123/p/",
                "Noon"
        );

        assertEquals("Noon", result.getSourcePlatform());
        assertEquals("USB Rechargeable Bakhoor Burner", result.getSourceTitle());
        assertEquals("https://f.nooncdn.com/p/v1/main.jpg", result.getSourceImageUrl());
        assertEquals("SAR 59.00", result.getPriceSummary());
        assertTrue(result.getSourceDescriptionEn().contains("Rechargeable battery"));
        assertTrue(result.getSourceSellingPointsEn().stream().anyMatch(item -> item.contains("Rechargeable battery")));
        assertTrue(result.getSpecHints().stream().anyMatch(item -> item.contains("Rechargeable battery")));
    }

    @Test
    void mapsNoonPublicProductPayloadWithoutCookie() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        List<String> cookies = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            cookies.add(exchange.getRequestHeaders().getFirst("Cookie"));
            boolean arabic = exchange.getRequestURI().getPath().contains("saudi-ar");
            Map<String, Object> product = Map.ofEntries(
                    Map.entry("sku", "N53335547A"),
                    Map.entry("product_title", arabic ? "سماعات لايف كيو 30 بلوتوث" : "Life Q30 Hybrid Active Noise Cancelling Headphones"),
                    Map.entry("brand", "Soundcore"),
                    Map.entry("long_description", arabic
                            ? "اسم اللون\tأزرق\nنوع الاتصال\tلاسلكي\nعدد القطع\t1"
                            : "Colour Name\tBlue\nConnection Type\tWireless\nItem Quantity\t1"),
                    Map.entry("feature_bullets", arabic
                            ? List.of("صوت عالي الدقة مع مشغلات 40 مم", "تقنية عزل ضوضاء متقدمة")
                            : List.of("Hi-Res certified music with 40mm drivers", "Advanced noise cancellation technology")),
                    Map.entry("specifications", List.of(
                            Map.of("name", "Colour Name", "value", "Blue"),
                            Map.of("name", "Connection Type", "value", "Wireless"),
                            Map.of("name", "Item Quantity", "value", "1")
                    )),
                    Map.entry("image_keys", List.of("pnsku/N53335547A/45/_/1728972591/main")),
                    Map.entry("variants", List.of(Map.of("offers", List.of(Map.of("sale_price", 79.0, "price", 99.0, "currency", "SAR"))))),
                    Map.entry("product_rating", Map.of("value", 4.5, "count", 6257))
            );
            String nextData = objectMapper.writeValueAsString(Map.of("props", Map.of("pageProps", Map.of("product", product))));
            String body = String.join("\n",
                    "<html>",
                    "  <head><script id=\"__NEXT_DATA__\" type=\"application/json\">" + nextData + "</script></head>",
                    "  <body><h1>" + product.get("product_title") + "</h1></body>",
                    "</html>"
            );
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            ProductSelectionSourceCollectionRow row = new ProductSelectionSourceCollectionRow();
            row.setSourcePlatform("Noon");
            row.setPageUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/saudi-en/life-q30/N53335547A/p/");
            NoonPublicPageSourceCollector noonCollector = new NoonPublicPageSourceCollector(
                    htmlParser,
                    new NoonPublicProductPayloadParser(htmlParser, objectMapper),
                    10,
                    "https://noon-catalog.noon.partners/_svc/catalog/api/u/"
            );

            ProductSelectionSourceCollectionResult result = noonCollector.collect(row);

            assertTrue(cookies.stream().allMatch(cookie -> cookie == null || cookie.isBlank()));
            assertEquals("Noon", result.getSourcePlatform());
            assertEquals("Life Q30 Hybrid Active Noise Cancelling Headphones", result.getSourceTitle());
            assertEquals("سماعات لايف كيو 30 بلوتوث", result.getSourceTitleAr());
            assertEquals("SAR 79.0", result.getPriceSummary());
            assertEquals("https://f.nooncdn.com/p/pnsku/N53335547A/45/_/1728972591/main.jpg", result.getSourceImageUrl());
            assertEquals(List.of("Hi-Res certified music with 40mm drivers", "Advanced noise cancellation technology"), result.getSourceSellingPointsEn());
            assertEquals(List.of("صوت عالي الدقة مع مشغلات 40 مم", "تقنية عزل ضوضاء متقدمة"), result.getSourceSellingPointsAr());
            assertTrue(result.getSourceDescriptionEn().contains("Colour Name"));
            assertTrue(result.getSourceDescriptionAr().contains("اسم اللون"));
            assertTrue(result.getSpecHints().contains("Noon SKU: N53335547A"));
            assertTrue(result.getSpecHints().contains("Brand: Soundcore"));
            assertTrue(result.getSpecHints().contains("Colour Name: Blue"));
            assertTrue(result.getSpecHints().contains("Item Quantity: 1"));
            assertTrue(result.getSpecHints().contains("Rating: 4.5 out of 5"));
            assertTrue(result.getSpecHints().contains("Review Count: 6257"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsNoonCatalogApiPayloadBeforePublicHtml() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        List<String> cookies = new CopyOnWriteArrayList<>();
        List<String> languages = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/_svc/catalog/api/u/", exchange -> {
            cookies.add(exchange.getRequestHeaders().getFirst("Cookie"));
            String language = exchange.getRequestHeaders().getFirst("X-Lang");
            languages.add(language);
            boolean arabic = "ar".equals(language);
            Map<String, Object> product = Map.ofEntries(
                    Map.entry("sku", "Z6B1AFDB7B334E2C123E8Z"),
                    Map.entry("product_title", arabic
                            ? "روب استحمام فاخر للنساء من الفرو الشيربا"
                            : "Premium Sherpa Fleece Women's Robe"),
                    Map.entry("brand", "joyzzz"),
                    Map.entry("long_description", arabic
                            ? "<p>رباط خصر عالي الجودة قابل للتعديل.</p>"
                            : "<p><strong>feature:</strong></p><p>High quality waistband that adjusts to the perfect fit.</p>"),
                    Map.entry("feature_bullets", arabic
                            ? List.of("راحة فائقة مع فرو شيربا", "جيوب أمامية عميقة")
                            : List.of("Ultra-plush Sherpa fleece comfort", "Deep front pockets for daily essentials")),
                    Map.entry("specifications", List.of(
                            Map.of("name", "Colour Name", "value", "Beige"),
                            Map.of("name", "Item Quantity", "value", "1")
                    )),
                    Map.entry("image_keys", List.of("pzsku/Z6B1AFDB7B334E2C123E8Z/45/1748068458/main")),
                    Map.entry("variants", List.of(Map.of("offers", List.of(Map.of("sale_price", 45.5, "currency", "SAR"))))),
                    Map.entry("product_rating", Map.of("value", 4.4, "count", 251))
            );
            String body = objectMapper.writeValueAsString(Map.of("product", product));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            ProductSelectionSourceCollectionRow row = new ProductSelectionSourceCollectionRow();
            row.setSourcePlatform("Noon");
            row.setPageUrl("https://www.noon.com/saudi-en/premium-sherpa-fleece-women-s-robe-ultra-soft-warm-bathrobe/Z6B1AFDB7B334E2C123E8Z/p/?o=z6b1afdb7b334e2c123e8z-1");
            NoonPublicPageSourceCollector noonCollector = new NoonPublicPageSourceCollector(
                    htmlParser,
                    new NoonPublicProductPayloadParser(htmlParser, objectMapper),
                    10,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/_svc/catalog/api/u/"
            );

            ProductSelectionSourceCollectionResult result = noonCollector.collect(row);

            assertTrue(cookies.stream().allMatch(cookie -> cookie == null || cookie.isBlank()));
            assertTrue(languages.contains("en"));
            assertTrue(languages.contains("ar"));
            assertEquals("Premium Sherpa Fleece Women's Robe", result.getSourceTitle());
            assertEquals("روب استحمام فاخر للنساء من الفرو الشيربا", result.getSourceTitleAr());
            assertEquals("SAR 45.5", result.getPriceSummary());
            assertEquals("https://f.nooncdn.com/p/pzsku/Z6B1AFDB7B334E2C123E8Z/45/1748068458/main.jpg", result.getSourceImageUrl());
            assertTrue(result.getSourceDescriptionEn().contains("High quality waistband"));
            assertTrue(result.getSourceDescriptionAr().contains("رباط خصر"));
            assertTrue(result.getSpecHints().contains("Noon SKU: Z6B1AFDB7B334E2C123E8Z"));
            assertTrue(result.getSpecHints().contains("Brand: joyzzz"));
            assertTrue(result.getSpecHints().contains("Colour Name: Beige"));
            assertTrue(result.getSpecHints().contains("Rating: 4.4 out of 5"));
            assertTrue(result.getSpecHints().contains("Review Count: 251"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void extractsAmazonProductMetadataFromHtml() {
        String html = String.join("\n",
                "<html>",
                "  <head>",
                "    <title>Electric Incense Burner : Amazon.sa</title>",
                "    <meta name=\"twitter:image\" content=\"https://m.media-amazon.com/images/I/main.jpg\">",
                "  </head>",
                "  <body>",
                "    <span class=\"a-price\"><span class=\"a-offscreen\">SAR 107.00</span></span>",
                "    <div id=\"feature-bullets\">",
                "      <ul><li>Ceramic material with USB rechargeable design</li></ul>",
                "    </div>",
                "  </body>",
                "</html>"
        );

        ProductSelectionSourceCollectionResult result = collector.collectHtml(
                html,
                "https://www.amazon.sa/-/en/example/dp/B000000000/",
                "Amazon"
        );

        assertEquals("Amazon", result.getSourcePlatform());
        assertEquals("Electric Incense Burner", result.getSourceTitle());
        assertEquals("https://m.media-amazon.com/images/I/main.jpg", result.getSourceImageUrl());
        assertEquals("SAR 107.00", result.getPriceSummary());
    }

    @Test
    void extractsSheinProductMetadataFromHtml() {
        String html = String.join("\n",
                "<html>",
                "  <head>",
                "    <meta property=\"og:title\" content=\"Portable Incense Burner - SHEIN\">",
                "    <meta property=\"og:image\" content=\"//img.ltwebstatic.com/images3_spmp/main.jpg\">",
                "  </head>",
                "  <body>",
                "    <div class=\"price\">SAR 27.00</div>",
                "    <p>Color black, compact size, gift package</p>",
                "  </body>",
                "</html>"
        );

        ProductSelectionSourceCollectionResult result = collector.collectHtml(
                html,
                "https://ar.shein.com/example-p-123.html",
                "SHEIN"
        );

        assertEquals("SHEIN", result.getSourcePlatform());
        assertEquals("Portable Incense Burner", result.getSourceTitle());
        assertEquals("https://img.ltwebstatic.com/images3_spmp/main.jpg", result.getSourceImageUrl());
        assertEquals("SAR 27.00", result.getPriceSummary());
    }

    @Test
    void mapsSheinStructuredProductPage() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/robe-p-123.html", exchange -> {
            String jsonLd = objectMapper.writeValueAsString(Map.of(
                    "@type", "Product",
                    "name", "Women's Plush Robe",
                    "sku", "sz230901112233",
                    "brand", Map.of("name", "SHEIN BASICS"),
                    "image", List.of("//img.ltwebstatic.com/images3_pi/main.jpg"),
                    "description", "Ultra soft plush bathrobe with warm lining.",
                    "offers", Map.of("priceCurrency", "SAR", "price", "64.00")
            ));
            String introData = objectMapper.writeValueAsString(Map.of(
                    "goods_name", "Women's Plush Robe",
                    "goods_sn", "sz230901112233",
                    "retailPrice", Map.of("amountWithSymbol", "SAR 64.00"),
                    "goods_img", "//img.ltwebstatic.com/images3_pi/detail.jpg",
                    "sellingPoints", List.of("Soft plush fabric", "Warm bathrobe for daily use"),
                    "productDetails", List.of(
                            Map.of("attr_name", "Color", "attr_value", "Dusty Pink"),
                            Map.of("attr_name", "Material", "attr_value", "Polyester"),
                            Map.of("attr_name", "Composition", "attr_value", "100% Polyester")
                    )
            ));
            String body = String.join("\n",
                    "<html>",
                    "  <head>",
                    "    <script type=\"application/ld+json\">" + jsonLd + "</script>",
                    "    <script>window.productIntroData = " + introData + ";</script>",
                    "  </head>",
                    "  <body><h1>Women's Plush Robe</h1></body>",
                    "</html>"
            );
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            ProductSelectionSourceCollectionRow row = new ProductSelectionSourceCollectionRow();
            row.setSourcePlatform("SHEIN");
            row.setPageUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/robe-p-123.html");
            SheinPublicPageSourceCollector sheinCollector = new SheinPublicPageSourceCollector(
                    htmlParser,
                    objectMapper,
                    10
            );

            ProductSelectionSourceCollectionResult result = sheinCollector.collect(row);

            assertEquals("SHEIN", result.getSourcePlatform());
            assertEquals("Women's Plush Robe", result.getSourceTitle());
            assertEquals("SAR 64.00", result.getPriceSummary());
            assertEquals("https://img.ltwebstatic.com/images3_pi/main.jpg", result.getSourceImageUrl());
            assertTrue(result.getImageUrls().contains("https://img.ltwebstatic.com/images3_pi/detail.jpg"));
            assertTrue(result.getSpecHints().contains("SHEIN SKU: sz230901112233"));
            assertTrue(result.getSpecHints().contains("Brand: SHEIN BASICS"));
            assertTrue(result.getSpecHints().contains("Color: Dusty Pink"));
            assertTrue(result.getSpecHints().contains("Material: Polyester"));
            assertTrue(result.getSpecHints().contains("Composition: 100% Polyester"));
            assertEquals("Ultra soft plush bathrobe with warm lining.", result.getSourceDescriptionEn());
            assertTrue(result.getSourceSellingPointsEn().contains("Soft plush fabric"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void collectsSheinThroughRapidApiBeforePublicPage() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        List<String> rapidApiKeys = new CopyOnWriteArrayList<>();
        List<String> rapidQueries = new CopyOnWriteArrayList<>();
        List<String> publicHits = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/shein/product/details", exchange -> {
            rapidApiKeys.add(exchange.getRequestHeaders().getFirst("x-rapidapi-key"));
            String query = exchange.getRequestURI().getQuery();
            rapidQueries.add(query);
            boolean arabic = URLDecoder.decode(query, StandardCharsets.UTF_8).contains("language=ar");
            String body = objectMapper.writeValueAsString(Map.of("success", true, "data", Map.ofEntries(
                    Map.entry("goods_name", arabic ? "أطراف أظافر صناعية شفافة من شي إن" : "RapidAPI SHEIN Nail Tips"),
                    Map.entry("goods_sn", "33710994"),
                    Map.entry("retailPrice", Map.of("amountWithSymbol", "USD 6.70")),
                    Map.entry("detail_image", List.of("//img.ltwebstatic.com/images3_pi/rapid-detail.jpg")),
                    Map.entry("sellingPoints", arabic
                            ? List.of("مجموعة من 600 قطعة مناسبة للتدريب والاستخدام اليومي")
                            : List.of("600 pieces full cover tips")),
                    Map.entry("description", arabic
                            ? "أطراف أظافر قابلة لإعادة الاستخدام لتدريب الصالون."
                            : "Reusable press-on nail tips for salon practice."),
                    Map.entry("productDetails", List.of(
                            Map.of("attr_name", "Color", "attr_value", "Clear"),
                            Map.of("attr_name", "Material", "attr_value", "ABS")
                    ))
            )));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/600Pcs-Full-Cover-Press-On-Nail-Tips-p-33710994.html", exchange -> {
            publicHits.add("hit");
            String body = "<html><body>captcha</body></html>";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(403, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            ProductSelectionSourceCollectionRow row = new ProductSelectionSourceCollectionRow();
            row.setSourcePlatform("SHEIN");
            row.setPageUrl("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/600Pcs-Full-Cover-Press-On-Nail-Tips-p-33710994.html?mallCode=1");
            SheinPublicPageSourceCollector sheinCollector = new SheinPublicPageSourceCollector(
                    htmlParser,
                    objectMapper,
                    10,
                    true,
                    "test-rapid-key",
                    "USD",
                    10,
                    "",
                    "http://127.0.0.1:" + server.getAddress().getPort()
                            + "/shein/product/details?goods_id={goodsId}&currency={currency}&country={country}&language={language}"
            );

            ProductSelectionSourceCollectionResult result = sheinCollector.collect(row);

            assertEquals("SHEIN", result.getSourcePlatform());
            assertEquals("RapidAPI SHEIN Nail Tips", result.getSourceTitle());
            assertEquals("أطراف أظافر صناعية شفافة من شي إن", result.getSourceTitleAr());
            assertEquals("USD 6.70", result.getPriceSummary());
            assertEquals("https://img.ltwebstatic.com/images3_pi/rapid-detail.jpg", result.getSourceImageUrl());
            assertTrue(result.getSpecHints().contains("SHEIN Product ID: 33710994"));
            assertTrue(result.getSpecHints().contains("Color: Clear"));
            assertTrue(result.getSpecHints().contains("Material: ABS"));
            assertEquals("Reusable press-on nail tips for salon practice.", result.getSourceDescriptionEn());
            assertEquals("أطراف أظافر قابلة لإعادة الاستخدام لتدريب الصالون.", result.getSourceDescriptionAr());
            assertTrue(result.getSourceSellingPointsEn().contains("600 pieces full cover tips"));
            assertTrue(result.getSourceSellingPointsAr().contains("مجموعة من 600 قطعة مناسبة للتدريب والاستخدام اليومي"));
            assertEquals("test-rapid-key", rapidApiKeys.get(0));
            assertTrue(URLDecoder.decode(rapidQueries.get(0), StandardCharsets.UTF_8).contains("goods_id=33710994"));
            assertTrue(rapidQueries.stream()
                    .map(query -> URLDecoder.decode(query, StandardCharsets.UTF_8))
                    .anyMatch(query -> query.contains("language=en")));
            assertTrue(rapidQueries.stream()
                    .map(query -> URLDecoder.decode(query, StandardCharsets.UTF_8))
                    .anyMatch(query -> query.contains("language=ar")));
            assertFalse(publicHits.contains("hit"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fallsBackToSheinUrlWhenPublicPageBlocked() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            String body = "<html><head><title>outOfService</title></head><body>System Updating</body></html>";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(403, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            ProductSelectionSourceCollectionRow row = new ProductSelectionSourceCollectionRow();
            row.setSourcePlatform("SHEIN");
            row.setPageUrl("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/1-3-5-6pcs-Artificial-Poppy-Flowers-59cm-Long-4-Flowers-Per-Stem"
                    + "-p-55110364.html?mallCode=1&main_attr=27_447");
            SheinPublicPageSourceCollector sheinCollector = new SheinPublicPageSourceCollector(
                    htmlParser,
                    objectMapper,
                    10
            );

            ProductSelectionSourceCollectionResult result = sheinCollector.collect(row);

            assertEquals("SHEIN", result.getSourcePlatform());
            assertTrue(result.getSourceTitle().contains("Artificial Poppy Flowers"));
            assertTrue(result.getSpecHints().contains("SHEIN Product ID: 55110364"));
            assertTrue(result.getSpecHints().contains("SHEIN Mall Code: 1"));
            assertTrue(result.getSpecHints().contains("SHEIN Selected Attribute: 27_447"));
            assertTrue(result.getSpecHints().contains("Source Access: public page blocked, URL fallback only"));
            assertTrue(result.getSpecHints().contains("Source Failure: HTTP 403"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void extractsProvidedSheinArabicUrlFallbackMetadata() {
        String pageUrl = "https://ar.shein.com/600Pcs-Full-Cover-Press-On-Nail-Tips-Stiletto-Almond-Square-Coffin-French-False-Fake-Soak-Off-Gel-Nail-Extension-Tips-Capsule-Press-On-Nails-Nail-Supplies-p-33710994.html?src_identifier=fc%3DBeauty%20%26%20Health%60sc%3DBeauty%20%26%20Health%60tc%3D0%60oc%3D0%60ps%3Dtab10navbar10%60jc%3DitemPicking_017172970&src_module=topcat&src_tab_page_id=page_goods_group1772522529333&mallCode=1&pageListType=4";

        ProductSelectionSourceCollectionResult result = new SheinProductUrlFallback(htmlParser)
                .fromUrl(pageUrl, "SHEIN 公网页面采集失败：Status=403");

        assertEquals("SHEIN", result.getSourcePlatform());
        assertEquals("600Pcs Full Cover Press On Nail Tips Stiletto Almond Square Coffin French False Fake Soak Off Gel Nail Extension Tips Capsule Press On Nails Nail Supplies", result.getSourceTitle());
        assertTrue(result.getSpecHints().contains("SHEIN Product ID: 33710994"));
        assertTrue(result.getSpecHints().contains("SHEIN Mall Code: 1"));
        assertTrue(result.getSpecHints().contains("Source Access: public page blocked, URL fallback only"));
        assertTrue(result.getSpecHints().contains("Source Failure: HTTP 403"));
    }

    @Test
    void extractsSheinProductIdFromChallengeRedirectionUrl() {
        String pageUrl = "https://ar.shein.com/risk/challenge?captcha_type=905&redirection="
                + "https%3A%2F%2Far.shein.com%2FSilicone-Suction-Cup-Phone-Case-p-38682625.html";

        ProductSelectionSourceCollectionResult result = new SheinProductUrlFallback(htmlParser)
                .fromUrl(pageUrl, "captcha");

        assertEquals("Silicone Suction Cup Phone Case", result.getSourceTitle());
        assertTrue(result.getSpecHints().contains("SHEIN Product ID: 38682625"));
    }

    @Test
    void mapsAmazonExternalCrawlerResponse() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        List<String> queries = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/flask/crawl", exchange -> {
            queries.add(exchange.getRequestURI().getQuery());
            String data = objectMapper.writeValueAsString(List.of(Map.of(
                    "title", "External Amazon Title",
                    "price", "SAR 99.00",
                    "images", List.of("https://m.media-amazon.com/images/I/main.jpg"),
                    "feature_bullets", List.of("Ceramic material", "Gift package"),
                    "descriptions", List.of("Long description text")
            )));
            String body = objectMapper.writeValueAsString(Map.of("code", 200, "data", data));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            ProductSelectionSourceCollectionRow row = new ProductSelectionSourceCollectionRow();
            row.setSourcePlatform("Amazon");
            row.setPageUrl("https://www.amazon.sa/-/en/example/dp/B000000000/");
            AmazonExternalCrawlerSourceCollector amazonCollector = new AmazonExternalCrawlerSourceCollector(
                    htmlParser,
                    objectMapper,
                    true,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/flask/crawl?channel={channel}&url={url}",
                    10,
                    false,
                    false
            );

            ProductSelectionSourceCollectionResult result = amazonCollector.collect(row);

            assertEquals("Amazon", result.getSourcePlatform());
            assertEquals("External Amazon Title", result.getSourceTitle());
            assertEquals("SAR 99.00", result.getPriceSummary());
            assertEquals("https://m.media-amazon.com/images/I/main.jpg", result.getSourceImageUrl());
            assertEquals("Long description text", result.getSourceDescriptionEn());
            assertEquals("Long description text", result.getSourceDescriptionAr());
            assertEquals(List.of("Ceramic material", "Gift package"), result.getSourceSellingPointsEn());
            assertEquals(List.of("Ceramic material", "Gift package"), result.getSourceSellingPointsAr());
            assertEquals("External Amazon Title", result.getSourceTitleAr());
            List<String> decodedQueries = queries.stream()
                    .map(query -> URLDecoder.decode(query, StandardCharsets.UTF_8))
                    .collect(Collectors.toList());
            assertTrue(decodedQueries.stream().anyMatch(query -> query.contains("channel=1")));
            assertTrue(decodedQueries.stream().anyMatch(query -> query.contains("language=en_AE")));
            assertTrue(decodedQueries.stream().anyMatch(query -> query.contains("language=ar_AE")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void addsAmazonPublicPageProductOverviewSpecs() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/flask/crawl", exchange -> {
            String data = objectMapper.writeValueAsString(List.of(Map.of(
                    "title", "External Amazon Title",
                    "price", "$23.99",
                    "images", List.of("https://m.media-amazon.com/images/I/main.jpg"),
                    "feature_bullets", List.of("Silk")
            )));
            String body = objectMapper.writeValueAsString(Map.of("code", 200, "data", data));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/gp/aw/d/B000000000", exchange -> {
            String body = String.join("\n",
                    "<html><body>",
                    "<div id=\"productOverview_feature_div\">",
                    "  <div id=\"productOverview_hoc_view_div\">",
                    "    <div class=\"a-row po-brand\"><div class=\"a-column a-span5\"><span class=\"a-text-bold\">Brand</span></div><div class=\"a-column a-span7\"><span>DUYONE</span></div></div>",
                    "    <div class=\"a-row po-plant_or_animal_product_type\"><div class=\"a-column a-span5\"><span class=\"a-text-bold\">Plant or Animal Product Type</span></div><div class=\"a-column a-span7\"><span>Artificial Mohnblume</span></div></div>",
                    "    <div class=\"a-row po-color\"><div class=\"a-column a-span5\"><span class=\"a-text-bold\">Color</span></div><div class=\"a-column a-span7\"><span>Orange Pink 6pcs</span></div></div>",
                    "    <div class=\"a-row po-material\"><div class=\"a-column a-span5\"><span class=\"a-text-bold\">Material</span></div><div class=\"a-column a-span7\"><span>Silk</span></div></div>",
                    "    <div class=\"a-row po-item_depth_width_height\"><div class=\"a-column a-span5\"><span class=\"a-text-bold\">Product Dimensions</span></div><div class=\"a-column a-span7\"><span>0.4&quot;D x 2&quot;W x 23&quot;H</span></div></div>",
                    "  </div>",
                    "</div>",
                    "<div id=\"averageCustomerReviews\"><i class=\"a-icon a-icon-star\"><span class=\"a-icon-alt\">4.4 out of 5 stars</span></i></div>",
                    "<span id=\"acrCustomerReviewText\" aria-label=\"494 Reviews\">(494)</span>",
                    "<table id=\"productDetails_techSpec_section_1\">",
                    "  <tr><th>ASIN</th><td>B000000000</td></tr>",
                    "  <tr><th>Package Quantity</th><td>1</td></tr>",
                    "  <tr><th>Unit Count</th><td>6.0 Count</td></tr>",
                    "  <tr><th>Best Sellers Rank</th><td>#38,743 in Home & Kitchen</td></tr>",
                    "</table>",
                    "</body></html>"
            );
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            ProductSelectionSourceCollectionRow row = new ProductSelectionSourceCollectionRow();
            row.setSourcePlatform("Amazon");
            row.setPageUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/dp/B000000000?th=1");
            AmazonExternalCrawlerSourceCollector amazonCollector = new AmazonExternalCrawlerSourceCollector(
                    htmlParser,
                    objectMapper,
                    true,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/flask/crawl?channel={channel}&url={url}",
                    10,
                    false,
                    true
            );

            ProductSelectionSourceCollectionResult result = amazonCollector.collect(row);

            assertTrue(result.getSpecHints().contains("Brand: DUYONE"));
            assertTrue(result.getSpecHints().contains("Plant or Animal Product Type: Artificial Mohnblume"));
            assertTrue(result.getSpecHints().contains("Color: Orange Pink 6pcs"));
            assertTrue(result.getSpecHints().contains("Material: Silk"));
            assertTrue(result.getSpecHints().contains("Product Dimensions: 0.4\"D x 2\"W x 23\"H"));
            assertTrue(result.getSpecHints().contains("Rating: 4.4 out of 5 stars"));
            assertTrue(result.getSpecHints().contains("Review Count: 494"));
            assertTrue(result.getSpecHints().contains("Unit Count: 6.0 Count"));
            assertTrue(result.getSpecHints().contains("Best Sellers Rank: #38,743 in Home & Kitchen"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejects1688CollectionForCurrentScope() {
        ProductSelectionSourceCollectionRow row = new ProductSelectionSourceCollectionRow();
        row.setSourcePlatform("1688");
        row.setPageUrl("https://detail.1688.com/offer/123456.html");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> collector.collect(row));

        assertTrue(exception.getMessage().contains("Noon、Amazon 和 SHEIN"));
    }
}
