// $Id: TextTool.java 39939 2021-05-06 16:37:40Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.viewer2.util.PropertyParser;
import org.faceless.pdf2.viewer2.util.RichTextTransferHandler;
import org.faceless.util.WeakPropertyChangeSupport;
import org.faceless.pdf2.*;
import java.lang.ref.WeakReference;
import javax.swing.*;
import java.awt.event.*;
import java.awt.datatransfer.*;
import java.awt.geom.*;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.beans.*;
import java.util.regex.Pattern;
import java.util.List;
import org.faceless.pdf2.viewer2.feature.TextSelection.RangeList;
import org.faceless.pdf2.viewer2.feature.TextSelection.Range;

/**
 * <p>
 * This widget allows text to be selected from the DocumentViewport. By default it
 * uses a translucent yellow highlight, but this can be set using the
 * {@link #setHighlightType setHighlightType()} method or the matching initialization
 * parameters. When the text is highlighted, right-clicking on the page will bring
 * up a list of any {@link TextSelectionAction} features specified in the viewer.
 * </p><p>
 * The TextTool object selects text in a document-wide manner, not just from the currently
 * visible pages (which was the behaviour prior to 2.11.7). This means operations like
 * {@link #selectAll} or {@link #select(Pattern)} require all the text in the PDF to be
 * extracted, which can cause these two operations to take some time to complete. One solution
 * is to add the {@link BackgroundTextExtractor} feature, which will automatically begin the
 * text extaction when a PDF is loaded.
 * </p><p>
 * The {@link TextHighlighter} class performs a similar job, except no mouse selection or
 * no copying to the clipboard is possible. These differences mean if you simply want to
 * highlight text matching a pattern, this class will have to extract text from the entire
 * PDF first, whereas that class will do it on a page by page basis.
 * </p>
 *
 * <div class="initparams">
 * The following <a href="../doc-files/initparams.html">initialization parameters</a> can be specified to configure this feature.
 * <table summary="">
 * <tr><th>highlightColor</th><td>A 32-bit color value, eg 0x80FF0000 (for translucent red)</td></tr>
 * <tr><th>highlightType</th><td>One of <code>block</code>, <code>underline</code>, <code>outline</code>, <code>doubleunderline</code>, <code>strikeout</code> or <code>doublestrikeout</code></td></tr>
 * <tr><th>highlightMargin</th><td>A floating point value &gt;= 0</td></tr>
 * <tr><th>draggable</th><td><code>true</code> or <code>false</code>, for {@link #setDraggable}</td></tr>
 * <tr><th>selectPattern</th><td>A <code>java.util.regex.Pattern</code> to be searched for automatically when a PDF is loaded</td></tr>
 * <tr><th>selectTextToolOnTextSelection</th><td>if specified, toggle text selection mode in the viewer when text is selected by other means (e.g. clicking a search result)</td></tr>
 * <tr><th>selectionOrder</th><td><code>display</code> or <code>natural</code>, to set the order text is selected in when dragging the mouse. Defaults to "natural".
 * </table>
 * </div>
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">TextTool</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @see TextSelectionAction
 * @since 2.8.5
 */
public class TextTool extends ToggleViewerWidget implements DocumentPanelListener, PagePanelInteractionListener, PagePanelListener, PropertyChangeListener {
    private TransferHandler transferhandler;      // The handler to manage clipboard transfer
    private final Action selectAllAction;               // The Swing action run by "select all"

    private DocumentViewport viewport;  // The viewport we're working on
    private boolean draggable, copyable;
    private Paint highlightcolor;
    private Stroke highlightstroke;
    private int highlighttype;
    private float highlightmargin;
    private Pattern loadpattern;
    private Map<PageExtractor,RangeList> allranges;  // Weak mapping from PageExtractor->RangeList
    private Point2D startPoint;         // Where the mouse went down at the start of a drag
    private int startPageNumber;        // Where the mouse went down at the start of a drag
    private Comparator<PageExtractor.Text> order;
    private PropertyChangeSupport pcs = new WeakPropertyChangeSupport(this);

    private boolean selectTextToolOnTextSelection;
    private PageExtractor.Text textSelection;

    /**
     * A parameter to {@link #setHighlightType} which will highlight the selected
     * text using a solid block of (usually translucent) color
     */
    public static final int TYPE_BLOCK = TextSelection.TYPE_BLOCK;

    /**
     * A parameter to {@link #setHighlightType} which will highlight the selected
     * text using a single underline.
     * @since 2.11.4
     */
    public static final int TYPE_UNDERLINE = TextSelection.TYPE_UNDERLINE;

    /**
     * A parameter to {@link #setHighlightType} which will highlight the selected
     * text using a double underline.
     * @since 2.11.4
     */
    public static final int TYPE_DOUBLEUNDERLINE = TextSelection.TYPE_DOUBLEUNDERLINE;

    /**
     * A parameter to {@link #setHighlightType} which will outline the selected
     * text.
     * @since 2.11.4
     */
    public static final int TYPE_OUTLINE = TextSelection.TYPE_OUTLINE;

    /**
     * A parameter to {@link #setHighlightType} which will strike-out the selected
     * text.
     * @since 2.11.4
     */
    public static final int TYPE_STRIKEOUT = TextSelection.TYPE_STRIKEOUT;

    /**
     * A parameter to {@link #setHighlightType} which will strike-out the selected
     * text with a double line
     * @since 2.11.4
     */
    public static final int TYPE_DOUBLESTRIKEOUT = TextSelection.TYPE_DOUBLESTRIKEOUT;


    /**
     * Create a new TextTool object
     */
    public TextTool() {
        super("TextTool", DragScroll.GROUP);
        setButton(DragScroll.GROUP, "PDFViewer.Feature.TextTool.icon", "PDFViewer.tt.TextTool");
        selectAllAction = new AbstractAction(DefaultEditorKit.selectAllAction) {
            public void actionPerformed(ActionEvent event) {
                selectAll();
            }
        };
        allranges = new WeakHashMap<PageExtractor,RangeList>();  // Only accessed from synchronized methods, which doesn't help!
        setHighlightType(TYPE_BLOCK, null, null, 0);
        setDraggable(true);
        setOrder(PageExtractor.NATURALORDER);
    }

    /**
     * Set the type and color of the highlight. If drawing a "block",
     * the specified color should be translucent so the text behind it
     * is still visible when selected.
     * @param type the type of highlight - {@link #TYPE_BLOCK}, {@link #TYPE_OUTLINE},
     * {@link #TYPE_UNDERLINE}, {@link #TYPE_DOUBLEUNDERLINE}, {@link #TYPE_STRIKEOUT}
     * or {@link #TYPE_DOUBLESTRIKEOUT}
     * @param paint the Paint to paint this highlight with
     * @param stroke the Stroke to stroke this highlight with (not used for {@link #TYPE_BLOCK})
     * @param margin the distance in points from the text
     * @since 2.11.4
     */
    public void setHighlightType(int type, Paint paint, Stroke stroke, float margin) {
        this.highlighttype = type;
        this.highlightcolor = paint;
        this.highlightstroke = stroke;
        this.highlightmargin = margin;
    }

    /**
     * Set whether this TextTool can select text by dragging over it.
     * For all TextTool instances that interact with the mouse, this should
     * be set to true (the default)
     * @param draggable whether this TextTool can select text with the mouse
     */
    public void setDraggable(boolean draggable) {
        this.draggable = draggable;
    }

    /**
     * Set the order in which text is selected in this TextTool, either
     * {@link PageExtractor#NATURALORDER} or {@link PageExtractor#DISPLAYORDER}.
     * For documents internally constructed in a logical order, natural is
     * usually the best choice as for text in columns, it will allow you to
     * select an individual column. However there is no way to determine this
     * in advance, so if there's any doubt, then "display" will always give
     * acceptable results. The default is "natural"
     * @param order the order in which text is selected.
     * @since 2.12
     */
    public void setOrder(Comparator<PageExtractor.Text> order) {
        this.order = order;
    }

    public void initialize(PDFViewer viewer) {
        super.initialize(viewer);

        this.transferhandler = Util.createTransferHandler(viewer);
        this.highlightcolor = PropertyParser.getPaint(getFeatureProperty(viewer, "highlightColor"), highlightcolor);
        this.highlighttype = PropertyParser.getHighlightType(getFeatureProperty(viewer, "highlightType"), highlighttype);
        this.highlightstroke = PropertyParser.getStroke(getFeatureProperty(viewer, "highlightStroke"), highlightstroke);
        this.highlightmargin = PropertyParser.getMargin(getFeatureProperty(viewer, "highlightMargin"), highlightmargin);
        if (highlightcolor == null) {
            highlightcolor = UIManager.getColor("PDFViewer.Feature.TextHighlighter.color");
        }

        String val = getFeatureProperty(viewer, "draggable");
        if (val!=null) {
            setDraggable("true".equals(val));
        }
        val = getFeatureProperty(viewer, "selectionOrder");
        if ("display".equals(val)) {
            setOrder(PageExtractor.DISPLAYORDER);
        } else if ("natural".equals(val)) {
            setOrder(PageExtractor.NATURALORDER);
        }
        if (getFeatureProperty(viewer, "selectPattern")!=null) {
            loadpattern = Pattern.compile(getFeatureProperty(viewer, "selectPattern"));
        }
        viewer.addDocumentPanelListener(this);

        String prop = getFeatureProperty(viewer, "selectTextToolOnTextSelection");
        selectTextToolOnTextSelection = prop != null && !"false".equals(prop);
    }

    public void action(ViewerEvent event) {
        if (!isSelected()) {
            setSelected(true);
        }
    }

    /**
     * Add a property change listener to this TextTool object.
     * @param listener the listener
     * @since 2.23
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    /**
     * Remove a property change listener from this TextTool object.
     * @param listener the listener
     * @since 2.23
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    //-----------------------------------------------------------------------------------
    // Range Managemenet
    //
    // These methods work on allranges, and must all be synchronized
    //-----------------------------------------------------------------------------------

    public RangeList getRangeList() {
        DocumentPanel docpanel = viewport.getDocumentPanel();
        int num = docpanel.getNumberOfPages();
        List<Range> combined = new ArrayList<Range>();
        synchronized(allranges) {
            for (int i=0;i<num;i++) {
                PageExtractor extractor = docpanel.getPageExtractor(docpanel.getPage(i));
                RangeList list = allranges.get(extractor);
                if (list != null && list.isValid()) {
                    combined.addAll(list);
                }
            }
        }
        return new RangeList(viewport.getDocumentPanel(), combined, order);
    }

    /**
     * Get the RangeList for the specified {@link PageExtractor}
     */
    private RangeList getRangeList(PageExtractor extractor) {
        synchronized (allranges) {
            RangeList list = allranges.get(extractor);
            if (list == null) {
                list = RangeList.EMPTY_RANGELIST;
            }
            return list;
        }
    }

    /**
     * Get the DragRange for this extractor (if it doesn't exist, create it).
     */
    private DragRange getDragRange(PageExtractor extractor) {
        synchronized (allranges) {
            RangeList list = allranges.get(extractor);
            DragRange range = null;
            if (list == null || list.size() != 1 || !(list.get(0) instanceof DragRange)) {
                range = new DragRange();
                allranges.put(extractor, new RangeList(viewport.getDocumentPanel(), range, order));
            } else {
                range = (DragRange)list.get(0);
            }
            return range;
        }
    }

    /**
     * Set an extractor to a RangeList of FixedRange objects
     */
    private void setFixedRanges(PageExtractor extractor, RangeList ranges) {
        synchronized (allranges) {
            if (ranges == null) {
                allranges.remove(extractor);
            } else {
                allranges.put(extractor, ranges);
            }
        }
    }

    /**
     * Clear any extractors from this PDF
     */
    private void clear(PDF pdf) {
        synchronized (allranges) {
            for (Iterator<PageExtractor> i = allranges.keySet().iterator();i.hasNext();) {
                PageExtractor extractor = i.next();
                if (extractor.getPage().getPDF() == pdf) {
                    i.remove();
                }
            }
        }
        repaintTextSelections(false);
    }

    public void propertyChange(PropertyChangeEvent event) {
        if (viewport != null && event.getSource() instanceof PDFPage) {
            PDFPage page = (PDFPage)event.getSource();
            DocumentPanel docpanel = viewport.getDocumentPanel();
            if (page.getPDF() == docpanel.getPDF()) {
                String name = event.getPropertyName();
                if (name.equals("content") || name.equals("orientation")) {
                    PageExtractor extractor = docpanel.getPageExtractor(page);
                    boolean removed = false;
                    synchronized(allranges) {
                        removed = allranges.remove(extractor) != null;
                    }
                    if (removed) {
                        repaintTextSelections(false);
                    }
                }
            }
        }
    }

    //-----------------------------------------------------------------------------------
    // Glue - integration of TextTool with Viewport
    //-----------------------------------------------------------------------------------

    protected void updateViewport(final DocumentViewport viewport, boolean selected) {
        // Add or remove the ActionHandlers, the InputHandlers, the listeners
        // and the TextSelection objects to the PagePanels
        if (selected) {
            this.viewport = viewport;
            copyable = viewport.getDocumentPanel().hasPermission("Extract");
            viewport.addPagePanelInteractionListener(this);
            viewport.addPagePanelListener(this);

            for (Iterator<PagePanel> i = viewport.getPagePanels().iterator();i.hasNext();) {
                PagePanel pagepanel = i.next();
                getOrCreateTextSelection(pagepanel);
                pagepanel.repaint();
            }

            ActionMap actionmap = viewport.getActionMap();
            InputMap inputmap = viewport.getInputMap();
            viewport.setTransferHandler(transferhandler);
            actionmap.put(selectAllAction.getValue(Action.NAME), selectAllAction);
            inputmap.put(KeyStroke.getKeyStroke('A', Util.getMenuShortcutKeyMask(viewport)), selectAllAction.getValue(Action.NAME));

            if (copyable) {
                inputmap.put(KeyStroke.getKeyStroke('C', Util.getMenuShortcutKeyMask(viewport)), TransferHandler.getCopyAction().getValue(Action.NAME));
                actionmap.put(TransferHandler.getCopyAction().getValue(Action.NAME), TransferHandler.getCopyAction());
            }

            Object[] keys = actionmap.keys();
            for (int i=0;i<keys.length;i++) {
                if (keys[i] instanceof TextSelectionAction) {
                    actionmap.remove(keys[i]);
                }
            }

            ViewerFeature[] features = getViewer().getFeatures();
            for (int i=0;i<features.length;i++) {
                if (features[i] instanceof TextSelectionAction) {
                    final TextSelectionAction handler = (TextSelectionAction)features[i];
                    Action action = new AbstractAction(handler.getDescription()) {
                        public void actionPerformed(ActionEvent e) {
                            if (!handler.isEnabled()) {
                                return;
                            }
                            DocumentPanel docpanel = viewport.getDocumentPanel();
                            List<Range> combined = new ArrayList<Range>();
                            synchronized (allranges) {
                                Collection<RangeList> r = allranges.values();
                                for (Iterator<RangeList> i = r.iterator(); i.hasNext(); ) {
                                    RangeList rangelist = i.next();
                                    if (rangelist.isValid()) {
                                        combined.addAll(rangelist);
                                    }
                                }
                            }
                            handler.selectAction(docpanel, new RangeList(docpanel, combined, order));
                        }
                        public boolean isEnabled() {
                            return handler.isEnabled();
                        }
                    };
                    actionmap.put(handler, action);
                }
            }
        } else {
            viewport.removePagePanelInteractionListener(this);
            viewport.removePagePanelListener(this);

            ActionMap actionmap = viewport.getActionMap();
            Object[] keys = actionmap.keys();
            for (int i=0;i<keys.length;i++) {
                if (keys[i] instanceof TextSelectionAction) {
                    actionmap.remove(keys[i]);
                }
            }
            actionmap.put(selectAllAction.getValue(Action.NAME), null);
            actionmap.put(TransferHandler.getCopyAction().getValue(Action.NAME), null);
            viewport.setTransferHandler(null);
            this.viewport = null;
        }
        viewport.requestFocusInWindow();
    }

    /**
     * Get the TextSelection object for the specified PagePanel
     */
    private TextSelection getTextSelection(PagePanel panel) {
        for (int i=0;i<panel.getComponentCount();i++) {
            Component c = panel.getComponent(i);
            if (c instanceof TextToolTextSelection && ((TextToolTextSelection)c).getTextTool()==this) {
                return (TextSelection)c;
            }
        }
        return null;
    }

    /**
     * Get or create the TextSelection object for the specified PagePanel
     */
    private TextSelection getOrCreateTextSelection(PagePanel panel) {
        TextSelection selection = getTextSelection(panel);
        if (selection == null) {
            selection = new TextToolTextSelection(panel, highlighttype, highlightcolor, highlightstroke, highlightmargin);
            panel.add(selection);
            panel.revalidate();
            panel.repaint();
        }
        return selection;
    }

    /**
     * Repaint any TextSelection objects in this Viewport
     * @param busy whether to set the cursor to busy or not
     */
    private void repaintTextSelections(boolean busy) {
        if (viewport != null) {
            for (Iterator<PagePanel> j = viewport.getPagePanels().iterator();j.hasNext();) {
                PagePanel pagepanel = j.next();
                TextSelection selection = getOrCreateTextSelection(pagepanel);
                selection.repaint();
                selection.setCursor(Cursor.getPredefinedCursor(busy ? Cursor.WAIT_CURSOR : Cursor.TEXT_CURSOR));
            }
        }
    }

    //-----------------------------------------------------------------------------------
    // Event Handlers
    //-----------------------------------------------------------------------------------

    public void documentUpdated(DocumentPanelEvent event) {
        String type = event.getType();
        final DocumentPanel docpanel = event.getDocumentPanel();
        if (type.equals("viewportChanged") || type.equals("activated")) {
            if (getGroupSelection(getGroupName()) == null) {
                PropertyManager manager = getViewer()==null ? PDF.getPropertyManager() : getViewer().getPropertyManager();
                String defaultmode = manager == null ? null : manager.getProperty("Default"+getGroupName());
                if (getName().equals(defaultmode)) {
                    setSelected(true);
                }
            }
            if (viewport != null && docpanel.getPDF() != null) {
                updateViewport(docpanel.getViewport(), isSelected());
            }
        } else if (loadpattern!=null && type=="loaded") {
            Thread thread = new Thread() {
                public void run() {
                    select(docpanel, loadpattern);
                    setSelected(true);
                }
            };
            thread.setDaemon(true);
            thread.start();
        }
        if (type == "pageChanged") {
            selectText(docpanel.getViewport());
        }
    }

    public void pageUpdated(PagePanelEvent event) {
        if (isSelected()) {
            PagePanel panel = event.getPagePanel();
            if (event.getType().equals("redrawing")) {     // Set to extract if we're changing page
                panel.setExtractText(true);
            } else if (event.getType().equals("redrawn")) {
                getOrCreateTextSelection(panel).repaint();
            } else if (event.getType().equals("visible")) {
                panel.getPage().addPropertyChangeListener(this);
            } else if (event.getType().equals("hidden")) {
                panel.getPage().removePropertyChangeListener(this);
            }
        }
    }

    //-----------------------------------------------------------------------------------
    // Public actions
    //-----------------------------------------------------------------------------------

    /**
     * Copy the selected text (if any) to the System clipboard
     */
    public void copy() {
        Action action = TransferHandler.getCopyAction();
        if (viewport!=null) {
            action.actionPerformed(new ActionEvent(viewport, ActionEvent.ACTION_PERFORMED, (String)action.getValue(Action.NAME)));
        }
    }

    /**
     * <p>
     * Select all the text in the currently displayed viewport.
     * This will cause all the text to be extracted if it hasn't
     * already, which may be a slow operation.
     * </p><p>
     * Any subsequent selections, by the mouse or programatically, will replace this selection.
     * </p>
     * @see #select(Pattern)
     * @see BackgroundTextExtractor
     */
    public void selectAll() {
        DocumentPanel docpanel = viewport.getDocumentPanel();
        int num = docpanel.getNumberOfPages();
        for (int i=0;i<num;i++) {
            PageExtractor extractor = docpanel.getPageExtractor(docpanel.getPage(i));
            PageExtractor.Text first = null, last = null;
            Collection<PageExtractor.Text> alltext = extractor.getText(order);
            for (Iterator<PageExtractor.Text> j = alltext.iterator();j.hasNext();) {
                last = j.next();
                if (first == null) {
                    first = last;
                }
            }
            if (last == null) {
                setFixedRanges(extractor, null);
            } else {
                setFixedRanges(extractor, new RangeList(viewport.getDocumentPanel(), Range.createRange(first, 0, last, last.getTextLength()-1), order));
            }
            repaintTextSelections(i != num - 1);
        }
    }

    /**
     * Deselect all the text on the viewport
     * @since 2.10.4
     */
    public void clear() {
        if (viewport != null && viewport.getDocumentPanel() != null) {
            clear(viewport.getDocumentPanel().getPDF());
        }
    }

    /**
     * <p>
     * Select the specified text. If the text is on a different page
     * to the current one, the page is changed first, and either way
     * the viewport may be repositioned to display the specified text.
     * </p><p>
     * Any subsequent selections, by the mouse or programatically, will replace this selection.
     * </p>
     * @param text the Text item to display, or <code>null</code> to select no text
     * @throws IllegalArgumentException if the text item is from a different PDF to that currently displayed
     */
    public void select(final PageExtractor.Text text) {
        if (!isSelected() && selectTextToolOnTextSelection) {
            setSelected(true);
        }
        if (viewport == null) {
            DocumentPanel docpanel = getViewer().getActiveDocumentPanel();
            viewport = docpanel.getViewport();
        }
        clear();
        PDFPage page = text.getPage();
        PageExtractor extractor = text.getPageExtractor();
        setFixedRanges(extractor, new RangeList(viewport.getDocumentPanel(), Range.createRange(text), order));
        textSelection = text;
        float[] r = text.getCorners();
        float x = r[2];
        float y = r[3];
        viewport.ensureVisible(page, x, y);
        selectText(viewport);
    }

    private void selectText(DocumentViewport viewport) {
        if (textSelection != null) {
            PDFPage page = textSelection.getPage();
            for (Iterator<PagePanel> i = viewport.getPagePanels().iterator();i.hasNext();) {
                PagePanel pagepanel = i.next();
                if (pagepanel.getPage() == page) {
                    getOrCreateTextSelection(pagepanel);
                    pagepanel.repaint();
                    textSelection = null;
                }
            }
        }
    }

    /**
     * <p>
     * Select the specified text objects. The viewport will not be scrolled to
     * match the selections, so the text may be from any loaded PDF objects.
     * </p><p>
     * Any subsequent selections, by the mouse or programatically, will replace this selection.
     * </p>
     * @param texts a Collection of {@link org.faceless.pdf2.PageExtractor.Text} objects.
     * @see TextHighlighter
     * @see TextSelectionAction
     * @since 2.11.7
     */
    public void select(Collection<PageExtractor.Text> texts) {
        Map<PDF,Map<PageExtractor,List<Range>>> all = new HashMap<PDF,Map<PageExtractor,List<Range>>>();
        for (Iterator<PageExtractor.Text> i = texts.iterator();i.hasNext();) {
            PageExtractor.Text text = i.next();
            PageExtractor extractor = text.getPageExtractor();
            PDF pdf = extractor.getPage().getPDF();
            Map<PageExtractor,List<Range>> extractors = all.get(pdf);
            if (extractors == null) {
                extractors = new HashMap<PageExtractor,List<Range>>();
                all.put(pdf, extractors);
            }
            List<Range> list = extractors.get(extractor);
            if (list == null) {
                list = new ArrayList<Range>();
                extractors.put(extractor, list);
            }
            list.add(Range.createRange(text));
        }
        for (Iterator<Map.Entry<PDF,Map<PageExtractor,List<Range>>>> i = all.entrySet().iterator();i.hasNext();) {
            Map.Entry<PDF,Map<PageExtractor,List<Range>>> e = i.next();
            PDF pdf = e.getKey();
            Map<PageExtractor,List<Range>> map = e.getValue();
            clear(pdf);
            for (Iterator<Map.Entry<PageExtractor,List<Range>>> j = map.entrySet().iterator();j.hasNext();) {
                Map.Entry<PageExtractor,List<Range>> e2 = j.next();
                PageExtractor extractor = e2.getKey();
                setFixedRanges(extractor, new RangeList(viewport.getDocumentPanel(), e2.getValue(), order));
            }
        }
        repaintTextSelections(false);
    }

    /**
     * <p>
     * Select any text matching the specified Pattern. This method has to
     * run a pattern match against the entire PDF in the currently displayed
     * viewport, so if the text has not yet been extracted this method could
     * take some time to complete.
     * </p><p>
     * Any subsequent selections, by the mouse or programatically, will replace this selection.
     * </p>
     * @see BackgroundTextExtractor
     * @see PageExtractor#getMatchingText
     * @see TextHighlighter
     * @see TextSelectionAction
     */
    public void select(Pattern pattern) {
        select(viewport.getDocumentPanel(), pattern);
    }

    private void select(DocumentPanel docpanel, Pattern pattern) {
        List<PageExtractor.Text> out = new ArrayList<PageExtractor.Text>();
        repaintTextSelections(SwingUtilities.isEventDispatchThread());
        int num = docpanel.getNumberOfPages();
        for (int i=0;i<num;i++) {
            PageExtractor extractor = docpanel.getPageExtractor(docpanel.getPage(i));
            out.addAll(extractor.getMatchingText(pattern));
        }
        select(out);
    }

    /**
     * Handle mousePressed, mouseDragged and mouseMoved events on this page, which
     * are used to select text. Text can be selected by clicking and dragging over it,
     * or by double clicking (to select the current word) or triple clicking (to select
     * the current line)
     */
    public void pageAction(PagePanelInteractionEvent event) {
        if (!isSelected()) {
            return;
        }

        if (event.getMouseEvent().isPopupTrigger()) {
            MouseEvent e = event.getMouseEvent();
            PageExtractor extractor = event.getPagePanel().getPageExtractor();
            RangeList rangelist = getRangeList(extractor);
            if (rangelist.isValid()) {
                JPopupMenu popup = new JPopupMenu();
                boolean found = false;
                ActionMap actionmap = viewport.getActionMap();
                Object[] keys = actionmap.keys();
                for (int i=0;i<keys.length;i++) {
                    if (keys[i] instanceof TextSelectionAction) {
                        TextSelectionAction tsa = (TextSelectionAction) keys[i];
                        if (tsa.isEnabled()) {
                            found = true;
                            popup.add(actionmap.get(tsa));
                        }
                    }
                }
                if (found) {
                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
            return;
        }

        boolean changed = false;
        DocumentPanel docpanel = event.getPagePanel().getDocumentPanel();
        if ((draggable && event.getMouseEvent().getButton() == 1) || startPoint != null) {
            if (event.getType() == "mousePressed") {
                clear();
                startPageNumber = docpanel.getPageNumber(event.getPagePanel().getPage());
                startPoint = event.getPoint();
                getOrCreateTextSelection(event.getPagePanel()).repaint();
            } else if (event.getType() == "mouseDragged") {
                PagePanelInteractionEvent newevent = event.getEventOnNewPanel();
                if (newevent != null) {         // Check if we've dragged from one page to another
                    event = newevent;
                }
                int endPageNumber = docpanel.getPageNumber(event.getPagePanel().getPage());
                int firstPageNumber = Math.min(startPageNumber, endPageNumber);
                int lastPageNumber = Math.max(startPageNumber, endPageNumber);

                // Algorithm works like so:
                // 1. First time we press or drag onto text, that is the start point. Then,
                // 2. If we subsequently drag onto another piece of text we end the text there
                // 3. If we subsequently drag off the text, we work out our position based on
                //    the previous endpoint to determine if we dragged backwards over the start
                //    or forwards over the end, and we set the limit to the start/end respectively.
                // 4. When dragging between pages, if start/end weren't on this page then they
                //    are pegged at start/end of the page respectively.
                //
                // Many improvements that can be made to this, but tests must include:
                // * Dragging up onto text: should select from end to mousecursor
                // * Dragging down onto text: should select from start to mousecursor
                // * Dragging down off text: should select from selection to mousecursor
                // * Dragging up off text: should select from mousecursor to selection
                // Here "start" and "end" can mean start of the selected text or some subseqeuent
                // one closer to the mousecursor

                for (int i=firstPageNumber;i<=lastPageNumber;i++) {
                    PageExtractor extractor = docpanel.getPageExtractor(docpanel.getPage(i));
                    DragRange range = getDragRange(extractor);
                    PageExtractor.Text text;

                    if (i == firstPageNumber) {
                        if (range.getStart() == null) {
                            Point2D p = startPoint;
                            text = getText(extractor, p, highlightmargin);
                            if (text != null) {         // Dragged onto text for first time.
                                changed |= range.setStart(text, getTextPosition(text, getPointInTextSpace(text, p)));
                            }
                        }
                    } else {
                        Iterator<? extends PageExtractor.Text> j = extractor.getText(order).iterator();
                        text = j.hasNext() ? j.next() : null;           // First on page
                        changed |= range.setStart(text, 0);
                    }

                    if (i == lastPageNumber) {
                        Point2D p = event.getPoint();
                        text = getText(extractor, p, highlightmargin);
                        if (text != null) {                     // Dragged over text - set start if not set, otherwise set end.
                            if (range.getStart() != null) {
                                changed |= range.setEnd(text, getTextPosition(text, getPointInTextSpace(text, p)));
                            } else {
                                int pos = getTextPosition(text, getPointInTextSpace(text, startPoint));  // out of range
                                changed |= range.setStart(text, pos < 0 ? 0 : text.getTextLength()-1);
                            }
                        } else if (range.getEnd() != null) {    // Dragged off edge of text, but to where?
                            text = range.getEnd();
                            int pos = getTextPosition(text, getPointInTextSpace(text, p));    // will be -1 or len
                            boolean dragoffstart = pos < 0;
                            boolean reversed = range.isReversed();
                            if (dragoffstart && reversed) {             // Dragged backwards off start - set to start
                                pos = 0;
                            } else if (!dragoffstart && !reversed) {    // Dragged forwards off end - set to end
                                pos = text.getTextLength() - 1;
                            } else if (dragoffstart && !reversed) {     // Dragged forwards off start - set to end of previous
                                text = getNeighbourText(text, -1, order);
                                if (text != null) {
                                    pos = text.getTextLength() - 1;
                                } else {
                                    text = range.getEnd();
                                    pos = 0;
                                }
                            } else {                                    // Dragged backwards off end - set to start of next
                                text = getNeighbourText(text, 1, order);
                                if (text != null) {
                                    pos = 0;
                                } else {
                                    text = range.getEnd();
                                    pos = text.getTextLength() - 1;
                                }
                            }
                            changed |= range.setEnd(text, pos);
                        }
                    } else {
                        text = null;
                        for (Iterator<? extends PageExtractor.Text> j = extractor.getText(order).iterator();j.hasNext();) {
                            text = j.next();        // Set to last on page
                        }
                        changed |= range.setEnd(text, text == null ? 0 : text.getTextLength() - 1);
                    }

                    PageExtractor.Text first = range.getFirst();
                    PageExtractor.Text last = range.getLast();
                    if (first !=null && last != null) {
                        PDFPage firstPage = first.getPage();
                        PDFPage lastPage = last.getPage();
                        boolean highlight = false;
                        for (Iterator<PagePanel> j = viewport.getPagePanels().iterator(); j.hasNext(); ) {
                            PagePanel panel = j.next();
                            PDFPage panelPage = panel.getPage();
                            if (panelPage == firstPage) {
                                highlight = true;
                            }
                            getOrCreateTextSelection(panel).repaint();
                            if (panelPage == lastPage) {
                                highlight = false;
                            }
                        }
                    }
                }

            } else if (event.getType()=="mouseClicked") {
                int clickcount = event.getMouseEvent().getClickCount();
                if (clickcount == 2 || clickcount == 3) {
                    PageExtractor extractor = event.getPagePanel().getPageExtractor();
                    Point2D p = event.getPoint();
                    PageExtractor.Text text = getText(extractor, p, highlightmargin);
                    if (text!=null) {
                        DragRange range = getDragRange(extractor);
                        String val = text.getText();
                        if (clickcount==2) {            // Double clicked on some text - select word
                            int position = getTextPosition(text, getPointInTextSpace(text, p));
                            int lastspace = -1;
                            for (int i=0;i<=val.length();i++) {
                                if (i==val.length() || val.charAt(i)==' ') {
                                    if (i < position) {
                                        lastspace = i;
                                    } else {
                                        range.setStart(text, lastspace+1);
                                        range.setEnd(text, i-1);
                                        changed = true;
                                        break;
                                    }
                                }
                            }
                        } else if (clickcount==3) {     // Triple clicked - select all this text
                            range.setStart(text, 0);
                            range.setEnd(text, val.length()-1);
                            changed = true;
                        }
                    }
                }
                getOrCreateTextSelection(event.getPagePanel()).repaint();
            } else if (event.getType() == "mouseReleased") {
                startPoint = null;
                startPageNumber = -1;
            }
        }
        if (changed) {
            pcs.firePropertyChange("selection", false, true);
        }
    }

    //-------------------------------------------------------------------------------------
    // Static methods to relate mouse clicks to text objects
    //-------------------------------------------------------------------------------------

    /**
     * Return the Shape representing the specified Text object.
     * @param text the Text object
     * @param margin the margin to place around the edge of the text
     * @param needspath true if the returned value must be a GeneralPath
     */
    static Shape getShape(PageExtractor.Text text, float margin, boolean path) {
        return getShape(text, 0, text.getTextLength()-1, margin, path);
    }

    /**
     * Return the Shape representing the specified Text object from characters
     * "start" to "end" inclusive (eg getShape(text, 0, 0) would return the shape
     * for the first character of text).
     * @param text the Text object
     * @param start the first character to include in the shape
     * @param end the last character to include in the shape
     * @param margin the margin to place around the edge of the text
     * @param needspath true if the returned value must be a GeneralPath
     */
    static Shape getShape(PageExtractor.Text text, int start, int end, float margin, boolean needspath) {
        if (start < 0 || end < start) {
            return null;
        }
        if (start > 0 || end != text.getTextLength()) {
            text = text.getSubText(start, (end - start) + 1);
        }
        float[] c = text.getCorners();
        boolean horizontal = text.isHorizontal();
        if (margin!=0) {
            // Expand margin in correct direction for rotated shapes
            double advance = Math.sqrt((c[6]-c[0])*(c[6]-c[0])+(c[7]-c[1])*(c[7]-c[1]));
            double rise = Math.sqrt((c[2]-c[0])*(c[2]-c[0])+(c[3]-c[1])*(c[3]-c[1]));
            if (advance!=0 && rise!=0) {
                double xm1 = (c[6]-c[0]) * margin / advance;
                double xm2 = (c[2]-c[0]) * margin / rise;
                double ym1 = (c[7]-c[1]) * margin / advance;
                double ym2 = (c[3]-c[1]) * margin / rise;
                c[0] -= xm1+xm2; c[1] -= ym1+ym2;
                c[2] += xm2-xm1; c[3] += ym2-ym1;
                c[4] += xm1+xm2; c[5] += ym1+ym2;
                c[6] += xm1-xm2; c[7] += ym1-ym2;
            }
        }

        if (!needspath) {
            if (horizontal && c[1] == c[7] && c[6] > c[0]) {
                // Horizontal, L2R
                // BL, TL, TR, BR
                return new Rectangle2D.Float(c[0], c[1], c[6] - c[0], c[3] - c[1]);
            } else if (!horizontal && c[0] == c[6] && c[7] < c[1]) {
                // Vertical, T2B
                // TL, TR, BR, BL
                return new Rectangle2D.Float(c[6], c[7], c[2] - c[6], c[3] - c[7]);
            }
        }
        GeneralPath path = new GeneralPath();
        path.moveTo(c[0], c[1]);
        path.lineTo(c[2], c[3]);
        path.lineTo(c[4], c[5]);
        path.lineTo(c[6], c[7]);
        path.closePath();
        return path;
    }

    /**
     * Return true if the first event was before the second, where "before" means
     * before in the document, reading forward and from the top-left of each page.
     */
    private static boolean isBefore(PagePanelInteractionEvent e1, PagePanelInteractionEvent e2) {
        if (e1.getPagePanel() == e2.getPagePanel()) {
            Point p1 = e1.getMouseEvent().getPoint();
            Point p2 = e2.getMouseEvent().getPoint();
            return (p1.getY() < p2.getY()) || (p1.getY() == p2.getY() && p1.getX() < p2.getX());
        } else {
            DocumentPanel docpanel = e1.getPagePanel().getDocumentPanel();
            return docpanel.getPageNumber(e1.getPagePanel().getPage()) < docpanel.getPageNumber(e2.getPagePanel().getPage());
        }
    }

    /**
     * Return the Text object containing "point". If the point is not in any
     * Text object, return null.
     * @param range the TextSelection who's extractor to search in
     * @param point the Point
     */
    private static PageExtractor.Text getText(PageExtractor extractor, Point2D point, float margin) {
        // Order not important
        for (Iterator<PageExtractor.Text> i = extractor.getTextUnordered().iterator();i.hasNext();) {
            PageExtractor.Text text = i.next();
            Shape shape = getShape(text, margin, false);
            if (shape.contains(point)) {
                return text;
            }
        }
        return null;
    }

    /**
     * Given a Text object, return the previous (if distance=-1) or
     * next (if distance=1) text item. Really this should be more
     * efficient, but it's not called often.
     */
    private static PageExtractor.Text getNeighbourText(final PageExtractor.Text search, int distance, Comparator<PageExtractor.Text> order) {
        PageExtractor.Text lasttext = null;
        PageExtractor extractor = search.getPageExtractor();
        for (Iterator<PageExtractor.Text> i = extractor.getText(order).iterator();i.hasNext();) {
            PageExtractor.Text text = i.next();
            if (text == search) {
                if (distance == -1) {
                    return lasttext;
                } else if (distance == 1) {
                    return i.hasNext() ? i.next() : null;
                }
            }
            lasttext = text;
        }
        return null;            // Shouldn't happen
    }

    /**
     * Given a Text object and a Point "p", return where in "text space" this
     * point is. Text space is a system where x=0 is the leftmost edge, x=1
     * is the rightmost edge, y=0 is the top and y=1 is the bottom edge of the text.
     * Handles rotated text.
     */
    private static Point2D getPointInTextSpace(PageExtractor.Text text, Point2D p) {
        float[] c = text.getCorners();
        double a = c[3]-c[1];
        double b = c[2]-c[0];
        double e = c[6]-c[0];
        double f = c[7]-c[1];
        double d = p.getX()-c[0];
        double g = p.getY()-c[1];
        double i = Math.sqrt(d*d+g*g);
        double z = Math.atan2(d, g) - Math.atan2(e, f);
        double h = Math.sin(z) * i / Math.sqrt(a*a+b*b);
        double w = Math.sin(Math.PI/2-z) * i / Math.sqrt(e*e+f*f);
        return new Point2D.Double(w, h+1);
    }

    /**
     * Given a point and a Text object, return which character in the text
     * was clicked on. If the click was before (to the left of or above) this
     * method returns -1, and if it was after (to the right of or below) this
     * method returns text.length(). Otherwise returns a value from
     * 0 to text.length()-1
     */
    private static int getTextPosition(PageExtractor.Text text, Point2D point) {
        if (point.getY() < 0) {
            return -1;
        } else if (point.getY() > 1) {
            return text.getTextLength();
        } else if (point.getX() <= 0) {
            return -1;
        } else if (point.getX() >= 1) {
            return text.getTextLength();
        } else {
            String val = text.getText();
            for (int i=1;i<val.length();i++) {
                if (point.getX()<text.getOffset(i)) return i-1;
            }
            return val.length()-1;
        }
    }

    //-------------------------------------------------------------------------------------------
    // Subclasses
    //-------------------------------------------------------------------------------------------

    /**
     * This class is a transparent panel which is laid overtop of the PagePanel. It will
     * highlight any text in RangeList that matches its page.
     */
    private class TextToolTextSelection extends TextSelection {

        private TextToolTextSelection(PagePanel panel, int type, Paint color, Stroke stroke, float margin) {
            super(panel, type, color, stroke, margin);
            setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        }

        TextTool getTextTool() {
            return TextTool.this;
        }

        protected RangeList getRangeList() {
            return TextTool.this.getRangeList(pagepanel.getPageExtractor());
        }

        public boolean isGlassPane() {
            return viewport == null;
        }

    }

    private class DragRange extends Range {
        private WeakReference<PageExtractor.Text> start, end;
        private int startposition, endposition;
        private boolean reversedrag;

        public boolean isValid() {
            return getStart() != null && getEnd() != null;
        }

        boolean isReversed() {
            return reversedrag;
        }

        public PageExtractor.Text getFirst() {
            return reversedrag ? getEnd() : getStart();
        }

        public PageExtractor.Text getLast() {
            return reversedrag ? getStart() : getEnd();
        }

        public int getFirstPosition() {
            return reversedrag ? endposition : startposition;
        }

        public int getLastPosition() {
            return reversedrag ? startposition : endposition;
        }

        PageExtractor.Text getStart() {
            return start == null ? null : start.get();
        }

        PageExtractor.Text getEnd() {
            return end == null ? null : end.get();
        }

        public String toString() {
            return "("+getStart()+":"+startposition+(reversedrag ? " <- " : " -> ")+getEnd()+":"+endposition+")";
        }

        boolean setStart(PageExtractor.Text text, int pos) {
            boolean changed = pos != startposition || (start == null ? text != null : start.get() != text);
            start = text == null ? null : new WeakReference<PageExtractor.Text>(text);
            startposition = pos;
            end = null;
            reversedrag = false;
            return changed;
        }

        boolean setEnd(PageExtractor.Text text, int pos) {
            boolean changed = pos != endposition || (end == null ? text != null : end.get() != text);
            endposition = pos;
            if (text == null) {
                reversedrag = false;
                end = null;
            } else {
                PageExtractor.Text starttext = getStart();
                end = new WeakReference<PageExtractor.Text>(text);
                if (starttext == text) {
                    reversedrag = endposition < startposition;
                } else if (starttext != null) {
                    reversedrag = order.compare(starttext, text) > 0;
                }
            }
            return changed;
        }

        public int hashCode() {
            PageExtractor.Text ff = start.get();
            PageExtractor.Text lf = end.get();
            return (startposition<<20) ^ (endposition<<10) ^ (ff==null?0:ff.hashCode()) ^ (lf==null?0:lf.hashCode()) ^ (reversedrag?1:0);
        }

        public boolean equals(Object o) {
            if (o instanceof DragRange) {
                DragRange f = (DragRange)o;
                PageExtractor.Text ff = start.get();
                PageExtractor.Text lf = end.get();

                return (ff == null ? f.start.get() == null : ff.equals(f.start.get())) && 
                       (lf == null ? f.end.get() == null : lf.equals(f.end.get())) && 
                       reversedrag == f.reversedrag &&
                       startposition == f.startposition &&
                       endposition == f.endposition;
            }
            return false;
        }
    }

    //-------------------------------------------------------------------------------------------
    // Design notes
    //
    // Prior to 2.11.7 this class was designed around the JPanel also managing the selected text.
    // This wouldn't work properly as when the panel was scrolled off it was gced, and the text
    // was deselected.
    //
    // The current release decouples these - allranges contains a weak mapping from PageExtractor->
    // RangeList and access to this is synchronized. TextSelection objects can flit in and out
    // of existance, and will retrieve their RangeList from this object. PageExtractor has been
    // fixed to cope with multiple threads trying to populate it, and has been streamlined so
    // it is very light on creation.
    //
    // TextSelection is now a top level class and can be referenced from elsewhere, which fixes
    // the hideous crosslinking between TextTool and TextHighlighter.
    //-------------------------------------------------------------------------------------------
}
