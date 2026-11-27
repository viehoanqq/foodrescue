package com.foodrescue.identity.application.port.out;

public interface IEmailSenderPort {
    void ensureConfigured();
    void send(String to, String subject, String text);
    void sendHtml(String to, String subject, String plainText, String html);
}
