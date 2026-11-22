package com.foodrescue.store.domain.exception;

public class StoreException extends RuntimeException {
    private final int status;
    private final String code;

    public StoreException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }
}
