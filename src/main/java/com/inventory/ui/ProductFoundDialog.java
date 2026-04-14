package com.inventory.ui;

import com.inventory.controller.InventoryManager;
import com.inventory.model.Product;
import com.inventory.util.ProductLookupService.ProductDetails;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Modal dialog shown after a barcode scan + product lookup.
 *
 * <p>Displays found product details (pre-filled from local DB or Open Food Facts),
 * lets the user confirm / edit, then adds the product to inventory directly.</p>
 *
 * <p>If {@code details} is {@code null}, all fields are empty and the user must
 * fill them manually (red "not found" badge).</p>
 */
public class ProductFoundDialog extends JDialog {

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final Color ACCENT      = new Color(0x4a7c59);
    private static final Color HEADER_BG   = new Color(0x4a7c59);
    private static final Color BG_WHITE    = Color.WHITE;
    private static final Color AMBER_FG    = new Color(0x92400e);
    private static final Color AMBER_BG    = new Color(0xfef3c7);

    // ── Categories (must match MainFrame / FormPanel) ─────────────────────────
    private static final String[] CATS =
            {"Dairy", "Bakery", "Vegetables", "Beverages", "Snacks", "Grains", "Other"};

    // ── State ─────────────────────────────────────────────────────────────────
    private final JFrame           parentFrame;
    private final String           barcode;
    private final ProductDetails   details;       // null = not found
    private final InventoryManager inventoryManager;

    // ── Form widgets ──────────────────────────────────────────────────────────
    private final JTextField        nameField    = new JTextField();
    private final JTextField        priceField   = new JTextField();
    private final JTextField        qtyField     = new JTextField();
    private final JTextField        expiryField  = new JTextField();
    private final JComboBox<String> categoryCombo = new JComboBox<>(CATS);

    // ── Constructor ───────────────────────────────────────────────────────────

    public ProductFoundDialog(JFrame parent,
                              String barcode,
                              ProductDetails details,
                              InventoryManager inventoryManager) {
        super(parent, "Product Lookup — " + barcode, true);
        this.parentFrame      = parent;
        this.barcode          = barcode;
        this.details          = details;
        this.inventoryManager = inventoryManager;

        setSize(420, 490);
        setResizable(false);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());

        prefillFields();

        add(buildHeader(),  BorderLayout.NORTH);
        add(buildCenter(),  BorderLayout.CENTER);
        add(buildSouth(),   BorderLayout.SOUTH);
    }

    // ── Pre-fill fields from lookup result ────────────────────────────────────

    private void prefillFields() {
        if (details == null) return;

        nameField.setText(details.name != null ? details.name : "");
        priceField.setText(details.price > 0
                ? String.format("%.2f", details.price) : "");
        qtyField.setText(String.valueOf(details.quantity));

        // Auto-fill expiry date if the API provided one
        if (details.expiryDate != null) {
            expiryField.setText(details.expiryDate.toString());
            expiryField.setForeground(new Color(0x111827));
        }

        // Pre-select category combo
        for (int i = 0; i < CATS.length; i++) {
            if (CATS[i].equalsIgnoreCase(details.category)) {
                categoryCombo.setSelectedIndex(i);
                break;
            }
        }
    }

    // ── Panel builders ────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(HEADER_BG);
        p.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel title = new JLabel("Product Found!");
        if (details == null) title.setText("Product Not Found");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(Color.WHITE);
        title.setAlignmentX(CENTER_ALIGNMENT);

        JLabel sub = new JLabel("Barcode: " + barcode);
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(new Color(0xd4edda));
        sub.setAlignmentX(CENTER_ALIGNMENT);

        p.add(title);
        p.add(Box.createRigidArea(new Dimension(0, 4)));
        p.add(sub);
        return p;
    }

    private JPanel buildCenter() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(BG_WHITE);
        outer.setBorder(BorderFactory.createEmptyBorder(16, 20, 8, 20));

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(BG_WHITE);

        // ── Brand (read-only) ─────────────────────────────────────────────────
        String brandText = (details != null && details.brand != null && !details.brand.isEmpty())
                ? details.brand : "—";
        JLabel brandValueLabel = new JLabel(brandText);
        brandValueLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        brandValueLabel.setForeground(new Color(0x374151));
        addLabelRow(form, "Brand:", brandValueLabel);

        // ── Product Name ──────────────────────────────────────────────────────
        styleField(nameField);
        addLabelRow(form, "Product Name:", nameField);

        // ── Category ──────────────────────────────────────────────────────────
        categoryCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        categoryCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        categoryCombo.setAlignmentX(LEFT_ALIGNMENT);
        addLabelRow(form, "Category:", categoryCombo);

        // ── Price ─────────────────────────────────────────────────────────────
        styleField(priceField);
        addLabelRow(form, "Price (₹):", priceField);

        // Amber warning if price is not available
        if (details != null && details.price == 0.0) {
            JLabel warn = new JLabel("⚠  Price not available — please enter manually");
            warn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            warn.setForeground(AMBER_FG);
            warn.setBackground(AMBER_BG);
            warn.setOpaque(true);
            warn.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            warn.setAlignmentX(LEFT_ALIGNMENT);
            warn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            form.add(warn);
            form.add(Box.createRigidArea(new Dimension(0, 6)));
        }

        // ── Quantity ──────────────────────────────────────────────────────────
        styleField(qtyField);
        addLabelRow(form, "Quantity:", qtyField);

        // ── Expiry Date ──────────────────────────────────────────────────────
        styleField(expiryField);
        expiryField.setToolTipText("Format: YYYY-MM-DD");
        boolean expiryAutoFilled = (details != null && details.expiryDate != null);
        if (!expiryAutoFilled) {
            // Light placeholder hint
            expiryField.setText("YYYY-MM-DD");
            expiryField.setForeground(new Color(0xaaaaaa));
            expiryField.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusGained(java.awt.event.FocusEvent e) {
                    if (expiryField.getText().equals("YYYY-MM-DD")) {
                        expiryField.setText("");
                        expiryField.setForeground(new Color(0x111827));
                    }
                }
                @Override public void focusLost(java.awt.event.FocusEvent e) {
                    if (expiryField.getText().isBlank()) {
                        expiryField.setText("YYYY-MM-DD");
                        expiryField.setForeground(new Color(0xaaaaaa));
                    }
                }
            });
        }
        // Label row: if auto-filled show a small "(auto-filled)" hint
        JPanel expiryRow = new JPanel(new BorderLayout(6, 0));
        expiryRow.setBackground(BG_WHITE);
        expiryRow.add(expiryField, BorderLayout.CENTER);
        if (expiryAutoFilled) {
            JLabel autoTag = new JLabel("✔ auto");
            autoTag.setFont(new Font("Segoe UI", Font.BOLD, 10));
            autoTag.setForeground(new Color(0x059669));
            autoTag.setToolTipText("Expiry date was automatically fetched from the product database");
            expiryRow.add(autoTag, BorderLayout.EAST);
        }
        expiryRow.setAlignmentX(LEFT_ALIGNMENT);
        expiryRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        addLabelRowWidget(form, "Expiry Date:", expiryRow);

        // ── Source badge ──────────────────────────────────────────────────────
        JLabel badge = buildBadge();
        badge.setAlignmentX(LEFT_ALIGNMENT);
        form.add(Box.createRigidArea(new Dimension(0, 8)));
        form.add(badge);

        outer.add(form, BorderLayout.NORTH);
        return outer;
    }

    private JPanel buildSouth() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        p.setBackground(new Color(0xf7faf8));
        p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xd1e0d7)));

        JButton addBtn    = actionBtn("✔  Add to Inventory", ACCENT, Color.WHITE);
        JButton editBtn   = actionBtn("✏  Edit & Add",       new Color(0x546a5e), Color.WHITE);
        JButton linkBtn   = actionBtn("🔗  Link to Existing",  new Color(0x1d4ed8), Color.WHITE);
        JButton cancelBtn = actionBtn("✕  Cancel",           new Color(0xe5e7eb), new Color(0x374151));

        // Check if a product with the same name already exists in inventory
        boolean nameExists = inventoryManager != null
                && details != null
                && details.name != null
                && !inventoryManager.searchByName(details.name).isEmpty();

        addBtn.addActionListener(e    -> handleAdd());
        editBtn.addActionListener(e   -> { /* keep dialog open for editing */ });
        linkBtn.addActionListener(e   -> handleLinkBarcode());
        cancelBtn.addActionListener(e -> dispose());

        p.add(addBtn);
        p.add(editBtn);
        if (nameExists) p.add(linkBtn);   // only show when a match exists
        p.add(cancelBtn);
        return p;
    }

    // ── Badge helper ──────────────────────────────────────────────────────────

    private JLabel buildBadge() {
        JLabel badge;
        if (details == null) {
            badge = new JLabel("  ✗  Product not found — fill details manually  ");
            badge.setBackground(new Color(0xfee2e2));
            badge.setForeground(new Color(0x991b1b));
        } else if (details.fromLocalDB) {
            badge = new JLabel("  ✔  Found in local database  ");
            badge.setBackground(new Color(0xd1fae5));
            badge.setForeground(new Color(0x065f46));
        } else {
            badge = new JLabel("  ✔  Found via Open Food Facts  ");
            badge.setBackground(new Color(0xdbeafe));
            badge.setForeground(new Color(0x1e40af));
        }
        badge.setFont(new Font("Segoe UI", Font.BOLD, 11));
        badge.setOpaque(true);
        badge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(badge.getBackground().darker(), 1, true),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        return badge;
    }

    // ── Add-to-inventory logic ────────────────────────────────────────────────

    private void handleAdd() {
        // ── Validate name ─────────────────────────────────────────────────────
        String name = nameField.getText().trim();
        if (name.isEmpty() || name.equals("YYYY-MM-DD")) {
            err("Product name cannot be empty.");
            return;
        }

        // ── Validate price ────────────────────────────────────────────────────
        double price;
        try {
            price = Double.parseDouble(priceField.getText().trim());
            if (price < 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            err("Enter a valid positive price (e.g. 20.00).");
            return;
        }

        // ── Validate quantity ─────────────────────────────────────────────────
        int qty;
        try {
            qty = Integer.parseInt(qtyField.getText().trim());
            if (qty < 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            err("Enter a valid non-negative quantity.");
            return;
        }

        // ── Validate / parse expiry ───────────────────────────────────────────
        LocalDate expiry = null;
        String expiryRaw = expiryField.getText().trim();
        if (!expiryRaw.isEmpty() && !expiryRaw.equals("YYYY-MM-DD")) {
            try {
                expiry = LocalDate.parse(expiryRaw);
            } catch (DateTimeParseException ex) {
                err("Date format must be YYYY-MM-DD (e.g. 2026-12-31).");
                return;
            }
        }

        String category = (String) categoryCombo.getSelectedItem();

        // ── Duplicate-barcode check using existing names ──────────────────────
        ArrayList<Product> existing = inventoryManager.searchByName(name);
        if (!existing.isEmpty()) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "\"" + name + "\" already exists in inventory.\n"
                    + "Use \u201cLink to Existing\u201d to add this barcode to that product.\n\nAdd anyway as a new entry?",
                    "Product Already Exists",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) return;
        }

        // ── Build Product and add via MainFrame's public API ──────────────────
        int newId = inventoryManager.nextId();
        Product p = new Product(newId, name, category, price, qty, expiry, 0.0);
        // Attach the scanned barcode to this product
        p.addBarcode(barcode);

        // saveProduct() handles: addProduct, refreshAll, statusBar update, form clear
        if (parentFrame instanceof MainFrame mf) {
            mf.saveProduct(p);
        } else {
            inventoryManager.addProduct(p);
        }

        dispose();
    }

    /**
     * Links the scanned barcode to an existing product that shares the same name.
     * If multiple matches exist, the user picks one from a list.
     */
    private void handleLinkBarcode() {
        String name = (details != null && details.name != null) ? details.name.trim() : nameField.getText().trim();
        if (name.isEmpty()) { err("No product name available to match."); return; }

        List<Product> matches = inventoryManager.searchByName(name);
        if (matches.isEmpty()) {
            err("No existing product named \"" + name + "\" found. Use Add to Inventory instead.");
            return;
        }

        Product target;
        if (matches.size() == 1) {
            target = matches.get(0);
        } else {
            Product[] arr = matches.toArray(new Product[0]);
            target = (Product) JOptionPane.showInputDialog(
                    this,
                    "Multiple products match \"" + name + "\".\nSelect one to link barcode " + barcode + " to:",
                    "Link Barcode", JOptionPane.QUESTION_MESSAGE, null, arr, arr[0]);
            if (target == null) return;
        }

        target.addBarcode(barcode);
        inventoryManager.updateProduct(target);
        if (parentFrame instanceof MainFrame mf) mf.refreshAll();

        JOptionPane.showMessageDialog(this,
                "Barcode " + barcode + " linked to \"" + target.getName() + "\" successfully!",
                "Barcode Linked", JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }

    // ── Form helpers ──────────────────────────────────────────────────────────

    private void addLabelRow(JPanel form, String labelText, JComponent widget) {
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lbl.setForeground(new Color(0x4a5568));
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        form.add(lbl);
        form.add(Box.createRigidArea(new Dimension(0, 3)));
        widget.setAlignmentX(LEFT_ALIGNMENT);
        widget.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        form.add(widget);
        form.add(Box.createRigidArea(new Dimension(0, 10)));
    }

    /**
     * Same as addLabelRow but accepts any JComponent (e.g. a wrapper JPanel);
     * does not force MaximumSize so that panels can manage their own layout.
     */
    private void addLabelRowWidget(JPanel form, String labelText, JComponent widget) {
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lbl.setForeground(new Color(0x4a5568));
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        form.add(lbl);
        form.add(Box.createRigidArea(new Dimension(0, 3)));
        widget.setAlignmentX(LEFT_ALIGNMENT);
        form.add(widget);
        form.add(Box.createRigidArea(new Dimension(0, 10)));
    }

    private static void styleField(JTextField tf) {
        tf.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xc5d9ce), 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        tf.setForeground(new Color(0x111827));
    }

    private static JButton actionBtn(String text, Color bg, Color fg) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setBackground(bg);
        b.setForeground(fg);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void err(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Validation Error", JOptionPane.WARNING_MESSAGE);
    }
}
