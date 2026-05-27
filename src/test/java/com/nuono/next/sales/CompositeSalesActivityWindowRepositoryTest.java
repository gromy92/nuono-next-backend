package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompositeSalesActivityWindowRepositoryTest {

    @Test
    void activeReadCombinesLegacyRowsAndAdvancedCompatibilityRowsForExistingConsumers() {
        InMemorySalesActivityWindowRepository legacyRepository = new InMemorySalesActivityWindowRepository();
        legacyRepository.records.add(window(40001L, "Legacy Ramadan", new BigDecimal("1.20"), 2));
        SalesActivityWindowRecord advancedWindow = window(82001L, "Advanced White Friday", new BigDecimal("1.35"), 80001);
        CompositeSalesActivityWindowRepository repository = new CompositeSalesActivityWindowRepository(
                legacyRepository,
                List.of(scope -> List.of(advancedWindow))
        );

        List<SalesActivityWindowRecord> active = repository.listActive(scope());

        assertEquals(2, active.size());
        assertEquals("Legacy Ramadan", active.get(0).getName());
        assertEquals("Advanced White Friday", active.get(1).getName());
        assertEquals(new BigDecimal("1.35"), active.get(1).getFactor());
    }

    @Test
    void historyStaysLegacyOnlyBecauseAdvancedAuditIsServedByOperationsConfigHistory() {
        InMemorySalesActivityWindowRepository legacyRepository = new InMemorySalesActivityWindowRepository();
        legacyRepository.records.add(window(40001L, "Legacy Ramadan", new BigDecimal("1.20"), 2));
        CompositeSalesActivityWindowRepository repository = new CompositeSalesActivityWindowRepository(
                legacyRepository,
                List.of(scope -> List.of(window(82001L, "Advanced White Friday", new BigDecimal("1.35"), 80001)))
        );

        List<SalesActivityWindowRecord> history = repository.listHistory(scope());

        assertEquals(1, history.size());
        assertEquals("Legacy Ramadan", history.get(0).getName());
    }

    private static SalesActivityWindowScope scope() {
        return new SalesActivityWindowScope(
                10002L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31)
        );
    }

    private static SalesActivityWindowRecord window(Long id, String name, BigDecimal factor, int versionNo) {
        return new SalesActivityWindowRecord(
                id,
                10002L,
                "STR245027-SAU",
                "SA",
                name,
                "holiday",
                "all_products",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                factor,
                true,
                versionNo,
                10003L,
                10003L
        );
    }

    private static class InMemorySalesActivityWindowRepository implements SalesActivityWindowRepository {
        private final List<SalesActivityWindowRecord> records = new ArrayList<>();

        @Override
        public SalesActivityWindowRecord save(SalesActivityWindowRecord record) {
            records.add(record);
            return record;
        }

        @Override
        public SalesActivityWindowRecord find(Long id) {
            return records.stream().filter(record -> id.equals(record.getId())).findFirst().orElseThrow();
        }

        @Override
        public void setEnabled(Long id, boolean enabled, Long updatedBy) {
            SalesActivityWindowRecord current = find(id);
            records.remove(current);
            records.add(current.withEnabled(enabled, updatedBy));
        }

        @Override
        public List<SalesActivityWindowRecord> listHistory(SalesActivityWindowScope scope) {
            return records;
        }

        @Override
        public List<SalesActivityWindowRecord> listActive(SalesActivityWindowScope scope) {
            return records;
        }
    }
}
