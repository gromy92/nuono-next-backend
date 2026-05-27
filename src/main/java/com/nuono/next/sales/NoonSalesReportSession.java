package com.nuono.next.sales;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public interface NoonSalesReportSession {

    JsonNode postJson(String url, JsonNode body, boolean withProject, Map<String, String> extraHeaders);

    String getText(String url, boolean withProject, Map<String, String> extraHeaders);
}
