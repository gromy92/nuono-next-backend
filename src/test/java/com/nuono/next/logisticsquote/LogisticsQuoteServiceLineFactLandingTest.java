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

class LogisticsQuoteServiceLineFactLandingTest {

    @Test
    void shouldLandPublishedServiceLineFactAndReadBackSource() {
        InMemoryFactRepository repository = new InMemoryFactRepository();
        LogisticsQuoteFactPublisher publisher = new LogisticsQuoteFactPublisher(repository);

        LogisticsQuoteFactLandingResult result = publisher.land(List.of(serviceLineItem(
                20112L,
                40104L,
                70024L,
                88001L,
                "et|SA|FBN|air|headhaul|RUH",
                mapOf(
                        "forwarderCode", "et",
                        "forwarderName", "ET/易通",
                        "country", "SA",
                        "fulfillmentMode", "FBN",
                        "transportMode", "air",
                        "serviceScope", "headhaul",
                        "channelName", "沙特空运",
                        "originWarehouse", "广州仓",
                        "destinationWarehouse", "RUH",
                        "destinationNode", "Riyadh",
                        "departureFrequency", "weekly",
                        "leadTimeMinDays", 6,
                        "leadTimeMaxDays", 9,
                        "effectiveDate", "2026-04-14",
                        "sourceVersion", "LOGISTICS-ET-20260522-70024"
                )
        )));

        assertEquals(1, result.getInsertedCount());
        assertEquals(0, result.getUnchangedCount());
        List<LogisticsServiceLineFact> facts = repository.findActiveServiceLines(
                new LogisticsServiceLineQuery("et", "SA", "air", "headhaul")
        );
        assertEquals(1, facts.size());

        LogisticsServiceLineFact fact = facts.get(0);
        assertEquals("et", fact.getForwarderCode());
        assertEquals("ET/易通", fact.getForwarderName());
        assertEquals("SA", fact.getCountry());
        assertEquals("air", fact.getTransportMode());
        assertEquals("headhaul", fact.getServiceScope());
        assertEquals("沙特空运", fact.getChannelName());
        assertEquals("广州仓", fact.getOriginWarehouse());
        assertEquals("RUH", fact.getDestinationWarehouse());
        assertEquals("Riyadh", fact.getDestinationNode());
        assertEquals(Integer.valueOf(6), fact.getEstimatedDaysMin());
        assertEquals(Integer.valueOf(9), fact.getEstimatedDaysMax());
        assertEquals("ACTIVE", fact.getStatus());

        LogisticsQuoteFactSourceLineage source = fact.getSourceLineage();
        assertEquals("file_management", source.getSourceType());
        assertEquals(20112L, source.getSourceTaskId());
        assertEquals(40104L, source.getSourceResultId());
        assertEquals(70024L, source.getSourceVersionId());
        assertEquals(88001L, source.getSourceVersionItemId());
        assertEquals("ET物流报价-20260414入仓生效.pdf", source.getSourceFileName());
        assertEquals("page 1 row 4", source.getSourceLocator());
    }

    @Test
    void shouldKeepServiceLineLandingIdempotentForSameSourceVersionItem() {
        InMemoryFactRepository repository = new InMemoryFactRepository();
        LogisticsQuoteFactPublisher publisher = new LogisticsQuoteFactPublisher(repository);
        LogisticsQuotePublishedItem item = serviceLineItem(
                20106L,
                40098L,
                70023L,
                88002L,
                "yite|SA|FBN|air|headhaul|RUH",
                mapOf(
                        "forwarderCode", "yite",
                        "forwarderName", "义特物流",
                        "country", "SA",
                        "fulfillmentMode", "FBN",
                        "transportMode", "air",
                        "serviceScope", "headhaul",
                        "destinationNode", "Riyadh"
                )
        );

        LogisticsQuoteFactLandingResult first = publisher.land(List.of(item));
        LogisticsQuoteFactLandingResult second = publisher.land(List.of(item));

        assertEquals(1, first.getInsertedCount());
        assertEquals(0, first.getUnchangedCount());
        assertEquals(0, second.getInsertedCount());
        assertEquals(1, second.getUnchangedCount());
        assertEquals(1, repository.findActiveServiceLines(
                new LogisticsServiceLineQuery("yite", "SA", "air", "headhaul")
        ).size());
    }

    private static LogisticsQuotePublishedItem serviceLineItem(
            Long sourceTaskId,
            Long sourceResultId,
            Long sourceVersionId,
            Long sourceVersionItemId,
            String naturalKey,
            Map<String, Object> payload
    ) {
        return new LogisticsQuotePublishedItem(
                "logistics_service_line",
                naturalKey,
                payload,
                new LogisticsQuoteFactSourceLineage(
                        "file_management",
                        sourceTaskId,
                        sourceResultId,
                        sourceVersionId,
                        sourceVersionItemId,
                        "ET物流报价-20260414入仓生效.pdf",
                        "page 1 row 4"
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

        @Override
        public Optional<LogisticsServiceLineFact> findServiceLineBySourceVersionItemId(Long sourceVersionItemId) {
            return serviceLines.stream()
                    .filter(fact -> sourceVersionItemId.equals(fact.getSourceLineage().getSourceVersionItemId()))
                    .findFirst();
        }

        @Override
        public void insertServiceLine(LogisticsServiceLineFact fact) {
            assertFalse(findServiceLineBySourceVersionItemId(fact.getSourceLineage().getSourceVersionItemId()).isPresent());
            serviceLines.add(fact);
        }

        @Override
        public List<LogisticsServiceLineFact> findActiveServiceLines(LogisticsServiceLineQuery query) {
            List<LogisticsServiceLineFact> matches = new ArrayList<>();
            for (LogisticsServiceLineFact fact : serviceLines) {
                if (!LogisticsQuoteFactStatus.ACTIVE.value().equals(fact.getStatus())) {
                    continue;
                }
                if (query.matches(fact)) {
                    matches.add(fact);
                }
            }
            return matches;
        }
    }
}
