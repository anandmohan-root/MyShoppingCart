package com.myshoppingcart.order.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class OrderTest {

    private static final UUID PRODUCT = UUID.randomUUID();

    @Test
    void newOrderIsPendingWithTotalOfPriceTimesQuantity() {
        Order order = Order.place("alice", PRODUCT, 3, new BigDecimal("18.75"));

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.total()).isEqualByComparingTo("56.25");
    }

    @Test
    void rejectsQuantityBelowOne() {
        assertThatIllegalArgumentException().isThrownBy(() -> Order.place("alice", PRODUCT, 0, BigDecimal.TEN));
    }

    @Test
    void payingIsIdempotent() {
        Order paid = Order.place("alice", PRODUCT, 1, BigDecimal.TEN).paid();

        assertThat(paid.status()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.paid()).isEqualTo(paid);
    }
}
