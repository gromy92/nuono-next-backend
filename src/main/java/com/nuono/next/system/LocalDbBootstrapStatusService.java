package com.nuono.next.system;

import com.nuono.next.infrastructure.mapper.CoreTableStatusMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local-db")
public class LocalDbBootstrapStatusService {

    private final CoreTableStatusMapper coreTableStatusMapper;
    private final BootstrapProperties bootstrapProperties;

    public LocalDbBootstrapStatusService(
            CoreTableStatusMapper coreTableStatusMapper,
            BootstrapProperties bootstrapProperties
    ) {
        this.coreTableStatusMapper = coreTableStatusMapper;
        this.bootstrapProperties = bootstrapProperties;
    }

    public Map<String, Object> describe() {
        CoreTableInspection inspection = inspect();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mode", "local-db");
        payload.put("ready", inspection.isReady());
        payload.put("existingCoreTables", inspection.getExistingTables());
        payload.put("missingCoreTables", inspection.getMissingTables());
        payload.put("initScripts", List.of(
                "classpath:db/init/000_local_dev_bootstrap.sql",
                "classpath:db/init/001_clone_legacy_core_tables.sql",
                "classpath:db/init/002_import_whitelist_sample.sql"
        ));
        return payload;
    }

    public CoreTableInspection inspect() {
        List<String> expectedTables = bootstrapProperties.getExpectedCoreTables();
        List<String> existingTables = coreTableStatusMapper.findExistingTableNames(
                bootstrapProperties.getSchema(),
                expectedTables
        );

        List<String> missingTables = expectedTables.stream()
                .filter(table -> !existingTables.contains(table))
                .collect(Collectors.toList());

        return new CoreTableInspection(
                bootstrapProperties.getSchema(),
                expectedTables,
                existingTables,
                missingTables
        );
    }
}
