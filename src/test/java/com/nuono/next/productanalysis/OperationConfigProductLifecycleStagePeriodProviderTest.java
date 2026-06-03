package com.nuono.next.productanalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.operationsconfig.OperationConfigDefaultVersionCatalog;
import com.nuono.next.operationsconfig.OperationConfigTypedVersion;
import com.nuono.next.operationsconfig.OperationConfigTypedVersionRepository;
import com.nuono.next.operationsconfig.OperationConfigVersionDetailView;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OperationConfigProductLifecycleStagePeriodProviderTest {

    @Test
    void resolvesForecastStageDurationsFromPublishedLifecycleConfig() throws JsonProcessingException {
        OperationConfigDefaultVersionCatalog catalog = new OperationConfigDefaultVersionCatalog();
        OperationConfigVersionDetailView detail = catalog.getDetail(
                OperationConfigDefaultVersionCatalog.DEFAULT_LIFECYCLE_VERSION_NO
        );
        OperationConfigTypedVersion version = new OperationConfigTypedVersion(
                88009L,
                "LIFECYCLE_CONFIG_88009",
                "生命周期配置",
                detail.getConfigType(),
                "CURRENT",
                detail.getVersionNo(),
                "复制版本",
                detail.getSummary(),
                detail.getItemCount(),
                "全局当前",
                new ObjectMapper().writeValueAsString(detail.getItems()),
                0L,
                0L,
                LocalDateTime.of(2026, 5, 28, 0, 0),
                LocalDateTime.of(2026, 5, 28, 0, 0)
        );
        OperationConfigProductLifecycleStagePeriodProvider provider =
                new OperationConfigProductLifecycleStagePeriodProvider(new SingleVersionRepository(version));

        ProductLifecycleStagePeriodConfig config = provider.resolveStagePeriods(new ProductLifecycleAnalysisQuery(
                10002L,
                "STR245027-NAE",
                "AE"
        ));

        assertEquals(60, config.getDurationDays("new"));
        assertEquals(45, config.getDurationDays("growth"));
        assertEquals(180, config.getDurationDays("stable"));
        assertEquals(30, config.getDurationDays("decline"));
    }

    private static class SingleVersionRepository implements OperationConfigTypedVersionRepository {
        private final OperationConfigTypedVersion version;

        private SingleVersionRepository(OperationConfigTypedVersion version) {
            this.version = version;
        }

        @Override
        public Long nextVersionId() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void insert(OperationConfigTypedVersion version) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<OperationConfigTypedVersion> listVersions() {
            return List.of(version);
        }

        @Override
        public Optional<OperationConfigTypedVersion> findByVersionNo(String versionNo) {
            return Optional.empty();
        }

        @Override
        public void update(OperationConfigTypedVersion version) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteByVersionNo(String versionNo) {
            throw new UnsupportedOperationException();
        }
    }
}
