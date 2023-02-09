// $Id: Export.java 41659 2021-11-10 09:23:06Z mike $

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
 * <p>
 * Create a button that will open a dialog allowing the PDF to be saved to disk in a 
 * non-PDF format (eg TIFF or plain Text). This functionality is normally part of the
 * {@link SaveAs} feature, but it can be disabled - if it is, this feature can be
 * added to re-enable non-PDF export formats, just under a different menu item.
 * </p><p>
 * Consequently this feature is not enabled by default.
 * </p>
 * <div class="initparams">
 * The following <a href="../doc-files/initparams.html">initialization parameters</a> can be specified to configure this feature.
 * <table summary="">
 * <tr><th>promptOnOverwrite</th><td>true to prompt before overwriting files, false otherwise (the default)</td></tr>
 * </table>
 * </div>
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">Export</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.17
 */
public class Export extends SaveAs {

    public Export() {
        super("Export");
        setMenu("File\tExport...");
    }

    @Override public List<Exporter> getExporters(ViewerEvent event) {
        List<Exporter> exporters = new ArrayList<Exporter>();
        for (ViewerFeature feature : event.getViewer().getFeatures()) {
            if (feature instanceof Exporter && !(feature instanceof PDFExporter) && ((Exporter)feature).isEnabled(event.getDocumentPanel())) {
                exporters.add((Exporter)feature);
            }
        }
        return exporters;
    }

    public void action(ViewerEvent event) {
        List<Exporter> exporters = getExporters(event);
        Save.save(null, event, exporters, true, isPromptOnOverwrite());
        // Export doesn't clear dirty flag
    }

}
