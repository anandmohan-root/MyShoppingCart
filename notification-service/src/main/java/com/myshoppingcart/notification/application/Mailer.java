package com.myshoppingcart.notification.application;

/** Outbound port: sends a plain-text email. Throws if the mail server does not accept it. */
public interface Mailer {

    void send(String to, String subject, String body);
}
