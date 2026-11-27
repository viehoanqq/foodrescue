package com.foodrescue.identity.infrastructure.adapter.out.mail;

import com.foodrescue.identity.application.port.out.IEmailSenderPort;
import com.foodrescue.identity.domain.exception.IdentityException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/** Outbound SMTP adapter for sending plain-text and HTML transactional emails. */
@Service
public class SmtpMailService implements IEmailSenderPort {
    private final JavaMailSender mailSender;
    private final String from;

    public SmtpMailService(JavaMailSender mailSender,
                           @Value("${spring.mail.username:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void ensureConfigured() {
        if (from == null || from.isBlank()) {
            throw new IdentityException(503, "EMAIL_NOT_CONFIGURED", "Chưa cấu hình dịch vụ email");
        }
    }

    @Override
    public void send(String to, String subject, String text) {
        ensureConfigured();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            throw sendFailed();
        }
    }

    @Override
    public void sendHtml(String to, String subject, String plainText, String html) {
        ensureConfigured();
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(plainText, html);
            mailSender.send(mimeMessage);
        } catch (MailException | MessagingException ex) {
            throw sendFailed();
        }
    }

    private IdentityException sendFailed() {
        return new IdentityException(503, "EMAIL_SEND_FAILED", "Không gửi được email. Vui lòng thử lại sau.");
    }
}
