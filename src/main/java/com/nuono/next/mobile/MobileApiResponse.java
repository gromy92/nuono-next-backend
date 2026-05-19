package com.nuono.next.mobile;

public class MobileApiResponse<T> {

    private int code;

    private String msg;

    private T data;

    public static <T> MobileApiResponse<T> success(T data) {
        MobileApiResponse<T> response = new MobileApiResponse<>();
        response.setCode(200);
        response.setMsg("success");
        response.setData(data);
        return response;
    }

    public static <T> MobileApiResponse<T> failure(int code, String message) {
        MobileApiResponse<T> response = new MobileApiResponse<>();
        response.setCode(code);
        response.setMsg(message);
        return response;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
