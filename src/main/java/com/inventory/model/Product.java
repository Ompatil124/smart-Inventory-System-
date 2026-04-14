package com.inventory.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <h2>Product — Model Layer (Phase 2)</h2>
 *
 * <p>Represents a single inventory item with full encapsulation.
 * Every field is {@code private}; access is only through typed
 * getters and validated setters.</p>
 *
 * <p><b>Core fields (Phase 2 spec):</b></p>
 * <ul>
 *   <li>{@code name}        – human-readable product name (non-blank)</li>
 *   <li>{@code category}    – product category / section</li>
 *   <li>{@code price}       – unit price in USD (≥ 0)</li>
 *   <li>{@code quantity}    – stock count (≥ 0)</li>
 *   <li>{@code expiryDate}  – optional best-before date; {@code null} for non-perishables</li>
 * </ul>
 *
 * <p><b>System fields (maintained for controller compatibility):</b></p>
 * <ul>
 *   <li>{@code id}              – auto-assigned unique identifier (≥ 1)</li>
 *   <li>{@code discountPercent} – promotional discount 0–100 %</li>
 * </ul>
 */
public class Product {

    // ── Phase 2 core fields (ALL private — full encapsulation) ────────────────

    /** Human-readable product name. Never null or blank after construction. */
    private String name;

    /** Product category / group (e.g. "Dairy", "Electronics"). */
    private String category;

    /** Unit selling price in USD. Always ≥ 0. */
    private double price;

    /** Current stock quantity. Always ≥ 0. */
    private int quantity;

    /**
     * Best-before / expiry date.
     * {@code null} indicates the product does not expire.
     */
    private LocalDate expiryDate;

    // ── System fields (private, used by controller/logic layers) ─────────────

    /** Auto-assigned unique numeric identifier. */
    private int id;

    /**
     * Barcode strings associated with this product.
     * A product may have multiple barcodes (e.g. different packaging sizes).
     * Never {@code null}; starts empty.
     */
    private final ArrayList<String> barcodes = new ArrayList<>();

    /**
     * Promotional discount percentage in the range [0, 100].
     * {@code 0.0} means no discount is active.
     */
    private double discountPercent;


    // ══════════════════════════════════════════════════════════════════════════
    //  CONSTRUCTORS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * No-argument constructor.
     * Fields are left at Java defaults; use setters before storing the object.
     */
    public Product() {}

    /**
     * Full-argument constructor — preferred way to create a complete product.
     *
     * @param id              unique identifier (positive integer)
     * @param name            product name (must not be blank)
     * @param category        product category
     * @param price           unit price in USD (must be ≥ 0)
     * @param quantity        stock quantity (must be ≥ 0)
     * @param expiryDate      best-before date, or {@code null} if non-perishable
     * @param discountPercent discount percentage in [0, 100]
     * @throws IllegalArgumentException if any numeric constraint is violated
     *                                  or name is blank
     */
    public Product(int id,
                   String name,
                   String category,
                   double price,
                   int quantity,
                   LocalDate expiryDate,
                   double discountPercent) {
        setId(id);
        setName(name);
        setCategory(category);
        setPrice(price);
        setQuantity(quantity);
        setExpiryDate(expiryDate);
        setDiscountPercent(discountPercent);
    }

    /**
     * Convenience constructor matching the Phase 2 field order
     * (no id, no discount — for quick construction in tests).
     *
     * @param name       product name
     * @param category   product category
     * @param price      unit price
     * @param quantity   stock quantity
     * @param expiryDate best-before date, or {@code null}
     */
    public Product(String name,
                   String category,
                   double price,
                   int quantity,
                   LocalDate expiryDate) {
        setName(name);
        setCategory(category);
        setPrice(price);
        setQuantity(quantity);
        setExpiryDate(expiryDate);
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  GETTERS  (read-only access to private state)
    // ══════════════════════════════════════════════════════════════════════════

    /** @return the product's unique identifier */
    public int getId()                 { return id; }

    /** @return the product name */
    public String getName()            { return name; }

    /** @return the product category */
    public String getCategory()        { return category; }

    /** @return the unit price in USD */
    public double getPrice()           { return price; }

    /** @return current stock quantity */
    public int getQuantity()           { return quantity; }

    /**
     * @return the expiry date, or {@code null} if non-perishable
     */
    public LocalDate getExpiryDate()   { return expiryDate; }

    /** @return the active discount percentage (0 = none) */
    public double getDiscountPercent() { return discountPercent; }

    /**
     * @return an unmodifiable view of the barcodes associated with this product
     */
    public List<String> getBarcodes() { return Collections.unmodifiableList(barcodes); }

    /**
     * Adds a barcode to this product if it is not already present.
     *
     * @param barcode the barcode string to add (ignored if null/blank or duplicate)
     */
    public void addBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) return;
        String trimmed = barcode.trim();
        if (!barcodes.contains(trimmed)) barcodes.add(trimmed);
    }

    /**
     * Replaces the barcode list (used when loading from CSV).
     *
     * @param list new list of barcodes; {@code null} is treated as empty
     */
    public void setBarcodes(List<String> list) {
        barcodes.clear();
        if (list != null) list.forEach(this::addBarcode);
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  SETTERS  (validated — enforce business rules at the boundary)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Sets the product ID.
     *
     * @param id must be a positive integer
     * @throws IllegalArgumentException if id ≤ 0
     */
    public void setId(int id) {
        if (id <= 0) throw new IllegalArgumentException("Product ID must be positive (got " + id + ").");
        this.id = id;
    }

    /**
     * Sets the product name.
     *
     * @param name must not be {@code null} or blank
     * @throws IllegalArgumentException if name is null or blank
     */
    public void setName(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Product name must not be blank.");
        this.name = name.trim();
    }

    /**
     * Sets the product category.
     *
     * @param category allowed to be blank/null (stored as empty string in that case)
     */
    public void setCategory(String category) {
        this.category = (category == null) ? "" : category.trim();
    }

    /**
     * Sets the unit price.
     *
     * @param price must be ≥ 0
     * @throws IllegalArgumentException if price is negative
     */
    public void setPrice(double price) {
        if (price < 0) throw new IllegalArgumentException("Price cannot be negative (got " + price + ").");
        this.price = price;
    }

    /**
     * Sets the stock quantity.
     *
     * @param quantity must be ≥ 0
     * @throws IllegalArgumentException if quantity is negative
     */
    public void setQuantity(int quantity) {
        if (quantity < 0)
            throw new IllegalArgumentException("Quantity cannot be negative (got " + quantity + ").");
        this.quantity = quantity;
    }

    /**
     * Sets the expiry date.
     *
     * @param expiryDate a future or past date, or {@code null} for non-perishables
     */
    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;   // null is a valid "no expiry" sentinel
    }

    /**
     * Sets the discount percentage.
     *
     * @param discountPercent value in [0, 100]
     * @throws IllegalArgumentException if outside that range
     */
    public void setDiscountPercent(double discountPercent) {
        if (discountPercent < 0 || discountPercent > 100)
            throw new IllegalArgumentException(
                "Discount must be 0–100 % (got " + discountPercent + ").");
        this.discountPercent = discountPercent;
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  DERIVED / BUSINESS HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Calculates the effective selling price after the discount is applied.
     *
     * @return {@code price * (1 - discountPercent / 100)}
     */
    public double getDiscountedPrice() {
        return price * (1.0 - discountPercent / 100.0);
    }

    /**
     * Checks whether this product is past its expiry date.
     *
     * @return {@code true} if {@code expiryDate} is set and is before today
     */
    public boolean isExpired() {
        return expiryDate != null && expiryDate.isBefore(LocalDate.now());
    }

    /**
     * Checks whether this product will expire within the given number of days
     * (today + {@code days}), inclusive of today.
     *
     * @param days look-ahead window (positive integer)
     * @return {@code true} if expiry is within the window and the product has
     *         not yet expired
     */
    public boolean isExpiringSoon(int days) {
        if (expiryDate == null) return false;
        LocalDate today     = LocalDate.now();
        LocalDate threshold = today.plusDays(days);
        return !expiryDate.isBefore(today) && !expiryDate.isAfter(threshold);
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  OBJECT OVERRIDES
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns a single-line, human-readable representation of this product.
     *
     * <pre>
     * [ID:  1] Whole Milk               | Cat: Dairy        | Qty:  100 | Price:    $2.99 | Expiry: 2026-04-01  | Discount: 5.0%
     * </pre>
     */
    @Override
    public String toString() {
        String expiryStr   = (expiryDate != null)    ? expiryDate.toString() : "N/A";
        String discountStr = (discountPercent > 0.0) ? discountPercent + "%" : "None";
        String barcodeStr  = barcodes.isEmpty() ? "None" : String.join("|", barcodes);

        return String.format(
            "[ID: %2d] %-25s | Cat: %-12s | Qty: %4d | Price: $%8.2f | Expiry: %-12s | Discount: %s | Barcodes: %s",
            id, name, category, quantity, price, expiryStr, discountStr, barcodeStr
        );
    }

    /**
     * Two products are <em>equal</em> if and only if they share the same ID.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Product other)) return false;
        return this.id == other.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
