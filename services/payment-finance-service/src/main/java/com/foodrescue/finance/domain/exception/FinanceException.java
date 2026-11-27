package com.foodrescue.finance.domain.exception;

public class FinanceException extends RuntimeException {
    private final int status;
    private final String code;

    public FinanceException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static FinanceException conflict(String code) {
        return new FinanceException(409, code, code);
    }
}
