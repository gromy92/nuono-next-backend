package com.nuono.next.noon;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.spec.KeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.util.StringUtils;

public final class ChromeNoonCookieSupport {

    private static final Path CHROME_COOKIE_DB = Path.of(
            System.getProperty("user.home"),
            "Library",
            "Application Support",
            "Google",
            "Chrome",
            "Default",
            "Cookies"
    );
    private static final String SAFE_STORAGE_SERVICE = "Chrome Safe Storage";
    private static final String SAFE_STORAGE_ACCOUNT = "Chrome";
    private static final byte[] SALT = "saltysalt".getBytes(StandardCharsets.UTF_8);
    private static final byte[] IV = "                ".getBytes(StandardCharsets.UTF_8);
    private static final Duration CACHE_TTL = Duration.ofMinutes(2);
    private static final List<String> REQUIRED_COOKIE_NAMES = List.of("_npsid", "_nprtnetid");

    private static volatile CachedCookies cachedCookies;

    private ChromeNoonCookieSupport() {
    }

    public static Map<String, String> loadAuthCookies() {
        CachedCookies cached = cachedCookies;
        if (cached != null && !cached.isExpired()) {
            return new LinkedHashMap<>(cached.cookies);
        }

        if (!Files.exists(CHROME_COOKIE_DB)) {
            throw new IllegalStateException("本机 Chrome 默认资料目录里没有 Noon Cookie。");
        }

        try {
            String safeStorageValue = runCommand(List.of(
                    "security",
                    "find-generic-password",
                    "-w",
                    "-s",
                    SAFE_STORAGE_SERVICE,
                    "-a",
                    SAFE_STORAGE_ACCOUNT
            )).trim();
            if (!StringUtils.hasText(safeStorageValue)) {
                throw new IllegalStateException("没有拿到 Chrome Safe Storage 密钥。");
            }

            Path tempDb = Files.createTempFile("nuono-chrome-cookies", ".db");
            try {
                Files.copy(CHROME_COOKIE_DB, tempDb, StandardCopyOption.REPLACE_EXISTING);
                String query = "select name, hex(encrypted_value) from cookies "
                        + "where host_key='.noon.partners' and name in ('_npsid','_nprtnetid')";
                String rawRows = runCommand(List.of("sqlite3", tempDb.toString(), query));
                Map<String, String> cookies = decryptCookieRows(rawRows, safeStorageValue);
                for (String name : REQUIRED_COOKIE_NAMES) {
                    if (!StringUtils.hasText(cookies.get(name))) {
                        throw new IllegalStateException("Chrome Noon 会话缺少关键 Cookie: " + name);
                    }
                }
                cachedCookies = new CachedCookies(cookies);
                return new LinkedHashMap<>(cookies);
            } finally {
                Files.deleteIfExists(tempDb);
            }
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalStateException("读取 Chrome Noon 会话失败：" + exception.getMessage(), exception);
        }
    }

    private static Map<String, String> decryptCookieRows(String rawRows, String safeStorageValue)
            throws GeneralSecurityException {
        Map<String, String> cookies = new LinkedHashMap<>();
        if (!StringUtils.hasText(rawRows)) {
            return cookies;
        }

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        KeySpec spec = new PBEKeySpec(safeStorageValue.toCharArray(), SALT, 1003, 128);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKeySpec secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(IV));

        String[] lines = rawRows.split("\\R");
        for (String line : lines) {
            if (!StringUtils.hasText(line) || !line.contains("|")) {
                continue;
            }
            int splitIndex = line.indexOf('|');
            String name = line.substring(0, splitIndex).trim();
            String encryptedHex = line.substring(splitIndex + 1).trim();
            if (!StringUtils.hasText(name) || !StringUtils.hasText(encryptedHex)) {
                continue;
            }

            byte[] encryptedBytes = hexToBytes(encryptedHex);
            byte[] payload = encryptedBytes;
            if (encryptedBytes.length > 3
                    && encryptedBytes[0] == 'v'
                    && encryptedBytes[1] == '1'
                    && encryptedBytes[2] == '0') {
                payload = new byte[encryptedBytes.length - 3];
                System.arraycopy(encryptedBytes, 3, payload, 0, payload.length);
            }

            byte[] decrypted = cipher.doFinal(payload);
            if (decrypted.length > 32) {
                byte[] stripped = new byte[decrypted.length - 32];
                System.arraycopy(decrypted, 32, stripped, 0, stripped.length);
                decrypted = stripped;
            }
            cookies.put(name, new String(decrypted, StandardCharsets.UTF_8));
        }
        return cookies;
    }

    private static String runCommand(List<String> command) throws IOException {
        Process process = new ProcessBuilder(new ArrayList<>(command)).start();
        byte[] stdout;
        byte[] stderr;
        try (InputStream inputStream = process.getInputStream();
             InputStream errorStream = process.getErrorStream()) {
            stdout = readAllBytes(inputStream);
            stderr = readAllBytes(errorStream);
        }
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String errorText = new String(stderr, StandardCharsets.UTF_8).trim();
                throw new IOException(StringUtils.hasText(errorText) ? errorText : "命令执行失败");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("命令执行被中断", exception);
        }
        return new String(stdout, StandardCharsets.UTF_8);
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }

    private static byte[] hexToBytes(String hex) {
        byte[] result = new byte[hex.length() / 2];
        for (int index = 0; index < hex.length(); index += 2) {
            result[index / 2] = (byte) Integer.parseInt(hex.substring(index, index + 2), 16);
        }
        return result;
    }

    private static final class CachedCookies {
        private final Instant loadedAt;
        private final Map<String, String> cookies;

        private CachedCookies(Map<String, String> cookies) {
            this.loadedAt = Instant.now();
            this.cookies = new LinkedHashMap<>(cookies);
        }

        private boolean isExpired() {
            return loadedAt.plus(CACHE_TTL).isBefore(Instant.now());
        }
    }
}
