package com.inventory.logic;

import com.inventory.model.Product;

/**
 * <h2>DiscountEngine — Logic Layer (Phase 4)</h2>
 *
 * <p>Applies rule-based automatic discounts to {@link Product} objects.
 * All members are {@code static}; this class must not be instantiated.</p>
 *
 * <h3>Discount rules (evaluated in priority order, first match wins)</h3>
 * <table border="1" cellpadding="4">
 *   <caption>Rule table</caption>
 *   <tr><th>Priority</th><th>Condition</th>              <th>Discount</th></tr>
 *   <tr><td>1 (highest)</td><td>Expiry within 3 days</td><td>30 %</td></tr>
 *   <tr><td>2</td>         <td>Category = "Bakery"</td>  <td>20 %</td></tr>
 *   <tr><td>3</td>         <td>Quantity &gt; 100</td>    <td>10 %</td></tr>
 *   <tr><td>—</td>         <td>No rule matched</td>      <td>0 % (unchanged)</td></tr>
 * </table>
 *
 * <p>Rules are <em>mutually exclusive</em>: once a rule fires the remaining
 * rules are skipped (if-else semantics).  Ordering from highest to lowest
 * discount ensures the most urgent / beneficial rule always takes precedence.</p>
 *
 * <p>When a rule fires, the product's {@code discountPercent} field is updated
 * via {@link Product#setDiscountPercent(double)} so the object records the
 * applied rate and {@link Product#getDiscountedPrice()} remains the single
 * source of truth for the final price.</p>
 *
 * <h3>Integration with ExpiryChecker</h3>
 * <p>Expiry proximity is determined by delegating to
 * {@link ExpiryChecker#daysUntilExpiry(Product)} — no raw date arithmetic
 * is duplicated here.</p>
 */
public final class DiscountEngine {

    // ── Rule constants — change these in one place to adjust all rules ────────

    /** Days-to-expiry threshold that triggers the near-expiry discount. */
    public static final int    NEAR_EXPIRY_DAYS     = 3;

    /** Discount applied to products expiring within {@link #NEAR_EXPIRY_DAYS}. */
    public static final double NEAR_EXPIRY_DISCOUNT  = 30.0;

    /** Category name (case-insensitive) that triggers the Bakery discount. */
    public static final String BAKERY_CATEGORY       = "Bakery";

    /** Discount applied to Bakery category products. */
    public static final double BAKERY_DISCOUNT        = 20.0;

    /** Quantity threshold above which the bulk discount is applied. */
    public static final int    BULK_QUANTITY_MIN      = 100;

    /** Discount applied to products with quantity above {@link #BULK_QUANTITY_MIN}. */
    public static final double BULK_DISCOUNT          = 10.0;

    /** Sentinel value used when no rule matches — discount stays at 0 %. */
    public static final double NO_DISCOUNT            = 0.0;

    /** Prevent instantiation — all members are static. */
    private DiscountEngine() {}


    // ══════════════════════════════════════════════════════════════════════════
    //  applyDiscount
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Evaluates the discount rules against {@code product}, updates the
     * product's {@code discountPercent} field with the matched rate, and
     * returns the resulting discounted price.
     *
     * <h4>Rule evaluation order</h4>
     * <ol>
     *   <li><b>Near-expiry (30 %)</b> — fires when the product has an expiry
     *       date and {@link ExpiryChecker#daysUntilExpiry(Product)} returns a
     *       value in the range {@code [0, 3]} (expires today through 3 days
     *       from now).</li>
     *   <li><b>Bakery category (20 %)</b> — fires when the product's category
     *       matches {@code "Bakery"} (case-insensitive).</li>
     *   <li><b>Bulk quantity (10 %)</b> — fires when
     *       {@code product.getQuantity() > 100}.</li>
     *   <li><b>No match (0 %)</b> — the existing discount is cleared to 0
     *       so the engine always produces a deterministic result.</li>
     * </ol>
     *
     * @param product the product to evaluate and update; must not be {@code null}
     * @return the effective selling price after applying any matched discount,
     *         i.e. {@link Product#getDiscountedPrice()} after the update
     * @throws IllegalArgumentException if {@code product} is {@code null}
     */
    public static double applyDiscount(Product product) {
        if (product == null)
            throw new IllegalArgumentException("Product must not be null.");

        // ── Rule 1: Near-expiry → 30 % off (highest priority) ─────────────
        //    daysUntilExpiry returns Long.MAX_VALUE for non-perishables, so
        //    Long.MAX_VALUE >= 0 but not <= 3 — non-perishables are safely excluded.
        long daysLeft = ExpiryChecker.daysUntilExpiry(product);
        if (daysLeft >= 0 && daysLeft <= NEAR_EXPIRY_DAYS) {
            product.setDiscountPercent(NEAR_EXPIRY_DISCOUNT);

        // ── Rule 2: Bakery category → 20 % off ────────────────────────────
        } else if (BAKERY_CATEGORY.equalsIgnoreCase(product.getCategory())) {
            product.setDiscountPercent(BAKERY_DISCOUNT);

        // ── Rule 3: Bulk quantity > 100 → 10 % off ────────────────────────
        } else if (product.getQuantity() > BULK_QUANTITY_MIN) {
            product.setDiscountPercent(BULK_DISCOUNT);

        // ── No rule matched → clear any previously applied discount ────────
        } else {
            product.setDiscountPercent(NO_DISCOUNT);
        }

        // Product now holds the correct discountPercent; delegate price calc
        return product.getDiscountedPrice();
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  DESCRIBE (diagnostic / display helper)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns a human-readable description of which rule <em>would</em> fire
     * for the given product, without mutating the product's state.
     *
     * <p>Useful for report headers and logging.</p>
     *
     * <pre>
     *   "Near-Expiry 30%"
     *   "Bakery Category 20%"
     *   "Bulk Quantity 10%"
     *   "No discount applicable"
     * </pre>
     *
     * @param product the product to inspect; must not be {@code null}
     * @return a short description of the applicable rule
     * @throws IllegalArgumentException if {@code product} is {@code null}
     */
    public static String describeRule(Product product) {
        if (product == null)
            throw new IllegalArgumentException("Product must not be null.");

        long daysLeft = ExpiryChecker.daysUntilExpiry(product);
        if (daysLeft >= 0 && daysLeft <= NEAR_EXPIRY_DAYS)
            return "Near-Expiry " + (int) NEAR_EXPIRY_DISCOUNT + "% (expires in " + daysLeft + " day(s))";

        if (BAKERY_CATEGORY.equalsIgnoreCase(product.getCategory()))
            return "Bakery Category " + (int) BAKERY_DISCOUNT + "%";

        if (product.getQuantity() > BULK_QUANTITY_MIN)
            return "Bulk Quantity " + (int) BULK_DISCOUNT + "% (qty=" + product.getQuantity() + ")";

        return "No discount applicable";
    }
}
