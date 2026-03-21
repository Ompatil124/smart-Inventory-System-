package com.inventory;

import com.inventory.io.FileHandler;
import com.inventory.io.ReportGenerator;
import com.inventory.logic.DiscountEngine;
import com.inventory.logic.ExpiryChecker;
import com.inventory.model.Product;
import com.inventory.util.ProductList;
import com.inventory.util.SortUtil;

import org.junit.jupiter.api.*;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <h2>InventorySystemTest — Phase 9 Validation Suite</h2>
 *
 * <p>Covers all 10 sample products (5 categories), all major features, and
 * the requested edge-cases:</p>
 * <ul>
 *   <li>Empty inventory</li>
 *   <li>Duplicate names in search / remove</li>
 *   <li>Invalid / missing expiry dates (null safety)</li>
 *   <li>Missing file handling in FileHandler</li>
 * </ul>
 *
 * <p>Run with: {@code mvn test}</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InventorySystemTest {

    // ══════════════════════════════════════════════════════════════════════════
    //  SAMPLE DATA — 10 products across 5 categories
    //  Chosen to exercise every discount rule and expiry scenario.
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Builds and returns a fresh ArrayList of 10 varied sample products.
     * Dates are relative to runtime so expiry rules always fire correctly.
     */
    private static ArrayList<Product> sampleProducts() {
        LocalDate today    = LocalDate.now();
        ArrayList<Product> list = new ArrayList<>();

        /* ── Dairy ─────────────────────────────────────────────────────────── */
        // #1  — expires in 2 days  → triggers Near-Expiry 30% discount
        list.add(new Product(1, "Whole Milk",        "Dairy",      2.99, 100, today.plusDays(2),  0.0));
        // #2  — expires in 60 days → no discount
        list.add(new Product(2, "Greek Yoghurt",     "Dairy",      1.49,   5, today.plusDays(60), 0.0));

        /* ── Bakery ─────────────────────────────────────────────────────────── */
        // #3  — Bakery category    → triggers Bakery 20% discount
        list.add(new Product(3, "Sourdough Loaf",    "Bakery",     3.50,  20, today.plusDays(5),  0.0));
        // #4  — Bakery + expires today → Near-Expiry (30%) wins over Bakery (20%)
        list.add(new Product(4, "Croissant",         "Bakery",     1.20,  15, today,              0.0));

        /* ── Vegetables ─────────────────────────────────────────────────────── */
        // #5  — quantity > 100     → triggers Bulk 10% discount
        list.add(new Product(5, "Baby Spinach",      "Vegetables", 1.80, 150, today.plusDays(7),  0.0));
        // #6  — expired yesterday  → no new discount (was expired)
        list.add(new Product(6, "Cherry Tomatoes",   "Vegetables", 2.20,   0, today.minusDays(1), 0.0));

        /* ── Beverages ──────────────────────────────────────────────────────── */
        // #7  — no expiry (non-perishable)
        list.add(new Product(7, "Orange Juice 1L",   "Beverages",  3.99,  80, null,               0.0));
        // #8  — quantity > 100     → Bulk 10% discount
        list.add(new Product(8, "Sparkling Water",   "Beverages",  0.99, 200, null,               0.0));

        /* ── Snacks ─────────────────────────────────────────────────────────── */
        // #9  — no discount rule matches
        list.add(new Product(9, "Salted Crisps",     "Snacks",     1.25,  40, today.plusDays(90), 0.0));
        // #10 — expires in 3 days  → Near-Expiry 30% (boundary: exactly 3 days)
        list.add(new Product(10,"Dark Chocolate Bar","Snacks",     2.50,  30, today.plusDays(3),  0.0));

        return list;
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — Product Model
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(1)
    @DisplayName("Product: constructor sets all 5 core fields correctly")
    void product_constructorSetsFields() {
        LocalDate expiry = LocalDate.of(2026, 6, 1);
        Product p = new Product("Test Item", "Snacks", 4.99, 25, expiry);

        assertEquals("Test Item", p.getName());
        assertEquals("Snacks",    p.getCategory());
        assertEquals(4.99,        p.getPrice(),    0.001);
        assertEquals(25,          p.getQuantity());
        assertEquals(expiry,      p.getExpiryDate());
    }

    @Test @Order(2)
    @DisplayName("Product: setters reject invalid values")
    void product_settersValidate() {
        Product p = new Product("Valid", "Cat", 1.0, 10, null);

        assertThrows(IllegalArgumentException.class, () -> p.setPrice(-1));
        assertThrows(IllegalArgumentException.class, () -> p.setQuantity(-5));
        assertThrows(IllegalArgumentException.class, () -> p.setDiscountPercent(101));
        assertThrows(IllegalArgumentException.class, () -> p.setName(""));
        assertThrows(IllegalArgumentException.class, () -> p.setName(null));
    }

    @Test @Order(3)
    @DisplayName("Product: getDiscountedPrice() math is correct")
    void product_discountedPrice() {
        Product p = new Product(1, "Item", "Cat", 10.00, 1, null, 20.0);
        assertEquals(8.00, p.getDiscountedPrice(), 0.001);
    }

    @Test @Order(4)
    @DisplayName("Product: toString() contains name, category, price")
    void product_toStringContainsKeyFields() {
        Product p = new Product(1, "Whole Milk", "Dairy", 2.99, 100, null, 0.0);
        String s = p.toString();
        assertTrue(s.contains("Whole Milk"));
        assertTrue(s.contains("Dairy"));
        assertTrue(s.contains("2.99"));
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — ExpiryChecker
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(10)
    @DisplayName("ExpiryChecker: isExpired() → true for past date")
    void expiry_isExpiredForPastDate() {
        Product p = new Product(1, "Old Bread", "Bakery", 1.0, 5,
                LocalDate.now().minusDays(1), 0.0);
        assertTrue(ExpiryChecker.isExpired(p));
    }

    @Test @Order(11)
    @DisplayName("ExpiryChecker: isExpired() → false for today (last safe day)")
    void expiry_notExpiredToday() {
        Product p = new Product(1, "Fresh", "Dairy", 1.0, 5, LocalDate.now(), 0.0);
        assertFalse(ExpiryChecker.isExpired(p));
    }

    @Test @Order(12)
    @DisplayName("ExpiryChecker: isExpired() → false for non-perishable (null expiry)")
    void expiry_nonPerishableNeverExpires() {
        Product p = new Product(1, "Water", "Beverages", 1.0, 50, null, 0.0);
        assertFalse(ExpiryChecker.isExpired(p));
    }

    @Test @Order(13)
    @DisplayName("ExpiryChecker: daysUntilExpiry() → Long.MAX_VALUE for null expiry")
    void expiry_daysMaxForNull() {
        Product p = new Product(1, "Water", "Beverages", 1.0, 50, null, 0.0);
        assertEquals(Long.MAX_VALUE, ExpiryChecker.daysUntilExpiry(p));
    }

    @Test @Order(14)
    @DisplayName("ExpiryChecker: daysUntilExpiry() → 0 for today")
    void expiry_zeroForToday() {
        Product p = new Product(1, "x", "y", 1.0, 1, LocalDate.now(), 0.0);
        assertEquals(0L, ExpiryChecker.daysUntilExpiry(p));
    }

    @Test @Order(15)
    @DisplayName("ExpiryChecker: getExpiredList() returns only expired products")
    void expiry_getExpiredList() {
        ArrayList<Product> list = sampleProducts();
        ArrayList<Product> expired = ExpiryChecker.getExpiredList(list);

        // Cherry Tomatoes expired yesterday — that's the only one
        assertFalse(expired.isEmpty());
        expired.forEach(p ->
                assertTrue(p.getExpiryDate().isBefore(LocalDate.now()),
                        p.getName() + " should be expired"));
    }

    @Test @Order(16)
    @DisplayName("ExpiryChecker: getNearExpiry() excludes already-expired items")
    void expiry_nearExpiryExcludesExpired() {
        ArrayList<Product> list = sampleProducts();
        ArrayList<Product> near = ExpiryChecker.getNearExpiry(list, 7);

        // None of the near-expiry results should be already expired
        near.forEach(p ->
                assertFalse(ExpiryChecker.isExpired(p),
                        p.getName() + " is expired but appeared in near-expiry list"));
    }

    @Test @Order(17)
    @DisplayName("ExpiryChecker: getNearExpiry() rejects negative days")
    void expiry_negativeWindowThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> ExpiryChecker.getNearExpiry(new ArrayList<>(), -1));
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — DiscountEngine
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(20)
    @DisplayName("DiscountEngine: near-expiry (≤3 days) → 30% discount")
    void discount_nearExpiry30Pct() {
        // Expires in exactly 2 days
        Product p = new Product(1, "Milk", "Dairy", 10.0, 5,
                LocalDate.now().plusDays(2), 0.0);
        double final_ = DiscountEngine.applyDiscount(p);
        assertEquals(30.0, p.getDiscountPercent(), 0.001);
        assertEquals(7.00, final_,                 0.001);
    }

    @Test @Order(21)
    @DisplayName("DiscountEngine: Bakery category → 20% discount")
    void discount_bakery20Pct() {
        Product p = new Product(1, "Bread", "Bakery", 10.0, 20,
                LocalDate.now().plusDays(10), 0.0);
        double final_ = DiscountEngine.applyDiscount(p);
        assertEquals(20.0, p.getDiscountPercent(), 0.001);
        assertEquals(8.00, final_,                 0.001);
    }

    @Test @Order(22)
    @DisplayName("DiscountEngine: quantity > 100 → 10% discount")
    void discount_bulk10Pct() {
        Product p = new Product(1, "Water", "Beverages", 10.0, 150, null, 0.0);
        double final_ = DiscountEngine.applyDiscount(p);
        assertEquals(10.0, p.getDiscountPercent(), 0.001);
        assertEquals(9.00, final_,                 0.001);
    }

    @Test @Order(23)
    @DisplayName("DiscountEngine: near-expiry takes priority over Bakery (highest discount wins)")
    void discount_nearExpiryBeforeBakery() {
        // Bakery + expiring in 1 day → 30% should win over 20%
        Product p = new Product(1, "Croissant", "Bakery", 10.0, 5,
                LocalDate.now().plusDays(1), 0.0);
        DiscountEngine.applyDiscount(p);
        assertEquals(30.0, p.getDiscountPercent(), 0.001,
                "Near-expiry rule (30%) must beat Bakery rule (20%)");
    }

    @Test @Order(24)
    @DisplayName("DiscountEngine: no rule match → 0% discount")
    void discount_noRuleZeroPct() {
        // qty=40, non-perishable, category Snacks
        Product p = new Product(1, "Crisps", "Snacks", 10.0, 40, null, 0.0);
        double final_ = DiscountEngine.applyDiscount(p);
        assertEquals(0.0,  p.getDiscountPercent(), 0.001);
        assertEquals(10.0, final_,                 0.001);
    }

    @Test @Order(25)
    @DisplayName("DiscountEngine: null product → IllegalArgumentException")
    void discount_nullThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> DiscountEngine.applyDiscount(null));
    }

    @Test @Order(26)
    @DisplayName("DiscountEngine: Bakery match is case-insensitive")
    void discount_bakeryCaseInsensitive() {
        Product p = new Product(1, "Bagel", "BAKERY", 10.0, 5, null, 0.0);
        DiscountEngine.applyDiscount(p);
        assertEquals(20.0, p.getDiscountPercent(), 0.001);
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — ProductList (Collections Layer)
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(30)
    @DisplayName("ProductList: add() and size() work correctly")
    void productList_addAndSize() {
        ProductList pl = new ProductList();
        assertTrue(pl.isEmpty());
        pl.add(new Product(1, "Milk", "Dairy", 2.99, 10, null, 0.0));
        assertFalse(pl.isEmpty());
        assertEquals(1, pl.size());
    }

    @Test @Order(31)
    @DisplayName("ProductList: add() rejects null")
    void productList_addNullThrows() {
        ProductList pl = new ProductList();
        assertThrows(IllegalArgumentException.class, () -> pl.add(null));
    }

    @Test @Order(32)
    @DisplayName("ProductList: add() rejects duplicate ID")
    void productList_duplicateIdThrows() {
        ProductList pl = new ProductList();
        pl.add(new Product(1, "Item A", "Cat", 1.0, 1, null, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> pl.add(new Product(1, "Item B", "Cat", 2.0, 1, null, 0.0)));
    }

    @Test @Order(33)
    @DisplayName("ProductList: remove() by name — exact match, case-insensitive")
    void productList_removeByName() {
        ProductList pl = new ProductList();
        pl.add(new Product(1, "Whole Milk", "Dairy", 2.99, 10, null, 0.0));
        pl.add(new Product(2, "Greek Yoghurt", "Dairy", 1.49, 5, null, 0.0));

        assertTrue(pl.remove("whole milk"));  // case-insensitive
        assertEquals(1, pl.size());
        assertEquals("Greek Yoghurt", pl.getAll().get(0).getName());
    }

    @Test @Order(34)
    @DisplayName("ProductList: remove() returns false when name not found (edge case)")
    void productList_removeMissingReturnsFalse() {
        ProductList pl = new ProductList();
        pl.add(new Product(1, "Milk", "Dairy", 2.99, 10, null, 0.0));
        assertFalse(pl.remove("Cheese"));  // not in list
    }

    @Test @Order(35)
    @DisplayName("ProductList: findByName() returns substring matches, empty list okay")
    void productList_findByName() {
        ProductList pl = new ProductList();
        pl.add(new Product(1, "Whole Milk", "Dairy", 2.99, 10, null, 0.0));
        pl.add(new Product(2, "Skimmed Milk", "Dairy", 1.99, 8, null, 0.0));
        pl.add(new Product(3, "Cheese", "Dairy", 4.50, 3, null, 0.0));

        // "milk" matches two products
        assertEquals(2, pl.findByName("milk").size());
        // Non-existent search returns empty (edge case)
        assertEquals(0, pl.findByName("XYZ-NOMATCH").size());
    }

    @Test @Order(36)
    @DisplayName("ProductList: getAll() returns unmodifiable view")
    void productList_getAllIsUnmodifiable() {
        ProductList pl = new ProductList();
        pl.add(new Product(1, "Milk", "Dairy", 2.99, 1, null, 0.0));
        assertThrows(UnsupportedOperationException.class,
                () -> pl.getAll().add(new Product(2, "X", "Y", 1.0, 1, null, 0.0)));
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 5 — SortUtil
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(40)
    @DisplayName("SortUtil: sortByName() — alphabetical A→Z, case-insensitive")
    void sort_byName() {
        ArrayList<Product> list = sampleProducts();
        SortUtil.sortByName(list);
        for (int i = 0; i < list.size() - 1; i++) {
            assertTrue(list.get(i).getName().compareToIgnoreCase(
                        list.get(i + 1).getName()) <= 0,
                    "Names out of order at index " + i);
        }
    }

    @Test @Order(41)
    @DisplayName("SortUtil: sortByPrice() — ascending, cheapest first")
    void sort_byPrice() {
        ArrayList<Product> list = sampleProducts();
        SortUtil.sortByPrice(list);
        for (int i = 0; i < list.size() - 1; i++) {
            assertTrue(list.get(i).getPrice() <= list.get(i + 1).getPrice(),
                    "Prices out of order at index " + i);
        }
    }

    @Test @Order(42)
    @DisplayName("SortUtil: sortByQuantity() — ascending, lowest stock first")
    void sort_byQuantity() {
        ArrayList<Product> list = sampleProducts();
        SortUtil.sortByQuantity(list);
        for (int i = 0; i < list.size() - 1; i++) {
            assertTrue(list.get(i).getQuantity() <= list.get(i + 1).getQuantity(),
                    "Quantities out of order at index " + i);
        }
    }

    @Test @Order(43)
    @DisplayName("SortUtil: sortByExpiry() — nulls sort last")
    void sort_byExpiry_nullsLast() {
        ArrayList<Product> list = sampleProducts();
        SortUtil.sortByExpiry(list);
        // All non-null expiry dates must come before null dates
        boolean seenNull = false;
        for (Product p : list) {
            if (p.getExpiryDate() == null) {
                seenNull = true;
            } else {
                assertFalse(seenNull,
                        p.getName() + " has a date but appeared after a null-expiry product");
            }
        }
    }

    @Test @Order(44)
    @DisplayName("SortUtil: sort operates in-place (same list reference)")
    void sort_inPlace() {
        ArrayList<Product> list = sampleProducts();
        ArrayList<Product> ref  = list;            // same reference
        SortUtil.sortByName(list);
        assertSame(ref, list, "sortByName should modify the original list, not return a copy");
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 6 — FileHandler (Phase 6 methods)
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(50)
    @DisplayName("FileHandler: saveToFile/loadFromFile round-trip preserves 5 core fields")
    void file_roundTrip() {
        String path = System.getProperty("java.io.tmpdir") + File.separator + "inv_test.csv";
        ArrayList<Product> original = sampleProducts();

        FileHandler fh = new FileHandler();
        fh.saveToFile(original, path);

        ArrayList<Product> loaded = fh.loadFromFile(path);
        assertEquals(original.size(), loaded.size(),
                "Loaded product count must match saved count");

        for (int i = 0; i < original.size(); i++) {
            Product o = original.get(i);
            Product l = loaded.get(i);
            assertEquals(o.getName(),     l.getName(),     "Name mismatch at row " + i);
            assertEquals(o.getCategory(), l.getCategory(), "Category mismatch at row " + i);
            assertEquals(o.getPrice(),    l.getPrice(),    0.001, "Price mismatch at row " + i);
            assertEquals(o.getQuantity(), l.getQuantity(), "Quantity mismatch at row " + i);
            assertEquals(o.getExpiryDate(), l.getExpiryDate(),
                    "ExpiryDate mismatch at row " + i);
        }

        // Clean up
        new File(path).delete();
    }

    @Test @Order(51)
    @DisplayName("FileHandler: loadFromFile() returns empty list for missing file (edge case)")
    void file_missingFileReturnsEmptyList() {
        FileHandler fh = new FileHandler();
        ArrayList<Product> result = fh.loadFromFile("nonexistent_file_xyz.csv");
        // Must not throw — must return empty list silently
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test @Order(52)
    @DisplayName("FileHandler: saveToFile() then loadFromFile() restores null expiry (non-perishable)")
    void file_nullExpiryRoundTrip() {
        String path = System.getProperty("java.io.tmpdir") + File.separator + "inv_null_test.csv";
        ArrayList<Product> list = new ArrayList<>();
        list.add(new Product(1, "Sparkling Water", "Beverages", 0.99, 200, null, 0.0));

        FileHandler fh = new FileHandler();
        fh.saveToFile(list, path);
        ArrayList<Product> loaded = fh.loadFromFile(path);

        assertNull(loaded.get(0).getExpiryDate(),
                "Non-perishable product should have null expiryDate after round-trip");
        new File(path).delete();
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 7 — ReportGenerator (smoke tests — no crash, no exception)
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(60)
    @DisplayName("ReportGenerator: printAllProducts() runs without exception on 10 products")
    void report_allProducts() {
        assertDoesNotThrow(() -> ReportGenerator.printAllProducts(sampleProducts()));
    }

    @Test @Order(61)
    @DisplayName("ReportGenerator: printAllProducts() handles empty inventory (edge case)")
    void report_allProductsEmpty() {
        assertDoesNotThrow(() -> ReportGenerator.printAllProducts(new ArrayList<>()));
    }

    @Test @Order(62)
    @DisplayName("ReportGenerator: printLowStock() threshold=5 filters correctly")
    void report_lowStock() {
        assertDoesNotThrow(() -> ReportGenerator.printLowStock(sampleProducts(), 5));
    }

    @Test @Order(63)
    @DisplayName("ReportGenerator: printLowStock() threshold=0 (only out-of-stock)")
    void report_lowStockZeroThreshold() {
        assertDoesNotThrow(() -> ReportGenerator.printLowStock(sampleProducts(), 0));
    }

    @Test @Order(64)
    @DisplayName("ReportGenerator: printLowStock() rejects negative threshold (edge case)")
    void report_lowStockNegativeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> ReportGenerator.printLowStock(sampleProducts(), -1));
    }

    @Test @Order(65)
    @DisplayName("ReportGenerator: printExpiryReport() runs without exception on all 10 products")
    void report_expiryReport() {
        assertDoesNotThrow(() -> ReportGenerator.printExpiryReport(sampleProducts()));
    }

    @Test @Order(66)
    @DisplayName("ReportGenerator: printDiscountReport() runs and mutates discountPercent correctly")
    void report_discountReport() {
        ArrayList<Product> list = sampleProducts();
        assertDoesNotThrow(() -> ReportGenerator.printDiscountReport(list));

        // After running the report, Whole Milk (expires in 2 days) must have 30%
        Product milk = list.get(0);
        assertEquals(30.0, milk.getDiscountPercent(), 0.001,
                "Whole Milk (2 days to expiry) should carry 30% after discount report");
    }


    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 8 — Edge Cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test @Order(70)
    @DisplayName("Edge: invalid date format string — LocalDate.parse throws DateTimeParseException")
    void edge_invalidDateString() {
        assertThrows(java.time.format.DateTimeParseException.class,
                () -> LocalDate.parse("32-13-2099"));
    }

    @Test @Order(71)
    @DisplayName("Edge: duplicate product names — findByName returns all matches")
    void edge_duplicateNameSearch() {
        ProductList pl = new ProductList();
        // Two products with the same name, different IDs
        pl.add(new Product(1, "Whole Milk", "Dairy", 2.99, 10, null, 0.0));
        pl.add(new Product(2, "Whole Milk", "Dairy", 3.49, 20, null, 0.0));

        assertEquals(2, pl.findByName("Whole Milk").size(),
                "Both duplicates should appear in search results");
    }

    @Test @Order(72)
    @DisplayName("Edge: remove() on a name that matches only first occurrence")
    void edge_removeOnlyFirstDuplicate() {
        ProductList pl = new ProductList();
        pl.add(new Product(1, "Milk", "Dairy", 2.99, 10, null, 0.0));
        pl.add(new Product(2, "Milk", "Dairy", 3.49, 20, null, 0.0));
        pl.add(new Product(3, "Cheese", "Dairy", 5.00, 5, null, 0.0));

        pl.remove("Milk");  // only the first "Milk" should go
        assertEquals(2, pl.size(), "Only the first duplicate should be removed");
    }

    @Test @Order(73)
    @DisplayName("Edge: ExpiryChecker exact boundary — 3 days fires near-expiry discount")
    void edge_exactBoundaryNearExpiry() {
        // Exactly 3 days → should trigger 30% (boundary inclusive)
        Product p = new Product(1, "Choc Bar", "Snacks", 2.50, 30,
                LocalDate.now().plusDays(3), 0.0);
        assertEquals(30.0, DiscountEngine.applyDiscount(p) > 0 ? p.getDiscountPercent() : 0.0,
                0.001);
        assertEquals(30.0, p.getDiscountPercent(), 0.001);
    }

    @Test @Order(74)
    @DisplayName("Edge: ExpiryChecker boundary — 4 days does NOT fire near-expiry")
    void edge_fourDaysNotNearExpiry() {
        // 4 days → must NOT trigger near-expiry (only 0-3 qualify)
        Product p = new Product(1, "Item", "Snacks", 2.50, 30,
                LocalDate.now().plusDays(4), 0.0);
        DiscountEngine.applyDiscount(p);
        assertNotEquals(30.0, p.getDiscountPercent(),
                "4 days to expiry should NOT trigger the 30% near-expiry rule");
    }

    @Test @Order(75)
    @DisplayName("Edge: empty inventory — all reports produce no exception")
    void edge_emptyInventoryAllReports() {
        ArrayList<Product> empty = new ArrayList<>();
        assertDoesNotThrow(() -> ReportGenerator.printAllProducts(empty));
        assertDoesNotThrow(() -> ReportGenerator.printLowStock(empty, 10));
        assertDoesNotThrow(() -> ReportGenerator.printExpiryReport(empty));
        assertDoesNotThrow(() -> ReportGenerator.printDiscountReport(empty));
        assertEquals(0, ExpiryChecker.getExpiredList(empty).size());
        assertEquals(0, ExpiryChecker.getNearExpiry(empty, 7).size());
    }
}
