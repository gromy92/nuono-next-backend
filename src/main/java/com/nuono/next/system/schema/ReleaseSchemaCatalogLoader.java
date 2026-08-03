package com.nuono.next.system.schema;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
class ReleaseSchemaCatalogLoader {
    private static final String CATALOG = "db/init/release-migrations.tsv";
    private static final int CATALOG_START_ORDER = 227;
    private static final String HEADER =
            "order\tmigration_key\tkind\tscript_path\tpostcheck_path\t"
                    + "livecheck_path";
    private static final Pattern NAME =
            Pattern.compile("^(\\d{3})_[a-z0-9_]+\\.sql$");

    List<ReleaseSchemaMigrationDescriptor> load() {
        List<ReleaseSchemaMigrationDescriptor> migrations = new ArrayList<>();
        List<Integer> orders = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource(CATALOG),
                StandardCharsets.UTF_8
        ))) {
            String header = reader.readLine();
            if (!HEADER.equals(header)) {
                throw invalid("unexpected catalog header");
            }
            String line;
            int previousOrder = -1;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] cells = line.split("\\t", -1);
                if (cells.length != 6) {
                    throw invalid("catalog row must contain six columns");
                }
                int order = parseOrder(cells[0]);
                String key = cells[1];
                String kind = cells[2];
                String scriptPath = safePath(cells[3], "db/init/");
                String postcheckPath = safePath(cells[4], "db/postcheck/");
                String livecheckPath = safeLivecheckPath(cells[5]);
                Matcher name = NAME.matcher(key);
                if (!name.matches()
                        || Integer.parseInt(name.group(1)) != order
                        || !key.equals(fileName(scriptPath))
                        || !key.equals(fileName(postcheckPath))
                        || !key.equals(fileName(livecheckPath))
                        || order <= previousOrder) {
                    throw invalid("catalog identity/order is invalid for " + key);
                }
                if (!List.of("BOOTSTRAP", "AUTO_ADDITIVE", "MANAGED").contains(kind)) {
                    throw invalid("unsupported migration kind for " + key);
                }
                migrations.add(new ReleaseSchemaMigrationDescriptor(
                        order,
                        key,
                        kind,
                        sha256(resource(scriptPath)),
                        sha256(resource(postcheckPath)),
                        sha256(resource(livecheckPath))
                ));
                orders.add(order);
                previousOrder = order;
            }
        } catch (IOException error) {
            throw invalid("cannot read release migration catalog", error);
        }
        validateCatalogOrders(orders);
        if (!"BOOTSTRAP".equals(migrations.get(0).getKind())
                || migrations.stream().filter(
                        item -> "BOOTSTRAP".equals(item.getKind())
                ).count() != 1) {
            throw invalid("catalog must start with exactly one BOOTSTRAP");
        }
        return migrations;
    }

    static void validateCatalogOrders(List<Integer> orders) {
        if (orders.isEmpty() || orders.get(0) != CATALOG_START_ORDER) {
            throw invalid("catalog must start at " + CATALOG_START_ORDER);
        }
        for (int index = 0; index < orders.size(); index++) {
            if (orders.get(index) != CATALOG_START_ORDER + index) {
                throw invalid("catalog orders must form a continuous sequence");
            }
        }
    }

    private static InputStream resource(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IOException("missing classpath resource " + path);
        }
        return resource.getInputStream();
    }

    private static String safePath(String value, String prefix) {
        if (!value.startsWith(prefix)
                || value.contains("..")
                || value.startsWith("/")
                || value.contains("\\")) {
            throw invalid("unsafe catalog resource path");
        }
        return value;
    }

    private static String safeLivecheckPath(String value) {
        if (value.startsWith("db/postcheck/")) {
            return safePath(value, "db/postcheck/");
        }
        return safePath(value, "db/livecheck/");
    }

    private static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static int parseOrder(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            throw invalid("invalid catalog order", error);
        }
    }

    private static String sha256(InputStream input) throws IOException {
        try (InputStream source = input) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int length;
            while ((length = source.read(buffer)) != -1) {
                digest.update(buffer, 0, length);
            }
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) {
                result.append(String.format(Locale.ROOT, "%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw invalid("SHA-256 is unavailable", error);
        }
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException(
                "release schema catalog is invalid: " + message
        );
    }

    private static IllegalStateException invalid(String message, Exception error) {
        return new IllegalStateException(
                "release schema catalog is invalid: " + message,
                error
        );
    }
}
