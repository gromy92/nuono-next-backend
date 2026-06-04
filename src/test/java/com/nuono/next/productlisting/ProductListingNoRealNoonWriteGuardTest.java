package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ProductListingNoRealNoonWriteGuardTest {

    @Test
    void productListingPackageDoesNotReferenceRealNoonWriteCalls() throws IOException {
        Path sourceDir = Path.of("src/main/java/com/nuono/next/productlisting");
        String source = Files.walk(sourceDir)
                .filter(path -> path.toString().endsWith(".java"))
                .map(this::read)
                .collect(Collectors.joining("\n"))
                .toLowerCase(Locale.ROOT);

        for (String forbidden : forbiddenWriteMarkers()) {
            assertFalse(source.contains(forbidden), "Forbidden Noon write marker found: " + forbidden);
        }
    }

    private List<String> forbiddenWriteMarkers() {
        return List.of(
                "nooncreateskucall",
                "noonskucachecall",
                "noonupsert",
                "createsku",
                "create-psku",
                "zsku/upsert",
                "upsertsku",
                "uploadimg"
        );
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read " + path, exception);
        }
    }
}
