package com.nuono.next.datapull.orchestration;

import com.nuono.next.infrastructure.mapper.DataPullReleaseDatabaseMapper;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.core.env.Environment;

/** Database livecheck for the exact migration cohort bound by the managed release. */
public final class DataPullRuntimeSchemaEvidence implements DataPullRuntimeReleaseEvidence {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private final DataPullReleaseDatabaseMapper mapper;
    private final Environment environment;

    public DataPullRuntimeSchemaEvidence(
            DataPullReleaseDatabaseMapper mapper,
            Environment environment
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    @Override
    public DataPullRuntimeReleaseRequirement requirement() {
        return DataPullRuntimeReleaseRequirement.RUNTIME_SCHEMA;
    }

    @Override
    public boolean verified() {
        try {
            String expected = value(DataPullManagedReleaseProvenanceEvidence.SCHEMA_BINDING);
            if (!SHA256.matcher(expected).matches()) return false;
            DataPullReleaseDatabaseBinding actual = mapper.selectBinding();
            return actual != null && expected.equals(actual.getSchemaBindingSha256());
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private String value(String name) {
        String value = environment.getProperty(name);
        return value == null ? "" : value.trim();
    }
}
