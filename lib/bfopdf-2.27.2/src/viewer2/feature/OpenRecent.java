// $Id: OpenRecent.java 29231 2018-08-11 23:05:18Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * This feature adds a most-recently-used menu listing the <i>n</i> most
 * recently opened documents. Actuating the menu opens the associated document
 * in the viewer.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">OpenRecent</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.10.4
 */
public class OpenRecent extends ViewerFeature {

    private int max;
    private Preferences preferences;
    private ActionListener opener;
    private JMenu openrecentmenu;

    /**
     * Create a new "Open Recent" menu with a default number of items
     */
    public OpenRecent() {
        this(4);
    }

    /**
     * Create a new "Open Recent" menu with the specified number of items
     */
    public OpenRecent(int max) {
        super("OpenRecent");
        this.max = max;
    }

    public void initialize(final PDFViewer viewer) {
        super.initialize(viewer);
        preferences = viewer.getPreferences();
        opener = new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                String path = event.getActionCommand();
                viewer.loadPDF(new File(path));
            }
        };
        if (preferences != null) {
            for (int i=0;i<max;i++) {
                String path = preferences.get("features.OpenRecent."+i, null);
                if (path == null) {       // Check old naming convention, and if found migrate it.
                    path = preferences.get("File/mru"+i, null);
                    if (path != null) {
                        preferences.put("features.OpenRecent."+i, path);
                        preferences.remove("File/mru"+i);
                    }
                }
                if (path != null) {
                    // Name is temporary but must be unique - fixed in cleanup
                    JMenuItem item = viewer.setMenu("File\tOpenRecent\t"+i, (char)0, false, opener);
                    openrecentmenu = (JMenu)((JPopupMenu)item.getParent()).getInvoker();
                    item.setActionCommand(path);
                }
            }
        }
        if (openrecentmenu == null) {
            JMenuItem item = viewer.setMenu("File\tOpenRecent\tnull", (char)0, false, null);
            openrecentmenu = (JMenu)((JPopupMenu)item.getParent()).getInvoker();
            openrecentmenu.remove(item);
        }
        cleanup();
    }

    /**
     * This method is run by the PDFViewer when the specified file is opened.
     * @param viewer the viewer
     * @param file the File that was opened
     */
    public void opened(PDFViewer viewer, File file) {
        String path = file.getPath();
        // delete existing item that corresponds to this file
        int num = openrecentmenu.getItemCount();
        boolean found = false;
        for (int i=0;!found && i<num;i++) {
            JMenuItem item = openrecentmenu.getItem(i);
            if (item.getActionCommand().equals(path)) {
                openrecentmenu.remove(item);
                openrecentmenu.insert(item, 0);
                found = true;
            }
        }
        if (!found) {
            JMenuItem item = new JMenuItem(file.getName());
            item.setActionCommand(path);
            item.addActionListener(opener);
            openrecentmenu.insert(item, 0);
            // Delete any surplus shortcuts
            while (openrecentmenu.getItemCount() > max) {
                openrecentmenu.remove(max);
            }
            num = openrecentmenu.getItemCount();
        }
        cleanup();

        if (preferences != null) {
            num = openrecentmenu.getItemCount();
            for (int i=0;i<num;i++) {
                preferences.put("features.OpenRecent." + i, openrecentmenu.getItem(i).getActionCommand());
            }
        }
    }

    /**
     * Ensure all names are unique and accelerators are assigned
     */
    private void cleanup() {
        int num = openrecentmenu.getItemCount();
        for (int i=0;i<num;i++) {
            JMenuItem item = openrecentmenu.getItem(i);
            int mask = Util.getMenuShortcutKeyMask(openrecentmenu);
            item.setAccelerator(KeyStroke.getKeyStroke((char)('0'+i), mask, false));
            String path = item.getActionCommand();
            File file = new File(path);
            int count = 0;
            for (int j=0;j<num;j++) {
                String otherpath = openrecentmenu.getItem(j).getActionCommand();
                File otherfile = new File(otherpath);
                if (file.getName().equals(otherfile.getName())) {
                    count++;
                }
            }
            String newname = count == 1 ? file.getName() : file.getPath();
            if (!newname.equals(item.getText())) {
                item.setText(newname);
            }
        }
    }

}
