package com.myshoppingcart.order.application;

import com.myshoppingcart.order.domain.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The customer's cart. It holds products and quantities only: prices are looked up at checkout,
 * so the order is charged at the prices of that moment.
 *
 * <p>A cart becomes at most one order. Checkout uses the cart's id as the order's idempotency
 * key, so checking out the same cart again, after a timeout or a failure to empty it, returns the
 * order already placed instead of placing a second one.
 */
@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    public static final int MAX_QUANTITY = 99;

    private final Carts carts;
    private final ProductCatalog catalog;
    private final OrderService orders;

    public CartService(Carts carts, ProductCatalog catalog, OrderService orders) {
        this.carts = carts;
        this.catalog = catalog;
        this.orders = orders;
    }

    public List<OrderItem> view(String customerId) {
        return toItems(currentCart(customerId).quantities());
    }

    /** Sets the product's quantity in the cart. The product must exist in the catalog. */
    public List<OrderItem> put(String customerId, UUID productId, int quantity) {
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("quantity must be between 1 and " + MAX_QUANTITY);
        }
        Map<UUID, Integer> cart = currentCart(customerId).quantities();
        if (!cart.containsKey(productId) && cart.size() >= Order.MAX_LINES) {
            throw new IllegalArgumentException("a cart can hold at most " + Order.MAX_LINES + " products");
        }
        if (catalog.priceOf(productId).isEmpty()) {
            throw new UnknownProductException(productId);
        }
        carts.put(customerId, productId, quantity);
        return view(customerId);
    }

    public List<OrderItem> remove(String customerId, UUID productId) {
        carts.remove(customerId, productId);
        return view(customerId);
    }

    public void clear(String customerId) {
        carts.clear(customerId);
    }

    /**
     * Places one order for everything in the cart, then empties the cart. If the cart was already
     * ordered, returns that order. If emptying the cart fails, the order still stands: the failure
     * is logged, and the cart is emptied on the customer's next visit to it.
     */
    public Order checkout(String customerId) {
        Carts.Cart cart = carts.get(customerId);
        if (cart.id() != null) {
            var existing = orders.findByIdempotencyKey(customerId, keyOf(cart));
            if (existing.isPresent()) {
                emptyAfterOrder(customerId);
                return existing.get();
            }
        }
        if (cart.quantities().isEmpty()) {
            throw new EmptyCartException();
        }
        Order order = orders.place(customerId, toItems(cart.quantities()), keyOf(cart));
        emptyAfterOrder(customerId);
        return order;
    }

    /** The customer's cart, emptied first if it was already ordered but could not be emptied then. */
    private Carts.Cart currentCart(String customerId) {
        Carts.Cart cart = carts.get(customerId);
        if (cart.id() != null && orders.findByIdempotencyKey(customerId, keyOf(cart)).isPresent()) {
            carts.clear(customerId);
            return new Carts.Cart(null, Map.of());
        }
        return cart;
    }

    /** The order's idempotency key for this cart; null for a cart without an id. */
    private static String keyOf(Carts.Cart cart) {
        return cart.id() == null ? null : "cart:" + cart.id();
    }

    private void emptyAfterOrder(String customerId) {
        try {
            carts.clear(customerId);
        } catch (RuntimeException e) {
            log.warn("Order placed, but the cart of {} could not be emptied; it is emptied on the next visit", customerId, e);
        }
    }

    private static List<OrderItem> toItems(Map<UUID, Integer> quantities) {
        return quantities.entrySet().stream().map(e -> new OrderItem(e.getKey(), e.getValue())).toList();
    }
}
