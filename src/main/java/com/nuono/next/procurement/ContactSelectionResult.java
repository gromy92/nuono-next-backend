package com.nuono.next.procurement;

class ContactSelectionResult {
    public boolean ok;
    public boolean selected;
    public boolean alreadySelected;
    public String matchedText;
    public String matchedToken;
    public String locator;
    public String failureCode;
    public String failureMessage;

    static ContactSelectionResult success(
            boolean alreadySelected,
            String matchedText,
            String matchedToken,
            String locator
    ) {
        ContactSelectionResult result = new ContactSelectionResult();
        result.ok = true;
        result.selected = true;
        result.alreadySelected = alreadySelected;
        result.matchedText = matchedText;
        result.matchedToken = matchedToken;
        result.locator = locator;
        return result;
    }

    static ContactSelectionResult failure(String failureCode, String failureMessage) {
        ContactSelectionResult result = new ContactSelectionResult();
        result.ok = false;
        result.failureCode = failureCode;
        result.failureMessage = failureMessage;
        return result;
    }
}
