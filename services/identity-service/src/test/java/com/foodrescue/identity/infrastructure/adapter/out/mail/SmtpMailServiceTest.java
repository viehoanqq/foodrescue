package com.foodrescue.identity.infrastructure.adapter.out.mail;

import com.foodrescue.identity.domain.exception.IdentityException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SmtpMailServiceTest {
    @Test
    void rejectsWhenSenderEmailIsMissing() {
        JavaMailSender sender = mock(JavaMailSender.class);
        SmtpMailService service = new SmtpMailService(sender, " ");

        IdentityException ex = assertThrows(IdentityException.class,
                () -> service.send("user@example.com", "Subject", "Body"));

        assertEquals(503, ex.status());
        assertEquals("EMAIL_NOT_CONFIGURED", ex.code());
        verifyNoInteractions(sender);
    }

    @Test
    void sendsSimpleMailMessageWithConfiguredSender() {
        JavaMailSender sender = mock(JavaMailSender.class);
        SmtpMailService service = new SmtpMailService(sender, "noreply@foodrescue.test");

        service.send("user@example.com", "Test Subject", "Hello");

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(mail.capture());
        assertEquals("noreply@foodrescue.test", mail.getValue().getFrom());
        assertArrayEquals(new String[]{"user@example.com"}, mail.getValue().getTo());
        assertEquals("Test Subject", mail.getValue().getSubject());
        assertEquals("Hello", mail.getValue().getText());
    }

    @Test
    void sendsMultipartHtmlMimeMessageWithSubjectAndUtf8() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(mime);
        SmtpMailService service = new SmtpMailService(sender, "noreply@foodrescue.test");

        service.sendHtml("user@example.com", "FoodRescue - Mã xác minh", "Mã: 123456",
                "<html><body><b>123456</b></body></html>");

        verify(sender).send(mime);
        assertEquals("FoodRescue - Mã xác minh", mime.getSubject());
        assertEquals("noreply@foodrescue.test", mime.getFrom()[0].toString());
        assertEquals("user@example.com", mime.getAllRecipients()[0].toString());
    }

    @Test
    void wrapsMailExceptionAsServiceUnavailable() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("SMTP down")).when(sender).send(any(SimpleMailMessage.class));
        SmtpMailService service = new SmtpMailService(sender, "noreply@foodrescue.test");

        IdentityException ex = assertThrows(IdentityException.class,
                () -> service.send("user@example.com", "Subject", "Body"));

        assertEquals(503, ex.status());
        assertEquals("EMAIL_SEND_FAILED", ex.code());
    }
}
