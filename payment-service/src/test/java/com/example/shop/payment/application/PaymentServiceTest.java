package com.example.shop.payment.application;

import com.example.shop.payment.domain.Payment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentServiceTest {

    private final Set<UUID> paidOrders = new HashSet<>();
    private final List<Payment> published = new ArrayList<>();
    private final PaymentService service = new PaymentService(p -> paidOrders.add(p.orderId()), published::add);

    @Test
    void duplicateOrderCreatedIsPaidAndPublishedOnce() {
        UUID orderId = UUID.randomUUID();

        service.payFor(orderId, new BigDecimal("37.50"));
        service.payFor(orderId, new BigDecimal("37.50"));

        assertThat(published).hasSize(1);
        assertThat(published.getFirst().orderId()).isEqualTo(orderId);
    }
}
