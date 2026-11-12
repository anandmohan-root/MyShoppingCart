package com.myshoppingcart.order.application;

import com.myshoppingcart.order.domain.Order;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The customer's cart. It holds products and quantities only: prices are looked up at checkout,
 * so the order is charged at the prices of that moment.
 */
@Service
public class CartService {

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
        return toItems(carts.get(customerId));
    }

    /** Sets the product's quantity in the cart. The product must exist in the catalog. */
    public List<OrderItem> put(String customerId, UUID productId, int quantity) {
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("quantity must be between 1 and " + MAX_QUANTITY);
        }
        Map<UUID, Integer> cart = carts.get(customerId);
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
     * Places one order for everything in the cart, then empties the cart. If emptying fails after
     * the order was stored, the order stands and the cart keeps its products.
     */
    public Order checkout(String customerId) {
        List<OrderItem> items = view(customerId);
        if (items.isEmpty()) {
            throw new EmptyCartException();
        }
        Order order = orders.place(customerId, items);
        carts.clear(customerId);
        return order;
    }

    private static List<OrderItem> toItems(Map<UUID, Integer> cart) {
        return cart.entrySet().stream().map(e -> new OrderItem(e.getKey(), e.getValue())).toList();
    }
}
