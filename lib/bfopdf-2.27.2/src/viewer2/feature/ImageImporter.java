// $Id: ImageImporter.java 42224 2022-02-01 09:07:49Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.UIManager;
import java.util.*;
import java.util.concurrent.Callable;
import java.io.*;

/**
 * A subclass of {@link Importer} that allows bitmap images to be converted
 * to PDF documents and loaded directly into the {@link PDFViewer}. This
 * class handles all the formats supported by the {@link PDFImage} class,
 * namely TIFF, PNG, GIF, JPEG, PNM and JPEG-2000.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">ImageImporter</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.10.2.
 */
public class ImageImporter extends Importer
{
    private final FileFilter filefilter;
    private static final Map<String,String> PREFIXES;
    static {
        try {
            PREFIXES = new HashMap<String,String>();
            PREFIXES.put(new String(new byte[] { (byte)0xFF, (byte)0xD8 }, "ISO-8859-1"), "JPEG");
            PREFIXES.put(new String(new byte[] { (byte)0x89, 'P', 'N', 'G', '\r' }, "ISO-8859-1"), "PNG");
            PREFIXES.put(new String(new byte[] { 'I', 'I', '*', 0 }, "ISO-8859-1"), "TIFF");
            PREFIXES.put(new String(new byte[] { 'M', 'M', 0, '*' }, "ISO-8859-1"), "TIFF");
            PREFIXES.put(new String(new byte[] { 'G', 'I', 'F', '8' }, "ISO-8859-1"), "GIF");
            PREFIXES.put(new String(new byte[] { 'P', '4' }, "ISO-8859-1"), "PNM");
            PREFIXES.put(new String(new byte[] { 'P', '5' }, "ISO-8859-1"), "PNM");
            PREFIXES.put(new String(new byte[] { 'P', '6' }, "ISO-8859-1"), "PNM");
            PREFIXES.put(new String(new byte[] { 'B', 'M' }, "ISO-8859-1"), "PNM");
            PREFIXES.put(new String(new byte[] { 0, 0, 0, 0x0C, 'j', 'P', ' ', ' ', '\r' }, "ISO-8859-1"), "JP2");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    };

    public ImageImporter() {
        super("ImageImporter");
        setAddToMostRecent(false);
        filefilter = new FileFilter() {
            public boolean accept(File file) {
                String fname = file.getName().toLowerCase();
                return file.isDirectory() || fname.endsWith(".png") || fname.endsWith(".gif") || fname.endsWith(".jpg") || fname.endsWith(".jp2") || fname.endsWith(".tif") || fname.endsWith(".bmp") || fname.endsWith(".pnm") || fname.endsWith(".ppm") || fname.endsWith(".pbm") || fname.endsWith(".pgm");
            }
            public String getDescription() {
                return UIManager.getString("PDFViewer.FilesBitmap");
            }
        };
    }

    protected boolean isPDFImporter() {
        return false;
    }

    public FileFilter getFileFilter() {
        return filefilter;
    }

    public boolean matches(final File file) throws IOException {
        try {
            return (JSEngine.doPrivileged(new Callable<Boolean>() {
                public Boolean call() throws IOException {
                    if (file.length() > 10) {
                        BufferedInputStream in = new BufferedInputStream(new FileInputStream(file), 10);
                        String type = getImageFormat(in);
                        in.close();
                        return type != null;
                    }
                    return Boolean.FALSE;
                }
            })).booleanValue();
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException)e;
            } else {
                throw (RuntimeException)e;
            }
        }
    }

    private static String getImageFormat(InputStream in) throws IOException {
        in.mark(10);
        byte[] buf = new byte[10];
        for (int i=0;i<10;i++) {
            buf[i] = (byte)in.read();
        }
        in.reset();
        for (Map.Entry<String,String> e : PREFIXES.entrySet()) {
            byte[] b = e.getKey().getBytes("ISO-8859-1");
            boolean ok = true;
            for (int i=0;i<b.length;i++) {
                if (buf[i] != b[i]) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return e.getValue();
            }
        }
        return null;
    }

    public boolean canLoad(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }

    public ImporterTask getImporter(PDFViewer viewer, File file) {
        return getImporter(viewer, null, file.getName(), file);
    }

    public ImporterTask getImporter(final PDFViewer viewer, final InputStream in, final String title, final File file) {
        return new ImporterTask(viewer, in, title, file) {
            private int i, num;
            public float getProgress() {
                return num == 0 ? 0 : (float)i / num;
            }
            public PDF loadPDF() throws IOException {
                final String name = file.getName();
                int ix = name.lastIndexOf(".");
                if (ix > 0) {
                    setFile(new File(file.getParentFile(), name.substring(0, ix) + ".pdf"));
                    setTitle(Util.getUIString("PDFViewer.ImportedFrom").replaceAll("\\{1\\}", getTitle()));
                }
                final InputStream fin = this.in;
                try {
                    return JSEngine.doPrivileged(new Callable<PDF>() {
                        public PDF call() throws IOException {
                            InputStream in = fin;
                            if (in == null) {
                                in = new FileInputStream(file);
                            }
                            try {
                                PDF pdf = new PDF();
                                if (!in.markSupported()) {
                                    in = new BufferedInputStream(in);
                                }
                                String type = getImageFormat(in);
                                PDFImageSet imageset = new PDFImageSet(in);
                                num = imageset.getNumImages();
                                Reader xmp = null;
                                for (i=0;i<num && !isCancelled();i++) {
                                    PDFImage image = imageset.getImage(i);
                                    if (xmp == null) {
                                        xmp = image.getMetaData();
                                    }
                                    PDFPage page = pdf.newPage((int)image.getWidth(), (int)image.getHeight());
                                    page.drawImage(image, 0, 0, page.getWidth(), page.getHeight());
                                }
                                if (isCancelled()) {
                                    return null;
                                } else if (xmp != null) {
                                    StringWriter w = new StringWriter();
                                    int c;
                                    while ((c=xmp.read())>=0) {
                                        w.write((char)c);
                                    }
                                    xmp.close();
                                    if (w.toString().length() == 0) {
                                        xmp = null;
                                    } else {
                                        pdf.setMetaData(w.toString());
                                    }
                                }
                                if (xmp == null) {
                                    pdf.setInfo("Title", title);
                                }
                                // As of 2.26.3 we record the conversion in the XMP history
                                // of our newly created PDF file. This works best if the
                                // PDF has an InstanceID, which it doesn't in a newly created
                                // file. This is how to set one.
                                org.faceless.util.json.Json j = org.faceless.util.json.Json.read("{}");
                                j.put("format", type);
                                j.put("name", name);
                                String uuid = UUID.randomUUID().toString();
                                pdf.getXMP().set("xmpMM:DocumentID", uuid);
                                pdf.getXMP().set("xmpMM:InstanceID", uuid);
                                pdf.getXMP().addHistory("convert", j.toString(), "bfopdf " + PDF.VERSION, uuid, null);
                                // Do a dummy render on the file to make sure it has a proper
                                // PDF structure on it, suitable for (for example) PDF/A validation
                                pdf.render(new OutputStream() {
                                    public void write(int v) {
                                    }
                                    public void write(byte[] buf, int off, int len) {
                                    }
                                });
                                return pdf;
                            } finally {
                                in.close();
                            }
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
        };
    }
}
