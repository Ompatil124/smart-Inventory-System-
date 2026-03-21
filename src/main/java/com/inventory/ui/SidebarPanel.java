package com.inventory.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Dark-green left sidebar with grouped action buttons. */
public class SidebarPanel extends JPanel {

    // Colours
    private static final Color BG_SIDEBAR  = new Color(0x1e3a2a);
    private static final Color BTN_DEFAULT = new Color(0x2d4a38);
    private static final Color BTN_ACTIVE  = new Color(0x4a7c59);
    private static final Color BTN_HOVER   = new Color(0x3a5f48);
    private static final Color BTN_TEXT    = new Color(0xe2f0e9);
    private static final Color BTN_DANGER  = new Color(0x6b2e2e);
    private static final Color SECTION_FG  = new Color(0x6f9a82);

    private JButton activeButton = null;

    public SidebarPanel(MainFrame frame) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(BG_SIDEBAR);
        setPreferredSize(new Dimension(172, 0));
        setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        // Logo / header
        JLabel logo = new JLabel("  🌿  INVENTORY");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 13));
        logo.setForeground(new Color(0x9fd3b8));
        logo.setBorder(BorderFactory.createEmptyBorder(4, 10, 14, 0));
        logo.setAlignmentX(LEFT_ALIGNMENT);
        add(logo);

        // ── ACTIONS section ────────────────────────────────────────────────────
        addSection("ACTIONS");
        JButton viewAll = btn("◉   View All",       () -> { frame.clearFilter(); frame.refreshAll(); });
        btn("\uD83D\uDCF7   Scan Barcode",          frame::openScannerDirectly);
        btn("＋   Add Product",                      frame::startAddMode);
        btn("\u270e   Edit Product",                     frame::startEditMode);
        JButton removeBtn = btn("\u2715   Remove Product", frame::openRemoveByBarcode);
        styleAsRed(removeBtn);

        // ── REPORTS section ────────────────────────────────────────────────────
        addSection("REPORTS");
        JButton expBtn = btn("⚠   Expiry Report",   frame::showExpiryReport);
        JButton lstBtn = btn("📦  Low Stock Alert",  frame::showLowStockDialog);
        btn("🏷   Discount Report",                  frame::showDiscountReport);
        styleAsAmber(expBtn);
        styleAsAmber(lstBtn);

        // ── FILE section ───────────────────────────────────────────────────────
        addSection("FILE");
        btn("💾  Save to File",   frame::saveInventory);
        btn("📂  Load from File", frame::loadInventory);

        add(Box.createVerticalGlue());

        setActiveButton(viewAll);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void addSection(String title) {
        JLabel lbl = new JLabel("  " + title);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lbl.setForeground(SECTION_FG);
        lbl.setAlignmentX(LEFT_ALIGNMENT);
        lbl.setBorder(BorderFactory.createEmptyBorder(14, 10, 4, 0));
        add(lbl);
    }

    private JButton btn(String text, Runnable action) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        b.setForeground(BTN_TEXT);
        b.setBackground(BTN_DEFAULT);
        b.setAlignmentX(LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(172, 36));
        b.setPreferredSize(new Dimension(172, 36));
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        b.addActionListener(e -> { setActiveButton(b); action.run(); });
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { if (b != activeButton) b.setBackground(BTN_HOVER); }
            public void mouseExited(MouseEvent e)  { if (b != activeButton) b.setBackground(BTN_DEFAULT); }
        });
        add(b);
        return b;
    }

    public void setActiveButton(JButton b) {
        if (activeButton != null) activeButton.setBackground(BTN_DEFAULT);
        activeButton = b;
        if (b != null) b.setBackground(BTN_ACTIVE);
    }

    private void styleAsRed(JButton b) {
        b.setBackground(BTN_DANGER);
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(new Color(0x8b3535)); }
            public void mouseExited(MouseEvent e)  { if (b != activeButton) b.setBackground(BTN_DANGER); }
        });
    }

    private void styleAsAmber(JButton b) {
        b.setForeground(new Color(0xfde68a));
    }
}
