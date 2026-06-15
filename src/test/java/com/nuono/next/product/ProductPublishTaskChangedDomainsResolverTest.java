package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductPublishTaskChangedDomainsResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returnsStoredDomainsWithoutRecomputeWhenDomainsAreKnown() {
        ProductPublishTaskChangedDomainsResolver.ChangedDomainsRecomputer recomputer =
                mock(ProductPublishTaskChangedDomainsResolver.ChangedDomainsRecomputer.class);
        ProductPublishTaskChangedDomainsResolver resolver =
                new ProductPublishTaskChangedDomainsResolver(objectMapper, recomputer);
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setChangedDomainsJson("[\"content\",\"site_offer\"]");

        List<String> domains = resolver.resolve(task);

        assertEquals(List.of("content", "site_offer"), domains);
        verifyNoInteractions(recomputer);
    }

    @Test
    void recomputesWhenStoredDomainsAreUnknown() throws Exception {
        ProductPublishTaskChangedDomainsResolver resolver =
                new ProductPublishTaskChangedDomainsResolver(objectMapper, (draft, baseline, currentSiteCode) -> {
                    assertEquals("SA", currentSiteCode);
                    assertEquals("draft title", draft.getContent().get("titleEn"));
                    assertEquals("baseline title", baseline.getContent().get("titleEn"));
                    return List.of("content");
                });
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setCurrentSiteCode("SA");
        task.setChangedDomainsJson("[\"unknown\"]");
        task.setBaselineJson(writeSnapshot("baseline title"));
        task.setDraftJson(writeSnapshot("draft title"));

        List<String> domains = resolver.resolve(task);

        assertEquals(List.of("content"), domains);
    }

    @Test
    void fallsBackToStoredDomainsWhenRecomputeFails() {
        ProductPublishTaskChangedDomainsResolver resolver =
                new ProductPublishTaskChangedDomainsResolver(objectMapper, (draft, baseline, currentSiteCode) -> {
                    throw new IllegalStateException("boom");
                });
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setChangedDomainsJson("[\"unknown\"]");
        task.setBaselineJson("{bad json");
        task.setDraftJson("{bad json");

        List<String> domains = resolver.resolve(task);

        assertEquals(List.of("unknown"), domains);
    }

    private String writeSnapshot(String titleEn) throws Exception {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.getContent().put("titleEn", titleEn);
        return objectMapper.writeValueAsString(snapshot);
    }
}
