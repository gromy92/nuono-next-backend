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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Exercises one fixed pool endpoint that recovers after accepting no MySQL handshake. */
class DataPullConnectionHandshakeMySqlTest {

    @Test
    void handshakeBlackholeTimesOutAndTheSamePoolEndpointSubsequentlyRefills()
            throws Exception {
        String healthyUrl = System.getenv("NUONO_DP_DEADLINE_MYSQL_URL");
        Assumptions.assumeTrue(healthyUrl != null && !healthyUrl.isBlank());
        JdbcTarget target = JdbcTarget.parse(healthyUrl);
        try (RecoveringHandshakeProxy proxy = new RecoveringHandshakeProxy(
                target.host, target.port)) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(target.through(proxy.port()));
            config.setUsername(System.getenv("NUONO_DP_DEADLINE_MYSQL_USERNAME"));
            config.setPassword(System.getenv("NUONO_DP_DEADLINE_MYSQL_PASSWORD"));
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(500L);
            config.setInitializationFailTimeout(-1L);
            config.addDataSourceProperty("connectTimeout", "1000");
            config.addDataSourceProperty("socketTimeout", "1000");
            config.addDataSourceProperty("queryTimeoutKillsConnection", "true");
            long started = System.nanoTime();
            try (HikariDataSource pool = new HikariDataSource(config)) {
                assertTrue(proxy.firstConnectionAccepted.await(2, TimeUnit.SECONDS));
                assertThrows(SQLException.class, pool::getConnection);
                try (Connection connection = awaitConnection(pool, Duration.ofSeconds(5));
                     Statement statement = connection.createStatement()) {
                    assertTrue(statement.execute("SELECT 1"));
                }
                assertTrue(Duration.ofNanos(System.nanoTime() - started)
                        .compareTo(Duration.ofSeconds(5)) < 0);
                assertTrue(proxy.acceptedConnections.get() >= 2);
                assertEquals(0, pool.getHikariPoolMXBean().getActiveConnections());
                assertTrue(pool.getHikariPoolMXBean().getIdleConnections() >= 1);
            }
        }
    }

    private Connection awaitConnection(HikariDataSource pool, Duration timeout)
            throws SQLException {
        long deadline = System.nanoTime() + timeout.toNanos();
        SQLException lastFailure = null;
        while (System.nanoTime() < deadline) {
            try {
                return pool.getConnection();
            } catch (SQLException unavailable) {
                lastFailure = unavailable;
            }
        }
        throw lastFailure == null
                ? new SQLException("pool did not refill before deadline")
                : lastFailure;
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

    private static final class RecoveringHandshakeProxy implements AutoCloseable {
        private final String upstreamHost;
        private final int upstreamPort;
        private final ServerSocket server = new ServerSocket(0);
        private final ExecutorService io = Executors.newCachedThreadPool();
        private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
        private final CountDownLatch firstConnectionAccepted = new CountDownLatch(1);
        private final AtomicInteger acceptedConnections = new AtomicInteger();

        private RecoveringHandshakeProxy(String upstreamHost, int upstreamPort)
                throws Exception {
            this.upstreamHost = upstreamHost;
            this.upstreamPort = upstreamPort;
            io.execute(this::acceptLoop);
        }

        int port() { return server.getLocalPort(); }

        private void acceptLoop() {
            while (!server.isClosed()) {
                try {
                    Socket client = server.accept();
                    sockets.add(client);
                    if (acceptedConnections.incrementAndGet() == 1) {
                        firstConnectionAccepted.countDown();
                        continue;
                    }
                    Socket upstream = new Socket(upstreamHost, upstreamPort);
                    sockets.add(upstream);
                    io.execute(() -> pump(client, upstream));
                    io.execute(() -> pump(upstream, client));
                } catch (Exception stopped) {
                    if (!server.isClosed()) throw new IllegalStateException(stopped);
                }
            }
        }

        private void pump(Socket source, Socket destination) {
            byte[] buffer = new byte[8_192];
            try {
                InputStream input = source.getInputStream();
                OutputStream output = destination.getOutputStream();
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, count);
                    output.flush();
                }
            } catch (Exception disconnected) {
                // Driver timeout, pool close and fixture shutdown are expected exits.
            } finally {
                closeSocket(source);
                closeSocket(destination);
            }
        }

        @Override
        public void close() throws Exception {
            server.close();
            sockets.forEach(RecoveringHandshakeProxy::closeSocket);
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
