package com.myshoppingcart.notification.adapter.out.mail;

import com.myshoppingcart.notification.application.Mailer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Sends through the SMTP server in spring.mail (Mailpit when running locally). */
@Component
class SmtpMailer implements Mailer {

    private final JavaMailSender mail;
    private final String from;

    SmtpMailer(JavaMailSender mail, @Value("${notification.from}") String from) {
        this.mail = mail;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mail.send(message);
    }
}
