package com.foodrescue.gateway.chat;

public class ChatException extends RuntimeException {
    private final int status;
    private final String code;

    public ChatException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }
}
