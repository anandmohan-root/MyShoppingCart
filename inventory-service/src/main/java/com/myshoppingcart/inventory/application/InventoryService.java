package com.myshoppingcart.inventory.application;

import com.myshoppingcart.inventory.domain.OrderLine;
import com.myshoppingcart.inventory.domain.ReservationStatus;
import com.myshoppingcart.inventory.domain.StockItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Inventory's part of the order saga. Every event is safe to receive twice and in any order:
 * each order has exactly one reservation row, and every change checks that row first.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    /** The most one restock can add, to catch a mistyped quantity. */
    public static final int MAX_RESTOCK = 10_000;

    private final Stock stock;
    private final Reservations reservations;
    private final InventoryEvents events;

    public InventoryService(Stock stock, Reservations reservations, InventoryEvents events) {
        this.stock = stock;
        this.reservations = reservations;
        this.events = events;
    }

    public List<StockItem> all() {
        return stock.findAll();
    }

    public Optional<StockItem> find(UUID productId) {
        return stock.find(productId);
    }

    /** A new product in the catalog starts with nothing in stock. Safe to call more than once. */
    public void open(UUID productId) {
        stock.open(productId);
    }

    /** Adds stock for a product the shop already stocks; empty if it does not. */
    public Optional<StockItem> restock(UUID productId, int quantity, String by) {
        if (quantity < 1 || quantity > MAX_RESTOCK) {
            throw new IllegalArgumentException("quantity must be between 1 and " + MAX_RESTOCK);
        }
        Optional<StockItem> restocked = stock.add(productId, quantity);
        restocked.ifPresent(item -> log.info("{} restocked {} by {}, now {}", by, productId, quantity, item.available()));
        return restocked;
    }

    /**
     * Reserves everything the order needs, or nothing. The stock rows are locked first, so two
     * orders for the last item cannot both get it.
     */
    @Transactional
    public void reserve(UUID orderId, String customerId, BigDecimal amount, List<OrderLine> lines) {
        Map<UUID, Integer> available = stock.lockAvailable(lines.stream().map(OrderLine::productId).toList());
        List<UUID> missing = lines.stream()
                .filter(line -> available.getOrDefault(line.productId(), 0) < line.quantity())
                .map(OrderLine::productId)
                .toList();
        if (missing.isEmpty()) {
            if (reservations.createIfNew(orderId, ReservationStatus.RESERVED, lines)) {
                stock.take(lines);
                events.reserved(orderId, customerId, amount);
            }
        } else if (reservations.createIfNew(orderId, ReservationStatus.REJECTED, lines)) {
            events.rejected(orderId, "not enough stock of " + missing);
        }
    }

    /**
     * Puts a reservation's stock back, after a declined payment or a cancellation. If the order
     * has no reservation yet, records it as released so a late OrderCreated reserves nothing.
     */
    @Transactional
    public void release(UUID orderId) {
        if (reservations.createIfNew(orderId, ReservationStatus.RELEASED, List.of())) {
            return;
        }
        stock.putBack(reservations.release(orderId));
    }
}
