package com.danmakulive.common.exception;

public enum BaseErrorCode implements ErrorCode {

    // A: Client errors
    CLIENT_ERROR("A000001", "请求参数错误"),
    UNAUTHORIZED("A000002", "未登录或登录已过期"),
    FORBIDDEN("A000003", "无权限访问"),
    NOT_FOUND("A000004", "资源不存在"),
    DUPLICATE("A000005", "资源已存在"),
    VALIDATION_ERROR("A000006", "数据校验失败"),

    // B: Server errors
    SYSTEM_ERROR("B000001", "系统繁忙，请稍后重试"),
    ;

    private final String code;
    private final String message;

    BaseErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() { return code; }

    @Override
    public String message() { return message; }
}
