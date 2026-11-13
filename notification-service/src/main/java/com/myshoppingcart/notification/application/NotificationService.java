package com.myshoppingcart.notification.application;

import com.myshoppingcart.notification.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationStore store;
    private final Mailer mailer;

    public NotificationService(NotificationStore store, Mailer mailer) {
        this.store = store;
        this.mailer = mailer;
    }

    public void orderCreated(UUID orderId, String email, BigDecimal total) {
        store.saveContact(new OrderContact(orderId, email, total));
    }

    /**
     * Sends the email once. The "sent" row is written first and the email sent inside the same
     * transaction: if sending fails, the row is rolled back and Kafka redelivers the event. If the
     * email went out but the commit then failed, the customer could get it twice; delivery is at
     * least once.
     */
    @Transactional
    public void notify(UUID orderId, Notification notification) {
        OrderContact contact = store.findContact(orderId).orElseThrow(() -> new ContactNotYetKnownException(orderId));
        if (contact.email() == null || contact.email().isBlank()) {
            log.info("No email address for order {}, {} not sent", orderId, notification);
            return;
        }
        if (store.markSent(orderId, notification)) {
            mailer.send(contact.email(), notification.subject(), notification.body(orderId, contact.total()));
        }
    }
}
