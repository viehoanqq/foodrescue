package com.foodrescue.buildingblocks.http;

public final class InternalServiceException extends RuntimeException {
    private final int status;

    public InternalServiceException(int status, String responseBody, Throwable cause) {
        super("Internal service returned HTTP " + status + ": " + responseBody, cause);
        this.status = status;
    }

    public int status() { return status; }
}
