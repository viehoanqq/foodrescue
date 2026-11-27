package com.foodrescue.identity.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "registration_email_otps")
public class RegistrationEmailOtpEntity {
    @Id @Column(name = "email", length = 255)
    private String email;
    @Column(name = "code_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String codeHash;
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    @Column(name = "resend_after", nullable = false)
    private LocalDateTime resendAfter;
    @Column(name = "attempts", nullable = false)
    private int attempts;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getResendAfter() { return resendAfter; }
    public void setResendAfter(LocalDateTime resendAfter) { this.resendAfter = resendAfter; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
