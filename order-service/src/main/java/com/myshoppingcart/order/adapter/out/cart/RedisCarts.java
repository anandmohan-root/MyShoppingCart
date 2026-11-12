package com.myshoppingcart.order.adapter.out.cart;

import com.myshoppingcart.order.application.Carts;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Carts in Redis: one hash per customer holding the cart's id under {@link #ID_FIELD} and
 * product id to quantity for everything else. A cart that is not changed for
 * {@link #IDLE_EXPIRY} disappears.
 */
@Component
class RedisCarts implements Carts {

    static final Duration IDLE_EXPIRY = Duration.ofDays(30);
    static final String ID_FIELD = "id";

    private final StringRedisTemplate redis;

    RedisCarts(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private static String key(String customerId) {
        return "cart:" + customerId;
    }

    /** Products sorted by id, so the cart reads the same way every time. */
    @Override
    public Cart get(String customerId) {
        Map<String, String> fields = redis.<String, String>opsForHash().entries(key(customerId));
        Map<UUID, Integer> quantities = new TreeMap<>();
        fields.forEach((field, value) -> {
            if (!field.equals(ID_FIELD)) {
                quantities.put(UUID.fromString(field), Integer.parseInt(value));
            }
        });
        String id = fields.get(ID_FIELD);
        return new Cart(id == null ? null : UUID.fromString(id), quantities);
    }

    @Override
    public void put(String customerId, UUID productId, int quantity) {
        redis.opsForHash().putIfAbsent(key(customerId), ID_FIELD, UUID.randomUUID().toString());
        redis.opsForHash().put(key(customerId), productId.toString(), Integer.toString(quantity));
        redis.expire(key(customerId), IDLE_EXPIRY);
    }

    @Override
    public void remove(String customerId, UUID productId) {
        redis.opsForHash().delete(key(customerId), productId.toString());
    }

    @Override
    public void clear(String customerId) {
        redis.delete(key(customerId));
    }
}
