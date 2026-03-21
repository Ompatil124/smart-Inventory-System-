package com.inventory.logic;

import com.inventory.model.Product;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Pure business logic – no I/O here.
 * Operates on the shared product list held by InventoryController.
 */
public class InventoryLogic {

    // ── Search ────────────────────────────────────────────────────────────────

    /**
     * Case-insensitive search by product name fragment.
     */
    public List<Product> searchByName(List<Product> inventory, String query) {
        String q = query.trim().toLowerCase();
        return inventory.stream()
                .filter(p -> p.getName().toLowerCase().contains(q))
                .collect(Collectors.toList());
    }

    /**
     * Find a product by exact ID.
     */
    public Optional<Product> findById(List<Product> inventory, int id) {
        return inventory.stream().filter(p -> p.getId() == id).findFirst();
    }

    // ── Sort ──────────────────────────────────────────────────────────────────

    public enum SortField { NAME, PRICE, QUANTITY, EXPIRY }

    /**
     * Returns a new sorted list; original list is unchanged.
     */
    public List<Product> sort(List<Product> inventory, SortField field, boolean ascending) {
        Comparator<Product> cmp = switch (field) {
            case NAME     -> Comparator.comparing(Product::getName,
                                String.CASE_INSENSITIVE_ORDER);
            case PRICE    -> Comparator.comparingDouble(Product::getPrice);
            case QUANTITY -> Comparator.comparingInt(Product::getQuantity);
            case EXPIRY   -> Comparator.comparing(
                                p -> p.getExpiryDate() != null
                                        ? p.getExpiryDate()
                                        : java.time.LocalDate.MAX);
        };
        if (!ascending) cmp = cmp.reversed();
        return inventory.stream().sorted(cmp).collect(Collectors.toList());
    }

    // ── Reports ───────────────────────────────────────────────────────────────

    /**
     * Products with expiry date set, expiring within {@code withinDays} days
     * (inclusive) sorted by expiry ascending.
     */
    public List<Product> expiryReport(List<Product> inventory, int withinDays) {
        return inventory.stream()
                .filter(p -> p.getExpiryDate() != null)
                .filter(p -> p.isExpiringSoon(withinDays) || p.isExpired())
                .sorted(Comparator.comparing(Product::getExpiryDate))
                .collect(Collectors.toList());
    }

    /**
     * Products that currently have a discount > 0, sorted by discount desc.
     */
    public List<Product> discountReport(List<Product> inventory) {
        return inventory.stream()
                .filter(p -> p.getDiscountPercent() > 0)
                .sorted(Comparator.comparingDouble(Product::getDiscountPercent).reversed())
                .collect(Collectors.toList());
    }

    // ── ID generator ─────────────────────────────────────────────────────────

    /**
     * Returns the next available ID (max existing + 1, or 1 if empty).
     */
    public int nextId(List<Product> inventory) {
        return inventory.stream()
                .mapToInt(Product::getId)
                .max()
                .orElse(0) + 1;
    }
}
