package com.foodrescue.identity.infrastructure.adapter.out.persistence;

import com.foodrescue.identity.application.port.out.IRegistrationOtpPort;
import com.foodrescue.identity.domain.exception.IdentityException;
import com.foodrescue.identity.infrastructure.adapter.out.persistence.entity.RegistrationEmailOtpEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaRegistrationOtpAdapter implements IRegistrationOtpPort {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final IJpaRegistrationOtpRepository repository;
    private final JavaMailSender mailSender;
    private final String from;
    private final byte[] secret;

    public JpaRegistrationOtpAdapter(IJpaRegistrationOtpRepository repository, JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String from,
            @Value("${app.otp.secret:}") String secret) {
        this.repository = repository;
        this.mailSender = mailSender;
        this.from = from;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override @Transactional
    public void send(String email) {
        if (from.isBlank() || secret.length < 32)
            throw new IdentityException(503, "EMAIL_NOT_CONFIGURED", "Chưa cấu hình dịch vụ email");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        RegistrationEmailOtpEntity otp = repository.findLocked(email).orElseGet(RegistrationEmailOtpEntity::new);
        if (otp.getResendAfter() != null && otp.getResendAfter().isAfter(now))
            throw new IdentityException(429, "OTP_RATE_LIMITED", "Vui lòng đợi 60 giây trước khi gửi lại mã");
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        otp.setEmail(email);
        otp.setCodeHash(hash(email, code));
        otp.setExpiresAt(now.plusMinutes(10));
        otp.setResendAfter(now.plusSeconds(60));
        otp.setAttempts(0);
        otp.setCreatedAt(now);
        repository.saveAndFlush(otp);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("FoodRescue - Mã xác minh đăng ký");
        message.setText("Mã xác minh FoodRescue của bạn là: " + code +
                "\nMã có hiệu lực trong 10 phút. Không chia sẻ mã này với người khác.");
        try { mailSender.send(message); }
        catch (MailException ex) {
            throw new IdentityException(503, "EMAIL_SEND_FAILED", "Không gửi được email xác minh. Vui lòng thử lại sau.");
        }
    }

    @Override
    public void verify(String email, String code) {
        RegistrationEmailOtpEntity otp = repository.findById(email).orElse(null);
        if (otp == null || otp.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC)))
            throw invalid();
        if (otp.getAttempts() >= 5) throw new IdentityException(429, "OTP_ATTEMPTS_EXCEEDED", "Mã đã vượt quá số lần thử. Vui lòng yêu cầu mã mới.");
        if (!matches(otp, email, code)) {
            repository.incrementAttempts(email);
            throw invalid();
        }
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void consume(String email, String code) {
        RegistrationEmailOtpEntity otp = repository.findLocked(email).orElseThrow(this::invalid);
        if (otp.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC)) || otp.getAttempts() >= 5
                || !matches(otp, email, code)) throw invalid();
        repository.delete(otp);
    }

    private boolean matches(RegistrationEmailOtpEntity otp, String email, String code) {
        if (code == null || !code.matches("[0-9]{6}")) return false;
        return MessageDigest.isEqual(otp.getCodeHash().getBytes(StandardCharsets.US_ASCII),
                hash(email, code).getBytes(StandardCharsets.US_ASCII));
    }

    private String hash(String email, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((email + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("Cannot hash registration OTP", ex); }
    }

    private IdentityException invalid() {
        return new IdentityException(400, "INVALID_OTP", "Mã xác minh không đúng hoặc đã hết hạn");
    }
}
