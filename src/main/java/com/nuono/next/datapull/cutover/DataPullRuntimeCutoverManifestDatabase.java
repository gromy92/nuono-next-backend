package com.nuono.next.datapull.cutover;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.competitoranalysis.dp08.MyBatisDp08ScopeCatalog;
import com.nuono.next.competitoranalysis.dp08.Dp08ListTargetPreparation;
import com.nuono.next.datapull.orchestration.DataPullJob;
import com.nuono.next.datapull.orchestration.DataPullJobRegistry;
import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.orchestration.DataPullScopePreparation;
import com.nuono.next.datapull.orchestration.NoonDataPullScopeSource;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.schedule.AdmittedDataPullScope;
import com.nuono.next.datapull.schedule.DataPullScopeAdmission;
import com.nuono.next.datapull.scope.DataPullScopeBindingCandidate;
import com.nuono.next.datapull.scope.DataPullScopeBindingEpoch;
import com.nuono.next.datapull.scope.DataPullScopeBindingStore;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10RuntimeMapper;
import com.nuono.next.infrastructure.mapper.Dp08ScopeMapper;
import com.nuono.next.infrastructure.mapper.NoonDataPullScopeMapper;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10ScopeSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

/** Opens one read-only consistent snapshot and invokes the production scope Implementations. */
final class DataPullRuntimeCutoverManifestDatabase {

    private static final DateTimeFormatter MYSQL_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    static final String READ_ONLY_SNAPSHOT_SQL =
            "START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY";

    DataPullRuntimeCutoverSourceCohort read(
            DataPullRuntimeCutoverManifestEnvironment environment,
            String cutoverKey
    ) throws Exception {
        DataSource dataSource = new UnpooledDataSource(
                "com.mysql.cj.jdbc.Driver",
                environment.require("NUONO_NEXT_DB_URL"),
                environment.require("NUONO_NEXT_DB_USERNAME"),
                environment.require("NUONO_NEXT_DB_PASSWORD")
        );
        SqlSessionFactory sessions = sessions(dataSource);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.setReadOnly(true);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ");
                statement.execute(READ_ONLY_SNAPSHOT_SQL);
            }
            try (SqlSession session = sessions.openSession(connection)) {
                LocalDateTime observedAtUtc = databaseTime(connection);
                DataPullRuntimeCutoverSourceCohort result = readCohort(
                        session, connection, cutoverKey, observedAtUtc
                );
                connection.rollback();
                return result;
            }
        }
    }

    private DataPullRuntimeCutoverSourceCohort readCohort(
            SqlSession session,
            Connection connection,
            String cutoverKey,
            LocalDateTime observedAtUtc
    ) throws Exception {
        List<DataPullScope> noon = new NoonDataPullScopeSource(
                session.getMapper(NoonDataPullScopeMapper.class)
        ).listScopes();
        List<DataPullScope> ali = new Ali1688Dp10ScopeSource(
                session.getMapper(Ali1688Dp10RuntimeMapper.class)
        ).listScopes();
        CapturingBindingStore captured = new CapturingBindingStore();
        MyBatisDp08ScopeCatalog dp08 = new MyBatisDp08ScopeCatalog(
                session.getMapper(Dp08ScopeMapper.class), captured, new ObjectMapper()
        );
        DataPullScopePreparation dp08a = dp08.prepareKeywordScopesForEnqueue();
        LocalDate factDate = observedAtUtc.atZone(java.time.ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDate();
        Dp08ListTargetPreparation dp08b = dp08.prepareListTargetScopesForEnqueue(factDate);
        complete(dp08a, cutoverKey, observedAtUtc);
        complete(dp08b, cutoverKey, observedAtUtc);

        EnumMap<OperationCode, List<DataPullScope>> scopes =
                new EnumMap<>(OperationCode.class);
        for (OperationCode operation : OperationCode.values()) {
            if (operation == OperationCode.DP08A) scopes.put(operation, dp08a.getScopes());
            else if (operation == OperationCode.DP08B) scopes.put(operation, dp08b.getScopes());
            else if (operation == OperationCode.DP10) scopes.put(operation, ali);
            else scopes.put(operation, noon);
        }
        List<DataPullJob> jobs = new ArrayList<>();
        scopes.forEach((operation, values) -> jobs.add(new ManifestJob(operation, values)));
        DataPullJobRegistry registry = new DataPullJobRegistry(jobs);
        registry.requireComplete();
        return new DataPullRuntimeCutoverSourceCohort(
                observedAtUtc,
                registry,
                captured.snapshot(),
                new DataPullLegacyScheduleBoundaryReader().read(connection, noon)
        );
    }

    private static void complete(
            DataPullScopePreparation preparation,
            String cutoverKey,
            LocalDateTime observedAtUtc
    ) {
        preparation.completeAfterAdmission(admissions(
                preparation.getScopes(), cutoverKey, observedAtUtc
        ));
    }

    private static void complete(
            Dp08ListTargetPreparation preparation,
            String cutoverKey,
            LocalDateTime observedAtUtc
    ) {
        List<AdmittedDataPullScope> admitted = admissions(
                preparation.getScopes(), cutoverKey, observedAtUtc
        );
        preparation.completeAfterAdmission(admitted);
    }

    private static List<AdmittedDataPullScope> admissions(
            List<DataPullScope> scopes,
            String cutoverKey,
            LocalDateTime observedAtUtc
    ) {
        List<AdmittedDataPullScope> admitted = new ArrayList<>();
        for (DataPullScope scope : scopes) {
            admitted.add(new AdmittedDataPullScope(
                    scope,
                    DataPullScopeAdmission.cutoverExisting(scope, cutoverKey, observedAtUtc)
            ));
        }
        return admitted;
    }

    private static LocalDateTime databaseTime(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             java.sql.ResultSet rows = statement.executeQuery(
                     "SELECT DATE_FORMAT(UTC_TIMESTAMP(3), '%Y-%m-%d %H:%i:%s.%f')"
             )) {
            if (!rows.next()) throw new IllegalStateException("DP_CUTOVER_DB_TIME_MISSING");
            String value = rows.getString(1);
            if (value == null || value.length() < 23) {
                throw new IllegalStateException("DP_CUTOVER_DB_TIME_INVALID");
            }
            return LocalDateTime.parse(value.substring(0, 23), MYSQL_MILLIS);
        }
    }

    private static SqlSessionFactory sessions(DataSource dataSource) {
        Environment environment = new Environment(
                "dp-cutover-read-only", new JdbcTransactionFactory(), dataSource
        );
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(NoonDataPullScopeMapper.class);
        configuration.addMapper(Ali1688Dp10RuntimeMapper.class);
        configuration.addMapper(Dp08ScopeMapper.class);
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static final class CapturingBindingStore implements DataPullScopeBindingStore {
        private final EnumMap<OperationCode, List<DataPullScopeBindingCandidate>> values =
                new EnumMap<>(OperationCode.class);

        @Override
        public List<DataPullScopeBindingEpoch> reconcileCurrent(
                OperationCode operation,
                List<DataPullScopeBindingCandidate> candidates
        ) {
            if (operation != OperationCode.DP08A && operation != OperationCode.DP08B) {
                throw new IllegalArgumentException("cutover manifest received non-DP08 binding");
            }
            if (values.putIfAbsent(operation, List.copyOf(candidates)) != null) {
                throw new IllegalStateException("cutover binding cohort was captured twice");
            }
            return List.of();
        }

        Map<OperationCode, List<DataPullScopeBindingCandidate>> snapshot() {
            return Map.copyOf(values);
        }
    }

    private static final class ManifestJob implements DataPullJob {
        private final OperationCode operation;
        private final List<DataPullScope> scopes;

        ManifestJob(OperationCode operation, List<DataPullScope> scopes) {
            this.operation = operation;
            this.scopes = List.copyOf(scopes);
        }
        @Override public OperationCode operationCode() { return operation; }
        @Override public String providerChannel() { return "CUTOVER_MANIFEST_READ_ONLY"; }
        @Override public String initialStep() { return "READ_ONLY"; }
        @Override public List<DataPullScope> listScopes() { return scopes; }
        @Override public AdvanceResult advance(
                com.nuono.next.datapull.orchestration.ExecutionContext ignored
        ) {
            throw new UnsupportedOperationException("cutover manifest job cannot execute");
        }
    }
}
