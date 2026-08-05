package com.nuono.next.datapull.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** Real-socket proof for a blackholed response during transaction begin, before MyBatis. */
class DataPullTransactionBeginNetworkMySqlTest {

    @Test
    void existingPoolConnectionBoundsBlackholedSetAutoCommitAndThenRecovers() throws Exception {
        String sourceUrl = System.getenv("NUONO_DP_DEADLINE_MYSQL_URL");
        Assumptions.assumeTrue(sourceUrl != null && !sourceUrl.isBlank());
        JdbcTarget target = JdbcTarget.parse(sourceUrl);
        try (TcpResponseBlackhole proxy = new TcpResponseBlackhole(target.host, target.port)) {
            HikariConfig config = config(target.through(proxy.port()));
            try (HikariDataSource pool = new HikariDataSource(config)) {
                Connection existing = pool.getConnection();
                try {
                    assertEquals(1, queryOne(existing));
                    SingleConnectionDataSource borrowed =
                            new SingleConnectionDataSource(existing, true);
                    proxy.blackholeResponses();
                    TransactionTemplate transaction = new TransactionTemplate(
                            new DataSourceTransactionManager(
                                    new DataPullDeadlineAwareDataSource(borrowed)
                            )
                    );
                    long started = System.nanoTime();

                    try (DataPullAdvanceDeadline ignored =
                                 DataPullAdvanceDeadline.open(Duration.ofMillis(500))) {
                        assertThrows(RuntimeException.class, () ->
                                transaction.execute(status -> null)
                        );
                    }

                    assertTrue(proxy.droppedResponses() > 0);
                    assertTrue(Duration.ofNanos(System.nanoTime() - started)
                            .compareTo(Duration.ofSeconds(3)) < 0);
                } finally {
                    existing.close();
                }
                proxy.forwardResponses();
                assertEquals(1, awaitQueryOne(pool, Duration.ofSeconds(5)));
            }
        }
    }

    private HikariConfig config(String url) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(System.getenv("NUONO_DP_DEADLINE_MYSQL_USERNAME"));
        config.setPassword(System.getenv("NUONO_DP_DEADLINE_MYSQL_PASSWORD"));
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(1_000L);
        config.addDataSourceProperty("connectTimeout", "1000");
        config.addDataSourceProperty("socketTimeout", "300000");
        config.addDataSourceProperty("queryTimeoutKillsConnection", "true");
        return config;
    }

    private int queryOne(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             java.sql.ResultSet row = statement.executeQuery("SELECT 1")) {
            if (!row.next()) throw new SQLException("prewarmed query returned no row");
            return row.getInt(1);
        }
    }

    private int awaitQueryOne(HikariDataSource pool, Duration timeout)
            throws SQLException {
        long deadline = System.nanoTime() + timeout.toNanos();
        SQLException lastFailure = null;
        while (System.nanoTime() < deadline) {
            try {
                try (Connection connection = pool.getConnection()) {
                    return queryOne(connection);
                }
            } catch (SQLException unavailable) {
                lastFailure = unavailable;
            }
        }
        throw lastFailure == null ? new SQLException("pool did not recover") : lastFailure;
    }

    private static final class JdbcTarget {
        private final String originalUrl;
        private final String host;
        private final int port;
        private final int authorityStart;
        private final int authorityEnd;

        private JdbcTarget(
                String originalUrl,
                String host,
                int port,
                int authorityStart,
                int authorityEnd
        ) {
            this.originalUrl = originalUrl;
            this.host = host;
            this.port = port;
            this.authorityStart = authorityStart;
            this.authorityEnd = authorityEnd;
        }

        static JdbcTarget parse(String url) {
            String prefix = "jdbc:mysql://";
            if (!url.startsWith(prefix)) throw new IllegalArgumentException("MySQL URL required");
            int start = prefix.length();
            int end = url.indexOf('/', start);
            String authority = url.substring(start, end);
            int colon = authority.lastIndexOf(':');
            String host = colon < 0 ? authority : authority.substring(0, colon);
            int port = colon < 0 ? 3306 : Integer.parseInt(authority.substring(colon + 1));
            return new JdbcTarget(url, host, port, start, end);
        }

        String through(int proxyPort) {
            return originalUrl.substring(0, authorityStart)
                    + "127.0.0.1:" + proxyPort
                    + originalUrl.substring(authorityEnd);
        }
    }

    private static final class TcpResponseBlackhole implements AutoCloseable {
        private final String upstreamHost;
        private final int upstreamPort;
        private final ServerSocket server;
        private final ExecutorService io = Executors.newCachedThreadPool();
        private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean forwardResponses = new AtomicBoolean(true);
        private final AtomicInteger droppedResponses = new AtomicInteger();

        private TcpResponseBlackhole(String upstreamHost, int upstreamPort) throws Exception {
            this.upstreamHost = upstreamHost;
            this.upstreamPort = upstreamPort;
            this.server = new ServerSocket(0);
            io.execute(this::acceptLoop);
        }

        int port() {
            return server.getLocalPort();
        }

        void blackholeResponses() {
            forwardResponses.set(false);
        }

        void forwardResponses() {
            forwardResponses.set(true);
        }

        int droppedResponses() {
            return droppedResponses.get();
        }

        private void acceptLoop() {
            while (!server.isClosed()) {
                try {
                    Socket client = server.accept();
                    Socket upstream = new Socket(upstreamHost, upstreamPort);
                    sockets.add(client);
                    sockets.add(upstream);
                    io.execute(() -> pump(client, upstream, true));
                    io.execute(() -> pump(upstream, client, false));
                } catch (Exception stopped) {
                    if (!server.isClosed()) throw new IllegalStateException(stopped);
                }
            }
        }

        private void pump(Socket source, Socket destination, boolean request) {
            byte[] buffer = new byte[8_192];
            try {
                InputStream input = source.getInputStream();
                OutputStream output = destination.getOutputStream();
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (request || forwardResponses.get()) {
                        output.write(buffer, 0, count);
                        output.flush();
                    } else {
                        droppedResponses.incrementAndGet();
                    }
                }
            } catch (Exception disconnected) {
                // Socket abort and fixture close are expected termination paths.
            } finally {
                closeSocket(source);
                closeSocket(destination);
            }
        }

        @Override
        public void close() throws Exception {
            server.close();
            sockets.forEach(TcpResponseBlackhole::closeSocket);
            io.shutdownNow();
            assertTrue(io.awaitTermination(2, TimeUnit.SECONDS));
        }

        private static void closeSocket(Socket socket) {
            try {
                socket.close();
            } catch (Exception ignored) {
                // Best-effort fixture cleanup.
            }
        }
    }
}
