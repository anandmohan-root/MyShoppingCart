package com.myshoppingcart.payment.adapter.in.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myshoppingcart.payment.application.PaymentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class RefundRequestedListener {

    /** The part of the RefundRequested message (published by order-service) that this service needs. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RefundRequested(UUID orderId) {
    }

    private final PaymentService payments;
    private final ObjectMapper json;

    RefundRequestedListener(PaymentService payments, ObjectMapper json) {
        this.payments = payments;
        this.json = json;
    }

    @KafkaListener(topics = "refund-requested")
    void onRefundRequested(String payload) throws JsonProcessingException {
        payments.refund(json.readValue(payload, RefundRequested.class).orderId());
    }
}
