package com.nuono.next.noon;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

final class NoonProxyConnectPreflight {

    void verify(
            Proxy proxy,
            String fingerprint,
            String targetHost,
            int targetPort,
            int connectTimeoutMillis,
            int readTimeoutMillis
    ) {
        if (!(proxy.address() instanceof InetSocketAddress)) {
            throw failure(fingerprint, "UNSUPPORTED_ADDRESS", null);
        }
        try {
            if (proxy.type() == Proxy.Type.SOCKS) {
                verifySocks(
                        proxy,
                        targetHost,
                        targetPort,
                        connectTimeoutMillis,
                        readTimeoutMillis
                );
                return;
            }
            verifyHttp(
                    proxy,
                    fingerprint,
                    targetHost,
                    targetPort,
                    connectTimeoutMillis,
                    readTimeoutMillis
            );
        } catch (PreflightFailure failure) {
            throw failure;
        } catch (ConnectException exception) {
            throw failure(fingerprint, "CONNECT_REFUSED", exception);
        } catch (SocketTimeoutException exception) {
            throw failure(fingerprint, "CONNECT_TIMEOUT", exception);
        } catch (IOException exception) {
            throw failure(fingerprint, "CONNECT_IO", exception);
        }
    }

    private static void verifySocks(
            Proxy proxy,
            String targetHost,
            int targetPort,
            int connectTimeoutMillis,
            int readTimeoutMillis
    ) throws IOException {
        try (Socket socket = new Socket(proxy)) {
            socket.setSoTimeout(readTimeoutMillis);
            socket.connect(
                    InetSocketAddress.createUnresolved(targetHost, targetPort),
                    connectTimeoutMillis
            );
        }
    }

    private static void verifyHttp(
            Proxy proxy,
            String fingerprint,
            String targetHost,
            int targetPort,
            int connectTimeoutMillis,
            int readTimeoutMillis
    ) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect((InetSocketAddress) proxy.address(), connectTimeoutMillis);
            socket.setSoTimeout(readTimeoutMillis);
            String authority = targetHost + ":" + targetPort;
            socket.getOutputStream().write((
                    "CONNECT " + authority + " HTTP/1.1\r\n"
                            + "Host: " + authority + "\r\n"
                            + "Proxy-Connection: keep-alive\r\n\r\n"
            ).getBytes(StandardCharsets.US_ASCII));
            int status = parseStatus(readStatusLine(socket));
            if (status < 200 || status >= 300) {
                throw failure(fingerprint, "CONNECT_STATUS_" + status, null);
            }
        }
    }

    private static String readStatusLine(Socket socket) throws IOException {
        StringBuilder line = new StringBuilder();
        int value;
        while ((value = socket.getInputStream().read()) != -1 && line.length() < 512) {
            if (value == '\n') {
                break;
            }
            if (value != '\r') {
                line.append((char) value);
            }
        }
        return line.toString();
    }

    private static int parseStatus(String statusLine) {
        String[] parts = statusLine == null ? new String[0] : statusLine.trim().split("\\s+");
        if (parts.length < 2 || !parts[0].startsWith("HTTP/")) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static PreflightFailure failure(
            String fingerprint,
            String evidenceCode,
            Throwable cause
    ) {
        return new PreflightFailure(fingerprint, evidenceCode, cause);
    }

    static final class PreflightFailure extends IllegalStateException {
        private final String fingerprint;
        private final String evidenceCode;

        private PreflightFailure(String fingerprint, String evidenceCode, Throwable cause) {
            super("Noon proxy preflight failed: fingerprint="
                    + fingerprint + " stage=" + evidenceCode, cause);
            this.fingerprint = fingerprint;
            this.evidenceCode = evidenceCode;
        }

        String fingerprint() {
            return fingerprint;
        }

        String evidenceCode() {
            return evidenceCode;
        }
    }
}
