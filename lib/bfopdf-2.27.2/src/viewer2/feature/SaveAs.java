// $Id: SaveAs.java 41659 2021-11-10 09:23:06Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.viewer2.util.DialogPanel;
import org.faceless.pdf2.*;
import org.faceless.util.Langolier;
import javax.swing.*;
import javax.swing.plaf.basic.BasicFileChooserUI;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.filechooser.FileFilter;
import java.io.*;

/**
 * Create a button that will open a dialog allowing the PDF to be saved to disk.
 * By default, any {@link Exporter} formats included as features in the Viewer
 * will be presented as options. However it's possible to change this by either
 * overriding the {@link #getExporters(ViewerEvent)} method, or by use of the
 * <code>onlyPDF</code> initialization parameter. The {@link Export} feature
 * can be a useful addition in this case.
 *
 * <div class="initparams">
 * The following <a href="../doc-files/initparams.html">initialization parameters</a> can be specified to configure this feature.
 * <table summary="">
 * <tr><th>promptOnOverwrite</th><td>true to prompt before overwriting files, false otherwise (the default)</td></tr>
 * <tr><th>disableUnlessDirty</th><td>true to disable this feature until the PDF has been marked as "dirty" (ie it has been altered), false to always enable this feature (the defualt)</td></tr>
 * <tr><th>onlyPDF</th><td>true limit the "save as" dialog to only allow the PDF format</td></tr>
 * </table>
 * </div>
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">SaveAs</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.17
 */
public class SaveAs extends ViewerWidget implements DocumentPanelListener {

    private Action action;
    private boolean overwriteprompt = false;
    private boolean onlypdf = false;

    public SaveAs() {
        this("SaveAs");
        setMenu("File\tSaveAs...");
    }

    SaveAs(String name) {
        super(name);
        action = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                SaveAs.super.createActionListener().actionPerformed(e);
            }
        };
    }

    protected ActionListener createActionListener() {
        return action;
    }

    /**
     * Return the List of Exporters that will be presented to the user
     * on save.
     * @param event the event the returned list will apply to
     */
    public List<Exporter> getExporters(ViewerEvent event) {
        List<Exporter> exporters = new ArrayList<Exporter>();
        for (ViewerFeature feature : event.getViewer().getFeatures()) {
            if (feature instanceof Exporter && ((Exporter)feature).isEnabled(event.getDocumentPanel())) {
                if (onlypdf && feature instanceof PDFExporter) {
                    exporters.add((Exporter)feature);
                    break;
                } else {
                    exporters.add((Exporter)feature);
                }
            }
        }
        if (exporters.isEmpty()) {
            exporters.add(new PDFExporter());
        }
        return exporters;
    }

    public void action(ViewerEvent event) {
        List<Exporter> exporters = getExporters(event);
        boolean success = Save.save(null, event, exporters, true, isPromptOnOverwrite());
        if (success) {
            event.getDocumentPanel().setDirty(false);
        }
    }

    public boolean isEnabledByDefault() {
        return Util.hasFilePermission();
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        String val = getFeatureProperty(viewer, "promptOnOverwrite");
        if (val != null) {
            setPromptOnOverwrite("true".equals(val));
        }
        val = getFeatureProperty(viewer, "onlyPDF");
        if (val != null) {
            onlypdf = "true".equals(val);
        }
        viewer.addDocumentPanelListener(this);
    }

    public void documentUpdated(DocumentPanelEvent event) {
        String type = event.getType();
        DocumentPanel docpanel = event.getDocumentPanel();
        if (type.equals("activated") && docpanel != null) {
            action.setEnabled(docpanel.hasPermission("Save"));
        } else if (type.equals("permissionChanged") && docpanel != null && docpanel == getViewer().getActiveDocumentPanel()) {
            action.setEnabled(docpanel.hasPermission("Save"));
        } else if (type.equals("deactivated")) {
            action.setEnabled(false);
        }
    }

    /**
     * Set whether this feature should prompt before overwriting a file
     * @param prompt whether to prompt before overwriting a file (detault is false)
     * @since 2.11.25
     */
    public void setPromptOnOverwrite(boolean prompt) {
        this.overwriteprompt = prompt;
    }

    /**
     * Indicates whether this feature should prompt before overwriting a file
     * @since 2.18
     */
    public boolean isPromptOnOverwrite() {
        return overwriteprompt;
    }

}
