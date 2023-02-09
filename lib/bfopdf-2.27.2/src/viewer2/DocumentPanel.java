// $Id: DocumentPanel.java 41670 2021-11-10 16:02:46Z mike $

package org.faceless.pdf2.viewer2;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.undo.*;
import java.awt.event.*;
import java.awt.*;
import java.beans.*;
import org.faceless.pdf2.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.io.File;
import java.util.List;
import java.util.concurrent.*;
import java.util.prefs.Preferences;
import javax.print.*;
import java.awt.print.*;
import javax.print.attribute.*;
import javax.print.attribute.standard.*;
import java.lang.ref.SoftReference;
import org.faceless.pdf2.Event;
import org.faceless.util.SoftInterruptibleThread;
import org.faceless.pdf2.viewer2.feature.SinglePageView;
import org.faceless.pdf2.viewer2.feature.ContinuousPageView;
import org.faceless.pdf2.viewer2.feature.DualPageView;
import org.faceless.pdf2.viewer2.feature.Undo;
import org.faceless.pdf2.viewer2.feature.Menus;

/**
 * <p>
 * A <code>DocumentPanel</code> is the basic component that displays a PDF, and may be
 * instantiated on it's own or as part of a {@link PDFViewer}. It contains a
 * {@link DocumentViewport} and optionally one or more {@link SidePanel} objects on the
 * left, and may process {@link PDFAction}s on the PDF.
 * See the <a href="doc-files/tutorial.html">viewer tutorial</a> for more detail on how to use this class and the "viewer" package.
 * </p>
 *
 * <a name="initParams"></a>
 * <div class="initparams">
 * The following <a href="doc-files/initparams.html">initialization parameters</a> may be specified
 * <table summary="">
 * <tr><th>defaultViewport</th><td>The class name of the default viewport to use if not specified in the PDF. May be <code>SinglePageDocumentViewport</code>, <code>MultiPageDocumentVieport</code> or a fully-qualified class name of another {@link DocumentViewport}</td></tr>
 * <tr><th>defaultPageMode</th><td>The default "page mode" of the PDF if not set. This may take one of the values for the "pagemode" {@link PDF#setOption PDF option}, and would typically be "UseThumbs" or "UseOutlines".</td></tr>
 * <tr><th>ignoreDocumentPageMode</th><td><code>true</code> or <code>false</code> (the default) - whether to ignore the "pagemode" {@link PDF#setOption PDF option} in the PDF when choosing which SidePanel t open by default.</td></tr>
 * <tr><th>ignoreDocumentTitle</th><td><code>true</code> or <code>false</code> (the default) - whether to ignore the "displaydoctitle" {@link PDF#setOption PDF option} in the PDF when choosing the text to use for the window title.</td></tr>
 * <tr><th>defaultZoom</th><td>The default zoom level of the PDF, if not set by a PDF open action. This may be the value "fit", "fitwidth", "fitheight" or a number between 12.5 and 6400 to set the zoom level.</td></tr>
 * <tr><th>useNamedSidePanels</th><td><code>true</code> or <code>false</code> (the default) - whether to show names on the side panel tabs rather than icons.</td></tr>
 * <tr><th>sidePanelSize</th><td>The default (and minimum) width of the side panels displayed in this DocumentPanel. The default is 120</td></tr>
 * <tr><th>mouseWheelUnit</th><td>The number of pixels to adjust the viewport's scrollbar by when using the mouse wheel. The default is 16.</td></tr>
 * <tr><th>smoothScrollTime</th><td>When smoothly scrolling a viewport's scrollbars, the number of ms to animate the scroll over. The default is 500, set to zero to disable.</td></tr>
 * <tr><th>smoothScrollDistance</th><td>When smoothly scrolling a viewport's scrollbars, the maximum number of pixels to try to animate. The default is 500, set to zero to disable.</td></tr>
 * <tr><th>earlyClose</th><td>When closing a DocumentPanel or changing the PDF it contains, the old PDF object remains open and will naturally have its {@link PDF#close} method called during garbage collection. This can lead to problems on Windows platforms; As the PDF may retain a reference to the file it was read from, this prevents the file being deleted until <code>close</code> is called. The <code>earlyClose</code> parameter can be set to close the PDF file immediately the PDF is removed from the DocumentPanel or the panel closed; this will free any resources held by the PDF, and so invalidate any reference to those resources (which may be held elsewhere - for example, if the PDF had its pages moved to another document). So use with caution - by default this value is not set, but set to any non-null value to enable.</td></tr>
 * <tr><th>noDirtyDocuments</th><td>Set this value to non-null to disable the {@link #setDirty dirty} flag on documents. If disabled, no prompt will appear when trying to close a document that has been modified.</td></tr>
 * <tr><th>noDirtyInTitle</th><td>Set this value to non-null to disable the "*" in the window title when the PDF is flagged as dirty.</td></tr>
 * <tr><th>noProgressInTitle</th><td>Set this value to non-null to disable the load-progress indicator in the window title when the PDF is linearized and only partially loaded.</td></tr>
 * <tr><th>respectSignatureCertification</th><td>If true, any restrictions found on a {@link FormSignature#getCertificationType certified} signature in the PDF will be honoured - for example, if the PDF being displayed has {@link FormSignature#CERTIFICATION_NOCHANGES nochanges} set then no changes will be allowed to the PDF through the viewer</td></tr>
 * <tr><th>viewportBorderColor</th><td>Can be set to the Color to draw the border around the pages in the viewport, specified as a hex value. The default value is "666666", which is a dark gray. If the specified value has 8 digits, the first two hex digits are used as the alpha value. A value of "none" or "transparent" will not draw the border.</td></tr>
 * <tr><th>viewportShadowColor</th><td>Can be set to the Color to draw the shadow below the pages in the viewport, specified as a hex value. The default value is "80666666", which is a translucent dark gray. If the specified value has 8 digits, the first two hex digits are used as the alpha value. A value of "none" or "transparent" will not draw the shadow.</td></tr>
 * <tr><th>viewportMargin</th><td>If set, this is the margin to place around the outside of all the pages displayed in the viewport, in pixels. The default value is 4.</td></tr>
 * <tr><th>viewportGap</th><td>If set, this is the gap to place between multiple pages displayed in the viewport, in pixels (if applicable to the type of viewport in use). The default value is 10.</td></tr>
 * <tr><th>printDialog</th><td>This can be set to <code>native</code> to always use the native print dialog, <code>java</code> to always use the Java dialog, or <code>auto</code> to choose depending on which print options are set. The native dialog does not allow options (such as number of copies, duplex printing) to be initialized, and some PDFs can specify defaults for these with the {@link PDF#setOption setOption} mechanism. And the alternative java dialog may miss some advanced options, such as options for stapling the printed document. So a value of <code>auto</code> will use the native dialog unless the PDF (or the call to {@link #print print} specifies initial value for some of these options.</td></tr>
 * </table>
 * </div>
 *
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 */
public class DocumentPanel extends JPanel {

    private boolean includeEmbedded;            // EXPERIMENTAL
    private JSManager jsmanager;
    private PDF pdf;                            // The PDF
    private List<PDFParser> parserlist;
    private PDFViewer viewer;                   // The parent viewer - may be null
    private DocumentViewport viewport;          // The viewport
    private JTabbedPane tabbedpane;             // If not null, contains the SidePanels
    private JSplitPane splitpane;               // If not null, contains tabbedpane and viewport
    private int initialsize, thresholdsize;   // The sizing values for splitpane
    private final Collection<ActionHandler> actionhandlers;    // A collection of ActionHandler
    private final Collection<AnnotationComponentFactory> annotfactories;    // A collection of AnnotationComponentFactory
    private final Collection<SidePanelFactory> panelfactories;    // A collection of ViewerFeaurte.SidePanelFactory
    private final Collection<DocumentPanelListener> listeners;         // A collection of DocumentPanelListeners
    private boolean initialpageset;             // False until a valid page has been set
    private boolean loadedfired;                // False until a valid page has been set
    private boolean splitpaneopen;              // It the splitpane open?
    private boolean dirty;                      // It the splitpane open?
    private SidePanel selectedsidepanel;        // The current sidepanel
    private int lastpagenumber;                 // Used only when the current page has been deleted
    private transient final DirtyListener dirtylistener;
    private final Collection<UndoableEditListener> undolisteners;
    private final Collection<String> permissiondenied;
    private int signaturePermissionDenied;
    private LinearizedSupport linearizedsupport;
    private String windowtitle;
    private transient volatile ScheduledExecutorService scheduler;

    final int panelindex;               // For debugging
    private static int globalpanelindex;        // For debugging

    /**
     * Create a new DocumentPanel
     */
    public DocumentPanel() {
        super(new BorderLayout());
        Util.initialize();
        this.actionhandlers = new LinkedHashSet<ActionHandler>();
        this.annotfactories = new LinkedHashSet<AnnotationComponentFactory>();
        this.panelfactories = new LinkedHashSet<SidePanelFactory>();
        this.listeners = new LinkedHashSet<DocumentPanelListener>();
        this.undolisteners = new LinkedHashSet<UndoableEditListener>();
        this.permissiondenied = new LinkedHashSet<String>();
        setOpaque(true);
        setBackground(Color.gray);
        this.panelindex = globalpanelindex++;
        if (getProperty("debug.Event") != null) {
            System.err.println("[PDF] Created DocumentPanel#"+panelindex);
        }

        this.dirtylistener = new DirtyListener(this);
        PropertyChangeListener l = new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent e) {
                updateTitle();
            }
        };
        addPropertyChangeListener("dirty", l);
        addPropertyChangeListener("loadProgress", l);
        includeEmbedded = getProperty("viewportIncludesEmbeddedFiles") != null; // experimental!
    }

    /**
     * Create pageup/pagedown actions. Done this way so we can experiment with
     * different binding locations
     * @param comp the component on which to create the actions and edit the InputMap
     * @param level the level - WHEN_IN_FOCUSED_WINDOW or WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
     * @param panel the DocumentPanel
     */
    @SuppressWarnings("deprecation")
    static void createInputMapActions(JComponent comp, int level, final DocumentPanel docpanel) {
        Action actionScrollUp = new AbstractAction("pageUp") {
            public boolean isEnabled() {
                DocumentViewport vp = docpanel.getViewport();
                PDFPage page = vp.getRenderingPage();
                return page != null && vp.getPreviousSelectablePageIndex(page) >= 0;
            }
            public void actionPerformed(ActionEvent e)  {
                DocumentViewport vp = docpanel.getViewport();
                PDFPage page = vp.getRenderingPage();
                if (page != null) {
                    int ix = vp.getPreviousSelectablePageIndex(page);
                    if (ix >= 0) {
                        docpanel.setPageNumber(ix);
                    }
                }
            }
        };

        Action actionScrollDown = new AbstractAction("pageDown") {
            public boolean isEnabled() {
                DocumentViewport vp = docpanel.getViewport();
                PDFPage page = vp.getRenderingPage();
                return page != null && vp.getNextSelectablePageIndex(page) >= 0;
            }
            public void actionPerformed(ActionEvent e)  {
                DocumentViewport vp = docpanel.getViewport();
                PDFPage page = vp.getRenderingPage();
                if (page != null) {
                    int ix = vp.getNextSelectablePageIndex(page);
                    if (ix >= 0) {
                        docpanel.setPageNumber(ix);
                    }
                }
            }
        };

        Action actionScrollHome = new AbstractAction("pageFirst") {
            public void actionPerformed(ActionEvent e)  {
                DocumentViewport vp = docpanel.getViewport();
                PDFPage page = vp.getRenderingPage();
                if (page != null && docpanel.getPageNumber(page) != 0) {
                    docpanel.setPageNumber(0);
                }
            }
        };

        Action actionScrollEnd = new AbstractAction("pageLast") {
            public void actionPerformed(ActionEvent e)  {
                DocumentViewport vp = docpanel.getViewport();
                PDFPage page = vp.getRenderingPage();
                if (page != null && docpanel.getPageNumber(page) != docpanel.getNumberOfPages() - 1) {
                    docpanel.setPageNumber(docpanel.getNumberOfPages() - 1);
                }
            }
        };

        Action actionFrameNext = new AbstractAction("frameNext") {
            public void actionPerformed(ActionEvent e) {
                PDFViewer viewer = docpanel.getViewer();
                if (viewer != null) {
                    viewer.selectFrame(true);
                }
            }
        };

        Action actionFramePrevious = new AbstractAction("framePrevious") {
            public void actionPerformed(ActionEvent e) {
                PDFViewer viewer = docpanel.getViewer();
                if (viewer != null) {
                    viewer.selectFrame(false);
                }
            }
        };

        Action actionDebugBindings = new AbstractAction("debugBindings") {
            public void actionPerformed(ActionEvent e)  {
                Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                while (c!=null) {
                    if (c instanceof JComponent) {
                        JComponent jc = (JComponent)c;
                        InputMap map1 = jc.getInputMap(jc.WHEN_FOCUSED);
                        InputMap map2 = jc.getInputMap(jc.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
                        InputMap map3 = jc.getInputMap(jc.WHEN_IN_FOCUSED_WINDOW);
                        String a1 = map1.allKeys()==null ? "[]" : Arrays.asList(map1.allKeys()).toString();
                        String a2 = map2.allKeys()==null ? "[]" : Arrays.asList(map2.allKeys()).toString();
                        String a3 = map3.allKeys()==null ? "[]" : Arrays.asList(map3.allKeys()).toString();
                        System.out.println("InputMap: c="+jc.getClass().getName()+" f="+a1+" a="+a2+" w="+a3);
                    } else {
                        System.out.println("InputMap: c="+c.getClass().getName());
                    }
                    c = c.getParent();
                }
            }
        };

        InputMap inputmap = comp.getInputMap(level);
        ActionMap actionmap = comp.getActionMap();
        inputmap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), "pageUp");
        inputmap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), "pageDown");
        inputmap.put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), "pageFirst");
        inputmap.put(KeyStroke.getKeyStroke(KeyEvent.VK_END, 0), "pageLast");
        inputmap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.CTRL_DOWN_MASK), "pageUp");
        inputmap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.CTRL_DOWN_MASK), "pageDown");
        inputmap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "pageFirst");
        inputmap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "pageLast");
        inputmap.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK), "frameNext");
        inputmap.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "framePrevious");
        actionmap.put("pageUp", actionScrollUp);
        actionmap.put("pageDown", actionScrollDown);
        actionmap.put("pageFirst", actionScrollHome);
        actionmap.put("pageLast", actionScrollEnd);
        actionmap.put("frameNext", actionFrameNext);
        actionmap.put("framePrevious", actionFramePrevious);

        // If no menus, operate Undo/Redo this way
        if (docpanel.viewer != null && docpanel.viewer.getFeature(Menus.class) == null && docpanel.viewer.getFeature(Undo.class) != null) {
            actionmap.put("undo", new AbstractAction("undo") {
                public void actionPerformed(ActionEvent e) {
                    docpanel.viewer.getFeature(Undo.class).undo(docpanel);
                }
            });
            actionmap.put("redo", new AbstractAction("redo") {
                public void actionPerformed(ActionEvent e) {
                    docpanel.viewer.getFeature(Undo.class).redo(docpanel);
                }
            });
            inputmap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()), "undo");  // Deprecated in Java 10
            inputmap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()), "redo");
        }

        // For debugging - can remove
        inputmap = comp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        inputmap.put(KeyStroke.getKeyStroke("ctrl alt shift K"), "debugBindings");
        actionmap.put("debugBindings", actionDebugBindings);
    }

    void setPreference(String key, String value) {
        if (viewer != null && viewer.getPreferences() != null) {
            viewer.getPreferences().put(key, value);
        }
    }

    String getPreference(String key) {
        return viewer == null ? null : viewer.getPreferences() == null ? null : viewer.getPreferences().get(key, null);
    }

    private DocumentViewport createDefaultViewport() {
        DocumentViewport viewport = null;
        // If we have a viewer, look through it's features to see which Viewport
        // type comes first. Use that.
        if (getPDF() != null) {   // If the preferred viewport if specified in the PDF, use that
            String pagelayout = (String)getPDF().getOption("pagelayout");
            if ("OneColumn".equals(pagelayout)) {
                viewport = new MultiPageDocumentViewport();
            } else if ("SinglePage".equals(pagelayout)) {
                viewport = new SinglePageDocumentViewport();
            } else if ("DualPage".equals(pagelayout)) {
                viewport = new DualPageDocumentViewport();
            }
        }
        if (viewport==null) {
            String viewportname = getProperty("defaultViewport");
            if (viewportname == null && viewer != null) {
                viewportname = getPreference("defaultViewport");
            }
            if (viewportname == null && viewer != null) {
                ViewerFeature[] features = viewer.getFeatures();
                for (int i=0;viewportname==null && i<features.length;i++) {
                    if (features[i] instanceof SinglePageView) {
                        viewportname = "SinglePageDocumentViewport";
                    } else if (features[i] instanceof ContinuousPageView) {
                        viewportname = "MultiPageDocumentViewport";
                    } else if (features[i] instanceof DualPageView) {
                        viewportname = "DualPageDocumentViewport";
                    }
                }
            }
            if (viewportname != null) {
                if (viewportname.equals("MultiPageDocumentViewport")) {
                    viewport = new MultiPageDocumentViewport();
                } else if (viewportname.equals("SinglePageDocumentViewport")) {
                    viewport = new SinglePageDocumentViewport();
                } else if (viewportname.equals("DualPageDocumentViewport")) {
                    viewport = new DualPageDocumentViewport();
                } else {
                    try {
                        viewport = (DocumentViewport)Class.forName(viewportname, true, Thread.currentThread().getContextClassLoader()).getDeclaredConstructor().newInstance();
                    } catch (Throwable e) { }
                }
            }
            if (viewport==null) {
                viewport = new MultiPageDocumentViewport();
            }
        }
        if (viewer != null) {
            Preferences preferences = viewer.getPreferences();
            if (preferences != null) {
                int zoomMode = preferences.getInt("zoomMode", DocumentViewport.ZOOM_NONE);
                viewport.setZoomMode(zoomMode);
            }
        }
        return viewport;
    }

    //----------------------------------------------------------------------------------
    // Viewer and Viewport

    /**
     * Set the {@link DocumentViewport} used by this DocumentPanel.
     * @param viewport the Viewport
     */
    public void setViewport(DocumentViewport viewport) {
        if (viewport == null) {
            throw new NullPointerException("Viewport is null");
        }
        if (viewer != null) {
            String viewportname = viewport.getClass().getName();
            if (viewportname.equals(Util.PACKAGE + ".SinglePageDocumentViewport")) {
                viewportname = "SinglePageDocumentViewport";
            } else if (viewportname.equals(Util.PACKAGE + ".MultiPageDocumentViewport")) {
                viewportname = "MultiPageDocumentViewport";
            }
            setPreference("defaultViewport", viewportname);
        }

        DocumentViewport oldviewport = this.viewport;
        PDFPage currentpage = null;
        double zoom = 0;
        if (oldviewport!=null) {
            currentpage = getPage();
            zoom = getZoom();
            oldviewport.setDocumentPanel(null);
            remove(oldviewport);
        }
        if (viewport.getDocumentPanel()!=null) {
            throw new IllegalArgumentException("Viewport associated with another DocumentPanel");
        }
        this.viewport = viewport;
        viewport.setDocumentPanel(this);
        createInputMapActions(viewport, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, this);

        raiseDocumentPanelEvent(DocumentPanelEvent.createViewportChanged(DocumentPanel.this));
        // Add listeners to handle page open/close actions and to trigger "document redrawn"
        viewport.addPagePanelListener(new PagePanelListener() {
            private float lastdpi;
            private SoftReference<PDFPage> lastpageref;
            public void pageUpdated(PagePanelEvent event) {
                if (event.getType() == "redrawn") {
                    PDFPage lastpage = lastpageref == null ? null : lastpageref.get();
                    if (event.getPage() != lastpage || event.getPagePanel().getDPI() != lastdpi) {
                        lastpageref = new SoftReference<PDFPage>(event.getPage());
                        lastdpi = event.getPagePanel().getDPI();
                        raiseDocumentPanelEvent(DocumentPanelEvent.createRedrawn(DocumentPanel.this));
                    }
                }
            }
        });
        viewport.applyComponentOrientation(ComponentOrientation.getOrientation(getLocale()));
        viewport.requestFocusInWindow();
        if (getPDF() != null) {
            if (viewport instanceof NullDocumentViewport) {
                removeAll();
                if (tabbedpane != null) {
                    add(tabbedpane, BorderLayout.CENTER);
                }
            } else if (splitpane != null && splitpane.getParent() == this) {
                // Splitpane exists, just change viewport
                int div = splitpane.getDividerLocation();
                splitpane.setBottomComponent(viewport);
                splitpane.setDividerLocation(div);
            } else if (tabbedpane != null && tabbedpane.getParent() == this) {
                // Only here if we're changing from a NullDocumentViewport back to normal
                remove(tabbedpane);
                splitpane.setTopComponent(tabbedpane);
                splitpane.setBottomComponent(viewport);
                add(splitpane, BorderLayout.CENTER);
            } else {
                add(viewport, BorderLayout.CENTER);
            }
            if (currentpage != null) {
                viewport.setPage(currentpage, 0, 0, zoom);
            }
        }
        revalidate();
        repaint();
        viewport.setFocusCycleRoot(true);
    }

    private String getProperty(String property) {
        PropertyManager manager = getViewer()==null ? PDF.getPropertyManager() : getViewer().getPropertyManager();
        return manager==null ? null : manager.getProperty(property);
    }

    /**
     * Return the {@link DocumentViewport} contained by this DocumentPanel
     */
    public DocumentViewport getViewport() {
        if (viewport==null) {
            setViewport(createDefaultViewport());
        }
        return viewport;
    }

    /**
     * Return the JSManager object for this DocumentPanel.
     * @since 2.9
     */
    public JSManager getJSManager() {
        if (jsmanager==null) {
            jsmanager = new JSManager(this);
        }
        return jsmanager;
    }

    /**
     * Set the JSManager object for this DocumentPanel.
     * This method should only be called if multiple DocumentPanel
     * object are used in the same non-PDFViewer container.
     * @since 2.9
     */
    public void setJSManager(JSManager jsmanager) {
        this.jsmanager = jsmanager;
    }

    /**
     * Return the {@link PDFViewer} that contains this DocumentPanel.
     * Note a DocumentPanel does <i>not</i> have to be contained inside
     * a PDFViewer, in which case this method will return <code>null</code>.
     */
    public PDFViewer getViewer() {
        return viewer;
    }

    void setViewer(PDFViewer viewer) {
        this.viewer = viewer;
        setLocale(viewer.getLocale());
        setJSManager(viewer.getJSManager());
    }

    //--------------------------------------------------------------------------------
    // Resources and settings

    /**
     * Control the size of the leftmost pane. The two values specify the threshold
     * below which the pane is considered to be closed, and the default size of the
     * pane when it's opened.
     *
     * @param threshold the minimum size, below which the panel is assumed to be closed
     * @param preferred the default size of the leftmost pane when opened
     */
    public void setSidePanelSize(int threshold, int preferred) {
        this.thresholdsize = Math.max(thresholdsize, splitpane.getMinimumDividerLocation());
        this.initialsize = preferred;
        splitpane.setLastDividerLocation(initialsize);
    }

    /**
     * Add a {@link SidePanelFactory} to this
     * <code>DocumentPanel</code>. When a PDF is set, the panels that are
     * appropriate for that PDF will be created from this list of factories.
     * @param panelfactory the factory
     */
    public void addSidePanelFactory(SidePanelFactory panelfactory) {
        if (panelfactory!=null) panelfactories.add(panelfactory);
    }

    /**
     * Add a {@link AnnotationComponentFactory} to this
     * <code>DocumentPanel</code>. Any PDF's displayed by this panel will have annotations
     * created by these factories.
     * @param annotationfactory the factory
     */
    public void addAnnotationComponentFactory(AnnotationComponentFactory annotationfactory) {
        if (annotationfactory!=null) annotfactories.add(annotationfactory);
    }

    /**
     * Return the set of AnnotationFactories - called by PagePanel
     */
    Collection<AnnotationComponentFactory> getAnnotationFactories() {
        return Collections.unmodifiableCollection(annotfactories);
    }

    /**
     * Add a {@link ActionHandler} to this <code>DocumentPanel</code>.
     * Any actions passed to {@link #runAction} will by handled by this list of handlers.
     * @param actionhandler the handler
     */
    public void addActionHandler(ActionHandler actionhandler) {
        if (actionhandler!=null) actionhandlers.add(actionhandler);
    }

    /**
     * Run the specified action on the PDF. Actions are handled by
     * {@link ActionHandler}s, which should be registered
     * with this class via the {@link #addActionHandler addActionHandler()} method.
     * @param action the PDFAction to run.
     * @return true if the action was recognised and run successfully, false otherwise.
     */
    public boolean runAction(PDFAction action) {
        boolean success = false;
        while (action != null) {
            for (Iterator<ActionHandler> i = actionhandlers.iterator();i.hasNext();) {
                ActionHandler handler = i.next();
                if (handler.matches(this, action)) {
                    handler.run(this, action);
                    success = true;
                    break;
                }
            }
            action = action.getNext();
        }
        return success;
    }

    /**
     * Add a {@link DocumentPanelListener} to this DocumentPanel
     * @param listener the listener
     */
    public void addDocumentPanelListener(DocumentPanelListener listener) {
        if (listener != null) {
            synchronized(listeners) {
                listeners.add(listener);
            }
        }
    }

    /**
     * Remove a {@link DocumentPanelListener} from this DocumentPanel
     * @param listener the listener
     */
    public void removeDocumentPanelListener(DocumentPanelListener listener) {
        synchronized(listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * Return a list of all the {@link DocumentPanelListener} objects registered on
     * this DocumentPanel, or an empty array if there are none
     * @return the list of listeners
     * @since 2.13.1
     */
    public DocumentPanelListener[] getDocumentPanelListeners() {
        synchronized(listeners) {
             return (DocumentPanelListener[])listeners.toArray(new DocumentPanelListener[0]);
        }
    }

    /**
     * Raise a {@link DocumentPanelEvent} on the DocumentPanel. In general
     * this shouldn't be called unless you're extending one of the code
     * classes, i.e. by writing your own {@link DocumentViewport}.
     * @since 2.11.25
     */
    public void raiseDocumentPanelEvent(DocumentPanelEvent event) {
        if (getProperty("debug.Event")!=null) {
            if (SwingUtilities.isEventDispatchThread()) {
                System.err.println("[PDF] Raise DocumentPanel#"+panelindex+" "+event);
            } else {
                System.err.println("[PDF] Raise DocumentPanel#"+panelindex+" "+event+" (not in event thread)");
            }
        }
        if (event.getType() == "pageChanged") {
            if (!loadedfired) {
                loadedfired = true;
                raiseDocumentPanelEvent(DocumentPanelEvent.createLoaded(this));
                if (viewer != null) {
                    raiseDocumentPanelEvent(DocumentPanelEvent.createActivated(this));
                }
                postLoaded();
                File file = getAssociatedFile();
                getJSManager().runEventDocOpen(this, file==null ? null : file.getName());
            }
            PDFPage oldPage = event.getPreviousPage();
            if (oldPage != null) {
                getJSManager().runEventPageClose(this, oldPage);
            }
            PDFPage newPage = getPage();
            if (newPage != null) {
                getJSManager().runEventPageOpen(this, newPage);
            }
        }
        DocumentPanelListener[] l = new DocumentPanelListener[0];
        synchronized(listeners) {
            l = (DocumentPanelListener[])listeners.toArray(l);
        }
        for (int i=0;i<l.length;i++) {
           l[i].documentUpdated(event);
        }
    }

    //-----------------------------------------------------------------------------
    // Panels

    /**
     * Return a read-only collection containing the {@link SidePanel} objects in use by this
     * <code>DocumentPanel</code>.
     * @since 2.10.3 (prior to this release a Map was returned instead)
     */
    public Collection<SidePanel> getSidePanels() {
        if (tabbedpane == null) {
            return Collections.<SidePanel>emptySet();
        }
        List<SidePanel> l = new ArrayList<SidePanel>(tabbedpane.getTabCount());
        for (int i=0;i<tabbedpane.getTabCount();i++) {
            l.add((SidePanel)tabbedpane.getComponentAt(i));
        }
        return Collections.unmodifiableCollection(l);
    }

    /**
     * Remove the specified SidePanel from the DocumentPanel.
     * @since 2.10.3
     */
    public void removeSidePanel(SidePanel panel) {
        if (tabbedpane == null || panel == null) {
            return;
        }
        int tab = tabbedpane.indexOfComponent((Component)panel);
        if (tab >= 0) {
            boolean selected = getSelectedSidePanel()==panel;
            if (selected) {
                panel.panelHidden();
            }
            tabbedpane.remove((Component)panel);
            panel.setDocumentPanel(null);
            if (tabbedpane.getTabCount() == 0) {
                selectedsidepanel = null;
                remove(splitpane);
                add(viewport, BorderLayout.CENTER);
                tabbedpane = null;
                revalidate();
                repaint();
            } else if (selected) {
                tabbedpane.setSelectedIndex(tab==0 ? 0 : tab-1);
            }
        }
    }

    /**
     * Add the specified sidepanel to the DocumentPanel
     * @since 2.10.3
     */
    public void addSidePanel(final SidePanel panel) {
        final Component comp = (Component)panel;
        if (tabbedpane == null) {
            tabbedpane = new JTabbedPane(JTabbedPane.TOP);
            tabbedpane.setMinimumSize(new Dimension(0, 0));
            tabbedpane.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent event) {
                    if (splitpaneopen) {
                        if (selectedsidepanel != null) {
                            selectedsidepanel.panelHidden();
                        }
                        selectedsidepanel = (SidePanel)tabbedpane.getSelectedComponent();
                        if (selectedsidepanel != null) {
                            selectedsidepanel.panelVisible();
                            setPreference("sidePanelName", selectedsidepanel.getName());
                        }
                    }
                }
            });
            splitpane.setTopComponent(tabbedpane);
        }
        panel.setDocumentPanel(DocumentPanel.this);
        if (comp.isVisible()) {
            if (tabbedpane.indexOfComponent(comp) < 0) {
                Icon icon = null;
                try {
                    icon = panel.getIcon();
                } catch (Throwable e) {}    // Just in case old interface was used.
                String name = UIManager.getString("PDFViewer."+panel.getName());
                if (icon == null || "true".equals(getProperty("useNamedSidePanels"))) {
                    tabbedpane.addTab(name, comp);
                } else {
                    tabbedpane.addTab(null, icon, comp, name);
                }
                comp.applyComponentOrientation(ComponentOrientation.getOrientation(getLocale()));
                if (tabbedpane.getTabCount() == 1) {
                    removeAll();
                    if (viewport instanceof NullDocumentViewport) {
                        add(tabbedpane, BorderLayout.CENTER);
                    } else if (splitpane != null) {
                        add(splitpane, BorderLayout.CENTER);
                        splitpane.setBottomComponent(viewport);
                    }
                    selectedsidepanel = panel;
                    revalidate();
                    repaint();
                }
            }
        }
    }

    /**
     * Set the currently displayed {@link SidePanel}. If the specified SidePanel is not in the
     * list of SidePanels added to this DocumentPanel, this method does nothing.
     * @param panel the SidePanel to display, or null to display no SidePanel
     * @since 2.10.3 (prior to this release the name of the panel was specified instead)
     */
    public void setSelectedSidePanel(final SidePanel panel) {
        if (splitpane == null) {
            // no-op - no side panels to select
        } else if (panel == null) {
            splitpane.setDividerLocation(splitpane.getMinimumDividerLocation());
        } else if (tabbedpane.indexOfComponent((Component)panel)>=0) {
            tabbedpane.setSelectedComponent((Component)panel);
            if (splitpane.getDividerLocation() < thresholdsize) {
                int size = splitpane.getLastDividerLocation();
                if (size < thresholdsize) {
                    size = initialsize;
                }
                splitpane.setDividerLocation(size);
            }
        }
    }

    /**
     * Return the currently selected {@link SidePanel}, or
     * <code>null</code> if no panels are displayed.
     * @since 2.10.3 (prior to this release the name of the panel was returned instead)
     */
    public SidePanel getSelectedSidePanel() {
        if (splitpane != null && splitpane.getDividerLocation() != splitpane.getMinimumDividerLocation()) {
            return (SidePanel)tabbedpane.getSelectedComponent();
        } else {
            return null;
        }
    }

    //-----------------------------------------------------------------------------
    // setPDF

    /**
     * Set the PDF to be displayed by this <code>DocumentPanel</code>.
     * A value of <code>null</code> will remove the current PDF from this object
     * and free any resources that reference it - this should be done before this
     * object is disposed of.
     * @param pdf the PDF, or <code>null</code> to remove the current PDF
     */
    public void setPDF(PDF pdf) {
        setPDF(pdf, pdf==null ? null : pdf.getPage(0));
    }

    /**
     * Set the PDF to be displayed by this <code>DocumentPanel</code>, and specify the
     * initial page to display.
     * @param pdf the PDF, or <code>null</code> to remove the current PDF
     * @param page the initial page to display, or <code>null</code> to not display an initial
     * page (exactly how this is handled depends on the Viewport).
     * This will be ignored if the DocumentPanel is part of a PDFViewer and the PDF has an
     * open action that sets the page.
     * @since 2.11
     */
    public void setPDF(PDF pdf, PDFPage page) {
        setPDF(pdf != null ? new PDFParser(pdf) : null, page);
    }

    private static void addFiles(List<PDFParser> list, PDFParser parser, boolean includeEmbedded) {
        PDF pdf = parser.getPDF();
        boolean inline = "Inline".equals(pdf.getPortfolio().getPresentation());
        for (EmbeddedFile ef : pdf.getEmbeddedFiles().values()) {
            if (includeEmbedded || (inline && ef.getPortfolioFolder().equals("/"))) {
                try {
                    PDFParser parser2 = new PDFParser(ef.getPDF());
                    addFiles(list, parser2, includeEmbedded);     // recurse
                } catch (Exception e) {}
            }
        }
        list.add(parser);
    }

    /**
     * Set the PDF to be displayed by this <code>DocumentPanel</code>, and specify the
     * initial page to display and the exact {@link PDFParser} to use.
     * @param parser the PDFParser to use to retrieve the PDF from
     * @param page the initial page to display, or <code>null</code> to not display an initial
     * page (exactly how this is handled depends on the Viewport).
     * This will be ignored if the DocumentPanel is part of a PDFViewer and the PDF has an
     * open action that sets the page.
     * @since 2.11.3
     */
    public void setPDF(PDFParser parser, PDFPage page) {
        if (getProperty("debug.Event") != null) {
            System.err.println("[PDF] DocumentPanel.setPDF("+(parser==null?"null":"parser")+")");
        }
        initialpageset = false;
        loadedfired = false;
        PDF pdf = parser == null ? null : parser.getPDF();
        final PDF oldpdf = this.pdf;
        if (oldpdf != pdf && oldpdf != null) {
            if (scheduler != null) {
                scheduler.shutdown();
                scheduler = null;
            }
            // Can't call oldpdf.close() here, as we may still be rendering thumbnails in background threads etc.
            // See end of this method for resolution of this.
            getJSManager().runEventDocWillClose(this);
            raiseDocumentPanelEvent(DocumentPanelEvent.createClosing(DocumentPanel.this));
            dirtylistener.unbind();
            lastpagenumber = 0;
            if (viewer != null && viewer.getActiveDocumentPanel() == this) {
                raiseDocumentPanelEvent(DocumentPanelEvent.createDeactivated(DocumentPanel.this));
            }
        }
        this.parserlist = new ArrayList<PDFParser>(1);
        if (parser != null) {
            addFiles(parserlist, parser, includeEmbedded);
        }

        this.pdf = pdf;
        linearizedsupport = new LinearizedSupport(this);
        if (pdf == null) {
            if (viewport != null) {
                viewport.setDocumentPanel(null);
            }
            for (Iterator<SidePanel> i = getSidePanels().iterator();i.hasNext();) {
                removeSidePanel(i.next());
            }
            removeAll();
            tabbedpane = null;
            splitpane = null;
            viewport = null;
            selectedsidepanel = null;
        } else {
            linearizedsupport.invokeOnDocumentLoad(new Runnable() {
                public void run() {
                    PDF pdf = getPDF();
                    if (pdf != null) {
                        pdf.getForm().rebuild();
                    }
                }
            });
            if (splitpane == null) {                     // First time through - initialize
                // Threshold - minimum size below which the panel is closed
                // Preferred - initial size and size after a double click to close
                splitpane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
                splitpane.setOneTouchExpandable(true);
                splitpane.setDividerSize(UIManager.getInt("PDFViewer.DocumentPanel.dividerWidth"));
                splitpane.setResizeWeight(0);
                splitpane.setContinuousLayout(true);
                initialsize = UIManager.getInt("PDFViewer.DocumentPanel.sidePanelSize");
                thresholdsize = UIManager.getInt("PDFViewer.DocumentPanel.minSidePanelSize");
                try {
                    if (getProperty("minSidePanelSize") != null) {
                        thresholdsize = Math.max(10, Math.min(500, Integer.parseInt(getProperty("minSidePanelSize"))));
                    }
                } catch (NumberFormatException e) { }
                try {
                    if (getProperty("sidePanelSize")!=null) {
                        initialsize = Math.max(10, Math.min(1000, Integer.parseInt(getProperty("sidePanelSize"))));
                    }
                } catch (NumberFormatException e) { }
                try {
                    String s = getPreference("sidePanelSize");
                    if (s != null) {
                        initialsize = Integer.parseInt(s);
                    }
                } catch (NumberFormatException e) { }
                splitpane.setDividerLocation(splitpane.getMinimumDividerLocation());
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        if (splitpane != null) {
                            splitpane.setLastDividerLocation(initialsize);
                        }
                    }
                });
                splitpane.addPropertyChangeListener(new PropertyChangeListener() {
                    public void propertyChange(PropertyChangeEvent event) {
                        if (event.getPropertyName().equals("dividerLocation")) {
                            int newvalue = ((Integer)event.getNewValue()).intValue();
                            if (newvalue >= thresholdsize) {
                                if (!splitpaneopen) {
                                    splitpaneopen = true;
                                    if (tabbedpane != null) {
                                        SidePanel panel = (SidePanel)tabbedpane.getSelectedComponent();
                                        if (panel != null) {
                                            panel.panelVisible();
                                        }
                                    }
                                    if (getSelectedSidePanel() != null) {
                                        setPreference("sidePanelName", getSelectedSidePanel().getName());
                                    }
                                }
                            } else {
                                newvalue = splitpane.getMinimumDividerLocation();
                                splitpane.setDividerLocation(newvalue);
                                SwingUtilities.invokeLater(new Runnable() {
                                    public void run() {
                                        if (splitpane != null) {
                                            splitpane.setLastDividerLocation(thresholdsize);
                                        }
                                    }
                                });
                                if (splitpaneopen) {
                                    splitpaneopen = false;
                                    if (tabbedpane != null) {
                                        SidePanel panel = (SidePanel)tabbedpane.getSelectedComponent();
                                        if (panel != null) {
                                            panel.panelHidden();
                                        }
                                    }
                                }
                            }
                            setPreference("sidePanelSize", Integer.toString(newvalue));
                        }
                    }
                });
            }

            // From here we may be replacing a PDF, ie we have one previously
            // open. Close down the old tabbedpanes and open new ones, but if
            // we were previously open make sure we restore the size
            final int currentsize = splitpane.getDividerLocation();
            for (Iterator<SidePanel> i = getSidePanels().iterator();i.hasNext();) {
                removeSidePanel(i.next());
            }

            // WARNING. Really weird stuff happens here if running as an applet - 
            // getViewport will set the viewport if not already set, and in previous
            // version of code we were then re-adding the same item. This was fine in
            // an application but in an applet locked solid for 5 mins spinning deep in
            // Container.removeNotify. No idea why, couldn't attach debugger to applet.
            // Reproducible in OpenJDK7u45 and 6u65 (OS X). Happened only with MPDV,
            // tested several LAFs. Removing it later is fine so got rid of the second
            // add(viewport) here and call it fixed, but left this comment here as a
            // warning to others.
            // 
            getViewport();      // Will call setViewport() if we create a new one here

            for (Iterator<SidePanelFactory> i = panelfactories.iterator();i.hasNext();) {
                SidePanelFactory panelfactory = i.next();
                if (panelfactory.isSidePanelRequired(this)) {
                    addSidePanel(panelfactory.createSidePanel());
                }
            }
            if (currentsize != 0) {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        if (splitpane != null) {
                            splitpane.setDividerLocation(currentsize);
                        }
                    }
                });
            }

            Object pagemode = pdf.getOption("pagemode");
            if ("true".equals(getProperty("ignoreDocumentPageMode"))) {
                pagemode = null;
            }
            if (pagemode == null) {
                pagemode = getProperty("defaultPageMode");
            }
            String sidePanelName = null;
            boolean fallbackSidePanel = false;
            if ("UseOutlines".equals(pagemode)) {
                sidePanelName = "Bookmarks";
            } else if ("UseThumbs".equals(pagemode)) {
                sidePanelName = "Pages";
            } else if ("UseSignatures".equals(pagemode)) {
                sidePanelName = "Signatures";
            } else if ("UseNone".equals(pagemode)) {
                sidePanelName = null;
            } else {
                String s = getPreference("sidePanelSize");
                if (s != null) {
                    try {
                        if (Integer.parseInt(s) > 0) {
                            sidePanelName = getPreference("sidePanelName");
                            if (sidePanelName != null) {
                                fallbackSidePanel = true;
                            }
                        }
                    } catch (Exception e) { }
                }
            }
            boolean sidePanelFound = false;
            if (sidePanelName != null) {
                for (Iterator<SidePanel> i=getSidePanels().iterator();i.hasNext();) {
                    SidePanel panel = i.next();
                    if (panel.getName().equals(sidePanelName)) {
                        setSelectedSidePanel(panel);
                        sidePanelFound = true;
                    }
                }
                if (!sidePanelFound && fallbackSidePanel) {
                    // This block will run when an open side panel was loaded from preferences,
                    // but that panel wasn't found. This could happen if a Signature panel was
                    // open, the document panel was closed, and later reopened with a PDF
                    // that had no signatures. In this case, the "open" state of the side panel
                    // is best preserved, so we fall back to Thumbnails (which are usually available)
                    SidePanel outlines = null, pages = null;
                    for (Iterator<SidePanel> i=getSidePanels().iterator();i.hasNext();) {
                        SidePanel panel = i.next();
                        if (panel.getName().equals("Bookmarks")) {
                            outlines = panel;
                        } else if (panel.getName().equals("Pages")) {
                            pages = panel;
                        }
                    }
                    if (outlines != null) {
                        setSelectedSidePanel(outlines);
                        sidePanelFound = true;
                    } else if (pages != null) {
                        setSelectedSidePanel(pages);
                        sidePanelFound = true;
                    }
                }
            }
            if (!sidePanelFound) {
                setSelectedSidePanel(null);
            }
            dirtylistener.bind();

            // This validate() is crucial, as it sizes the DocumentPanel and so provides
            // a size to it's children. Without this, requesting the Viewport zooms to
            // fit won't know how big to zoom it to.
            validate();
            viewport.setDocumentPanel(this);

            if (!initialpageset) {
                if (page != null) {
                    String initzoom = getProperty("defaultZoom");
                    if (initzoom == null) {
                        initzoom = getPreference("zoomMode");
                    }
                    int zoommode = viewport.getZoomMode();
                    float zoom = Float.NaN;
                    if ("fit".equals(initzoom)) {
                        zoommode = DocumentViewport.ZOOM_FIT;
                        viewport.setZoomMode(zoommode);
                    } else if ("fitwidth".equals(initzoom)) {
                        zoommode = DocumentViewport.ZOOM_FITWIDTH;
                        viewport.setZoomMode(zoommode);
                    } else if ("fitheight".equals(initzoom)) {
                        zoommode = DocumentViewport.ZOOM_FITHEIGHT;
                        viewport.setZoomMode(zoommode);
                    } else if ("none".equals(initzoom)) {
                        zoommode = DocumentViewport.ZOOM_NONE;
                    } else if (initzoom != null) {
                        try {
                            zoom = Math.max(0.125f, Math.min(64, Float.parseFloat(initzoom) / 100));
                        } catch (Exception e) { }
                    }
                    if (zoom != zoom) {
                        zoom = viewport.getTargetZoom(zoommode, page);
                    }
                    setPage(page, 0, 0, zoom);
                }
            }
            viewport.requestFocusInWindow();
        }

        if (oldpdf != null) {
            // Closing the PDF is potentially problematic, because if the PDF was loaded
            // from a file, references to the original file may still remain if pages have
            // been moved to another PDF.
            //
            // But not closing can be an issue too, because it prevents the file from being
            // deleted (on Windows). So let the user decide with the "EarlyClose" property.
            // 
            // In addition, linearized PDFs that are loaded from a URL will continue to load
            // in the background, even after the DocumentPanel containining them has been closed
            // (because the background threads hold a reference to the PDF). Calling "close"
            // here is a necessity; it won't invalidate anything or prevent future loads, but will 
            // ensure there is no-more idle load in the background.
            //
            if (oldpdf.getBasicOutputProfile().isSet(OutputProfile.Feature.LinearizedLoader) || getProperty("EarlyClose") != null || getProperty("earlyClose") != null) {      // legacy
                oldpdf.close();
            }
        }
        repaint();
    }

    private void postLoaded() {
        boolean found = false;
        if (getProperty("respectSignatureCertification") != null) {
            for (Iterator<FormElement> i = pdf.getForm().getElements().values().iterator();i.hasNext();) {
                FormElement elt = i.next();
                if (elt instanceof FormSignature) {
                    FormSignature sig = (FormSignature)elt;
                    if (sig.getState() == FormSignature.STATE_SIGNED) {
                        int v = sig.getSignatureHandler().getCertificationType();
                        if (v != 0 && (signaturePermissionDenied == 0 || v < signaturePermissionDenied)) {
                            setSignaturePermissionRestrictions(sig);
                            found = true;
                        }
                    }
                }
            }
        }
        if (!found) {
            setSignaturePermissionRestrictions(null);
        }
    }

    //-----------------------------------------------------------------------------
    // Controlling the view

    /**
     * Get the PDFParser being used to parse this PDF.
     */
    public PDFParser getParser() {
        if (parserlist.isEmpty()) {
            return null;
        }
        for (PDFParser parser : parserlist) {
            if (parser.getPDF() == pdf) {
                return parser;
            }
        }
        throw new IllegalStateException();
    }

    /**
     * Return a read-only list of all PDFParser objects
     * used in this DocumentPanel. Typically this will be
     * a list of one item, the value returned from {@link #getParser}
     * @since 2.26
     */
    public List<PDFParser> getAllParsers() {
        return Collections.<PDFParser>unmodifiableList(parserlist);
    }

    /**
     * Return a read-only list of all PDF objects
     * used in this DocumentPanel. Typically this will be
     * a list of one item, the value returned from {@link #getPDF}
     * @since 2.26
     */
    public List<PDF> getAllPDFs() {
        List<PDF> l = new ArrayList<PDF>(parserlist.size());
        for (PDFParser parser : parserlist) {
            l.add(parser.getPDF());
        }
        return Collections.<PDF>unmodifiableList(l);
    }

    /**
     * Return the PDF currently being displayed by this <code>DocumentPanel</code>
     */
    public PDF getPDF() {
        return pdf;
    }

    /**
     * Return the PDFPage currently being displayed by the {@link DocumentViewport}.
     * If no PDF is set or the first page is still being rendered, this method will return
     * <code>null</code>.
     */
    public PDFPage getPage() {
        return getViewport().getPage();
    }

    /**
     * Return the number of pages being displayed by the {@link DocumentPanel}.
     * The same as <code>getPDF().getNumberOfPages()</code>
     * @since 2.26
     */
    public int getNumberOfPages() {
        int n = 0;
        for (PDFParser parser : parserlist) {
            n += parser.getNumberOfPages();
        }
        return n;
    }

    /**
     * Return the specified page being displayed by the {@link DocumentPanel}.
     * The same as <code>getPDF().getPage(i)</code>
     * @param i the page index, from 0..getNumberOfPages()
     * @since 2.26
     */
    public PDFPage getPage(int i) {
        for (PDFParser parser : parserlist) {
            int n = parser.getNumberOfPages();
            if (i < n) {
                return parser.getPDF().getPage(i);
            }
            i -= n;
        }
        throw new IllegalArgumentException();
    }

    /**
     * Return the index (from 0) of the specified page in this DocumentPanel's
     * pagelist, or -1 if it doesn't exist..
     * @param page the page
     * @since 2.26
     */
    public int getPageNumber(PDFPage page) {
        if (page == null) {
            return -1;
        }
        int n = 0;
        for (PDFParser parser : parserlist) {
            PDF pdf = parser.getPDF();
            if (page.getPDF() == pdf) {
                int j = pdf.getPages().indexOf(page);
                return j < 0 ? j : n + j;
            }
            n += pdf.getNumberOfPages();
        }
        return -1;
    }

    /**
     * Return the PDFParser used for the specified page.
     * The same as {@link #getParser}
     * @since 2.26
     */
    public PDFParser getParser(PDFPage page) {
        for (PDFParser parser : parserlist) {
            if (page.getPDF() == parser.getPDF()) {
                return parser;
            }
        }
        return getParser();
    }

    /**
     * Return the PageExtractor used for the specified page.
     * @since 2.26
     */
    public PageExtractor getPageExtractor(PDFPage page) {
        return getParser(page).getPageExtractor(page);
    }

    /**
     * Return the list of PageLabels to use for all the pages in
     * this DocumentPanel.
     * @return a list of Strings to use as pagelabels. The list is getNumberOfPages() long
     * @since 2.26
     */
    public List<String> getPageLabels() {
        List<String> l = new ArrayList<String>();
        int j = 1;
        for (PDFParser parser : parserlist) {
            PDF pdf = parser.getPDF();
            for (int i=0;i<pdf.getNumberOfPages();i++) {
                String s = pdf.getPageLabel(i);
                if (s == null) {
                    s = Integer.toString(j);
                }
                l.add(s);
                j++;
            }
        }
        return l;
    }

    /**
     * Return the LinearizedSupport object for this DocumentPanel
     * @since 2.14.1
     */
    public LinearizedSupport getLinearizedSupport() {
        return linearizedsupport;
    }

    /**
     * Set the page being displayed. A shortcut for <code>setPage(getPDF().getPage(i))</code>.
     */
    public void setPageNumber(final int i) {
        getLinearizedSupport().invokeOnPageLoadWithDialog(i, new Runnable() {
            public void run() {
                PDF pdf = getPDF();
                if (pdf != null) {
                    int page = i;
                    if (page < 0) {
                        page = 0;
                    } else {
                        int max = getNumberOfPages() - 1;
                        if (page > max) {
                            page = max;
                        }
                    }
                    setPage(getPage(page));
                }
            }
        });
    }

    /**
     * Return the pagenumber of the currently displayed page starting at 0, or -1 if no
     * page is being displayed.
     */
    public int getPageNumber() {
        return getPageNumber(getPage());
    }

    /**
     * Return the current zoom level. A value of 1 means the document is being displayed
     * at it's actual size, 0.5 means 50% and so on.
     */
    public float getZoom() {
        return getViewport().getZoom();
    }

    /**
     * Set the current zoom level
     * @param zoom the zoom level
     */
    public void setZoom(float zoom) {
        getViewport().setZoom(zoom);
    }

    /**
     * Set the page to display in the {@link DocumentViewport}. The page is displayed
     * at it's top-left and at the current zoom level.
     * @param page the page
     */
    public void setPage(PDFPage page) {
        setPage(page, 0, 0, getViewport().getTargetZoom(getViewport().getZoomMode(), page));
    }

    /**
     * Set the page to display in the {@link DocumentViewport}. The page is displayed
     * at the co-ordinates supplied and at the specified zoom level.
     * @param page the page
     * @param x the left-most position of the page to display, in units relative to {@link PagePanel#getFullPageView}
     * @param y the top-most position of the page to display, in units relative to {@link PagePanel#getFullPageView}
     * @param zoom the zoom level
     */
    public void setPage(PDFPage page, float x, float y, float zoom) {
        initialpageset = true;
        lastpagenumber = page.getPageNumber()-1;
        getViewport().setPage(page, x, y, zoom);
    }

    int getLastPageNumber() {
        return lastpagenumber;
    }

    /**
     * Redraw the specified object.
     * param o the Object that has been altered - typically a {@link PDFPage} or {@link PDFAnnotation}
     * @deprecated DocumentPanel.redraw() is no longer required as this object now listens to
     * {@link PropertyChangeEvent PropertyChangeEvents} fired by the PDF. This method is not called
     * anywhere and is a no-op
     */
    @Deprecated
    public void redraw(Object o) {
    }

    /**
     * Set the document as being "dirty", ie that it has been modified since loading.
     * The property <code>noDirtyDocuments</code> can be set to prevent this value
     * from being set.
     * @since 2.11.19
     */
    public void setDirty(boolean dirty) {
        if (dirty != this.dirty && getProperty("noDirtyDocuments") == null) {
            this.dirty = dirty;
            firePropertyChange("dirty", !this.dirty, this.dirty);
        }
    }

    /**
     * Updates the window title with a "*" for dirty or the load progress
     * for linearized. Called in response to the propertyChange events for "dirty" and "loadProgress",
     * to disable it just set the "noProgressInTitle" or "noDirtyInTitle" properties to not-null
     */
    private void updateTitle() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                JInternalFrame f = (JInternalFrame)SwingUtilities.getAncestorOfClass(JInternalFrame.class, DocumentPanel.this);
                if (f != null) {
                    getWindowTitle();
                    int percent = Math.round(getLinearizedSupport().getLoadProgress() * 100);
                    String s = windowtitle;
                    if (percent < 100 && getProperty("noProgressInTitle") == null) {
                        s += " ("+percent+"%)";
                    }
                    if (isDirty() && getProperty("noDirtyInTitle") == null) {
                        s += " *";
                    }
                    f.setTitle(s);
                }
            }
        });
    }

    /**
     * Get the base title of the window containing this DocumentPanel,
     * which does not include any "*" or loading progress details.
     * @since 2.15.4
     */
    public String getWindowTitle() {
        if (windowtitle == null) {
            JInternalFrame f = (JInternalFrame)SwingUtilities.getAncestorOfClass(JInternalFrame.class, DocumentPanel.this);
            if (f != null) {
                windowtitle = f.getTitle();
            }
            if (windowtitle == null) {
                windowtitle = "Document";
            }
        }
        return windowtitle;
    }

    /**
     * Set the base title of the window containing this DocumentPanel
     * @param title the new title.
     * @since 2.15.4
     */
    public void setWindowTitle(String title) {
        this.windowtitle = title;
        updateTitle();
    }

    /**
     * Return the value of the dirty flag, as set by {@link #setDirty}
     * @since 2.11.19
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Add an {@link UndoableEditListener} to this DocumentPanel
     * @since 2.11.19
     */
    public void addUndoableEditListener(UndoableEditListener l) {
        synchronized(undolisteners) {
            undolisteners.add(l);
        }
    }

    /**
     * Remove an {@link UndoableEditListener} from this DocumentPanel
     * @since 2.11.19
     */
    public void removeUndoableEditListener(UndoableEditListener l) {
        synchronized(undolisteners) {
            undolisteners.remove(l);
        }
    }

    /**
     * Fire an {@link UndoableEditEvent} on this DocumentPanel. As a special
     * hack, passing <code>new UndoableEditEvent(documentPanel, Undo.DISCARD)</code>
     * to this method will truncate the list of events
     * @since 2.11.19
     */
    public void fireUndoableEditEvent(UndoableEditEvent e) {
        synchronized(undolisteners) {
            if (e == null) {
                throw new NullPointerException("Event cannot be null");
            }
            if (e.getSource() != this) {
                throw new IllegalArgumentException("Source of UndoableEditEvent must be this DocumentPanel");
            }
            for (Iterator<UndoableEditListener> i = undolisteners.iterator();i.hasNext();) {
                i.next().undoableEditHappened(e);
            }
        }
    }

    //-----------------------------------------------------------------------------
    // Other document actions

    /**
     * Display a Print dialog for printing this document, or if a {@link PrintService} is
     * specified, print directly to that service without displaying a dialog.
     * @param fservice the PrintService to print to. If this value is <code>null</code>
     * a dialog will be displayed allowing the selection of a service.
     * @param fatts the print attributes - may be set to an AttributeSet to control the
     * printing, or <code>null</code> to use the default.
     */
    public void print(final PrintService fservice, final PrintRequestAttributeSet fatts) throws PrintException, PrinterException {
        if (pdf==null) throw new NullPointerException("Document is null");

        try {
            JSEngine.doPrivileged(new Callable<Void>() {
                public Void call() throws PrintException, PrinterException {
                    PrintService service = fservice;

                    PrintRequestAttributeSet atts = fatts;
                    if (atts == null) {
                        atts = new HashPrintRequestAttributeSet();
                    }
                    if (atts.get(Sides.class) == null && pdf.getOption("print.duplex") != null) {
                        String s = (String)pdf.getOption("print.duplex");
                        if ("DuplexFlipShortEdge".equals(s)) {
                            atts.add(Sides.TWO_SIDED_SHORT_EDGE);
                        } else if ("DuplexFlipLongEdge".equals(s)) {
                            atts.add(Sides.TWO_SIDED_LONG_EDGE);
                        }
                    }
                    if (atts.get(Finishings.class) == null && pdf.getOption("print.finishings") != null) {
                        String s = (String)pdf.getOption("print.finishings");
                        if ("NONE".equals(s)) {
                            atts.add(Finishings.NONE);
                        } else if ("BIND".equals(s)) {
                            atts.add(Finishings.BIND);
                        } else if ("STAPLE".equals(s)) {
                            atts.add(Finishings.STAPLE);
                        } else if ("SADDLE_STITCH".equals(s)) {
                            atts.add(Finishings.SADDLE_STITCH);
                        } else if ("EDGE_STITCH".equals(s)) {
                            atts.add(Finishings.EDGE_STITCH);
                        } else if ("COVER".equals(s)) {
                            atts.add(Finishings.COVER);
                        }
                    }
                    if (atts.get(PageRanges.class) == null) {
                        @SuppressWarnings("unchecked") List<PDFPage> l = (List<PDFPage>)pdf.getOption("print.pagerange");
                        if (l != null) {
                            int[][] x = new int[l.size()][];
                            for (int i=0;i<l.size();i++) {
                                x[i] = new int[] { ((PDFPage)l.get(i)).getPageNumber() };
                            }
                            atts.add(new PageRanges(x));
                        }
                    }
                    if (atts.get(Copies.class) == null && pdf.getOption("print.numcopies") != null) {
                        // Why would you honour this? It makes no sense to include this in a PDF
                        // for general use; guaranteed some clown will set it to MAX_VALUE on a single
                        // page PDF. 
//                        atts.add(new Copies(((Integer)pdf.getOption("print.numcopies")).intValue()));
                    }
                    boolean noatts = atts.isEmpty();
                    if (atts.get(DocumentName.class) == null) {
                        try {
                            String title = pdf.getInfo("Title");
                            if (title != null) {
                                atts.add(new DocumentName(title, Locale.getDefault()));
                            }
                        } catch (ClassCastException e) { }
                    }

                    final PrinterJob job = PrinterJob.getPrinterJob();
                    job.setPageable(new Pageable() {
                        public int getNumberOfPages() {
                            return DocumentPanel.this.getNumberOfPages();
                        }
                        public Printable getPrintable(int pagenumber) {
                            PDFParser parser = getParser(getPage(pagenumber));
                            return parser.getPrintable(pagenumber);
                        }
                        public PageFormat getPageFormat(int pagenumber) {
                            PDFParser parser = getParser(getPage(pagenumber));
                            PageFormat format = parser.getPageFormat(pagenumber);
                            Paper paper = job.defaultPage(format).getPaper();
                            paper.setImageableArea(0, 0, paper.getWidth(), paper.getHeight());
                            format.setPaper(paper);
                            return format;
                        }
                    });

                    boolean print;
                    if (service != null) {
                        job.setPrintService(service);
                        print = true;
                    } else {
                        String dialogtype = getProperty("printDialog");
                        if ("native".equalsIgnoreCase(dialogtype) || ("auto".equalsIgnoreCase(dialogtype) && noatts)) {
                            print = job.printDialog();
                        } else {
                            print = job.printDialog(atts);
                        }
                    }
                    if (print) {
                        getJSManager().runEventDocWillPrint(DocumentPanel.this);
                        job.print(atts);
                        // Technically we should be printing with a javax.print.SimpleDoc
                        // and run this event on the print completed event. However we
                        // dropped SimpleDoc for PrinterJob in 1.17, for something to
                        // do with landscape or odd media sizes as I recall. Double
                        // check this before implementing.
                        getJSManager().runEventDocDidPrint(DocumentPanel.this);
                        if (service instanceof StreamPrintService) {
                            ((StreamPrintService)service).dispose();
                        }
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            if (e instanceof PrintException) {
                throw (PrintException)e;
            } else if (e instanceof PrinterException) {
                throw (PrinterException)e;
            } else {
                throw (RuntimeException)e;
            }
        }
    }

    /**
     * Indicates whether the specified type of action is allowed for this
     * DocumentPanel. A permission is allowed by default, but may be denied
     * permanently by the Document's {@link EncryptionHandler}, or temporarily
     * by a call to {@link #setPermission}. The currently used list of
     * permissions includes:
     * <dl>
     * <dt>Print</dt><dd>The document may be printed.</dd>
     * <dt>Extract</dt><dd>Text may be extracted from the document.</dd>
     * <dt>Assemble</dt><dd>The pages in the document may be added, removed, rotated or reordered.</dd>
     * <dt>Annotate</dt><dd>Annotations may be added, removed, moved around, or edited.</dd>
     * <dt>FormFill</dt><dd>Form fields may be filled in and the form submitted.</dd>
     * <dt>PageEdit</dt><dd>The page content may be edited, ie the page may be cropped or redacted</dd>
     * <dt>Save</dt><dd>The document may be saved.</dd>
     * </dl>
     * Other non-standard permissions may be also be used if customization is required.
     * By default anything not recognized will return true.
     * @see #setPermission
     * @see #setSignaturePermissionRestrictions
     * @see EncryptionHandler#hasRight
     * @see FormSignature#getCertificationType
     * @since 2.13
     */
    public boolean hasPermission(String permission) {
        EncryptionHandler handler = pdf == null ? null : pdf.getEncryptionHandler();
        return (handler == null || handler.hasRight(permission)) &&
               (signaturePermissionDenied != FormSignature.CERTIFICATION_NOCHANGES || (!"Assemble".equals(permission) && !"Annotate".equals(permission) && !"FormFill".equals(permission)) && !"PageEdit".equals(permission)) &&
               (signaturePermissionDenied != FormSignature.CERTIFICATION_ALLOWFORMS || (!"Assemble".equals(permission) && !"Annotate".equals(permission) && !"PageEdit".equals(permission))) &&
               (signaturePermissionDenied != FormSignature.CERTIFICATION_ALLOWCOMMENTS || (!"Assemble".equals(permission) && !"PageEdit".equals(permission))) &&
                !permissiondenied.contains(permission);
    }

    /**
     * Sets whether the specified permission is allowed on this DocumentPanel.
     * @param permission the permission
     * @param enable true to allow the action, false otherwise
     * @see #hasPermission
     * @see #setSignaturePermissionRestrictions
     * @since 2.13
     */
    public void setPermission(String permission, boolean enable) {
        if (enable ? permissiondenied.remove(permission) : permissiondenied.add(permission)) {
            raiseDocumentPanelEvent(DocumentPanelEvent.createPermissionChanged(this, permission));
        }
    }

    /**
     * Limit the permissions that can be {@link #setPermission set} on this PDF
     * to ensure they don't conflict with the {@link FormSignature#getCertificationType certification}
     * of this signature. This can be used to ensure that modifications to a PDF don't
     * invalidate an existing digital siganture that disallows them. By default this is
     * not the case, but setting the
     * <code>respectSignatureCertification</code> <a href="#initParam">initialization-parameter</a>
     * will ensure those restrictions are respected. This method can be called with <code>null</code>
     * to make the setting of permissions unrestricted.
     * @see #hasPermission
     * @see FormSignature#getCertificationType
     * @since 2.13
     */
    public void setSignaturePermissionRestrictions(FormSignature sig) {
        int oldval = signaturePermissionDenied;
        signaturePermissionDenied = sig == null || sig.getSignatureHandler() == null ? 0 : sig.getSignatureHandler().getCertificationType();
        if (oldval != signaturePermissionDenied) {
            raiseDocumentPanelEvent(DocumentPanelEvent.createPermissionChanged(this, null));
        }
    }


    /*
    public static void main(final String args[]) throws Exception {
        final PDF pdf = new PDF(new PDFReader(new java.io.File(args[0])));
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                final DocumentPanel panel = new DocumentPanel();
//                panel.addAnnotationComponentFactory(new org.faceless.pdf2.viewer2.feature.FormTextWidgetFactory());
                JFrame frame = new JFrame("BFO");
                frame.getContentPane().add(panel);
                panel.setPDF(pdf);
                frame.setSize(600, 700);
                frame.setVisible(true);
            }
        });
    }
    */

    /**
     * Set the associated File on this DocumentPanel. The file is the
     * filename which the PDF is assumed to have originated - typically
     * it is set automatically, but if the PDF was loaded into the viewer
     * manually this method may be called later to update the filename
     * 
     * @param file the file
     * @param enableSave if true, the file was loaded from this source, and the "Save" option should enabled. If false, the file serves as an initial filename only (for instance, if the file was loaded from a URL, or a bitmap image).
     * @since 2.24.3
     */
    public void setAssociatedFile(File file, boolean enableSave) {
        putClientProperty("file", file);
        putClientProperty("fileEnableSave", enableSave);
    }

    /**
     * Set the associated File on this DocumentPanel.
     * Calls {@link #setAssociatedFile(File,boolean) setAssociatedFile(file, true)}
     * @param file the file
     * @since 2.18.1
     */
    public void setAssociatedFile(File file) {
        setAssociatedFile(file, true);
    }

    /**
     * Get the associated File from this DocumentPanel. This is typically
     * set automatically if the PDF was loaded via an {@link Importer},
     * but may not be set if the {@link PDFViewer#loadPDF} method was used
     * instead. The {@link #setAssociatedFile} method can be called to set
     * it in that case.
     * @return file the file associated with this DocumentPanel, which may be null
     * if it hasn't been set.
     * @since 2.18.1
     */
    public File getAssociatedFile() {
        return (File)getClientProperty("file");
    }

    /**
     * Return true if the associated file was loaded as an "import".
     * @since 2.24.3
     */
    public boolean getAssociatedFileEnablesSave() {
        return Boolean.TRUE.equals(getClientProperty("fileEnableSave"));
    }

    /**
     * Schedule a timer task to run which is related to this DocmentPanel.
     * The advantage of using this method over a standalone own timer is that all 
     * these tasks will be automatically cancelled when the DocumentPanel pdf
     * is changed.
     * @param r the runnable task to run
     * @param delay the delay in ms from now to run the task
     * @return a ScheduledFuture which can be used to cancel the task
     * @since 2.20.4
     */
    public ScheduledFuture<?> schedule(final Runnable r, final long delay) {
        if (scheduler == null) {
            final ScheduledThreadPoolExecutor localscheduler = new ScheduledThreadPoolExecutor(1);
            localscheduler.setThreadFactory(new ThreadFactory() {
                public Thread newThread(Runnable r) {
                    return new SoftInterruptibleThread(r, "BFO-DocPanelScheduler") {
                        public boolean isSoftInterrupted() {
                            return DocumentPanel.this.scheduler == localscheduler;
                        }
                    };
                }
            });
            scheduler = localscheduler;
        }
        return scheduler.schedule(new Runnable() {
            public void run() {
                try {
                    r.run();
                } catch (RuntimeException e) {
                    if (!((SoftInterruptibleThread)Thread.currentThread()).isSoftInterrupted()) {
                        throw e;
                    }
                }
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Make this DocumentPanel the active one in any parent PDFViewer
     * @since 2.26
     */
    public void focus() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                JInternalFrame f = (JInternalFrame)SwingUtilities.getAncestorOfClass(JInternalFrame.class, DocumentPanel.this);
                if (f != null) {
                    try {
                        f.setSelected(true);
                    } catch (PropertyVetoException ee) { }
                }
            }
        });
    }

}
