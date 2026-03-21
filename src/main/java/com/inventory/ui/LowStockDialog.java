package com.inventory.ui;

import com.inventory.controller.InventoryManager;
import com.inventory.model.Product;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;

/** Modal dialog: products with quantity below 10. */
public class LowStockDialog extends JDialog {

    private static final String[] COLS =
            {"Product Name", "Category", "Quantity", "Price (₹)"};

    public LowStockDialog(MainFrame owner, InventoryManager manager) {
        super(owner, "Low Stock Alert", true);
        setSize(600, 400);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        ArrayList<Product> list = manager.getLowStock(9);
        DefaultTableModel model = new DefaultTableModel(COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        for (Product p : list) {
            model.addRow(new Object[]{
                p.getName(),
                p.getCategory(),
                p.getQuantity(),
                String.format("%.2f", p.getPrice())
            });
        }

        JTable table = ExpiryReportDialog.styledTable(model);

        // Highlight zero-stock rows in red
        table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, value, sel, foc, row, col);
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                if (!sel) {
                    Object qty = t.getValueAt(row, 2);
                    if (qty instanceof Integer && (Integer) qty == 0)
                        setBackground(new Color(0xfee2e2));
                    else
                        setBackground(row % 2 == 0 ? Color.WHITE : new Color(0xf7faf8));
                }
                return this;
            }
        });

        add(ExpiryReportDialog.header("📦  Low Stock Alert — " + list.size() + " product(s)"), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(footer(), BorderLayout.SOUTH);
    }

    private JPanel footer() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        p.setBackground(new Color(0xf7faf8));
        p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xd1e0d7)));
        JButton closeBtn = new JButton("Close");
        closeBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        closeBtn.addActionListener(e -> dispose());
        p.add(closeBtn);
        return p;
    }
}
