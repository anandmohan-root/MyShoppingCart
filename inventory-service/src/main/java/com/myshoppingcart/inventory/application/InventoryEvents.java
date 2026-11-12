package com.myshoppingcart.inventory.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Outbound port: tells the rest of the platform about reservations. Implementations must record
 * the event in the caller's transaction, so a reservation and its event commit together.
 */
public interface InventoryEvents {

    void reserved(UUID orderId, String customerId, BigDecimal amount);

    void rejected(UUID orderId, String reason);
}
