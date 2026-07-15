package com.nuono.next.noonmaintenance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DailyFileStoreSiteMaintenanceGateTest {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @TempDir
    Path tempDir;

    @Test
    void shouldMatchExactSiteAndWholeStoreWildcardWithinEffectiveDates() throws IOException {
        Path config = tempDir.resolve("maintenance.json");
        write(config, "{\n"
                + "  \"version\": 1,\n"
                + "  \"maintenance\": [\n"
                + "    {\"ownerUserId\":308,\"storeCode\":\"str-a\",\"siteCode\":\"ae\",\"effectiveFrom\":\"2026-07-15\",\"effectiveTo\":\"2026-07-20\"},\n"
                + "    {\"ownerUserId\":308,\"storeCode\":\"str-b\",\"siteCode\":\"*\"},\n"
                + "    {\"ownerUserId\":308,\"storeCode\":\"str-future\",\"siteCode\":\"SA\",\"effectiveFrom\":\"2026-07-16\"}\n"
                + "  ]\n"
                + "}");
        MutableClock clock = new MutableClock(Instant.parse("2026-07-15T01:00:00Z"), SHANGHAI);
        DailyFileStoreSiteMaintenanceGate gate = new DailyFileStoreSiteMaintenanceGate(
                new ObjectMapper(), clock, config.toString());

        assertTrue(gate.isUnderMaintenance(308L, "STR-A", "AE"));
        assertFalse(gate.isUnderMaintenance(308L, "STR-A", "SA"));
        assertTrue(gate.isUnderMaintenance(308L, "STR-B", "AE"));
        assertTrue(gate.isUnderMaintenance(308L, "STR-B", "SA"));
        assertFalse(gate.isUnderMaintenance(307L, "STR-B", "SA"));
        assertFalse(gate.isUnderMaintenance(308L, "STR-FUTURE", "SA"));
    }

    @Test
    void shouldReloadOnlyAfterShanghaiBusinessDateChanges() throws IOException {
        Path config = tempDir.resolve("maintenance.json");
        write(config, configWithOneScope());
        MutableClock clock = new MutableClock(Instant.parse("2026-07-15T01:00:00Z"), SHANGHAI);
        DailyFileStoreSiteMaintenanceGate gate = new DailyFileStoreSiteMaintenanceGate(
                new ObjectMapper(), clock, config.toString());

        assertTrue(gate.isUnderMaintenance(308L, "STR-A", "AE"));
        write(config, "{\"version\":1,\"maintenance\":[]}");
        assertTrue(gate.isUnderMaintenance(308L, "STR-A", "AE"));

        clock.setInstant(Instant.parse("2026-07-16T01:00:00Z"));
        assertFalse(gate.isUnderMaintenance(308L, "STR-A", "AE"));
    }

    @Test
    void shouldRetainLastKnownGoodWhenNextDailyFileIsInvalid() throws IOException {
        Path config = tempDir.resolve("maintenance.json");
        write(config, configWithOneScope());
        MutableClock clock = new MutableClock(Instant.parse("2026-07-15T01:00:00Z"), SHANGHAI);
        DailyFileStoreSiteMaintenanceGate gate = new DailyFileStoreSiteMaintenanceGate(
                new ObjectMapper(), clock, config.toString());

        assertTrue(gate.isUnderMaintenance(308L, "STR-A", "AE"));
        write(config, "{not-valid-json");
        clock.setInstant(Instant.parse("2026-07-16T01:00:00Z"));

        assertTrue(gate.isUnderMaintenance(308L, "STR-A", "AE"));
    }

    private String configWithOneScope() {
        return "{\"version\":1,\"maintenance\":[{\"ownerUserId\":308,"
                + "\"storeCode\":\"STR-A\",\"siteCode\":\"AE\"}]}";
    }

    private void write(Path path, String content) throws IOException {
        Files.writeString(path, content);
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this(new AtomicReference<>(instant), zone);
        }

        private MutableClock(AtomicReference<Instant> instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        private void setInstant(Instant instant) {
            this.instant.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this.zone.equals(zone) ? this : new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
