// $Id: AnnotationLineFactory.java 39802 2021-04-21 15:32:05Z mike $

package org.faceless.pdf2.viewer2.feature;

import org.faceless.pdf2.viewer2.*;
import org.faceless.pdf2.*;
import org.faceless.pdf2.viewer2.util.ColorChoicePanel;
import javax.swing.*;
import java.beans.*;
import java.awt.geom.*;
import javax.swing.event.*;
import javax.swing.undo.*;
import java.awt.event.*;
import java.awt.image.*;
import java.awt.*;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * Create annotations that handle Line {@link AnnotationShape} objects.
 *
 * <span class="featurename">The name of this feature is <span class="featureactualname">AnnotationLine</span></span>
 * <p><i>This code is copyright the Big Faceless Organization. You're welcome to use, modify and distribute it in any form in your own projects, provided those projects continue to make use of the Big Faceless PDF library.</i></p>
 * @since 2.11.7
 */
public class AnnotationLineFactory extends AnnotationShapeFactory {

    public AnnotationLineFactory() {
        super("AnnotationLine");
    }

    public String getAnnotationType() {
        return UIManager.getString("PDFViewer.annot.Line");
    }

    protected boolean matchesShape(AnnotationShape annot) {
        Shape shape = annot.getShape();
        return shape instanceof Line2D || shape instanceof GeneralPath;
    }

    public PDFAnnotation createNewAnnotation(float x1, float y1, float x2, float y2) {
        Line2D line = new Line2D.Float(x1, y1, x2, y2);
        AnnotationShape annot = new AnnotationShape(line);
        if (getViewer() != null) {
            annot.setAuthor(getViewer().getCurrentUser());
        }
        PDFStyle style = new PDFStyle();

        Color linecolor = Color.black;
        Color fillcolor = null;
        float weight = 1;
        float[] dash = null;
        String arrow1 = null;
        String arrow2 = null;
        Preferences preferences = getPreferences();
        if (preferences!=null) {
            linecolor = ColorChoicePanel.loadColor(preferences, "feature.AnnotationLineFactory.lineColor", linecolor);
            fillcolor = ColorChoicePanel.loadColor(preferences, "feature.AnnotationLineFactory.arrowColor", fillcolor);
            weight = preferences.getFloat("feature.AnnotationLineFactory.lineWeight", 1);
            dash = BorderStyleEditor.getDashArray(preferences.get("feature.AnnotationLineFactory.dashPattern", "solid"));
            arrow1 = preferences.get("feature.AnnotationLineFactory.arrow1", "None");
            arrow2 = preferences.get("feature.AnnotationLineFactory.arrow2", "None");
        }
        style.setLineColor(linecolor);
        style.setFillColor(fillcolor);
        style.setLineWeighting(weight);
        style.setLineDash(dash, 0);
        annot.setStyle(style);
        annot.setFirstLineEnding(arrow1);
        annot.setLastLineEnding(arrow2);
        return annot;
    }

    protected JComponent createEditor(final AnnotationShape annot) {
        PDFStyle style = annot.getStyle();
        JPanel editorPane = new JPanel();
        editorPane.setLayout(new GridBagLayout());

        addStockDetailsToEditComponent(annot, editorPane);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridwidth = GridBagConstraints.REMAINDER;

        final JTextArea textarea = new JTextArea(5, 20);
        Util.setAutoFocusComponent(textarea);
        textarea.addFocusListener(new FocusListener() {
            public void focusGained(FocusEvent e) { }
            public void focusLost(FocusEvent e) {
                annot.setContents(textarea.getText());
            }
        });

        textarea.setEditable(true);
        textarea.setLineWrap(true);
        textarea.setText(annot.getContents());
        editorPane.add(new JScrollPane(textarea), gbc);

        gbc.weighty = 0;
        gbc.fill = gbc.NONE;
        JPanel appearancePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        editorPane.add(appearancePanel, gbc);

        LineEndingSelector first = new LineEndingSelector(annot.getFirstLineEnding(), false) {
            public void itemStateChanged(ItemEvent e) {
                String end = getLineEnding();
                annot.setFirstLineEnding(end);
                super.itemStateChanged(e);
            }
        };
        first.setPreferences(getPreferences(), "feature.AnnotationLineFactory.arrow1");
        appearancePanel.add(first);

        BorderStyleEditor borderStyle = new BorderStyleEditor(annot, false);
        borderStyle.setPreferences(getPreferences(), "feature.AnnotationLineFactory.lineWeight", "feature.AnnotationLineFactory.dashPattern");
        appearancePanel.add(borderStyle);

        LineEndingSelector last = new LineEndingSelector(annot.getLastLineEnding(), true) {
            public void itemStateChanged(ItemEvent e) {
                String end = getLineEnding();
                annot.setLastLineEnding(end);
                super.itemStateChanged(e);
            }
        };
        last.setPreferences(getPreferences(), "feature.AnnotationLineFactory.arrow2");
        appearancePanel.add(last);

        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        Color color = getLineColor(style);
        JComponent colorEditor = ColorChoicePanel.createColorChoiceButton(color, new ColorChoicePanel.ColorChoiceListener() {
            public void colorChosen(Color color) {
                PDFStyle style = annot.getStyle();
                style.setLineColor(color);
                annot.setStyle(style);
                Preferences preferences = getPreferences();
                if (preferences!=null) {
                    ColorChoicePanel.saveColor(preferences, "feature.AnnotationLineFactory.lineColor", color);
                }
            }
        }, ColorChoicePanel.TYPE_RECTANGLE, true, false, UIManager.getString("PDFViewer.annot.setColor"));
        appearancePanel.add(colorEditor);

        JPanel space = new JPanel();
        space.setPreferredSize(new Dimension(2, 20));
        appearancePanel.add(space);

        Color fillColor = getFillColor(style);
        JComponent fillColorEditor = ColorChoicePanel.createColorChoiceButton(fillColor, new ColorChoicePanel.ColorChoiceListener() {
            public void colorChosen(Color color) {
                PDFStyle style = annot.getStyle();
                style.setFillColor(color);
                annot.setStyle(style);
                Preferences preferences = getPreferences();
                if (preferences!=null) {
                    ColorChoicePanel.saveColor(preferences, "feature.AnnotationLineFactory.arrowColor", color);
                }
            }
        }, ColorChoicePanel.TYPE_ARROW, true, true, UIManager.getString("PDFViewer.annot.setColor"));
        appearancePanel.add(fillColorEditor);

        return editorPane;
    }

    public JComponent createComponent(final PagePanel pagepanel, PDFAnnotation annot) {
        final AnnotationShape shapeannot = (AnnotationShape)annot;

        ArrayList<Point2D> l = new ArrayList<Point2D>(4);
        float[] coords = new float[6];
        boolean tclosed = false;
        final BitSet ismove = new BitSet();
        for (PathIterator i = new FlatteningPathIterator(shapeannot.getShape().getPathIterator(null), 1);!i.isDone();i.next()) {
            int st;
            if ((st=i.currentSegment(coords)) == i.SEG_CLOSE) {
                tclosed = true;
            } else {
                if (st == i.SEG_MOVETO) {
                    ismove.set(l.size());
                }
                l.add(new Point2D.Float(coords[0], coords[1]));
            }
        }
        final Point2D[] points = l.toArray(new Point2D[l.size()]);
        final boolean closed = tclosed;

        return new LineComponent(pagepanel, shapeannot, points, ismove, closed);
    }

    private class LineComponent extends JPanel implements MouseListener, MouseMotionListener, PropertyChangeListener, DocumentPanelListener {

        private static final int MARGIN = 5;
        private final AnnotationShape annot;
        private final DocumentPanel docpanel;
        private final PagePanel pagepanel;
        private final PDFPage page;
        private final Point2D[] points;
        private final BitSet ismove;
        private final boolean closepath;

        private boolean readonly;
        private Point dragPointScreen;
        private Shape targetArea;
        private int selectedVertex = -1, hoverVertex = -1;

        LineComponent(PagePanel pagepanel, AnnotationShape annot, Point2D[] points, BitSet ismove, boolean close) {
            this.pagepanel = pagepanel;
            this.points = points;
            this.ismove = ismove;
            this.annot = annot;
            this.closepath = close;
            this.page = pagepanel.getPage();
            this.docpanel = pagepanel.getDocumentPanel();
            setBorder(AnnotationComponentFactory.FOCUSBORDER);
            setOpaque(false);
        }

        public void documentUpdated(DocumentPanelEvent event) {
            updateEnabled();
        }

        public void propertyChange(PropertyChangeEvent event) {
            updateEnabled();
        }

        public void addNotify() {
            super.addNotify();
            docpanel.addDocumentPanelListener(this);
            annot.addPropertyChangeListener(this);
            page.addPropertyChangeListener(this);
            updateEnabled();
        }

        public void removeNotify() {
            super.removeNotify();
            updateEnabled();
        }


        private void updateEnabled() {
            if (docpanel == pagepanel.getDocumentPanel() && SwingUtilities.getAncestorOfClass(PagePanel.class, this) == pagepanel) {
                boolean newreadonly = !annot.isReadOnly() && docpanel.hasPermission("Annotate");
                if (newreadonly && !readonly) {
                    addMouseListener(this);
                    addMouseMotionListener(this);
                } else if (readonly && !newreadonly) {
                    reset();
                    removeMouseListener(this);
                    removeMouseMotionListener(this);
                }
                readonly = newreadonly;
            } else {
                // If our annot has been removed, docpanel has changed PDF then remove ourselves for GC
                docpanel.removeDocumentPanelListener(this);
                annot.removePropertyChangeListener(this);
                page.removePropertyChangeListener(this);
            }
        }

        private Point getScreenPoint(int point) {
            return (Point)pagepanel.getPageToScreenTransform().transform(points[point], new Point());
        }

        public void mouseClicked(MouseEvent e) {
            if (e.isPopupTrigger()) {
                showPopup(e.getPoint());
                reset();
                redrawAnnotation(false);
                repaint();
            }
        }

        private void reset() {
            selectedVertex = hoverVertex = -1;
            dragPointScreen = null;
            targetArea = null;
            setCursor(null);
        }

        public void mousePressed(MouseEvent e) {
            if (e.isPopupTrigger()) {
                mouseClicked(e);
                return;
            }
            boolean caught = false;
            Point mousepoint = SwingUtilities.convertPoint(this, e.getPoint(), pagepanel);
            for (int i=0;!caught && i < points.length;i++) {
                Point screenpoint = getScreenPoint(i);
                if (mousepoint.distance(screenpoint) < MARGIN) {
                    selectedVertex = i;
                    caught = true;
                }
            }
            if (caught) {
                dragPointScreen = mousepoint;
                Rectangle pageBounds = getParent().getBounds();
                putClientProperty("images", new BufferedImage[3]);
                setBounds(0, 0, pageBounds.width, pageBounds.height);
                repaint();
                new Thread() {
                    public void run() {
                        try {
                            while (dragPointScreen!=null) {
                                Thread.sleep(AbstractRegionSelector.CRAWLSPEED);
                                repaint();
                            }
                        } catch (InterruptedException e) { }
                    }
                }.start();
            } else {
                dispatchMouseEvent(e);
            }
        }

        private void dispatchMouseEvent(MouseEvent e) {
            // TODO - currently only sends to parents, we really want to
            // send to next item in the stacking hierarchy in case two
            // components overlap
            Point offset = getLocation();
            e.translatePoint(offset.x, offset.y);
            getParent().dispatchEvent(e);
        }

        public void mouseReleased(MouseEvent e) {
            if (e.isPopupTrigger()) {
                mouseClicked(e);
                return;
            }
            if (selectedVertex >= 0) {
                Point screenpoint = SwingUtilities.convertPoint(this, e.getPoint(), pagepanel);
                if (isAllowedDragPoint(screenpoint)) {
                    Point2D pagepoint = pagepanel.getScreenToPageTransform().transform(screenpoint, null);
                    points[selectedVertex] = pagepoint;
                }
                redrawAnnotation(true);
                reset();
                repaint();
            }
            dispatchMouseEvent(e);
        }

        public void mouseEntered(MouseEvent e) {
            mouseMoved(e);
        }

        public void mouseExited(MouseEvent e) {
            if (targetArea != null) {
                targetArea = null;
                setCursor(null);
                repaint();
            }
        }

        public void mouseDragged(MouseEvent e) {
            if (selectedVertex >= 0) {
                // Ensure drag point stays inside page bounds
                Point p = SwingUtilities.convertPoint(this, e.getPoint(), pagepanel);
                Rectangle b = getBounds();
                p.x = Math.max(b.x, Math.min(b.width-1, p.x));
                p.y = Math.max(b.y, Math.min(b.height-1, p.y));
                if (isAllowedDragPoint(p)) {
                    dragPointScreen = p;
                }
                repaint();
             } else {
                 dispatchMouseEvent(e);
             }
        }

        protected boolean isAllowedDragPoint(Point p) {
            return true;
        }

        public void mouseMoved(MouseEvent e) {
            Point mousepoint = SwingUtilities.convertPoint(this, e.getPoint(), pagepanel);
            Point p = null;
            int newhover = -1;
            for (int i=0;p==null && i < points.length;i++) {
                Point screenpoint = getScreenPoint(i);
                if (mousepoint.distance(screenpoint) < MARGIN) {
                    p = SwingUtilities.convertPoint(pagepanel, screenpoint, this);
                    newhover = i;
                }
            }
            if (newhover != hoverVertex) {
                hoverVertex = newhover;
                if (hoverVertex >= 0) {
                    targetArea = new Ellipse2D.Float(p.x - MARGIN, p.y - MARGIN, MARGIN * 2, MARGIN * 2);
                    setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                } else {
                    targetArea = null;
                    setCursor(null);
                }
                repaint();
            }
            if (targetArea == null) {
                 dispatchMouseEvent(e);
            }
        }

        public void paintComponent(Graphics g) {
            if (dragPointScreen != null) {
                g.setColor(Color.black);
                BasicStroke stroke = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 15, new float[] { AbstractRegionSelector.CRAWLLENGTH, AbstractRegionSelector.CRAWLLENGTH }, (int)((System.currentTimeMillis()/AbstractRegionSelector.CRAWLSPEED)%(AbstractRegionSelector.CRAWLLENGTH*2)));
                ((Graphics2D)g).setStroke(stroke);

                Point firstp = null, lastp = null;
                for (int i=0;i < points.length;i++) {
                    Point p = i==selectedVertex ? dragPointScreen : getScreenPoint(i);
                    p = SwingUtilities.convertPoint(pagepanel, p, this);
                    if (i > 0) {
                        g.drawLine(lastp.x, lastp.y, p.x, p.y);
                    }
                    lastp = p;
                    if (firstp==null) {
                        firstp = p;
                    }
                }
                if (closepath) {
                    g.drawLine(lastp.x, lastp.y, firstp.x, firstp.y);
                }
            } else {
                AnnotationComponentFactory.paintComponent(this, this.ui, g);
                if (targetArea != null) {
                    g.setColor(new Color(0xA0FF0000, true));
                    ((Graphics2D)g).fill(targetArea);
                }
            }
        }

        /**
         * Called when a popup trigger is received
         */
        void showPopup(Point point) {
            popupPropertyMenu(annot, this, point);
        }

        /**
         * Called when the annotation needs to be redrawn.
         * @param rebuild true if the points have been altered, false
         * if we just need to call PagePanel.redrawAnnotaton()
         */
        void redrawAnnotation(boolean changed) {
            if (changed) {

                Shape workpath;
                if (points.length == 2) {
                    workpath = new Line2D.Float(points[0], points[1]);
                } else {
                    GeneralPath path = new GeneralPath();
                    for (int i=0;i<points.length;i++) {
                        if (ismove.get(i)) {
                            path.moveTo((float)points[i].getX(), (float)points[i].getY());
                        } else {
                            path.lineTo((float)points[i].getX(), (float)points[i].getY());
                        }
                    }
                    if (closepath) {
                        path.closePath();
                    }
                    workpath = path;
                }

                final Shape newpath = workpath;
                final Shape oldpath = annot.getShape();
                annot.setShape(newpath);
                annot.rebuild();

                final DocumentPanel docpanel = pagepanel.getDocumentPanel();
                docpanel.fireUndoableEditEvent(new UndoableEditEvent(docpanel, new AbstractUndoableEdit() {
                    public String getPresentationName() {
                        return UIManager.getString("InternalFrameTitlePane.moveButtonText");
                    }
                    public void undo() {
                        super.undo();
                        annot.setShape(oldpath);
                        annot.rebuild();
                    }
                    public void redo() {
                        super.redo();
                        annot.setShape(newpath);
                        annot.rebuild();
                    }
                }));
            }
        }
    }
}
