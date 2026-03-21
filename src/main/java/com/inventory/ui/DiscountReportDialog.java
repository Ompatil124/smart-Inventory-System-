package com.inventory.ui;

import com.inventory.controller.InventoryManager;
import com.inventory.logic.DiscountEngine;
import com.inventory.model.Product;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;

/** Modal dialog: discount applied to each product and the reason rule. */
public class DiscountReportDialog extends JDialog {

    private static final String[] COLS =
            {"Product Name", "Original Price", "Discount %", "Final Price (₹)", "Reason"};

    public DiscountReportDialog(MainFrame owner, InventoryManager manager) {
        super(owner, "Discount Report", true);
        setSize(700, 420);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        ArrayList<Product> all = manager.getAll();
        DefaultTableModel model = new DefaultTableModel(COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        int discountedCount = 0;
        for (Product p : all) {
            String  reason     = DiscountEngine.describeRule(p);   // non-mutating peek
            double  finalPrice = DiscountEngine.applyDiscount(p);  // applies & returns
            double  discPct    = p.getDiscountPercent();
            if (discPct > 0) discountedCount++;
            model.addRow(new Object[]{
                p.getName(),
                String.format("%.2f", p.getPrice()),
                String.format("%.0f%%", discPct),
                String.format("%.2f", finalPrice),
                reason
            });
        }

        JTable table = ExpiryReportDialog.styledTable(model);

        String headerTxt = String.format("🏷  Discount Report — %d discounted / %d total",
                discountedCount, all.size());
        add(ExpiryReportDialog.header(headerTxt), BorderLayout.NORTH);
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
