package com.nuono.next.procurement.aliorder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

/** Loads the candidate runtime configuration without creating a Spring application context. */
final class Ali1688Dp10OpenApiProbeEnvironment {
    private static final Pattern KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final String PREFIX =
            "nuono.procurement.ali1688.historical-order.open-api";
    private static final String RUNTIME_PREFIX = "nuono.data-pull.runtime";

    private final Map<String, Object> values;

    private Ali1688Dp10OpenApiProbeEnvironment(Map<String, Object> values) {
        this.values = Map.copyOf(values);
    }

    static Ali1688Dp10OpenApiProbeEnvironment load(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("PROBE_ENV_FILE_INVALID");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("export ")) line = line.substring(7).trim();
            int separator = line.indexOf('=');
            if (separator <= 0) throw invalidLine(index);
            String key = line.substring(0, separator).trim();
            if (!KEY.matcher(key).matches() || values.containsKey(key)) {
                throw invalidLine(index);
            }
            values.put(key, parseValue(line.substring(separator + 1).trim(), index));
        }
        return new Ali1688Dp10OpenApiProbeEnvironment(values);
    }

    Ali1688HistoricalOrderOpenApiProperties openApiProperties() throws IOException {
        return bind(PREFIX, Ali1688HistoricalOrderOpenApiProperties.class);
    }

    DataPullRuntimeProperties runtimeProperties() throws IOException {
        return bind(RUNTIME_PREFIX, DataPullRuntimeProperties.class);
    }

    private <T> T bind(String prefix, Class<T> type) throws IOException {
        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("probe-env", values)
        );
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        loader.load("probe-application", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);
        return Binder.get(environment)
                .bind(prefix, Bindable.of(type))
                .orElseThrow(() -> new IllegalStateException("PROBE_CONFIG_MISSING"));
    }

    String require(String key) {
        Object value = values.get(key);
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isEmpty()) throw new IllegalStateException("PROBE_REQUIRED_CONFIG_MISSING");
        return text;
    }

    boolean matchesRawValues(Environment environment) {
        if (environment == null) return false;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!java.util.Objects.equals(
                    String.valueOf(entry.getValue()),
                    environment.getProperty(entry.getKey())
            )) return false;
        }
        return true;
    }

    private static String parseValue(String raw, int index) {
        if (raw.isEmpty()) return "";
        char quote = raw.charAt(0);
        if (quote == '\'' || quote == '"') {
            int end = closingQuote(raw, quote);
            String trailing = raw.substring(end + 1).trim();
            if (!trailing.isEmpty() && !trailing.startsWith("#")) throw invalidLine(index);
            String content = raw.substring(1, end);
            return quote == '\'' ? content : decodeDoubleQuoted(content, index);
        }
        int comment = raw.indexOf(" #");
        return (comment < 0 ? raw : raw.substring(0, comment)).trim();
    }

    private static int closingQuote(String raw, char quote) {
        boolean escaped = false;
        for (int index = 1; index < raw.length(); index++) {
            char current = raw.charAt(index);
            if (quote == '"' && current == '\\' && !escaped) {
                escaped = true;
                continue;
            }
            if (current == quote && !escaped) return index;
            escaped = false;
        }
        throw new IllegalArgumentException("PROBE_ENV_QUOTE_INVALID");
    }

    private static String decodeDoubleQuoted(String value, int line) {
        StringBuilder decoded = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != '\\') {
                decoded.append(current);
                continue;
            }
            if (++index >= value.length()) throw invalidLine(line);
            char escaped = value.charAt(index);
            if (escaped == 'n') decoded.append('\n');
            else if (escaped == 'r') decoded.append('\r');
            else if (escaped == 't') decoded.append('\t');
            else if (escaped == '"' || escaped == '\\' || escaped == '$') decoded.append(escaped);
            else throw invalidLine(line);
        }
        return decoded.toString();
    }

    private static IllegalArgumentException invalidLine(int zeroBasedLine) {
        return new IllegalArgumentException("PROBE_ENV_LINE_INVALID_" + (zeroBasedLine + 1));
    }
}
