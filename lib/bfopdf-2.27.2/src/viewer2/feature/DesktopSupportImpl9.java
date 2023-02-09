package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.PDFPage;
import javax.swing.*;
import java.awt.Desktop;
import java.awt.Taskbar;
import java.awt.desktop.*;

/**
 * <p>Desktop classes for Java 9 and later</p>
 * Public becase Java 17 won't let us do reflection nicely
 */
public class DesktopSupportImpl9 {

    public static void initialize(final PDFViewer viewer, boolean menus, String icon) {
        Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
        Taskbar taskbar = Taskbar.isTaskbarSupported() ? Taskbar.getTaskbar() : null;
        if (menus && viewer.getFeature(Menus.class) != null && desktop != null) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");   // Won't hurt on non-macos systems
            JMenu menu = viewer.getMenu("Window");
            if (menu != null && desktop.isSupported(Desktop.Action.APP_ABOUT)) {
                for (int i=0;i<menu.getMenuComponentCount();i++) {
                    final JMenuItem item = menu.getItem(i);
                    if (item.getText().equals(UIManager.get("PDFViewer.About"))) {
                        menu.remove(item);
                        desktop.setAboutHandler(new AboutHandler() {
                            public void handleAbout(AboutEvent event) {
                                item.doClick();
                            }
                        });
                    }
                }
            }
            menu = viewer.getMenu("File");
            if (menu != null && desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
                for (int i=0;i<menu.getMenuComponentCount();i++) {
                    final JMenuItem item = menu.getItem(i);
                    if (item.getText().equals(UIManager.get("PDFViewer.Quit"))) {
                        menu.remove(item);
                        desktop.setQuitHandler(new QuitHandler() {
                            public void handleQuitRequestWith(QuitEvent event, QuitResponse response) {
                                item.doClick();
                            }
                        });
                    }
                }
            }
        }

        if (icon != null && taskbar != null && taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
            try {
                taskbar.setIconImage(new ImageIcon(PDFViewer.class.getResource(icon)).getImage());
            } catch (Throwable e) { }
        }
    }

}
