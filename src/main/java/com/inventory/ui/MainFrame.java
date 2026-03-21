package com.inventory.ui;

import com.inventory.controller.InventoryManager;
import com.inventory.model.Product;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Root JFrame — 1000×620, non-resizable.
 * Owns every panel and wires all cross-panel events together.
 */
public class MainFrame extends JFrame {

    private static final String DEFAULT_FILE = "inventory.csv";

    // Shared model
    private final InventoryManager manager = new InventoryManager();

    // Panels
    private SidebarPanel       sidebar;
    private StatsPanel         stats;
    private ToolbarPanel       toolbar;
    private ProductTablePanel  tablePanel;
    private FormPanel          formPanel;
    private StatusBar          statusBar;

    // Currently selected product (for edit / delete)
    private Product selectedProduct = null;

    // ── Constructor ───────────────────────────────────────────────────────────

    public MainFrame() {
        setTitle("Grocery Inventory Management System");
        setSize(1000, 620);
        setLocationRelativeTo(null);
        setResizable(false);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        buildUI();
        buildMenuBar();
        bindKeyboardShortcuts();
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                saveInventory();   // auto-save on close
                dispose();
                System.exit(0);
            }
        });

        autoLoadCsv();
        refreshAll();
    }

    // ── UI construction ───────────────────────────────────────────────────────

    private void buildUI() {
        setLayout(new BorderLayout());

        sidebar    = new SidebarPanel(this);
        stats      = new StatsPanel();
        toolbar    = new ToolbarPanel(this);
        tablePanel = new ProductTablePanel(this);
        formPanel  = new FormPanel(this);
        statusBar  = new StatusBar();

        // Centre column = stats + [toolbar + table]
        JPanel centre = new JPanel(new BorderLayout());
        centre.setBackground(Color.WHITE);
        centre.add(stats, BorderLayout.NORTH);

        JPanel tableArea = new JPanel(new BorderLayout());
        tableArea.add(toolbar,    BorderLayout.NORTH);
        tableArea.add(tablePanel, BorderLayout.CENTER);
        centre.add(tableArea, BorderLayout.CENTER);

        add(sidebar,   BorderLayout.WEST);
        add(centre,    BorderLayout.CENTER);
        add(formPanel, BorderLayout.EAST);
        add(statusBar, BorderLayout.SOUTH);
    }

    private void buildMenuBar() {
        JMenuBar mb = new JMenuBar();
        mb.setBackground(new Color(0x2d4a38));
        mb.setBorder(BorderFactory.createEmptyBorder());

        mb.add(fileMenu());
        mb.add(inventoryMenu());
        mb.add(reportsMenu());
        mb.add(sortMenu());
        mb.add(helpMenu());
        setJMenuBar(mb);
    }

    private JMenu fileMenu() {
        JMenu m = menu("File");
        JMenuItem save = item("Save",        KeyEvent.VK_S, ActionEvent.CTRL_MASK);
        JMenuItem load = item("Load",        KeyEvent.VK_L, ActionEvent.CTRL_MASK);
        JMenuItem exit = new JMenuItem("Exit");

        save.addActionListener(e -> saveInventory());
        load.addActionListener(e -> loadInventory());
        exit.addActionListener(e -> { saveInventory(); System.exit(0); });

        m.add(save); m.add(load); m.addSeparator(); m.add(exit);
        return m;
    }

    private JMenu inventoryMenu() {
        JMenu m = menu("Inventory");
        JMenuItem add      = new JMenuItem("Add Product");
        JMenuItem edit     = new JMenuItem("Edit Product");
        JMenuItem remove   = new JMenuItem("Remove Product");
        JMenuItem scanAdd  = new JMenuItem("Scan \u0026 Add Product");
        JMenuItem scanRem  = new JMenuItem("Scan \u0026 Remove Product");
        JMenuItem genLabel = new JMenuItem("Generate Barcode Label");

        add.addActionListener(e      -> startAddMode());
        edit.addActionListener(e     -> startEditMode());
        remove.addActionListener(e   -> removeSelectedProduct());
        scanAdd.addActionListener(e  -> openScannerDirectly());
        scanRem.addActionListener(e  -> openRemoveByBarcode());
        genLabel.addActionListener(e -> showBarcodeGenerator());

        m.add(add); m.add(edit); m.add(remove);
        m.addSeparator();
        m.add(scanAdd); m.add(scanRem); m.add(genLabel);
        return m;
    }

    private JMenu reportsMenu() {
        JMenu m = menu("Reports");
        JMenuItem exp  = new JMenuItem("Expiry Report");
        JMenuItem low  = new JMenuItem("Low Stock");
        JMenuItem disc = new JMenuItem("Discount Report");

        exp.addActionListener(e  -> showExpiryReport());
        low.addActionListener(e  -> showLowStockDialog());
        disc.addActionListener(e -> showDiscountReport());

        m.add(exp); m.add(low); m.add(disc);
        return m;
    }

    private JMenu sortMenu() {
        JMenu m = menu("Sort");
        JMenuItem n = new JMenuItem("By Name");
        JMenuItem p = new JMenuItem("By Price");
        JMenuItem q = new JMenuItem("By Quantity");
        JMenuItem x = new JMenuItem("By Expiry");

        n.addActionListener(e -> sortByName());
        p.addActionListener(e -> sortByPrice());
        q.addActionListener(e -> sortByQuantity());
        x.addActionListener(e -> sortByExpiry());

        m.add(n); m.add(p); m.add(q); m.add(x);
        return m;
    }

    private JMenu helpMenu() {
        JMenu m = menu("Help");
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Grocery Inventory Management System\nv1.0  — Swing GUI Edition",
                "About", JOptionPane.INFORMATION_MESSAGE));
        m.add(about);
        return m;
    }

    private static JMenu menu(String title) {
        JMenu m = new JMenu(title);
        m.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        m.setForeground(new Color(0xe2f0e9));
        return m;
    }

    private static JMenuItem item(String title, int keyCode, int modifier) {
        JMenuItem i = new JMenuItem(title);
        i.setAccelerator(KeyStroke.getKeyStroke(keyCode, modifier));
        return i;
    }

    // ── Keyboard shortcuts ────────────────────────────────────────────────────

    private void bindKeyboardShortcuts() {
        JPanel root = (JPanel) getContentPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "save");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK), "add");

        root.getActionMap().put("save", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { saveInventory(); }
        });
        root.getActionMap().put("add", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { startAddMode(); }
        });
    }

    // ── Public event handlers (called by panels) ──────────────────────────────

    /** Applies current search text + category combo to the table. */
    public void applyFilter() {
        String search   = toolbar.getSearchText().toLowerCase().trim();
        String category = toolbar.getSelectedCategory();

        ArrayList<Product> all      = manager.getAll();
        ArrayList<Product> filtered = new ArrayList<>();
        for (Product p : all) {
            boolean matchName = search.isEmpty()
                    || p.getName().toLowerCase().contains(search)
                    || p.getCategory().toLowerCase().contains(search);
            boolean matchCat  = "All".equals(category)
                    || p.getCategory().equalsIgnoreCase(category);
            if (matchName && matchCat) filtered.add(p);
        }
        tablePanel.refreshTable(filtered);
    }

    /** Full refresh: table, stats, status bar. */
    public void refreshAll() {
        applyFilter();
        stats.refreshStats(manager.getAll());
        statusBar.setMessage(manager.size() + " products loaded — " + DEFAULT_FILE);
    }

    /** Called when a row is clicked in the table. */
    public void onProductSelected(Product p) {
        selectedProduct = p;
        formPanel.populate(p);
    }

    /** Switch form to "Add new" mode. */
    public void startAddMode() {
        selectedProduct = null;
        tablePanel.clearSelection();
        formPanel.clear();
        formPanel.focusName();
    }

    /** Switch form to edit mode (uses current selection). */
    public void startEditMode() {
        if (selectedProduct == null) {
            JOptionPane.showMessageDialog(this,
                    "Please select a product in the table first.", "", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** Remove the currently selected product (with confirm dialog). */
    public void removeSelectedProduct() {
        if (selectedProduct == null) {
            JOptionPane.showMessageDialog(this, "No product selected.",
                    "Delete", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int res = JOptionPane.showConfirmDialog(this,
                "Remove \"" + selectedProduct.getName() + "\"?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (res == JOptionPane.YES_OPTION) {
            manager.removeById(selectedProduct.getId());
            selectedProduct = null;
            formPanel.clear();
            tablePanel.clearSelection();
            refreshAll();
            statusBar.setMessage("Product removed.");
        }
    }

    /** Called by FormPanel when the user clicks Save. */
    public void saveProduct(Product p) {
        boolean existed = manager.findById(p.getId()).isPresent();
        if (existed) manager.updateProduct(p);
        else         manager.addProduct(p);

        formPanel.clear();
        selectedProduct = null;
        tablePanel.clearSelection();
        refreshAll();
        statusBar.setMessage("Product \"" + p.getName() + "\" saved.");
    }

    // ── Sort delegates ────────────────────────────────────────────────────────

    public void sortByName()     { manager.sortByName();     refreshAll(); }
    public void sortByPrice()    { manager.sortByPrice();    refreshAll(); }
    public void sortByQuantity() { manager.sortByQuantity(); refreshAll(); }
    public void sortByExpiry()   { manager.sortByExpiry();   refreshAll(); }

    // ── File I/O ──────────────────────────────────────────────────────────────

    public void saveInventory() {
        try {
            manager.saveToFile(DEFAULT_FILE);
            statusBar.setSaveTime();
            statusBar.setMessage("Saved " + manager.size() + " products → " + DEFAULT_FILE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Save failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void loadInventory() {
        JFileChooser fc = new JFileChooser(new File("."));
        fc.setDialogTitle("Load Inventory CSV");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            manager.loadFromFile(fc.getSelectedFile().getAbsolutePath());
            refreshAll();
            statusBar.setMessage("Loaded " + manager.size() + " products.");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Load failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Report dialogs ────────────────────────────────────────────────────────

    public void showExpiryReport()   { new ExpiryReportDialog(this, manager).setVisible(true); }
    public void showLowStockDialog() { new LowStockDialog(this, manager).setVisible(true); }
    public void showDiscountReport() { new DiscountReportDialog(this, manager).setVisible(true); }

    // Opens scanner and if something is scanned, pre-fills the add form
    public void openScannerDirectly() {
        BarcodeScannerDialog dlg = new BarcodeScannerDialog(this);
        dlg.setVisible(true);
        String scanned = dlg.getScannedText();
        if (scanned == null || scanned.isBlank()) return;
        startAddMode();
        // Let the form panel know: delegate via onProductScanned
        formPanel.setNameText(scanned);
        // Check if it already exists
        java.util.List<com.inventory.model.Product> found = manager.searchByName(scanned);
        if (!found.isEmpty()) {
            onProductSelected(found.get(0));
        }
    }

    // Opens the 2-tab remove dialog
    public void openRemoveByBarcode() {
        new RemoveByBarcodeDialog(this, manager).setVisible(true);
    }

    // Opens barcode generator for the currently selected product (or nothing pre-selected)
    public void showBarcodeGenerator() {
        String preselect = (selectedProduct != null) ? selectedProduct.getName() : null;
        new BarcodeGeneratorDialog(this, manager, preselect).setVisible(true);
    }

    // ── Toolbar helper ────────────────────────────────────────────────────────

    public void clearFilter() { toolbar.clearSearch(); }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public InventoryManager getManager() { return manager; }
    public Product getSelectedProduct()  { return selectedProduct; }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void autoLoadCsv() {
        File f = new File(DEFAULT_FILE);
        if (!f.exists()) return;
        try {
            manager.loadFromFile(DEFAULT_FILE);
        } catch (IOException ignored) { /* first run — no file yet */ }
    }
}
