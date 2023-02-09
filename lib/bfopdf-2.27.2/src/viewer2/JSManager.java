// $Id: JSManager.java 41659 2021-11-10 09:23:06Z mike $

package org.faceless.pdf2.viewer2;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.concurrent.Callable;
import java.text.SimpleDateFormat;
import java.awt.event.*;
import java.awt.geom.Point2D;
import org.faceless.pdf2.*;

/**
 * <p>
 * Handles the <i>Event</i> - primarily JavaScript events - raised during the lifetime of
 * the viewer. This class will raise the PDF lifetime events defined in the
 * Acrobat JavaScript Scripting Reference, which may have {@link PDFAction} objects
 * associated with them. These are unrelated to Swing events and to the
 * {@link DocumentPanelEvent}, {@link PagePanelEvent} and similar.
 * </p><p>
 * Since 2.11.18 is it possible to configure custom JavaScript that will run on certain
 * events. This can be done by either calling the {@link #addCustomJavaScript} method,
 * or by setting a System property. As an example, here's how to run one custom scripts
 * on App/Init and Doc/Open events:
 * <pre class="brush:shell">
 *  java -Dorg.faceless.pdf2.viewer2.JSManager.AppInit=myscripts/appinit.js \
 *       -Dorg.faceless.pdf2.viewer2.JSManager.DocOpen=myscripts/docopen.js \
 *       org.faceless.pdf2.viewer2.PDFViewer
 * </pre>
 * <p>
 * The <code>value</code> attribute is the path to a JavaScript file, the contents
 * of which will be executed on the specified event
 * </p><p>
 * With the exception of the above, we recommend developers and those working with the
 * viewer steer well clear of calling into this class, as the API is subject to change
 * without notice.
 * </p>
 * @since 2.9
 */
public class JSManager extends JSCoreMethods {

    private Map<String,String> custom, localcustom;
    private WeakHashMap<DocumentPanel,Point2D> lastmousepos;
    private final JComponent root;
    private JSEngine engine;
    private JDialog dialog;
    private JTextArea textarea;
    private boolean initialized;
    private final boolean debug;
    private HashMap<PDF,DocumentPanel> docpanels;          // Map of PDF->DocPanel, for redrawing
    private Preferences preferences;
    private static final int maxhistory = 20;
    private ArrayList<String> history;
    private int historyix;

    // State
    private Boolean appRuntimeHighlight;
    private Boolean appFocusRect;
    private Color appRuntimeHighlightColor;

    JSManager(final JComponent root) {
        this.root = root;
        textarea = new JTextArea();
        textarea.setFont(new Font("Monospaced", Font.PLAIN, 10));
        textarea.setLineWrap(true);
        textarea.setEditable(false);
        textarea.setWrapStyleWord(true);
        textarea.setAutoscrolls(true);
        docpanels = new HashMap<PDF,DocumentPanel>();
        custom = new HashMap<String,String>();
        localcustom = new HashMap<String,String>();
        lastmousepos = new WeakHashMap<DocumentPanel,Point2D>();
        PropertyManager manager;
        if (root instanceof PDFViewer) {
            preferences = ((PDFViewer)root).getPreferences();
            manager = ((PDFViewer)root).getPropertyManager();
        } else {
            manager = PDF.getPropertyManager();
        }
        String[] events = new String[] { "App/Init", "Doc/DidPrint", "Doc/DidSave", "Doc/Open", "Doc/WillClose", "Doc/WillPrint", "Doc/WillSave"  };
        for (int i=0;i<events.length;i++) {
            try {
                URL url = manager.getURLProperty("JSManager."+events[i].replaceAll("/",""));
                if (url != null) {
                    addCustomJavaScript(events[i], url);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        debug = manager.getProperty("debug.JSEvent") != null;
    }

    private JSEngine getEngine() {
        if (engine==null) {
            engine = new JSEngine(this);
        }
        return engine;
    }

    /**
     * Return the PDFViewer this JSManager is a part of
     * @since 2.11
     */
    public final PDFViewer getViewer() {
        return root instanceof PDFViewer ? (PDFViewer)root : null;
    }

    /**
     * Return the DocumentPanel that contains the specified PDF
     * @since 2.11
     */
    public final DocumentPanel getDocumentPanel(PDF pdf) {
        return root instanceof DocumentPanel ? (DocumentPanel)root : (DocumentPanel)docpanels.get(pdf);
    }

    /**
     * Return a description of the JavaScript implementation in use
     */
    public String getImplementationDescription() {
        return getEngine().getImplementationDescription();
    }

    /**
     * Return the JComponent matchin the specified PDFAnnotation
     */
    public final Object getComponent(PDFAnnotation annot) {
        PDF pdf = annot.getPage().getPDF();
        DocumentPanel docpanel = getDocumentPanel(pdf);
        if (docpanel != null) {
            for (Iterator<PagePanel> i = docpanel.getViewport().getPagePanels().iterator();i.hasNext();) {
                PagePanel panel = i.next();
                if (panel.getPage() == annot.getPage()) {
                    return panel.getAnnotationComponents().get(annot);
                }
            }
        }
        return null;
    }

    private void storeMouseEvent(DocumentPanel docpanel, WidgetAnnotation annot, MouseEvent e) {
        PagePanel pagepanel = (PagePanel)SwingUtilities.getAncestorOfClass(PagePanel.class, e.getComponent());
        if (pagepanel != null) {
            Point awtpoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), pagepanel);
            Point2D pdfpoint = pagepanel.getPDFPoint(awtpoint);
            lastmousepos.put(docpanel, pdfpoint);
        }
    }

    /**
     * Get the point in PDF space where the last mouse event (MouseEnter, MouseExit, MouseUp, MouseDown) occurred
     */
    public Point2D getLastMousePosition(PDF pdf) {
        Point2D p = (Point2D)lastmousepos.get(getDocumentPanel(pdf));
        if (p == null) {
            p = new Point2D.Float(0 ,0);
        }
        return p;
    }

    //----------------------------------------------------------------------

    /**
     * An implementation of the <code>App.beep</code> JavaScript method
     */
    public void appBeep() {
        root.getToolkit().beep();
    }

    /**
     * An implementation of the <code>Console.show</code> JavaScript method
     */
    public synchronized void consoleShow() {
        if (dialog == null) {
            try {
                JSEngine.doPrivileged(new Callable<Void>() {
                    public Void call() {
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                dialog = new JDialog(JOptionPane.getFrameForComponent(root), UIManager.getString("PDFViewer.JavaScriptConsole"), false);
                                final JPanel body = new JPanel(new GridBagLayout());
                                GridBagConstraints gbc = new GridBagConstraints();
                                gbc.fill = gbc.BOTH;
                                gbc.weighty = 1;
                                gbc.gridwidth = gbc.REMAINDER;

                                final JScrollPane scroll = new JScrollPane(textarea);
                                scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
                                scroll.setPreferredSize(new Dimension(750, 300));
                                body.add(scroll, gbc);
                                gbc.weightx = 1;
                                gbc.weighty = 0;
                                gbc.gridwidth = 1;

                                final JTextField field = new JTextField() {
                                    public boolean isFocusable() {
                                        return getViewer().getActiveDocumentPanel()!=null;
                                    }
                                };
                                field.addActionListener(new ActionListener() {
                                    public void actionPerformed(ActionEvent evt) {
                                        String text = field.getText();
                                        if (text.equals("// clearhistory")) {
                                            field.setText("");
                                            addHistory(null);
                                        } else if (text.trim().length() > 0) {
                                            runEventConsoleExec(getViewer().getActiveDocumentPanel(), text);
                                            field.setText("");
                                            addHistory(text);
                                        }
                                    }
                                });
                                field.getActionMap().put("UpArrow", new AbstractAction() {
                                    public void actionPerformed(ActionEvent e) {
                                        ArrayList<String> history = getHistory();
                                        if (historyix < history.size() - 1) {
                                            if (historyix == 0) {
                                                history.set(0, field.getText());
                                            }
                                            field.setText(history.get(++historyix));
                                        } else {
                                            appBeep();
                                        }
                                    }
                                });
                                field.getActionMap().put("DownArrow", new AbstractAction() {
                                    public void actionPerformed(ActionEvent e) {
                                        ArrayList<String> history = getHistory();
                                        if (historyix > 0) {
                                            field.setText(history.get(--historyix));
                                        } else {
                                            appBeep();
                                        }
                                    }
                                });
                                field.getInputMap().put(KeyStroke.getKeyStroke("UP"), "UpArrow");
                                field.getInputMap().put(KeyStroke.getKeyStroke("DOWN"), "DownArrow");
                                body.add(field, gbc);

                                gbc.weightx = 0;
                                final JButton clearbutton = new JButton(UIManager.getString("PDFViewer.Clear"));
                                clearbutton.addActionListener(new ActionListener() {
                                    public void actionPerformed(ActionEvent evt) {
                                        consoleClear();
                                    }
                                });
                                body.add(clearbutton, gbc);
                                final JButton markbutton = new JButton(UIManager.getString("PDFViewer.Mark"));
                                markbutton.addActionListener(new ActionListener() {
                                    public void actionPerformed(ActionEvent evt) {
                                        consoleMark();
                                    }
                                });
                                body.add(markbutton, gbc);
                                final JButton hidebutton = new JButton(UIManager.getString("PDFViewer.Hide"));
                                hidebutton.addActionListener(new ActionListener() {
                                    public void actionPerformed(ActionEvent evt) {
                                        consoleHide();
                                    }
                                });
                                body.add(hidebutton, gbc);
                                dialog.setContentPane(body);
                                dialog.setResizable(true);
                                dialog.pack();
                                dialog.setLocationRelativeTo(root);
                                dialog.addWindowListener(new WindowAdapter() {
                                    public void windowClosing(WindowEvent evt) {
                                        consoleHide();
                                    }
                                });
                                dialog.setVisible(true);
                            }
                        });
                        return null;
                    }
                });
            } catch (Exception e) {
                throw (RuntimeException)e;
            }
        }
    }

    private ArrayList<String> getHistory() {
        if (history == null) {
            if (preferences != null) {
                String hisvalue = preferences.get("JSManager.history", "");
                List<String> t = Arrays.asList(hisvalue.split("\n"));
                history = new ArrayList<String>(t.size() + 1);
                history.add("");
                history.addAll(t);
            } else {
                history = new ArrayList<String>();
                history.add("");
            }
        }
        return history;
    }

    private void addHistory(String s) {
        ArrayList<String> history = getHistory();
        if (s == null) {
            history.subList(1, history.size()).clear();
        } else {
            history.set(0, "");
            history.add(1, s);
            if (history.size() == maxhistory) {
                history.remove(history.size() - 1);
            }
        }
        if (preferences != null) {
            StringBuilder hisvalue = new StringBuilder();
            for (int i=1;i<history.size();i++) {
                if (i != 1) {
                    hisvalue.append("\n");
                }
                hisvalue.append(history.get(i));
            }
            preferences.put("JSManager.history", hisvalue.toString());
        }
        historyix = 0;
    }

    /**
     * An implementation of the <code>Console.hide</code> JavaScript method
     */
    public synchronized void consoleHide() {
        if (dialog != null) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    dialog.setVisible(false);
                    dialog = null;
                    if (preferences != null) {
                        try {
                            preferences.flush();
                        } catch (Exception e) { }
                    }
                }
            });
        }
    }

    /**
     * An implementation of the <code>Console.println</code> JavaScript method
     */
    public void consolePrintln(final String message) {
        textarea.append(message+"\n");  // Threadsafe
        textarea.setCaretPosition(textarea.getDocument().getLength());  // Scroll
    }

    /**
     * An implementation of the <code>Console.clear</code> JavaScript method
     */
    public void consoleClear() {
        textarea.setText("");   // Threadsafe
    }

    private void consoleMark() {
        String s = "-- MARK "+new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        DocumentPanel panel = getViewer().getActiveDocumentPanel();
        s += ": BFO "+PDF.VERSION+" on "+getEngine().getImplementationDescription();
        File file = null;
        if (panel != null && panel.getPDF() != null && (file=panel.getAssociatedFile()) != null) {
            s += ": \"" + file.getName() + "\"";
        }
        consolePrintln(s);
        consolePrintln("");
    }

    /**
     * An implementation of the <code>App.alert</code> JavaScript method
     */
    public int appAlert(String message, int nIcon, int nType, final String cTitle, Object oDoc, Object oCheckbox) {
        final JOptionPane pane = new JOptionPane() {
            public int getMaxCharactersPerLineCount() {
                return 60;
            }
        };
        pane.setMessage(message);

        if (nIcon == 1) {
            pane.setMessageType(JOptionPane.WARNING_MESSAGE);
        } else if (nIcon == 2) {
            pane.setMessageType(JOptionPane.QUESTION_MESSAGE);
        } else if (nIcon == 3) {
            pane.setMessageType(JOptionPane.INFORMATION_MESSAGE);
        } else {
            pane.setMessageType(JOptionPane.ERROR_MESSAGE);
        }

        if (nType == 1) {
            pane.setOptionType(JOptionPane.OK_CANCEL_OPTION);
        } else if (nType == 2) {
            pane.setOptionType(JOptionPane.YES_NO_OPTION);
        } else if (nType == 3) {
            pane.setOptionType(JOptionPane.YES_NO_CANCEL_OPTION);
        } else {
            pane.setOptionType(JOptionPane.DEFAULT_OPTION);
        }

        try {
            JSEngine.doPrivileged(new Callable<Void>() {
                public Void call() {
                    pane.createDialog(root, cTitle==null ? "JavaScript" : cTitle).setVisible(true);
                    return null;
                }
            });
        } catch (Exception e) {
            throw (RuntimeException)e;
        }
        int val = ((Integer)pane.getValue()).intValue();
        if (val == JOptionPane.OK_OPTION) {
            return 1;
        } else if (val==JOptionPane.CANCEL_OPTION) {
            return 2;
        } else if (val==JOptionPane.NO_OPTION) {
            return 3;
        } else if (val==JOptionPane.YES_OPTION) {
            return 4;
        } else {
            return 0;
        }
    }

    /**
     * An implementation of the <code>App.response</code> JavaScript method
     */
    public String appResponse(String message, String cTitle, String cDefault, boolean bPassword, String cLabel) {
        return (String)JOptionPane.showInputDialog(root, message, cTitle, JOptionPane.PLAIN_MESSAGE, null, null, cDefault);
    }


    //-----------------------------------------------------------------------------------

    private synchronized void runEvent(DocumentPanel panel, JSEvent event) {
        if (panel != null && panel.getPDF() == null) {   // Don't run if no PDF, we've returned.
            return;
        }
        if (debug) {
            if (SwingUtilities.isEventDispatchThread()) {
                System.err.println("Raise JS "+event);
            } else {
                System.err.println("Raise JS "+event+" (not in event thread)");
            }
        }
        int index = 0;
        PDFAction action = event.javaGetAction();
        do {
            if (index == 0 || "FormJavaScript".equals(action.getType())) {
                getEngine().runEvent(event, index);
            }
            if (action != null && panel != null && !"FormJavaScript".equals(action.getType())) {
                panel.runAction(action);
            }
            action = action == null ? null : action.getNext();
            index++;
        } while (action != null);
    }

    private boolean isShift(InputEvent event) {
        return event.isShiftDown();
    }

    private boolean isModifier(InputEvent event) {
        try {
            return (event.isControlDown() && System.getProperty("os.name").indexOf("Windows")>=0) ||
                    (event.isMetaDown() && System.getProperty("os.name").indexOf("Windows")<0);
        } catch (Throwable e) {
            return event.isControlDown();
        }
    }

    /**
     * Raise the <code>App/Init</code> JavaScript method
     */
    public synchronized void runEventAppInit() {
        if (!initialized) {             // So we can only run this once per manager
            initialized = true;
            JSEvent event = JSEvent.createAppInit();
            runEvent(null, event);
        }
    }

    /**
     * Raise the <code>Batch/Exec</code> JavaScript method
     */
    public JSEvent runEventBatchExec(DocumentPanel panel, PDF pdf, String javascript) {
        JSEvent event = JSEvent.createBatchExec(pdf, javascript);
        runEvent(panel, event);
        return event;
    }

    /**
     * Raise the <code>Bookmark/MouseUp</code> JavaScript method
     */
    public JSEvent runEventBookmarkMouseUp(DocumentPanel panel, PDFBookmark bookmark) {
        JSEvent event = JSEvent.createBookmarkMouseUp(bookmark);
        runEvent(panel, event);
        return event;
    }

    /**
     * Raise the <code>Console/Exec</code> JavaScript method
     */
    public JSEvent runEventConsoleExec(DocumentPanel panel, String javascript) {
        JSEvent event = JSEvent.createConsoleExec(panel.getPDF(), javascript);
        runEvent(panel, event);
        return event;
    }

    /**
     * Raise the <code>Doc/DidPrint</code> JavaScript method
     */
    public JSEvent runEventDocDidPrint(DocumentPanel panel) {
        JSEvent event = JSEvent.createDocDidPrint(panel.getPDF());
        runEvent(panel, event);
        return event;
    }

    /**
     * Raise the <code>Doc/DidSave</code> JavaScript method
     */
    public JSEvent runEventDocDidSave(DocumentPanel panel) {
        JSEvent event = JSEvent.createDocDidSave(panel.getPDF());
        runEvent(panel, event);
        return event;
    }

    /**
     * Raise the <code>Doc/Open</code> JavaScript method
     */
    public JSEvent runEventDocOpen(DocumentPanel panel, String targetName) {
        docpanels.put(panel.getPDF(), panel);
        JSEvent event = JSEvent.createDocOpen(panel.getPDF(), targetName);
        runEvent(panel, event);
        return event;
    }

    /**
     * Raise the <code>Doc/WillClose</code> JavaScript method
     */
    public JSEvent runEventDocWillClose(DocumentPanel panel) {
        docpanels.remove(panel.getPDF());
        JSEvent event = JSEvent.createDocWillClose(panel.getPDF());
        runEvent(panel, event);
        return event;
    }

    /**
     * Raise the <code>Doc/WillPrint</code> JavaScript method
     */
    public JSEvent runEventDocWillPrint(DocumentPanel panel) {
        JSEvent event = JSEvent.createDocWillPrint(panel.getPDF());
        runEvent(panel, event);
        return event;
    }

    /**
     * Raise the <code>Doc/WillSave</code> JavaScript method
     */
    public JSEvent runEventDocWillSave(DocumentPanel panel) {
        JSEvent event = JSEvent.createDocWillSave(panel.getPDF());
        runEvent(panel, event);
        return event;
    }

    /**
     * Raise the <code>External/Exec</code> JavaScript method
     */
    public JSEvent runEventExternalExec(DocumentPanel panel, String javascript) {
        JSEvent event = JSEvent.createExternalExec(javascript);
        runEvent(panel, event);
        return event;
    }

    /**
     * Raise the <code>Field/Blur</code> JavaScript method
     */
    public JSEvent runEventFieldBlur(DocumentPanel panel, WidgetAnnotation annot, boolean shift, boolean modifier) {
        JSEvent event = JSEvent.createFieldBlur(annot, shift, modifier);
        runEvent(panel, event);
        return event;
    }

    /**
     * Raise the <code>Field/Calculate</code> JavaScript method
     */
    public JSEvent runEventFieldCalculate(DocumentPanel panel, WidgetAnnotation target, WidgetAnnotation source) {
        JSEvent event = JSEvent.createFieldCalculate(target, source);
        runEvent(panel, event);
        return event;
    }

    /**
     * Raise the <code>Field/Focus</code> JavaScript method
     */
    public JSEvent runEventFieldFocus(DocumentPanel panel, WidgetAnnotation annot, boolean shift, boolean modifier) {
        JSEvent event = JSEvent.createFieldFocus(annot, shift, modifier);
        runEvent(panel, event);
        return event;
    }

    /**
     * Raise the <code>Field/Format</code> JavaScript method
     */
    public JSEvent runEventFieldFormat(DocumentPanel panel, WidgetAnnotation annot, int commitKey, boolean willCommit) {
        JSEvent event = JSEvent.createFieldFormat(annot, commitKey, willCommit);
        runEvent(panel, event);
        return event;
    }

    /**
     * Raise the <code>Field/Keystroke</code> JavaScript method
     */
    public JSEvent runEventFieldKeystroke(DocumentPanel panel, WidgetAnnotation annot, int commitKey, String change, String changeEx, boolean fieldFull, boolean keyDown, boolean modifier, int selStart, int selEnd, boolean shift, String value, boolean willCommit) {
        JSEvent event = JSEvent.createFieldKeystroke(annot, commitKey, change.toString(), changeEx, fieldFull, keyDown, modifier, selStart, selEnd, shift, value, willCommit);
        runEvent(panel, event);
        return event;
    }

    /**
     * Raise the <code>Field/Mouse Down</code> JavaScript method
     */
    public JSEvent runEventFieldMouseDown(DocumentPanel panel, WidgetAnnotation annot, MouseEvent mevent) {
        storeMouseEvent(panel, annot, mevent);
        boolean modifier = isModifier(mevent);
        boolean shift = isShift(mevent);
        JSEvent event = JSEvent.createFieldMouseDown(annot, shift, modifier);
        runEvent(panel, event);
        return event;
    }

    /**
     * Raise the <code>Field/Mouse Enter</code> JavaScript method
     */
    public JSEvent runEventFieldMouseEnter(DocumentPanel panel, WidgetAnnotation annot, MouseEvent mevent) {
        storeMouseEvent(panel, annot, mevent);
        boolean modifier = isModifier(mevent);
        boolean shift = isShift(mevent);
        JSEvent event = JSEvent.createFieldMouseEnter(annot, shift, modifier);
        runEvent(panel, event);
        return event;
    }

    /**
     * Raise the <code>Field/Mouse Exit</code> JavaScript method
     */
    public JSEvent runEventFieldMouseExit(DocumentPanel panel, WidgetAnnotation annot, MouseEvent mevent) {
        storeMouseEvent(panel, annot, mevent);
        boolean modifier = isModifier(mevent);
        boolean shift = isShift(mevent);
        JSEvent event = JSEvent.createFieldMouseExit(annot, shift, modifier);
        runEvent(panel, event);
        return event;
    }

    /**
     * Raise the <code>Field/Mouse Up</code> JavaScript method
     */
    public JSEvent runEventFieldMouseUp(DocumentPanel panel, WidgetAnnotation annot, MouseEvent mevent) {
        storeMouseEvent(panel, annot, mevent);
        boolean modifier = isModifier(mevent);
        boolean shift = isShift(mevent);
        JSEvent event = JSEvent.createFieldMouseUp(annot, shift, modifier);
        runEvent(panel, event);
        return event;
    }

    /**
     * Raise the <code>Field/Validate</code> JavaScript method
     */
    public JSEvent runEventFieldValidate(DocumentPanel panel, WidgetAnnotation annot, String value, boolean shift, boolean modifier, String change, String changeEx, boolean keyDown) {
        JSEvent event = JSEvent.createFieldValidate(annot, value, shift, modifier, change, changeEx, keyDown);
        runEvent(panel, event);
        return event;
    }

    /**
     * Raise the <code>Link/Mouse Up</code> JavaScript method
     */
    public JSEvent runEventLinkMouseUp(DocumentPanel panel, AnnotationLink annot) {
        JSEvent event = JSEvent.createLinkMouseUp(annot);
        runEvent(panel, event);
        return event;
    }

    /**
     * Raise the <code>Page/Open</code> JavaScript method
     */
    public JSEvent runEventPageOpen(DocumentPanel panel, PDFPage page) {
        JSEvent event = JSEvent.createPageOpen(page);
        runEvent(panel, event);
        return event;
    }

    /**
     * Raise the <code>Page/Close</code> JavaScript method
     */
    public JSEvent runEventPageClose(DocumentPanel panel, PDFPage page) {
        JSEvent event = JSEvent.createPageClose(page);
        runEvent(panel, event);
        return event;
    }

    /**
     * Return any Custom JavaScript to be run when the specified event is
     * received. This method checks each of the {@link ViewerFeature Features}
     * in the PDFViewer for custom JavaScript, and returns the concatenation
     * of all of them.
     * @see ViewerFeature#getCustomJavaScript
     */
    public String getCustomJavaScript(String type, String name) {
        String key = type+"/"+name;
        if (!custom.containsKey(key)) {
            StringBuilder js = new StringBuilder();
            String z = super.getCustomJavaScript(type, name);
            if (z != null) {
                js.append(z);
            }
            PDFViewer viewer = getViewer();
            if (viewer != null) {
                ViewerFeature[] features = viewer.getFeatures();
                for (int i=0;i<features.length;i++) {
                    z = features[i].getCustomJavaScript(type, name);
                    if (z != null) {
                        js.append(z);
                        js.append("\n");
                    }
                }
            }
            if (localcustom.containsKey(key)) {
                js.append(localcustom.get(key));
                js.append("\n");
            }
            custom.put(key, js.length() == 0 ? null : js.toString());
        }
        return custom.get(key);
    }

    /**
     * Add some custom JavaScript to the viewer.
     * @param event the event type, eg "App/Init" or "Doc/Open"
     * @param url the URL to load the JavaScript from
     * @since 2.11.18
     */
    public void addCustomJavaScript(String event, URL url) throws IOException {
        URLConnection con = url.openConnection();
        con.setUseCaches(false);
        String enc = con.getContentEncoding();
        if (enc == null) {
            enc = "ISO-8859-1";
        }
        StringWriter w = new StringWriter();
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(con.getInputStream(), enc));
            String s;
            while ((s=r.readLine())!=null) {
                w.append(s);
                w.append("\n");
            }
        } finally {
            if (r != null) { try { r.close(); } catch (IOException e) {} };
        }
        addCustomJavaScript(event, w.toString());
    }

    /**
     * Add some custom JavaScript to the viewer.
     * @param event the event type, eg "App/Init" or "Doc/Open"
     * @param script the JavaScript to execute on that event
     * @since 2.11.18
     */
    public void addCustomJavaScript(String event, String script) {
        if (localcustom.containsKey(event)) {
            localcustom.put(event, localcustom.get(event) + "\n" + script);
        } else {
            localcustom.put(event, script);
        }
    }

    //-------------------------------------------------------------------------

    /**
     * Sets whether to highlight form fields displayed in the viewer
     * using the color from {@link #getAppRuntimeHighlightColor}.
     * The default value can be set by the <code>JavaScript.app.runtimeHighlight</code>
     * property, which can be set to "true" to enable this by default.
     * @param highlight true to highlight form fields
     * @since 2.11.12
     */
    public void setAppRuntimeHighlight(boolean highlight) {
        boolean old = getAppRuntimeHighlight();
        this.appRuntimeHighlight = Boolean.valueOf(highlight);
        if (old != highlight) {
            ((JComponent)root).firePropertyChange("JavaScript.app.runtimeHighlight", highlight ? 0 : getAppRuntimeHighlightColor().getRGB(), highlight ? getAppRuntimeHighlightColor().getRGB() : 0);
            repaintEverything();
        }
    }

    /**
     * Return the value set by {@link #setAppRuntimeHighlight}
     * @since 2.11.12
     */
    public boolean getAppRuntimeHighlight() {
        if (appRuntimeHighlight == null) {
            String val = getViewer() == null ? null : getViewer().getPropertyManager().getProperty("JavaScript.app.runtimeHighlight");
            appRuntimeHighlight = "true".equals(val) ? Boolean.TRUE : Boolean.FALSE;
        }
        return appRuntimeHighlight.booleanValue();
    }

    /**
     * Sets the color to highlight form fields if {@link #setAppRuntimeHighlight}
     * returns true.
     * The default value can be set by the <code>JavaScript.app.runtimeHighlightColor</code>
     * property, which may be an HTML-style color (eg "#FF0000").
     * @param c the highlight color
     * @since 2.11.12
     */
    public void setAppRuntimeHighlightColor(Color c) {
        if (c != null) {
            Color old = appRuntimeHighlightColor;
            appRuntimeHighlightColor = c;
            if (getAppRuntimeHighlight()) {
                ((JPanel)root).firePropertyChange("JavaScript.app.runtimeHighlightColor", old==null ? 0 : old.getRGB(), c.getRGB());
                repaintEverything();
            }
        }
    }

    /**
     * Return the value set by {@link #setAppRuntimeHighlightColor}
     * @since 2.11.12
     */
    public Color getAppRuntimeHighlightColor() {
        if (appRuntimeHighlightColor == null) {
            if (getViewer() != null) {
                String val = getViewer().getPropertyManager().getProperty("JavaScript.app.runtimeHighlightColor");
                try {
                    appRuntimeHighlightColor = new Color(Integer.parseInt(val.substring(1), 16));
                } catch (Exception e) { }
            }
            if (appRuntimeHighlightColor == null) {
                appRuntimeHighlightColor = new Color(234, 255, 255);
            }
        }
        return appRuntimeHighlightColor;
    }

    /**
     * Sets whether to mark form fields with focus with a dotted rectangle.
     * The default value can be set by the <code>JavaScript.app.focusRect</code>
     * property, which can be set to "false" to disable this by default.
     * @param value true to highlight form fields
     * @since 2.11.12
     */
    public void setAppFocusRect(boolean value) {
        boolean old = getAppFocusRect();
        this.appFocusRect = Boolean.valueOf(value);
        if (old != value) {
            ((JComponent)root).firePropertyChange("JavaScript.app.focusRect", old, value);
            repaintEverything();
        }
    }

    /**
     * Return the value set by {@link #setAppFocusRect}
     * @since 2.11.12
     */
    public boolean getAppFocusRect() {
        if (appFocusRect == null) {
            String val = getViewer() == null ? null : getViewer().getPropertyManager().getProperty("JavaScript.app.focusRect");
            appFocusRect = "false".equals(val) ? Boolean.FALSE : Boolean.TRUE;
        }
        return appFocusRect.booleanValue();
    }

    private void repaintEverything() {
        try {
            JSEngine.doPrivileged(new Callable<Void>() {
                public Void call() {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            if (getViewer() != null) {
                                DocumentPanel[] panels = getViewer().getDocumentPanels();
                                for (int i=0;i<panels.length;i++) {
                                    panels[i].repaint();
                                }
                            } else {
                                root.repaint();
                            }
                        }
                    });
                    return null;
                }
            });
        } catch (Exception e) {
            throw (RuntimeException)e;
        }
    }

}
