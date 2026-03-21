package com.inventory.ui;

import com.inventory.controller.InventoryManager;
import com.inventory.model.Product;
import com.inventory.util.BarcodeService;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.io.File;
import java.util.ArrayList;

/**
 * Modal dialog to generate and display a CODE-128 barcode for any product,
 * with Save-as-PNG and Print options.
 */
public class BarcodeGeneratorDialog extends JDialog {

    private static final Color ACCENT = new Color(0x4a7c59);

    private final JLabel barcodeLabel = new JLabel();
    private BufferedImage currentImage = null;

    public BarcodeGeneratorDialog(JFrame owner, InventoryManager manager, String preselect) {
        super(owner, "Generate Barcode Label", true);
        setSize(420, 320);
        setResizable(false);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(0, 0));

        buildNorth(manager, preselect);
        buildCenter();
        buildSouth(owner);
    }

    // ── Panel builders ────────────────────────────────────────────────────────

    private void buildNorth(InventoryManager manager, String preselect) {
        JPanel north = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        north.setBackground(new Color(0x1e3a2a));

        // Product combo populated from inventory
        ArrayList<Product> all = manager.getAll();
        String[] names = all.stream().map(Product::getName).toArray(String[]::new);
        JComboBox<String> combo = new JComboBox<>(names.length > 0 ? names : new String[]{"(no products)"});
        combo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        combo.setPreferredSize(new Dimension(220, 30));
        if (preselect != null) combo.setSelectedItem(preselect);

        JButton generateBtn = new JButton("⚡ Generate");
        generateBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        generateBtn.setBackground(ACCENT);
        generateBtn.setForeground(Color.WHITE);
        generateBtn.setBorderPainted(false);
        generateBtn.setFocusPainted(false);
        generateBtn.setOpaque(true);
        generateBtn.addActionListener(e -> generate((String) combo.getSelectedItem()));

        JLabel lbl = new JLabel("Product:");
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lbl.setForeground(new Color(0xd1e8da));

        north.add(lbl);
        north.add(combo);
        north.add(generateBtn);
        add(north, BorderLayout.NORTH);

        // Auto-generate if a product is preselected
        if (preselect != null && !preselect.isBlank()) {
            SwingUtilities.invokeLater(() -> generate(preselect));
        }
    }

    private void buildCenter() {
        JPanel center = new JPanel(new GridBagLayout());
        center.setBackground(Color.WHITE);
        center.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        barcodeLabel.setText("← Select a product and click Generate");
        barcodeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        barcodeLabel.setForeground(new Color(0x9ca3af));
        barcodeLabel.setHorizontalAlignment(SwingConstants.CENTER);

        center.add(barcodeLabel);
        add(center, BorderLayout.CENTER);
    }

    private void buildSouth(Frame owner) {
        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        south.setBackground(new Color(0xf7faf8));
        south.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xd1e0d7)));

        JButton saveBtn  = btn("💾  Save as PNG", new Color(0x374151), Color.WHITE);
        JButton printBtn = btn("🖨  Print",        new Color(0x374151), Color.WHITE);
        JButton closeBtn = btn("✕  Close",         new Color(0xd1d5db), new Color(0x374151));

        saveBtn.addActionListener(e  -> saveAsPng(owner));
        printBtn.addActionListener(e -> printBarcode(owner));
        closeBtn.addActionListener(e -> dispose());

        south.add(saveBtn);
        south.add(printBtn);
        south.add(closeBtn);
        add(south, BorderLayout.SOUTH);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void generate(String productName) {
        if (productName == null || productName.isBlank()) return;
        try {
            currentImage = BarcodeService.generateBarcode(productName);
            barcodeLabel.setIcon(new ImageIcon(currentImage));
            barcodeLabel.setText(null);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to generate barcode: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveAsPng(Frame owner) {
        if (currentImage == null) { JOptionPane.showMessageDialog(this, "Generate a barcode first."); return; }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("barcode.png"));
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG Image", "png"));
        if (fc.showSaveDialog(owner) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            if (!f.getName().endsWith(".png")) f = new File(f.getAbsolutePath() + ".png");
            try {
                ImageIO.write(currentImage, "PNG", f);
                JOptionPane.showMessageDialog(this, "Saved: " + f.getName());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void printBarcode(Frame owner) {
        if (currentImage == null) { JOptionPane.showMessageDialog(this, "Generate a barcode first."); return; }
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintable((g, pf, page) -> {
            if (page > 0) return Printable.NO_SUCH_PAGE;
            Graphics2D g2 = (Graphics2D) g;
            double x = pf.getImageableX(), y = pf.getImageableY();
            g2.drawImage(currentImage, (int) x, (int) y, 300, 100, null);
            return Printable.PAGE_EXISTS;
        });
        if (job.printDialog()) {
            try { job.print(); }
            catch (PrinterException ex) {
                JOptionPane.showMessageDialog(this, "Print failed: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static JButton btn(String text, Color bg, Color fg) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        b.setBackground(bg); b.setForeground(fg);
        b.setBorderPainted(false); b.setFocusPainted(false); b.setOpaque(true);
        b.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}
