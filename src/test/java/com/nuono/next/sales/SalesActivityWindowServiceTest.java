package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SalesActivityWindowServiceTest {

    @Test
    void createsVersionsTogglesAndReturnsActiveSiteScopedSnapshot() {
        InMemorySalesActivityWindowRepository repository = new InMemorySalesActivityWindowRepository();
        SalesActivityWindowService service = new SalesActivityWindowService(repository);
        SalesActivityWindowCommand ramadan = command(null, "Ramadan", "holiday", new BigDecimal("1.20"), true);

        SalesActivityWindowRecord created = service.save(ramadan);
        SalesActivityWindowRecord edited = service.save(command(created.getId(), "Ramadan Peak", "holiday", new BigDecimal("1.35"), true));
        service.setEnabled(edited.getId(), false, 10003L);
        SalesActivityWindowRecord disabled = service.find(edited.getId());

        assertEquals(1, created.getVersionNo());
        assertEquals(2, edited.getVersionNo());
        assertEquals(false, disabled.isEnabled());
        assertEquals(2, service.history(scope()).size());
        assertEquals(0, service.activeSnapshot(scope()).getWindows().size());

        service.setEnabled(edited.getId(), true, 10003L);
        SalesActivityWindowSnapshot snapshot = service.activeSnapshot(scope());

        assertEquals(1, snapshot.getWindows().size());
        assertEquals("Ramadan Peak", snapshot.getWindows().get(0).getName());
        assertEquals(new BigDecimal("1.35"), snapshot.getWindows().get(0).getFactor());
    }

    private SalesActivityWindowCommand command(
            Long id,
            String name,
            String activityType,
            BigDecimal factor,
            boolean enabled
    ) {
        return new SalesActivityWindowCommand(
                id,
                10002L,
                "STR245027-SAU",
                "SA",
                name,
                activityType,
                "stationery",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 4, 10),
                factor,
                enabled,
                10003L
        );
    }

    private SalesActivityWindowScope scope() {
        return new SalesActivityWindowScope(
                10002L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 3, 20),
                LocalDate.of(2026, 3, 31)
        );
    }

    private static class InMemorySalesActivityWindowRepository implements SalesActivityWindowRepository {
        private final List<SalesActivityWindowRecord> records = new ArrayList<>();
        private long nextId = 7001L;

        @Override
        public SalesActivityWindowRecord save(SalesActivityWindowRecord record) {
            SalesActivityWindowRecord saved = record.withId(nextId++);
            records.add(saved);
            return saved;
        }

        @Override
        public SalesActivityWindowRecord find(Long id) {
            return records.stream().filter(record -> record.getId().equals(id)).findFirst().orElseThrow();
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
            List<SalesActivityWindowRecord> active = new ArrayList<>();
            for (SalesActivityWindowRecord record : records) {
                if (record.isEnabled()
                        && record.getOwnerUserId().equals(scope.getOwnerUserId())
                        && record.getStoreCode().equals(scope.getStoreCode())
                        && record.getSiteCode().equals(scope.getSiteCode())
                        && !record.getDateTo().isBefore(scope.getDateFrom())
                        && !record.getDateFrom().isAfter(scope.getDateTo())) {
                    active.add(record);
                }
            }
            return active;
        }
    }
}
