package com.foodrescue.identity.application.port.out;

public interface IRegistrationOtpPort {
    void send(String email);
    void verify(String email, String code);
    void consume(String email, String code);
}
