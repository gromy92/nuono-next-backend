package com.nuono.next.procurement;

class ComposerProbe {
    public boolean ok;
    public String locator;
    public String editorText;
    public String bodyText;
    public String evidence;
    public String failureCode;
    public String failureMessage;

    static ComposerProbe failure(String failureCode, String failureMessage) {
        ComposerProbe probe = new ComposerProbe();
        probe.ok = false;
        probe.failureCode = failureCode;
        probe.failureMessage = failureMessage;
        return probe;
    }
}
