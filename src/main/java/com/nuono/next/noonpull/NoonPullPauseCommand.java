package com.nuono.next.noonpull;

public class NoonPullPauseCommand {
    private String reason;

    public NoonPullPauseCommand() {
    }

    public NoonPullPauseCommand(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
