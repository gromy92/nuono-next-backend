package com.nuono.next.foundation.standard;

public class FoundationQualityState {

    private final String key;
    private final String name;
    private final String description;

    public FoundationQualityState(String key, String name, String description) {
        this.key = key;
        this.name = name;
        this.description = description;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
