// $Id: Exporter.java 22144 2015-10-21 17:31:29Z mike $

package org.faceless.pdf2.viewer2;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.*;
import org.faceless.pdf2.*;
import org.faceless.pdf2.viewer2.feature.*;
import org.faceless.pdf2.viewer2.util.LongRunningTask;
import java.util.*;
import java.io.*;

/**
 * A type of {@link ViewerFeature} which allows PDF's to be saved in a variety of formats.
 * It's chiefly used with the {@link Save} widget, although it can be
 * used in other contexts if necessary.
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.10.2
 */
public abstract class Exporter extends ViewerFeature {

    /**
     * Create a new Exporter
     * @param name the name of the feature
     */
    public Exporter(String name) {
        super(name);
    }

    /**
     * Get a FileFilter that matches the Files output by this Exporter
     */
    public abstract FileFilter getFileFilter();

    /**
     * Return the suffix of files normally output by this Exporter, such
     * as "pdf", "tif", "jpg" etc.
     */
    public abstract String getFileSuffix();

    /**
     * Return true if this Exporter should be available for this DocumentPanel.
     * The default implementation always returns true.
     * @since 2.10.3
     */
    public boolean isEnabled(DocumentPanel docpanel) {
        return true;
    }

    /**
     * Return a new {@link ExporterTask} that would save a PDF
     * @param panel the DocumentPanel this PDF is being saved from - may be null
     * @param pdf the PDF being saves (not null)
     * @param component the JComponent returned by {@link ExporterTask#getComponent}
     * @param out the OutputStream to write the PDF to
     */
    public abstract ExporterTask getExporter(DocumentPanel panel, PDF pdf, JComponent component, OutputStream out);

    /**
     * This class is a {@link LongRunningTask} that can be run to save a PDF to
     * an OutputStream. Subclasses need to implement the {@link #savePDF} method,
     * which should save the PDF to the OutputStream supplied in the {@link Exporter#getExporter}
     * method.
     * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
     * @since 2.10.2
     */
    public abstract class ExporterTask extends LongRunningTask {

        public ExporterTask() {
            setModal(true);
        }

        public void run() throws IOException {
            savePDF();
            completed();
            Exporter.this.completed();
        }

        /**
         * Save the PDF to the OutputStream
         */
        public abstract void savePDF() throws IOException;

        /**
         * Called when the save has completed - by default, a no-op.
         * Both this and the {@link Exporter#completed} method in
         * the {@link Exporter} will be called on completion, but
         * it may be easier for subclasses to override the other method.
         * @since 2.11.2
         */
        public void completed() {
        }
    }

    /**
     * <p>
     * Return a JComponent which prompts the user for additional information after
     * the initial save dialog. An example might be when saving to a bitmap format -
     * this component could prompt for image resolution and so on. If this method
     * returns <code>null</code> (the default), then no additional component will
     * be displayed on save.
     * </p><p>
     * By default this method calls <code>return getComponent()</code>, which calls
     * the legacy method which was the recommended approach prior to 2.15.1
     * </p>
     * @param panel the DocumentPanel containing the PDF being saved
     * @param file the File being saved to
     * @since 2.15.1
     */
    @SuppressWarnings("deprecated")
    public JComponent getComponent(DocumentPanel panel, File file) {
        return getComponent();
    }

    /**
     * As for {@link #getComponent(DocumentPanel, File)}, but this method takes no DocumentPanel
     * and File arguments. Subclasses should override the {@link #getComponent(DocumentPanel,File)}
     * method instead.
     * @deprecated Subclasses should override {@link #getComponent(DocumentPanel,File)} instead
     */
    @Deprecated public JComponent getComponent() {
        return null;
    }

    /**
     * Given the component returned by {@link #getComponent}, return <code>null</code>
     * if the values are valid or an error message if they're invalid.
     * @param comp the Component returned by {@link #getComponent}
     */
    public String validateComponent(JComponent comp) {
        return null;
    }

    /**
     * Called by {@link ExporterTask#savePDF} just before the PDF is saved,
     * you can override this method for custom processing. By default
     * it's a no-op
     * @since 2.13
     */
    public void preProcessPDF(PDF pdf) {
    }

    /**
     * Called by {@link ExporterTask#savePDF} just after the PDF is saved,
     * you can override this method for custom processing. By default
     * it's a no-op
     * @since 2.13
     */
    public void postProcessPDF(PDF pdf) {
    }

    /**
     * Called when the save has completed - by default, a no-op.
     * Both this and the {@link ExporterTask#completed} method in
     * the {@link ExporterTask} will be called on completion, and
     * subclasses can override either.
     * @since 2.18.1
     */
    public void completed() {
    }

}
