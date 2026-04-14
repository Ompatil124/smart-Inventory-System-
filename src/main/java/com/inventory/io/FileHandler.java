package com.inventory.io;

import com.inventory.model.Product;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves and loads the inventory to/from a plain CSV file.
 *
 * <h3>Full format (used by {@link #save} / {@link #load})</h3>
 * <pre>id,name,category,quantity,price,expiryDate,discountPercent,barcodes</pre>
 * Barcodes column is pipe-separated (e.g. 8901234567890|TMCNOIR150ML).
 * Old 7-column files without a barcodes column load correctly (barcodes left empty).
 * Preserves all Product fields including ID, discount, and barcodes.
 *
 * <h3>Phase 6 format (used by {@link #saveToFile} / {@link #loadFromFile})</h3>
 * <pre>name,category,price,quantity,expiryDate</pre>
 * Core 5-field format; IDs are auto-reassigned on load.
 *
 * <p>In both formats {@code expiryDate} is stored as {@code yyyy-MM-dd},
 * or the literal string {@code "null"} for non-perishable products.</p>
 */
public class FileHandler {

    private static final String DELIMITER = ",";
    private static final String NULL_VALUE = "null";

    // ── Save ─────────────────────────────────────────────────────────────────

    /**
     * Writes the entire inventory list to {@code filePath}.
     * Overwrites any existing file.
     */
    public void save(List<Product> inventory, String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            // header row
            writer.write("id,name,category,quantity,price,expiryDate,discountPercent,barcodes");
            writer.newLine();

            for (Product p : inventory) {
                String expiry = (p.getExpiryDate() != null)
                        ? p.getExpiryDate().toString()
                        : NULL_VALUE;

                String barcodes = p.getBarcodes().isEmpty()
                        ? ""
                        : String.join("|", p.getBarcodes());

                String line = String.join(DELIMITER,
                        String.valueOf(p.getId()),
                        escapeCsv(p.getName()),
                        escapeCsv(p.getCategory()),
                        String.valueOf(p.getQuantity()),
                        String.valueOf(p.getPrice()),
                        expiry,
                        String.valueOf(p.getDiscountPercent()),
                        escapeCsv(barcodes)
                );
                writer.write(line);
                writer.newLine();
            }
        }
    }

    // ── Load ─────────────────────────────────────────────────────────────────

    /**
     * Reads products from {@code filePath}.
     * Lines that cannot be parsed are skipped (with a warning to stderr).
     *
     * @throws IOException if the file cannot be opened at all.
     */
    public List<Product> load(String filePath) throws IOException {
        List<Product> list = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line = reader.readLine(); // skip header
            int lineNum = 1;

            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    Product p = parseLine(line);
                    list.add(p);
                } catch (Exception e) {
                    System.err.printf("[FileHandler] Skipping malformed line %d: %s%n",
                            lineNum, e.getMessage());
                }
            }
        }
        return list;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PHASE 6 — saveToFile / loadFromFile  (5-field CSV, explicit try-catch)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Writes {@code products} to {@code filename} using {@link BufferedWriter}.
     *
     * <p>CSV format written (one product per line, header included):</p>
     * <pre>name,category,price,quantity,expiryDate</pre>
     *
     * <p>The {@code expiryDate} column stores the date as {@code yyyy-MM-dd}
     * or the literal {@code "null"} for non-perishable products.</p>
     *
     * <p>Any {@link IOException} (including {@link FileNotFoundException})
     * is caught internally; a descriptive message is printed to
     * {@code System.err} and the method returns without rethrowing,
     * keeping the call-site free of checked-exception boilerplate.</p>
     *
     * @param products the list of products to persist; must not be {@code null}
     * @param filename the target file path (created or overwritten)
     */
    public void saveToFile(ArrayList<Product> products, String filename) {
        if (products == null || filename == null)
            throw new IllegalArgumentException("products and filename must not be null.");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {

            // ── Header row ────────────────────────────────────────────────────
            writer.write("name,category,price,quantity,expiryDate");
            writer.newLine();

            // ── Data rows ─────────────────────────────────────────────────────
            for (Product p : products) {
                String expiry = (p.getExpiryDate() != null)
                        ? p.getExpiryDate().toString()
                        : NULL_VALUE;

                // 5-field format: name,category,price,quantity,expiryDate
                String line = String.join(DELIMITER,
                        escapeCsv(p.getName()),
                        escapeCsv(p.getCategory()),
                        String.valueOf(p.getPrice()),
                        String.valueOf(p.getQuantity()),
                        expiry
                );
                writer.write(line);
                writer.newLine();
            }

            System.out.printf("[FileHandler] Saved %d product(s) to \"%s\".%n",
                    products.size(), filename);

        } catch (FileNotFoundException e) {
            System.err.printf("[FileHandler] saveToFile — file not found / cannot create: %s%n",
                    e.getMessage());
        } catch (IOException e) {
            System.err.printf("[FileHandler] saveToFile — I/O error: %s%n",
                    e.getMessage());
        }
    }

    /**
     * Reads products from {@code filename} using {@link BufferedReader}.
     *
     * <p>Expects the 5-field CSV format written by {@link #saveToFile}:</p>
     * <pre>name,category,price,quantity,expiryDate</pre>
     *
     * <p>The first line is treated as a header and skipped automatically.
     * Blank lines and malformed rows are skipped with a warning to
     * {@code System.err}; the remaining valid rows are returned.</p>
     *
     * <p>A {@link FileNotFoundException} is caught internally and an empty
     * list is returned (the file simply does not exist yet — a normal
     * first-run condition).</p>
     *
     * <p>IDs are auto-assigned sequentially starting from 1 because the
     * 5-field format does not persist the ID field.</p>
     *
     * @param filename the source file path to read
     * @return a new {@link ArrayList} of products; never {@code null},
     *         may be empty if the file is absent or contains no valid rows
     */
    public ArrayList<Product> loadFromFile(String filename) {
        ArrayList<Product> list = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {

            reader.readLine();   // skip header line
            String line;
            int lineNum  = 1;
            int nextId   = 1;   // auto-assign IDs sequentially (no ID column)

            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    Product p = parseShortLine(line, nextId++);
                    list.add(p);
                } catch (Exception e) {
                    System.err.printf(
                        "[FileHandler] loadFromFile — skipping malformed line %d: %s%n",
                        lineNum, e.getMessage());
                    nextId--;   // don't advance ID for a skipped row
                }
            }

            System.out.printf("[FileHandler] Loaded %d product(s) from \"%s\".%n",
                    list.size(), filename);

        } catch (FileNotFoundException e) {
            System.err.printf(
                "[FileHandler] loadFromFile — file not found: \"%s\". Returning empty list.%n",
                filename);
        } catch (IOException e) {
            System.err.printf("[FileHandler] loadFromFile — I/O error: %s%n",
                    e.getMessage());
        }

        return list;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Product parseLine(String line)
            throws NumberFormatException, DateTimeParseException, ArrayIndexOutOfBoundsException {

        String[] parts = line.split(DELIMITER, -1);
        // CSV column order: id,name,category,quantity,price,expiryDate,discountPercent[,barcodes]
        int    id       = Integer.parseInt(parts[0].trim());
        String name     = unescapeCsv(parts[1].trim());
        String category = unescapeCsv(parts[2].trim());
        int    qty      = Integer.parseInt(parts[3].trim());
        double price    = Double.parseDouble(parts[4].trim());

        LocalDate expiry = NULL_VALUE.equalsIgnoreCase(parts[5].trim())
                ? null
                : LocalDate.parse(parts[5].trim());

        double discount = Double.parseDouble(parts[6].trim());

        Product p = new Product(id, name, category, price, qty, expiry, discount);

        // Barcodes column (column 7) — optional for backward compatibility
        if (parts.length > 7) {
            String barcodeCol = unescapeCsv(parts[7].trim());
            if (!barcodeCol.isEmpty()) {
                for (String bc : barcodeCol.split("\\|")) {
                    p.addBarcode(bc.trim());
                }
            }
        }
        return p;
    }

    /**
     * Parses one line of the <em>5-field</em> CSV format.
     * Column order: name,category,price,quantity,expiryDate
     *
     * @param line  the raw CSV line (no header)
     * @param newId the ID to assign to the created product
     */
    private Product parseShortLine(String line, int newId)
            throws NumberFormatException, DateTimeParseException,
                   ArrayIndexOutOfBoundsException {

        String[] parts = line.split(DELIMITER, -1);
        // 5-field order: name, category, price, quantity, expiryDate
        String name     = unescapeCsv(parts[0].trim());
        String category = unescapeCsv(parts[1].trim());
        double price    = Double.parseDouble(parts[2].trim());
        int    qty      = Integer.parseInt(parts[3].trim());

        LocalDate expiry = NULL_VALUE.equalsIgnoreCase(parts[4].trim())
                ? null
                : LocalDate.parse(parts[4].trim());

        // Use the full 7-arg constructor; discount defaults to 0 for fresh loads
        return new Product(newId, name, category, price, qty, expiry, 0.0);
    }

    /** Wraps values that contain commas in double-quotes. */
    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(DELIMITER) || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /** Strips surrounding double-quotes and un-escapes internal ones. */
    private String unescapeCsv(String value) {
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
            value = value.replace("\"\"", "\"");
        }
        return value;
    }
}
