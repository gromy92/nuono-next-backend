package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HttpClientProductListingImageDownloaderTest {

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
