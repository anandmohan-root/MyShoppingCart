package com.myshoppingcart.notification.application;

import com.myshoppingcart.notification.domain.Notification;

import java.util.Optional;
import java.util.UUID;

/** Outbound port: order contacts and the emails already sent. */
public interface NotificationStore {

    /** Remembers the contact; a repeated OrderCreated changes nothing. */
    void saveContact(OrderContact contact);

    Optional<OrderContact> findContact(UUID orderId);

    /** Records that this email is being sent. Returns false, and records nothing, if it already was. */
    boolean markSent(UUID orderId, Notification notification);
}
