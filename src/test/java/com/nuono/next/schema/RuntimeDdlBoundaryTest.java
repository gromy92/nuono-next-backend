package com.nuono.next.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeDdlBoundaryTest {
    private static final Pattern DDL = Pattern.compile(
            "(?i)\\b(?:"
                    + "CREATE\\s+(?:(?:OR\\s+REPLACE|TEMPORARY|UNIQUE)\\s+)*"
                    + "(?:(?:ALGORITHM\\s*=\\s*\\S+|DEFINER\\s*=\\s*\\S+"
                    + "|SQL\\s+SECURITY\\s+(?:DEFINER|INVOKER))\\s+)*"
                    + "(?:TABLE|INDEX|VIEW|TRIGGER|PROCEDURE|FUNCTION|EVENT|SCHEMA|DATABASE)\\b"
                    + "|DROP\\s+(?:TEMPORARY\\s+)?"
                    + "(?:TABLE|INDEX|VIEW|TRIGGER|PROCEDURE|FUNCTION|EVENT|SCHEMA|DATABASE)\\b"
                    + "|ALTER\\s+"
                    + "(?:TABLE|INDEX|VIEW|TRIGGER|PROCEDURE|FUNCTION|EVENT|SCHEMA|DATABASE)\\b"
                    + "|TRUNCATE\\s+(?:TABLE\\s+)?(?=[`A-Z_])"
                    + "|RENAME\\s+(?:TABLE|INDEX|VIEW)\\b"
                    + ")"
    );
    private static final Pattern MYBATIS_SQL_ANNOTATION = Pattern.compile(
            "@(?:org\\.apache\\.ibatis\\.annotations\\.)?"
                    + "(?:Insert|Update|Delete|Select)\\s*\\("
    );
    private static final Pattern JAVA_STRING_LITERAL = Pattern.compile(
            "\"((?:\\\\.|[^\"\\\\])*)\""
    );

    @Test
    void applicationSourceDoesNotOwnSchemaDdl() throws IOException {
        List<String> violations = new ArrayList<>();
        inspect(Path.of("src/main/java"), ".java", violations);
        inspect(Path.of("src/main/resources"), ".xml", violations);

        assertThat(violations)
                .as("Schema DDL belongs to the release-side Migration Module, never application requests")
                .isEmpty();
    }

    @Test
    void recognizesSupportedSchemaDdlForms() {
        List<String> ddlStatements = List.of(
                "CREATE TABLE sample (id BIGINT)",
                "CREATE TEMPORARY TABLE scratch (id BIGINT)",
                "DROP TABLE sample",
                "DROP TEMPORARY TABLE scratch",
                "ALTER TABLE sample ADD COLUMN name VARCHAR(20)",
                "TRUNCATE TABLE sample",
                "TRUNCATE TABLE `sample`",
                "TRUNCATE sample",
                "RENAME TABLE sample TO sample_archive",
                "CREATE INDEX idx_sample_name ON sample(name)",
                "CREATE UNIQUE INDEX uk_sample_name ON sample(name)",
                "DROP INDEX idx_sample_name ON sample",
                "CREATE OR REPLACE VIEW sample_view AS SELECT 1",
                "CREATE DEFINER='nuono'@'%' TRIGGER sample_trigger BEFORE INSERT ON sample SET @x = 1",
                "DROP VIEW sample_view",
                "CREATE TRIGGER sample_trigger BEFORE INSERT ON sample FOR EACH ROW SET @x = 1",
                "DROP TRIGGER sample_trigger",
                "CREATE PROCEDURE sample_procedure() SELECT 1",
                "DROP PROCEDURE sample_procedure",
                "CREATE FUNCTION sample_function() RETURNS INT RETURN 1",
                "DROP FUNCTION sample_function",
                "CREATE EVENT sample_event ON SCHEDULE EVERY 1 DAY DO SELECT 1",
                "DROP EVENT sample_event"
        );

        for (String statement : ddlStatements) {
            assertThat(DDL.matcher(statement).find())
                    .as("Expected runtime DDL guard to reject: %s", statement)
                    .isTrue();
        }
    }

    @Test
    void allowsDmlAndNonSqlCreateLanguage() {
        List<String> allowedStatements = List.of(
                "SELECT * FROM sample",
                "INSERT INTO sample(id) VALUES (1)",
                "UPDATE sample SET id = 2",
                "DELETE FROM sample",
                "URI.create(value)",
                "create a task",
                "ALTER VALUE",
                "DROP SHIPMENT"
        );

        for (String statement : allowedStatements) {
            assertThat(DDL.matcher(statement).find())
                    .as("Expected runtime DDL guard to allow: %s", statement)
                    .isFalse();
        }
    }

    @Test
    void joinsFragmentedMyBatisAnnotationStrings(@TempDir Path temporaryDirectory) throws IOException {
        Path source = temporaryDirectory.resolve("FragmentedMapper.java");
        Files.writeString(
                source,
                "interface FragmentedMapper {\n"
                        + "  @org.apache.ibatis.annotations.Update({\n"
                        + "    \"CREATE\",\n"
                        + "    \"UNIQUE\",\n"
                        + "    \"INDEX uk_sample_name ON sample(name)\"\n"
                        + "  })\n"
                        + "  void migrate();\n"
                        + "}\n",
                StandardCharsets.UTF_8
        );
        List<String> violations = new ArrayList<>();

        inspect(temporaryDirectory, ".java", violations);

        assertThat(violations)
                .anySatisfy(violation -> assertThat(violation)
                        .contains("joined MyBatis annotation", "CREATE UNIQUE INDEX"));
    }

    @Test
    void fragmentedMyBatisDmlRemainsAllowed(@TempDir Path temporaryDirectory) throws IOException {
        Path source = temporaryDirectory.resolve("DmlMapper.java");
        Files.writeString(
                source,
                "interface DmlMapper {\n"
                        + "  @Update({\"UPDATE\", \"sample SET name = #{name}\"})\n"
                        + "  void update(String name);\n"
                        + "}\n",
                StandardCharsets.UTF_8
        );
        List<String> violations = new ArrayList<>();

        inspect(temporaryDirectory, ".java", violations);

        assertThat(violations).isEmpty();
    }

    @Test
    void missingScanRootFailsFast(@TempDir Path temporaryDirectory) {
        Path missingRoot = temporaryDirectory.resolve("missing");

        assertThatThrownBy(() -> inspect(missingRoot, ".java", new ArrayList<>()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DDL scan root does not exist");
    }

    private static void inspect(Path root, String suffix, List<String> violations) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("DDL scan root does not exist: " + root);
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(suffix))
                    .forEach(path -> findViolations(path, violations));
        }
    }

    private static void findViolations(Path path, List<String> violations) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            appendViolations(path, source, "", violations);
            if (path.toString().endsWith(".java")) {
                int annotationNumber = 0;
                for (String sql : joinedMyBatisStatements(source)) {
                    appendViolations(
                            path,
                            sql,
                            " [joined MyBatis annotation " + (++annotationNumber) + "]",
                            violations
                    );
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to inspect " + path, exception);
        }
    }

    private static void appendViolations(
            Path path,
            String source,
            String origin,
            List<String> violations
    ) {
        var matcher = DDL.matcher(source);
        while (matcher.find()) {
            long line = 1 + source.substring(0, matcher.start())
                    .chars()
                    .filter(character -> character == '\n')
                    .count();
            violations.add(path + ":" + line + origin + " " + matcher.group());
        }
    }

    private static List<String> joinedMyBatisStatements(String source) {
        List<String> statements = new ArrayList<>();
        int cursor = 0;
        while (cursor < source.length()) {
            var annotation = MYBATIS_SQL_ANNOTATION.matcher(source);
            if (!annotation.find(cursor)) {
                break;
            }
            int openingParenthesis = annotation.end() - 1;
            int closingParenthesis = closingParenthesis(source, openingParenthesis);
            if (closingParenthesis < 0) {
                throw new IllegalStateException("Unclosed MyBatis SQL annotation.");
            }
            String annotationBody = source.substring(openingParenthesis + 1, closingParenthesis);
            var literal = JAVA_STRING_LITERAL.matcher(annotationBody);
            StringBuilder statement = new StringBuilder();
            while (literal.find()) {
                if (statement.length() > 0) {
                    statement.append(' ');
                }
                statement.append(decodeJavaString(literal.group(1)));
            }
            if (statement.length() > 0) {
                statements.add(statement.toString());
            }
            cursor = closingParenthesis + 1;
        }
        return statements;
    }

    private static int closingParenthesis(String source, int openingParenthesis) {
        int depth = 0;
        boolean inString = false;
        boolean inCharacter = false;
        boolean escaped = false;
        for (int index = openingParenthesis; index < source.length(); index++) {
            char current = source.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if ((inString || inCharacter) && current == '\\') {
                escaped = true;
                continue;
            }
            if (!inCharacter && current == '"') {
                inString = !inString;
                continue;
            }
            if (!inString && current == '\'') {
                inCharacter = !inCharacter;
                continue;
            }
            if (inString || inCharacter) {
                continue;
            }
            if (current == '(') {
                depth++;
            } else if (current == ')' && --depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private static String decodeJavaString(String source) {
        StringBuilder decoded = new StringBuilder(source.length());
        boolean escaped = false;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            if (!escaped && current == '\\') {
                escaped = true;
                continue;
            }
            if (escaped) {
                decoded.append(current == 'n' || current == 'r' || current == 't' ? ' ' : current);
                escaped = false;
            } else {
                decoded.append(current);
            }
        }
        if (escaped) {
            decoded.append('\\');
        }
        return decoded.toString();
    }
}
