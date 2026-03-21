package com.inventory.ui;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Thin bar at the bottom of the window with status message and last-saved timestamp. */
public class StatusBar extends JPanel {

    private static final Color BG     = new Color(0x2d4a38);
    private static final Color FG     = new Color(0xd1e8da);
    private static final Color FG_DIM = new Color(0x7aab90);
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm a");

    private final JLabel leftLabel  = new JLabel(" Ready");
    private final JLabel rightLabel = new JLabel("");

    public StatusBar() {
        setLayout(new BorderLayout());
        setBackground(BG);
        setPreferredSize(new Dimension(0, 26));
        setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));

        leftLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        leftLabel.setForeground(FG);
        rightLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        rightLabel.setForeground(FG_DIM);

        add(leftLabel,  BorderLayout.WEST);
        add(rightLabel, BorderLayout.EAST);
    }

    public void setMessage(String msg)  { leftLabel.setText(" " + msg); }
    public void setSaveTime() {
        rightLabel.setText("Last saved: " + LocalDateTime.now().format(FMT) + "  ");
    }
}
