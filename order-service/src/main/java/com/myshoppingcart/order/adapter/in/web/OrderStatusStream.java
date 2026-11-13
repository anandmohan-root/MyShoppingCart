package com.myshoppingcart.order.adapter.in.web;

import com.myshoppingcart.order.application.OrderStatusChanged;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Open server-sent event streams, per customer. A status change is sent after its transaction
 * commits, so a browser never sees a change that was rolled back.
 *
 * shortcut: the streams live in this one process; with several order-service instances a change
 * handled by one would not reach browsers connected to another. Fan out through Kafka or Redis
 * pub/sub when running more than one.
 */
@Component
class OrderStatusStream {

    /** Shorter than Kong's 60 s read timeout: the stream ends cleanly and the browser reconnects. */
    static final Duration STREAM_LIFETIME = Duration.ofSeconds(50);

    private final Map<String, Set<SseEmitter>> streams = new ConcurrentHashMap<>();

    SseEmitter open(String customerId) {
        SseEmitter emitter = new SseEmitter(STREAM_LIFETIME.toMillis());
        Set<SseEmitter> mine = streams.computeIfAbsent(customerId, id -> ConcurrentHashMap.newKeySet());
        mine.add(emitter);
        Runnable forget = () -> mine.remove(emitter);
        emitter.onCompletion(forget);
        emitter.onTimeout(forget);
        emitter.onError(error -> forget.run());
        return emitter;
    }

    @TransactionalEventListener
    void onStatusChanged(OrderStatusChanged change) {
        for (SseEmitter emitter : streams.getOrDefault(change.customerId(), Set.of())) {
            try {
                emitter.send(SseEmitter.event().name("order-status")
                        .data(new StatusMessage(change.orderId().toString(), change.status().name())));
            } catch (IOException | IllegalStateException e) {
                // The browser went away; its callbacks remove the emitter.
                emitter.completeWithError(e);
            }
        }
    }

    record StatusMessage(String orderId, String status) {
    }
}
