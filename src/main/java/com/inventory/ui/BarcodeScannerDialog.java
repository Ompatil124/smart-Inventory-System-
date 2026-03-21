package com.inventory.ui;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.inventory.controller.InventoryManager;
import com.inventory.util.BarcodeService;
import com.inventory.util.ProductLookupService;
import com.inventory.util.ProductLookupService.ProductDetails;
import com.inventory.util.ZBarScanner;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

/**
 * Modal barcode scanner dialog using real webcam feed via webcam-capture 0.3.12.
 *
 * <p>Opens the system default camera, displays a live {@link WebcamPanel} preview,
 * and auto-decodes every frame with ZXing every 200 ms.
 * On success: beep + green flash + fills result field + enables Confirm.</p>
 *
 * <p>Falls back to JFileChooser image upload if no webcam is detected.</p>
 *
 * <p>Preserved public API (called by FormPanel and MainFrame — do not change):
 * <ul>
 *   <li>{@link #BarcodeScannerDialog(JFrame)}</li>
 *   <li>{@link #getScannedText()}</li>
 *   <li>{@link #getScannedProductName()} — alias</li>
 * </ul>
 * </p>
 */
public class BarcodeScannerDialog extends JDialog {

    private static final Color ACCENT   = new Color(0x4a7c59);
    private static final Color DARK_BG  = new Color(0x1e3a2a);
    private static final Color SOUTH_BG = new Color(0xf7faf8);

    // Webcam
    private Webcam      webcam      = null;
    private WebcamPanel webcamPanel = null;

    // Scan state
    private Timer  scanTimer   = null;
    private String scannedText = null;
    private int    frameCount  = 0;

    // South panel widgets
    private final JTextField resultField  = new JTextField("Scanning\u2026");
    private final JButton    confirmBtn   = new JButton("\u2714  Confirm");
    private final JPanel     centerHolder = new JPanel(new BorderLayout());

    // Product lookup service (initialized lazily to avoid blocking constructor)
    private ProductLookupService lookupService;
    // InventoryManager reference — passed from FormPanel / MainFrame
    private InventoryManager     inventoryManager;

    // ── Constructor ───────────────────────────────────────────────────────────

    public BarcodeScannerDialog(JFrame owner) {
        this(owner, null);
    }

    /**
     * Constructor that also receives an {@link InventoryManager} so that
     * {@link ProductFoundDialog} can add directly to the inventory after scan.
     *
     * @param owner            parent JFrame
     * @param inventoryManager the shared inventory manager (may be {@code null})
     */
    public BarcodeScannerDialog(JFrame owner, InventoryManager inventoryManager) {
        super(owner, "Barcode Scanner — Hold barcode to camera", true);
        this.inventoryManager = inventoryManager;
        // Initialize lookup service in background so constructor doesn't block
        new Thread(() -> {
            try {
                lookupService = new ProductLookupService();
            } catch (Exception e) {
                System.err.println("ProductLookupService init failed: " + e.getMessage());
            }
        }, "lookup-init").start();

        setSize(520, 450);
        setResizable(false);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        add(buildNorth(),  BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildSouth(),  BorderLayout.SOUTH);

        // Clean up camera on any close path
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { closeWebcam(); dispose(); }
            @Override public void windowClosed(WindowEvent e)  { closeWebcam(); }
        });
    }

    // ── Panel builders ────────────────────────────────────────────────────────

    private JPanel buildNorth() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(DARK_BG);
        p.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));

        JLabel title = new JLabel("Hold barcode in front of camera");
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        title.setForeground(Color.WHITE);
        title.setAlignmentX(CENTER_ALIGNMENT);

        JLabel sub = new JLabel("Supports EAN-13 · UPC-A · CODE-128 · QR Code");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sub.setForeground(new Color(0x9fd3b8));
        sub.setAlignmentX(CENTER_ALIGNMENT);

        p.add(title);
        p.add(Box.createRigidArea(new Dimension(0, 3)));
        p.add(sub);
        
        // Placeholder for resolution label
        p.setName("northPanel");
        return p;
    }

    private void updateResolutionLabel() {
        if (webcam == null) return;
        Dimension actualSize = webcam.getViewSize();
        JLabel resLabel = new JLabel(
            "Camera: " + actualSize.width + "x" + actualSize.height + " @ 30 FPS",
            SwingConstants.CENTER
        );
        resLabel.setFont(new Font("Arial", Font.PLAIN, 10));
        resLabel.setForeground(Color.GRAY);
        resLabel.setAlignmentX(CENTER_ALIGNMENT);

        // Find the north panel and add label
        JPanel north = (JPanel) ((BorderLayout)getContentPane().getLayout()).getLayoutComponent(BorderLayout.NORTH);
        if (north != null) {
            north.add(Box.createRigidArea(new Dimension(0, 2)));
            north.add(resLabel);
            north.revalidate();
        }
    }

    private JPanel buildCenter() {
        centerHolder.setBackground(new Color(0x1a2e23));
        centerHolder.setPreferredSize(new Dimension(520, 310));

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

            // Step 5 — Create panel AFTER resolution set
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

            centerHolder.add(webcamPanel, BorderLayout.CENTER);

            // Update North panel with actual resolution label (Fix 7)
            updateResolutionLabel();

            // Start scanning as soon as the dialog is shown
            SwingUtilities.invokeLater(this::startScanTimer);

        } catch (Exception ex) {
            // No webcam attached — show graceful message
            JLabel msg = new JLabel(
                "<html><center>"
                + "<font color='#f87171' size='5'>⚠</font><br><br>"
                + "<font color='#f87171'><b>No webcam detected</b></font><br>"
                + "<font color='#9fd3b8'>Use the Upload Image button below</font>"
                + "</center></html>");
            msg.setHorizontalAlignment(SwingConstants.CENTER);
            centerHolder.add(msg, BorderLayout.CENTER);

            // Show the error dialog after the constructor finishes (non-blocking)
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this,
                    "No webcam detected.\nPlease use the Upload Image button instead.",
                    "Webcam Not Found", JOptionPane.WARNING_MESSAGE));
        }

        return centerHolder;
    }

    private JPanel buildSouth() {
        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.setBackground(SOUTH_BG);
        south.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xd1e0d7)),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)));

        // Result field
        resultField.setEditable(false);
        resultField.setFont(new Font("Segoe UI", Font.BOLD, 12));
        resultField.setForeground(new Color(0x6b7280));
        resultField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xc5d9ce)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        resultField.setAlignmentX(LEFT_ALIGNMENT);
        resultField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        south.add(resultField);
        south.add(Box.createRigidArea(new Dimension(0, 8)));

        // Button row
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        btns.setBackground(SOUTH_BG);
        btns.setAlignmentX(LEFT_ALIGNMENT);
        btns.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        JButton scanAgainBtn = btn("↺  Scan Again",    new Color(0x546a5e), Color.WHITE);
        JButton uploadBtn    = btn("📁  Upload Image",  new Color(0x374151), Color.WHITE);
        JButton cancelBtn    = btn("✕  Cancel",         new Color(0xe5e7eb), new Color(0x374151));

        // Style the confirm button
        confirmBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        confirmBtn.setBackground(ACCENT);
        confirmBtn.setForeground(Color.WHITE);
        confirmBtn.setBorderPainted(false);
        confirmBtn.setFocusPainted(false);
        confirmBtn.setOpaque(true);
        confirmBtn.setBorder(BorderFactory.createEmptyBorder(7, 16, 7, 16));
        confirmBtn.setEnabled(false);

        scanAgainBtn.addActionListener(e -> restartScan());
        uploadBtn.addActionListener(e    -> uploadAndDecode());
        cancelBtn.addActionListener(e    -> { closeWebcam(); scannedText = null; dispose(); });
        confirmBtn.addActionListener(e   -> { closeWebcam(); dispose(); });

        btns.add(scanAgainBtn);
        btns.add(uploadBtn);
        btns.add(confirmBtn);
        btns.add(cancelBtn);
        south.add(btns);

        return south;
    }

    // ── Scanning logic ────────────────────────────────────────────────────────

    private void startScanTimer() {
        if (webcam == null) return;
        if (scanTimer != null && scanTimer.isRunning()) scanTimer.stop();

        System.gc(); // hint GC before camera loop

        scanTimer = new Timer(300, e -> {
            if (!webcam.isOpen() || !webcamPanel.isStarted()) return;

            BufferedImage frame = webcam.getImage();
            if (frame == null) return;

            // Log resolution for the first 3 frames
            if (frameCount < 3) {
                System.out.println("Frame: " + frame.getWidth() + "x" + frame.getHeight()
                        + " (" + frameCount + ")" +
                        (ZBarScanner.isZBarAvailable() ? " [ZBar]" : " [ZXing]"));
                frameCount++;
            }

            // Decode via ZBar (if installed) or enhanced ZXing fallback
            String text = ZBarScanner.decode(frame);
            frame = null; // help GC

            if (text != null && !text.isBlank()) {
                onBarcodeDetected(text.trim());
            }
        });
        scanTimer.start();
    }



    private void onBarcodeDetected(String barcodeNumber) {
        if (scanTimer != null) scanTimer.stop();
        scannedText = barcodeNumber;

        SwingUtilities.invokeLater(() -> {
            Toolkit.getDefaultToolkit().beep();
            flashGreenBorder();
            resultField.setText("Looking up: " + barcodeNumber + " …");
            resultField.setForeground(new Color(0x1d4ed8));
            confirmBtn.setEnabled(false);
        });

        // Run product lookup in background — never freeze the EDT
        SwingWorker<ProductDetails, Void> worker = new SwingWorker<>() {
            @Override
            protected ProductDetails doInBackground() {
                // Wait briefly for lookupService init if still loading
                for (int i = 0; i < 30 && lookupService == null; i++) {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                }
                if (lookupService == null) return null;
                return lookupService.lookupByBarcode(barcodeNumber);
            }

            @Override
            protected void done() {
                try {
                    ProductDetails details = get();
                    handleLookupResult(barcodeNumber, details);
                } catch (Exception e) {
                    handleLookupResult(barcodeNumber, null);
                }
            }
        };
        worker.execute();
    }

    /**
     * Called on the EDT once the background lookup finishes.
     * Closes this scanner dialog and opens {@link ProductFoundDialog}.
     */
    private void handleLookupResult(String barcode, ProductDetails details) {
        // Retrieve owner before dispose() clears it
        JFrame owner = (JFrame) getOwner();
        InventoryManager mgr = inventoryManager;

        // If mgr is null, try to get it from the owner MainFrame
        if (mgr == null && owner instanceof MainFrame mf) {
            mgr = mf.getManager();
        }

        // Reset scannedText so that callers like MainFrame.openScannerDirectly()
        // see null and do not try to set the raw barcode as a product name.
        // (ProductFoundDialog handles the full add flow.)
        scannedText = null;

        closeWebcam();
        dispose(); // close scanner / camera


        final InventoryManager finalMgr = mgr;
        final JFrame           finalOwner = owner;
        SwingUtilities.invokeLater(() -> {
            ProductFoundDialog dialog = new ProductFoundDialog(
                    finalOwner, barcode, details, finalMgr);
            dialog.setVisible(true);
        });
    }

    private void restartScan() {
        scannedText = null;
        frameCount = 0; // Fix 4: Reset frame count
        resultField.setText("Scanning…");
        resultField.setForeground(new Color(0x6b7280));
        confirmBtn.setEnabled(false);
        centerHolder.setBorder(null);
        startScanTimer();
    }

    private void uploadAndDecode() {
        if (scanTimer != null) scanTimer.stop();

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select barcode image file");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Images (jpg, png, bmp)", "jpg", "jpeg", "png", "bmp"));

        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                BufferedImage img = ImageIO.read(fc.getSelectedFile());
                if (img == null) {
                    JOptionPane.showMessageDialog(this, "Cannot read this image file.",
                            "Error", JOptionPane.ERROR_MESSAGE);
                    startScanTimer();
                    return;
                }
                String decoded = BarcodeService.decodeBarcode(img);
                if (decoded != null) {
                    onBarcodeDetected(decoded);
                } else {
                    JOptionPane.showMessageDialog(this,
                            "No barcode found in this image.\nTry a clearer photo.",
                            "Not Detected", JOptionPane.INFORMATION_MESSAGE);
                    startScanTimer();
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error reading image: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                startScanTimer();
            }
        } else {
            startScanTimer(); // user cancelled chooser — resume live scan
        }
    }

    private void flashGreenBorder() {
        centerHolder.setBorder(BorderFactory.createLineBorder(ACCENT, 4));
        new Timer(900, e -> {
            centerHolder.setBorder(null);
            ((Timer) e.getSource()).stop();
        }).start();
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



    // ── Public API (called by FormPanel + MainFrame — do NOT rename) ──────────

    /** @return scanned product name, or {@code null} if the dialog was cancelled. */
    public String getScannedText() { return scannedText; }

    /** Alias for {@link #getScannedText()}. */
    public String getScannedProductName() { return scannedText; }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
