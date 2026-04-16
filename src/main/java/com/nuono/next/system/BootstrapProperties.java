package com.nuono.next.system;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nuono.bootstrap")
public class BootstrapProperties {

    private boolean dbEnabled;

    private String schema = "nuono_new_dev";

    private List<String> expectedCoreTables = new ArrayList<>();

    public boolean isDbEnabled() {
        return dbEnabled;
    }

    public void setDbEnabled(boolean dbEnabled) {
        this.dbEnabled = dbEnabled;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public List<String> getExpectedCoreTables() {
        return expectedCoreTables;
    }

    public void setExpectedCoreTables(List<String> expectedCoreTables) {
        this.expectedCoreTables = expectedCoreTables;
    }
}
