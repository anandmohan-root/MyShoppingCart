package com.myshoppingcart.payment.adapter.out.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * Polls the outbox table and publishes pending rows to Kafka. A row is marked published
 * only after the broker acknowledged it, so delivery is at least once and consumers must
 * be idempotent. Production designs often replace this poller with CDC (Debezium).
 */
@Component
class OutboxRelay {

    private record Row(long id, String topic, String key, String payload) {
    }

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafka;

    OutboxRelay(JdbcClient jdbc, KafkaTemplate<String, String> kafka) {
        this.jdbc = jdbc;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void publishPending() throws Exception {
        var rows = jdbc.sql("""
                        SELECT id, topic, message_key, payload FROM outbox
                        WHERE published_at IS NULL ORDER BY id LIMIT 50
                        FOR UPDATE SKIP LOCKED""")
                .query((rs, n) -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)))
                .list();
        for (Row row : rows) {
            kafka.send(row.topic(), row.key(), row.payload()).get(10, TimeUnit.SECONDS);
            jdbc.sql("UPDATE outbox SET published_at = now() WHERE id = :id").param("id", row.id()).update();
        }
    }
}
