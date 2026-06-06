package com.danmakulive.common.result;

public class Result<T> {

    private static final String SUCCESS_CODE = "0";

    private String code;
    private String message;
    private T data;

    private Result() {}

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = SUCCESS_CODE;
        r.data = data;
        return r;
    }

    public static Result<Void> success() {
        Result<Void> r = new Result<>();
        r.code = SUCCESS_CODE;
        return r;
    }

    public static Result<Void> failure(String code, String message) {
        Result<Void> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }

    public boolean isSuccess() {
        return SUCCESS_CODE.equals(code);
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
