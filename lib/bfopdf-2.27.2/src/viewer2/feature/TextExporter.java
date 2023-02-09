// $Id: TextExporter.java 39802 2021-04-21 15:32:05Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.util.*;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import org.faceless.util.SoftInterruptibleThread;

/**
 * A subclass of {@link Exporter} that handles saving a PDF as a Text file.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">TextExporter</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.10.3
 */
public class TextExporter extends Exporter {

    private FileFilter filefilter;

    public TextExporter() {
        super("TextExporter");
        filefilter = new FileFilter() {
            public boolean accept(File file) {
                return file.isDirectory() || file.getName().toLowerCase().endsWith(".txt");
            }
            public String getDescription() {
                return UIManager.getString("PDFViewer.FilesText");
            }
        };
    }

    public FileFilter getFileFilter() {
        return filefilter;
    }

    public String getFileSuffix() {
        return "txt";
    }

    public boolean isEnabled(DocumentPanel docpanel) {
        return docpanel.hasPermission("Extract");
    }

    public ExporterTask getExporter(final DocumentPanel docpanel, final PDF pdf, final JComponent c, final OutputStream out) {
        return new ExporterTask() {
            private float progress;
            public final boolean isCancellable() {
                return true;
            }
            public float getProgress() {
                return progress;
            }
            public void savePDF() throws IOException {
                preProcessPDF(pdf);
                postProcessPDF(pdf);
                OutputStreamWriter writer = new OutputStreamWriter(out, "UTF-8");
                SoftInterruptibleThread ct = Thread.currentThread() instanceof SoftInterruptibleThread ? (SoftInterruptibleThread)Thread.currentThread() : null;
                for (int i=0;(ct == null || !ct.isSoftInterrupted()) && i < docpanel.getNumberOfPages();i++) {
                    PageExtractor extractor = docpanel.getPageExtractor(docpanel.getPage(i));
                    writer.write(extractor.getTextAsStringBuffer().toString());
                    progress = (float)i / docpanel.getNumberOfPages();
                }
                writer.close();
                if (ct != null && ct.isSoftInterrupted()) {
                    throw new IOException("Export as text interrupted");
                }
            }
        };
    }

}
