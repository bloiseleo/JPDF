// $Id: About.java 35489 2020-03-06 11:57:14Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.event.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.*;

/**
 * Create a simple "About" dialog displaying information about the PDFViewer and the runtime environment.
 *
 * <div class="initparams">
 * The following <a href="../doc-files/initparams.html">initialization parameters</a> can be specified to configure this feature (since 2.11.23). Setting any one of these to the empty string will remove them from the dialog completely.
 * <table summary="">
 * <tr><th>Icon</th><td>The URL of the Icon to display instead of the BFO logo - the image should be roughly 100 pixels wide</td></tr>
 * <tr><th>Title</th><td>The title to display (default is "Java PDF Viewer")</td></tr>
 * <tr><th>Main</th><td>The main body to display. Default is the BFO Copyright message</td></tr>
 * <tr><th>PDF Library</th><td>The version number of the PDF Library</td></tr>
 * <tr><th>Java</th><td>The version of Java in use</td></tr>
 * <tr><th>JavaScript</th><td>The JavaScript engine that's in use</td></tr>
 * <tr><th>Licensed To</th><td>The details of the license for the PDF Library</td></tr>
 * <tr><th>Expiry</th><td>The expiry date for the License</td></tr>
 * <tr><th>Memory</th><td>The amount of memory available to the JVM</td></tr>
 * <tr><th>NoFeatures</th><td>If set, the list of features will never be displayed</td></tr>
 * </table>
 * </div>
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">About</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class About extends ViewerWidget {

    private Map<String,Object> keys;
    private static Map<String,Object> statickeys;

    public About() {
        super("About");
        setDocumentRequired(false);
        setMenu("Window\tAbout");
    }

    protected About(String title) {
        super(title);
    }

    public void action(final ViewerEvent event) {
        showAboutDialog(event.getViewer(), true, 0);
    }

    private static synchronized void initializeStatic() {
        statickeys = new LinkedHashMap<String,Object>();
        statickeys.put("Icon", PDFViewer.class.getResource("resources/BFOLogo.png"));
        statickeys.put("Title", "Java PDF Viewer");
        String year = new java.text.SimpleDateFormat("yyyy").format(new Date());
        statickeys.put("Main", "Big Faceless Organization \u00A9 2001-"+year);
        statickeys.put("PDF Library", PDF.VERSION);
        java.io.InputStream bin = null;
        try {
            bin = Class.forName("org.faceless.license.LicensePDF", true, Thread.currentThread().getContextClassLoader()).getResourceAsStream("PDF.license");
            java.io.ObjectInputStream in = new java.io.ObjectInputStream(bin);
            java.security.SignedObject so = (java.security.SignedObject)in.readObject();
            in.close();
            @SuppressWarnings("rawtypes") Map licensemap = (Map)so.getObject();
            Object licensee = licensemap.get("name");
            Object licensenumber = licensemap.get("number");
            Date expiry = (Date)licensemap.get("expiry");
            boolean core = "true".equals(licensemap.get("core"));
            if (licensee!=null) {
                String val = licensee+" ("+licensenumber+(core?"":": viewer")+")";
                statickeys.put("Licensed To", val);
                if (expiry.getTime()<4102405200000l) {
                    statickeys.put("Expiry", new java.text.SimpleDateFormat("dd MMM yyyy").format(expiry));
                }
            }
        } catch (Throwable e) {
            statickeys.put("Licensed To", "Unlicensed - Demo Version");
        } finally {
            try { if (bin!=null) bin.close(); } catch (Exception e) {}
        }
        statickeys.put("Java", System.getProperty("java.version")+" by "+System.getProperty("java.vendor"));
    }

    private synchronized void initialize() {
        PDFViewer viewer = getViewer();
        if (statickeys == null) {
            initializeStatic();
            if (!statickeys.containsKey("JavaScript")) {
                putStaticProperty("JavaScript", viewer.getJSManager().getImplementationDescription());
            }
        }
        keys = new LinkedHashMap<String,Object>();

        // Bit of a dogs breakfast this, as was designed before the
        // standard PropertyManager approach.

        String[] k = new String[] { "Icon", "Title", "Main", "PDF Library", "Java", "JavaScript", "Licensed To", "Expiry", "Memory", "NoFeatures" };
        for (int i=0;i<k.length;i++) {
            String key = k[i];
            String val = getFeatureProperty(viewer, key.replaceAll(" ", ""));
            if ("".equals(val)) {
                putProperty(key, null);
            } else if (val != null) {
                if (key.equals("Icon")) {
                    try {
                        putProperty(key, getFeatureURLProperty(viewer, key.replaceAll(" ", "")));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    putProperty(key, val);
                }
            }
        }
    }

    /**
     * Add a property to the "About" dialog. This method is static, which means that properties
     * set via this method will appear in all About dialogs created by this class and its subclasses.
     * Keys that have alreay been set and may be overridden include:
     * <table summary="" class="defntable">
     * <tr><th>Icon</th><td>The URL of the Icon to display in the dialog</td></tr>
     * <tr><th>Title</th><td>The Title displayed in the dialog</td></tr>
     * <tr><th>Main</th><td>The main line of text</td></tr>
     * <tr><th>PDF Library</th><td>The version of the PDF Viewer software</td></tr>
     * <tr><th>Java</th><td>The version of Java being run</td></tr>
     * <tr><th>JavaScript</th><td>The JavaScript implementation availabe to the Viewer</td></tr>
     * </table>
     * Any one of these keys may be overriden or have their value set to <code>null</code> to
     * suppress their display.
     * @since 2.9
     */
    public synchronized static Object putStaticProperty(String key, Object property) {
        if (statickeys==null) initializeStatic();
        return statickeys.put(key, property);
    }

    /**
     * Add a property to the "About" dialog. This method only adds properties to About dialogs created
     * by this instance. Values set in this way will override values set via the {@link #putStaticProperty}
     * method.
     * @since 2.9
     */
    public synchronized Object putProperty(String key, Object property) {
        if (keys==null) initialize();
        return keys.put(key, property);
    }

    /**
     * Show an "About" dialog in the viewer.
     * @param viewer the Viewer this about dialog relates to
     * @param featurelist whether to display the list of Features available to the viewer
     * @param autoclear the number of ms to wait before clearing the dialog. A value of 0 means
     * the dialog must be clicked to close.
     * @since 2.9
     */
    public void showAboutDialog(PDFViewer viewer, boolean featurelist, final int autoclear) {
        if (keys == null) {
            initialize();
        }
        LinkedHashMap<String,Object> aboutkeys = new LinkedHashMap<String,Object>();
        aboutkeys.putAll(statickeys);
        aboutkeys.putAll(keys);

        final JDialog dialog = Util.newJDialog(viewer, null, false);
        JPanel content = new JPanel(new GridBagLayout());
        content.setBackground(Color.white);
        content.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x80000000, true)), BorderFactory.createEmptyBorder(20, 20, 20, 20)));
        GridBagConstraints gbc = new GridBagConstraints();
        GridBagConstraints gbk = new GridBagConstraints();
        gbc.fill = gbc.HORIZONTAL;
        gbk.fill = gbc.HORIZONTAL;
        gbk.gridwidth = 1;
        gbk.insets = new Insets(0, 0, 0, 20);
        gbc.gridwidth = gbc.REMAINDER;
        ImageIcon icon = aboutkeys.get("Icon")!=null ? new ImageIcon((java.net.URL)aboutkeys.get("Icon")) : null;
        if (aboutkeys.containsKey("Title") || icon != null) {
            content.add(new JLabel((String)aboutkeys.get("Title"), icon, SwingConstants.LEFT), gbc);
        }

        gbc.insets = new Insets(10, 0, 10, 0);
        content.add(new JLabel((String)aboutkeys.get("Main")), gbc);
        gbc.insets = new Insets(0, 0, 0, 0);

        if (!aboutkeys.containsKey("Memory")) {
            System.gc();
            Runtime runtime = Runtime.getRuntime();
            long usedmem = runtime.totalMemory() - runtime.freeMemory();
            aboutkeys.put("Memory", formatMemory(usedmem)+" / "+formatMemory(runtime.maxMemory())+" ("+Math.round(usedmem * 100f / runtime.maxMemory())+"%)");
        }

        for (Iterator<Map.Entry<String,Object>> i = aboutkeys.entrySet().iterator();i.hasNext();) {
            Map.Entry<String,Object> e = i.next();
            String key = e.getKey();
            if (!"Main".equals(key) && !"Icon".equals(key) && !"Title".equals(key) && e.getValue() != null) {
                Object value = e.getValue();
                if (value instanceof String) {
                    if (value.equals("")) {
                        content.add(new JLabel(key), gbc);
                    } else {
                        content.add(new JLabel(key), gbk);
                        content.add(new JLabel((String)value), gbc);
                    }
                }
            }
        }
        if (featurelist && !aboutkeys.containsKey("NoFeatures")) {
            gbc.insets = new Insets(10, 0, 0, 0);

            JLabel featureLabel = new JLabel("Available Features");
            featureLabel.setFont(featureLabel.getFont().deriveFont(Font.BOLD));
            content.add(featureLabel, gbc);

            gbc.insets = new Insets(0, 0, 0, 0);
            JList<ViewerFeature> featureList = new JList<ViewerFeature>(viewer.getFeatures());
            featureList.setFont(featureList.getFont().deriveFont(featureList.getFont().getSize2D() * 0.75f));
            JScrollPane featureListScroll = new JScrollPane(featureList);
            featureListScroll.setPreferredSize(new Dimension(300, 70));
            content.add(featureListScroll, gbc);
        }

        dialog.setContentPane(content);
        dialog.setUndecorated(true);
        dialog.setResizable(false);
        dialog.pack();
        dialog.setLocationRelativeTo(viewer);
        if (autoclear!=0) {
            new Thread() {
                public void run() {
                    try { Thread.sleep(autoclear); } catch (InterruptedException e) { }
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            dialog.setVisible(false);
                            dialog.dispose();
                        }
                    });
                }
            }.start();
            dialog.addFocusListener(new FocusListener() {
                public void focusGained(FocusEvent event) {}
                public void focusLost(FocusEvent event) {
                    dialog.setVisible(false);
                    dialog.dispose();
                }
            });
        }
        content.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent event) {
                dialog.setVisible(false);
                dialog.dispose();
            }
        });
        dialog.setVisible(true);
        dialog.requestFocus();
    }

    private static String formatMemory(long mem) {
        return Math.round(mem/1024f/1024)+"MB";
    }

}
