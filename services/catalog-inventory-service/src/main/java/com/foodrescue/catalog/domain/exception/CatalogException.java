package com.foodrescue.catalog.domain.exception;

/** Lỗi nghiệp vụ của Catalog; status/code được CatalogError đổi thành response. */
public class CatalogException extends RuntimeException {
    private final int status;
    private final String code;

    public CatalogException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }

    public static CatalogException badRequest(String code, String message) {
        return new CatalogException(400, code, message);
    }

    public static CatalogException forbidden(String code, String message) {
        return new CatalogException(403, code, message);
    }

    public static CatalogException notFound(String code, String message) {
        return new CatalogException(404, code, message);
    }

    public static CatalogException conflict(String code, String message) {
        return new CatalogException(409, code, message);
    }
}
