// $Id: Importer.java 41659 2021-11-10 09:23:06Z mike $

package org.faceless.pdf2.viewer2;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import org.faceless.pdf2.*;
import org.faceless.pdf2.viewer2.feature.*;
import org.faceless.pdf2.viewer2.util.LongRunningTask;
import org.faceless.util.Base64;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.net.*;
import java.io.*;

/**
 * <p>
 * A type of {@link ViewerFeature} which takes care of loading a PDF file into
 * a {@link PDFViewer}. Subclasses of this feature are supplied that can load
 * PDF documents directly ({@link PDFImporter}) and by converting bitmap images
 * ({@link ImageImporter}), and further custom loaders can be written.
 * </p><p>
 * Here's an example of how to load a PDF into the viewer from a file
 * <pre class="brush:java">
 * PDFImporter importer = new PDFImporter();
 * Importer.ImporterTask task = importer.getImporter(viewer, file);
 * task.start(viewer, "Loading");
 * </pre>
 * The <code>start</code> method will start a background thread and return immediately.
 * <div class="initparams">
 * The following <a href="../doc-files/initparams.html">initialization parameters</a> can be specified to configure this feature.
 * <table summary="">
 * <tr><th>modal</th><td>If set to not null, the loading wil open a modal dialog, preventing any other actions on the viewer while loading. The default is false.</td></tr>
 * <tr><th>mostrecent</th><td>If set to not false, files loaded with this Importer will not be added to the "most recent" list. The default is "true".</td></tr>
 * </table>
 * </div>
 *
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.10.2
 */
public abstract class Importer extends ViewerFeature {

    /**
     * If true, import tasks from this importer will add the associated file
     * to the most recently used list.
     */
    private boolean addToMRU = true;

    /**
     * Create a new Importer
     * @param name the name of this feature
     */
    protected Importer(String name) {
        super(name);
    }

    @Override public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        if ("false".equals(getFeatureProperty(viewer, "mostrecent"))) {
            setAddToMostRecent(false);
        }
    }

    /**
     * Indicates whether import tasks from this importer will add the
     * associated file to the most recently used list.
     */
    public boolean isAddToMostRecent() {
        return addToMRU;
    }

    /**
     * Sets whether import tasks from this importer will add the
     * associated file to the most recently used list.
     * @param addToMRU if true, add to the list
     */
    public void setAddToMostRecent(boolean addToMRU) {
        this.addToMRU = addToMRU;
    }

    /**
     * Indicates whether this importer loads from a PDF document.
     */
    protected boolean isPDFImporter() {
        return true;
    }

    /**
     * Get a {@link FileFilter} that can be used to choose files
     * acceptable to this Importer.
     */
    public abstract FileFilter getFileFilter();

    /**
     * Return true if this Importer can load the specified file.
     * @param file the File to be checked.
     * @return true if this file can be loaded by this Importer, false otherwise
     */
    public abstract boolean matches(File file) throws IOException;

    /**
     * Returns true if this importer can load a file or stream of the
     * specified MIME Content-Type.
     * @param mimeType the MIME content type
     * @since 2.18
     */
    public boolean canLoad(String mimeType) {
        return false;
    }

    /**
     * Return an {@link ImporterTask} that will load a PDF into the viewer from the specifed File.
     * @param viewer the PDFViewer
     * @param file the FILE to load the PDF from
     * @return an {@link ImporterTask} to load the PDF
     */
    public abstract ImporterTask getImporter(PDFViewer viewer, File file);

    /**
     * Return an {@link ImporterTask} that will load a PDF into the viewer from the specifed InputStream.
     * @param viewer the PDFViewer
     * @param in the InputStream to load the PDF from
     * @param title the title to give that PDF - may be null
     * @param savefile if the PDF is later saved, the file to initialize the path to - may be null.
     * @return an {@link ImporterTask} to load the PDF
     */
    public abstract ImporterTask getImporter(PDFViewer viewer, InputStream in, String title, File savefile);

    /**
     * Return an {@link ImporterTask} that will load a PDF into the viewer from the specified URL.
     * @param viewer the PDFViewer
     * @param url the URL to load the PDF from
     * @return an {@link ImporterTask} to load the PDF
     */
    public ImporterTask getImporter(final PDFViewer viewer, final URL url) throws IOException {
        if (url.getProtocol().equals("file")) {
            File file;
            try {
                file = new File(new URI(url.toString()));
            } catch(URISyntaxException e) {
                file = new File(url.getPath());
            }
            if (file!=null) {
               return getImporter(viewer, file);
            }
        }
        try {
            return JSEngine.doPrivileged(new Callable<ImporterTask>() {
                public ImporterTask call() throws IOException {
                    URLConnection c = url.openConnection();
                    InputStream in;
                    String urlstring = new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getFile()).toString();      // Strip password
                    if (c instanceof HttpURLConnection) {
                        HttpURLConnection h = (HttpURLConnection) c;
                        h.setFollowRedirects(true);
                        if (h.getURL().getUserInfo() != null) {
                            h.setRequestProperty("Authorization", "Basic "+Base64.encode(c.getURL().getUserInfo().getBytes("ISO-8859-1")));
                        }
                        h.connect();
                        in = h.getInputStream();
                        int rc = h.getResponseCode();
                        if (rc != 200) {
                            String msg = "Server returned error status code " + rc;
                            String rm = h.getResponseMessage();
                            if (rm != null) {
                                msg += ": " + rm;
                            }
                            throw new IOException(msg);
                        }
                    } else {
                        c.connect();
                        in = c.getInputStream();
                    }
                    return getImporter(viewer, in, urlstring, null);
                }
            });
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException)e;
            } else {
                throw (RuntimeException)e;
            }
        }
    }

    /**
     * This class is a {@link LongRunningTask} that can be run to load a PDF
     * into the viewer. Subclasses of {@link Importer} need to implement a concrete
     * subclass of this which implemented the {@link #loadPDF} method. By default
     * this task is cancellable, but that may be overridden too.
     * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
     * @since 2.10.2
     */
    public abstract class ImporterTask extends LongRunningTask implements DocumentPanelListener {
        /**
         * The viewer passed to the constructor
         */
        protected final PDFViewer viewer;

        /**
         * The file passed to the constructor
         */
        protected final File file;

        /**
         * The InputStream passed to the constructor
         */
        protected final InputStream in;

        /**
         * The title passed to the constructor
         */
        protected String title;

        /**
         * The initial pagenumber to request
         */
        protected int pagenumber;

        private File savefile;

        /**
         * Create a new ImporterTask which will read it's input from a File (if <code>in</code>
         * is null) or from an InputStream.
         * @param viewer the Viewer this task is going to add the PDF to
         * @param in the InputStream to read the PDF from, or <code>null</code> to read
         * the PDF from the <code>file</code> parameter
         * @param title the title of the PDF - may be null
         * @param file If <code>in</code> is null, the File to read the PDF from
         * (may not be null). If <code>in</code> is not null, the file that is used
         * as the default when this PDF is save, in which case it may be null.
         */
        protected ImporterTask(PDFViewer viewer, InputStream in, String title, File file) {
            this.viewer = viewer;
            if (file != null) {
                file = file.getAbsoluteFile();
            }
            this.file = file;
            this.savefile = file;
            this.in = in;
            if (title == null && file != null) {
                title = file.getName();
            }
            if (getFeatureProperty(viewer, "modal") != null) {
                setModal(true);
            }
            this.title = title;
        }

        public boolean isCancellable() {
            return true;
        }

        @SuppressWarnings("rawtypes")
        public void run() throws IOException {
            final PDF pdf = loadPDF();
            processPDF(pdf);
            if (!isCancelled() && pdf!=null) {
                if (title == null || ("true".equals(pdf.getOption("view.displayDocTitle")) && viewer.getPropertyManager() != null && viewer.getPropertyManager().getProperty("ignoreDocumentTitle") == null)) {
                    Object t = pdf.getXMP().get("dc:title");
                    if (t == null) {
                        t = pdf.getInfo("Title");
                    } else if (t instanceof List && !((List)t).isEmpty()) {
                        t = ((List)t).get(0);
                    }
                    if (t instanceof String) {
                        title = (String)t;
                    }
                }
                if (title == null) {
                    title = "BFO";
                }
                if ("true".equals(pdf.getOption("view.fullscreen"))) {
                    viewer.addDocumentPanelListener(new DocumentPanelListener() {
                        public void documentUpdated(final DocumentPanelEvent event) {
                            if (event.getType() == "activated" && event.getDocumentPanel().getPDF()==pdf) {
                                viewer.getActiveDocumentPanel().runAction(PDFAction.named("FullScreen"));
                                viewer.removeDocumentPanelListener(this);
                            }
                        }
                    });
                }
                viewer.addDocumentPanel(getParser(pdf), title, savefile, pagenumber, addToMostRecent(), isPDFImporter(), this);
            }
        }

        /**
         * Set the initial pagenumber to display. This may be overridden
         * by a PDF openaction or if it is out of range
         * @since 2.11.10
         */
        public void setPageNumber(int pagenumber) {
            this.pagenumber = pagenumber;
        }

        /**
         * Set the default file to save the file to if the PDF is later saved.
         * For PDFs loaded from a File, this defaults to the same file.
         * If the specified File does not have an absolute path, the directory
         * it will be resolved against is undefined. The {@link org.faceless.pdf2.viewer2.feature.Save}
         * feature will try to resolve it against the last directory used by the user
         * @param file If the PDF is later saved, the file to initialize the path to. Should
         * be an absolute file if one is required, and may also be <code>null</code>.
         * @since 2.11.22
         */
        public void setFile(File file) {
            this.savefile = file;
        }

        /**
         * Set the title which is displayed in the window decoration of the imported file.
         * @since 2.24.3
         */
        public void setTitle(String title) {
            this.title = title;
        }

        /**
         * Return the file previously set by {@link #setFile}
         * @since 2.24.3
         */
        public File getFile() {
            return savefile;
        }

        /**
         * Return the title, as set by {@link #setTitle}
         */
        public String getTitle() {
            return title;
        }

        /**
         * Load and return a PDF. Subclasses must implement this method, loading
         * the PDF from the {@link InputStream} <code>in</code> if it's specified
         * of the {@link File} <code>file</code> if it's not
         * @return the PDF
         */
        public abstract PDF loadPDF() throws IOException;

        /**
         * Return true if the file should be added to the "Most Recent" list of files,
         * false otherwise. The default is true
         * @since 2.16.1
         */
        public boolean addToMostRecent() {
            return addToMRU;
        }

        /**
         * This event listener will be called when the PDF has been imported.
         * By default it does nothing, but can be overriden in subclasses.
         * @since 2.24.3
         */
        @Override public void documentUpdated(DocumentPanelEvent event) {
        }
    }

    /**
     * Perform any processing on the PDF after it has been loaded but before
     * it's handed off to the viewer. By default this method is a no-op, but
     * custom subclasses may want to override it.
     * @since 2.10.6
     */
    protected void processPDF(PDF pdf) {
    }

    /**
     * Create a {@link PDFParser} for the specified PDF. By default
     * this method simply returns <code>new PDFParser(pdf)</code> -
     * if necessary this can be overridden to return a custom subclass
     * of PDFParser.
     * @param pdf the PDF
     * @return a PDFParser to parse the specified PDF
     * @since 2.11.3
     */
    protected PDFParser getParser(PDF pdf) {
        return new PDFParser(pdf);
    }
}
