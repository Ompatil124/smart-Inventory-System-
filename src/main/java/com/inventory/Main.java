package com.inventory;

import com.inventory.ui.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;

/**
 * Application entry point — applies Nimbus Look-and-Feel,
 * then launches the Swing UI on the Event Dispatch Thread.
 */
public class Main {

    public static void main(String[] args) {

        // ── Nimbus Look-and-Feel ──────────────────────────────────────────────
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");

            // Tint the Nimbus palette toward the dark-green theme
            UIManager.put("nimbusBase",     new Color(0x4a7c59));
            UIManager.put("nimbusBlueGrey", new Color(0x6b8f7a));
            UIManager.put("control",        new Color(0xf0f4f2));
            UIManager.put("text",           new Color(0x1a2e23));
            UIManager.put("nimbusSelectionBackground", new Color(0x4a7c59));

        } catch (Exception e) {
            // Graceful fallback to system L&F
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) { }
        }

        // ── Launch on the EDT ─────────────────────────────────────────────────
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
