package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductSnapshotTemplateFetcherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ProductAttributeTemplateService productAttributeTemplateService;

    private ProductSnapshotTemplateFetcher fetcher;

    @BeforeEach
    void setUp() {
        fetcher = new ProductSnapshotTemplateFetcher(productAttributeTemplateService);
    }

    @Test
    void shouldSkipTemplateLoadWhenProductFulltypeIsBlank() {
        List<String> warnings = new ArrayList<>();
        List<String> stageNames = new ArrayList<>();

        JsonNode result = fetcher.fetch(
                null,
                "PRJ108065",
                "STR245027-NSA",
                " ",
                307L,
                warnings,
                (stageName, startedAt) -> stageNames.add(stageName)
        );

        assertTrue(result.isMissingNode());
        assertEquals(List.of(), stageNames);
        verify(productAttributeTemplateService, never()).loadTemplate(
                isNull(),
                eq("PRJ108065"),
                eq("STR245027-NSA"),
                eq(" "),
                eq(307L),
                anyList()
        );
    }

    @Test
    void shouldLoadTemplateAndRecordStageWhenProductFulltypeExists() throws Exception {
        JsonNode templateRoot = objectMapper.readTree("{\"data\":{\"fields\":[{\"code\":\"brand\"}]}}");
        List<String> warnings = new ArrayList<>();
        List<String> stageNames = new ArrayList<>();
        when(productAttributeTemplateService.loadTemplate(
                isNull(),
                eq("PRJ108065"),
                eq("STR245027-NSA"),
                eq("home_decor-home_decor_accents"),
                eq(307L),
                eq(warnings)
        )).thenReturn(templateRoot);

        JsonNode result = fetcher.fetch(
                null,
                "PRJ108065",
                "STR245027-NSA",
                "home_decor-home_decor_accents",
                307L,
                warnings,
                (stageName, startedAt) -> stageNames.add(stageName)
        );

        assertEquals(templateRoot, result);
        assertEquals(List.of("fulltypeTemplate"), stageNames);
    }
}
