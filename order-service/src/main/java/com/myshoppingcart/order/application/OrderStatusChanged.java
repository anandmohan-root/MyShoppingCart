package com.myshoppingcart.order.application;

import com.myshoppingcart.order.domain.OrderStatus;

import java.util.UUID;

/** Published inside order-service when an order's status changes, so open browsers can be told. */
public record OrderStatusChanged(String customerId, UUID orderId, OrderStatus status) {
}
