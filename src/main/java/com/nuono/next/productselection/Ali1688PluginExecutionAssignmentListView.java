package com.nuono.next.productselection;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Ali1688PluginExecutionAssignmentListView {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public List<Ali1688PluginExecutionAssignmentView> items = List.of();
    public Map<String, Object> summary = new LinkedHashMap<>();
    public Map<String, Object> diagnostics = new LinkedHashMap<>();
    public String emptyReason;
    public String message;
    public String refreshedAt = LocalDateTime.now().format(FORMATTER);
}
