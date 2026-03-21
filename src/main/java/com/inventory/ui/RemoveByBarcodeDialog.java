package com.inventory.ui;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.inventory.controller.InventoryManager;
import com.inventory.model.Product;
import com.inventory.util.BarcodeService;
import com.inventory.util.ZBarScanner;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

/**
 * Two-tab modal dialog to remove a product by scanning its barcode
 * or selecting it from a JList.
 *
 * <p>Tab 1 — Scan to Remove: live webcam feed + ZXing decode → product lookup → confirm delete.</p>
 * <p>Tab 2 — Select from List: JList of all products → confirm delete.</p>
 */
public class RemoveByBarcodeDialog extends JDialog {

    private static final Color RED_DARK = new Color(0x991b1b);
    private static final Color RED_BG   = new Color(0xfee2e2);

    private final MainFrame        frame;
    private final InventoryManager manager;

    // Webcam state (Tab 1)
    private Webcam      webcam      = null;
    private WebcamPanel webcamPanel = null;
    private Timer       scanTimer   = null;
    private int         frameCount  = 0;
    private Product     foundProduct = null;
    private JLabel      resLabel;

    // Tab 1 widgets
    private final JTextField scanResultField = new JTextField("Scanning…");
    private final JPanel     detailCard      = new JPanel();
    private final JLabel     detailName  = detailLabel("Name:     —");
    private final JLabel     detailCat   = detailLabel("Category: —");
    private final JLabel     detailPrice = detailLabel("Price:    —");
    private final JLabel     detailQty   = detailLabel("Quantity: —");
    private final JLabel     detailExp   = detailLabel("Expiry:   —");
    private final JButton    confirmRemoveBtn = redBtn("⚠  Confirm Remove");

    // ── Constructor (called by SidebarPanel + MainFrame — do NOT change signature) ─

    public RemoveByBarcodeDialog(MainFrame owner, InventoryManager manager) {
        super(owner, "Remove Product", true);
        this.frame   = owner;
        this.manager = manager;
        setSize(580, 460);
        setResizable(false);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tabs.addTab("📷  Scan to Remove", buildScanTab());
        tabs.addTab("📋  Select from List", buildListTab());

        // Stop scanner when switching away from scan tab
        tabs.addChangeListener(e ->  {
            if (tabs.getSelectedIndex() == 1 && scanTimer != null) scanTimer.stop();
            if (tabs.getSelectedIndex() == 0) startScanTimer();
        });

        add(tabs, BorderLayout.CENTER);

        // Clean up on close
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { closeWebcam(); dispose(); }
            @Override public void windowClosed(WindowEvent e)  { closeWebcam(); }
        });
        
        initReader();
    }

    // ── Tab 1: Scan to Remove ─────────────────────────────────────────────────

    private JPanel buildScanTab() {
        JPanel tab = new JPanel(new BorderLayout(0, 8));
        tab.setBackground(Color.WHITE);
        tab.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        // Fix 7 — North panel for resolution info
        JPanel northRes = new JPanel(new FlowLayout(FlowLayout.CENTER));
        northRes.setBackground(Color.WHITE);
        resLabel = new JLabel("Camera: Initializing…", SwingConstants.CENTER);
        resLabel.setFont(new Font("Arial", Font.PLAIN, 10));
        resLabel.setForeground(Color.GRAY);
        northRes.add(resLabel);
        tab.add(northRes, BorderLayout.NORTH);

        // ── Live webcam preview area
        JPanel previewHolder = new JPanel(new BorderLayout());
        previewHolder.setBackground(new Color(0x1a2e23));
        previewHolder.setPreferredSize(new Dimension(560, 230));

        try {
            // Step 1 — Get default webcam
            webcam = Webcam.getDefault();
            if (webcam == null) throw new IllegalStateException("No webcam found.");

            // Step 2 — Check what resolutions camera supports
            Dimension[] supportedSizes = webcam.getViewSizes();
            System.out.println("Supported resolutions:");
            for (Dimension d : supportedSizes) {
                System.out.println("  " + d.width + "x" + d.height);
            }

            // Register supported custom sizes
            webcam.setCustomViewSizes(new Dimension[] {
                new Dimension(640, 480),   // VGA — fallback
                new Dimension(1280, 720)   // HD 720p — target
            });

            // Set to 720p; fall back to VGA if not supported
            try {
                webcam.setViewSize(new Dimension(1280, 720));
                System.out.println("Camera set to HD 720p: 1280x720");
            } catch (Exception e) {
                Dimension[] sizes = webcam.getViewSizes();
                Dimension best = sizes[sizes.length - 1];
                webcam.setViewSize(best);
                System.out.println("720p not supported, fallback to: " + best.width + "x" + best.height);
            }

            // Log actual resolution that was set
            Dimension actual = webcam.getViewSize();
            System.out.println("FINAL camera resolution: " + actual.width + "x" + actual.height);

            // Fix 2 — Create panel AFTER resolution set
            // BEFORE webcam.open() — WebcamPanel opens it
            webcamPanel = new WebcamPanel(webcam);
            
            // Preview panel is SMALLER than capture size
            // Camera captures at 5MP for sharp barcode scan
            // Preview shows scaled 640x480 so UI stays clean
            webcamPanel.setPreferredSize(new Dimension(640, 480));
            // Fill cleanly without stretching or distorting
            webcamPanel.setFillArea(true);
            // Show FPS so user can see camera is working
            webcamPanel.setFPSDisplayed(true);
            // Mirror OFF — mirroring breaks barcode detection
            webcamPanel.setMirrored(false);
            // Do not pause on error — keep trying
            // webcamPanel.setPauseOnError(false); // Method not available

            previewHolder.add(webcamPanel, BorderLayout.CENTER);
            
            // Fix 7: Update actual resolution display label
            if (resLabel != null) {
                resLabel.setText("Camera: " + actual.width + "x" + actual.height + " @ 30 FPS");
            }

            SwingUtilities.invokeLater(this::startScanTimer);
        } catch (Exception ex) {
            JLabel noWc = new JLabel(
                "<html><center><font color='#f87171'>⚠ No webcam detected</font><br>"
                + "<font color='#9fd3b8'>Use Upload Image button</font></center></html>");
            noWc.setHorizontalAlignment(SwingConstants.CENTER);
            previewHolder.add(noWc, BorderLayout.CENTER);
        }

        // ── Result field + product detail card
        scanResultField.setEditable(false);
        scanResultField.setFont(new Font("Segoe UI", Font.BOLD, 12));
        scanResultField.setForeground(new Color(0x6b7280));
        scanResultField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xc5d9ce)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        // Detail card (hidden until product found)
        detailCard.setLayout(new BoxLayout(detailCard, BoxLayout.Y_AXIS));
        detailCard.setBackground(RED_BG);
        detailCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xfca5a5), 1, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        detailCard.add(detailName); detailCard.add(detailCat);
        detailCard.add(detailPrice); detailCard.add(detailQty); detailCard.add(detailExp);
        detailCard.setVisible(false);

        // ── Buttons
        confirmRemoveBtn.setEnabled(false);

        JButton scanAgainBtn = plainBtn("↺  Scan Again");
        JButton uploadBtn    = plainBtn("📁  Upload Image");
        JButton closeBtn     = plainBtn("Close");

        scanAgainBtn.addActionListener(e -> {
            foundProduct = null;
            frameCount = 0; // Fix 4: Reset frame count
            scanResultField.setText("Scanning…");
            scanResultField.setForeground(new Color(0x6b7280));
            detailCard.setVisible(false);
            confirmRemoveBtn.setEnabled(false);
            startScanTimer();
        });

        uploadBtn.addActionListener(e -> scanFromFile());

        confirmRemoveBtn.addActionListener(e -> {
            if (foundProduct == null) return;
            int res = JOptionPane.showConfirmDialog(this,
                    "Remove \"" + foundProduct.getName() + "\" from inventory?",
                    "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (res == JOptionPane.YES_OPTION) {
                manager.removeById(foundProduct.getId());
                frame.refreshAll();
                closeWebcam();
                dispose();
            }
        });

        closeBtn.addActionListener(e -> { closeWebcam(); dispose(); });

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.setBackground(Color.WHITE);
        btnRow.add(scanAgainBtn); btnRow.add(uploadBtn);
        btnRow.add(confirmRemoveBtn); btnRow.add(closeBtn);

        // ── Assemble
        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBackground(Color.WHITE);
        bottom.add(scanResultField);
        bottom.add(Box.createRigidArea(new Dimension(0, 6)));
        bottom.add(detailCard);
        bottom.add(Box.createRigidArea(new Dimension(0, 6)));
        bottom.add(btnRow);

        tab.add(previewHolder, BorderLayout.CENTER);
        tab.add(bottom,        BorderLayout.SOUTH);
        return tab;
    }

    // ── Tab 2: Select from List ───────────────────────────────────────────────

    private JPanel buildListTab() {
        JPanel tab = new JPanel(new BorderLayout(0, 8));
        tab.setBackground(Color.WHITE);
        tab.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        ArrayList<Product> all = manager.getAll();

        // Product JList
        DefaultListModel<String> listModel = new DefaultListModel<>();
        all.forEach(p -> listModel.addElement(
                String.format("[%d]  %-28s  Qty:%-4d  ₹%.2f",
                        p.getId(), p.getName(), p.getQuantity(), p.getPrice())));

        JList<String> jList = new JList<>(listModel);
        jList.setFont(new Font("Segoe UI Mono", Font.PLAIN, 12));
        jList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jList.setFixedCellHeight(28);
        jList.setBackground(new Color(0xfafafa));

        JScrollPane scroll = new JScrollPane(jList);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(0xd1e0d7)));

        // Detail panel (updates on list click)
        JPanel detail = new JPanel(new GridLayout(5, 1, 0, 2));
        detail.setBackground(new Color(0xf7faf8));
        detail.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xd1e0d7), 1, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        detail.setPreferredSize(new Dimension(0, 110));

        JLabel[] detRow = new JLabel[5];
        for (int i = 0; i < 5; i++) {
            detRow[i] = new JLabel("—");
            detRow[i].setFont(new Font("Segoe UI", Font.PLAIN, 12));
            detRow[i].setForeground(new Color(0x374151));
            detail.add(detRow[i]);
        }

        jList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int idx = jList.getSelectedIndex();
            if (idx < 0 || idx >= all.size()) return;
            Product p = all.get(idx);
            detRow[0].setText("Name:     " + p.getName());
            detRow[1].setText("Category: " + p.getCategory());
            detRow[2].setText("Price:    ₹" + String.format("%.2f", p.getPrice()));
            detRow[3].setText("Quantity: " + p.getQuantity());
            detRow[4].setText("Expiry:   " + (p.getExpiryDate() != null ? p.getExpiryDate() : "N/A"));
        });

        // Buttons
        JButton removeSelectedBtn = redBtn("🗑  Remove Selected");
        JButton cancelBtn         = plainBtn("Cancel");

        removeSelectedBtn.addActionListener(e -> {
            int idx = jList.getSelectedIndex();
            if (idx < 0) { JOptionPane.showMessageDialog(this, "Select a product first."); return; }
            Product p = all.get(idx);
            int res = JOptionPane.showConfirmDialog(this,
                    "Remove \"" + p.getName() + "\" from inventory?",
                    "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (res == JOptionPane.YES_OPTION) {
                manager.removeById(p.getId());
                frame.refreshAll();
                dispose();
            }
        });

        cancelBtn.addActionListener(e -> dispose());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setBackground(Color.WHITE);
        btns.add(removeSelectedBtn); btns.add(cancelBtn);

        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.setBackground(Color.WHITE);
        south.add(detail, BorderLayout.CENTER);
        south.add(btns,   BorderLayout.SOUTH);

        tab.add(scroll, BorderLayout.CENTER);
        tab.add(south,  BorderLayout.SOUTH);
        return tab;
    }

    // ── Scan logic ────────────────────────────────────────────────────────────

    private void initReader() { /* no-op: decoding delegated to ZBarScanner */ }

    private void startScanTimer() {
        if (webcam == null) return;
        if (scanTimer != null && scanTimer.isRunning()) scanTimer.stop();

        System.gc();

        scanTimer = new Timer(300, e -> {
            if (!webcam.isOpen() || !webcamPanel.isStarted()) return;

            BufferedImage frame = webcam.getImage();
            if (frame == null) return;

            if (frameCount < 3) {
                System.out.println("Frame: " + frame.getWidth() + "x" + frame.getHeight()
                        + " (" + frameCount + ")"
                        + (ZBarScanner.isZBarAvailable() ? " [ZBar]" : " [ZXing]"));
                frameCount++;
            }

            String text = ZBarScanner.decode(frame);
            frame = null; // help GC

            if (text != null && !text.isBlank()) {
                SwingUtilities.invokeLater(() -> onBarcodeDetected(text.trim()));
            }
        });
        scanTimer.start();
    }

    private void onBarcodeDetected(String text) {
        if (scanTimer != null) scanTimer.stop();
        Toolkit.getDefaultToolkit().beep();

        scanResultField.setText("Scanned: " + text);
        scanResultField.setForeground(new Color(0x15803d));

        // Look up product in inventory
        ArrayList<Product> matches = manager.searchByName(text);
        if (!matches.isEmpty()) {
            foundProduct = matches.get(0);
            detailName.setText( "Name:     " + foundProduct.getName());
            detailCat.setText(  "Category: " + foundProduct.getCategory());
            detailPrice.setText("Price:    ₹" + String.format("%.2f", foundProduct.getPrice()));
            detailQty.setText(  "Quantity: " + foundProduct.getQuantity());
            detailExp.setText(  "Expiry:   " +
                    (foundProduct.getExpiryDate() != null ? foundProduct.getExpiryDate() : "N/A"));
            detailCard.setVisible(true);
            confirmRemoveBtn.setEnabled(true);
        } else {
            foundProduct = null;
            detailCard.setVisible(false);
            confirmRemoveBtn.setEnabled(false);
            JOptionPane.showMessageDialog(this,
                    "No product named \"" + text + "\" found in inventory.",
                    "Not Found", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void scanFromFile() {
        if (scanTimer != null) scanTimer.stop();
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select barcode image");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Images (jpg, png, bmp)", "jpg", "jpeg", "png", "bmp"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                BufferedImage img = ImageIO.read(fc.getSelectedFile());
                String decoded = BarcodeService.decodeBarcode(img);
                if (decoded != null) onBarcodeDetected(decoded);
                else JOptionPane.showMessageDialog(this, "No barcode found in image.",
                        "Not Detected", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        startScanTimer();
    }

    private void closeWebcam() {
        try {
            if (scanTimer != null && scanTimer.isRunning()) {
                scanTimer.stop();
            }
            if (webcam != null && webcam.isOpen()) {
                webcamPanel.stop();
                webcam.close();
                System.out.println("Webcam closed cleanly");
            }
        } catch (Exception e) {
            System.out.println("Webcam close error: " + e.getMessage());
        }
    }



    // ── Helpers ───────────────────────────────────────────────────────────────

    private static JLabel detailLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI Mono", Font.PLAIN, 12));
        l.setForeground(new Color(0x7f1d1d));
        return l;
    }

    private static JButton redBtn(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setBackground(RED_DARK); b.setForeground(Color.WHITE);
        b.setBorderPainted(false); b.setFocusPainted(false); b.setOpaque(true);
        b.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static JButton plainBtn(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}
