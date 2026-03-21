package com.inventory.util;

import com.inventory.model.Product;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * <h2>ProductList — Collections Layer (Phase 3)</h2>
 *
 * <p>A fully encapsulated, {@code ArrayList}-backed store for {@link Product}
 * objects.  All mutation is routed through validated methods; the raw list is
 * never exposed directly (only an unmodifiable view is returned by
 * {@link #getAll()}).</p>
 *
 * <p>This class acts as the canonical backing store and can be used by the
 * controller layer in place of a bare {@code ArrayList<Product>}.</p>
 *
 * <h3>API overview</h3>
 * <ul>
 *   <li>{@link #add(Product)}            – append a product (no duplicates)</li>
 *   <li>{@link #remove(String)}          – remove first case-insensitive name match</li>
 *   <li>{@link #findByName(String)}      – return all case-insensitive name matches</li>
 *   <li>{@link #getAll()}                – unmodifiable view of the full list</li>
 *   <li>{@link #isEmpty()}               – true when no products are stored</li>
 * </ul>
 */
public class ProductList {

    // ── Backing store (private — full encapsulation) ──────────────────────────

    /** The single, canonical list — never exposed by reference. */
    private final ArrayList<Product> products = new ArrayList<>();


    // ══════════════════════════════════════════════════════════════════════════
    //  ADD
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Appends {@code product} to the list.
     *
     * <p>Validation rules:</p>
     * <ul>
     *   <li>{@code product} must not be {@code null}.</li>
     *   <li>No two products may share the same ID (if the product has a
     *       positive ID already assigned).</li>
     * </ul>
     *
     * @param product the product to add
     * @throws IllegalArgumentException if {@code product} is {@code null} or
     *                                  a product with the same ID already exists
     */
    public void add(Product product) {
        if (product == null)
            throw new IllegalArgumentException("Cannot add a null product.");

        // Duplicate-ID guard (only relevant when ID > 0, i.e. already assigned)
        if (product.getId() > 0 && containsId(product.getId()))
            throw new IllegalArgumentException(
                "A product with ID " + product.getId() + " already exists.");

        products.add(product);
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  REMOVE
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Removes the <em>first</em> product whose name matches {@code name}
     * (case-insensitive, leading/trailing whitespace ignored).
     *
     * @param name the name to match against
     * @return {@code true} if a product was found and removed,
     *         {@code false} if no match was found
     * @throws IllegalArgumentException if {@code name} is {@code null} or blank
     */
    public boolean remove(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Search name must not be blank.");

        String target = name.trim().toLowerCase();
        for (int i = 0; i < products.size(); i++) {
            if (products.get(i).getName().toLowerCase().equals(target)) {
                products.remove(i);
                return true;
            }
        }
        return false;   // no match
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  FIND BY NAME
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns a new list of every product whose name <em>contains</em>
     * {@code name} as a substring (case-insensitive).
     *
     * <p>The original backing list is never modified and the returned list
     * is a fresh, independent copy safe to iterate or sort.</p>
     *
     * @param name the substring to search for
     * @return a (possibly empty) list of matching products
     * @throws IllegalArgumentException if {@code name} is {@code null} or blank
     */
    public List<Product> findByName(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Search name must not be blank.");

        String query = name.trim().toLowerCase();
        List<Product> results = new ArrayList<>();
        for (Product p : products) {
            if (p.getName().toLowerCase().contains(query)) {
                results.add(p);
            }
        }
        return results;
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  GET ALL
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns an <em>unmodifiable</em> view of the entire product list.
     *
     * <p>The view reflects any later mutations (add / remove) automatically,
     * but callers cannot mutate it directly — attempts will throw
     * {@link UnsupportedOperationException}.</p>
     *
     * @return unmodifiable live view of all products
     */
    public List<Product> getAll() {
        return Collections.unmodifiableList(products);
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  IS EMPTY
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Reports whether the inventory contains no products.
     *
     * @return {@code true} if the list is empty
     */
    public boolean isEmpty() {
        return products.isEmpty();
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  CONVENIENCE / EXTRA HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the number of products currently stored.
     *
     * @return product count (≥ 0)
     */
    public int size() {
        return products.size();
    }

    /**
     * Finds a product by exact numeric ID.
     *
     * @param id the ID to look for
     * @return an {@link Optional} containing the match, or empty if not found
     */
    public Optional<Product> findById(int id) {
        return products.stream().filter(p -> p.getId() == id).findFirst();
    }

    /**
     * Replaces the entire backing list with the contents of {@code newList}.
     * Useful when loading from a file (mirrors the controller's
     * {@code inventory.clear(); inventory.addAll(loaded);} pattern).
     *
     * @param newList products to load; must not be {@code null}
     * @throws IllegalArgumentException if {@code newList} is {@code null}
     */
    public void replaceAll(List<Product> newList) {
        if (newList == null)
            throw new IllegalArgumentException("Replacement list must not be null.");
        products.clear();
        products.addAll(newList);
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /** @return true if any stored product already uses {@code id} */
    private boolean containsId(int id) {
        return products.stream().anyMatch(p -> p.getId() == id);
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  OBJECT OVERRIDES
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns a concise summary string, e.g. {@code "ProductList[3 item(s)]"}.
     */
    @Override
    public String toString() {
        return "ProductList[" + products.size() + " item(s)]";
    }
}
