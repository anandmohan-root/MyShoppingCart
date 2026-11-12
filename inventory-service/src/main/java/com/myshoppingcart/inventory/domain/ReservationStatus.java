package com.myshoppingcart.inventory.domain;

/**
 * <pre>
 * PENDING --enough stock---------> RESERVED --declined or cancelled--> RELEASED
 * PENDING --not enough stock-----> REJECTED
 * (none)  --cancelled first------> RELEASED (nothing is reserved for this order later)
 * </pre>
 */
public enum ReservationStatus {
    PENDING, RESERVED, REJECTED, RELEASED
}
