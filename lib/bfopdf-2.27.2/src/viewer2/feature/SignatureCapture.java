package org.faceless.pdf2.viewer2.feature;

import java.io.*;
import java.util.*;
import java.net.*;
import java.awt.image.*;
import java.awt.geom.*;
import java.awt.*;
import java.security.*;
import org.faceless.pdf2.*;
import org.faceless.util.Base64;
import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.viewer2.util.*;
import javax.swing.*;
import java.awt.event.*;
import javax.swing.event.*;

/**
 * <p>
 * A feature which can be used to capture a handwritten signature on a
 * Smartphone (iPhone, iPad or Android). It is disabled by default.
 * </p><p>
 * When run this feature starts an (extremely) simple webserver and then
 * creates a QR-Code which can be scanned by the smartphone to access
 * that server. On doing so the user is presented with a page with a
 * signature box which they can "write" their signature into, using a
 * finger or stylus. On completion the signature vector data is sent back
 * to the webserver started by this process, and the server is shut down.
 * </p><p>
 * This requires that the computer running the viewer with this feature
 * is accessible to the network the smartphone is on - in practice, this
 * means they're on the same IP network with the smartphone connecting
 * over WiFi. The URL for that computer will be guessed, but it can
 * be specified if necessary with the "host" and "port" features.
 * </p><p>
 * This feature is normally used by the {@link ManageIdentities} feature,
 * which will use this to capture the signature and store it in the KeyStore.
 * Doing so requires a
 * <a href="http://download.java.net/jdk8/docs/technotes/guides/security/p11guide.html" target="_new">PKCS#11</a> or
 * <a href="http://docs.oracle.com/javase/1.5.0/docs/guide/security/jce/JCERefGuide.html#JceKeystore" target="_new">JCEKS</a>
 * KeyStore - if the the KeyStore is in the default JCE format, the user
 * will be prompted to upgrade it to JCEKS.
 * </p>
 * <div class="initparams">
 * The following <a href="../doc-files/initparams.html">initialization parameters</a> can be specified to configure this feature.
 * <table summary="">
 * <tr><th>host</th><td>The fully qualified name of the host to use in the URL - specify this if the viewer can't derive it</td></tr>
 * <tr><th>port</th><td>The port to open the temporary server on - if not specified a random high port will be used</td></tr>
 * </table>
 * </div>
 * <span class="featurename">The name of this feature is <span class="featureactualname">SignatureCapture</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 *
 * @since 2.11.25
 */
public class SignatureCapture extends ViewerFeature {

    /**
     * The suffix recommended to be appended to a KeyStore alias for that
     * entries signature appearance
     */
    public static final String PATHSUFFIX = ".sigpath";

    /**
     * The "algorithm" for the signature appearance KeyStore entry - they
     * are stored as secret keys, which require an algorithm.
     */
    public static final String KEYALGORITHM = "bfo.signaturepath";

    /**
     * The password for the signature appearance KeyStore entry - they
     * are stored as secret keys so must be password protected, although
     * the information is not secret. To avoid prompting we use a standard
     * password each time.
     */
    public static final char[] SECRETKEYPASSWORD = KEYALGORITHM.toCharArray();

    private InetSocketAddress address;

    public SignatureCapture() {
        super("SignatureCapture");
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        String hostname = getFeatureProperty(viewer, "host");
        InetAddress host = null;
        try {
            if (hostname != null) {
                host = InetAddress.getByName(hostname);
            }
        } catch (IOException e) {
            throw new RuntimeException("Can't identify local host from "+hostname);
        }
        String v = getFeatureProperty(viewer, "port");
        int port = 0;
        if (v != null) {
            try {
                port = Integer.parseInt(v);
                if (port < 1024 || port > 65535) {
                    port = 0;
                }
            } catch (Exception e) { }
        }
        setLocalAddress(new InetSocketAddress(host, port));
    }

    public boolean isEnabledByDefault() {
        return false;
    }

    /**
     * Set the local address to open the webserver on. By default
     * the server will be opened on the wildcard address on a
     * randomly allocated port.
     */
    public void setLocalAddress(InetSocketAddress address) {
        this.address = address;
    }

    /**
     * A convenience method which will serialize the specified
     * GeneralPath into a byte array.
     * @see #readPath
     */
    public static byte[] writePath(GeneralPath path) {
        if (path == null || path.getBounds().width == 0) {
            return new byte[0];
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            new ObjectOutputStream(out).writeObject(path);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * A convenience method which will deserialize the specified
     * GeneralPath into a byte array.
     * @see #writePath
     */
    public static GeneralPath readPath(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            return (GeneralPath)new ObjectInputStream(new ByteArrayInputStream(data)).readObject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * A convenience method which will return a JComponent that
     * displays the specified {@link GeneralPath}
     */
    public static JComponent getPathComponent(final GeneralPath path) {
        final Rectangle bounds = path.getBounds();
        return new JPanel() {
            public void paintComponent(Graphics g) {
                float w = getWidth() - 2f;
                float h = getHeight() - 2f;
                int bw = bounds.width;
                int bh = bounds.height;
                float scale = Math.min(w / bw, h / bh);
                AffineTransform t = new AffineTransform();
                t.translate((w-(bw*scale)) / 2, (h-(bh*scale)) / 2);
                t.scale(scale, scale);
                t.translate(-bounds.x, -bounds.y);
                AffineTransform origt = ((Graphics2D)g).getTransform();
                ((Graphics2D)g).transform(t);
                ((Graphics2D)g).draw(path);
                ((Graphics2D)g).setTransform(origt);
            }
        };
    }

    public void action(ViewerEvent event) {
    }

    public GeneralPath capture(int width, int height) {
        return capture(this.address, null, width, height);
    }

    /**
     * Start a webserver, display a QR-Code that can be scanned to access it,
     * and capture the signature entered on that webpage as a GeneralPath.
     * The webserver is closed immediately on capture or cancellation
     * @param address the local address of the webserver - the port may be null - or null to use the default for this feature.
     * @param submiturl if not null, the URL that the signature should be submitted to
     * @param width the width of the desired "signature strip". Will be scaled to fit the device screen
     * @param height the height of the desired "signature strip".
     * @return a GeneralPath if a signature was submitted, null if the process was cancelled, or a zero-size signature if the delete option was chosen.
     */
    public static GeneralPath capture(InetSocketAddress address, String submiturl, int width, int height) {

        final Object[] path = { null };
        try {
            final ServerSocket socket = new ServerSocket();
            socket.bind(address);
            String hostname;
            if (address.getAddress() == null || address.getHostName().equals("0.0.0.0")) {
                hostname = InetAddress.getLocalHost().getCanonicalHostName();
            } else {
                hostname = address.getAddress().getCanonicalHostName();
            }
            String url = "http://"+hostname+":"+socket.getLocalPort()+"/capture?w="+width+"&h="+height+"&reset="+UIManager.getString("PDFViewer.Clear")+"&ok="+UIManager.getString("PDFViewer.OK");
            if (submiturl != null) {
                url += "&proxy="+URLEncoder.encode(submiturl, "UTF-8");
            }

            final DialogPanel panel = new DialogPanel(false, true);
            BarCode code = BarCode.newQRCode(url, 2, 2, 0);
            panel.addButton("delete", UIManager.getString("PDFViewer.Delete"), KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), new AbstractAction() {
                public void actionPerformed(ActionEvent e) {
                    path[0] = new GeneralPath();
                    panel.acceptDialog();
                }
            });
            BufferedImage img = code.getBufferedImage(1, Color.white);
            JButton button = new JButton("<html><p style='text-align:center; width:340px'>"+Util.getUIString("PDFViewer.CaptureSignatureHowto", url)+"</p></html>", new ImageIcon(img));
            button.setBorderPainted(false);
            button.setContentAreaFilled(false);
            button.setIconTextGap(10);
            button.setVerticalTextPosition(SwingConstants.BOTTOM);
            button.setHorizontalTextPosition(SwingConstants.CENTER);
            button.setFocusable(false);
            panel.addComponent(button);

            Thread serverthread = new Thread() {
                public void run() {
                    while (path[0] == null) {
                        try {
                            final Socket cs = socket.accept();
                            Thread actionthread = new Thread() {
                                public void run() {
                                    InputStream in = null;
                                    try {
                                        // Read a max of 64K characters to prevent malicious
                                        // connections doing any damage
                                        in = cs.getInputStream();
                                        StringBuilder sb = new StringBuilder();
                                        int c;
                                        while ((c=in.read()) >=0 && c != '\r' && sb.length() < 65536) {
                                            sb.append((char)c);
                                        }
                                        if (c == '\r' && in.read() == '\n' && sb.indexOf(" HTTP/1") > 0) {
                                            sb.setLength(sb.indexOf(" HTTP/1"));
                                            sb.delete(0, sb.indexOf(" ") + 1);
                                            if (sb.indexOf("/capture") >= 0) {
                                                serveCapture(cs, sb.toString());
                                            } else if (sb.indexOf("/store") >= 0) {
                                                path[0] = serveStore(cs, sb.toString());
                                                SwingUtilities.invokeLater(new Runnable() {
                                                    public void run() {
                                                        panel.acceptDialog();
                                                    }
                                                });
                                            }
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    } finally {
                                        try { if (in != null) in.close(); } catch (Exception e) {}
                                        try { cs.close(); } catch (Exception e) {}
                                    }
                                }
                            };
                            actionthread.setDaemon(true);
                            actionthread.start();
                        } catch (Exception e) {
                            path[0] = e;
                        }
                    }
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            if (panel.isVisible()) {
                                panel.cancelDialog();
                            }
                        }
                    });
                }
            };
            serverthread.setDaemon(true);
            serverthread.start();
            panel.showDialog(null, UIManager.getString("PDFViewer.ManageIdentities"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (path[0] instanceof GeneralPath) {
            return (GeneralPath)path[0];
        }
        return null;
    }

    private static void serveCapture(Socket cs, String s) throws IOException {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = PDFViewer.class.getResourceAsStream("resources/sigcapture.html");
            out = cs.getOutputStream();
            out.write("HTTP/1.0 200 OK\r\nContent-Type: text/html\r\nConnection: close\r\n\r\n".getBytes("ISO-8859-1"));
            int c;
            while ((c=in.read())>=0) {
                out.write(c);
            }
            out.close();
        } finally {
            if (in != null) try { in.close(); } catch (IOException e) {}
            if (out != null) try { out.close(); } catch (IOException e) {}
        }
    }

    private static GeneralPath serveStore(Socket cs, String s) throws IOException {
        if (cs != null) {
            OutputStream out = cs.getOutputStream();
            out.write("HTTP/1.0 200 OK\r\nContent-Type: text/html\r\nConnection: close\r\n\r\n<html><head><script>window.close()</script></head></html>".getBytes("ISO-8859-1"));
            out.close();

            s = s.substring(s.indexOf("?") + 1);
        }
        byte[] data = Base64.decode(s);
        GeneralPath path = new GeneralPath();
        int i = 0;
        int l = ((data[i++]&0xFF)<<8) | (data[i++]&0xFF);
        for (int j=0;j<l;j++) {
            int m = ((data[i++]&0xFF)<<8) | (data[i++]&0xFF) / 2;
            if (m > 0) {
                int x = ((data[i++]&0xFF)<<8) | (data[i++]&0xFF);
                int y = ((data[i++]&0xFF)<<8) | (data[i++]&0xFF);
                path.moveTo(x, y);
                for (int k=1;k<m;k++) {
                    x = ((data[i++]&0xFF)<<8) | (data[i++]&0xFF);
                    y = ((data[i++]&0xFF)<<8) | (data[i++]&0xFF);
                    path.lineTo(x, y);
                }
            }
        }
        return path;
    }

}
