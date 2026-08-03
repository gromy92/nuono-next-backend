package com.nuono.next.system.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ReleaseSchemaMigrationMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class ReleaseSchemaReadinessGateTest {
    private final ReleaseSchemaCatalogLoader loader =
            new ReleaseSchemaCatalogLoader();
    private final ReleaseSchemaMigrationMapper mapper =
            mock(ReleaseSchemaMigrationMapper.class);

    @Test
    void acceptsTheContinuousCatalogBoundToCurrentAttemptRows() {
        when(mapper.selectMigrationHistory()).thenReturn(validRows());

        assertDoesNotThrow(gate()::afterPropertiesSet);
    }

    @Test
    void rejectsAMissingMigrationBeforeTheApplicationCanStart() {
        List<ReleaseSchemaMigrationRow> rows = validRows();
        rows.remove(1);
        when(mapper.selectMigrationHistory()).thenReturn(rows);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                gate()::afterPropertiesSet
        );

        assertTrue(error.getMessage().contains("is missing"));
    }

    @Test
    void rejectsChecksumDriftOrAnInconsistentCurrentAttempt() {
        List<ReleaseSchemaMigrationRow> rows = validRows();
        rows.get(1).setAttemptChecksum("0".repeat(64));
        when(mapper.selectMigrationHistory()).thenReturn(rows);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                gate()::afterPropertiesSet
        );

        assertTrue(error.getMessage().contains("history/attempt"));
    }

    @Test
    void rejectsHistoryRowsMissingFromThisJarCatalog() {
        List<ReleaseSchemaMigrationRow> rows = validRows();
        ReleaseSchemaMigrationRow unknown = new ReleaseSchemaMigrationRow();
        unknown.setMigrationKey("231_future_migration.sql");
        rows.add(unknown);
        when(mapper.selectMigrationHistory()).thenReturn(rows);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                gate()::afterPropertiesSet
        );

        assertTrue(error.getMessage().contains("not present in this Jar catalog"));
        assertTrue(error.getMessage().contains("231_future_migration.sql"));
    }

    @Test
    void rejectsAttemptRowsWithoutAHistoryOwner() {
        when(mapper.selectMigrationHistory()).thenReturn(validRows());
        when(mapper.countOrphanAttempts()).thenReturn(1L);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                gate()::afterPropertiesSet
        );

        assertTrue(error.getMessage().contains("orphan attempt"));
    }

    @Test
    void reportsMissingHistoryTablesAsAMigrationReadinessFailure() {
        when(mapper.selectMigrationHistory()).thenThrow(
                new IllegalStateException("table does not exist")
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                gate()::afterPropertiesSet
        );

        assertTrue(error.getMessage().contains("history cannot be read"));
        assertTrue(error.getCause().getMessage().contains("does not exist"));
    }

    @Test
    void localDbProfileRegistersTheGateBeforeAnyTrafficCanStart() {
        when(mapper.selectMigrationHistory()).thenReturn(validRows());

        contextRunner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ReleaseSchemaReadinessGate.class);
        });
    }

    @Test
    void isolatedTestsCanExplicitlyDisableTheGate() {
        contextRunner()
                .withPropertyValues("nuono.schema-release-gate.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ReleaseSchemaReadinessGate.class));
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=local-db")
                .withBean(
                        ReleaseSchemaMigrationMapper.class,
                        () -> mapper
                )
                .withUserConfiguration(GateConfiguration.class);
    }

    private ReleaseSchemaReadinessGate gate() {
        return new ReleaseSchemaReadinessGate(mapper, loader);
    }

    private List<ReleaseSchemaMigrationRow> validRows() {
        List<ReleaseSchemaMigrationRow> rows = new ArrayList<>();
        List<ReleaseSchemaMigrationDescriptor> catalog = loader.load();
        for (int index = 0; index < catalog.size(); index++) {
            ReleaseSchemaMigrationDescriptor migration = catalog.get(index);
            String state = index == 0 ? "BASELINED" : "APPLIED";
            ReleaseSchemaMigrationRow row = new ReleaseSchemaMigrationRow();
            row.setMigrationKey(migration.getKey());
            row.setChecksum(migration.getChecksum());
            row.setPostcheckChecksum(migration.getPostcheckChecksum());
            row.setState(state);
            row.setAttemptNo(1);
            row.setAttemptChecksum(migration.getChecksum());
            row.setAttemptPostcheckChecksum(migration.getPostcheckChecksum());
            row.setAttemptState(state);
            row.setJoinedAttemptNo(1);
            rows.add(row);
        }
        return rows;
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            ReleaseSchemaCatalogLoader.class,
            ReleaseSchemaReadinessGate.class
    })
    static class GateConfiguration {
    }
}
