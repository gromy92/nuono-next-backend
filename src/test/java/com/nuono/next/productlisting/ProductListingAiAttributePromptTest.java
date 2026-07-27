package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.ai.AiStructuredTextCommand;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductListingAiAttributePromptTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void factExtractionPromptMustNotExposeUnselectedAttributeOptions() throws Exception {
        ProductListingDraftCommand draft = draft();

        AiStructuredTextCommand command = new ProductListingAiFactExtractionFactory(objectMapper).create(null, draft);
        Map<?, ?> prompt = objectMapper.readValue(command.getPrompt(), Map.class);
        List<?> attributes = (List<?>) prompt.get("keyAttributes");

        assertEquals(2, attributes.size());
        assertTrue(attributes.toString().contains("black"));
        assertTrue(attributes.toString().contains("MILKYWAYA04"));
        assertFalse(attributes.toString().contains("options"));
        assertFalse(attributes.toString().contains("LED"));
        assertTrue(command.getInstructions().contains("never a JSON fragment"));
        assertTrue(command.getInstructions().contains("one independently meaningful fact per ledger entry"));
        assertTrue(command.getInstructions().contains("titleRequired=false for audience and usage-scenario facts"));
        assertTrue(command.getInstructions().contains("基础款"));
        assertTrue(command.getInstructions().contains("not purchase-defining physical facts"));
        assertTrue(command.getInstructions().contains("selected key attribute wins for that same attribute"));
        assertTrue(command.getInstructions().contains("compact translation into the target language"));
    }

    @Test
    void listingGenerationPromptMustUseTheSameSelectedAttributeEvidence() throws Exception {
        ProductListingDraftCommand draft = draft();
        ProductListingAiListingCommand listingCommand = new ProductListingAiListingCommand();
        listingCommand.setDraft(draft);

        AiStructuredTextCommand command = new ProductListingAiRequestFactory(objectMapper).create(
                null,
                listingCommand,
                draft,
                List.of(),
                ProductListingAiFactLedger.from(Map.of())
        );
        Map<?, ?> prompt = objectMapper.readValue(command.getPrompt(), Map.class);
        Map<?, ?> verifiedFacts = (Map<?, ?>) prompt.get("verifiedFacts");
        List<?> attributes = (List<?>) verifiedFacts.get("keyAttributes");

        assertEquals(2, attributes.size());
        assertFalse(attributes.toString().contains("options"));
        assertFalse(attributes.toString().contains("LED"));
        assertTrue(command.getInstructions().contains("already resolved by the verified structured value"));
        assertTrue(command.getInstructions().contains("Do not require a canonical phrase from one language"));
    }

    private ProductListingDraftCommand draft() {
        ProductListingDraftCommand draft = new ProductListingDraftCommand();
        draft.setStoreCode("STR245027-NAE");
        draft.setProductTitleEn("Galaxy Projector Bedroom Night Light");
        draft.setKeyAttributes(List.of(
                Map.of(
                        "code", "colour_family",
                        "commonValue", "black",
                        "options", List.of(Map.of("value", "blue", "en", "Blue"))
                ),
                Map.of(
                        "code", "lighting_technology",
                        "options", List.of(Map.of("value", "led", "en", "LED", "ar", "LED"))
                ),
                Map.of("code", "mpn", "enValue", "MILKYWAYA04")
        ));
        return draft;
    }
}
