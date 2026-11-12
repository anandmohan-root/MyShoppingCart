package com.myshoppingcart.inventory.application;

import com.myshoppingcart.inventory.domain.OrderLine;
import com.myshoppingcart.inventory.domain.ReservationStatus;

import java.util.List;
import java.util.UUID;

/** Outbound port: one reservation per order. */
public interface Reservations {

    /**
     * Creates the order's reservation with the given status and lines, unless the order already
     * has one. Returns false, and changes nothing, if it had.
     */
    boolean createIfNew(UUID orderId, ReservationStatus status, List<OrderLine> lines);

    /**
     * Moves a RESERVED reservation to RELEASED and returns its lines, so they can be put back in
     * stock. Returns an empty list if the reservation was not RESERVED.
     */
    List<OrderLine> release(UUID orderId);
}
