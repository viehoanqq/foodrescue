package com.foodrescue.identity.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class EmailOtpId implements Serializable {
    @Column(name = "otp_type", nullable = false, length = 32)
    private String otpType;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    public EmailOtpId() {}

    public EmailOtpId(String otpType, String email) {
        this.otpType = otpType;
        this.email = email;
    }

    public String getOtpType() { return otpType; }
    public void setOtpType(String otpType) { this.otpType = otpType; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmailOtpId that)) return false;
        return Objects.equals(otpType, that.otpType) && Objects.equals(email, that.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(otpType, email);
    }
}
