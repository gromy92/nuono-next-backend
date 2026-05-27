package com.nuono.next.logisticsquote;

import static com.nuono.next.logisticsquote.LogisticsQuoteBasePriceFactLandingTest.mapOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LogisticsQuoteComparisonServiceTest {

    @Test
    void shouldCompareBasePricesAcrossCurrentAndFutureForwardersForSameAirCargo() {
        InMemoryFactRepository repository = new InMemoryFactRepository();
        LogisticsQuoteFactPublisher publisher = new LogisticsQuoteFactPublisher(repository);
        LogisticsQuoteComparisonService comparisonService = new LogisticsQuoteComparisonService(repository);

        publisher.land(List.of(
                serviceLine("yite|SA|air|headhaul|RUH", "yite"),
                serviceLine("et|SA|air|headhaul|RUH", "et"),
                serviceLine("qike|SA|air|headhaul|RUH", "qike"),
                serviceLine("helian|SA|air|headhaul|RUH", "helian"),
                serviceLine("chic|SA|air|headhaul|RUH", "chic"),
                category("yite|SA|air|headhaul|RUH|A", "yite", "yite|SA|air|headhaul|RUH"),
                category("et|SA|air|headhaul|RUH|A", "et", "et|SA|air|headhaul|RUH"),
                category("qike|SA|air|headhaul|RUH|A", "qike", "qike|SA|air|headhaul|RUH"),
                category("helian|SA|air|headhaul|RUH|A", "helian", "helian|SA|air|headhaul|RUH"),
                category("chic|SA|air|headhaul|RUH|A", "chic", "chic|SA|air|headhaul|RUH"),
                price("yite|SA|air|headhaul|RUH|A|kg", "yite", "yite|SA|air|headhaul|RUH", "yite|SA|air|headhaul|RUH|A", "64"),
                price("et|SA|air|headhaul|RUH|A|kg", "et", "et|SA|air|headhaul|RUH", "et|SA|air|headhaul|RUH|A", "65"),
                price("qike|SA|air|headhaul|RUH|A|kg", "qike", "qike|SA|air|headhaul|RUH", "qike|SA|air|headhaul|RUH|A", "63"),
                price("helian|SA|air|headhaul|RUH|A|kg", "helian", "helian|SA|air|headhaul|RUH", "helian|SA|air|headhaul|RUH|A", "66"),
                price("chic|SA|air|headhaul|RUH|A|kg", "chic", "chic|SA|air|headhaul|RUH", "chic|SA|air|headhaul|RUH|A", "62"),
                price("ask|SA|air|headhaul|RUH|A|kg", "ask", "qike|SA|air|headhaul|RUH", "qike|SA|air|headhaul|RUH|A", null)
        ));

        LogisticsQuoteComparisonResult result = comparisonService.compareBasePrices(new LogisticsQuoteComparisonQuery(
                "SA",
                "air",
                "headhaul",
                "普货",
                "kg"
        ));

        assertEquals(5, result.getRows().size());
        assertEquals("chic", result.getRows().get(0).getForwarderCode());
        assertEquals(new BigDecimal("62"), result.getRows().get(0).getUnitPrice());
        assertEquals("qike", result.getRows().get(1).getForwarderCode());
        assertEquals(new BigDecimal("63"), result.getRows().get(1).getUnitPrice());
        assertEquals("yite", result.getRows().get(2).getForwarderCode());
        assertEquals(new BigDecimal("64"), result.getRows().get(2).getUnitPrice());
        assertEquals("et", result.getRows().get(3).getForwarderCode());
        assertEquals(new BigDecimal("65"), result.getRows().get(3).getUnitPrice());
        assertEquals("helian", result.getRows().get(4).getForwarderCode());
        assertEquals(new BigDecimal("66"), result.getRows().get(4).getUnitPrice());
        assertEquals("chic", result.getCheapest().getForwarderCode());
        assertEquals("file_management", result.getCheapest().getSourceLineage().getSourceType());
        assertEquals("quote.pdf", result.getCheapest().getSourceLineage().getSourceFileName());
        assertEquals("page 1", result.getCheapest().getSourceLineage().getSourceLocator());
    }

    @Test
    void shouldComparePricesWhenSourceRowsReferenceYiteServiceLineAlias() {
        InMemoryFactRepository repository = new InMemoryFactRepository();
        LogisticsQuoteFactPublisher publisher = new LogisticsQuoteFactPublisher(repository);
        LogisticsQuoteComparisonService comparisonService = new LogisticsQuoteComparisonService(repository);
        String canonicalServiceLineKey = "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh";
        String sourceAlias = "yite-YT-SAU-UNDATED-001-KSA-Riyadh-sea-ddp-fbn";

        publisher.land(List.of(
                new LogisticsQuotePublishedItem(
                        "logistics_service_line",
                        canonicalServiceLineKey,
                        mapOf(
                                "forwarderCode", "yite",
                                "forwarderName", "义特物流",
                                "country", "KSA",
                                "transportMode", "sea",
                                "serviceScope", "warehouse_to_fbn",
                                "destinationNode", "KSA/Riyadh",
                                "serviceLineKey", sourceAlias
                        ),
                        lineage(91001)
                ),
                new LogisticsQuotePublishedItem(
                        "logistics_cargo_category",
                        "yite|" + sourceAlias + "|A",
                        mapOf(
                                "forwarderCode", "yite",
                                "serviceLineKey", sourceAlias,
                                "categoryCode", "A",
                                "categoryName", "普货"
                        ),
                        lineage(91002)
                ),
                new LogisticsQuotePublishedItem(
                        "logistics_base_price",
                        "yite|" + sourceAlias + "|A|cbm|unit_price",
                        mapOf(
                                "forwarderCode", "yite",
                                "serviceLineKey", sourceAlias,
                                "cargoCategoryKey", "A",
                                "unitPrice", "120",
                                "currency", "SAR",
                                "billingUnit", "cbm",
                                "pricingModel", "unit_price",
                                "priceStatus", "NORMAL"
                        ),
                        lineage(91003)
                )
        ));

        LogisticsQuoteComparisonResult result = comparisonService.compareBasePrices(new LogisticsQuoteComparisonQuery(
                "KSA",
                "sea",
                "warehouse_to_fbn",
                "普货",
                "cbm"
        ));

        assertEquals(1, result.getRows().size());
        LogisticsPriceRuleFact price = result.getRows().get(0);
        assertEquals("yite", price.getForwarderCode());
        assertEquals(canonicalServiceLineKey, price.getServiceLineKey());
        assertEquals("yite|" + canonicalServiceLineKey + "|A", price.getCargoCategoryKey());
        assertEquals(new BigDecimal("120"), price.getUnitPrice());
    }

    @Test
    void shouldSkipSourceAliasPriceRowsThatCannotAttachToPublishedServiceLine() {
        InMemoryFactRepository repository = new InMemoryFactRepository();
        LogisticsQuoteFactPublisher publisher = new LogisticsQuoteFactPublisher(repository);

        LogisticsQuoteFactLandingResult landingResult = publisher.land(List.of(
                new LogisticsQuotePublishedItem(
                        "logistics_service_line",
                        "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                        mapOf(
                                "forwarderCode", "yite",
                                "country", "KSA",
                                "transportMode", "sea",
                                "serviceScope", "warehouse_to_fbn",
                                "destinationNode", "KSA/Riyadh",
                                "serviceLineKey", "known-yite-source-line"
                        ),
                        lineage(92001)
                ),
                new LogisticsQuotePublishedItem(
                        "logistics_base_price",
                        "yite|unknown-yite-source-line|A|cbm|unit_price",
                        mapOf(
                                "forwarderCode", "yite",
                                "serviceLineKey", "unknown-yite-source-line",
                                "cargoCategoryKey", "A",
                                "unitPrice", "99",
                                "currency", "SAR",
                                "billingUnit", "cbm",
                                "pricingModel", "unit_price",
                                "priceStatus", "NORMAL"
                        ),
                        lineage(92002)
                )
        ));

        assertEquals(1, landingResult.getInsertedCount());
        assertEquals(1, landingResult.getSkippedCount());
        assertEquals(0, repository.findPriceRulesByServiceLineKey("unknown-yite-source-line").size());
    }

    private static LogisticsQuotePublishedItem serviceLine(String naturalKey, String forwarderCode) {
        return new LogisticsQuotePublishedItem(
                "logistics_service_line",
                naturalKey,
                mapOf(
                        "forwarderCode", forwarderCode,
                        "forwarderName", forwarderCode,
                        "country", "SA",
                        "fulfillmentMode", "FBN",
                        "transportMode", "air",
                        "serviceScope", "headhaul",
                        "destinationNode", "Riyadh"
                ),
                lineage(naturalKey.hashCode())
        );
    }

    private static LogisticsQuotePublishedItem category(String naturalKey, String forwarderCode, String serviceLineKey) {
        return new LogisticsQuotePublishedItem(
                "logistics_cargo_category",
                naturalKey,
                mapOf(
                        "forwarderCode", forwarderCode,
                        "serviceLineKey", serviceLineKey,
                        "categoryCode", "A",
                        "categoryName", "普货"
                ),
                lineage(naturalKey.hashCode())
        );
    }

    private static LogisticsQuotePublishedItem price(
            String naturalKey,
            String forwarderCode,
            String serviceLineKey,
            String cargoCategoryKey,
            String unitPrice
    ) {
        return new LogisticsQuotePublishedItem(
                "logistics_base_price",
                naturalKey,
                mapOf(
                        "forwarderCode", forwarderCode,
                        "serviceLineKey", serviceLineKey,
                        "cargoCategoryKey", cargoCategoryKey,
                        "unitPrice", unitPrice,
                        "currency", "SAR",
                        "billingUnit", "kg",
                        "pricingModel", "unit_price",
                        "priceStatus", unitPrice == null ? "ASK_QUOTE" : "NORMAL"
                ),
                lineage(naturalKey.hashCode())
        );
    }

    private static LogisticsQuoteFactSourceLineage lineage(int id) {
        return new LogisticsQuoteFactSourceLineage(
                "file_management",
                20112L,
                40104L,
                70024L,
                Math.abs((long) id),
                "quote.pdf",
                "page 1"
        );
    }

    private static final class InMemoryFactRepository extends LogisticsQuoteBasePriceFactLandingTest.InMemoryFactRepository {

        @Override
        public List<LogisticsPriceRuleFact> findComparablePriceRules(LogisticsQuoteComparisonQuery query) {
            List<LogisticsPriceRuleFact> matches = new ArrayList<>();
            for (LogisticsPriceRuleFact price : prices) {
                if (!price.isComparable() || !query.getBillingUnit().equals(price.getBillingUnit())) {
                    continue;
                }
                Optional<LogisticsServiceLineFact> serviceLine = serviceLines.stream()
                        .filter(line -> line.getNaturalKey().equals(price.getServiceLineKey()))
                        .findFirst();
                Optional<LogisticsCargoCategoryFact> category = categories.stream()
                        .filter(value -> value.getNaturalKey().equals(price.getCargoCategoryKey()))
                        .findFirst();
                if (serviceLine.isPresent()
                        && category.isPresent()
                        && query.matches(serviceLine.get(), category.get())) {
                    matches.add(price);
                }
            }
            return matches;
        }
    }
}
