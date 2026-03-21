package com.inventory.logic;

import com.inventory.model.Product;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * <h2>ExpiryChecker — Logic Layer (Phase 4)</h2>
 *
 * <p>A stateless utility class that centralises all expiry-date calculations
 * for {@link Product} objects.  Every method is {@code static} — no
 * instantiation is needed or permitted.</p>
 *
 * <p>All date arithmetic uses {@link ChronoUnit#DAYS} via
 * {@link ChronoUnit#between(java.time.temporal.Temporal, java.time.temporal.Temporal)}
 * for precision and DST-safety.</p>
 *
 * <h3>Method summary</h3>
 * <ul>
 *   <li>{@link #isExpired(Product)}                      – has the product already expired?</li>
 *   <li>{@link #daysUntilExpiry(Product)}                – signed day count to expiry</li>
 *   <li>{@link #getExpiredList(ArrayList)}               – all products past their expiry date</li>
 *   <li>{@link #getNearExpiry(ArrayList, int)}           – products expiring within N days</li>
 * </ul>
 *
 * <h3>Non-perishable products</h3>
 * <p>Products whose {@code expiryDate} is {@code null} are treated as
 * <em>non-perishable</em>: they never appear as expired or near-expiry, and
 * {@link #daysUntilExpiry(Product)} returns {@link Long#MAX_VALUE} for them.</p>
 */
public final class ExpiryChecker {

    /** Prevent instantiation — all members are static. */
    private ExpiryChecker() {}


    // ══════════════════════════════════════════════════════════════════════════
    //  isExpired
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Determines whether the given product has already passed its expiry date.
     *
     * <p>A product is considered expired when its {@code expiryDate} is set
     * <em>and</em> is strictly before today's date (i.e. today itself is
     * <strong>not</strong> expired — it is the last safe day).</p>
     *
     * @param product the product to evaluate; must not be {@code null}
     * @return {@code true}  if {@code expiryDate != null} and
     *                       {@code expiryDate < today},
     *         {@code false} if the product is non-perishable ({@code expiryDate == null})
     *                       or has not yet expired
     * @throws IllegalArgumentException if {@code product} is {@code null}
     */
    public static boolean isExpired(Product product) {
        requireNonNull(product, "product");
        if (product.getExpiryDate() == null) return false;          // non-perishable
        return product.getExpiryDate().isBefore(LocalDate.now());
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  daysUntilExpiry
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Calculates the <em>signed</em> number of days between today and the
     * product's expiry date using {@link ChronoUnit#DAYS}.
     *
     * <table border="1" cellpadding="4">
     *   <caption>Return value semantics</caption>
     *   <tr><th>Condition</th>                 <th>Return value</th></tr>
     *   <tr><td>Non-perishable (null date)</td><td>{@link Long#MAX_VALUE}</td></tr>
     *   <tr><td>Expires today</td>             <td>{@code 0}</td></tr>
     *   <tr><td>Expires in future</td>         <td>positive number of days</td></tr>
     *   <tr><td>Already expired</td>           <td>negative number of days</td></tr>
     * </table>
     *
     * @param product the product to evaluate; must not be {@code null}
     * @return signed day count from today to {@code expiryDate},
     *         or {@link Long#MAX_VALUE} if the product has no expiry date
     * @throws IllegalArgumentException if {@code product} is {@code null}
     */
    public static long daysUntilExpiry(Product product) {
        requireNonNull(product, "product");
        if (product.getExpiryDate() == null) return Long.MAX_VALUE;  // non-perishable sentinel

        LocalDate today  = LocalDate.now();
        LocalDate expiry = product.getExpiryDate();
        // ChronoUnit.DAYS.between(start, end) → positive when end is in the future
        return ChronoUnit.DAYS.between(today, expiry);
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  getExpiredList
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Filters the supplied list and returns every product that has already
     * expired, sorted by expiry date ascending (oldest first).
     *
     * <p>The original list is <strong>not</strong> modified; the returned list
     * is a fresh, independent {@code ArrayList}.</p>
     *
     * @param products the full product list to filter; must not be {@code null}
     * @return a new {@code ArrayList} of expired products, oldest first;
     *         never {@code null}, may be empty
     * @throws IllegalArgumentException if {@code products} is {@code null}
     */
    public static ArrayList<Product> getExpiredList(ArrayList<Product> products) {
        requireNonNull(products, "products");

        return products.stream()
                .filter(ExpiryChecker::isExpired)
                .sorted((a, b) -> a.getExpiryDate().compareTo(b.getExpiryDate()))
                .collect(Collectors.toCollection(ArrayList::new));
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  getNearExpiry
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns products that will expire within the next {@code days} days
     * (inclusive), <em>excluding</em> products that have already expired.
     *
     * <p>In other words, a product is included when:</p>
     * <pre>
     *   0 ≤ daysUntilExpiry(product) ≤ days
     * </pre>
     *
     * <p>Results are sorted by expiry date ascending (soonest first) so the
     * most urgent items appear at the top.
     * The original list is <strong>not</strong> modified.</p>
     *
     * @param products the full product list to filter; must not be {@code null}
     * @param days     look-ahead window in days (must be ≥ 0)
     * @return a new {@code ArrayList} of near-expiry products, soonest first;
     *         never {@code null}, may be empty
     * @throws IllegalArgumentException if {@code products} is {@code null}
     *                                  or {@code days} is negative
     */
    public static ArrayList<Product> getNearExpiry(ArrayList<Product> products, int days) {
        requireNonNull(products, "products");
        if (days < 0)
            throw new IllegalArgumentException(
                "Look-ahead window must be ≥ 0 days (got " + days + ").");

        return products.stream()
                .filter(p -> {
                    long d = daysUntilExpiry(p);        // MAX_VALUE for non-perishables
                    return d >= 0 && d <= days;          // [0 … days] inclusive
                })
                .sorted((a, b) -> a.getExpiryDate().compareTo(b.getExpiryDate()))
                .collect(Collectors.toCollection(ArrayList::new));
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Throws {@link IllegalArgumentException} if {@code obj} is {@code null}.
     *
     * @param obj       object to check
     * @param paramName name used in the error message
     */
    private static void requireNonNull(Object obj, String paramName) {
        if (obj == null)
            throw new IllegalArgumentException("Parameter '" + paramName + "' must not be null.");
    }
}
