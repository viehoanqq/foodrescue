package com.foodrescue.identity.infrastructure.adapter.out.persistence;

import com.foodrescue.identity.application.port.out.IEmailOtpPort;
import com.foodrescue.identity.application.port.out.IEmailSenderPort;
import com.foodrescue.identity.domain.exception.IdentityException;
import com.foodrescue.identity.domain.model.enums.OtpType;
import com.foodrescue.identity.infrastructure.adapter.out.mail.OtpEmailTemplate;
import com.foodrescue.identity.infrastructure.adapter.out.persistence.entity.EmailOtpEntity;
import com.foodrescue.identity.infrastructure.adapter.out.persistence.entity.EmailOtpId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaEmailOtpAdapter implements IEmailOtpPort {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final IJpaEmailOtpRepository repository;
    private final IEmailSenderPort emailSender;
    private final byte[] secret;

    public JpaEmailOtpAdapter(IJpaEmailOtpRepository repository, IEmailSenderPort emailSender,
                              @Value("${app.otp.secret:}") String secret) {
        this.repository = repository;
        this.emailSender = emailSender;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override @Transactional
    public void send(OtpType type, String email) {
        emailSender.ensureConfigured();
        if (secret.length < 32) {
            throw new IdentityException(503, "EMAIL_NOT_CONFIGURED", "Chưa cấu hình dịch vụ email");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        EmailOtpId id = key(type, email);
        EmailOtpEntity otp = repository.findLocked(id.getOtpType(), id.getEmail()).orElseGet(EmailOtpEntity::new);
        if (otp.getResendAfter() != null && otp.getResendAfter().isAfter(now)) {
            throw new IdentityException(429, "OTP_RATE_LIMITED", "Vui lòng đợi 60 giây trước khi gửi lại mã");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        otp.setId(id);
        otp.setCodeHash(hash(type, email, code));
        otp.setExpiresAt(now.plusMinutes(10));
        otp.setResendAfter(now.plusSeconds(60));
        otp.setAttempts(0);
        otp.setCreatedAt(now);
        repository.saveAndFlush(otp);

        OtpEmailTemplate.EmailContent content = OtpEmailTemplate.build(type, code);
        emailSender.sendHtml(email, content.subject(), content.plainText(), content.html());
    }

    @Override
    public void verify(OtpType type, String email, String code) {
        EmailOtpId id = key(type, email);
        EmailOtpEntity otp = repository.findById(id).orElse(null);
        if (otp == null || otp.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw invalid();
        }
        if (otp.getAttempts() >= 5) {
            throw new IdentityException(429, "OTP_ATTEMPTS_EXCEEDED",
                    "Mã đã vượt quá số lần thử. Vui lòng yêu cầu mã mới.");
        }
        if (!matches(otp, type, email, code)) {
            repository.incrementAttempts(id.getOtpType(), id.getEmail());
            throw invalid();
        }
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void consume(OtpType type, String email, String code) {
        EmailOtpId id = key(type, email);
        EmailOtpEntity otp = repository.findLocked(id.getOtpType(), id.getEmail()).orElseThrow(this::invalid);
        if (otp.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))
                || otp.getAttempts() >= 5
                || !matches(otp, type, email, code)) {
            throw invalid();
        }
        repository.delete(otp);
    }

    private EmailOtpId key(OtpType type, String email) {
        return new EmailOtpId(type.name(), email);
    }

    private String hashDomainFor(OtpType type) {
        return switch (type) {
            case REGISTRATION -> "";
            case PASSWORD_RESET -> "password-reset:";
        };
    }

    private boolean matches(EmailOtpEntity otp, OtpType type, String email, String code) {
        if (code == null || !code.matches("[0-9]{6}")) return false;
        return MessageDigest.isEqual(otp.getCodeHash().getBytes(StandardCharsets.US_ASCII),
                hash(type, email, code).getBytes(StandardCharsets.US_ASCII));
    }

    private String hash(OtpType type, String email, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(
                    mac.doFinal((hashDomainFor(type) + email + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot hash email OTP", ex);
        }
    }

    private IdentityException invalid() {
        return new IdentityException(400, "INVALID_OTP", "Mã xác minh không đúng hoặc đã hết hạn");
    }
}
