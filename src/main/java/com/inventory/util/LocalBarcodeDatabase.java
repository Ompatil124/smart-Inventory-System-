package com.inventory.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Loads {@code data/indian_products.csv} and provides fast offline barcode lookup.
 *
 * <p>CSV format: {@code barcode,name,category,price,defaultQty}</p>
 */
public class LocalBarcodeDatabase {

    /** key = barcode string, value = {name, category, price, defaultQty} */
    private final Map<String, String[]> database = new HashMap<>();

    public LocalBarcodeDatabase() {
        loadFromCSV();
    }

    // ── CSV loading ───────────────────────────────────────────────────────────

    private void loadFromCSV() {
        // Try loading from file system first (data/indian_products.csv relative to working dir)
        boolean loaded = tryLoadFromFile("data/indian_products.csv");

        // Fallback: load from classpath resource
        if (!loaded) {
            loaded = tryLoadFromClasspath("/data/indian_products.csv");
        }
        if (!loaded) {
            loaded = tryLoadFromClasspath("data/indian_products.csv");
        }

        System.out.println("Local DB loaded: " + database.size() + " products");
    }

    private boolean tryLoadFromFile(String path) {
        try (BufferedReader br = Files.newBufferedReader(Paths.get(path))) {
            return readLines(br);
        } catch (IOException e) {
            return false;
        }
    }

    private boolean tryLoadFromClasspath(String resource) {
        try (InputStream is = LocalBarcodeDatabase.class.getResourceAsStream(resource)) {
            if (is == null) return false;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                return readLines(br);
            }
        } catch (IOException e) {
            return false;
        }
    }

    private boolean readLines(BufferedReader br) throws IOException {
        String line = br.readLine(); // skip header
        if (line == null) return false;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split(",", 5);
            if (parts.length < 5) continue;
            String barcode = parts[0].trim();
            String name    = parts[1].trim();
            String cat     = parts[2].trim();
            String price   = parts[3].trim();
            String qty     = parts[4].trim();
            database.put(barcode, new String[]{name, cat, price, qty});
        }
        return !database.isEmpty();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns product data for the given barcode, or {@code null} if not found.
     *
     * @param barcode EAN / UPC string
     * @return {@code String[]{name, category, price, defaultQty}} or {@code null}
     */
    public String[] lookup(String barcode) {
        if (barcode == null) return null;
        return database.get(barcode.trim());
    }

    /** Returns all barcode keys in the database. */
    public Set<String> getAllBarcodes() {
        return database.keySet();
    }
}
