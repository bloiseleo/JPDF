// $Id: DragAndDrop.java 41659 2021-11-10 09:23:06Z mike $

package org.faceless.pdf2.viewer2.feature;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.Callable;
import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.JSEngine;
import java.awt.datatransfer.*;
import javax.swing.TransferHandler;
import javax.swing.JComponent;
import javax.swing.UIManager;

/**
 * A feature which will add the ability to drag and drop files into the PDFViewer in a standard
 * way. This class can be added as-is to the viewer to support that, or it can be subclassed
 * and its {@link #processTransferable} method called - this method will call the {@link #action}
 * methods for each File or URL specified.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">DragAndDrop</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.10.3
 */
public class DragAndDrop extends ViewerFeature
{
    private static final DataFlavor gtkFlavor;  // Needed to work with GTK - Bug 4899516
    static {
        DataFlavor x = null;
        try {
            x = new DataFlavor("text/uri-list;class=java.lang.String");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);      // Won't happen
        }
        gtkFlavor = x;
    }

    private PDFViewer viewer;

    /**
     * Create a new DragAndDrop object
     */
    public DragAndDrop() {
        super("DragAndDrop");
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        viewer.setTransferHandler(getTransferHandler());
        this.viewer = viewer;
    }

    /**
     * Return a {@link TransferHandler} which can be added to a {@link JComponent}.
     * @see JComponent#setTransferHandler
     */
    public TransferHandler getTransferHandler() {
        return new TransferHandler() {
            @Override public boolean canImport(TransferHandler.TransferSupport support) {
                DataFlavor[] flavors = support.getDataFlavors();
                support.setDropAction(TransferHandler.COPY);
                for (int i=0;i<flavors.length;i++) {
                    if (DragAndDrop.this.canImport(flavors[i])) {
                        return true;
                    }
                }
                return false;
            }

            public boolean importData(JComponent comp, Transferable tran) {
                return processTransferable(tran);
            }
        };
    }

    /**
     * Return true if the DataFlavor can be processed by this class
     * @see TransferHandler#canImport(JComponent, DataFlavor[])
     */
    public static boolean canImport(DataFlavor[] flavors) {
        for (int i=0;i<flavors.length;i++) {
            if (canImport(flavors[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return true if the DataFlavor can be processed by this class
     * @see TransferHandler#canImport(JComponent, DataFlavor[])
     */
    public static boolean canImport(DataFlavor flavor) {
        return flavor.equals(DataFlavor.javaFileListFlavor) || flavor.getRepresentationClass()==URL.class || flavor.equals(gtkFlavor);
    }

    /**
     * Process the Transferable object by calling {@link #action(File)} or {@link #action(URL)}
     * on each matching item being transfered.
     * @see TransferHandler#importData(JComponent, Transferable)
     */

    public boolean processTransferable(Transferable tran) {
        try {
            DataFlavor[] flavors = tran.getTransferDataFlavors();
            for (int i=0;i<flavors.length;i++) {        // Try Files first
                if (flavors[i].equals(DataFlavor.javaFileListFlavor)) {
                    List<?> list = (List<?>)tran.getTransferData(flavors[i]);
                    for (i=0;i<list.size();i++) {
                        action((File)list.get(i));
                    }
                    return true;
                }
            }
            for (int i=0;i<flavors.length;i++) {        // Try URLs next
                if (flavors[i].getRepresentationClass()==URL.class) {
                    URL url = (URL)tran.getTransferData(flavors[i]);
                    metaaction(url);
                    return true;
                }
            }
            for (int i=0;i<flavors.length;i++) {        // Try URL list last
                if (flavors[i].equals(gtkFlavor)) {
                    for (StringTokenizer st = new StringTokenizer((String)tran.getTransferData(flavors[i]), "\r\n"); st.hasMoreTokens();) {
                        String s = st.nextToken();
                        if (!s.startsWith("#")) {
                            try {
                                metaaction(new URL(s.trim()));
                            } catch (MalformedURLException e) {}
                        }
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void metaaction(URL url) {
        if (url.getProtocol().equals("file")) {
            File file;
            try {
                file = new File(url.toURI());   // Java15
            } catch (Throwable e) {
                file = new File(url.getPath());
            }
            action(file);
        } else {
            action(url);
        }
    }

    /**
     * Process a {@link File} from a Transferable. By default called {@link PDFViewer#loadPDF(File)}
     */
    public void action(File file) {
        viewer.loadPDF(file);
    }

    /**
     * Process a {@link URL} from a Transferable, which is guaranteed not to be a <code>file</code> URL.
     * By default passes the URL stream to an {@link Importer}
     */
    public void action(final URL url) {
        try {
            JSEngine.doPrivileged(new Callable<Void>() {
                public Void call() throws Exception {
                    PDFImporter importer = viewer.getFeature(PDFImporter.class);
                    if (importer == null) {
                        importer = new PDFImporter();
                    }
                    InputStream in = url.openStream();
                    Importer.ImporterTask task = importer.getImporter(viewer, in, url.toString(), null);
                    task.start(viewer, UIManager.getString("PDFViewer.Loading"));
                    return null;
                }
            });
        } catch (Exception e) {
            Util.displayThrowable(e, viewer);
        }
    }
}
