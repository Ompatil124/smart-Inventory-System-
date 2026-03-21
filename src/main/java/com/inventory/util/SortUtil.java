package com.inventory.util;

import com.inventory.model.Product;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * <h2>SortUtil — Sorting &amp; Search Layer (Phase 5)</h2>
 *
 * <p>Provides static in-place sorting methods for {@code ArrayList<Product>}
 * using {@link Collections#sort(java.util.List, Comparator)} paired with
 * explicit {@link Comparator} definitions.</p>
 *
 * <p>Each method sorts the supplied list <em>in place</em> (the original
 * {@code ArrayList} is modified) and also returns the same list for
 * convenient method chaining.</p>
 *
 * <h3>Sort methods</h3>
 * <ul>
 *   <li>{@link #sortByExpiry(ArrayList)}   – ascending by expiry date (nulls last)</li>
 *   <li>{@link #sortByPrice(ArrayList)}    – ascending by unit price</li>
 *   <li>{@link #sortByQuantity(ArrayList)} – ascending by stock quantity</li>
 *   <li>{@link #sortByName(ArrayList)}     – ascending alphabetically, case-insensitive</li>
 * </ul>
 *
 * <p>All methods are {@code static}; this class is not instantiable.</p>
 *
 * <h3>Null-safety</h3>
 * <p>A {@code null} list argument causes an {@link IllegalArgumentException}.
 * {@code null} elements inside the list are not expected (Product objects are
 * always non-null post-Phase-2) and will surface as a {@link NullPointerException}
 * from the comparator if present.</p>
 *
 * <h3>Relationship to {@code InventoryLogic.sort()}</h3>
 * <p>{@code InventoryLogic.sort()} returns a <em>new</em> sorted list leaving
 * the original unchanged.  {@code SortUtil} mutates the list in place — both
 * approaches are valid and serve different call sites.</p>
 */
public final class SortUtil {

    /** Prevent instantiation — all members are static. */
    private SortUtil() {}


    // ══════════════════════════════════════════════════════════════════════════
    //  sortByExpiry
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Sorts {@code products} <em>in place</em> by expiry date, ascending
     * (earliest expiry first).
     *
     * <p><b>Null expiry dates</b> (non-perishable products) are placed
     * <em>last</em> so perishable items always surface at the top of the list —
     * the most operationally useful ordering for an inventory manager.</p>
     *
     * <p>Products with equal expiry dates maintain their relative order
     * (the sort is stable).</p>
     *
     * @param products the list to sort in place; must not be {@code null}
     * @return the same {@code products} reference, sorted, for chaining
     * @throws IllegalArgumentException if {@code products} is {@code null}
     */
    public static ArrayList<Product> sortByExpiry(ArrayList<Product> products) {
        requireNonNull(products, "products");

        // Comparator: null expiry → treat as LocalDate.MAX so nulls sort last
        Comparator<Product> byExpiry = Comparator.comparing(
                p -> (p.getExpiryDate() != null ? p.getExpiryDate() : LocalDate.MAX)
        );

        Collections.sort(products, byExpiry);
        return products;
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  sortByPrice
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Sorts {@code products} <em>in place</em> by unit price, ascending
     * (cheapest first).
     *
     * <p>Products with equal prices maintain their relative order
     * (the sort is stable).</p>
     *
     * @param products the list to sort in place; must not be {@code null}
     * @return the same {@code products} reference, sorted, for chaining
     * @throws IllegalArgumentException if {@code products} is {@code null}
     */
    public static ArrayList<Product> sortByPrice(ArrayList<Product> products) {
        requireNonNull(products, "products");

        Comparator<Product> byPrice = Comparator.comparingDouble(Product::getPrice);

        Collections.sort(products, byPrice);
        return products;
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  sortByQuantity
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Sorts {@code products} <em>in place</em> by stock quantity, ascending
     * (lowest stock first).
     *
     * <p>Sorting lowest-first is the most actionable order for an inventory
     * manager — near-zero stock items appear at the top for reorder decisions.
     * Products with equal quantities maintain their relative order.</p>
     *
     * @param products the list to sort in place; must not be {@code null}
     * @return the same {@code products} reference, sorted, for chaining
     * @throws IllegalArgumentException if {@code products} is {@code null}
     */
    public static ArrayList<Product> sortByQuantity(ArrayList<Product> products) {
        requireNonNull(products, "products");

        Comparator<Product> byQuantity = Comparator.comparingInt(Product::getQuantity);

        Collections.sort(products, byQuantity);
        return products;
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  sortByName
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Sorts {@code products} <em>in place</em> alphabetically by product name,
     * ascending (A → Z), <em>case-insensitive</em>.
     *
     * <p>Uses {@link String#CASE_INSENSITIVE_ORDER} so that "apple",
     * "Apple", and "APPLE" sort identically.  Products with equal names
     * (after normalisation) maintain their relative order.</p>
     *
     * @param products the list to sort in place; must not be {@code null}
     * @return the same {@code products} reference, sorted, for chaining
     * @throws IllegalArgumentException if {@code products} is {@code null}
     */
    public static ArrayList<Product> sortByName(ArrayList<Product> products) {
        requireNonNull(products, "products");

        Comparator<Product> byName = Comparator.comparing(
                Product::getName, String.CASE_INSENSITIVE_ORDER
        );

        Collections.sort(products, byName);
        return products;
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  REVERSED VARIANTS (convenience overloads)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Sorts {@code products} in place by expiry date, <em>descending</em>
     * (latest / non-perishables first). Nulls sort first when reversed.
     *
     * @param products the list to sort in place; must not be {@code null}
     * @return the same {@code products} reference, sorted, for chaining
     */
    public static ArrayList<Product> sortByExpiryDesc(ArrayList<Product> products) {
        requireNonNull(products, "products");
        Comparator<Product> byExpiry = Comparator.comparing(
                (Product p) -> (p.getExpiryDate() != null ? p.getExpiryDate() : LocalDate.MAX)
        ).reversed();
        Collections.sort(products, byExpiry);
        return products;
    }

    /**
     * Sorts {@code products} in place by unit price, <em>descending</em>
     * (most expensive first).
     *
     * @param products the list to sort in place; must not be {@code null}
     * @return the same {@code products} reference, sorted, for chaining
     */
    public static ArrayList<Product> sortByPriceDesc(ArrayList<Product> products) {
        requireNonNull(products, "products");
        Collections.sort(products, Comparator.comparingDouble(Product::getPrice).reversed());
        return products;
    }

    /**
     * Sorts {@code products} in place by stock quantity, <em>descending</em>
     * (highest stock first).
     *
     * @param products the list to sort in place; must not be {@code null}
     * @return the same {@code products} reference, sorted, for chaining
     */
    public static ArrayList<Product> sortByQuantityDesc(ArrayList<Product> products) {
        requireNonNull(products, "products");
        Collections.sort(products, Comparator.comparingInt(Product::getQuantity).reversed());
        return products;
    }

    /**
     * Sorts {@code products} in place alphabetically by name, <em>descending</em>
     * (Z → A), case-insensitive.
     *
     * @param products the list to sort in place; must not be {@code null}
     * @return the same {@code products} reference, sorted, for chaining
     */
    public static ArrayList<Product> sortByNameDesc(ArrayList<Product> products) {
        requireNonNull(products, "products");
        Comparator<Product> byNameDesc = Comparator
                .comparing(Product::getName, String.CASE_INSENSITIVE_ORDER)
                .reversed();
        Collections.sort(products, byNameDesc);
        return products;
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Throws {@link IllegalArgumentException} if {@code obj} is {@code null}.
     *
     * @param obj       the object to check
     * @param paramName parameter name used in the error message
     */
    private static void requireNonNull(Object obj, String paramName) {
        if (obj == null)
            throw new IllegalArgumentException(
                "Parameter '" + paramName + "' must not be null.");
    }
}
