package com.nuono.next.noonpull;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.NoonOrderFactMapper;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class NoonOrderFactProjectionIsolationContractTest {
    private static final Path MAIN_SOURCE = Path.of("src/main/java/com/nuono/next");
    private static final List<String> FORBIDDEN_PRODUCT_MUTATIONS = List.of(
            "UPDATE product_site_offer",
            "INSERT INTO product_site_offer",
            "DELETE FROM product_site_offer",
            "markProductSiteOffer"
    );

    @Test
    void dp02MapperAndWriterOwnOnlyOrderFacts() throws IOException {
        String mapper = read("infrastructure/mapper/NoonOrderFactMapper.java");
        String writer = read("noonpull/MyBatisNoonOrderFactWriter.java");

        assertThat(NoonOrderFactMapper.class.getDeclaredMethods())
                .extracting(Method::getName)
                .doesNotContain("markProductSiteOfferLogisticsHistoryByOrderLineFact");
        assertThat(mapper)
                .contains("INSERT INTO noon_order_line_fact")
                .doesNotContain(FORBIDDEN_PRODUCT_MUTATIONS.toArray(String[]::new));
        assertThat(writer)
                .contains("mapper.upsertOrderLineFact(id, fact)")
                .doesNotContain(FORBIDDEN_PRODUCT_MUTATIONS.toArray(String[]::new));
    }

    @Test
    void dpRuntimeSourcesDoNotMutateProductSiteOffers() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path root : runtimeRoots()) {
            try (Stream<Path> sources = Files.walk(root)) {
                sources.filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> inspect(path, violations));
            }
        }

        assertThat(violations).isEmpty();
    }

    private List<Path> runtimeRoots() {
        return List.of(
                MAIN_SOURCE.resolve("datapull"),
                MAIN_SOURCE.resolve("noonpull"),
                MAIN_SOURCE.resolve("officialwarehouse/datapull"),
                MAIN_SOURCE.resolve("productpublicdetail/datapull"),
                MAIN_SOURCE.resolve("competitoranalysis/dp08"),
                MAIN_SOURCE.resolve("procurement/aliorder/datapull")
        );
    }

    private void inspect(Path source, List<String> violations) {
        try {
            String content = Files.readString(source);
            for (String forbidden : FORBIDDEN_PRODUCT_MUTATIONS) {
                if (content.contains(forbidden)) {
                    violations.add(MAIN_SOURCE.relativize(source) + " contains " + forbidden);
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot inspect " + source, failure);
        }
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(MAIN_SOURCE.resolve(relativePath));
    }
}
