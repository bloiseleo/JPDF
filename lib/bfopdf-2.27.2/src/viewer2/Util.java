// $Id: Util.java 41659 2021-11-10 09:23:06Z mike $

package org.faceless.pdf2.viewer2;

import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.concurrent.Callable;
import java.beans.*;
import java.net.*;
import java.io.*;
import javax.swing.*;
import javax.swing.filechooser.*;
import java.awt.*;
import java.awt.event.*;
import org.faceless.util.log.*;
import org.faceless.pdf2.PDF;
import org.faceless.pdf2.JSEngine;
import org.faceless.pdf2.PropertyManager;
import org.faceless.pdf2.viewer2.util.*;

/**
 * A utility class that handles localized Strings, display of
 * error messages and so on. Developers extending the viewer are
 * welcome to use this class
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.11 - created from the SuperJOptionPane class
 */
public final class Util {

    /**
     * The package the PDFViewer belongs to, which is "org.faceless.pdf2.viewer2"
     */
    public static final String PACKAGE;

    /**
     * The BFOLogger instance for the viewer
     */
    public static final BFOLogger LOGGER;

    /**
     * The Java major version, i.e. 5, 6, 7, 8 or 9 at the time of writing
     */
    public static final int JAVA_MAJOR_VERSION;

    /**
     * The Java minor version
     */
    public static final int JAVA_MINOR_VERSION;

    private static boolean canusecomposites = true;

    static {
        String name = PDFViewer.class.getName();
        PACKAGE = name.substring(0, name.lastIndexOf("PDFViewer") - 1);
        LOGGER = BFOLogger.getLogger(PACKAGE);
    }

    private Util() {
    }

    /**
     * Display an Error message, including the stack trace
     * @param throwable the Throwable object this error relates to
     * @param parent the parent component (not used)
     */
    public static void displayThrowable(final Throwable throwable, Component parent) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    displayThrowable(throwable, null);
                }
            });
            return;
        }
        Util.initialize();
        String title = getUIString("PDFViewer.Error");

        final JPanel panel = new JPanel(new BorderLayout());
        final JLabel label = new JLabel(getMessage(throwable, false));
        panel.add(label);
        final JButton detailbutton = new JButton(getUIString("PDFViewer.Show"));
        JOptionPane pane = new JOptionPane(panel, JOptionPane.ERROR_MESSAGE, JOptionPane.YES_NO_OPTION, null, new Object[] { detailbutton, getUIString("PDFViewer.Close") });

        final JDialog dialog = pane.createDialog(null, title);
        dialog.setResizable(true);
        dialog.setModal(true);

        JEditorPane detail = new JEditorPane();
        detail.setContentType("text/html");
        detail.setText(getMessage(throwable, true));
        final JScrollPane scrollpane = new JScrollPane(detail);
        scrollpane.setPreferredSize(new Dimension(400, 200));

        detailbutton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                try {
                    if (detailbutton.getText().equals(getUIString("PDFViewer.Show"))) {
                        panel.add(label, BorderLayout.NORTH);
                        panel.add(scrollpane);
                        detailbutton.setText(getUIString("PDFViewer.Hide"));
                    } else {
                        panel.remove(scrollpane);
                        panel.add(label);
                        detailbutton.setText(getUIString("PDFViewer.Show"));
                    }
                    dialog.pack();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
        LOGGER.warning("PV1", "Viewer Exception", throwable);
        dialog.setVisible(true);
    }

    private static String getMessage(Throwable e, boolean expanded) {
        StringBuilder msg = new StringBuilder();
        if (expanded) {
            msg.append("<html><body>");
            while (e != null) {
                msg.append("<b>"+e.getClass().getName()+"</b>");
                if (e.getMessage()!=null) {
                    msg.append(": "+e.getMessage());
                }
                msg.append("<pre style='font-size:90%'>\n");
                StackTraceElement[] s = e.getStackTrace();
                for (int i=0;i<s.length;i++) {
                    msg.append("\u2022 " + s[i].getClassName() + ".");
                    msg.append("<b>"+s[i].getMethodName().replaceAll("<", "&lt;").replaceAll(">", "&gt;")+"</b>()");
                    if (s[i].getFileName() != null) {
                        msg.append(" at <tt>"+s[i].getFileName().replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")+":"+s[i].getLineNumber()+"</tt>");
                    }
                    msg.append("\n");
                }
                msg.append("</pre>");
                e = e.getCause();
                if (e != null) {
                    msg.append("<br/><i>Caused by: </i>");
                }
            }
            msg.append("</body></html>");
        } else {
            String name = e.getClass().getName();
            name = name.substring(name.lastIndexOf('.') + 1);
            msg.append(name);
            if (e.getMessage()!=null) {
                msg.append(": "+e.getMessage());
            }
        }
        return msg.toString();
    }

    // To identify the Look & Feel - needed to work around bugs

    private static boolean isLAF(String classname) {
        LookAndFeel laf = UIManager.getLookAndFeel();
        if (laf == null) {
            return false;
        } else {
            // Slightly awkward construct to try to avoid Class.forName(classname),
            // which would trigger a classload: we try to avoid classloads as they
            // can cause issues when this code is being run from a restricted
            // environment (i.e. in JavaScript) or from Applets, where it triggers
            // network activity. No need for it here, we are only checking against
            // classes not interfaces.
            Class<?> c = laf.getClass();
            while (c != null && !c.getName().equals(classname)) {
                c = c.getSuperclass();
            }
            return c != null;
        }
    }

    /**
     * Return true if the LAF uses the Flat LaF toolkit
     */
    public static boolean isLAFFlat() {
        return isLAF("com.formdev.flatlaf.FlatLightLaf");
    }

    /**
     * Return true if the LAF uses the Aqua toolkit (OS X)
     */
    public static boolean isLAFAqua() {
        return isLAF("com.apple.laf.AquaLookAndFeel") || isLAF("apple.laf.AquaLookAndFeel");
    }

    /**
     * Return true if the LAF uses the GTK+ toolkit (UNIX)
     */
    public static boolean isLAFGTK() {
        return isLAF("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
    }

    /**
     * Return true if the LAF uses the Nimbus toolkit
     */
    public static boolean isLAFNimbus() {
        return isLAF("javax.swing.plaf.nimbus.NimbusLookAndFeel") || isLAF("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
    }

    /**
     * Return true if the LAF uses the Window toolkit
     */
    public static boolean isLAFWindows() {
        return isLAF("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
    }

    /**
     * Return true if the LAF uses the Metal toolkit
     */
    public static boolean isLAFMetal() {
        return isLAF("javax.swing.plaf.metal.MetalLookAndFeel");
    }

    /**
     * Initialize whatever needs initializing before any Swing objects are created.
     * Currently this just loads the localized Strings into the UIManager.
     */
    synchronized static void initialize() {
        if (UIManager.get("PDFViewer.NextPage") == null) {
            // To work around UIDefaults.addResourceBundle() not working in applets or JWS (newly filed bug)
            ResourceBundle bundle = ResourceBundle.getBundle(PACKAGE + ".resources.Text");
            for (Enumeration<?> e = bundle.getKeys();e.hasMoreElements();) {
                String key = (String)e.nextElement();
                if (UIManager.get(key) == null) {
                    UIManager.put(key, bundle.getString(key));
                }
            }
        }
        if (UIManager.get("PDFViewer.Feature.PageNext.icon") == null) {
            installUIDefaults(PACKAGE + ".resources.Defaults");
        }
    }

    /**
     * Return true if the specified component is contained within a JavaFX component
     * @param o the Component
     * @since 2.18.8
     */
    public static boolean isJavaFX(Component o) {
        try {
            Class<?> c = Class.forName("javafx.embed.swing.SwingNode");
            return SwingUtilities.getAncestorOfClass(c, o) != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Return true if the specified component is running in a JNLP environment, false otherwise
     * @param c the Component
     * @since 2.11.1
     */
    public static boolean isJNLP(Component c) {
        try {
            return javax.jnlp.ServiceManager.lookup("javax.jnlp.BasicService")!=null;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Display a URL in a web browser, if possible
     * @param url the URL to open
     * @param c the Component
     * @return true if the URL could be opened, false otherwise
     * @since 2.11.1
     */
    @SuppressWarnings("rawtypes")
    public static boolean openURL(URL url, Component c) {
        String msg = getUIString("PDFViewer.ConfirmOpen").replaceAll("\\{1\\}", url.toString());
        if (JOptionPane.showConfirmDialog(null, msg, msg, JOptionPane.YES_NO_OPTION)==JOptionPane.NO_OPTION) {
            return false;
        }
        try {
            java.awt.Desktop.getDesktop().browse(url.toURI());                                  // Java9
            return true;
        } catch (Throwable e) { }
        return false;
    }

    /**
     * Replace the fields {1}, {2} etc. in a String with their respective fields
     * @param message the message, eg "Loading {1} from {2}"
     * @param fields an array of N number of fields
     * @return the modified string
     */
    private static String replaceString(String message, String[] fields) {
        int i = 1, j;
        boolean found = false;
        while (i<=fields.length) {
            while ((j=message.indexOf("{"+i+"}"))>=0) {
                message = message.substring(0, j) + fields[i-1] + message.substring(j+3);
                found = true;
            }
            if (found) {
                found = false;
                i++;
            } else {
                break;
            }
        }
        return message;
    }

    /**
     * Return a UIManager string field. Identical to getUIString except
     * this method never returns null - if the String isn't found, the key is returned.
     * @since 2.16.2
     */
    public static String getUIString(String key) {
        // Note - Window L&F will return null for unknown keys here
        String s = UIManager.getString(key);
        if (s == null) {
            s = key;
        }
        return s;
    }

    /**
     * Return a UIManager string field with the specified fields replaced
     * @param key the message key,
     * @param field1 the field to be substituted for {1}
     * @return the modified string
     */
    public static String getUIString(String key, String field1) {
        return replaceString(getUIString(key), new String[] { field1 });
    }

    /**
     * Return a UIManager string field with the specified fields replaced
     * @param key the message key,
     * @param field1 the field to be substituted for {1}
     * @param field2 the field to be substituted for {2}
     * @return the modified string
     */
    public static String getUIString(String key, String field1, String field2) {
        return replaceString(getUIString(key), new String[] { field1, field2 });
    }

    /**
     * Return a UIManager string field with the specified fields replaced
     * @param key the message key,
     * @param field1 the field to be substituted for {1}
     * @param field2 the field to be substituted for {2}
     * @return the modified string
     */
    public static String getUIString(String key, String field1, String field2, String field3) {
        return replaceString(getUIString(key), new String[] { field1, field2, field3 });
    }

    /**
     * Remove the pageup/pagedown key bindings from ScrollPanes - they're clashing
     * with the pageup/pagedown we're mapping on the DocumentPanel
     */
    public static void fixScrollPaneKeyBindings(JScrollPane scrollpane) {
        InputMap inputmap;
        /*
        inputmap = scrollpane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).getParent();
        inputmap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0));
        inputmap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0));
        inputmap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0));
        inputmap.remove(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0));
        */
        inputmap = scrollpane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputmap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), "dummyAction");
        inputmap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), "dummyAction");
        inputmap.put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), "dummyAction");
        inputmap.put(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), "dummyAction");
    }

    /**
     * Attempt to convert a relative URL to an absolute one.
     * URL is assumed to be relative to the file.
     * @param docpanel the document panel
     * @param path the URL path
     */
    public static URL toURL(DocumentPanel docpanel, String path) {
        try {
            URL url;
            File base = docpanel.getAssociatedFile();
            if (base != null) {
                url = base.getParentFile().toURI().resolve(path).toURL();
            } else {
                url = new URL(path);
                if (url.getProtocol() == null) {
                    url = new File(path).toURI().toURL();
                }
            }
            return url;
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL \""+path+"\"", e);
        }
    }

    /**
     * Return true if the viewer can access the local filesystem
     */
    public static boolean hasFilePermission() {
        try {
            return JSEngine.doPrivileged(new Callable<Boolean>() {
                public Boolean call() {
                    try {
                        System.getProperty("user.home");
                        return Boolean.TRUE;
                    } catch (Throwable e) {
                        return Boolean.FALSE;
                    }
                }
            }).booleanValue();
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Return the standard #RRGGBB representation of a Color
     */
    public static String encodeColor(Color c) {
        char[] s = "0123456789ABCDEF".toCharArray();
        return "#"+s[c.getRed()>>4]+s[c.getRed()&15]+s[c.getGreen()>>4]+s[c.getGreen()&15]+s[c.getBlue()>>4]+s[c.getBlue()&15];
    }

    /**
     * Get the screen resolution. Same as Toolkit.getScreenResolution, except
     * it allows us to override it using the PDFViewer "dpi" property
     */
    public static int getScreenResolution(Component comp) {
        int dpi = 0;
        if (comp != null && !(comp instanceof PDFViewer)) {
            comp = SwingUtilities.getAncestorOfClass(PDFViewer.class, comp);
        }
        if (comp != null) {
            String prop = ((PDFViewer)comp).getPropertyManager().getProperty("dpi");
            if (prop != null) {
                try {
                    dpi = Integer.parseInt(prop);
                } catch (Exception e) { }
            }
        }
        if (dpi == 0) {
            dpi = comp == null ? Toolkit.getDefaultToolkit().getScreenResolution() : comp.getToolkit().getScreenResolution();
        }
        return dpi;
    }

    /**
     * <p>
     * Create a dialog for the specified component. This will
     * search up the Swing hierarchy until the first Frame, Dialog or Window
     * is found and return a dialog on that object. This caters for situations
     * where the viewer itself is running in a JDialog.
     * </p><p>
     * Note the JDialog(Window) constructor will do the same, but that's Java 6 only.
     * </p>
     * @param comp the Component creating the dialog
     * @param title the title of the dialog, or <code>null</code> for no title
     * @param modal whether the dialog is to be modal or not.
     * @since 2.13.1
     */
    public static JDialog newJDialog(Component comp, String title, boolean modal) {
        while (!(comp == null || comp instanceof Frame || comp instanceof Dialog || comp instanceof Window) && comp.getParent() != null) {
            comp = comp.getParent();
        }
        if (comp instanceof Dialog) {
            return new JDialog((Dialog)comp, title, modal);
        } else if (comp instanceof Frame) {
            return new JDialog((Frame)comp, title, modal);
        } else if (comp instanceof Window) {
            // This is Java 1.6 code only, but can't run in Java 5 as one of the above
            // tests would have succeeded.
            if (modal) {
                return new JDialog((Window)comp, title, Dialog.ModalityType.DOCUMENT_MODAL);    // Java16
            } else {
                return new JDialog((Window)comp, title, Dialog.ModalityType.MODELESS);          // Java16
            }
        }
        return new JDialog((Frame)null, title, modal);
    }

    /**
     * Attempt to repair a reported bug in the AWT classes where Icons returned
     * from  this class a null, causing a NPE deep inside the JFileChooser UI
     * classes.
     */
    public static JFileChooser fixFileChooser(JFileChooser chooser) {
        final FileView wrapped = chooser.getFileView();
        if (wrapped != null) {
            FileView view = new FileView() {
                public String getName(File f) {
                    return wrapped.getName(f);
                }
                public String getDescription(File f) {
                    return wrapped.getDescription(f);
                }
                public String getTypeDescription(File f) {
                    return wrapped.getTypeDescription(f);
                }
                public Boolean isTraversable(File f) {
                    return wrapped.isTraversable(f);
                }
                public Icon getIcon(File f) {
                    Icon icon = wrapped.getIcon(f);
                    if (icon == null) {
                        new Exception("FileView found no Icon for "+f).printStackTrace();
                        if (f == null) {
                            return UIManager.getIcon("html.missingImage");
                        }
                    }
                    return icon;
                }
            };
            chooser.setFileView(view);
        }
        return chooser;
    }

    /**
     * Return the TransferHandler to use when copying/pasting text.
     * By default this is a {@link RichTextTransferHandler}, but this
     * can be overridden by setting the property "TransferHandler" on
     * the PDFViewer {@link PDFViewer#getPropertyManager PropertyManager}.
     * to the full class-name of the TransferHandler.
     * @since 2.16.1
     */
    public static TransferHandler createTransferHandler(PDFViewer viewer) {
        PropertyManager manager = viewer == null ? PDF.getPropertyManager() : viewer.getPropertyManager();
        String classname = manager.getProperty("TransferHandler");
        TransferHandler handler = null;
        if (classname != null) {
            try {
                handler = (TransferHandler)Class.forName(classname).getDeclaredConstructor().newInstance();
            } catch (Throwable e) { }
        }
        if (handler == null) {
            handler = new RichTextTransferHandler();
        }
        return handler;
    }

    /**
     * Set the supplied component to gain focus when it's newly displayed, even inside
     * a sub-pane in a popup window.
     * @param component the component
     */
    public static void setAutoFocusComponent(final JComponent component) {
        // This is way harder than it should be to capture focus when we're displayed...
        component.requestFocusInWindow();
        component.addHierarchyListener(new HierarchyListener() {
            boolean added = false;
            public void hierarchyChanged(HierarchyEvent event) {
                if (component.isShowing()) {
                    component.requestFocus();
                } else if (!added) {
                    Window window = (Window)SwingUtilities.getAncestorOfClass(Window.class, component);
                    if (window != null) {
                        //Make textField get the focus whenever frame is activated.
                        window.addWindowFocusListener(new WindowAdapter() {
                            public void windowGainedFocus(WindowEvent e) {
                                if (component.isShowing()) {
                                    component.requestFocus();
                                }
                            }
                        });
                        added = true;
                    }
                }
            }
        });
    }

    /**
     * Insert an "Option pane" into the JFileChooser. This has to be done differently for each 
     * look and feel - if it can't be done, this method will return false.
     * Don't blame us for this horror, blame Sun.
     * @since 2.16.2
     */
    public static boolean patchJFileChooser(JFileChooser chooser, JComponent patch, boolean add) {
        // Mike gets 1000 ugly points for this. Tested under Java7/Windows, Java8/OSX (Aqua, Nimbus)
        String options = getUIString("Options");
        options += ":";
        String ui = chooser.getUI().getClass().getName();
        try {
            if (ui.equals("com.apple.laf.AquaFileChooserUI")) {                                 // OSX
                if (add) {
                    JPanel comp = (JPanel)((JPanel)chooser.getComponent(4)).getComponent(0);
                    if (comp.getComponent(1) instanceof JComboBox) {
                        JPanel newchooserpanel = new JPanel(new FlowLayout());
                        while (comp.getComponentCount() > 0) {
                            newchooserpanel.add(comp.getComponent(0));
                        }
                        comp.setLayout(new BoxLayout(comp, BoxLayout.Y_AXIS));
                        JPanel p2 = new JPanel(new FlowLayout());
                        p2.add(patch);
                        comp.add(p2);
                        comp.add(newchooserpanel);
                        comp.revalidate();
                        return true;
                    }
                } else {
                    JPanel comp = (JPanel)((JPanel)chooser.getComponent(4)).getComponent(0);
                    JPanel subcomp = (JPanel)comp.getComponent(1);
                    comp.removeAll();
                    while (subcomp.getComponentCount() > 0) {
                        comp.add(subcomp.getComponent(0));
                    }
                    comp.setLayout(new FlowLayout());
                    comp.revalidate();
                    return true;
                }
            } else if (ui.equals("sun.swing.plaf.synth.SynthFileChooserUIImpl") || ui.equals("javax.swing.plaf.metal.MetalFileChooserUI")) {              // Nimbus / Metal
                JPanel comp = (JPanel)chooser.getComponent(3);
                if (add) {
                    if (((JPanel)comp.getComponent(2)).getComponent(1) instanceof JComboBox) {
                        JPanel newpanel = new JPanel();
                        newpanel.setLayout(new BoxLayout(newpanel, BoxLayout.X_AXIS));
                        JLabel label = new JLabel(options) {
                            @Override public Dimension getPreferredSize() {
                                try {
                                    return ((JLabel)((JPanel)getParent().getParent().getComponent(2)).getComponent(0)).getPreferredSize();
                                } catch (Exception e) {
                                    return super.getPreferredSize();
                                }
                            }
                        };
                        label.setAlignmentX(JComponent.LEFT_ALIGNMENT);
                        newpanel.add(label);
                        JPanel p2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
                        p2.add(patch);
                        newpanel.add(p2);
                        Dimension d = new Dimension(1, 5);
                        comp.add(newpanel, 0);
                        comp.add(new Box.Filler(d, d, d), 1);
                        comp.revalidate();
                        return true;
                    }
                } else {
                    comp.remove(0);
                    comp.remove(0);
                    comp.revalidate();
                    return true;
                }
            } else if (ui.equals("com.sun.java.swing.plaf.windows.WindowsFileChooserUI")) {     // Windows / Windows Classic
                JPanel comp = (JPanel)chooser.getComponent(2);
                if (add) {
                    if (((JPanel)((JPanel)comp.getComponent(2)).getComponent(0)).getComponent(1) instanceof JLabel) {
                        JPanel newpanel = new JPanel();
                        newpanel.setLayout(new BoxLayout(newpanel, BoxLayout.Y_AXIS));
                        Dimension d = new Dimension(1, 4);
                        newpanel.add(new Box.Filler(d, d, d));

                        JPanel newpanel2 = new JPanel();
                        newpanel2.setLayout(new BoxLayout(newpanel2, BoxLayout.X_AXIS));
                        JLabel label = new JLabel(options) {
                            @Override public Dimension getPreferredSize() {
                                try {
                                    JPanel p1 = (JPanel)getParent().getParent().getComponent(2);
                                    JPanel p2 = (JPanel)p1.getComponent(0);
                                    Dimension d = new Dimension(p2.getPreferredSize());
                                    d.height = super.getPreferredSize().height;
//                                    return ((JLabel)((JPanel)((JPanel)getParent().getParent().getComponent(2)).getComponent(0)).getComponent(3)).getPreferredSize();
                                    return d;
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    return super.getPreferredSize();
                                }
                            }
                        };
                        newpanel2.add(label);
                        d = new Dimension(15, 0);
                        newpanel2.add(new Box.Filler(d, d, d));
                        JPanel p2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
                        p2.add(patch);
                        newpanel2.add(p2);

                        newpanel.add(newpanel2);
                        newpanel.add(comp.getComponent(2));
                        comp.add(newpanel, BorderLayout.SOUTH);
                        comp.revalidate();
                        return true;
                    }
                } else {
                    Component orig = ((JPanel)comp.getComponent(2)).getComponent(2);
                    comp.remove(2);
                    comp.add(orig, BorderLayout.SOUTH);
                    comp.revalidate();
                }
            }
        } catch (Throwable e) {
            return false;       // Lint
        }
        return false;
    }

    /**
     * Return the JTextField containing the current filename from the JFileChooser,
     * or null if this can't be determined for the current look-and-feel.
     * @since 2.16.2
     */
    public static JTextField getJFileChooserFileName(JFileChooser chooser) {
        String ui = chooser.getUI().getClass().getName();
        try {
            if (ui.equals("com.apple.laf.AquaFileChooserUI")) {                                 // OSX
                JPanel p1 = (JPanel)chooser.getComponent(1);
                JPanel p2 = (JPanel)p1.getComponent(0);
                JPanel p3 = (JPanel)p2.getComponent(0);
                JPanel p4 = (JPanel)p3.getComponent(0);
                return (JTextField)p4.getComponent(1);
            } else if (ui.equals("sun.swing.plaf.synth.SynthFileChooserUIImpl") || ui.equals("javax.swing.plaf.metal.MetalFileChooserUI")) {              // Nimbus / Metal
                JPanel p1 = (JPanel)chooser.getComponent(3);
                JPanel p2 = (JPanel)p1.getComponent(0);
                return (JTextField)p2.getComponent(1);
            } else if (ui.equals("com.sun.java.swing.plaf.windows.WindowsFileChooserUI")) {     // Windows / Windows Classic
                JPanel p1 = (JPanel)chooser.getComponent(2);
                JPanel p2 = (JPanel)p1.getComponent(2);
                JPanel p3 = (JPanel)p2.getComponent(2);
                return (JTextField)p3.getComponent(1);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    /**
     * Get the top-level JComponent above the specified one - typically a JWindow or JFrame
     */
    public static JComponent getRootAncestor(JComponent c) {
        while (c.getParent() instanceof JComponent) {
            c = (JComponent)c.getParent();
        }
        return c;
    }

    public static void setCanUseComposites(boolean comp) {
        canusecomposites &= comp;  // Can only make false
    }

    /**
     * Return true if the AWT can use custom Composites. OSX and Ubuntu
     * can't up to and including Java 8, they throw an "Not Implemented" InternalError.
     */
    public static boolean canUseComposites() {
        return canusecomposites;
    }

    /**
     * Install a property file that controls the appearance of the viewer.
     * This uses the same syntax as the Property files from
     * <a href="https://www.formdev.com/flatlaf/properties-files/">FlatLAF</a>.
     * @since 2.26
     */
    public static void installUIDefaults(String property) {
        UIDefaultsLoader.install(property);
    }

    /** 
     * <p>
     * Scale all the fonts used in Swing by the specified scale.
     * Utility method which may be called any time from any code,
     * but specifically will be called if the "fontScale" property
     * is set on the viewer.
     * </p><p>
     * Inspired by <a href="https://stackoverflow.com/questions/34149453/java-swing-application-too-small-in-hidpi-computers">this StackOverflow</a> posting.
     * </p>
     * @param scale the font scale
     */
    public static void setFontScale(float scale) {
        Map<String,Font> originals = new HashMap<String,Font>(25);
        for (Map.Entry<Object,Object> entry : UIManager.getDefaults().entrySet()) {
            Object key = entry.getKey();
            if (key.toString().toLowerCase().contains(".font")) {
                Object value = entry.getValue();
                Font font = null;
                if (value.getClass().getName().equals("sun.swing.SwingLazyValue")) {
                    try {
                        value = value.getClass().getMethod("createValue", UIDefaults.class).invoke(value, UIManager.getDefaults());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (value instanceof Font) {
                    font = (Font) value;
                    originals.put(key.toString(), font);
                }
            }
        }

        for (Map.Entry<String, Font> entry : originals.entrySet()) {
            String key = entry.getKey();
            Font font = entry.getValue();

            float size = font.getSize();
            size *= scale;

            font = font.deriveFont(Font.PLAIN, size);
            UIManager.put(key, font);
        }
        UIManager.put("Tree.rowHeight", UIManager.getInt("Tree.rowHeight") * scale);
    }

    @SuppressWarnings("deprecation")
    public static int getMenuShortcutKeyMask(JComponent c) {
        return c.getToolkit().getMenuShortcutKeyMask();
    }

    static {
        // Trying to parse this with beta versions is risky.
        int major = 8, minor = 0;
        String v = System.getProperty("java.version");
        try {
            StringTokenizer st = new StringTokenizer(v, ".");
            String s1 = st.nextToken();
            String s2 = st.hasMoreTokens() ? st.nextToken() : null;
            if (s1.endsWith("ea")) {
                s1 = s1.substring(0, s1.length() - 2);
            }
            major = Integer.parseInt(s1);
            minor = s2 == null ? 0 : Integer.parseInt(s2);
        } catch (Exception e) { }
        JAVA_MAJOR_VERSION = major;
        JAVA_MINOR_VERSION = minor;
    }


}
