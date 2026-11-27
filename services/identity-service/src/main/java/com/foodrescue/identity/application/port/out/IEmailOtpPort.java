package com.foodrescue.identity.application.port.out;

public interface IEmailOtpPort {
    enum OtpType {
        REGISTRATION,
        PASSWORD_RESET
    }

    void send(OtpType type, String email);
    void verify(OtpType type, String email, String code);
    void consume(OtpType type, String email, String code);
}
