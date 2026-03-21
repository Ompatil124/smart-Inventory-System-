package com.inventory.ui;

import com.inventory.logic.ExpiryChecker;
import com.inventory.model.Product;
import com.inventory.util.BarcodeService;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Right-side form panel: add a new product or edit the currently selected one.
 * Width is fixed at 220 px.
 */
public class FormPanel extends JPanel {

    private static final Color ACCENT    = new Color(0x4a7c59);
    private static final Color AMBER_BG  = new Color(0xfef3c7);
    private static final Color AMBER_FG  = new Color(0x92400e);
    private static final String[] CATS   =
            {"Dairy", "Bakery", "Vegetables", "Beverages", "Snacks", "Grains", "Other"};

    private final MainFrame frame;

    // Form fields
    private final JTextField        nameField     = field();
    private final JComboBox<String> categoryCombo = new JComboBox<>(CATS);
    private final JTextField        priceField    = field();
    private final JTextField        qtyField      = field();
    private final JTextField        expiryField   = field();
    private final JLabel            discountWarn  = new JLabel();
    private final JLabel            titleLabel    = new JLabel("Add / Edit Product");

    /** -1 = "new mode"; any positive value = ID of product being edited. */
    private int editingId = -1;

    public FormPanel(MainFrame frame) {
        this.frame = frame;
        setBackground(new Color(0xf7faf8));
        setPreferredSize(new Dimension(222, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(0xd1e0d7)),
                BorderFactory.createEmptyBorder(16, 14, 16, 14)));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        // Title
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(ACCENT);
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);
        add(titleLabel);
        add(spacer(10));

        // Fields — Product Name row has two icon buttons appended
        addNameFieldRow();
        // addField("Product Name", nameField);  // replaced by addNameFieldRow()
        addCombo("Category",          categoryCombo);
        addField("Price (₹)",         priceField);
        addField("Quantity",          qtyField);
        addField("Expiry (YYYY-MM-DD)", expiryField);

        // Discount warning banner (hidden by default)
        discountWarn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        discountWarn.setForeground(AMBER_FG);
        discountWarn.setBackground(AMBER_BG);
        discountWarn.setOpaque(true);
        discountWarn.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        discountWarn.setAlignmentX(LEFT_ALIGNMENT);
        discountWarn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        discountWarn.setVisible(false);
        add(discountWarn);
        add(spacer(12));

        // Buttons row
        JPanel btnRow = new JPanel(new GridLayout(1, 2, 8, 0));
        btnRow.setBackground(new Color(0xf7faf8));
        btnRow.setAlignmentX(LEFT_ALIGNMENT);
        btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        JButton clearBtn = new JButton("Clear");
        clearBtn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        clearBtn.setFocusPainted(false);
        clearBtn.addActionListener(e -> clear());

        JButton saveBtn = new JButton("Save");
        saveBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        saveBtn.setBackground(ACCENT);
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setBorderPainted(false);
        saveBtn.setFocusPainted(false);
        saveBtn.setOpaque(true);
        saveBtn.addActionListener(e -> save());

        btnRow.add(clearBtn);
        btnRow.add(saveBtn);
        add(btnRow);
    }

    // ── public API ────────────────────────────────────────────────────────────

    /** Populate form fields from an existing product (edit mode). */
    public void populate(Product p) {
        editingId = p.getId();
        titleLabel.setText("Edit Product");
        nameField.setText(p.getName());
        categoryCombo.setSelectedItem(p.getCategory());
        priceField.setText(String.valueOf(p.getPrice()));
        qtyField.setText(String.valueOf(p.getQuantity()));
        expiryField.setText(p.getExpiryDate() != null ? p.getExpiryDate().toString() : "");
        updateDiscountWarning(p);
        nameField.requestFocusInWindow();
    }

    /** Reset to "add new product" mode. */
    public void clear() {
        editingId = -1;
        titleLabel.setText("Add / Edit Product");
        nameField.setText("");
        categoryCombo.setSelectedIndex(0);
        priceField.setText("");
        qtyField.setText("");
        expiryField.setText("");
        discountWarn.setVisible(false);
        nameField.requestFocusInWindow();
    }

    public void focusName()             { nameField.requestFocusInWindow(); }
    public void setNameText(String name){ nameField.setText(name); }

    // ── private helpers ───────────────────────────────────────────────────────

    private void save() {
        // ── Validate ──────────────────────────────────────────────────────────
        String name = nameField.getText().trim();
        if (name.isEmpty()) { error("Product name cannot be empty."); return; }

        double price;
        try { price = Double.parseDouble(priceField.getText().trim()); if (price < 0) throw new NumberFormatException(); }
        catch (NumberFormatException ex) { error("Enter a valid positive price."); return; }

        int qty;
        try { qty = Integer.parseInt(qtyField.getText().trim()); if (qty < 0) throw new NumberFormatException(); }
        catch (NumberFormatException ex) { error("Enter a valid non-negative quantity."); return; }

        LocalDate expiry = null;
        String expiryRaw = expiryField.getText().trim();
        if (!expiryRaw.isEmpty()) {
            try { expiry = LocalDate.parse(expiryRaw); }
            catch (DateTimeParseException ex) { error("Date format must be YYYY-MM-DD."); return; }
        }

        String category = (String) categoryCombo.getSelectedItem();

        // ── Build and save ────────────────────────────────────────────────────
        Product p;
        if (editingId > 0) {
            p = new Product(editingId, name, category, price, qty, expiry, 0.0);
        } else {
            int newId = frame.getManager().nextId();
            p = new Product(newId, name, category, price, qty, expiry, 0.0);
        }
        frame.saveProduct(p);
    }

    private void updateDiscountWarning(Product p) {
        long days = ExpiryChecker.daysUntilExpiry(p);
        if (days >= 0 && days <= 3) {
            discountWarn.setText("<html>⚠ Expiring in " + days + " day(s) — 30% off applied</html>");
            discountWarn.setVisible(true);
        } else {
            discountWarn.setVisible(false);
        }
    }

    private void addField(String labelText, JTextField tf) {
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lbl.setForeground(new Color(0x4a5568));
        lbl.setAlignmentX(LEFT_ALIGNMENT);

        tf.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xc5d9ce), 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        tf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        tf.setAlignmentX(LEFT_ALIGNMENT);

        add(lbl);
        add(spacer(3));
        add(tf);
        add(spacer(10));
    }

    private void addCombo(String labelText, JComboBox<String> combo) {
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lbl.setForeground(new Color(0x4a5568));
        lbl.setAlignmentX(LEFT_ALIGNMENT);

        combo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        combo.setAlignmentX(LEFT_ALIGNMENT);

        add(lbl);
        add(spacer(3));
        add(combo);
        add(spacer(10));
    }

    private static JTextField field() { return new JTextField(); }
    private static Component  spacer(int h) { return Box.createRigidArea(new Dimension(0, h)); }
    private void error(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Validation Error", JOptionPane.WARNING_MESSAGE);
    }

    // ── Name field row with barcode icon buttons ───────────────────────────────

    private void addNameFieldRow() {
        JLabel lbl = new JLabel("Product Name");
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lbl.setForeground(new Color(0x4a5568));
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        add(lbl);
        add(spacer(3));

        // Row: [name field] [📷] [≡]
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(new Color(0xf7faf8));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        nameField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        nameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xc5d9ce), 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        // Scan button — new flow: ProductFoundDialog opens automatically from BarcodeScannerDialog
        JButton scanBtn = iconBtn("\uD83D\uDCF7", "Scan barcode to look up and add product");
        scanBtn.addActionListener(e -> {
            JFrame parentWindow = (JFrame) SwingUtilities.getWindowAncestor(this);
            // Pass inventoryManager so BarcodeScannerDialog can hand it to ProductFoundDialog
            BarcodeScannerDialog dlg = new BarcodeScannerDialog(
                    parentWindow, frame.getManager());
            dlg.setVisible(true);
            // ProductFoundDialog handles adding directly — just refresh here in case
            // the user added a product via the scan → lookup → confirm chain
            frame.refreshAll();
        });

        // Generate barcode button (only useful when name is filled)
        JButton genBtn = iconBtn("\u2261", "Generate barcode for current name");
        genBtn.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) { error("Enter a product name first."); return; }
            new BarcodeGeneratorDialog(
                    (JFrame) SwingUtilities.getWindowAncestor(this),
                    frame.getManager(), name)
                .setVisible(true);
        });

        JPanel icons = new JPanel(new GridLayout(1, 2, 2, 0));
        icons.setBackground(new Color(0xf7faf8));
        icons.add(scanBtn);
        icons.add(genBtn);

        row.add(nameField, BorderLayout.CENTER);
        row.add(icons,     BorderLayout.EAST);
        add(row);
        add(spacer(10));
    }

    private static JButton iconBtn(String text, String tooltip) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
        b.setToolTipText(tooltip);
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(28, 28));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}
