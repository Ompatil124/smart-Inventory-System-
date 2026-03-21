package com.inventory.ui;

import com.inventory.logic.ExpiryChecker;
import com.inventory.model.Product;
import com.inventory.util.BarcodeService;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Scrollable JTable of inventory products with a colour-coded Status column.
 * Row clicks fire {@link MainFrame#onProductSelected(Product)}.
 */
public class ProductTablePanel extends JPanel {

    private static final String[] COLUMNS =
            {"#", "Product Name", "Category", "Price (\u20b9)", "Qty", "Expiry Date", "Status", "Barcode"};

    private final MainFrame        frame;
    private final DefaultTableModel model;
    private final JTable           table;

    // Currently displayed rows (filtered)
    private final ArrayList<Product> displayedRows = new ArrayList<>();

    // Barcode image cache keyed by product name
    private final Map<String, ImageIcon> barcodeCache = new HashMap<>();

    public ProductTablePanel(MainFrame frame) {
        this.frame = frame;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        model = new DefaultTableModel(COLUMNS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        table = new JTable(model);
        styleTable();

        // Row click → populate form
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && table.getSelectedRow() >= 0) {
                int idx = table.getSelectedRow();
                if (idx < displayedRows.size()) {
                    frame.onProductSelected(displayedRows.get(idx));
                }
            }
        });

        // Delete key on selected row
        table.getInputMap(JComponent.WHEN_FOCUSED)
             .put(KeyStroke.getKeyStroke("DELETE"), "deleteRow");
        table.getActionMap().put("deleteRow",
             new AbstractAction() { public void actionPerformed(java.awt.event.ActionEvent e) {
                 frame.removeSelectedProduct();
             }});

        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    // ── public API ────────────────────────────────────────────────────────────

    public void refreshTable(ArrayList<Product> products) {
        displayedRows.clear();
        displayedRows.addAll(products);
        model.setRowCount(0);

        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            String expiry = p.getExpiryDate() != null ? p.getExpiryDate().toString() : "N/A";
            // Cache barcode icons
            ImageIcon barcodeIcon = barcodeCache.computeIfAbsent(p.getName(), name -> {
                BufferedImage img = BarcodeService.generateSmall(name);
                return img != null ? new ImageIcon(img) : null;
            });
            model.addRow(new Object[]{
                i + 1,
                p.getName(),
                p.getCategory(),
                String.format("%.2f", p.getPrice()),
                p.getQuantity(),
                expiry,
                statusLabel(p),
                barcodeIcon
            });
        }
    }

    public void clearSelection() { table.clearSelection(); }

    // ── private helpers ───────────────────────────────────────────────────────

    private String statusLabel(Product p) {
        if (ExpiryChecker.isExpired(p))               return "Expired";
        long days = ExpiryChecker.daysUntilExpiry(p);
        if (days != Long.MAX_VALUE && days <= 7)       return "Expiring Soon";
        if (p.getQuantity() < 10)                      return "Low Stock";
        return "Good";
    }

    private void styleTable() {
        table.setRowHeight(32);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setGridColor(new Color(0xecf0ed));
        table.setShowVerticalLines(false);
        table.setSelectionBackground(new Color(0xd4e8da));
        table.setSelectionForeground(new Color(0x1a2e23));
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);

        // Header style
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.getTableHeader().setBackground(new Color(0x4a7c59));
        table.getTableHeader().setForeground(Color.WHITE);
        table.getTableHeader().setPreferredSize(new Dimension(0, 36));

        // Column widths
        int[] widths = {36, 180, 100, 80, 55, 100, 100, 105};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        // Centred numeric columns
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        for (int col : new int[]{0, 3, 4}) {
            table.getColumnModel().getColumn(col).setCellRenderer(center);
        }

        // Alternating row colour
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable t, Object value, boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, value, sel, foc, row, col);
                if (!sel) setBackground(row % 2 == 0 ? Color.WHITE : new Color(0xf7faf8));
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                return this;
            }
        });

        // Status column — pill renderer (column 6)
        table.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable t, Object value, boolean sel, boolean foc, int row, int col) {
                JLabel label = new JLabel(value == null ? "" : value.toString());
                label.setHorizontalAlignment(SwingConstants.CENTER);
                label.setFont(new Font("Segoe UI", Font.BOLD, 11));
                label.setOpaque(true);
                String status = value == null ? "" : value.toString();
                switch (status) {
                    case "Good":         label.setBackground(new Color(0xeaf3de)); label.setForeground(new Color(0x27500a)); break;
                    case "Expiring Soon":label.setBackground(new Color(0xfef3c7)); label.setForeground(new Color(0x633806)); break;
                    case "Expired":      label.setBackground(new Color(0xfee2e2)); label.setForeground(new Color(0x7f1d1d)); break;
                    case "Low Stock":    label.setBackground(new Color(0xfef3c7)); label.setForeground(new Color(0x92400e)); break;
                    default:             label.setBackground(Color.WHITE);         label.setForeground(Color.DARK_GRAY);
                }
                if (sel) { label.setBackground(new Color(0xd4e8da)); label.setForeground(new Color(0x1a2e23)); }
                label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                return label;
            }
        });

        // Barcode column — renders ImageIcon (column 7)
        table.getColumnModel().getColumn(7).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable t, Object value, boolean sel, boolean foc, int row, int col) {
                JLabel lbl = new JLabel();
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                lbl.setOpaque(true);
                lbl.setBackground(sel ? new Color(0xd4e8da) : (row % 2 == 0 ? Color.WHITE : new Color(0xf7faf8)));
                if (value instanceof ImageIcon) lbl.setIcon((ImageIcon) value);
                else lbl.setText("N/A");
                return lbl;
            }
        });
        table.setRowHeight(36);   // taller to fit barcode
    }
}
