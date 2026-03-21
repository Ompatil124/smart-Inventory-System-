package com.inventory.controller;

import com.inventory.io.FileHandler;
import com.inventory.logic.InventoryLogic;
import com.inventory.model.Product;
import com.inventory.util.AppUtils;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * Bridges the console menu (Main) with business logic and persistence.
 * All Scanner / user-interaction lives here.
 */
public class InventoryController {

    private static final String DEFAULT_FILE = "inventory.csv";
    private static final int    EXPIRY_WARN_DAYS = 30;

    private final List<Product>  inventory = new ArrayList<>();
    private final InventoryLogic logic     = new InventoryLogic();
    private final FileHandler    fileHandler = new FileHandler();
    private final Scanner        scanner;

    public InventoryController(Scanner scanner) {
        this.scanner = scanner;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ADD
    // ══════════════════════════════════════════════════════════════════════════

    public void addProduct() {
        AppUtils.printHeader("Add New Product");
        try {
            System.out.print("  Name        : ");
            String name = scanner.nextLine().trim();
            if (name.isEmpty()) { AppUtils.printError("Name cannot be blank."); return; }

            System.out.print("  Category    : ");
            String category = scanner.nextLine().trim();

            System.out.print("  Quantity    : ");
            int qty = AppUtils.parseInt(scanner.nextLine());
            if (qty < 0) { AppUtils.printError("Quantity cannot be negative."); return; }

            System.out.print("  Price ($)   : ");
            double price = AppUtils.parseDouble(scanner.nextLine());
            if (price < 0) { AppUtils.printError("Price cannot be negative."); return; }

            System.out.print("  Expiry date (yyyy-MM-dd, or press Enter to skip): ");
            String expRaw = scanner.nextLine().trim();
            LocalDate expiry = expRaw.isEmpty() ? null : AppUtils.parseDate(expRaw);

            System.out.print("  Discount %  (0 for none): ");
            double discount = AppUtils.parseDouble(scanner.nextLine());
            if (discount < 0 || discount > 100) {
                AppUtils.printError("Discount must be between 0 and 100."); return;
            }

            int id = logic.nextId(inventory);
            // Phase 2 constructor order: (id, name, category, price, quantity, expiryDate, discountPercent)
            Product p = new Product(id, name, category, price, qty, expiry, discount);
            inventory.add(p);
            AppUtils.printSuccess("Product added with ID " + id + ".");

        } catch (NumberFormatException e) {
            AppUtils.printError("Invalid number: " + e.getMessage());
        } catch (DateTimeParseException e) {
            AppUtils.printError("Invalid date format (use yyyy-MM-dd).");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  REMOVE
    // ══════════════════════════════════════════════════════════════════════════

    public void removeProduct() {
        AppUtils.printHeader("Remove Product");
        try {
            System.out.print("  Enter product ID to remove: ");
            int id = AppUtils.parseInt(scanner.nextLine());

            Optional<Product> found = logic.findById(inventory, id);
            if (found.isEmpty()) {
                AppUtils.printError("No product with ID " + id + ".");
                return;
            }
            System.out.println("  Found: " + found.get());
            System.out.print("  Confirm removal? (y/n): ");
            String confirm = scanner.nextLine().trim();
            if (confirm.equalsIgnoreCase("y")) {
                inventory.remove(found.get());
                AppUtils.printSuccess("Product ID " + id + " removed.");
            } else {
                System.out.println("  Removal cancelled.");
            }

        } catch (NumberFormatException e) {
            AppUtils.printError("Invalid ID entered.");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SEARCH
    // ══════════════════════════════════════════════════════════════════════════

    public void searchProduct() {
        AppUtils.printHeader("Search Products");
        System.out.print("  Enter name to search: ");
        String query = scanner.nextLine().trim();
        List<Product> results = logic.searchByName(inventory, query);
        if (results.isEmpty()) {
            System.out.println("  No products matched \"" + query + "\".");
        } else {
            System.out.println("  Found " + results.size() + " result(s):");
            results.forEach(p -> System.out.println("    " + p));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  VIEW ALL
    // ══════════════════════════════════════════════════════════════════════════

    public void viewAll() {
        AppUtils.printHeader("All Products (" + inventory.size() + ")");
        if (inventory.isEmpty()) {
            System.out.println("  Inventory is empty.");
            return;
        }
        inventory.forEach(p -> System.out.println("  " + p));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SORT
    // ══════════════════════════════════════════════════════════════════════════

    public void sortProducts() {
        AppUtils.printHeader("Sort Products");
        System.out.println("  Sort by:");
        System.out.println("    1. Name");
        System.out.println("    2. Price");
        System.out.println("    3. Quantity");
        System.out.println("    4. Expiry Date");
        System.out.print("  Choice: ");
        try {
            int choice = AppUtils.parseInt(scanner.nextLine());
            InventoryLogic.SortField field = switch (choice) {
                case 1 -> InventoryLogic.SortField.NAME;
                case 2 -> InventoryLogic.SortField.PRICE;
                case 3 -> InventoryLogic.SortField.QUANTITY;
                case 4 -> InventoryLogic.SortField.EXPIRY;
                default -> { AppUtils.printError("Invalid sort option."); yield null; }
            };
            if (field == null) return;

            System.out.print("  Order: (1) Ascending  (2) Descending: ");
            int order = AppUtils.parseInt(scanner.nextLine());
            boolean asc = (order != 2);

            List<Product> sorted = logic.sort(inventory, field, asc);
            AppUtils.printHeader("Sorted Results");
            sorted.forEach(p -> System.out.println("  " + p));

        } catch (NumberFormatException e) {
            AppUtils.printError("Invalid input — please enter a number.");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  EXPIRY REPORT
    // ══════════════════════════════════════════════════════════════════════════

    public void expiryReport() {
        AppUtils.printHeader("Expiry Report (within " + EXPIRY_WARN_DAYS + " days or already expired)");
        List<Product> report = logic.expiryReport(inventory, EXPIRY_WARN_DAYS);
        if (report.isEmpty()) {
            System.out.println("  No products are expiring soon or already expired.");
            return;
        }
        for (Product p : report) {
            String status = p.isExpired() ? "⚠ EXPIRED" : "Expiring soon";
            System.out.printf("  [%s] %s  (%s)%n", status, p, p.getExpiryDate());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  DISCOUNT REPORT
    // ══════════════════════════════════════════════════════════════════════════

    public void discountReport() {
        AppUtils.printHeader("Discount Report");
        List<Product> report = logic.discountReport(inventory);
        if (report.isEmpty()) {
            System.out.println("  No products currently have a discount.");
            return;
        }
        for (Product p : report) {
            System.out.printf("  %s  →  Discounted Price: $%.2f%n",
                    p, p.getDiscountedPrice());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SAVE
    // ══════════════════════════════════════════════════════════════════════════

    public void saveInventory() {
        AppUtils.printHeader("Save Inventory");
        System.out.print("  File path (Enter for '" + DEFAULT_FILE + "'): ");
        String path = scanner.nextLine().trim();
        if (path.isEmpty()) path = DEFAULT_FILE;

        try {
            fileHandler.save(inventory, path);
            AppUtils.printSuccess("Inventory saved to: " + path);
        } catch (IOException e) {
            AppUtils.printError("Save failed: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  LOAD
    // ══════════════════════════════════════════════════════════════════════════

    public void loadInventory() {
        AppUtils.printHeader("Load Inventory");
        System.out.print("  File path (Enter for '" + DEFAULT_FILE + "'): ");
        String path = scanner.nextLine().trim();
        if (path.isEmpty()) path = DEFAULT_FILE;

        try {
            List<Product> loaded = fileHandler.load(path);
            System.out.printf("  Loaded %d product(s). Replace current inventory? (y/n): ",
                    loaded.size());
            String confirm = scanner.nextLine().trim();
            if (confirm.equalsIgnoreCase("y")) {
                inventory.clear();
                inventory.addAll(loaded);
                AppUtils.printSuccess("Inventory updated with " + loaded.size() + " product(s).");
            } else {
                System.out.println("  Load cancelled.");
            }
        } catch (IOException e) {
            AppUtils.printError("Load failed: " + e.getMessage());
        }
    }
}
