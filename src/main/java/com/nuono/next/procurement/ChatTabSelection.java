package com.nuono.next.procurement;

class ChatTabSelection {
    public boolean ok;
    public ChromeTab tab;
    public ContactSelectionResult contactSelection;
    public String failureCode;
    public String failureMessage;

    static ChatTabSelection success(ChromeTab tab, ContactSelectionResult contactSelection) {
        ChatTabSelection result = new ChatTabSelection();
        result.ok = true;
        result.tab = tab;
        result.contactSelection = contactSelection;
        return result;
    }

    static ChatTabSelection failure(String failureCode, String failureMessage) {
        ChatTabSelection result = new ChatTabSelection();
        result.ok = false;
        result.failureCode = failureCode;
        result.failureMessage = failureMessage;
        return result;
    }
}
