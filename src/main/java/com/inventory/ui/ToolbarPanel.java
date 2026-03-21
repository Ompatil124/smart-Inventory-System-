package com.inventory.ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/** Search box, category filter, sort shortcut and add button — all in one toolbar row. */
public class ToolbarPanel extends JPanel {

    private static final String[] CATEGORIES =
            {"All", "Dairy", "Bakery", "Vegetables", "Beverages", "Snacks", "Grains"};

    private final JTextField        searchBox;
    private final JComboBox<String> categoryFilter;

    public ToolbarPanel(MainFrame frame) {
        setLayout(new FlowLayout(FlowLayout.LEFT, 8, 8));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xd1e0d7)));

        // Search field
        searchBox = new JTextField(22);
        searchBox.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        searchBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xc5d9ce), 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        // Live filter on every keystroke
        searchBox.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { frame.applyFilter(); }
            public void removeUpdate(DocumentEvent e)  { frame.applyFilter(); }
            public void changedUpdate(DocumentEvent e) { frame.applyFilter(); }
        });

        // Category combo
        categoryFilter = new JComboBox<>(CATEGORIES);
        categoryFilter.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        categoryFilter.addActionListener(e -> frame.applyFilter());

        JLabel searchLbl = new JLabel("🔍");
        JLabel catLbl    = new JLabel("Category:");
        catLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        catLbl.setForeground(new Color(0x4a5568));

        JButton sortExp = styledBtn("⏰ Sort by Expiry", new Color(0x546a5e), Color.WHITE);
        sortExp.addActionListener(e -> frame.sortByExpiry());

        JButton addBtn  = styledBtn("＋  Add Product",   new Color(0x4a7c59), Color.WHITE);
        addBtn.addActionListener(e -> frame.startAddMode());

        add(searchLbl);
        add(searchBox);
        add(Box.createHorizontalStrut(6));
        add(catLbl);
        add(categoryFilter);
        add(Box.createHorizontalStrut(12));
        add(sortExp);
        add(addBtn);
    }

    public String getSearchText()      { return searchBox.getText(); }
    public String getSelectedCategory() { return (String) categoryFilter.getSelectedItem(); }
    public void   clearSearch()        { searchBox.setText(""); categoryFilter.setSelectedIndex(0); }

    private JButton styledBtn(String text, Color bg, Color fg) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setBackground(bg);
        b.setForeground(fg);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        return b;
    }
}
