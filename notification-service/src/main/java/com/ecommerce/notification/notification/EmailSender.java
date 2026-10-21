package com.ecommerce.notification.notification;

import com.ecommerce.notification.config.NotificationProperties;
import com.ecommerce.notification.notification.EmailComposer.EmailMessage;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/** Invio SMTP: in sviluppo verso Mailpit, in produzione verso il servizio configurato in MAIL_HOST. */
@Component
public class EmailSender {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    public EmailSender(JavaMailSender mailSender, NotificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public void send(String recipient, EmailMessage email) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.fromAddress(), properties.fromName());
            helper.setTo(recipient);
            helper.setSubject(email.subject());
            helper.setText(email.text(), email.html());
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException exception) {
            throw new MailSendException("Impossibile preparare l'email per " + recipient, exception);
        }
    }
}
