package com.myshoppingcart.payment.application;

import com.myshoppingcart.payment.domain.Payment;
import com.myshoppingcart.payment.domain.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentServiceTest {

    /** In-memory stand-in for the payment table, keyed by order id like its unique constraint. */
    private final Map<UUID, Payment> stored = new HashMap<>();
    private final List<Payment> completed = new ArrayList<>();
    private final List<Payment> declined = new ArrayList<>();

    private final PaymentService service = new PaymentService(
            new Payments() {
                @Override
                public boolean saveIfNew(Payment payment) {
                    return stored.putIfAbsent(payment.orderId(), payment) == null;
                }

                @Override
                public Optional<Payment> findByOrderId(UUID orderId) {
                    return Optional.ofNullable(stored.get(orderId));
                }

                @Override
                public void updateStatus(Payment payment) {
                    stored.put(payment.orderId(), payment);
                }
            },
            new PaymentEvents() {
                @Override
                public void completed(Payment payment) {
                    completed.add(payment);
                }

                @Override
                public void declined(Payment payment) {
                    declined.add(payment);
                }
            },
            new BigDecimal("1000.00"));

    @Test
    void duplicateOrderCreatedIsPaidAndPublishedOnce() {
        UUID orderId = UUID.randomUUID();

        service.payFor(orderId, new BigDecimal("37.50"));
        service.payFor(orderId, new BigDecimal("37.50"));

        assertThat(completed).hasSize(1);
        assertThat(completed.getFirst().orderId()).isEqualTo(orderId);
        assertThat(declined).isEmpty();
    }

    @Test
    void amountAboveTheLimitIsDeclinedOnce() {
        UUID orderId = UUID.randomUUID();

        service.payFor(orderId, new BigDecimal("1245.00"));
        service.payFor(orderId, new BigDecimal("1245.00"));

        assertThat(declined).hasSize(1);
        assertThat(completed).isEmpty();
        assertThat(stored.get(orderId).status()).isEqualTo(PaymentStatus.DECLINED);
    }

    @Test
    void amountAtTheLimitIsCompleted() {
        UUID orderId = UUID.randomUUID();

        service.payFor(orderId, new BigDecimal("1000.00"));

        assertThat(completed).hasSize(1);
    }

    @Test
    void refundMarksACompletedPaymentRefundedAndLeavesADeclinedOneAlone() {
        UUID paidOrder = UUID.randomUUID();
        UUID declinedOrder = UUID.randomUUID();
        service.payFor(paidOrder, new BigDecimal("37.50"));
        service.payFor(declinedOrder, new BigDecimal("5000.00"));

        service.refund(paidOrder);
        service.refund(paidOrder);
        service.refund(declinedOrder);
        service.refund(UUID.randomUUID());

        assertThat(stored.get(paidOrder).status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(stored.get(declinedOrder).status()).isEqualTo(PaymentStatus.DECLINED);
    }
}
