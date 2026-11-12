package com.myshoppingcart.order.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final UUID PRODUCT = UUID.randomUUID();

    private static Order pending() {
        return Order.place("alice", List.of(new OrderLine(PRODUCT, 1, BigDecimal.TEN)));
    }

    @Test
    void newOrderIsPendingWithTotalOfAllLines() {
        Order order = Order.place("alice", List.of(
                new OrderLine(PRODUCT, 3, new BigDecimal("18.75")),
                new OrderLine(UUID.randomUUID(), 1, new BigDecimal("249.00"))));

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.total()).isEqualByComparingTo("305.25");
    }

    @Test
    void rejectsQuantityBelowOne() {
        assertThatIllegalArgumentException().isThrownBy(() -> new OrderLine(PRODUCT, 0, BigDecimal.TEN));
    }

    @Test
    void rejectsEmptyOversizedOrRepeatedLines() {
        OrderLine line = new OrderLine(PRODUCT, 1, BigDecimal.TEN);
        List<OrderLine> tooMany = IntStream.rangeClosed(0, Order.MAX_LINES)
                .mapToObj(i -> new OrderLine(UUID.randomUUID(), 1, BigDecimal.TEN)).toList();

        assertThatIllegalArgumentException().isThrownBy(() -> Order.place("alice", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> Order.place("alice", tooMany));
        assertThatIllegalArgumentException().isThrownBy(() -> Order.place("alice", List.of(line, line)));
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
