package com.foodrescue.identity.application.port.out;

import com.foodrescue.identity.domain.model.enums.OtpType;

public interface IEmailOtpPort {
    void send(OtpType type, String email);
    void verify(OtpType type, String email, String code);
    void consume(OtpType type, String email, String code);
}
