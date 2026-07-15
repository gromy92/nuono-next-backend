package com.nuono.next.noonmaintenance;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DailyFileStoreSiteMaintenanceGate implements StoreSiteMaintenanceGate {
    private static final Logger LOGGER = LoggerFactory.getLogger(DailyFileStoreSiteMaintenanceGate.class);
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final String ALL_SITES = "*";

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Path configPath;
    private final Object reloadMonitor = new Object();
    private volatile Snapshot snapshot = Snapshot.empty();

    @Autowired
    public DailyFileStoreSiteMaintenanceGate(
            ObjectMapper objectMapper,
            @Value("${nuono.noon.maintenance-scope.file:}") String configFile
    ) {
        this(objectMapper, Clock.system(SHANGHAI), configFile);
    }

    public DailyFileStoreSiteMaintenanceGate(ObjectMapper objectMapper, Clock clock, String configFile) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = clock == null ? Clock.system(SHANGHAI) : clock.withZone(SHANGHAI);
        this.configPath = StringUtils.hasText(configFile) ? Path.of(configFile.trim()).toAbsolutePath().normalize() : null;
    }

    @Override
    public boolean isUnderMaintenance(Long ownerUserId, String storeCode, String siteCode) {
        if (configPath == null || ownerUserId == null || !StringUtils.hasText(storeCode) || !StringUtils.hasText(siteCode)) {
            return false;
        }
        LocalDate businessDate = LocalDate.now(clock);
        ScopeKey scopeKey = new ScopeKey(ownerUserId, normalize(storeCode), normalize(siteCode));
        for (MaintenanceEntry entry : snapshotFor(businessDate).entries) {
            if (entry.matches(scopeKey, businessDate)) {
                return true;
            }
        }
        return false;
    }

    private Snapshot snapshotFor(LocalDate businessDate) {
        Snapshot current = snapshot;
        if (businessDate.equals(current.loadedForDate)) {
            return current;
        }
        synchronized (reloadMonitor) {
            current = snapshot;
            if (businessDate.equals(current.loadedForDate)) {
                return current;
            }
            snapshot = reload(businessDate, current);
            return snapshot;
        }
    }

    private Snapshot reload(LocalDate businessDate, Snapshot current) {
        try {
            if (!Files.isRegularFile(configPath)) {
                throw new IOException("maintenance config is not a readable regular file");
            }
            MaintenanceConfig config = objectMapper.readValue(configPath.toFile(), MaintenanceConfig.class);
            List<MaintenanceEntry> entries = validate(config);
            LOGGER.info(
                    "Loaded Noon maintenance scope config for businessDate={} path={} entryCount={}",
                    businessDate,
                    configPath,
                    entries.size()
            );
            return new Snapshot(businessDate, entries);
        } catch (Exception exception) {
            LOGGER.error(
                    "Failed to load Noon maintenance scope config for businessDate={} path={}; retaining last-known-good entryCount={}: {}",
                    businessDate,
                    configPath,
                    current.entries.size(),
                    exception.getMessage()
            );
            return new Snapshot(businessDate, current.entries);
        }
    }

    private List<MaintenanceEntry> validate(MaintenanceConfig config) {
        if (config == null || config.getVersion() == null || config.getVersion() != 1) {
            throw new IllegalArgumentException("version must be 1");
        }
        List<MaintenanceEntry> entries = new ArrayList<>();
        if (config.getMaintenance() == null) {
            return entries;
        }
        for (int index = 0; index < config.getMaintenance().size(); index++) {
            MaintenanceConfigEntry source = config.getMaintenance().get(index);
            if (source == null || Boolean.FALSE.equals(source.getEnabled())) {
                continue;
            }
            if (source.getOwnerUserId() == null || source.getOwnerUserId() <= 0) {
                throw new IllegalArgumentException("maintenance[" + index + "].ownerUserId must be positive");
            }
            if (!StringUtils.hasText(source.getStoreCode())) {
                throw new IllegalArgumentException("maintenance[" + index + "].storeCode is required");
            }
            if (!StringUtils.hasText(source.getSiteCode())) {
                throw new IllegalArgumentException("maintenance[" + index + "].siteCode is required; use * for all sites");
            }
            LocalDate effectiveFrom = parseDate(source.getEffectiveFrom(), "maintenance[" + index + "].effectiveFrom");
            LocalDate effectiveTo = parseDate(source.getEffectiveTo(), "maintenance[" + index + "].effectiveTo");
            if (effectiveFrom != null && effectiveTo != null && effectiveFrom.isAfter(effectiveTo)) {
                throw new IllegalArgumentException("maintenance[" + index + "] effectiveFrom must not be after effectiveTo");
            }
            entries.add(new MaintenanceEntry(
                    new ScopeKey(source.getOwnerUserId(), normalize(source.getStoreCode()), normalize(source.getSiteCode())),
                    effectiveFrom,
                    effectiveTo
            ));
        }
        return Collections.unmodifiableList(entries);
    }

    private LocalDate parseDate(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(fieldName + " must use yyyy-MM-dd", exception);
        }
    }

    private static String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static final class Snapshot {
        private final LocalDate loadedForDate;
        private final List<MaintenanceEntry> entries;

        private Snapshot(LocalDate loadedForDate, List<MaintenanceEntry> entries) {
            this.loadedForDate = loadedForDate;
            this.entries = entries == null ? Collections.emptyList() : entries;
        }

        private static Snapshot empty() {
            return new Snapshot(null, Collections.emptyList());
        }
    }

    private static final class ScopeKey {
        private final Long ownerUserId;
        private final String storeCode;
        private final String siteCode;

        private ScopeKey(Long ownerUserId, String storeCode, String siteCode) {
            this.ownerUserId = ownerUserId;
            this.storeCode = storeCode;
            this.siteCode = siteCode;
        }
    }

    private static final class MaintenanceEntry {
        private final ScopeKey scopeKey;
        private final LocalDate effectiveFrom;
        private final LocalDate effectiveTo;

        private MaintenanceEntry(ScopeKey scopeKey, LocalDate effectiveFrom, LocalDate effectiveTo) {
            this.scopeKey = scopeKey;
            this.effectiveFrom = effectiveFrom;
            this.effectiveTo = effectiveTo;
        }

        private boolean matches(ScopeKey candidate, LocalDate businessDate) {
            if (!scopeKey.ownerUserId.equals(candidate.ownerUserId)
                    || !scopeKey.storeCode.equals(candidate.storeCode)
                    || !(ALL_SITES.equals(scopeKey.siteCode) || scopeKey.siteCode.equals(candidate.siteCode))) {
                return false;
            }
            return (effectiveFrom == null || !businessDate.isBefore(effectiveFrom))
                    && (effectiveTo == null || !businessDate.isAfter(effectiveTo));
        }
    }

    public static class MaintenanceConfig {
        private Integer version;
        private List<MaintenanceConfigEntry> maintenance;

        public Integer getVersion() {
            return version;
        }

        public void setVersion(Integer version) {
            this.version = version;
        }

        public List<MaintenanceConfigEntry> getMaintenance() {
            return maintenance;
        }

        public void setMaintenance(List<MaintenanceConfigEntry> maintenance) {
            this.maintenance = maintenance;
        }
    }

    public static class MaintenanceConfigEntry {
        private Long ownerUserId;
        private String storeCode;
        private String siteCode;
        private Boolean enabled;
        private String effectiveFrom;
        private String effectiveTo;
        private String reason;

        public Long getOwnerUserId() {
            return ownerUserId;
        }

        public void setOwnerUserId(Long ownerUserId) {
            this.ownerUserId = ownerUserId;
        }

        public String getStoreCode() {
            return storeCode;
        }

        public void setStoreCode(String storeCode) {
            this.storeCode = storeCode;
        }

        public String getSiteCode() {
            return siteCode;
        }

        public void setSiteCode(String siteCode) {
            this.siteCode = siteCode;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getEffectiveFrom() {
            return effectiveFrom;
        }

        public void setEffectiveFrom(String effectiveFrom) {
            this.effectiveFrom = effectiveFrom;
        }

        public String getEffectiveTo() {
            return effectiveTo;
        }

        public void setEffectiveTo(String effectiveTo) {
            this.effectiveTo = effectiveTo;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
