package com.heytozzz.htzcosmetics.studio;

import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;

/**
 * HtzCosmetics Studio: a desktop app for the mod owner to add, check, test and publish cosmetics without typing
 * commands. Started with {@code gradlew htzStudio} or "HtzCosmetics Studio.bat".
 */
public final class StudioApp {
    private StudioApp() {
    }

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        UIManager.put("ToolTip.background", Theme.CHIP_ON);
        UIManager.put("ToolTip.foreground", Theme.TEXT);
        UIManager.put("ToolTip.font", Theme.FONT);
        UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.PANEL_EDGE), BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        UIManager.put("Panel.background", Theme.BG);
        UIManager.put("ScrollPane.background", Theme.PANEL);
        UIManager.put("Viewport.background", Theme.PANEL);
        ToolTipManager.sharedInstance().setInitialDelay(400);
        Project project;
        try {
            project = Project.locate();
        } catch (IllegalStateException e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "HtzCosmetics Studio", JOptionPane.ERROR_MESSAGE);
            return;
        }
        MainWindow.start(project);
    }
}
