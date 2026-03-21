package com.inventory.io;

import com.inventory.logic.DiscountEngine;
import com.inventory.logic.ExpiryChecker;
import com.inventory.model.Product;
import com.inventory.util.AppUtils;

import java.util.ArrayList;

/**
 * <h2>ReportGenerator — Reports &amp; Output Layer (Phase 7)</h2>
 *
 * <p>Produces formatted, tabular console reports for the Smart Inventory
 * System.  All output uses {@link System#out} via {@code printf} for
 * consistent column alignment.  Business logic (expiry math, discount
 * rules) is fully delegated to {@link ExpiryChecker} and
 * {@link DiscountEngine} — this class contains <em>zero</em> calculation
 * logic of its own.</p>
 *
 * <p>Headers and separators reuse {@link AppUtils#printHeader(String)} and
 * {@link AppUtils#SEP} so every screen in the application looks identical.</p>
 *
 * <h3>Report methods</h3>
 * <ul>
 *   <li>{@link #printAllProducts(ArrayList)}           — full product table</li>
 *   <li>{@link #printLowStock(ArrayList, int)}         — items at or below threshold</li>
 *   <li>{@link #printExpiryReport(ArrayList)}          — expiry status with day countdown</li>
 *   <li>{@link #printDiscountReport(ArrayList)}        — applicable discounts &amp; savings</li>
 * </ul>
 *
 * <p>All methods are {@code static}; this class is not instantiable.</p>
 *
 * <h3>Sample output — printAllProducts</h3>
 * <pre>
 * ─────────────────────────────────────────────────────────────────────────
 *   ALL PRODUCTS  (3 items)
 * ─────────────────────────────────────────────────────────────────────────
 *  ID  │ Name                      │ Category     │  Qty │    Price │ Expiry
 * ─────┼───────────────────────────┼──────────────┼──────┼──────────┼────────────
 *    1 │ Whole Milk                │ Dairy        │  100 │    $2.99 │ 2026-04-01
 *    2 │ Sourdough Loaf            │ Bakery       │   20 │    $3.50 │ N/A
 * ─────────────────────────────────────────────────────────────────────────
 * </pre>
 */
public final class ReportGenerator {

    // ── Column format strings (kept in one place for easy tuning) ─────────────

    /** Header row for the master product table. */
    private static final String ALL_HEADER =
        "  %-3s  │ %-25s │ %-12s │ %5s │ %8s │ %-12s%n";

    /** Data row for the master product table. */
    private static final String ALL_ROW =
        "  %3d  │ %-25s │ %-12s │ %5d │ %8s │ %-12s%n";

    /** Row divider matching the master table column widths. */
    private static final String ALL_DIVIDER =
        "  ─────┼───────────────────────────┼──────────────┼───────┼──────────┼─────────────";

    /** Header row for the low-stock table. */
    private static final String STOCK_HEADER =
        "  %-3s  │ %-25s │ %-12s │ %5s%n";

    /** Data row for the low-stock table. */
    private static final String STOCK_ROW =
        "  %3d  │ %-25s │ %-12s │ %5d%n";

    /** Row divider for the low-stock table. */
    private static final String STOCK_DIVIDER =
        "  ─────┼───────────────────────────┼──────────────┼───────";

    /** Header row for the expiry report table. */
    private static final String EXPIRY_HEADER =
        "  %-3s  │ %-25s │ %-12s │ %-12s │ %6s │ %-15s%n";

    /** Data row for the expiry report table. */
    private static final String EXPIRY_ROW =
        "  %3d  │ %-25s │ %-12s │ %-12s │ %6s │ %-15s%n";

    /** Row divider for the expiry report table. */
    private static final String EXPIRY_DIVIDER =
        "  ─────┼───────────────────────────┼──────────────┼──────────────┼────────┼────────────────";

    /** Header row for the discount report table. */
    private static final String DISC_HEADER =
        "  %-3s  │ %-25s │ %8s │ %7s │ %10s │ %8s │ %-30s%n";

    /** Data row for the discount report table. */
    private static final String DISC_ROW =
        "  %3d  │ %-25s │ %8s │ %6.0f%% │ %10s │ %8s │ %-30s%n";

    /** Row divider for the discount report table. */
    private static final String DISC_DIVIDER =
        "  ─────┼───────────────────────────┼──────────────┼─────────┼────────────┼──────────┼───────────────────────────────";

    /** Sentinel displayed when a product has no expiry date. */
    private static final String NA = "N/A";

    /** Prevent instantiation — all members are static. */
    private ReportGenerator() {}


    // ══════════════════════════════════════════════════════════════════════════
    //  printAllProducts
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Prints a formatted table of every product in the inventory.
     *
     * <p>Columns: ID │ Name │ Category │ Qty │ Price │ Expiry</p>
     *
     * @param products the list to display; must not be {@code null}
     * @throws IllegalArgumentException if {@code products} is {@code null}
     */
    public static void printAllProducts(ArrayList<Product> products) {
        requireNonNull(products, "products");
        AppUtils.printHeader("All Products  (" + products.size() + " item(s))");

        if (products.isEmpty()) {
            System.out.println("  No products in inventory.");
            System.out.println(AppUtils.SEP);
            return;
        }

        // Table header
        System.out.printf(ALL_HEADER, "ID", "Name", "Category", "Qty", "Price", "Expiry");
        System.out.println(ALL_DIVIDER);

        // Data rows
        for (Product p : products) {
            String expiry = formatExpiry(p);
            String price  = String.format("$%7.2f", p.getPrice());
            System.out.printf(ALL_ROW,
                    p.getId(),
                    truncate(p.getName(), 25),
                    truncate(p.getCategory(), 12),
                    p.getQuantity(),
                    price,
                    expiry);
        }

        System.out.println(AppUtils.SEP);
        System.out.printf("  Total items: %d%n%n", products.size());
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  printLowStock
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Prints products whose stock quantity is at or below {@code threshold}.
     *
     * <p>Columns: ID │ Name │ Category │ Qty</p>
     *
     * <p>Each row is flagged with a {@code ⚠} warning marker when
     * quantity is zero (out-of-stock).</p>
     *
     * @param products  the list to filter; must not be {@code null}
     * @param threshold maximum quantity to include (inclusive); must be ≥ 0
     * @throws IllegalArgumentException if {@code products} is {@code null}
     *                                  or {@code threshold} is negative
     */
    public static void printLowStock(ArrayList<Product> products, int threshold) {
        requireNonNull(products, "products");
        if (threshold < 0)
            throw new IllegalArgumentException(
                "Threshold must be ≥ 0 (got " + threshold + ").");

        AppUtils.printHeader("Low-Stock Report  (threshold ≤ " + threshold + ")");

        // Filter
        ArrayList<Product> lowStock = new ArrayList<>();
        for (Product p : products) {
            if (p.getQuantity() <= threshold) lowStock.add(p);
        }

        if (lowStock.isEmpty()) {
            System.out.println("  ✔ All products are above the stock threshold.");
            System.out.println(AppUtils.SEP);
            return;
        }

        // Table header
        System.out.printf(STOCK_HEADER, "ID", "Name", "Category", "Qty");
        System.out.println(STOCK_DIVIDER);

        int outOfStock = 0;
        for (Product p : lowStock) {
            String marker = (p.getQuantity() == 0) ? " ⚠ OUT" : "";
            // Use STOCK_ROW constant for aligned columns then append the out-of-stock marker
            System.out.printf(STOCK_ROW,
                    p.getId(),
                    truncate(p.getName(), 25),
                    truncate(p.getCategory(), 12),
                    p.getQuantity());
            if (!marker.isEmpty()) System.out.print(marker + "\n");
            if (p.getQuantity() == 0) outOfStock++;
        }

        System.out.println(AppUtils.SEP);
        System.out.printf("  %d item(s) below threshold  |  %d out-of-stock%n%n",
                lowStock.size(), outOfStock);
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  printExpiryReport
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Prints an expiry status report for all products that have an expiry date.
     *
     * <p>Columns: ID │ Name │ Category │ Expiry Date │ Days Left │ Status</p>
     *
     * <p>Status values:</p>
     * <ul>
     *   <li>{@code ✔ OK}          — more than 30 days remaining</li>
     *   <li>{@code ⚡ EXPIRES SOON} — 1–30 days remaining (inclusive)</li>
     *   <li>{@code ⚠ EXPIRES TODAY} — expires today (0 days)</li>
     *   <li>{@code ✘ EXPIRED}     — already past expiry</li>
     * </ul>
     *
     * <p>Non-perishable products (null expiry date) are listed in a
     * separate summary section at the bottom.</p>
     *
     * @param products the list to report on; must not be {@code null}
     * @throws IllegalArgumentException if {@code products} is {@code null}
     */
    public static void printExpiryReport(ArrayList<Product> products) {
        requireNonNull(products, "products");
        AppUtils.printHeader("Expiry Status Report");

        // Partition into perishable / non-perishable
        ArrayList<Product> perishable    = new ArrayList<>();
        ArrayList<Product> nonPerishable = new ArrayList<>();
        for (Product p : products) {
            if (p.getExpiryDate() != null) perishable.add(p);
            else                           nonPerishable.add(p);
        }

        if (perishable.isEmpty() && nonPerishable.isEmpty()) {
            System.out.println("  No products in inventory.");
            System.out.println(AppUtils.SEP);
            return;
        }

        // ── Perishable section ────────────────────────────────────────────────
        if (!perishable.isEmpty()) {
            System.out.printf(EXPIRY_HEADER,
                    "ID", "Name", "Category", "Expiry Date", "Days", "Status");
            System.out.println(EXPIRY_DIVIDER);

            int expired = 0, expiringSoon = 0;

            for (Product p : perishable) {
                long daysLeft = ExpiryChecker.daysUntilExpiry(p);
                String daysStr, status;

                if (daysLeft < 0) {
                    daysStr = String.valueOf(daysLeft);   // negative = days overdue
                    status  = "✘ EXPIRED";
                    expired++;
                } else if (daysLeft == 0) {
                    daysStr = "0";
                    status  = "⚠ EXPIRES TODAY";
                    expiringSoon++;
                } else if (daysLeft <= 30) {
                    daysStr = String.valueOf(daysLeft);
                    status  = "⚡ EXPIRES SOON";
                    expiringSoon++;
                } else {
                    daysStr = String.valueOf(daysLeft);
                    status  = "✔ OK";
                }

                System.out.printf(EXPIRY_ROW,
                        p.getId(),
                        truncate(p.getName(), 25),
                        truncate(p.getCategory(), 12),
                        p.getExpiryDate().toString(),
                        daysStr,
                        status);
            }

            System.out.println(AppUtils.SEP);
            System.out.printf(
                "  Perishable: %d  |  ✘ Expired: %d  |  ⚡ Expiring soon: %d%n",
                perishable.size(), expired, expiringSoon);
        }

        // ── Non-perishable summary ─────────────────────────────────────────────
        if (!nonPerishable.isEmpty()) {
            System.out.printf("%n  Non-perishable products (%d): ", nonPerishable.size());
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < nonPerishable.size(); i++) {
                names.append(nonPerishable.get(i).getName());
                if (i < nonPerishable.size() - 1) names.append(", ");
            }
            System.out.println(names);
        }

        System.out.println();
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  printDiscountReport
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Evaluates and prints the applicable discount for every product.
     *
     * <p>Columns: ID │ Name │ Orig. Price │ Discount │ Final Price │ Saving │ Rule Applied</p>
     *
     * <p>Each product is evaluated by
     * {@link DiscountEngine#applyDiscount(Product)} — this updates the
     * product's stored {@code discountPercent} as a side-effect and returns
     * the final price.  Products with no applicable rule show {@code 0%} and
     * no saving.</p>
     *
     * <p>A summary line at the bottom counts how many products received a
     * discount this cycle.</p>
     *
     * @param products the list to report on; must not be {@code null}
     * @throws IllegalArgumentException if {@code products} is {@code null}
     */
    public static void printDiscountReport(ArrayList<Product> products) {
        requireNonNull(products, "products");
        AppUtils.printHeader("Discount Report");

        if (products.isEmpty()) {
            System.out.println("  No products in inventory.");
            System.out.println(AppUtils.SEP);
            return;
        }

        // Table header
        System.out.printf(DISC_HEADER,
                "ID", "Name", "Orig.Price", "Disc.%", "Final Price", "Saving", "Rule Applied");
        System.out.println(DISC_DIVIDER);

        int discountedCount = 0;
        double totalSaving  = 0.0;

        for (Product p : products) {
            String rule         = DiscountEngine.describeRule(p);          // non-mutating peek
            double finalPrice   = DiscountEngine.applyDiscount(p);        // mutates discountPercent
            double discPct      = p.getDiscountPercent();
            double saving       = p.getPrice() - finalPrice;

            String origPriceStr  = String.format("$%7.2f", p.getPrice());
            String finalPriceStr = String.format("$%7.2f", finalPrice);
            String savingStr     = (saving > 0.0)
                    ? String.format("-$%.2f", saving)
                    : "—";

            System.out.printf(DISC_ROW,
                    p.getId(),
                    truncate(p.getName(), 25),
                    origPriceStr,
                    discPct,
                    finalPriceStr,
                    savingStr,
                    truncate(rule, 30));

            if (discPct > 0.0) {
                discountedCount++;
                totalSaving += saving;
            }
        }

        System.out.println(AppUtils.SEP);
        System.out.printf(
            "  %d of %d product(s) discounted  |  Total potential saving: $%.2f%n%n",
            discountedCount, products.size(), totalSaving);
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the expiry date as a string, or {@link #NA} for non-perishables.
     */
    private static String formatExpiry(Product p) {
        return (p.getExpiryDate() != null) ? p.getExpiryDate().toString() : NA;
    }

    /**
     * Truncates {@code text} to at most {@code maxLen} characters,
     * appending {@code "…"} when truncation occurs.
     *
     * @param text   the string to truncate
     * @param maxLen maximum allowed length (must be ≥ 1)
     * @return the original string if short enough, otherwise a truncated version
     */
    private static String truncate(String text, int maxLen) {
        if (text == null)        return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 1) + "…";
    }

    /**
     * Throws {@link IllegalArgumentException} if {@code obj} is {@code null}.
     */
    private static void requireNonNull(Object obj, String paramName) {
        if (obj == null)
            throw new IllegalArgumentException(
                "Parameter '" + paramName + "' must not be null.");
    }
}
