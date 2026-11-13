package com.myshoppingcart.inventory.application;

import com.myshoppingcart.inventory.domain.OrderLine;
import com.myshoppingcart.inventory.domain.StockItem;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Outbound port: stock levels. */
public interface Stock {

    List<StockItem> findAll();

    Optional<StockItem> find(UUID productId);

    /**
     * Locks the stock rows of these products until the caller's transaction ends and returns what
     * is available. Products without stock are missing from the result.
     */
    Map<UUID, Integer> lockAvailable(Collection<UUID> productIds);

    /** Adds to the product's stock in one statement and returns the new level; empty if the product has no stock row. */
    Optional<StockItem> add(UUID productId, int quantity);

    void take(List<OrderLine> lines);

    void putBack(List<OrderLine> lines);
}
