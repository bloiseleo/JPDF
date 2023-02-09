// $Id: ThumbnailPanel.java 43975 2022-08-24 06:43:47Z chris $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.viewer2.util.LongRunningTask;
import org.faceless.util.SoftInterruptibleThread;
import org.faceless.pdf2.*;
import javax.swing.*;
import javax.swing.undo.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.io.*;
import java.util.*;
import java.beans.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;
import java.util.concurrent.ScheduledFuture;
import java.awt.dnd.*;
import java.awt.datatransfer.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.event.*;
import java.text.NumberFormat;

/**
 * <p>
 * Creates a {@link SidePanel} that displays the page thumbnails.
 * Since release 2.10.2 the Thumbnail panel can also be used to
 * reorder, delete and insert pages or entire documents. Modifying
 * a document in this way requires the {@link EncryptionHandler} on
 * the document grants the "Assemble" right.
 * </p><p>
 * It's also possible to add custom actions to a ThumbnailPanel by
 * adding features implementing {@link ThumbnailSelectionAction}.
 * These will be available via keyboard shortcut or the popup menu
 * on the panel.
 * </p>
 *
 * <div class="initparams">
 * The following <a href="../doc-files/initparams.html">initialization parameters</a> can be specified to configure this feature.
 * <table summary="">
 * <tr><th>editable</th><td><code>true</code> or <code>false</code>, for {@link #setEditable setEditable()}. Default is true</td></tr>
 * <tr><th>draggable</th><td><code>true</code> or <code>false</code>, for {@link #setEditable setDraggable()}. Default is true</td></tr>
 * <tr><th>scrollFollow</th><td><code>true</code> or <code>false</code>, for {@link #setScrollFollow setScrollFollow()}. Default is true</td></tr>
 * <tr><th>usePageLabels</th><td><code>true</code> or <code>false</code>, for {@link #setUsePageLabels setUsePageLabels()}. Default is true</td></tr>
 * <tr><th>thumbnailSize</th><td><code>number</code>, for {@link #setThumbnailSize setThumbnailSize()}. Default is 100</td></tr>
 * </table>
 * </div>
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">ShowHideThumbnails</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.8
 * @see ThumbnailSelectionAction
 */
public class ThumbnailPanel extends SidePanelFactory {

    private static final boolean DISALLOW_EMBEDDED_EDITS = true;      // always disallow page changes to embedded PDF
    private static final int defaultListselectionBackgroundColor = 0x9999cc;
    private static final int defaultListbackgroundColor = 0xcccccc;
    private static final int defaultListforegroundColor = 0x000000;
    private boolean editmode = true, scrollfollow = true, draggable = true, uselabels = true;
    private int thumbnailsize;
    private Collection<ThumbnailSelectionAction> actions;

    /**
     * Create a new ThumbnailPanel
     * @since 2.10.2
     */
    public ThumbnailPanel() {
        super("ShowHideThumbnails");
        thumbnailsize = UIManager.getInt("PDFViewer.Feature.ThumbnailPanel.thumbnailWidthHeight");
        if (thumbnailsize <= 0) {
            thumbnailsize = 100;
        }

    }

    /**
     * Determine whether SidePanels created by this factory allow
     * pages to be edited in any way - either reordered via dragging
     * or through any editing {@link ThumbnailSelectionAction}.
     * The default is true, but this may be overridden by the PDF itself
     * if it is encrypted and doesn't allow editing.
     s* Pages may not be edited if the PDF is not yet fully loaded.
     * @param editmode true if SidePanels created by this Factory allow alterations to the PDF
     * @since 2.10.2
     */
    public void setEditable(boolean editmode) {
        this.editmode = editmode;
    }

    /**
     * Determines whether SidePanels created by this factory can be
     * edited by dragging pages around or dragging new documents in.
     * This is distinct from {@link #setEditable} - if that flag is
     * true but this flag false, the document can still be edited by
     * other means via the Thumbnail panel. The default is true
     * @param draggable if the PDF can be edited by dragging
     */
    public void setDraggable(boolean draggable) {
        this.draggable = draggable;
    }

    /**
     * Set whether the thumbnail panel should scroll to follow the
     * currently selected page or not. The default is true.
     * @param follow whether to follow the currently selected page in the viewport
     * @since 2.11.6
     */
    public void setScrollFollow(boolean follow) {
        this.scrollfollow = follow;
    }

    /**
     * Set whether to number the pages using the "page labels" if defined
     * on this PDF, or whether to always use the physical page number.
     * The default is true.
     * @param uselabels if true, use the page labels if defined
     * @see PDF#getPageLabel
     * @see PageNumber#setUsePageLabels
     * @since 2.11.19
     */
    public void setUsePageLabels(boolean uselabels) {
        this.uselabels = uselabels;
    }

    /**
    /**
     * If this ThumbnailPanel is part of a DocumentPanel with no viewer, use
     * this method add add {@link ThumbnailSelectionAction} actions to the
     * panel.
     * @since 2.12
     */
    public void addAction(ThumbnailSelectionAction action) {
        if (actions == null) {
            actions = new LinkedHashSet<ThumbnailSelectionAction>();
        }
        this.actions.add(action);
    }

    /**
     * Set the size of the longest dimension of each thumbnail. The
     * default is 100, although this can be changed by the "thumbnailSize"
     * property or via the user-interface while running the viewer.
     * @since 2.11.13
     */
    public void setThumbnailSize(int thumbnailsize) {
        this.thumbnailsize = thumbnailsize;
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);
        String val = getFeatureProperty(viewer, "editable");
        if (val!=null) {
            setEditable("true".equals(val));
        }
        val = getFeatureProperty(viewer, "draggable");
        if (val != null) {
            setDraggable("true".equals(val));
        }
        val = getFeatureProperty(viewer, "scrollFollow");
        if (val != null) {
            setScrollFollow("true".equals(val));
        }
        val = getFeatureProperty(viewer, "usePageLabels");
        if (val != null) {
            setUsePageLabels("true".equals(val));
        }
        Preferences preferences = viewer.getPreferences();
        if (preferences != null) {
            val = preferences.get("thumbnailPanelSize", null);
        }
        if (val == null) {
            val = getFeatureProperty(viewer, "thumbnailSize");
        }
        if (val != null) {
            try {
                thumbnailsize = Math.max(20, Math.min(400, Integer.parseInt(val)));
            } catch (Exception e) {}
        }
    }

    public SidePanel createSidePanel() {
        return new ThumbnailSidePanel(this);
    }

    /**
     * This type of {@link SidePanel} is returned from a call to {@link #createSidePanel}.
     * @since 2.12
     */
    public static class ThumbnailSidePanel extends JScrollPane implements SidePanel, PDFBackgroundTask {
        private final View view;
        private final Icon icon;

        ThumbnailSidePanel(ThumbnailPanel tp) {
            view = new View(tp);
            view.setVisible(false);
            setViewportView(view);
            Util.fixScrollPaneKeyBindings(this);
            icon = UIManager.getIcon("PDFViewer.Feature.ThumbnailPanel.icon");
        }

        /**
         * Return the {@link View} used by this SidePanel
         */
        public View getView() {
            return view;
        }

        public Icon getIcon() {
            return icon;
        }

        public String getName() {
            return "Pages";
        }

        public void setDocumentPanel(DocumentPanel docpanel) {
            view.setDocumentPanel(docpanel);
        }

        public void panelVisible() {
            view.setVisible(true);
            synchronized (view) {
                view.notifyAll();
            }
            view.requestFocusInWindow();
        }

        public void panelHidden() {
            view.setVisible(false);
            synchronized (view) {
                view.notifyAll();
            }
        }

        /**
         * Return true if the thumbnails are still rendering.
         */
        public boolean isRunning() {
            return view.isRunning();
        }

        /**
         * Return true if the Thumbnail rendering has been paused
         * with the {@link #pause} method.
         * @see #pause
         * @see #unpause
         */
        public boolean isPaused() {
            return view.isPaused();
        }

        /**
         * Pause the rendering thread. Completes the current thumbnail
         * rendering operation (if any), pauses the thread and returns.
         * @see #isPaused
         * @see #unpause
         */
        public void pause() throws InterruptedException {
            view.setPaused(true);
        }

        /**
         * Unpause the rendering thread if previously paused by the {@link #pause}
         * method
         * @see #pause
         */
        public void unpause() {
            view.setPaused(false);
        }
    }

    /**
     * <p>
     * This class is the JPanel containing all the thumbnails. It serves as a DropTarget
     * or drag-and-drop, as a the Scrollable content of the SidePanel, and handles actions
     * when the document is updated, when a popup has run on the selected panels, and when
     * a keypress is received (by way of its {@link InputMap} and {@link ActionMap}. New
     * actions may be registered with this class by adding {@link ThumbnailSelectionAction}s
     * to the viewer. These should respect this objects {@link #isEditable} flag if they're
     * going to modify the file.
     * </p><p>
     * This class fires "selection" {@link PropertyChangeEvent}s when the list of selected
     * pages is changed, and "selected" events when the current page is changed.
     * </p><p>
     * There's no need to acccess this class unless you're implementing your own
     * {@link ThumbnailSelectionAction}
     * </p>
     * @since 2.12
     */
    public static class View extends JPanel implements Scrollable, DocumentPanelListener, Autoscroll, PropertyChangeListener, DropTargetListener, Runnable {

        private final boolean scrollfollow, uselabels, factorydraggable, factoryeditable;
        private final int thumbnailsize;
        private final NumberFormat pageNumberFormat;

        private DocumentPanel docpanel;
        private PDF pdf;
        private int numPdfs; // For DocPanels showing pages from more than one PDF, the number of PDFs
        private volatile Dimension childsize;
        private boolean editable, updatepagepanelsrequired, animatemovements, batchNotifications, allPagesLoaded;
        private AnimateThread animatethread;
        private PainterThread painterthread;
        private SinglePagePanel currentPagePanel;       // Last one that was selected - broad meaning
        private SinglePagePanelFactory singlepagepanelfactory;
        private JPopupMenu popup;
        private List<SinglePagePanel> pagepanellist;
        private int dragTargetInsertPoint = -1;
        private int horigin, outlineWidth, cursorWidth, margin, hgap, vtop, vgap, columns;
        private List<Action> actions;
        private List<SinglePagePanel> previousNotification;
        private List<ThumbnailSelectionAction> nonViewerActions;
        private ScheduledFuture<?> updateTimerTask;
        private MouseAdapter mouseadapter;
        private final DropTarget droptarget;

        View(ThumbnailPanel tp) {
            super(null);
            this.factorydraggable = tp.draggable;  // "Factory" values are fixed at time of creation
            this.factoryeditable = tp.editmode;    // and are set by factory. Regular "editable" may vary
            this.scrollfollow = tp.scrollfollow;   // throughout life of document
            this.uselabels = tp.uselabels;
            this.thumbnailsize = tp.thumbnailsize;
            Insets insets = UIManager.getInsets("PDFViewer.Feature.ThumbnailPanel.margin");
            if (insets == null) {
                insets = new Insets(0,0,0,0);
            }
            this.margin = (insets.left + insets.right) / 2;
            this.vtop = insets.top;
            this.vgap = insets.bottom;
            this.outlineWidth = UIManager.getInt("PDFViewer.Feature.ThumbnailPanel.outlineWidth");
            this.cursorWidth = UIManager.getInt("PDFViewer.Feature.ThumbnailPanel.cursorWidth");
            this.pageNumberFormat = NumberFormat.getIntegerInstance();
            this.pagepanellist = Collections.synchronizedList(new ArrayList<SinglePagePanel>());  // Accessed from EDT and PainterThread - synchronize
            this.nonViewerActions = tp.actions == null ? null : new ArrayList<ThumbnailSelectionAction>(tp.actions);
            this.previousNotification = new ArrayList<SinglePagePanel>();
            this.singlepagepanelfactory = new SinglePagePanelFactory(this, thumbnailsize);

            droptarget = new DropTarget(this, this) {           // Work around bug in 7u45 at least - repeated initialization are possible.
                private boolean scrolling;
                protected void clearAutoscroll() {
                    super.clearAutoscroll();
                    scrolling = false;
                }
                protected void initializeAutoscrolling(Point p) {
                    if (!scrolling) {
                        super.initializeAutoscrolling(p);
                        scrolling = true;
                    }
                }
            };
            actions = new ArrayList<Action>();
            setFocusable(true);
        }

        PDF getPdf() {
            return pdf;
        }

        /**
         * Set the DocumentPanel this View relates to. This is really the
         * init method for this object - it starts the Animate and Painter thread
         * which continue running until this object is removed from a DocPanel,
         * even if there is no PDF.
         */
        void setDocumentPanel(DocumentPanel docpanel) {
            if (docpanel != this.docpanel && (docpanel == null || docpanel.getPDF() != null)) {
                if (this.docpanel != null) {
                    this.docpanel.removeDocumentPanelListener(this);
                }
                this.docpanel = docpanel;
                if (docpanel != null) {
                    docpanel.addDocumentPanelListener(this);
                    animatethread = new AnimateThread();
                    animatethread.start();
                    painterthread = new PainterThread();
                    painterthread.start();
                    addMouseListener(mouseadapter = new MouseAdapter() {
                        public void mouseClicked(MouseEvent e) {
                            requestFocusInWindow();
                        }
                    });
                } else {
                    animatethread.interrupt();
                    painterthread.interrupt();
                    removeMouseListener(mouseadapter);
                }
                initialize(docpanel);
            }
        }

        /**
         * Start or Stop the rendering of a PDF. This will be called more than once
         *
         * Run on EDT
         */
        private void initialize(DocumentPanel docpanel) {
            PDF pdf = docpanel == null ? null : docpanel.getPDF();
            actions.clear();
            InputMap inputmap = getInputMap();
            ActionMap actionmap = getActionMap();
            inputmap.clear();
            actionmap.clear();

            if (pdf == null && this.pdf != null) {
                this.pdf.removePropertyChangeListener(this);
                firePropertyChange("pdf", this.pdf, null);
                this.pdf = null;
                // Lingering reference to popup will be held by Swing, so make sure it
                // doesn't hold onto references for anything else.
                while (popup.getComponentCount() > 0) {
                    popup.remove(0);
                }
                while (getComponentCount() > 0) {
                    SinglePagePanel spp = (SinglePagePanel) getComponent(0);
                    spp.painter = null;
                    spp.page = null;
                    remove(0);
                }
                popup = null;
                currentPagePanel = null;

            } else if (pdf != null) {
                this.pdf = pdf;
                pdf.addPropertyChangeListener(this);
                updatepagepanelsrequired = true;
                updatePagePanels(docpanel);

                actions.add(new AbstractAction(UIManager.getString("PDFViewer.Bigger")) {
                    { putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke('+')); }
                    public void actionPerformed(ActionEvent event) {
                        singlepagepanelfactory.setSize((int)(singlepagepanelfactory.getSize() * 1.25));
                    }
                });

                actions.add(new AbstractAction(UIManager.getString("PDFViewer.Smaller")) {
                    { putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke('-')); }
                    public void actionPerformed(ActionEvent event) {
                        singlepagepanelfactory.setSize((int)(singlepagepanelfactory.getSize() / 1.25));
                    }
                });
                actions.add(null);

                PDFViewer viewer = docpanel.getViewer();
                if (viewer != null) {
                    ViewerFeature[] features = viewer.getFeatures();
                    for (int i=0;i<features.length;i++) {
                        if (features[i] instanceof ThumbnailSelectionAction) {
                            ThumbnailSelectionAction selectaction = (ThumbnailSelectionAction)features[i];
                            Action action = selectaction.getAction(this);
                            if (action != null) {
                                actions.add(action);
                            }
                        }
                    }
                } else if (nonViewerActions != null) {
                    for (int i=0;i<nonViewerActions.size();i++) {
                        ThumbnailSelectionAction selectaction = nonViewerActions.get(i);
                        Action action = selectaction.getAction(this);
                        if (action != null) {
                            actions.add(action);
                        }
                    }
                }

                // Use 5000lb Swing InputMap hammer to crack tiny nut. Note
                // insane requirement for individual instances of CursorAction
                // due to need to set ACTION_COMMAND_KEY - why not use name
                // from ActionMap, hmm?
                int[] modifiers = new int[] { 0, InputEvent.SHIFT_DOWN_MASK, InputEvent.META_DOWN_MASK, InputEvent.CTRL_DOWN_MASK };
                String[] directions = new String[] { "up", "down", "left", "right" };
                for (int i=0;i<directions.length;i++) {
                    int keycode = (new int[] { KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT })[i];
                    actionmap.put(directions[i], new CursorAction(directions[i], keycode));
                    for (int j=0;j<modifiers.length;j++) {
                        inputmap.put(KeyStroke.getKeyStroke(keycode, modifiers[j]), directions[i]);
                    }
                }

                popup = new JPopupMenu();
                for (int i=0;i<actions.size();i++) {
                    Action action = (Action)actions.get(i);
                    if (action != null) {
                        String name = (String)action.getValue(Action.NAME);
                        KeyStroke stroke = (KeyStroke)action.getValue(Action.ACCELERATOR_KEY);
                        if (stroke != null) {
                            inputmap.put(stroke, name);
                            actionmap.put(name, action);
                        }
                        popup.add(action);
                    } else if (i < actions.size() - 1) {
                        popup.add(new JSeparator());
                    }
                }
                // Bodge. Make BACKSPACE synonym for DELETE if we haven't otherwise defined it.
                if (inputmap.get(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0)) == null) {
                    inputmap.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), inputmap.get(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0)));
                }

                if (popup.getComponentCount() == 0) {
                    popup = null;
                }
                docpanel.getLinearizedSupport().invokeOnDocumentLoad(this);
                firePropertyChange("pdf", null, pdf);
            }
        }

        /**
         * Mark all items in panel as not cut
         */
        void unsetFlags() {
            for (int i = 0; i < getComponentCount(); i++) {
                SinglePagePanel spp = (SinglePagePanel) getComponent(i);
                spp.setFlags(SinglePagePanel.FLAG_NONE);
            }
            firePropertyChange("cut");
        }

        void firePropertyChange(String name) {
            firePropertyChange(name, (Object) null, (Object) null);
        }

        public void run() {
            // All pages have been loaded
            allPagesLoaded = true;
            singlepagepanelfactory.enableEditing();
        }

        private class CursorAction extends AbstractAction {
            private final int keycode;
            CursorAction(String name, int keycode) {
                super(name);
                putValue(Action.ACTION_COMMAND_KEY, name);
                this.keycode = keycode;
            }

            public void actionPerformed(ActionEvent event) {
                Dimension cs = getChildSize();
                Dimension s = getSize();
                int rowlen = Math.max(1, s.width / cs.width);
                int num = -1;
                for (int i=0;i<getComponentCount();i++) {
                    if (getComponent(i) == currentPagePanel) {
                        num = i;
                        break;
                    }
                }
                String name = event.getActionCommand();
                if ("up".equals(name)) {
                    num -= rowlen;
                } else if ("down".equals(name)) {
                    num += rowlen;
                } else if ("left".equals(name)) {
                    num--;
                } else if ("right".equals(name)) {
                    num++;
                } else {
                    return;
                }
                if (num >= 0 && num < getComponentCount()) {
                    SinglePagePanel spp = (SinglePagePanel)getComponent(num);
                    KeyEvent keyevent = new KeyEvent((Component)event.getSource(), event.getID(), event.getWhen(), event.getModifiers(), keycode, KeyEvent.CHAR_UNDEFINED);
                    spp.selectAction(keyevent);
                }
            }
        }

        /**
         * The document has been updated - check for loading, closing
         * or changing the current page.
         */
        public void documentUpdated(DocumentPanelEvent event) {
            String type = event.getType();
            if (type == "loaded") {
                if (docpanel.getPDF() != pdf) { // otherwise we call initialize twice, here and in setDocumentPanel
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            final DocumentPanel fdocpanel = docpanel;
                            if (fdocpanel != null) {
                                initialize(fdocpanel);
                            }
                        }
                    });
                }
            } else if (type == "closing") {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        initialize((DocumentPanel)null);
                    }
                });
            } else if (type == "pageChanged") {
                SinglePagePanel oldPagePanel = currentPagePanel;
                PDFPage nowpage = docpanel.getPage();
                currentPagePanel = getSinglePagePanel(nowpage);
                if (currentPagePanel == null) {
                    // The page has just been added
                    updatePagePanels(docpanel);
                    currentPagePanel = getSinglePagePanel(nowpage);
                }
                for (int i=0;i<getComponentCount();i++) {
                    SinglePagePanel panel = (SinglePagePanel)getComponent(i);
                    panel.setSelected(panel == currentPagePanel);
                }
                if (currentPagePanel != oldPagePanel && currentPagePanel != null) {
                    if (scrollfollow) {
                        // Push to the end of the queue, otherwise we sometimes get overriden
                        // by another scrollRectToVisible called by the cursor key handling
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                SinglePagePanel cpp = currentPagePanel;
                                if (cpp != null) { // avoid race condition
                                    scrollRectToVisible(cpp.getBounds());
                                }
                            }
                        });
                    }
                }
                firePropertyChange("selected", oldPagePanel, currentPagePanel);
            } else if (type == "permissionChanged" || type == "activated") {
                editable = docpanel.hasPermission("Assemble");
            }
        }

        private SinglePagePanel getSinglePagePanel(PDFPage page) {
            for (int i = 0; i < pagepanellist.size(); i++) {
                SinglePagePanel pp = pagepanellist.get(i);
                if (pp.getPage() == page) {
                    return pp;
                }
            }
            return null;
        }

        /**
         * The PDF fired a property change - document may have had its page
         * list altered.
         */
        public void propertyChange(PropertyChangeEvent event) {
            String name = event.getPropertyName();
            if (name.equals("pages") || name.equals("pagelabels")) {

                // Although we're most likely called on the EDT, schedule an
                // update for later anyway. This allows us to batch changes
                // that come in groups.
                updatepagepanelsrequired = true;

                // Why delay? Take the situation where many page operations are run.
                // Perhaps we clear the list then add a completely new list, to reorder. This
                // will fire an event on the first op, which will be processed synchronously
                // by our listener. If this is a "clear()" event we're going to die there as a
                // PDF must have at least one page. Even if not, we're unnecessarily scheduling
                // redraws because all this is going on on the one queue.
                //
                // Solution is either to a) hold then release events being fired, and when we
                // fire them fire them in batch, or b) wait a bit before acting. If other events
                // come in first, they'll trigger subsequent calls to updatePagePanels, but
                // that method will only act if the panels are "dirty". This will effectively
                // batch up operations to run no more than once every 100ms.
                //
                // A cleaner way to do it would be to use a Timer and have one event scheduled
                // for now+100ms in the future, which may be pushed back.
                //
                if (updateTimerTask != null) {
                    updateTimerTask.cancel(false);
                    updateTimerTask = null;
                }
                if (!isPaused()) {
                    updateTimerTask = docpanel.schedule(new Runnable() {
                        public void run() {
                            SwingUtilities.invokeLater(new Runnable() {
                                public void run() {
                                    final DocumentPanel fdocpanel = docpanel;
                                    if (fdocpanel != null) {
                                        updatePagePanels(fdocpanel);
                                    }
                                }
                            });
                        }
                    }, 100);
                }
                unsetFlags();
            }
        }

        /**
         * This method will add/remove the PagePanels so that there is a 1:1 mapping
         * from the page list to the pagepanel list. It should be called whenever
         * the PDF page list is updated.
         *
         * This is the only method that updates the "pagepanellist" array.
         *
         * Run on EDT
         *
         * @return true if the ordering of pages has changed
         */
        private void updatePagePanels(DocumentPanel docpanel) {
            if (updatepagepanelsrequired) {
                updatepagepanelsrequired = false;
                synchronized(pagepanellist) {
                    this.pdf = docpanel.getPDF();
                    this.numPdfs = 0;
                    List<SinglePagePanel> oldpagepanellist = new ArrayList<SinglePagePanel>(pagepanellist);
                    pagepanellist.clear();
                    if (pdf != null) {
                        pdf.addPropertyChangeListener(this);
                        Set<PDF> allpdf = new HashSet<PDF>();
                        allpdf.add(this.pdf);
                        int n = docpanel.getNumberOfPages();
                        for (int i = n - 1; i >= 0; i--) {
                            SinglePagePanel spp;
                            if (docpanel.getLinearizedSupport().isPageLoaded(i)) {
                                PDFPage page = docpanel.getPage(i);
                                allpdf.add(page.getPDF());
                                spp = singlepagepanelfactory.getSinglePagePanel(page);
                                spp.setPageNumber(i);
                            } else {
                                updatepagepanelsrequired = true;
                                Integer pagenumber = Integer.valueOf(i);
                                spp = singlepagepanelfactory.getSinglePagePanel(pagenumber);
                            }
                            add(spp, 0);
                            if (pagepanellist.isEmpty()) {
                                pagepanellist.add(spp);
                            } else {
                                pagepanellist.add(0, spp);
                            }
                        }
                        numPdfs = allpdf.size();
                        // remove any old pages
                        for (int i = getComponentCount() - 1; i >= n; i--) {
                            remove(i);
                        }
                    }
                    if (!oldpagepanellist.equals(pagepanellist)) {
                        childsize = null;
                        revalidate();
                        doLayout(getSize().width, false);
                        restartPainterThread();
                        repaint();
                    }
                }
            }
        }

        /**
         * Get a String describing the currently selected pages
         */
        public String getSelectedPagesDescription() {
            int lastn = -2;
            StringBuilder sb = new StringBuilder();
            for (int i=0;i<=getComponentCount();i++) {
                if (i != getComponentCount() && ((SinglePagePanel)getComponent(i)).isSelected()) {
                    if (lastn < 0) {
                        lastn = i;
                    }
                } else {
                    if (lastn >= 0) {
                        if (sb.length()>0) {
                            sb.append(",");
                        }
                        sb.append(lastn+1);
                        if (i != lastn + 1) {
                            sb.append("-");
                            sb.append(i);
                        }
                    }
                    lastn = -2;
                }
            }
            return sb.toString();
        }

        /**
         * Return a {@link List} of selected pages
         */
        public List<PDFPage> getSelectedPages() {
            // Not used internally, useful for others maybe.
            List<PDFPage> l = new ArrayList<PDFPage>();
            for (int i=0;i<getComponentCount();i++) {
                SinglePagePanel spp = (SinglePagePanel)getComponent(i);
                if (spp.isSelected()) {
                    l.add(spp.getPage());
                }
            }
            return l;
        }

        /**
         * Set the list of selected pages.
         * @param pages a non-empty list of PDFPage objects
         */
        public void setSelectedPages(List<PDFPage> pages) {
            if (pages == null || pages.isEmpty()) {
                throw new IllegalArgumentException();
            }
            for (Component c : getComponents()) {
                SinglePagePanel spp = (SinglePagePanel) c;
                spp.setSelected(pages.contains(spp.getPage()));
            }
        }

        /**
         * Signal the PainterThread that >=1 of the pages has changed and
         * may need repainting
         */
        void restartPainterThread() {
            painterthread.restart();
        }

        /**
         * Called when >=1 of the pages has changed its dimensions, which
         * requires laying out the children again
         */
        void resetChildSize() {
            childsize = null;
            revalidate();
            repaint();
        }

        public void doLayout() {
            // Called often, even when pane is hidden, so be quick.
            int w = getSize().width;
            if (w > 0) {
                doLayout(getSize().width, false);
            }
        }

        /**
         * Calculate the dimensions of this View and optionally move or
         * animate the panels to their correct positions.
         *
         * Run on EDT
         *
         * @param w the width that the View is (will be)
         * @param sizingonly if true, don't move anything - we're just measuring
         * @return the (anticipated) size of the View given the specified width
         */
        private Dimension doLayout(int w, boolean sizingonly) {
            Dimension cs = getChildSize();
            int n = getComponentCount();
            columns = Math.min(n, w / cs.width);
            hgap = Math.min(cs.width / 4, (w - (columns * cs.width)) / (columns + 1));
            horigin = (columns == 1 ? w - cs.width : w - (cs.width * columns) - (hgap * columns + 1)) / 2;
            int cx = horigin, cy = vtop;
            int xmax = 0, ymax = 0;
            Map<SinglePagePanel,Rectangle[]> animate = null;
            for (int i = 0; i < n; i++) {
                SinglePagePanel c = (SinglePagePanel)getComponent(i);
                if (!sizingonly) {
                    Rectangle src = animatethread.getDestinationBounds(c);
                    Rectangle dst = new Rectangle(cx, cy, cs.width, cs.height);
                    if (!animatemovements || src.width == 0) {
                        c.setBounds(dst);
                        revalidate();
                        repaint();
                    } else if (!src.equals(dst)) {
                        if (animate == null) {
                            animate = new HashMap<SinglePagePanel,Rectangle[]>();
                        }
                        animate.put(c, new Rectangle[] { src, dst });
                    }
                }
                xmax = Math.max(xmax, cx + cs.width);
                ymax = Math.max(ymax, cy + cs.height);
                cx += cs.width + hgap;
                if (cx + cs.width > w) {
                    cx = horigin;
                    cy += cs.height + vgap;
                }
            }
            if (animate != null) {
                animatethread.go(animate);
            }
            if (!sizingonly) {
                animatemovements = true;
            }

            return new Dimension(xmax, ymax);
        }

        /**
         * Thread that animates components moving from one position to another.
         */
        private class AnimateThread extends SoftInterruptibleThread {

            private Map<SinglePagePanel,Rectangle[]> map;
            private volatile boolean interrupt, waiting;

            AnimateThread() {
                super("ThumbnailAnimator");
                setDaemon(true);
            }

            public synchronized void interrupt() {
                interrupt = true;
                if (waiting) {
                    // Don't want to do this unless we have to, interrupt()
                    // closes the stream, so no further access to the PDF
                    // object is possible if we're reading it with NIO.
                    super.interrupt();
                }
                notifyAll();
            }

            public boolean isSoftInterrupted() {
                return interrupt;
            }

            /**
             * Return the bounds of the specific component when the current
             * animation (if any) is finished. In the default case this will
             * just be the bounds of the Component.
             */
            synchronized Rectangle getDestinationBounds(Component c) {
                Rectangle[] r = map == null ? null : (Rectangle[])map.get(c);
                return r == null ? c.getBounds() : r[1];
            }

            /**
             * Start a new animation
             * @param map a Map of Component -> [FromRectangle, ToRectangle]
             */
            synchronized void go(Map<SinglePagePanel,Rectangle[]> map) {
                this.map = map;
                notifyAll();
            }

            public void run() {
                while (docpanel != null) {
                    Map<SinglePagePanel,Rectangle[]> map;
                    synchronized(this) {
                        if (pdf == null || (map = this.map) == null) {
                            try {
                                waiting = true;
                                wait();
                            } catch (InterruptedException e) {}
                            waiting = false;
                            continue;
                        }
                    }
                    int steps = 10;
                    for (int i=0;i<=steps;i++) {
                        for (Iterator<Map.Entry<SinglePagePanel,Rectangle[]>> j = map.entrySet().iterator();j.hasNext();) {
                            Map.Entry<SinglePagePanel,Rectangle[]> e = j.next();
                            final SinglePagePanel panel = e.getKey();
                            if (panel.getParent() != null) {
                                Rectangle[] r = e.getValue();
                                final int x = r[0].x + (r[1].x - r[0].x)*i/steps;
                                final int y = r[0].y + (r[1].y - r[0].y)*i/steps;
                                final int w = r[0].width + (r[1].width - r[0].width)*i/steps;
                                final int h = r[0].height + (r[1].height - r[0].height)*i/steps;
                                SwingUtilities.invokeLater(new Runnable() {
                                    public void run() {
                                        panel.setBounds(x, y, w, h);
                                        DocumentPanel docpanel = getDocumentPanel();
                                        if (docpanel != null && scrollfollow && panel.getPage() == docpanel.getPage()) {
                                            scrollRectToVisible(panel.getBounds());
                                        }
                                    }
                                });
                                repaint();
                            } else {
                                j.remove();
                            }
                        }
                        try {
                            Thread.sleep(15);
                        } catch (InterruptedException e) { }
                    }
                    synchronized(this) {
                        if (map == this.map) {
                            this.map = null;
                        }
                    }
                }
            }

        }

        /**
         * Thread that cycles through all the SinglePagePanels and paints
         * them if necessary.
         */
        private class PainterThread extends SoftInterruptibleThread {

            private volatile int centerpage;
            private volatile boolean paused, completed, interrupt, waiting;

            PainterThread() {
                super("ThumbnailPainter");
                setDaemon(true);
                setPriority(Thread.MIN_PRIORITY+2);
            }

            public synchronized void interrupt() {
                interrupt = true;
                if (waiting) {
                    // Don't want to do this unless we have to, interrupt()
                    // closes the stream, so no further access to the PDF
                    // object is possible if we're reading it with NIO.
                    super.interrupt();
                }
                notifyAll();
            }

            public boolean isSoftInterrupted() {
                return interrupt;
            }


            boolean isComplete() {
                return this.completed;
            }

            synchronized void restart() {
                this.completed = false;
                notifyAll();
            }

            synchronized void setPaused(boolean paused) {
                if (paused != this.paused) {
                    this.paused = paused;
                    notifyAll();
                }
            }

            boolean isPaused() {
                return paused;
            }

            /**
             * Set which component is in the center of the viewport
             */
            void setCenterComponent(int page) {
                this.centerpage = page;
            }

            public void run() {
                final PDFViewer viewer = docpanel == null ? null : docpanel.getViewer();
                final WarningNotifier alert = viewer == null ? null : viewer.getFeature(WarningNotifier.class);
                if (alert != null) {
                    alert.register(this, docpanel);
                }
                try {
                    while (docpanel != null) {
                        synchronized(this) {
                            if (pdf == null || paused || completed) {
                                try {
                                    waiting = true;
                                    wait();
                                } catch (InterruptedException e) {}
                                waiting = false;
                                continue;
                            }
                        }

                        // Start at centerpage, then move up, down, up down based on d=delta
                        // d goes -1 2 -3 4... or 1 -2 3 -4... to cover [v v-1 v+1 v-2 v+2 ...]
                        int v = centerpage;
                        int d = 1;

                        SinglePagePanel spp = null;
                        synchronized (pagepanellist) {
                            while (v >= 0 && v < getComponentCount() && ((spp=(SinglePagePanel)getComponent(v)) == null || !spp.isRendered() || spp.isRendering() || spp.getPage() == null)) {
                                v += d;
                                d = d < 0 ? -d + 1 : -d - 1;
                                if (v < 0 || v >= getComponentCount()) {
                                    v += d;
                                    d = d < 0 ? -d + 1 : -d - 1;
                                }
                                spp = null;
                            }
                        }
                        if (spp != null) {
                            try {
                                spp.drawImage();
                            } catch (Exception e) {
                                if (docpanel != null) {
                                    e.printStackTrace();
                                }
                            }
                        } else {
                            completed = true;
                        }
                    }
                } finally {
                    if (alert != null) {
                        alert.unregister(this);
                    }
                }
            }

        }

        /**
         * Return true if this ThumbnailPanel will never allow editing.
         * This value is fixed over the life of the panel
         * @since 2.13
         */
        public boolean isFactoryEditable() {
            return factoryeditable;
        }

        /**
         * Return true if the PDF in the panel can be edited. This
         * value may vary over the life of the DocumentPanel.
         */
        public boolean isEditable() {
            return factoryeditable && editable && allPagesLoaded;
        }

        boolean isDraggable() {
            return factorydraggable && editable && allPagesLoaded;
        }

        boolean isRunning() {
            return painterthread != null && !painterthread.isComplete();
        }

        /**
         * Get the DocumentPanel this View relates to
         */
        public DocumentPanel getDocumentPanel() {
            return docpanel;
        }

        boolean isPaused() {
            return painterthread.isPaused();
        }

        void setPaused(boolean pause) {
            if (pause != isPaused()) {
                painterthread.setPaused(pause);
                if (pause) {
                    if (updateTimerTask != null) {
                        // Cancel any updates that are pending.
                        updateTimerTask.cancel(false);
                        updateTimerTask = null;
                    }
                    synchronized(pagepanellist) {
                        for (int i=0;i<pagepanellist.size();i++) {
                            SinglePagePanel panel = pagepanellist.get(i);
                            synchronized(panel) {
                                if (panel.isRendering()) {
                                    try {
                                        panel.wait();
                                    } catch (InterruptedException e) {}
                                }
                            }
                        }
                    }
                } else {
                    // This method will check if it needs to do anything,
                    // so always calling it at this point costs nothing.
                    updatePagePanels(docpanel);
                }
            }
        }

        //----------------------------------------------------------------------------
        // Drag/Drop methods
        //----------------------------------------------------------------------------

        public void dragOver(DropTargetDragEvent event) {
            dragEnter(event);
        }

        // Accept or Reject the event based on its DataFlavors
        //
        public void dragEnter(DropTargetDragEvent event) {
            if (isEditable() && isDraggable() && (event.getDropAction() & DnDConstants.ACTION_COPY_OR_MOVE) != 0) {
                DataFlavor[] flavors = event.getCurrentDataFlavors();
                for (int i=0;i<flavors.length;i++) {
                    if (flavors[i].isMimeTypeEqual(DataFlavor.javaJVMLocalObjectMimeType) || DragAndDrop.canImport(flavors[i])) {
                        Point p = event.getLocation();
                        Component dragTarget = findComponentAt(p);
                        if (dragTarget == null || dragTarget == this) {
                            if (columns == 1) {
                                // Allows dropping to the right of page in one-column mode
                                p.x = 10;
                                dragTarget = findComponentAt(p);
                            } else {
                                // Yes, ugly! Scan left until we find a page.
                                // If this doesn't work it's still OK.
                                do {
                                    p.x -= 10;
                                    dragTarget = findComponentAt(p);
                                } while (p.x > 0 && (dragTarget == null || dragTarget == this));
                            }
                        }
                        for (dragTargetInsertPoint=0;dragTargetInsertPoint<getComponentCount();dragTargetInsertPoint++) {
                            if (getComponent(dragTargetInsertPoint) == dragTarget) {
                                break;
                            }
                        }
                        // Test if we're dropping in the last half of the selected
                        // component, if so increase the index. Arbitrary but works.
                        if (dragTargetInsertPoint < getComponentCount()) {
                            p = SwingUtilities.convertPoint(this, p, dragTarget);
                            float prop = columns == 1 ? (float)p.y / dragTarget.getHeight() : (float)p.x / dragTarget.getWidth();
                            if (prop > 0.5) {
                                dragTargetInsertPoint++;
                            }
                        }
                        if (isDragTargetValid(event)) {
                            event.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
                            repaint();
                            return;
                        }
                    }
                }
            }
            dragTargetInsertPoint = -1;
            event.rejectDrag();
            repaint();
        }

        public void dragExit(DropTargetEvent event) {
            dragTargetInsertPoint = -1;
            repaint();
        }

        public boolean isDragTargetValid(DropTargetEvent event) {
            if (!isEditable() || !isDraggable() || dragTargetInsertPoint < 0) {
                return false;
            }
            if (dragTargetInsertPoint < getComponentCount() && !((SinglePagePanel)getComponent(dragTargetInsertPoint)).isDropTarget()) {
                return false;
            }
            try {
                final Transferable transfer = event instanceof DropTargetDragEvent ? ((DropTargetDragEvent)event).getTransferable() : ((DropTargetDropEvent)event).getTransferable();
                if (transfer.isDataFlavorSupported(SinglePagePanel.DRAGGABLE_PAGE)) {
                    final SinglePagePanel source = (SinglePagePanel)transfer.getTransferData(SinglePagePanel.DRAGGABLE_PAGE);
                    final View otherthumbpanel = source.thumbpanel;
                    final List<PDFPage> sourcepages = new ArrayList<PDFPage>();
                    for (int i=0;i<otherthumbpanel.getComponentCount();i++) {
                        SinglePagePanel spp = (SinglePagePanel)otherthumbpanel.getComponent(i);
                        if (spp.isSelected() && spp.getPage() != null) {
                            sourcepages.add(spp.getPage());
                        }
                    }
                    if (sourcepages.isEmpty()) {
                        return false;
                    }
                    if (sourcepages.size() == 1 && otherthumbpanel == this) {
                        int ix = getDocumentPanel().getPageNumber(sourcepages.get(0));
                        if (ix == dragTargetInsertPoint || (ix == dragTargetInsertPoint - 1 && ix < dragTargetInsertPoint)) {
                            return false;
                        }
                    }
                }
            } catch (Throwable e) {
                Util.displayThrowable(e, docpanel);
                return false;
            }
            return true;
        }

        /**
         * Accept a Drop. This method will update the PDF page list, which will fire
         * an event which this class is listening for.
         */
        public void drop(final DropTargetDropEvent event) {
            if (!isDragTargetValid(event)) {
                dragTargetInsertPoint = -1;
                event.rejectDrop();
                return;
            }

            // If we can have multiple PDF objects in one panel, which do we drag to?
            // The PDF that starts after the selected insert point
            final PDF targetpdf = dragTargetInsertPoint == getComponentCount() ? pdf : docpanel.getPage(dragTargetInsertPoint).getPDF();
            final List<PDFPage> targetpages = targetpdf.getPages();

            try {
                final Transferable transfer = event.getTransferable();
                if (transfer.isDataFlavorSupported(SinglePagePanel.DRAGGABLE_PAGE)) {
                    //-----------------------------------------------------------------
                    // Dragged to Move pages between PDFs or within PDF.
                    // Both PDFs are fully loaded, no need to worry about linearization
                    //-----------------------------------------------------------------
                    final SinglePagePanel source = (SinglePagePanel)transfer.getTransferData(SinglePagePanel.DRAGGABLE_PAGE);
                    final View otherthumbpanel = source.thumbpanel;
                    
                    List<PDFPage> otherpages = otherthumbpanel.getDocumentPanel().getPDF().getPages();
                    final List<PDFPage> sourcepages = new ArrayList<PDFPage>();
                    for (int i=0;i<otherthumbpanel.getComponentCount();i++) {
                        SinglePagePanel spp = (SinglePagePanel)otherthumbpanel.getComponent(i);
                        if (spp.isSelected() && spp.getPage() != null) {
                            sourcepages.add(spp.getPage());
                            spp.setSelected(spp == source);
                        }
                    }
                    PDFPage currentpage = getDocumentPanel().getPage();
                    if (currentpage == null) {
                        currentpage = getDocumentPanel().getViewport().getRenderingPage();
                    }

                    // Now:
                    // * otherthumbpanel is other panel - may be this
                    // * targetpages = our PDFs pages (live)
                    // * otherpages = other PDFs pages - may be = pages (live)
                    // * sourcepages = list of pages to transfer, in order
                    // * dragTargetInsertPoint = index to insert new pages at (0 <= i <= n);
                    // * currentpage = page our document panel is displaying

                    event.acceptDrop(DnDConstants.ACTION_MOVE);

                    if (otherthumbpanel != View.this) {
                        // We're moving pages between active DocumentPanels!
                        if (sourcepages.size() == otherthumbpanel.getComponentCount()) {
                            otherthumbpanel.getDocumentPanel().setDirty(false);
                            PDFViewer viewer = otherthumbpanel.getDocumentPanel().getViewer();
                            if (viewer != null) {
                                viewer.closeDocumentPanel(otherthumbpanel.getDocumentPanel());
                            }
                            otherpages = null;
                        } else if (sourcepages.contains(otherthumbpanel.docpanel.getPage())) {
                            // Got to ensure other DocumentPanel isn't displaying a page we're
                            // removing, or things go pearshaped.
                            List<PDFPage> copy = new ArrayList<PDFPage>(otherpages);
                            copy.removeAll(sourcepages);
                            int pn = Math.min(copy.size()-1, otherthumbpanel.docpanel.getPageNumber());
                            otherthumbpanel.docpanel.setPage(copy.get(pn));
                        }
                    }

                    final List<PDFPage> oldpages = new ArrayList<PDFPage>(targetpages);
                    if (otherpages != null && otherpages != targetpages) {
                        otherpages.removeAll(sourcepages);
                    }
                    targetpages.addAll(dragTargetInsertPoint, sourcepages);
                    final List<PDFPage> newpages = new ArrayList<PDFPage>(targetpages);

                    if (otherthumbpanel == View.this) {
                        // We can't really undo when moving pages between
                        // docs, because if the other document is closed
                        // after we've dragged its pages in here, this even
                        // will still hold a reference to it via all
                        // its page objects, which could be expensive.
                        //
                        docpanel.fireUndoableEditEvent(new UndoableEditEvent(docpanel, new DropEdit(docpanel, targetpdf, oldpages, newpages)));
                    } else {
                        docpanel.fireUndoableEditEvent(new UndoableEditEvent(docpanel, Undo.DISCARD));
                        currentpage = sourcepages.get(0);
                    }
                    event.dropComplete(true);
                    getDocumentPanel().setPage(currentpage);

                } else if (DragAndDrop.canImport(transfer.getTransferDataFlavors())) {

                    //---------------------------------------
                    // Dragged external object into PDF.
                    //---------------------------------------
                    // Create a list of files by using a DragAndDrop to process the Transferable.
                    // Then create a task to load and process that list, adding the pages to this document.
                    // This PDF is fully loaded, no need to worry about linearization.
                    //---------------------------------------
                    event.acceptDrop(DnDConstants.ACTION_COPY);

                    final List<File> list = new ArrayList<File>();
                    DragAndDrop dnd = new DragAndDrop() {
                        @Override public void action(File file) {
                            list.add(file);
                        }
                        @Override public void action(java.net.URL url) {
                        }
                    };

                    final PDFViewer viewer = docpanel.getViewer();
                    if (dnd.processTransferable(transfer) && list.size() > 0 && viewer != null) {
                        final ViewerFeature[] features = viewer.getFeatures();
                        final int[] targetindex = new int[] { dragTargetInsertPoint };
                        final List<PDFPage> oldpages = new ArrayList<PDFPage>(targetpages);

                        LongRunningTask task = new LongRunningTask() {
                            private Importer.ImporterTask task;

                            public void cancel() {
                                super.cancel();
                                if (task != null) {
                                    task.cancel();
                                }
                            }

                            public boolean isCancellable() {
                                return true;
                            }

                            public float getProgress() {
                                return task==null ? 0 : task.getProgress();
                            }

                            private Importer.ImporterTask getImporterTask(File file) throws IOException {
                                for (int j=0;j<features.length;j++) {
                                    if (features[j] instanceof Importer && ((Importer)features[j]).matches(file)) {
                                        Importer importer = (Importer)features[j];
                                        if (importer instanceof PDFImporter) {
                                            ((PDFImporter)importer).setUseInputStream(true);
                                        }
                                        return importer.getImporter(viewer, file);
                                    }
                                }
                                return null;
                            }

                            public void run() throws IOException {
                                for (int i=0;i<list.size();i++) {
                                    File file = (File)list.get(i);
                                    task = getImporterTask(file);
                                    if (task != null) {
                                        final PDF newpdf = task.loadPDF();
                                        if (!isCancelled() && newpdf != null) {
                                            final List<PDFPage> newpagelist = new ArrayList<PDFPage>(newpdf.getPages());
                                            final int offset = targetindex[0];
                                            targetindex[0] += newpagelist.size();

                                            SwingUtilities.invokeLater(new Runnable() {
                                                public void run() {
                                                    targetpdf.getPages().addAll(offset, newpagelist);
                                                    List<PDFPage> newpages = new ArrayList<PDFPage>(targetpages);
                                                    final DocumentPanel fdocpanel = docpanel;
                                                    if (fdocpanel != null) {
                                                        fdocpanel.fireUndoableEditEvent(new UndoableEditEvent(docpanel, new DropEdit(docpanel, targetpdf, oldpages, newpages)));
                                                        fdocpanel.setPage(newpagelist.get(0));
                                                    }
                                                }
                                            });
                                        }
                                    }
                                }
                            }
                        };
                        task.start(viewer, UIManager.getString("PDFViewer.Loading"));
                    }
                    event.dropComplete(true);
                } else {
                    event.rejectDrop();
                }
            } catch (Throwable e) {
                Util.displayThrowable(e, docpanel);
            }
            dragTargetInsertPoint = -1;
            repaint();
        }

        private static class DropEdit extends AbstractUndoableEdit {
            
            final DocumentPanel docpanel;
            final PDF pdf;
            final PDFPage beforevisiblepage;
            final List<PDFPage> oldpages, newpages;
            
            DropEdit(DocumentPanel docpanel, PDF pdf, List<PDFPage> oldpages, List<PDFPage> newpages) {
                this.docpanel = docpanel;
                this.pdf = pdf;
                this.oldpages = oldpages;
                this.newpages = newpages;
                beforevisiblepage = docpanel.getPage();
            }

            public String getPresentationName() {
                return UIManager.getString("PDFViewer.Pages");
            }

            public boolean canUndo() {
                return docpanel != null;
            }

            public boolean canRedo() {
                return docpanel != null;
            }

            public void undo() {
                super.undo();
                List<PDFPage> pages = pdf.getPages();
                pages.clear();
                pages.addAll(oldpages); // This makes list shorter, and we
                if (beforevisiblepage != null) {
                    docpanel.setPage(beforevisiblepage); // may have deleted visible page
                }
            }

            public void redo() {
                super.redo();
                List<PDFPage> pages = pdf.getPages();
                pages.clear();
                pages.addAll(newpages); // This makes list longer
            }

        }

        public void dropActionChanged(DropTargetDragEvent event) {
        }

        //----------------------------------------------------------------------------
        // Popup methods
        //----------------------------------------------------------------------------

        JPopupMenu getPopup() {
            return popup;
        }

        //----------------------------------------------------------------------------
        // Scrollable Methods
        //----------------------------------------------------------------------------

        public void setBounds(int x, int y, int width, int height) {
            Component parent = getParent();
            if (parent != null && painterthread != null) {
                Dimension p = parent.getSize();

                // To ensure we fill our scrollpane parent entirely
                height = Math.max(height, p.height);
                width = Math.max(width, p.width);

                // height = height of overall panel
                // p.height = height of visible portion
                // y = 0 when scrolled to top, (height - p.height) when scrolled to bottom
                int cy = (p.height/2) - y; // y at center of viewport (0..height)
                if (height > 0) { // Impossible? Apparently not.
                    painterthread.setCenterComponent(cy * getComponentCount() / height);
                }
            }
            Dimension size = getSize();
            if (width != size.width) {
                animatemovements = false;           // Don't animate on resize
            }
            super.setBounds(x, y, width, height);
        }

        /**
         * Returns the maximum width and height of all children.
         */
        private synchronized Dimension getChildSize() {
            if (childsize == null) {
                int csw = 0, csh = 0;
                int n = getComponentCount();
                // Determine max child width and height
                for (int i = 0; i < n; i++) {
                    Component c = getComponent(i);
                    Dimension cs = c.getPreferredSize();
                    if (cs.width > csw) {
                        csw = cs.width;
                    }
                    if (cs.height > csh) {
                        csh = cs.height;
                    }
                }
                childsize = new Dimension(csw, csh);
            }
            return childsize;
        }

        public Dimension getPreferredSize() {
            Container parent = getParent();
            if (parent == null || parent.getBounds().width <= 0) {
                Dimension cs = getChildSize();
                return new Dimension(cs.width, cs.height * getComponentCount());
            } else {
                return doLayout(parent.getBounds().width, true);
            }
        }

        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            int val = getScrollableBlockIncrement(visibleRect, orientation, direction) / 100;
            return val == 0 ? 1 : val;
        }

        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? getParent().getHeight() : getParent().getWidth();
        }

        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        public boolean getScrollableTracksViewportHeight() {
            return false;
        }

        /**
         * When a DocumentPanel contains multiple PDFs, returning non-null
         * from this method ensures they each have a different background on
         * the thumbnail panel
         * @param ix the pdf index, from 0..num - 1 
         * @param num the num PDFs in the DocumentPanel
         */
        private Color getDocumentFragmentBackgroundColor(PDF pdf, int ix, int num) {
            if (ix + 1 == num) {
                return null;
            }
            for (int i=num-1;i>=0;i--) {
                Color c = UIManager.getColor("PDFViewer.Feature.ThumbnailPanel.fragmentBackground." + i);
                if (c != null && ix % (i + 1) == 0) {
                    return c;
                }
            }
            return UIManager.getColor("PDFViewer.Feature.ThumbnailPanel.fragmentBackground");
        }

        /**
         * When a DocumentPanel contains multiple PDFs, returning non-null
         * from this method ensures they will have a separator between them
         * @param ix the pdf index, from 0..num - 1 
         * @param num the num PDFs in the DocumentPanel
         */
        private Color getDocumentFragmentSeparatorColor(PDF pdf, int ix, int num) {
            return UIManager.getColor("PDFViewer.Feature.ThumbnailPanel.fragmentSeparatorColor");
        }

        public void paintComponent(Graphics g) {
            super.paintComponent(g);

            PDF lastpdf = docpanel.getPage(0).getPDF();
            int lastix = 0;
            int pdfix = 0;
            for (int i=0;i<=getComponentCount();i++) {
                SinglePagePanel spp = i == getComponentCount() ? null : (SinglePagePanel)getComponent(i);
                PDF pagepdf = spp == null ? docpanel.getPDF() : spp.getPage().getPDF();
                if (pagepdf != lastpdf && lastpdf != docpanel.getPDF()) {
                    Color c = getDocumentFragmentBackgroundColor(lastpdf, pdfix, numPdfs);
                    if (c != null) {
                        g.setColor(c);
                        if (columns == 1) {
                            Rectangle b0 = getComponent(lastix).getBounds();
                            Rectangle b1 = spp.getBounds();
                            int y0 = b0.y - vgap / 2;
                            int y1 = b1.y - vgap / 2;
                            g.fillRect(0, y0, getWidth(), y1 - y0);
                        } else {
                            Rectangle b0 = getComponent(lastix).getBounds();
                            int x0 = b0.x - hgap / 2;
                            for (int j=lastix;j<i;j++) {
                                Rectangle b1 = getComponent(j).getBounds();
                                if (b1.x <= b0.x && j != lastix) {
                                    // column wrap
                                    int y0 = b0.y - vgap / 2;
                                    int y1 = b0.y + b0.height + vgap / 2;
                                    int x1 = getWidth();
                                    g.fillRect(x0, y0, x1 - x0, y1 - y0);
                                    b0 = b1;
                                    x0 = 0;
                                }
                                if (j + 1 == i) {
                                    int y0 = b0.y - vgap / 2;
                                    int y1 = b0.y + b0.height + vgap / 2;
                                    int x1 = b1.x + b1.width + (hgap + margin) / 2;
                                    g.fillRect(x0, y0, x1 - x0, y1 - y0);
                                }
                            }
                        }
                    }
                    c = getDocumentFragmentSeparatorColor(lastpdf, pdfix, numPdfs);
                    if (c != null) {
                        g.setColor(c);
                        Rectangle cb = spp.getBounds();
                        if (columns == 1) {
                            // Draw a horizontal insertion point
                            int y = cb.y - (margin + cursorWidth) / 2;
                            y = Math.max(0, Math.min(getHeight() - cursorWidth, y));
                            for (int j=0;j<=cb.width - cursorWidth;j+=cursorWidth * 2) {
                                g.fillRect(cb.x + j, y, cursorWidth, cursorWidth);
                            }
                        } else {
                            // Draw a vertical insertion point
                            int x = cb.x - (margin + hgap + cursorWidth) / 2;
                            x = Math.max(0, Math.min(getWidth() - cursorWidth, x));
                            for (int j=0;j<=cb.height - cursorWidth;j+=cursorWidth * 2) {
                                g.fillRect(x, cb.y + j, cursorWidth, cursorWidth);
                            }
                        }
                    }
                    pdfix++;
                    lastix = i;
                }
                lastpdf = pagepdf;
            }


            if (dragTargetInsertPoint >= 0 && getComponentCount() > 0) {
                boolean end = dragTargetInsertPoint == getComponentCount();
                Component c = getComponent(end ? dragTargetInsertPoint - 1 : dragTargetInsertPoint);

                Color selection = UIManager.getColor("List.selectionBackground");
                if (selection == null) {
                    selection = new Color(defaultListselectionBackgroundColor, false);
                }
                g.setColor(selection);

                Rectangle cb = c.getBounds();
                if (columns == 1) {
                    // Draw a horizontal insertion point
                    int y = cb.y - (margin + cursorWidth) / 2;
                    if (end) {
                        y += cb.height;
                    }
                    y = Math.max(0, Math.min(getHeight() - cursorWidth, y));
                    g.fillRect(cb.x, y, cb.width, cursorWidth);
                } else {
                    // Draw a vertical insertion point
                    int x = cb.x - (margin + hgap + cursorWidth) / 2;
                    if (end) {
                        x += cb.width + hgap;
                    }
                    x = Math.max(0, Math.min(getWidth() - cursorWidth, x));
                    g.fillRect(x, cb.y, cursorWidth, cb.height);
                }
            }
        }

        //----------------------------------------------------------------------------
        // AutoScroll Methods
        //----------------------------------------------------------------------------

        private static final int SCROLLINSET = 100;

        public void autoscroll(Point p) {
            JScrollPane scrollpane = (JScrollPane)SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
            if (scrollpane != null) {
                // Grr. Something rotten in OpenJDK7 7u45 here, can observe issues where:
                // a) Autoscroll timer is not cleared immediately after drop - still seeing events come in
                // b) Position passed in is not the correct one, but seems to be the position before the last scroll.
                // Not sure if b) derives from a) but have worked around a by extending DropTarget so going to
                // assume fixed. If not, alternative mouse position code is commented out here.
                int y = p.y;
                Rectangle r = getVisibleRect();
                int diff = 0;
                if (y <= r.y + SCROLLINSET) {
                    diff = y - (r.y + SCROLLINSET);
                } else if (y >= r.y + r.height - SCROLLINSET) {
                    diff = y - (r.y + r.height - SCROLLINSET);
                }
                if (diff != 0) {
                    diff = (int)Math.pow(Math.abs(diff), 1.5) * (diff < 0 ? -1 : 1);    // log acceleration
                    r.y += diff;
                    scrollRectToVisible(r);
                }
            }
        }

        public Insets getAutoscrollInsets() {
            Rectangle r = getVisibleRect();
            Dimension size = getSize();
            return new Insets(r.y+SCROLLINSET, 0, size.height-r.y-r.height+SCROLLINSET, 0);
        }

        // PropertyChangeNotifications

        /**
         * We're being notified that our selected list has changed. If we're
         * not batching, fire an event with the previous list and the new list,
         * then make the new list the previous one.
         */
        void notifySelectionChange(SinglePagePanel panel, boolean selected) {
            if (!batchNotifications) {
                List<SinglePagePanel> list = new ArrayList<SinglePagePanel>();
                for (int i=0;i<getComponentCount();i++) {
                    panel = (SinglePagePanel)getComponent(i);
                    if (panel.isSelected()) {
                        list.add(panel);
                    }
                }
                firePropertyChange("selection", Collections.unmodifiableList(previousNotification), Collections.unmodifiableCollection(list));
                previousNotification = list;
            }
        }

        /**
         * Batch any notifySelectionChange calls - true will hold them,
         * false will fire them all out immediately
         */
        void batchNotifySelectionChange(boolean batch) {
            if (batchNotifications != batch) {
                batchNotifications = batch;
                notifySelectionChange(null, false);
            }
        }

        private Rectangle scrollSource, scrollDestination;
        private static final int scrollNumMillis = 500;
        private long scrollStart;
        private javax.swing.Timer scrollTimer;

        /**
         * Scroll smoothly to the specified rectangle by animating the process
         */
        @Override public void scrollRectToVisible(Rectangle r) {
            scrollSource = getVisibleRect();
            if (scrollSource.width < r.width) {
                r.x = 0;
                r.width = scrollSource.width;
            }
            if (scrollSource.height < r.height) {
                r.y = 0;
                r.height = scrollSource.height;
            }
            if (scrollSource.contains(r)) {
                return;
            }
            // This ensures we scroll the minimum possible distance to bring
            // rectangle into view
            if (r.x + r.width > scrollSource.x + scrollSource.width) {
                r.x = r.x + r.width - scrollSource.width;
            }
            if (r.y + r.height > scrollSource.y + scrollSource.height) {
                r.y = r.y + r.height - scrollSource.height;
            }
            scrollDestination = r;
            scrollStart = System.currentTimeMillis();
            if (scrollTimer == null) {
                scrollTimer = new javax.swing.Timer(20, new ActionListener() {
                    public void actionPerformed(ActionEvent event) {
                        Rectangle r = getVisibleRect();
                        float t = Math.min((float)(System.currentTimeMillis() - scrollStart) / scrollNumMillis, 1);
                        float t1 = 1-t;
                        t = t*t1*t1*0 + t*t*t1*3 + t*t*t*1;
                        int dx = scrollSource.x + Math.round((scrollDestination.x - scrollSource.x) * t);
                        int dy = scrollSource.y + Math.round((scrollDestination.y - scrollSource.y) * t);

                        View.super.scrollRectToVisible(new Rectangle(dx, dy, r.width, r.height));
                        if (t == 1) {
                            scrollTimer.stop();
                            scrollTimer = null;
                        }
                    }
                });
                scrollTimer.setCoalesce(true);
                scrollTimer.start();
            }
        }

        /**
         * Return true if the selected draggable panels in this ThumbnailPanel include an entire PDF.
         * @since 2.26
         */
        public boolean isEntireDraggablePDFSelected() {
            boolean zero = false;
            Map<PDF,AtomicInteger> total = new HashMap<PDF,AtomicInteger>();
            AtomicInteger all = new AtomicInteger(docpanel.getNumberOfPages());
            for (int i=0;i<getComponentCount();i++) {
                SinglePagePanel panel = (SinglePagePanel)getComponent(i);
                if (panel.isSelected() && panel.isDraggable()) {
                    PDF pdf = panel.getPage().getPDF();
                    AtomicInteger v = total.get(pdf);
                    if (v == null) {
                        total.put(pdf, v = new AtomicInteger(pdf.getNumberOfPages()));
                    }
                    zero |= v.decrementAndGet() == 0;
                    zero |= all.decrementAndGet() == 0;
                }
            }
            return zero;
        }

        /**
         * Return true if the any draggable panels in this ThumbnailPanel are selected
         * @since 2.26
         */
        public boolean isAnythingDraggableSelected() {
            for (int i=0;i<getComponentCount();i++) {
                SinglePagePanel panel = (SinglePagePanel)getComponent(i);
                if (panel.isSelected() && panel.isDraggable()) {
                    return true;
                }
            }
            return false;
        }

    }

    /**
     * Factory to create SinglePaegPanels which will cache, to allow
     * pages to be removed and readded without being discarded. Necessary
     * as moving a page in a PDF often involves taking it temporarily
     * out of the page tree.
     */
    static class SinglePagePanelFactory {

        private final View thumbpanel;
        private final WeakHashMap<Object,SinglePagePanel> map;
        private int thumbnailsize;

        SinglePagePanelFactory(View thumbpanel, int thumbnailsize) {
            this.thumbpanel = thumbpanel;
            this.thumbnailsize = thumbnailsize;
            this.map = new WeakHashMap<Object,SinglePagePanel>();
        }

        synchronized SinglePagePanel getSinglePagePanel(Integer pagenumber) {
            SinglePagePanel panel = (SinglePagePanel)map.get(pagenumber);
            if (panel == null) {
                map.put(pagenumber, panel = new SinglePagePanel(thumbpanel, pagenumber.intValue()));
                panel.setThumbnailSize(thumbnailsize);
            }
            return panel;
        }

        synchronized SinglePagePanel getSinglePagePanel(PDFPage page) {
            SinglePagePanel panel = map.get(page);
            if (panel == null) {
                map.put(page, panel = new SinglePagePanel(thumbpanel, page));
                panel.setThumbnailSize(thumbnailsize);
            }
            return panel;
        }

        int getSize() {
            return thumbnailsize;
        }

        void setSize(int size) {
            if (size != thumbnailsize && size >= 20 && size <= 400) {
                this.thumbnailsize = size;
                for (Iterator<SinglePagePanel> i = map.values().iterator();i.hasNext();) {
                    SinglePagePanel panel = i.next();
                    if (panel.getParent() != null) {
                        panel.setThumbnailSize(thumbnailsize);
                    } else {
                        i.remove();
                    }
                }
                PDFViewer viewer = thumbpanel.getDocumentPanel().getViewer();
                Preferences preferences = viewer == null ? null : viewer.getPreferences();
                if (preferences != null) {
                    preferences.put("thumbnailPanelSize", Integer.toString(size));
                }
            }
        }

        void enableEditing() {
            for (Iterator<SinglePagePanel> i = map.values().iterator();i.hasNext();) {
                SinglePagePanel panel = i.next();
                if (panel.getParent() != null) {
                    panel.initCursor();
                }
            }
        }

    }

    /**
     * <p>
     * A {@link JPanel} representing a single page image. These are the children of the {@link View}
     * object. Instances of this class fire a "selected" {@link PropertyChangeEvent} when they
     * are selected or deselected. If you're holding a reference to one of these objects, be
     * advised they will be deleted when the page they are displaying is deleted or moved
     * to another {@link View} (in that case a new SinglePagePanel is also created on the new
     * View).
     * </p><p>
     * There's generally no need to worry about this class unless you're implementing your owm
     * {@link ThumbnailSelectionAction} and need to know which pages are selected.
     * </p>
     * @since 2.12
     */
    public static class SinglePagePanel extends JPanel implements MouseListener, DragSourceListener, DragGestureListener, Transferable, PropertyChangeListener, Runnable {

        private static final Font PAGENUMBERFONT = UIManager.getFont("Label.font").deriveFont(UIManager.getFont("Label.font").getSize2D() * UIManager.getInt("PDFViewer.Feature.ThumbnailPanel.pageNumberFontScale") / 100f);
        static final DataFlavor DRAGGABLE_PAGE = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType, "SinglePagePanel");

        public static final int FLAG_NONE = 0;
        public static final int FLAG_CUT = 1;
        public static final int FLAG_COPY = 2;

        private final View thumbpanel;
        private Dimension imagesize;
        private int thumbnailsize;
        private float dpi;
        private int pagenumber;
        private PDFPage page;
        private PagePainter painter;
        private String pagenumberstring;
        private boolean selected, draggable;
        private int flags;
        private volatile BufferedImage image, oldimage;
        private volatile boolean isrendering;

        /**
         * Constructor for a loaded page.
         */
        SinglePagePanel(View thumbpanel, PDFPage page) {
            this.thumbpanel = thumbpanel;
            this.pagenumber = thumbpanel.getDocumentPanel().getPageNumber(page);
            this.page = page;
            painter = thumbpanel.getDocumentPanel().getParser(page).getPagePainter(page);
            init();
            run();      // Sets page
            draggable = !DISALLOW_EMBEDDED_EDITS || page.getPDF() == thumbpanel.getDocumentPanel().getPDF();
            setPageNumberString();
        }
        
        /**
         * Constructor for a page that has not yet been loaded.
         */
        SinglePagePanel(View thumbpanel, int pagenumber) {
            this.thumbpanel = thumbpanel;
            this.pagenumber = pagenumber;
            init();
            thumbpanel.getDocumentPanel().getLinearizedSupport().invokeOnPageLoad(pagenumber, this);
            setPageNumberString();
        }

        /**
         * This is called only after the page is laoded.
         */
        public void run() {
            // The page has been loaded
            if (page == null) {
                PDF pdf = thumbpanel.getPdf();
                if (pdf != null) {
                    page = thumbpanel.getDocumentPanel().getPage(pagenumber);
                    setThumbnailSize(thumbnailsize);
                    initCursor();
                }
            }
            addListener();
        }

        private void addListener() {
            if (page != null) {
                page.addPropertyChangeListener(this);
                List<PDFAnnotation> annots = page.getAnnotations();
                for (int i=0;i<annots.size();i++) {
                    PDFAnnotation a = annots.get(i);
                    a.addPropertyChangeListener(this);
                }
            }
        }

        void init() {
            setOpaque(false);
            addMouseListener(this);
            DragSource ds = DragSource.getDefaultDragSource();
            ds.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_MOVE, this);
            initCursor();
        }

        void initCursor() {
            // Note this is where we would set the cursor to show a 
            // hand to indicate the page is draggable - in practice
            // this doesn't add any information so don't do it.
        }

        Dimension getImageSize() {
            if (imagesize == null && pagenumber > 0) {
                return ((SinglePagePanel) getParent().getComponent(pagenumber - 1)).getImageSize();
            }
            return imagesize;
        }

        public Dimension getPreferredSize() {
            int pad = thumbpanel.outlineWidth + thumbpanel.outlineWidth + UIManager.getInt("PDFViewer.Feature.ThumbnailPanel.dropShadowWidth") + thumbpanel.margin + thumbpanel.margin;
            Dimension is = getImageSize();
            return (is == null) ?
                new Dimension(0, 0) : 
                new Dimension(is.width + pad, is.height + pad + PAGENUMBERFONT.getSize() + 1);
        }

        /**
         * Resize the SinglePagePanel
         */
        synchronized void setThumbnailSize(int thumbnailsize) {
            this.thumbnailsize = thumbnailsize;
            PDFPage page = getPage();
            if (page != null) {
                if (painter != null) {
                    painter.interrupt();
                }
                painter = thumbpanel.docpanel.getParser(page).getPagePainter(page);
                isrendering = false;
                float[] box = page.getBox("ViewBox");
                float w = box[2] - box[0];
                float h = box[3] - box[1];
                float mul = thumbnailsize / Math.max(w, h);
                if (imagesize == null) {
                    imagesize = new Dimension();
                }
                imagesize.width = (int) Math.floor(w * mul);
                imagesize.height = (int) Math.floor(h * mul);
                dpi = Math.min(imagesize.width / w, imagesize.height / h) * 72;
                image = null;
                revalidate();
                thumbpanel.resetChildSize();
                thumbpanel.restartPainterThread();
            }
        }

        /**
         * Get the Page this SinglePagePanel contains
         */
        public PDFPage getPage() {
            return page;
        }

        public int getPageNumber() {
            return pagenumber;
        }

        private void setPageNumber(int pagenumber) {
            this.pagenumber = pagenumber;
            setPageNumberString();
            repaint();
        }

        private void setPageNumberString() {
            pagenumberstring = thumbpanel.uselabels ? thumbpanel.docpanel.getPageLabels().get(pagenumber) : null;
            if (pagenumberstring == null) {
                pagenumberstring = thumbpanel.pageNumberFormat.format(pagenumber + 1);
            }
        }

        public void addNotify() {
            super.addNotify();
            addListener();
        }

        public void removeNotify() {
            super.removeNotify();
            if (painter != null) {
                painter.interrupt();
                PDFPage page = getPage();
                page.removePropertyChangeListener(this);
                List<PDFAnnotation> annots = page.getAnnotations();
                for (int i=0;i<annots.size();i++) {
                    PDFAnnotation a = annots.get(i);
                    a.removePropertyChangeListener(this);
                }
            }
            isrendering = false;
            if (isSelected()) {
                thumbpanel.notifySelectionChange(this, false);
            }
            // Don't want to clear painter immediately, we might re-add it
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            Component ancestor = SwingUtilities.getAncestorOfClass(JScrollPane.class, SinglePagePanel.this);
                            if (ancestor == null) {
                                painter = null;
                            }
                        }
                    });
                }
            });
        }

        public void propertyChange(PropertyChangeEvent event) {
            String name = event.getPropertyName();
            if (name.endsWith("box") || name.equals("orientation")) {
                setThumbnailSize(thumbnailsize);
            } else {
                if (name.equals("annotations") && event.getSource() == getPage()) {
                    Object oldval = event.getOldValue();
                    Object newval = event.getNewValue();
                    if (oldval instanceof PDFAnnotation) {
                        ((PDFAnnotation)oldval).removePropertyChangeListener(this);
                    }
                    if (newval instanceof PDFAnnotation) {
                        ((PDFAnnotation)newval).addPropertyChangeListener(this);
                    }
                }
                oldimage = image;
                image = null;
                if (painter != null) {
                    painter.interrupt();
                }
                isrendering = false;
                thumbpanel.restartPainterThread();
            }
        }

        /**
         * Mark this page as selected. Fires a "selected" {@link PropertyChangeEvent}
         */
        public void setSelected(boolean selected) {
            if (selected != this.selected) {
                this.selected = selected;
                repaint();
                firePropertyChange("selected", false, true);
                thumbpanel.notifySelectionChange(this, selected);
            }
        }

        /**
         * Return true if this page is selected
         */
        public boolean isSelected() {
            return selected;
        }

        /**
         * Return true if this page can be dragged to a new location
         * if the rest of the document allows it.
         * @since 2.26
         */
        public boolean isDraggable() {
            return draggable;
        }

        /**
         * Return true if this page can be used as a drop target, so content
         * can be inserted before it if the rest of the document allows it.
         * @since 2.26
         */
        public boolean isDropTarget() {
            return draggable;   // same test
        }

        /**
         * Set the flags on this item.
         * @see #FLAG_NONE
         * @see #FLAG_CUT
         * @see #FLAG_COPY
         * @param flags a bitmask of flags
         */
        public void setFlags(int flags) {
            if (flags != this.flags) {
                this.flags = flags;
                repaint();
                firePropertyChange("flags", false, true);
            }
        }

        /**
         * Returns the flags set on this item.
         */
        public int getFlags() {
            return flags;
        }

        /**
         * Return true if the page image is currently rendering
         */
        boolean isRendering() {
            return isrendering;
        }

        /**
         * Return true if this page needs to be redrawn
         */
        boolean isRendered() {
            return image == null;
        }

        /**
         * Draw the image if it's not already drawn.
         */
        synchronized void drawImage() {
            if (image == null && painter != null) {
                isrendering = true;
                try {
                    image = painter.getImage(dpi, getColorModel());
                    if (image != null) {
                        oldimage = null;
                    }
                    isrendering = false;
                    notifyAll();
                    repaint();
                } catch (IllegalStateException e) {
                    // Page was still being modified by e.g. flatten
                    // Another event will update us soon
                    isrendering = false;
                }
            }
        }

        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            Color foreground = UIManager.getColor("List.foreground");
            if (foreground == null) {
                foreground = new Color(defaultListforegroundColor, false);
            }
            Color background = UIManager.getColor("List.background");
            if (background == null) {
                background = new Color(defaultListbackgroundColor, false);
            }

            Dimension size = getSize();
            if (isSelected()) {
                background = UIManager.getColor("List.selectionBackground");
                if (background == null) {
                    background = new Color(defaultListselectionBackgroundColor);
                }
                g.setColor(background);
                g.fillRect(0, 0, getWidth(), getHeight());
            }

            Dimension is = getImageSize();
            if (is != null) {
                // Calculate image border decoration bounds
                int iw = is.width + 2;
                int ih = is.height + 2;
                int ix = (size.width - iw) / 2;
                int iy = (((size.height - PAGENUMBERFONT.getSize()) - 1) - ih) / 2;

                // Draw shadow
                Color shadow = getIntermediateColor(background, foreground);
                g.setColor(shadow);
                g.fillRect(ix + UIManager.getInt("PDFViewer.Feature.ThumbnailPanel.dropShadowWidth"), iy + UIManager.getInt("PDFViewer.Feature.ThumbnailPanel.dropShadowWidth"), iw, ih);

                // Draw image
                BufferedImage image = this.image;
                if (image == null) {
                    image = this.oldimage;
                }
                if (image != null) {
                    Graphics2D g2 = (Graphics2D) g;
                    Composite comp = g2.getComposite();
                    if ((flags & FLAG_CUT) > 0) {
                        AlphaComposite ghostcomp = AlphaComposite.SrcOver.derive(0.5f);
                        g2.setComposite(ghostcomp);
                    }
                    g2.drawRenderedImage(image, AffineTransform.getTranslateInstance(ix + 1, iy + 1));
                    g2.setComposite(comp);
                } else {
                    g.setColor(Color.gray);
                    g.fillRect(ix + 1, iy + 1, is.width, is.height);
                }

                // Draw solid border
                g.setColor(foreground);
                g.drawRect(ix, iy, iw, ih);
            }

            // Draw page number
            if (isSelected()) {
                g.setColor(UIManager.getColor("List.selectionForeground"));
            } else {
                g.setColor(UIManager.getColor("List.foreground"));
            }
            g.setFont(PAGENUMBERFONT);
            FontRenderContext frc = ((Graphics2D)g).getFontRenderContext();
            Rectangle2D nw2d = PAGENUMBERFONT.getStringBounds(pagenumberstring, frc);
            int nw = (int) nw2d.getWidth();
            int nx = (size.width - nw) / 2;
            int ny = size.height - thumbpanel.margin;
            g.drawString(pagenumberstring, nx, ny);
        }

        static Color getIntermediateColor(Color c1, Color c2) {
            int r1 = c1.getRed(), g1 = c1.getGreen(), b1 = c1.getBlue();
            int r2 = c2.getRed(), g2 = c2.getGreen(), b2 = c2.getBlue();
            int r3 = (r1 < r2) ? r1 + ((r2 - r1) / 2) : r2 + ((r1 - r2) / 2);
            int g3 = (g1 < g2) ? g1 + ((g2 - g1) / 2) : g2 + ((g1 - g2) / 2);
            int b3 = (b1 < b2) ? b1 + ((b2 - b1) / 2) : b2 + ((b1 - b2) / 2);
            return new Color(r3, g3, b3);
        }

        public void mouseEntered(MouseEvent event) { }
        public void mouseExited(MouseEvent event) { }
        public void mouseClicked(MouseEvent event) {
            getParent().requestFocusInWindow();
        }

        public void mouseReleased(MouseEvent event) {
            if (event.isPopupTrigger() && thumbpanel.getPopup() != null) {
                thumbpanel.getPopup().show(this, event.getX(), event.getY());
            }
        }

        public void mousePressed(MouseEvent event) {
            if (event.isPopupTrigger() && thumbpanel.getPopup() != null) {
                thumbpanel.getPopup().show(this, event.getX(), event.getY());
            } else if (event.getButton() == 1) {
                selectAction(event);
            }
        }

        void selectAction(InputEvent event) {
            boolean shift = event.isShiftDown();
            boolean ctrl = event.isControlDown() || event.isMetaDown();

            thumbpanel.batchNotifySelectionChange(true);
            if (!shift && !ctrl) {
                // Do this so we can update menu, then set page.
                for (int i=0;i<thumbpanel.getComponentCount();i++) {
                    SinglePagePanel spp = (SinglePagePanel)thumbpanel.getComponent(i);
                    spp.setSelected(spp == this);
                }
                thumbpanel.docpanel.getLinearizedSupport().invokeOnPageLoadWithDialog(pagenumber, new Runnable() {
                    public void run() {
                        if (page == null) {
                            page = thumbpanel.getDocumentPanel().getPage(pagenumber);
                            setThumbnailSize(thumbnailsize);
                        }
                        thumbpanel.docpanel.setPage(page);
                    }
                });
                thumbpanel.currentPagePanel = this;
            } else if (ctrl) {
                if (isSelected()) {
                    // Do not allow zero pages to be selected
                    int count = 0;
                    for (int i=0;i<thumbpanel.getComponentCount();i++) {
                        SinglePagePanel spp = (SinglePagePanel)thumbpanel.getComponent(i);
                        if (spp.isSelected()) {
                            count++;
                        }
                    }
                    if (count > 1) {
                        setSelected(false);
                    }
                } else {
                    setSelected(true);
                }
                thumbpanel.currentPagePanel = this;
            } else if (shift) {
                DocumentPanel docpanel = thumbpanel.docpanel;
                PDFPage currentpage = docpanel.getPage();
                if (currentpage == null) {
                    currentpage = docpanel.getViewport().getRenderingPage();
                }
                if (currentpage != null) {
                    boolean toggle = false;
                    for (int i=0;i<thumbpanel.getComponentCount();i++) {
                        SinglePagePanel spp = (SinglePagePanel)thumbpanel.getComponent(i);
                        spp.setSelected(toggle);
                        if (spp.getPage() == currentpage) {
                            spp.setSelected(true);
                            toggle = !toggle;
                        }
                        if (spp == this) {
                            spp.setSelected(true);
                            toggle = !toggle;
                        }
                    }
                }
                thumbpanel.currentPagePanel = this;
            }
            thumbpanel.scrollRectToVisible(getBounds());
            thumbpanel.batchNotifySelectionChange(false);
        }

        public void dragGestureRecognized(DragGestureEvent event) {
            if (thumbpanel.isEditable() && thumbpanel.isDraggable() && isDraggable()) {
                if (!isSelected()) {
                    selectAction((MouseEvent)event.getTriggerEvent());
                }
                try {
                    event.startDrag(null, this, this);
                } catch (InvalidDnDOperationException e) {
                    // drag already in progress, ignore - nothing we can do at this point, drag/drop is jammed
                }
            }
        }

        public Object getTransferData(DataFlavor flavor) {
            return flavor == DRAGGABLE_PAGE ? this : null;
        }

        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { DRAGGABLE_PAGE };
        }

        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return flavor == DRAGGABLE_PAGE;
        }

        public void dragDropEnd(DragSourceDropEvent event) { }
        public void dragEnter(DragSourceDragEvent event) { }
        public void dragExit(DragSourceEvent event) { }
        public void dragOver(DragSourceDragEvent event) { }
        public void dropActionChanged(DragSourceDragEvent event) { }

        public String toString() {
            return super.toString() + " P"+(thumbpanel.docpanel.getPageNumber(getPage()));
        }

    }

    /**
     * This interface should be implemented by any {@link ViewerFeature} that
     * should be available as an action on the {@link View}, either by the popup
     * menu or by keypress.
     *
     * @see View
     * @see TextSelectionAction
     * @see AreaSelectionAction
     * @since 2.12
     */
    public interface ThumbnailSelectionAction {

        /**
         * Return an Action for the specified View, or <code>null</code> if this
         * action is not appropriate for the View (for example, because it edits
         * the Document and the {@link View#isEditable} method returns false
         */
        public Action getAction(ThumbnailPanel.View view);

    }


}
