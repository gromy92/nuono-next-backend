package com.nuono.next.store;

public class StoreBindingResult {

    private boolean success;

    private String message;

    public static StoreBindingResult succeeded(String message) {
        StoreBindingResult result = new StoreBindingResult();
        result.setSuccess(true);
        result.setMessage(message);
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

}
