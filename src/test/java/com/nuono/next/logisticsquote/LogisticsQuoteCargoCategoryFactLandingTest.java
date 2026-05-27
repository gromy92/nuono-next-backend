package com.nuono.next.logisticsquote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LogisticsQuoteCargoCategoryFactLandingTest {

    @Test
    void shouldLandCargoCategoryFactLinkedToServiceLineAndSource() {
        InMemoryFactRepository repository = new InMemoryFactRepository();
        LogisticsQuoteFactPublisher publisher = new LogisticsQuoteFactPublisher(repository);
        LogisticsQuotePublishedItem serviceLine = publishedItem(
                "logistics_service_line",
                "et|SA|air|headhaul|RUH",
                88001L,
                mapOf(
                        "forwarderCode", "et",
                        "forwarderName", "ET/易通",
                        "country", "SA",
                        "fulfillmentMode", "FBN",
                        "transportMode", "air",
                        "serviceScope", "headhaul",
                        "destinationNode", "Riyadh"
                )
        );
        LogisticsQuotePublishedItem category = publishedItem(
                "logistics_cargo_category",
                "et|et|SA|air|headhaul|RUH|A",
                88002L,
                mapOf(
                        "forwarderCode", "et",
                        "serviceLineKey", "et|SA|air|headhaul|RUH",
                        "categoryCode", "A",
                        "categoryName", "普货",
                        "productExamples", "家居用品、服饰",
                        "keywords", "home,fashion",
                        "electricType", "none",
                        "sensitiveTags", "none",
                        "packingPolicy", "可混装",
                        "manualConfirmRequired", false
                )
        );

        LogisticsQuoteFactLandingResult result = publisher.land(List.of(serviceLine, category));

        assertEquals(2, result.getInsertedCount());
        List<LogisticsCargoCategoryFact> categories = repository.findActiveCargoCategories("et", "et|SA|air|headhaul|RUH");
        assertEquals(1, categories.size());
        LogisticsCargoCategoryFact fact = categories.get(0);
        assertEquals("et", fact.getForwarderCode());
        assertEquals("et|SA|air|headhaul|RUH", fact.getServiceLineKey());
        assertEquals("A", fact.getCategoryCode());
        assertEquals("普货", fact.getCategoryName());
        assertEquals("家居用品、服饰", fact.getProductExamples());
        assertEquals("home,fashion", fact.getKeywords());
        assertFalse(fact.isManualConfirmRequired());
        assertTrue(fact.isComparable());
        assertEquals("ACTIVE", fact.getStatus());
        assertEquals(88002L, fact.getSourceLineage().getSourceVersionItemId());
    }

    @Test
    void shouldKeepManualConfirmCategoryVisibleButNotComparable() {
        InMemoryFactRepository repository = new InMemoryFactRepository();
        LogisticsQuoteFactPublisher publisher = new LogisticsQuoteFactPublisher(repository);

        LogisticsQuoteFactLandingResult result = publisher.land(List.of(publishedItem(
                "logistics_cargo_category",
                "et|et|SA|air|headhaul|RUH|battery",
                88003L,
                mapOf(
                        "forwarderCode", "et",
                        "serviceLineKey", "et|SA|air|headhaul|RUH",
                        "categoryCode", "B",
                        "categoryName", "带电货",
                        "manualConfirmRequired", true
                )
        )));

        assertEquals(1, result.getInsertedCount());
        LogisticsCargoCategoryFact fact = repository.findActiveCargoCategories("et", "et|SA|air|headhaul|RUH").get(0);
        assertTrue(fact.isManualConfirmRequired());
        assertFalse(fact.isComparable());
        assertEquals("PENDING_MANUAL_CONFIRM", fact.getStatus());
    }

    @Test
    void shouldSkipCargoCategoryMissingRequiredFields() {
        InMemoryFactRepository repository = new InMemoryFactRepository();
        LogisticsQuoteFactPublisher publisher = new LogisticsQuoteFactPublisher(repository);

        LogisticsQuoteFactLandingResult result = publisher.land(List.of(publishedItem(
                "logistics_cargo_category",
                "missing-category-name",
                88004L,
                mapOf(
                        "forwarderCode", "et",
                        "serviceLineKey", "et|SA|air|headhaul|RUH"
                )
        )));

        assertEquals(0, result.getInsertedCount());
        assertEquals(1, result.getSkippedCount());
        assertTrue(repository.findActiveCargoCategories("et", "et|SA|air|headhaul|RUH").isEmpty());
    }

    private static LogisticsQuotePublishedItem publishedItem(
            String itemType,
            String naturalKey,
            Long sourceVersionItemId,
            Map<String, Object> payload
    ) {
        return new LogisticsQuotePublishedItem(
                itemType,
                naturalKey,
                payload,
                new LogisticsQuoteFactSourceLineage(
                        "file_management",
                        20112L,
                        40104L,
                        70024L,
                        sourceVersionItemId,
                        "ET物流报价-20260414入仓生效.pdf",
                        "page 2 row 8"
                )
        );
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    private static final class InMemoryFactRepository implements LogisticsQuoteFactRepository {

        private final List<LogisticsServiceLineFact> serviceLines = new ArrayList<>();
        private final List<LogisticsCargoCategoryFact> categories = new ArrayList<>();

        @Override
        public Optional<LogisticsServiceLineFact> findServiceLineBySourceVersionItemId(Long sourceVersionItemId) {
            return serviceLines.stream()
                    .filter(fact -> sourceVersionItemId.equals(fact.getSourceLineage().getSourceVersionItemId()))
                    .findFirst();
        }

        @Override
        public void insertServiceLine(LogisticsServiceLineFact fact) {
            serviceLines.add(fact);
        }

        @Override
        public List<LogisticsServiceLineFact> findActiveServiceLines(LogisticsServiceLineQuery query) {
            List<LogisticsServiceLineFact> matches = new ArrayList<>();
            for (LogisticsServiceLineFact fact : serviceLines) {
                if (LogisticsQuoteFactStatus.ACTIVE.value().equals(fact.getStatus()) && query.matches(fact)) {
                    matches.add(fact);
                }
            }
            return matches;
        }

        @Override
        public Optional<LogisticsCargoCategoryFact> findCargoCategoryBySourceVersionItemId(Long sourceVersionItemId) {
            return categories.stream()
                    .filter(fact -> sourceVersionItemId.equals(fact.getSourceLineage().getSourceVersionItemId()))
                    .findFirst();
        }

        @Override
        public void insertCargoCategory(LogisticsCargoCategoryFact fact) {
            categories.add(fact);
        }

        @Override
        public List<LogisticsCargoCategoryFact> findActiveCargoCategories(String forwarderCode, String serviceLineKey) {
            List<LogisticsCargoCategoryFact> matches = new ArrayList<>();
            for (LogisticsCargoCategoryFact fact : categories) {
                if (forwarderCode.equals(fact.getForwarderCode()) && serviceLineKey.equals(fact.getServiceLineKey())) {
                    matches.add(fact);
                }
            }
            return matches;
        }
    }
}
