package com.myshoppingcart.order.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final UUID PRODUCT = UUID.randomUUID();

    private static Order pending() {
        return Order.place("alice", PRODUCT, 1, BigDecimal.TEN);
    }

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
        Order paid = pending().paid();

        assertThat(paid.status()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.paid()).isEqualTo(paid);
    }

    @Test
    void declinedPaymentEndsAPendingOrderOnly() {
        assertThat(pending().declined().status()).isEqualTo(OrderStatus.PAYMENT_DECLINED);
        assertThat(pending().paid().declined().status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void pendingAndPaidOrdersCanBeCancelled() {
        assertThat(pending().cancelled().status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(pending().paid().cancelled().status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void declinedOrCancelledOrdersCannotBeCancelled() {
        assertThatThrownBy(() -> pending().declined().cancelled()).isInstanceOf(OrderNotCancellableException.class);
        assertThatThrownBy(() -> pending().cancelled().cancelled()).isInstanceOf(OrderNotCancellableException.class);
    }

    @Test
    void paymentArrivingAfterCancellationDoesNotReviveTheOrder() {
        assertThat(pending().cancelled().paid().status()).isEqualTo(OrderStatus.CANCELLED);
    }
}
