package com.inventory.ui;

import com.inventory.controller.InventoryManager;
import com.inventory.logic.ExpiryChecker;
import com.inventory.logic.DiscountEngine;
import com.inventory.model.Product;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

/** Modal dialog: products expiring within 7 days, with an Export-to-TXT button. */
public class ExpiryReportDialog extends JDialog {

    private static final String[] COLS =
            {"Product Name", "Category", "Expiry Date", "Days Left", "Discounted Price (₹)"};

    public ExpiryReportDialog(MainFrame owner, InventoryManager manager) {
        super(owner, "Expiry Report — Next 7 Days", true);
        setSize(640, 420);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(0, 0));

        ArrayList<Product> list = manager.getNearExpiry(7);
        DefaultTableModel model = new DefaultTableModel(COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        for (Product p : list) {
            long days       = ExpiryChecker.daysUntilExpiry(p);
            double discPrice = DiscountEngine.applyDiscount(p);
            model.addRow(new Object[]{
                p.getName(),
                p.getCategory(),
                p.getExpiryDate() != null ? p.getExpiryDate().toString() : "N/A",
                days,
                String.format("%.2f", discPrice)
            });
        }

        JTable table = styledTable(model);
        add(header("⚠  Products Expiring Within 7 Days — " + list.size() + " found"), BorderLayout.NORTH);
        add(new JScrollPane(table),  BorderLayout.CENTER);
        add(footerWithExport(list, owner), BorderLayout.SOUTH);
    }

    private JPanel footerWithExport(ArrayList<Product> list, Frame owner) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        p.setBackground(new Color(0xf7faf8));
        p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xd1e0d7)));

        JButton exportBtn = new JButton("📄  Export to TXT");
        exportBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        exportBtn.addActionListener(e -> exportTxt(list, owner));

        JButton closeBtn = new JButton("Close");
        closeBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        closeBtn.addActionListener(e -> dispose());

        p.add(exportBtn);
        p.add(closeBtn);
        return p;
    }

    private void exportTxt(ArrayList<Product> list, Frame owner) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new java.io.File("expiry_report.txt"));
        if (fc.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) return;
        try (FileWriter fw = new FileWriter(fc.getSelectedFile())) {
            fw.write("EXPIRY REPORT\n" + "=".repeat(60) + "\n\n");
            for (Product p : list) {
                long days = ExpiryChecker.daysUntilExpiry(p);
                fw.write(String.format("%-30s | %-12s | %s | %d days left%n",
                        p.getName(), p.getCategory(), p.getExpiryDate(), days));
            }
            JOptionPane.showMessageDialog(owner, "Report saved successfully.");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(owner, "Export failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    static JTable styledTable(DefaultTableModel model) {
        JTable t = new JTable(model);
        t.setRowHeight(30);
        t.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        t.setGridColor(new Color(0xecf0ed));
        t.setShowVerticalLines(false);
        t.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        t.getTableHeader().setBackground(new Color(0x4a7c59));
        t.getTableHeader().setForeground(Color.WHITE);
        t.getTableHeader().setPreferredSize(new Dimension(0, 34));
        return t;
    }

    static JLabel header(String text) {
        JLabel l = new JLabel("  " + text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 13));
        l.setForeground(new Color(0x1a2e23));
        l.setBackground(new Color(0xeaf3de));
        l.setOpaque(true);
        l.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        return l;
    }
}
