package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.DataPullReleaseDatabaseMapper;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.core.env.Environment;

/** Binds the attested candidate configuration to the exact schema and cutover cohort. */
public final class DataPullManagedReleaseProvenanceEvidence
        implements DataPullRuntimeReleaseEvidence {
    static final String EXPECTED_COMMIT =
            "NUONO_DP_RUNTIME_RELEASE_EXPECTED_COMMIT";
    static final String SCHEMA_BINDING =
            "NUONO_DP_RUNTIME_RELEASE_SCHEMA_BINDING_SHA256";
    static final String CUTOVER_BINDING =
            "NUONO_DP_RUNTIME_RELEASE_CUTOVER_BINDING_SHA256";
    private static final String DP10_COMMIT =
            "NUONO_DP10_OPEN_API_EXECUTION_EXPECTED_COMMIT";
    private static final String APP_DIR = "NUONO_NEXT_APP_DIR";
    private static final String JAR = "NUONO_NEXT_JAR";
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");

    private final DataPullReleaseDatabaseMapper mapper;
    private final Environment environment;
    private final Path processDirectory;

    public DataPullManagedReleaseProvenanceEvidence(
            DataPullReleaseDatabaseMapper mapper,
            Environment environment
    ) {
        this(mapper, environment, Path.of(System.getProperty("user.dir")));
    }

    DataPullManagedReleaseProvenanceEvidence(
            DataPullReleaseDatabaseMapper mapper,
            Environment environment,
            Path processDirectory
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.processDirectory = Objects.requireNonNull(
                processDirectory, "processDirectory"
        ).toAbsolutePath().normalize();
    }

    @Override
    public DataPullRuntimeReleaseRequirement requirement() {
        return DataPullRuntimeReleaseRequirement.MANAGED_RELEASE_PROVENANCE;
    }

    @Override
    public boolean verified() {
        try {
            String commit = value(EXPECTED_COMMIT);
            String schema = value(SCHEMA_BINDING);
            String cutover = value(CUTOVER_BINDING);
            if (!matches(commit, COMMIT)
                    || !commit.equals(value(DP10_COMMIT))
                    || !matches(schema, SHA256)
                    || !matches(cutover, SHA256)
                    || !runtimeTopologyIsExact()) return false;
            DataPullReleaseDatabaseBinding actual = Objects.requireNonNull(
                    mapper.selectBinding(), "release database binding"
            );
            return schema.equals(actual.getSchemaBindingSha256())
                    && cutover.equals(actual.getCutoverBindingSha256())
                    && Long.valueOf(OperationCode.values().length).equals(
                            actual.getCutoverOperationCount()
                    );
        } catch (RuntimeException invalidEvidence) {
            return false;
        }
    }

    private boolean runtimeTopologyIsExact() {
        Path app = absolute(value(APP_DIR));
        Path jar = absolute(value(JAR));
        return processDirectory.equals(app)
                && jar.getParent().equals(app)
                && regularNoLink(app.resolve(".env"))
                && regularNoLink(jar);
    }

    private Path absolute(String value) {
        Path path = Path.of(value);
        if (!path.isAbsolute()) throw new IllegalArgumentException("runtime path is relative");
        return path.normalize();
    }

    private boolean regularNoLink(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(path);
    }

    private String value(String name) {
        String value = environment.getProperty(name);
        return value == null ? "" : value.trim();
    }

    private boolean matches(String value, Pattern pattern) {
        return value != null && pattern.matcher(value).matches();
    }
}
