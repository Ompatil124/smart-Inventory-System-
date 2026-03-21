package com.inventory.controller;

import com.inventory.io.FileHandler;
import com.inventory.logic.ExpiryChecker;
import com.inventory.logic.InventoryLogic;
import com.inventory.model.Product;
import com.inventory.util.SortUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * GUI-friendly business layer — wraps ArrayList&lt;Product&gt; and delegates
 * to the existing logic/util/io classes.  No Scanner; no console I/O.
 */
public class InventoryManager {

    private final ArrayList<Product> products   = new ArrayList<>();
    private final FileHandler        fileHandler = new FileHandler();
    private final InventoryLogic     logic       = new InventoryLogic();

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public void addProduct(Product p) { products.add(p); }

    public boolean removeById(int id) {
        return products.removeIf(p -> p.getId() == id);
    }

    public boolean updateProduct(Product updated) {
        for (int i = 0; i < products.size(); i++) {
            if (products.get(i).getId() == updated.getId()) {
                products.set(i, updated);
                return true;
            }
        }
        return false;
    }

    public Optional<Product> findById(int id) {
        return logic.findById(products, id);
    }

    public ArrayList<Product> searchByName(String query) {
        return new ArrayList<>(logic.searchByName(products, query));
    }

    /** Returns the live backing list — do not add/remove directly. */
    public ArrayList<Product> getAll() { return products; }

    public int     nextId()   { return logic.nextId(products); }
    public int     size()     { return products.size(); }
    public boolean isEmpty()  { return products.isEmpty(); }

    // ── Sorting (in-place) ────────────────────────────────────────────────────

    public void sortByName()     { SortUtil.sortByName(products); }
    public void sortByPrice()    { SortUtil.sortByPrice(products); }
    public void sortByQuantity() { SortUtil.sortByQuantity(products); }
    public void sortByExpiry()   { SortUtil.sortByExpiry(products); }

    // ── Filtered subsets ──────────────────────────────────────────────────────

    public ArrayList<Product> getExpiredList()         { return ExpiryChecker.getExpiredList(products); }
    public ArrayList<Product> getNearExpiry(int days)  { return ExpiryChecker.getNearExpiry(products, days); }

    public ArrayList<Product> getLowStock(int threshold) {
        ArrayList<Product> r = new ArrayList<>();
        for (Product p : products) if (p.getQuantity() <= threshold) r.add(p);
        return r;
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    public int    countExpiringSoon(int days) { return getNearExpiry(days).size(); }
    public int    countExpired()              { return getExpiredList().size(); }
    public int    countLowStock(int max)      { return getLowStock(max).size(); }
    public double totalValue() {
        return products.stream().mapToDouble(p -> p.getPrice() * p.getQuantity()).sum();
    }

    // ── File I/O ──────────────────────────────────────────────────────────────

    public void saveToFile(String path) throws IOException {
        fileHandler.save(products, path);
    }

    public void loadFromFile(String path) throws IOException {
        List<Product> loaded = fileHandler.load(path);
        products.clear();
        products.addAll(loaded);
    }
}
