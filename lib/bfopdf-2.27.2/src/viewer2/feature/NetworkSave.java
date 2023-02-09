// $Id: NetworkSave.java 41659 2021-11-10 09:23:06Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.viewer2.util.*;
import org.faceless.pdf2.*;
import org.faceless.util.CountingOutputStream;
import org.faceless.util.Base64;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.*;
import java.awt.event.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.io.*;

/**
 * Create a button that will submit a PDF to a network URL. By default this feature
 * is disabled - to enable it in an application,:
 * you would add a new NetworkSave instance
 * to the list of features passed in to the {@link PDFViewer#PDFViewer(java.util.Collection)}
 * constructor:
 * <pre class="brush:java">
 * ArrayList f = new ArrayList(ViewerFeature.getAllEnabledFeatures());
 * NetworkSave save = new NetworkSave();
 * save.setURL(new URL("http://localhost:8080/savepdf"));
 * f.add(save);
 * PDFViewer viewer = new PDFViewer(f);
 * </pre>
 * <p>
 * The {@link #setURL URL} attribute must be set on this feature before it can be used,
 * either via the <code>url</code> initialization parameter or by calling the {@link #setURL}
 * method. It may contain a Base64-encoded username and password if necessary. The PDF
 * will be rendered and sent directly to the URL connection's
 * {@link URLConnection#getOutputStream OutputStream}. If the filename of the PDF is known,
 * it will be submitted via the <code>X-BFOPDF-File</code> HTTP header - if more customization
 * is required you can override the {@link #setAdditionalHeaders} method to modify
 * the PDF and/or the connection headers before the PDF is submitted
 * </p>
 *
 * <div class="initparams">
 * The following <a href="../doc-files/initparams.html">initialization parameters</a> can be specified to configure this feature.
 * <table summary="">
 * <tr><th>url</th><td>The URL to submit the save action to</td></tr>
 * <tr><th>premessage</th><td>The message to display to the user before submission. If not null, the value will be displayed to the user with an "OK" or "Cancel" prompt before saving</td></tr>
 * <tr><th>postmessage</th><td>The message to display to the user after submission. If not null, the value will be displayed to the user with an "OK" prompt after saving. The special value "response" can be used to display the HMTL response from the server</td></tr>
 * <tr><th>fieldname</th><td>If set, the PDF will be sent as if submitted via an HTTP Form - "fieldname" would be the name of the field. It null, the PDF will simply be written as the body of the HTTP content (since 2.11.19).</td></tr>
 * <tr><th>filename</th><td>The filename parameter to send in the submission (defaults to the PDF filename)</td></tr>
 * <tr><th>timeout</th><td>The connection timeout value in milliseconds (0 is infinite)</td></tr>
 * <tr><th>readTimeout</th><td>The read timeout value in milliseconds (0 is infinite)</td></tr>
 * </table>
 * </div>
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">NetworkSave</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.11.17
 */
public class NetworkSave extends ViewerWidget implements DocumentPanelListener {

    private Action action;
    private int conntimeout, readtimeout;
    private URL url;
    private String premessage, postmessage, fieldname, filename;

    public NetworkSave() {
        super("NetworkSave");
        setButton("Document", "PDFViewer.Feature.NetworkSave.icon", "PDFViewer.tt.Save");
        setMenu("File\tSaveToNetwork", 's');
        action = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                NetworkSave.super.createActionListener().actionPerformed(e);
            }
        };
    }

    protected ActionListener createActionListener() {
        return action;
    }

    public void action(ViewerEvent event) {
        save(event, null, url);
    }

    public boolean isEnabledByDefault() {
        return false;
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        viewer.addDocumentPanelListener(this);
        String val = getFeatureProperty(viewer, "timeout");
        if (val!=null) {
            setTimeout(Integer.parseInt(val));
        }
        val = getFeatureProperty(viewer, "readTimeout");
        if (val!=null) {
            setReadTimeout(Integer.parseInt(val));
        }
        val = getFeatureProperty(viewer, "url");
        if (val!=null) {
            try {
                setURL(new URL(val));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        val = getFeatureProperty(viewer, "premessage");
        if (val!=null) {
            setPreMessage(val);
        }
        val = getFeatureProperty(viewer, "postmessage");
        if (val!=null) {
            setPostMessage(val);
        }
        val = getFeatureProperty(viewer, "fieldname");
        if (val!=null) {
            setFieldName(val);
        }
        val = getFeatureProperty(viewer, "filename");
        if (val!=null) {
            setFileName(val);
        }
    }

    /**
     * Set the timeout in milliseconds that should be used when connecting
     * to a URL. A value of 0 (the default) means infinite.
     * @param timeout the number of milliseconds to wait while connecting to a URL before timing out
     */
    public void setTimeout(int timeout) {
        conntimeout = timeout;
    }

    /**
     * Set the timeout in milliseconds that should be used when reading from
     * a URL. A value of 0 (the default) means infinite.
     * @param timeout the number of milliseconds to wait while reading from a URL before timing out
     */
    public void setReadTimeout(int timeout) {
        readtimeout = timeout;
    }

    /**
     * Set the URL the PDF will be submitted to. This must be set for this feature to work.
     * @param url the URL
     */
    public void setURL(URL url) {
        this.url = url;
    }

    /**
     * Set the message which will be displayed to the user before saving as a prompt - the
     * save will only continue if the user selects "OK". Setting this value to null (the
     * default) means no prompt will be given before saving.
     * @param message the message to display
     */
    public void setPreMessage(String message) {
        this.premessage = message;
    }

    /**
     * Set the message which will be displayed to the user after a successful save. If this
     * is null (the default) no message will be displayed. The special value "response" can
     * be used to display the HTML response from the server.
     * @param message the message to display
     */
    public void setPostMessage(String message) {
        this.postmessage = message;
    }

    /**
     * Set the field name which the PDF will be submitted as (as if it were sent from an HTML Form).
     * If null (the default), the PDF will simply be written to the HTTP stream.
     * @param fieldname the field name
     * @since 2.11.19
     */
    public void setFieldName(String fieldname) {
        this.fieldname = fieldname;
    }

    /**
     * Set the file name which the PDF will be submitted as (as if it were sent from an HTML Form).
     * If null (the default), the filename of the PDF will be used.
     * @param filename the file name
     * @since 2.11.19
     */
    public void setFileName(String filename) {
        this.filename = filename;
    }

    public void documentUpdated(DocumentPanelEvent event) {
        DocumentPanel docpanel = event.getDocumentPanel();
        String type = event.getType();
        if (type.equals("activated") || (type.equals("permissionChanged") && docpanel == getViewer().getActiveDocumentPanel())) {
            action.setEnabled(docpanel.getPDF() != null && docpanel.hasPermission("Save"));
        } else if (type.equals("deactivated")) {
            action.setEnabled(false);
        }
    }

    /**
     * Save the Document to the network
     * @param event the ViewerEvent that launched this action
     * @param exporter the {@link Exporter} to use, or null to default to a {@link PDFExporter}
     * @param url the URL to submit the PDF to
     */
    public void save(final ViewerEvent event, final Exporter exporter, final URL url) {
        if (url == null) {
            throw new IllegalStateException("No URL specified");
        }
        try {
            JSEngine.doPrivileged(new Callable<Void>() {
                @SuppressWarnings("deprecation")
                public Void call() {
                    final DocumentPanel docpanel = event.getDocumentPanel();
                    final PDFViewer viewer = event.getViewer();
                    final JComponent secondarycomp = exporter == null ? null : exporter.getComponent();
                    if (premessage != null && JOptionPane.showConfirmDialog(null, premessage, UIManager.getString("PDFViewer.Save"), JOptionPane.OK_CANCEL_OPTION) == JOptionPane.CANCEL_OPTION) {
                        return null;
                    }

                    boolean ok;
                    if (secondarycomp != null) {
                        DialogPanel dialog = new DialogPanel() {
                            public String validateDialog() {
                                return exporter.validateComponent(secondarycomp);
                            }
                        };
                        secondarycomp.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                        dialog.addComponent(secondarycomp);
                        dialog.setButtonText("ok", UIManager.getString("PDFViewer.Save"));
                        ok = dialog.showDialog(viewer, Util.getUIString("PDFViewer.SavingFile", "PDF"));
                    } else {
                        ok = true;
                    }

                    if (ok) {
                        LongRunningTask task = new LongRunningTask(true, 1000) {
                            volatile int submitlen;
                            volatile CountingOutputStream out;
                            volatile HttpURLConnection con;
                            public void run() throws IOException {
                                PDF pdf = docpanel.getPDF();

                                // Lifted from PDFExporter
                                Collection<Object> c = new LinkedHashSet<Object>(docpanel.getSidePanels());
                                if (docpanel.getViewer() != null) {
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

                                con = (HttpURLConnection)url.openConnection();
                                con.setUseCaches(false);
                                try {
                                    con.setConnectTimeout(conntimeout);     // Java15
                                    con.setReadTimeout(readtimeout);        // Java15
                                } catch (NoSuchMethodError e) { }
                                con.setInstanceFollowRedirects(true);
                                con.setRequestProperty("User-Agent", "Mozilla/4.0 ("+System.getProperty("os.name")+"/"+System.getProperty("os.version")+") BFOPDF/"+PDF.VERSION+" Java/"+System.getProperty("java.version"));
                                if (url.getUserInfo() != null) {
                                    con.setRequestProperty("Authorization", "Basic "+Base64.encode(url.getUserInfo().getBytes("ISO-8859-1")));
                                }
                                con.setRequestMethod("POST");
                                con.setDoOutput(true);
                                con.setRequestProperty("Connection", "close");
                                if (docpanel.getAssociatedFile() != null) {
                                    con.setRequestProperty("X-BFOPDF-File", docpanel.getAssociatedFile().toString());
                                }
                                setAdditionalHeaders(con, docpanel);
                                ByteArrayOutputStream bout = new ByteArrayOutputStream();

                                if (fieldname == null) {
                                    con.setRequestProperty("Content-Type", "application/pdf");
                                    pdf.render(bout);
                                } else {
                                    byte[] crlf = new byte[] { (byte) 13, (byte) 10 };
                                    byte[] dash = new byte[] { (byte) 45, (byte) 45 };
                                    byte[] rand = new byte[18];
                                    new Random().nextBytes(rand);
                                    String boundary = Base64.encode(rand);
                                    con.setRequestProperty("Content-Type", "multipart/form-data; boundary="+boundary);
                                    String fname = filename;
                                    if (fname == null) {
                                        File origfile = docpanel.getAssociatedFile();
                                        if (origfile == null) {
                                            fname = fieldname + ".pdf"; // Optional in theory. Just make it up
                                        } else {
                                            fname = origfile.getName();
                                        }
                                    }
                                    String disposition = "Content-Disposition: form-data; name=\""+fieldname+"\"; filename=\""+fname+"\"";
                                    String contentType = "Content-Type: application/pdf";
                                    bout.write(dash);
                                    bout.write(boundary.getBytes("ISO-8859-1"));
                                    bout.write(crlf);
                                    bout.write(disposition.getBytes("ISO-8859-1"));
                                    bout.write(crlf);
                                    bout.write(contentType.getBytes("ISO-8859-1"));
                                    bout.write(crlf);
                                    bout.write(crlf);
                                    pdf.render(bout);
                                    bout.write(crlf);
                                    bout.write(dash);
                                    bout.write(boundary.getBytes("ISO-8859-1"));
                                    bout.write(dash);
                                    bout.write(crlf);
                                }
                                bout.close();
                                submitlen = bout.size();
                                con.setRequestProperty("Content-Length", Integer.toString(submitlen));
                                for (Iterator<Object> i = c.iterator();i.hasNext();) {
                                    ((PDFBackgroundTask)i.next()).unpause();
                                }

                                out = new CountingOutputStream(con.getOutputStream());
                                try {
                                    bout.writeTo(out);
                                    out.close();
                                    out = null;
                                    int status = con.getResponseCode();
                                    if (status > 299) {
                                        String response = getServerResponse(con);
                                        JOptionPane.showMessageDialog(null, response, UIManager.getString("PDFViewer.Error"), JOptionPane.ERROR_MESSAGE);
                                    } else {
                                        if (postmessage != null) {
                                            final String msg = postmessage.equals("response") ? getServerResponse(con) : postmessage;
                                            SwingUtilities.invokeLater(new Runnable() {
                                                public void run() {
                                                    JOptionPane.showMessageDialog(null, msg, UIManager.getString("PDFViewer.Saved"), JOptionPane.INFORMATION_MESSAGE);
                                                }
                                            });
                                        }
                                    }
                                } catch (IOException e) {
                                    if (!isCancelled()) {
                                        throw e;
                                    }
                                } finally {
                                    if (out != null) out.close();
                                }
                                docpanel.setDirty(false);
                                docpanel.getJSManager().runEventDocDidSave(docpanel);
                            }

                            public float getProgress() {
                                if (submitlen == 0) {
                                    return docpanel.getPDF().getRenderProgress() / 2;
                                } else if (out != null) {
                                    double t = out.tell();
                                    return (float)((t / submitlen) / 2 + 0.5);
                                } else {
                                    return 1;
                                }
                            }

                            public void cancel() {
                                super.cancel();
                                if (con != null) {
                                    con.disconnect();       // Thread.interrupt() doesn't interrupt this I/O...
                                }
                            }
                        };
                        task.setModal(true);
                        task.start(viewer, Util.getUIString("PDFViewer.SavingFile", url.toString()));
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            throw (RuntimeException)e;
        }
    }

    /**
     * Set any additional headers on the connection, or modify the PDF if necessary
     * before saving. Called by the {@link #save} method just before the PDF is
     * sent, by default this method does nothing.
     */
    public void setAdditionalHeaders(URLConnection con, DocumentPanel panel) {
    }

    private static String getServerResponse(HttpURLConnection con) throws IOException {
        String enc = con.getContentEncoding();
        if (enc == null) {
            enc = "ISO-8859-1";
        }
        StringBuilder sout = new StringBuilder();
        BufferedReader sin = new BufferedReader(new InputStreamReader(con.getInputStream(), enc));
        String s;
        while ((s=sin.readLine()) != null) {
            sout.append(s);
            sout.append("\n");
        }
        sin.close();
        return sout.toString();
    }

}
