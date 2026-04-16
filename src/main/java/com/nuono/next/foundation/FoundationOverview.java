package com.nuono.next.foundation;

import java.util.ArrayList;
import java.util.List;

public class FoundationOverview {

    private String mode;

    private boolean ready;

    private String message;

    private FoundationStats counts;

    private List<FoundationUserSnapshot> sampleUsers = new ArrayList<>();

    private List<String> missingCoreTables = new ArrayList<>();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public FoundationStats getCounts() {
        return counts;
    }

    public void setCounts(FoundationStats counts) {
        this.counts = counts;
    }

    public List<FoundationUserSnapshot> getSampleUsers() {
        return sampleUsers;
    }

    public void setSampleUsers(List<FoundationUserSnapshot> sampleUsers) {
        this.sampleUsers = sampleUsers;
    }

    public List<String> getMissingCoreTables() {
        return missingCoreTables;
    }

    public void setMissingCoreTables(List<String> missingCoreTables) {
        this.missingCoreTables = missingCoreTables;
    }
}
