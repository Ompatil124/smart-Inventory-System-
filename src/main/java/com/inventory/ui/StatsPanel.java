package com.inventory.ui;

import com.inventory.logic.ExpiryChecker;
import com.inventory.model.Product;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/**
 * Horizontal row of 5 colour-coded stat cards displayed above the table.
 * Call {@link #refreshStats(ArrayList)} after every data mutation.
 */
public class StatsPanel extends JPanel {

    private final JLabel totalVal    = valueLabel("0",  new Color(0x4a7c59));
    private final JLabel expiringVal = valueLabel("0",  new Color(0xd97706));
    private final JLabel expiredVal  = valueLabel("0",  new Color(0xdc2626));
    private final JLabel lowStockVal = valueLabel("0",  new Color(0xb45309));
    private final JLabel totalValLbl = valueLabel("0",  new Color(0x0f766e));

    public StatsPanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 10, 8));
        setBackground(new Color(0xf0f4f2));
        setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));

        add(card("Total Products",   totalVal,    new Color(0x4a7c59)));
        add(card("Expiring ≤7 Days", expiringVal, new Color(0xd97706)));
        add(card("Expired",          expiredVal,  new Color(0xdc2626)));
        add(card("Low Stock (<10)",  lowStockVal, new Color(0xb45309)));
        add(card("Total Value (₹)",  totalValLbl, new Color(0x0f766e)));
    }

    // ── refresh ───────────────────────────────────────────────────────────────

    public void refreshStats(ArrayList<Product> products) {
        int expired  = ExpiryChecker.getExpiredList(products).size();
        int expiring = ExpiryChecker.getNearExpiry(products, 7).size();
        int lowStock = 0;
        double value = 0;
        for (Product p : products) {
            if (p.getQuantity() < 10) lowStock++;
            value += p.getPrice() * p.getQuantity();
        }
        totalVal.setText(String.valueOf(products.size()));
        expiringVal.setText(String.valueOf(expiring));
        expiredVal.setText(String.valueOf(expired));
        lowStockVal.setText(String.valueOf(lowStock));
        totalValLbl.setText(String.format("%.0f", value));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static JPanel card(String title, JLabel val, Color accent) {
        JPanel p = new JPanel(new BorderLayout(0, 3));
        p.setBackground(Color.WHITE);
        p.setPreferredSize(new Dimension(158, 68));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        JLabel lbl = new JLabel(title);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lbl.setForeground(new Color(0x6b7280));

        p.add(lbl, BorderLayout.NORTH);
        p.add(val, BorderLayout.CENTER);
        return p;
    }

    private static JLabel valueLabel(String text, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 22));
        l.setForeground(color);
        return l;
    }
}
