package com.inventory.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Resolves a barcode number to full product details.
 *
 * <p>Strategy (in order):
 * <ol>
 *   <li>Check {@link LocalBarcodeDatabase} (instant, offline)</li>
 *   <li>Call the Open Food Facts REST API (online fallback)</li>
 *   <li>Return {@code null} if neither source has the product</li>
 * </ol>
 * </p>
 *
 * <p>No external JSON library required — response is parsed with plain
 * {@link String} methods.</p>
 */
public class ProductLookupService {

    // ── Inner result record ───────────────────────────────────────────────────

    public static class ProductDetails {
        public String name;
        public String category;
        public double price;
        public int    quantity;
        public String brand;
        public String imageUrl;
        /** true if data came from local CSV; false if from Open Food Facts API */
        public boolean fromLocalDB;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final LocalBarcodeDatabase localDB;

    // ── Constructor ───────────────────────────────────────────────────────────

    public ProductLookupService() {
        this.localDB = new LocalBarcodeDatabase();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Looks up a barcode and returns product details, or {@code null} if not found.
     *
     * @param barcode EAN-13 / UPC-A / CODE-128 barcode string
     * @return {@link ProductDetails} populated with available data, or {@code null}
     */
    public ProductDetails lookupByBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) return null;

        // ── STEP A: local database ────────────────────────────────────────────
        String[] local = localDB.lookup(barcode);
        if (local != null) {
            ProductDetails details = new ProductDetails();
            details.name       = local[0];
            details.category   = local[1];
            details.price      = parseDouble(local[2]);
            details.quantity   = parseInt(local[3], 10);
            details.brand      = "";
            details.imageUrl   = "";
            details.fromLocalDB = true;
            return details;
        }

        // ── STEP B: Open Food Facts API ───────────────────────────────────────
        return fetchFromOpenFoodFacts(barcode);
    }

    // ── Open Food Facts ───────────────────────────────────────────────────────

    private ProductDetails fetchFromOpenFoodFacts(String barcode) {
        try {
            String urlStr = "https://world.openfoodfacts.org/api/v0/product/" + barcode + ".json";
            URL apiUrl = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "GroceryInventoryApp/1.0");

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) return null;

            // Read full response
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            String json = sb.toString();

            // ── STEP C: Parse JSON manually ───────────────────────────────────
            // Check status field — 1 means product found
            String statusStr = extractJsonValue(json, "status");
            if (!"1".equals(statusStr)) return null;

            // Extract name (try product_name_en first, then product_name)
            String name = extractJsonValue(json, "product_name_en");
            if (name.isEmpty()) name = extractJsonValue(json, "product_name");
            if (name.isEmpty()) name = "Unknown Product";

            String brand      = extractJsonValue(json, "brands");
            String categories = extractJsonValue(json, "categories");

            // Map API categories to our known set
            String category = mapCategory(categories.toLowerCase());

            // Price not available via Open Food Facts (Indian prices not in their DB)
            double price    = 0.0;
            int    quantity = 10;

            String imageUrl = extractJsonValue(json, "image_url");

            // ── STEP D: Build result ──────────────────────────────────────────
            ProductDetails details = new ProductDetails();
            details.name        = name;
            details.category    = category;
            details.price       = price;
            details.quantity    = quantity;
            details.brand       = brand;
            details.imageUrl    = imageUrl;
            details.fromLocalDB = false;
            return details;

        } catch (Exception e) {
            System.err.println("Open Food Facts lookup failed: " + e.getMessage());
            return null;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Extracts the string value for {@code key} from a flat JSON string.
     * Works for simple string values — does not handle nested arrays.
     */
    private String extractJsonValue(String json, String key) {
        int keyIndex = json.indexOf("\"" + key + "\"");
        if (keyIndex == -1) return "";
        int colonIndex  = json.indexOf(":", keyIndex);
        if (colonIndex == -1) return "";
        int quoteStart  = json.indexOf("\"", colonIndex);
        if (quoteStart == -1) return "";
        int quoteEnd    = json.indexOf("\"", quoteStart + 1);
        if (quoteEnd == -1) return "";
        return json.substring(quoteStart + 1, quoteEnd).trim();
    }

    /** Maps an Open Food Facts category string to our internal category names. */
    private String mapCategory(String raw) {
        if (raw.contains("chip") || raw.contains("snack") || raw.contains("crisp")
                || raw.contains("biscuit") || raw.contains("cookie") || raw.contains("noodle")) {
            return "Snacks";
        }
        if (raw.contains("dairy") || raw.contains("milk") || raw.contains("butter")
                || raw.contains("cheese") || raw.contains("yogurt") || raw.contains("cream")) {
            return "Dairy";
        }
        if (raw.contains("bread") || raw.contains("bak") || raw.contains("cake")
                || raw.contains("pastry")) {
            return "Bakery";
        }
        if (raw.contains("beverage") || raw.contains("drink") || raw.contains("juice")
                || raw.contains("tea") || raw.contains("coffee") || raw.contains("water")) {
            return "Beverages";
        }
        if (raw.contains("vegetable") || raw.contains("fruit") || raw.contains("produce")) {
            return "Vegetables";
        }
        if (raw.contains("grain") || raw.contains("rice") || raw.contains("wheat")
                || raw.contains("dal") || raw.contains("pulse") || raw.contains("salt")) {
            return "Grains";
        }
        return "Snacks"; // safe default
    }

    private double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0.0; }
    }

    private int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (Exception e) { return fallback; }
    }
}
