package com.myshoppingcart.order.adapter.out.cart;

import com.myshoppingcart.order.application.Carts;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Carts in Redis: one hash per customer, product id to quantity. A cart that is not changed for
 * {@link #IDLE_EXPIRY} disappears.
 */
@Component
class RedisCarts implements Carts {

    static final Duration IDLE_EXPIRY = Duration.ofDays(30);

    private final StringRedisTemplate redis;

    RedisCarts(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private static String key(String customerId) {
        return "cart:" + customerId;
    }

    /** Sorted by product id, so the cart reads the same way every time. */
    @Override
    public Map<UUID, Integer> get(String customerId) {
        Map<UUID, Integer> cart = new TreeMap<>();
        redis.<String, String>opsForHash().entries(key(customerId))
                .forEach((product, quantity) -> cart.put(UUID.fromString(product), Integer.parseInt(quantity)));
        return cart;
    }

    @Override
    public void put(String customerId, UUID productId, int quantity) {
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
