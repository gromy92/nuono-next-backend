package com.nuono.next.procurement;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
class ChromeAppleScriptClient {

    private static final String TAB_FIELD_SEPARATOR = "<<<NUONO_TAB_FIELD>>>";

    List<ChromeTab> listChromeTabs() {
        String output = runAppleScript(
                "set AppleScript's text item delimiters to \"\"\n"
                        + "on sanitizeValue(rawValue)\n"
                        + "  set normalizedText to rawValue as string\n"
                        + "  set AppleScript's text item delimiters to {return, linefeed, tab}\n"
                        + "  set cleanedItems to every text item of normalizedText\n"
                        + "  set AppleScript's text item delimiters to \" \"\n"
                        + "  set cleanedText to cleanedItems as string\n"
                        + "  set AppleScript's text item delimiters to \"\"\n"
                        + "  return cleanedText\n"
                        + "end sanitizeValue\n"
                        + "on joinLines(itemsList)\n"
                        + "  set AppleScript's text item delimiters to linefeed\n"
                        + "  set joinedText to itemsList as string\n"
                        + "  set AppleScript's text item delimiters to \"\"\n"
                        + "  return joinedText\n"
                        + "end joinLines\n"
                        + "tell application \"Google Chrome\"\n"
                        + "  set outputLines to {}\n"
                        + "  repeat with w from 1 to count of windows\n"
                        + "    set tabCounter to 0\n"
                        + "    repeat with t in tabs of window w\n"
                        + "      set tabCounter to tabCounter + 1\n"
                        + "      set end of outputLines to (w as string) & "
                        + quoted(TAB_FIELD_SEPARATOR)
                        + " & (tabCounter as string) & "
                        + quoted(TAB_FIELD_SEPARATOR)
                        + " & my sanitizeValue(title of t) & "
                        + quoted(TAB_FIELD_SEPARATOR)
                        + " & my sanitizeValue(URL of t)\n"
                        + "    end repeat\n"
                        + "  end repeat\n"
                        + "  return my joinLines(outputLines)\n"
                        + "end tell"
        );

        List<ChromeTab> tabs = new ArrayList<>();
        if (!StringUtils.hasText(output)) {
            return tabs;
        }
        String[] lines = output.split("\\R");
        for (String line : lines) {
            if (!StringUtils.hasText(line)) {
                continue;
            }
            String[] parts = line.split(java.util.regex.Pattern.quote(TAB_FIELD_SEPARATOR), 4);
            if (parts.length < 4) {
                continue;
            }
            ChromeTab tab = new ChromeTab();
            tab.windowIndex = parseInt(parts[0]);
            tab.tabIndex = parseInt(parts[1]);
            tab.title = normalize(parts[2]);
            tab.url = normalize(parts[3]);
            tabs.add(tab);
        }
        return tabs;
    }

    void focusTab(ChromeTab tab) {
        runAppleScript(
                "tell application \"Google Chrome\"\n"
                        + "  set active tab index of window " + tab.windowIndex + " to " + tab.tabIndex + "\n"
                        + "  set index of window " + tab.windowIndex + " to 1\n"
                        + "  activate\n"
                        + "end tell"
        );
        sleep(300L);
    }

    void openTab(String url) {
        runAppleScript(
                "tell application \"Google Chrome\"\n"
                        + "  if (count of windows) = 0 then\n"
                        + "    make new window\n"
                        + "  end if\n"
                        + "  make new tab at end of tabs of window 1 with properties {URL:" + quoted(url) + "}\n"
                        + "  activate\n"
                        + "end tell"
        );
    }

    String executeTabJavascript(ChromeTab tab, String javascript) {
        String payload = Base64.getEncoder().encodeToString(javascript.getBytes(StandardCharsets.UTF_8));
        return runAppleScript(
                "tell application \"Google Chrome\"\n"
                        + "  tell tab " + tab.tabIndex + " of window " + tab.windowIndex + "\n"
                        + "    return execute javascript " + quoted("eval(atob('" + payload + "'))") + "\n"
                        + "  end tell\n"
                        + "end tell"
        );
    }

    String readFully(InputStream inputStream) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int readBytes;
            while ((readBytes = inputStream.read(buffer)) >= 0) {
                outputStream.write(buffer, 0, readBytes);
            }
            return outputStream.toString(StandardCharsets.UTF_8);
        }
    }

    void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待浏览器回读结果时被中断。", exception);
        }
    }

    private String runAppleScript(String script) {
        Process process = null;
        try {
            process = new ProcessBuilder("/usr/bin/osascript", "-")
                    .redirectErrorStream(false)
                    .start();
            try (OutputStream outputStream = process.getOutputStream()) {
                outputStream.write(script.getBytes(StandardCharsets.UTF_8));
            }
            int exitCode = process.waitFor();
            String stdout = readFully(process.getInputStream()).trim();
            String stderr = readFully(process.getErrorStream()).trim();
            if (exitCode != 0) {
                throw new IllegalStateException(firstNonBlank(stderr, stdout, "AppleScript 执行失败。"));
            }
            return stdout;
        } catch (IOException exception) {
            throw new IllegalStateException("无法调用本机 AppleScript，请确认当前机器可控制 Google Chrome。", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("调用 AppleScript 时被中断，暂时不能继续真实发送。", exception);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private String quoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private int parseInt(String rawValue) {
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }
}
