// $Id: PDFExporter.java 35489 2020-03-06 11:57:14Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.util.*;
import java.io.*;

/**
 * A subclass of Exporter that handles exporting a PDF as a PDF file.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">PDFExporter</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.10.2
 */
public class PDFExporter extends Exporter
{
    private FileFilter filefilter;

    /**
     * Create a new PDFExporter
     */
    public PDFExporter() {
        super("PDFExporter");
        filefilter = new FileFilter() {
            public boolean accept(File file) {
                return file.isDirectory() || file.getName().toLowerCase().endsWith(".pdf");
            }
            public String getDescription() {
                return UIManager.getString("PDFViewer.FilesPDF");
            }
        };
    }

    public FileFilter getFileFilter() {
        return filefilter;
    }

    public String getFileSuffix() {
        return "pdf";
    }

    public boolean isEnabled(DocumentPanel docpanel) {
        return docpanel.hasPermission("Save");
    }

    public ExporterTask getExporter(final DocumentPanel docpanel, final PDF pdf, final JComponent c, final OutputStream out) {
        return new ExporterTask() {
            public final boolean isCancellable() {
                return false;
            }
            public float getProgress() {
                return pdf.getRenderProgress();
            }
            public void savePDF() throws IOException {
                Collection<Object> c = null;
                if (docpanel != null) {
                    c = new HashSet<Object>(docpanel.getSidePanels());
                    if (docpanel.getViewer()!=null) {
                        c.addAll(Arrays.asList(docpanel.getViewer().getFeatures()));
                    }
                    for (Iterator<Object> i = c.iterator();i.hasNext();) {
                        Object o = i.next();
                        if (o instanceof PDFBackgroundTask) {
                            try {
                                ((PDFBackgroundTask)o).pause();
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            i.remove();
                        }
                    }
                    docpanel.getJSManager().runEventDocWillSave(docpanel);
                }
                preProcessPDF(pdf);
                pdf.render(out);
                postProcessPDF(pdf);
                out.close();
                if (docpanel != null) {
                    docpanel.setDirty(false);
                    docpanel.getJSManager().runEventDocDidSave(docpanel);
                    for (Iterator<Object> i = c.iterator();i.hasNext();) {
                        ((PDFBackgroundTask)i.next()).unpause();
                    }
                }
            }
        };
    }
}
