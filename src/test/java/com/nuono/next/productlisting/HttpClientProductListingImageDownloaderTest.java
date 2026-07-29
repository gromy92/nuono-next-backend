package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HttpClientProductListingImageDownloaderTest {

    @Test
    void normalizesRawNoonCompositeCdnUrlBeforeCreatingUri() {
        assertEquals(
                "https://f.nooncdn.com/p/b7776acb5051594e983a3ec63aeacccc%7Cpzsku/"
                        + "Z043837C2EDFD1C9F1378Z/45/1770263594/"
                        + "55860709-2301-4d29-9bc1-d5d2108e8fb8.jpg",
                HttpClientProductListingImageDownloader.normalizeExternalImageUrl(
                        "https://f.nooncdn.com/p/b7776acb5051594e983a3ec63aeacccc|pzsku/"
                                + "Z043837C2EDFD1C9F1378Z/45/1770263594/"
                                + "55860709-2301-4d29-9bc1-d5d2108e8fb8.jpg"
                )
        );
    }

    @Test
    void downloadsLocalProductImageAssetFromUploadDirectory() throws Exception {
        Path uploadDir = Path.of(System.getProperty("java.io.tmpdir"), "nuono-next-product-images");
        Files.createDirectories(uploadDir);
        String filename = UUID.randomUUID() + ".jpg";
        Path file = uploadDir.resolve(filename);
        byte[] content = new byte[] {1, 2, 3, 4};
        Files.write(file, content);
        try {
            ProductListingImageDownload download = new HttpClientProductListingImageDownloader()
                    .download("/api/product-master/image-assets/" + filename);

            assertEquals(filename, download.fileName);
            assertEquals("image/jpeg", download.contentType);
            assertArrayEquals(content, download.content);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
